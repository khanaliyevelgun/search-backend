package az.pricecompare.scraper;

import az.pricecompare.domain.CreditOption;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final Pattern PRICE = Pattern.compile("(\\d[\\d\\s .,]*\\d|\\d)");

    private static final Pattern MONTHS = Pattern.compile("(\\d{1,2})\\s*(ay|month|мес)");

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

        // If both separators appear, whichever is last is the decimal mark.
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
        } else if (lastDot >= 0 && raw.length() - lastDot > 3) {
            // Only a dot, too far from the end to be decimal: "2.199" -> 2199
            raw = raw.replace(".", "");
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
        Matcher m = MONTHS.matcher(text.toLowerCase());
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /**
     * Build a credit option, deriving the total and whether the plan really is
     * interest-free by comparing against the cash price.
     *
     * Stores advertise "faizsiz" (interest-free) liberally, so we don't take their
     * word for it — 12 x 150.00 against a 1799.99 cash price is interest-free;
     * 12 x 175.00 is not, whatever the badge says.
     */
    public static CreditOption creditOption(Integer months, BigDecimal monthly, BigDecimal cashPrice) {
        if (months == null || months <= 0 || monthly == null) {
            return null;
        }
        BigDecimal total = monthly.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal overpayment = null;
        boolean interestFree = false;
        if (cashPrice != null && cashPrice.signum() > 0) {
            overpayment = total.subtract(cashPrice).setScale(2, RoundingMode.HALF_UP);
            // Within 1% of the cash price is rounding, not interest.
            BigDecimal tolerance = cashPrice.multiply(new BigDecimal("0.01"));
            interestFree = overpayment.compareTo(tolerance) <= 0;
        }
        return CreditOption.builder()
                .months(months)
                .monthlyPayment(monthly)
                .totalPayable(total)
                .overpayment(overpayment)
                .interestFree(interestFree)
                .build();
    }

    /** Collapse whitespace and trim. */
    public static String clean(String s) {
        return s == null ? null : s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Null-safe blank check. */
    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** First non-blank value, or null. */
    public static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (!isBlank(v)) return v;
        }
        return null;
    }

    /** Resolve a possibly-relative URL against a base. */
    public static String absoluteUrl(String baseUrl, String href) {
        if (isBlank(href)) return null;
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        if (href.startsWith("/")) return base + href;
        return base + "/" + href;
    }
}
