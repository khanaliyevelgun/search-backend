package az.pricecompare.scraper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The price parser must handle the many ways Azerbaijani stores format prices.
 * These are pure-logic tests — no network, no live HTML — so they're stable.
 */
class ScrapeUtilsTest {

    @Test
    void parsesPlainInteger() {
        assertThat(ScrapeUtils.parsePrice("2199")).isEqualByComparingTo("2199");
    }

    @Test
    void parsesSpaceThousandsWithManatSymbol() {
        assertThat(ScrapeUtils.parsePrice("2 199 ₼")).isEqualByComparingTo("2199");
    }

    @Test
    void parsesCommaDecimal() {
        assertThat(ScrapeUtils.parsePrice("2199,00 AZN")).isEqualByComparingTo("2199.00");
    }

    @Test
    void parsesDotThousandsCommaDecimal() {
        assertThat(ScrapeUtils.parsePrice("2.199,50")).isEqualByComparingTo("2199.50");
    }

    @Test
    void parsesCommaThousandsDotDecimal() {
        assertThat(ScrapeUtils.parsePrice("2,199.50")).isEqualByComparingTo("2199.50");
    }

    @Test
    void returnsNullForNoNumber() {
        assertThat(ScrapeUtils.parsePrice("Qiymət yoxdur")).isNull();
        assertThat(ScrapeUtils.parsePrice(null)).isNull();
    }

    @Test
    void parsesInstallmentMonths() {
        assertThat(ScrapeUtils.parseMonths("12 ay")).isEqualTo(12);
        assertThat(ScrapeUtils.parseMonths("6 months")).isEqualTo(6);
        assertThat(ScrapeUtils.parseMonths("no months here")).isNull();
    }

    @Test
    void buildsAbsoluteUrls() {
        String base = "https://kontakt.az";
        assertThat(ScrapeUtils.absoluteUrl(base, "/p/iphone")).isEqualTo("https://kontakt.az/p/iphone");
        assertThat(ScrapeUtils.absoluteUrl(base, "https://x.az/y")).isEqualTo("https://x.az/y");
        assertThat(ScrapeUtils.absoluteUrl(base, "//cdn.az/img.jpg")).isEqualTo("https://cdn.az/img.jpg");
    }
}
