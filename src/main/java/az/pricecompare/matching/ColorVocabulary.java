package az.pricecompare.matching;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises colour names in product titles and normalizes them to one English
 * label per colour.
 *
 * This exists because colour is how these stores model variants — "iPhone 16
 * 128 GB Black" and "iPhone 16 128 GB Pink" are separate listings — and the
 * headline feature ("Irshad has black, Kontakt doesn't") is only answerable if
 * "Qara", "BLACK" and "Black Titanium" all collapse to the same token.
 *
 * Phrases are matched longest-first so "Natural Titanium" wins over "Natural",
 * and "Mist Blue" over "Blue".
 */
final class ColorVocabulary {

    private ColorVocabulary() {}

    /**
     * Alias -> canonical label. Insertion order is irrelevant; matching sorts by
     * length. Apple's titanium/marketing names are kept distinct from plain
     * colours because a buyer choosing "Desert Titanium" does not mean "gold".
     */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        // Apple finishes — these are genuine distinct SKUs, not synonyms.
        put("Black Titanium", "black titanium", "qara titanium");
        put("White Titanium", "white titanium", "ağ titanium");
        put("Natural Titanium", "natural titanium", "naturaltitanium");
        put("Desert Titanium", "desert titanium");
        put("Blue Titanium", "blue titanium");
        put("Ultramarine", "ultramarine");
        put("Mist Blue", "mist blue");
        put("Icy Blue", "icy blue", "icyblue");
        put("Navy Shadow", "navy shadow");
        put("Shadow", "shadow");
        put("Coral Red", "coral red", "coralred");
        put("Mint", "mint green", "mintgreen", "mint");
        put("Sage", "sage");
        put("Teal", "teal");
        put("Midnight", "midnight", "gecə");
        put("Starlight", "starlight");
        put("Graphite", "graphite", "qrafit");
        put("Fuchsia", "fuchsia");
        put("Lavender", "lavender", "lavanda");
        put("Titanium", "titanium", "titan");

        // Plain colours, across the three languages these sites mix freely.
        put("Black", "black", "qara", "чёрный", "черный");
        put("White", "white", "ağ", "белый");
        put("Gray", "gray", "grey", "boz", "серый");
        put("Silver", "silver", "gümüşü", "gümüş", "серебристый");
        put("Gold", "gold", "qızılı", "золотой");
        put("Blue", "blue", "mavi", "синий", "goluboy");
        put("Green", "green", "yaşıl", "зелёный", "зеленый");
        put("Red", "red", "qırmızı", "красный");
        put("Yellow", "yellow", "sarı", "жёлтый");
        put("Purple", "purple", "bənövşəyi", "фиолетовый");
        put("Pink", "pink", "çəhrayı", "розовый");
        put("Orange", "orange", "narıncı");
        put("Beige", "beige", "bej");
        put("Brown", "brown", "qəhvəyi");
        put("Cream", "cream", "krem");
        put("Navy", "navy");
    }

    private static void put(String canonical, String... aliases) {
        for (String a : aliases) {
            ALIASES.put(a.toLowerCase(Locale.ROOT), canonical);
        }
    }

    /** Aliases ordered longest-first, so multi-word finishes win. */
    private static final List<String> ORDERED = ALIASES.keySet().stream()
            .sorted((a, b) -> Integer.compare(b.length(), a.length()))
            .toList();

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        for (String alias : ORDERED) {
            PATTERNS.put(alias, Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(alias)
                    + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    /** The canonical colour named in this text, or null. */
    static String detect(String lowerText) {
        if (lowerText == null) return null;
        for (String alias : ORDERED) {
            if (PATTERNS.get(alias).matcher(lowerText).find()) {
                return ALIASES.get(alias);
            }
        }
        return null;
    }

    /** Remove every colour mention, so the model signature isn't polluted by it. */
    static String strip(String lowerText) {
        if (lowerText == null) return null;
        String s = lowerText;
        for (String alias : ORDERED) {
            Matcher m = PATTERNS.get(alias).matcher(s);
            s = m.replaceAll(" ");
        }
        return s;
    }
}
