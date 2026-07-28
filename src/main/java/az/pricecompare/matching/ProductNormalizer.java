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
 *   "iPhone 16 Pro Max 256 GB Black Titanium"      (Kontakt)
 *   "iPhone 16 Pro Max 256 GB Black"               (Irshad)
 *   "iPhone 16 128GB BLACK"                        (Soliton)
 *
 * We extract brand, colour, storage and RAM, and reduce what's left to a model
 * signature the matcher can compare.
 */
@Component
public class ProductNormalizer {

    /**
     * Brands we recognise. Matched on word boundaries — an earlier version used
     * {@code contains()}, which made short entries like "hp" and "lg" fire inside
     * unrelated words.
     */
    private static final List<String> BRANDS = List.of(
            "samsung", "apple", "iphone", "ipad", "macbook", "xiaomi", "redmi",
            "poco", "huawei", "honor", "realme", "oppo", "vivo", "nokia", "asus",
            "lenovo", "hp", "dell", "acer", "msi", "lg", "sony", "google",
            "oneplus", "tecno", "infinix", "philips", "bosch", "beko", "canon",
            "nikon", "jbl", "anker", "logitech"
    );

    /** Brand aliases that should display as their parent brand. */
    private static final List<String> APPLE_FAMILY = List.of("iphone", "ipad", "macbook");

    // The leading lookbehind matters: without it "128GB" also matches as "28GB",
    // and 1TB/2TB drives need the single-digit case.
    private static final Pattern STORAGE = Pattern.compile(
            "(?<![\\d.])(\\d{1,4})\\s*(gb|tb)(?![\\p{L}])", Pattern.CASE_INSENSITIVE);

    private static final Pattern RAM = Pattern.compile(
            "(?:ram\\s*)?(?<!\\d)(\\d{1,2})\\s*gb\\s*(?:ram)?", Pattern.CASE_INSENSITIVE);

    /**
     * RAM and storage written together, like "8/256" or "12/128GB".
     *
     * Two details are load-bearing. The trailing lookahead rather than {@code \b}
     * is needed because {@code \b} fails on "12/128GB" — digit and "G" are both
     * word characters. And the unit is part of the match so it is removed with
     * the numbers: leaving an orphan "GB" behind let the storage pattern pair it
     * with the "25" of "Galaxy S25" and quietly eat the model number.
     */
    private static final Pattern RAM_SLASH = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*/\\s*(\\d{2,4})\\s*(gb|tb)?(?!\\d)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Manufacturer part numbers, e.g. "SM-S931B" on Samsung listings. Stores
     * include them inconsistently — İrşad writes "Galaxy S25 SM-S931", Soliton
     * just "Galaxy S25" — so leaving them in the signature splits one phone into
     * three separate comparisons.
     */
    private static final Pattern MODEL_CODE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])sm[-\\s]?[a-z]?\\d{3,4}[a-z]?(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE);

    /**
     * Populate normalized fields on an offer's specs and return a normalized key
     * object used by the matcher. The raw title is never modified.
     */
    public NormalizedProduct normalize(StoreOffer offer) {
        String raw = offer.getRawTitle() == null ? "" : offer.getRawTitle();
        String lower = raw.toLowerCase(Locale.ROOT);

        String brand = detectBrand(lower);
        String storage = detectStorage(lower);
        String ram = detectRam(lower);
        String color = ColorVocabulary.detect(lower);
        String signature = buildSignature(lower, brand);

        // Fold detected attributes back onto the offer so the response carries them
        // even when the store's own spec table was unavailable.
        ProductSpecs specs = offer.getSpecs() != null ? offer.getSpecs()
                : ProductSpecs.builder().build();
        if (specs.getStorage() == null && storage != null) specs.setStorage(storage);
        if (specs.getRam() == null && ram != null) specs.setRam(ram);
        offer.setSpecs(specs);

        if (offer.getColor() == null) {
            offer.setColor(color);
        }

        return new NormalizedProduct(
                brand, signature, storage, ram, color,
                buildCanonicalName(brand, signature, storage));
    }

    private String detectBrand(String lower) {
        for (String b : BRANDS) {
            if (wordPresent(lower, b)) {
                return APPLE_FAMILY.contains(b) ? "apple" : b;
            }
        }
        return "unknown";
    }

    private static boolean wordPresent(String haystack, String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])")
                .matcher(haystack).find();
    }

    private String detectStorage(String lower) {
        // "8/256" is unambiguous: the second number is storage.
        Matcher slash = RAM_SLASH.matcher(lower);
        if (slash.find()) {
            return slash.group(2) + "GB";
        }
        Matcher m = STORAGE.matcher(lower);
        while (m.find()) {
            int val = Integer.parseInt(m.group(1));
            String unit = m.group(2).toUpperCase(Locale.ROOT);
            // Any TB figure is storage; in GB, anything under 64 is really RAM.
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
     * Produce a compact model signature by removing brand, storage, colour and
     * marketing noise, keeping only the tokens that identify the model.
     */
    private String buildSignature(String lower, String brand) {
        String s = lower;

        // Storage and RAM come out first, while their units are still attached.
        // Stripping noise words first would remove the "gb" from "256gb" and
        // leave a bare "256" behind to pollute the signature.
        s = RAM_SLASH.matcher(s).replaceAll(" ");
        s = STORAGE.matcher(s).replaceAll(" ");
        s = MODEL_CODE.matcher(s).replaceAll(" ");

        for (String noise : NOISE_WORDS) {
            s = s.replaceAll("(?<![\\p{L}\\p{N}])" + Pattern.quote(noise) + "(?![\\p{L}\\p{N}])", " ");
        }
        s = ColorVocabulary.strip(s);

        // Drop the brand word, but keep Apple's product-family words ("iphone",
        // "ipad") — they're the most discriminating token in the title.
        if (!"unknown".equals(brand) && !"apple".equals(brand)) {
            s = s.replaceAll("(?<![\\p{L}\\p{N}])" + Pattern.quote(brand) + "(?![\\p{L}\\p{N}])", " ");
        } else if ("apple".equals(brand)) {
            s = s.replaceAll("(?<![\\p{L}\\p{N}])apple(?![\\p{L}\\p{N}])", " ");
        }

        // Keep letters, digits and spaces only.
        s = s.replaceAll("[^\\p{L}\\p{N} ]", " ");
        return s.replaceAll("\\s+", " ").trim();
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
            out.append(capitalize(tok)).append(' ');
        }
        return out.toString().trim();
    }

    /** Words that carry no model meaning; extend as you see store-specific fluff. */
    private static final List<String> NOISE_WORDS = List.of(
            "smartfon", "smartphone", "telefon", "mobil", "cep", "cib",
            "noutbuk", "notebook", "laptop", "kompüter", "computer", "planşet",
            "tablet", "yeni", "new", "original", "originali", "rəngli", "rəng",
            "əd", "ədəd", "model", "gb", "tb"
    );
}
