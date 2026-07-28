package az.pricecompare.scraper;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small parsing helpers shared by all store scrapers. Azerbaijani stores format
 * prices and specs inconsistently (spaces as thousands separators, "AZN"/"₼"
 * suffixes, comma vs. dot decimals), so these normalize that mess.
 */
public final class ScrapeUtils {

    private ScrapeUtils() {}

    // Matches the first number in a string, allowing thousands separators and
    // both comma and dot as decimal marks, e.g. "2 199,00 ₼" -> 2199.00
    private static final Pattern PRICE = Pattern.compile("(\\d[\\d\\s.,]*\\d|\\d)");

    /**
     * Extract a price from arbitrary text like "2 199 ₼" or "2199,00 AZN".
     * Returns null when no number is present.
     */
    public static BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = PRICE.matcher(text.trim());
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1)
                .replace(" ", "")   // non-breaking space
                .replace(" ", "");

        // If both separators appear, assume '.' or ',' whichever is last is decimal.
        int lastDot = raw.lastIndexOf('.');
        int lastComma = raw.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastComma > lastDot) {
                // "2.199,00" -> dot=thousands, comma=decimal
                raw = raw.replace(".", "").replace(',', '.');
            } else {
                // "2,199.00" -> comma=thousands, dot=decimal
                raw = raw.replace(",", "");
            }
        } else if (lastComma >= 0) {
            // Only comma present. Treat as decimal if it looks like ",dd".
            if (raw.length() - lastComma <= 3) {
                raw = raw.replace(',', '.');
            } else {
                raw = raw.replace(",", "");
            }
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extract months from installment text like "12 ay" (ay = month in Azerbaijani)
     * or "12 months". Returns null if none found.
     */
    public static Integer parseMonths(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("(\\d{1,2})\\s*(ay|month|мес)").matcher(text.toLowerCase());
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** Collapse whitespace and trim. */
    public static String clean(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    /** Resolve a possibly-relative URL against a base. */
    public static String absoluteUrl(String baseUrl, String href) {
        if (href == null || href.isBlank()) return null;
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return baseUrl + href;
        return baseUrl + "/" + href;
    }
}
