package az.pricecompare.matching;

import az.pricecompare.domain.ProductSpecs;
import az.pricecompare.domain.StoreOffer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a store's messy product title into normalized attributes we can match on.
 *
 * Stores title the same phone differently, e.g.:
 *   "Apple iPhone 16 Pro Max 256GB Natural Titanium"
 *   "iPhone 16 Pro Max (256 GB) - Titanium"
 *   "Smartfon Apple iPhone 16 Pro Max 256Gb Qara"
 *
 * We extract brand, a cleaned model, storage and RAM so the matcher can decide
 * these are the same product.
 */
@Component
public class ProductNormalizer {

    // Common brands seen in these stores. Order matters: longer/multiword first.
    private static final List<String> BRANDS = List.of(
            "samsung", "apple", "iphone", "xiaomi", "redmi", "poco", "huawei",
            "honor", "realme", "oppo", "vivo", "nokia", "asus", "lenovo",
            "hp", "dell", "acer", "msi", "lg", "sony", "google", "oneplus",
            "tecno", "infinix"
    );

    private static final Pattern STORAGE = Pattern.compile(
            "(\\d{2,4})\\s*(gb|tb)\\b", Pattern.CASE_INSENSITIVE);

    // RAM is usually written like "8/256", "8GB RAM", "RAM 8 GB".
    private static final Pattern RAM = Pattern.compile(
            "(?:ram\\s*)?(\\d{1,2})\\s*gb\\s*(?:ram)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern RAM_SLASH = Pattern.compile(
            "\\b(\\d{1,2})\\s*/\\s*(\\d{2,4})\\b");

    /**
     * Populate normalized fields on an offer's specs and return a normalized
     * key object used by the matcher. Non-destructive to the raw title.
     */
    public NormalizedProduct normalize(StoreOffer offer) {
        String raw = offer.getRawTitle() == null ? "" : offer.getRawTitle();
        String lower = raw.toLowerCase(Locale.ROOT);

        String brand = detectBrand(lower);
        String storage = detectStorage(lower);
        String ram = detectRam(lower);

        // Build a comparable "model signature": strip brand words, colors,
        // marketing fluff and punctuation, keep the meaningful model tokens.
        String signature = buildSignature(lower, brand);

        // Fold detected specs back onto the offer so the response carries them.
        ProductSpecs specs = offer.getSpecs() != null ? offer.getSpecs()
                : ProductSpecs.builder().build();
        if (specs.getStorage() == null && storage != null) specs.setStorage(storage);
        if (specs.getRam() == null && ram != null) specs.setRam(ram);
        offer.setSpecs(specs);

        return new NormalizedProduct(
                brand,
                signature,
                storage,
                ram,
                buildCanonicalName(brand, signature, storage)
        );
    }

    private String detectBrand(String lower) {
        for (String b : BRANDS) {
            if (lower.contains(b)) {
                // Normalize the "iphone" alias to the Apple brand for display.
                return b.equals("iphone") ? "apple" : b;
            }
        }
        return "unknown";
    }

    private String detectStorage(String lower) {
        // Prefer an explicit "x/yGB" pattern's second number as storage.
        Matcher slash = RAM_SLASH.matcher(lower);
        if (slash.find()) {
            return slash.group(2) + "GB";
        }
        Matcher m = STORAGE.matcher(lower);
        while (m.find()) {
            int val = Integer.parseInt(m.group(1));
            String unit = m.group(2).toUpperCase(Locale.ROOT);
            // Storage is typically >= 64GB; smaller GB numbers are usually RAM.
            if (unit.equals("TB") || val >= 64) {
                return val + unit;
            }
        }
        return null;
    }

    private String detectRam(String lower) {
        Matcher slash = RAM_SLASH.matcher(lower);
        if (slash.find()) {
            return slash.group(1) + "GB";
        }
        Matcher m = RAM.matcher(lower);
        while (m.find()) {
            int val = Integer.parseInt(m.group(1));
            // RAM for phones/laptops is realistically <= 32GB.
            if (val <= 32) {
                return val + "GB";
            }
        }
        return null;
    }

    /**
     * Produce a compact model signature by removing brand, storage, colors and
     * noise, then keeping alphanumeric model tokens. Two offers with the same
     * signature (or a high token overlap) are considered the same product.
     */
    private String buildSignature(String lower, String brand) {
        String s = lower;
        s = s.replace("iphone", " iphone "); // keep model family token
        // Remove obvious noise words (Azerbaijani + Russian + English).
        for (String noise : NOISE_WORDS) {
            s = s.replaceAll("\\b" + Pattern.quote(noise) + "\\b", " ");
        }
        // Remove colors.
        for (String color : COLOR_WORDS) {
            s = s.replaceAll("\\b" + Pattern.quote(color) + "\\b", " ");
        }
        // Remove storage/ram tokens (already captured separately).
        s = STORAGE.matcher(s).replaceAll(" ");
        s = RAM_SLASH.matcher(s).replaceAll(" ");
        // Drop the brand word itself except keep "iphone" family for phones.
        if (!brand.equals("apple")) {
            s = s.replaceAll("\\b" + Pattern.quote(brand) + "\\b", " ");
        } else {
            s = s.replaceAll("\\bapple\\b", " ");
        }
        // Keep letters, digits and spaces only.
        s = s.replaceAll("[^a-z0-9 ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private String buildCanonicalName(String brand, String signature, String storage) {
        StringBuilder sb = new StringBuilder();
        if (brand != null && !brand.equals("unknown")) {
            sb.append(capitalize(brand)).append(' ');
        }
        sb.append(titleCaseTokens(signature));
        if (storage != null) {
            sb.append(' ').append(storage);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String titleCaseTokens(String s) {
        StringBuilder out = new StringBuilder();
        for (String tok : s.split(" ")) {
            if (tok.isBlank()) continue;
            // Keep model numbers/letters uppercase-ish where natural.
            out.append(capitalize(tok)).append(' ');
        }
        return out.toString().trim();
    }

    // Words that carry no model meaning; extend as you see store-specific fluff.
    private static final List<String> NOISE_WORDS = List.of(
            "smartfon", "smartphone", "telefon", "mobil", "cep", "cib",
            "noutbuk", "notebook", "laptop", "kompüter", "computer", "planşet",
            "tablet", "yeni", "new", "original", "originali", "rəngli"
    );

    // Colors in az/ru/en so they don't pollute the model signature.
    private static final List<String> COLOR_WORDS = List.of(
            "qara", "ağ", "boz", "gümüşü", "qızılı", "mavi", "yaşıl", "sarı",
            "bənövşəyi", "çəhrayı", "black", "white", "gray", "grey", "silver",
            "gold", "blue", "green", "yellow", "purple", "pink", "titanium",
            "natural", "desert", "midnight", "starlight", "graphite",
            "чёрный", "белый", "серый", "золотой", "синий", "зелёный"
    );
}
