package az.pricecompare.scraper;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.CreditOption;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.impl.IrshadScraper;
import az.pricecompare.scraper.impl.KontaktHomeScraper;
import az.pricecompare.scraper.impl.SolitonScraper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Drives each scraper's real parsing code against markup captured verbatim from
 * the live stores.
 *
 * These are the tests that matter most in this codebase: every scraper depends on
 * CSS selectors against sites we don't control, and a redesign breaks them
 * silently — the service keeps returning 200 with an empty list. Refresh the
 * fixtures in {@code src/test/resources/fixtures} when a store changes, and these
 * will tell you exactly which selector died.
 */
class ScraperFixtureTest {

    private HtmlFetcher fetcher;
    private ScraperProperties props;

    @BeforeEach
    void setUp() {
        fetcher = mock(HtmlFetcher.class);
        props = new ScraperProperties();
        props.setMaxResultsPerStore(24);
        props.setStores(Map.of(
                "kontakt", storeConfig("https://kontakt.az"),
                "irshad", storeConfig("https://irshad.az"),
                "soliton", storeConfig("https://soliton.az")));
    }

    private ScraperProperties.StoreConfig storeConfig(String baseUrl) {
        ScraperProperties.StoreConfig cfg = new ScraperProperties.StoreConfig();
        cfg.setEnabled(true);
        cfg.setBaseUrl(baseUrl);
        cfg.setSearchUrl(baseUrl + "/search?q={query}");
        cfg.setEnrich(true);
        return cfg;
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = ScraperFixtureTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertThat(in).as("fixture %s must exist", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Document html(String name, String baseUri) throws IOException {
        return Jsoup.parse(fixture(name), baseUri);
    }

    private static JsonNode json(String name) throws IOException {
        return new ObjectMapper().readTree(fixture(name));
    }

    // ------------------------------------------------------------------
    // Kontakt — JSON search API, HTML detail page
    // ------------------------------------------------------------------

    @Test
    void kontaktParsesItsSearchApi() throws Exception {
        when(fetcher.getJson(anyString(), anyLong())).thenReturn(json("kontakt-search.json"));
        KontaktHomeScraper scraper = new KontaktHomeScraper(fetcher, props);

        List<StoreOffer> offers = scraper.search("iphone 16 pro max");

        assertThat(offers).isNotEmpty();
        assertThat(offers).allSatisfy(o -> {
            assertThat(o.getStore()).isEqualTo(StoreName.KONTAKT_HOME);
            assertThat(o.getRawTitle()).isNotBlank();
            assertThat(o.getProductUrl()).startsWith("https://kontakt.az/");
        });

        StoreOffer phone = offers.stream()
                .filter(o -> o.getRawTitle().startsWith("iPhone 16 Pro Max 256 GB Black"))
                .findFirst().orElseThrow();
        assertThat(phone.getPrice()).isEqualByComparingTo("2999.99");
        assertThat(phone.getSku()).isNotBlank();
        assertThat(phone.getImageUrls()).isNotEmpty();
    }

    @Test
    void kontaktParsesSpecsAndInstallmentsFromProductPage() throws Exception {
        when(fetcher.getHtml(anyString(), anyLong()))
                .thenReturn(html("kontakt-product.html", "https://kontakt.az/"));

        StoreOffer offer = StoreOffer.builder()
                .store(StoreName.KONTAKT_HOME)
                .rawTitle("Samsung Galaxy S25 128 GB Silver")
                .productUrl("https://kontakt.az/samsung-galaxy-s25")
                .price(new BigDecimal("1799.99"))
                .build();

        new KontaktHomeScraper(fetcher, props).enrich(offer);

        assertThat(offer.isEnriched()).isTrue();
        assertThat(offer.getSpecs().getStorage()).isEqualTo("128 GB");
        assertThat(offer.getSpecs().getRam()).isEqualTo("12 GB");
        assertThat(offer.getSpecs().getProcessor()).isNotBlank();
        // Unmapped attributes are preserved rather than dropped.
        assertThat(offer.getSpecs().getAdditional()).isNotEmpty();

        assertThat(offer.getCreditOptions())
                .extracting(CreditOption::getMonths)
                .containsExactly(6, 9, 12, 15, 18, 24);

        CreditOption twelve = offer.getCreditOptions().stream()
                .filter(c -> c.getMonths() == 12).findFirst().orElseThrow();
        assertThat(twelve.getMonthlyPayment()).isEqualByComparingTo("150.00");
        assertThat(twelve.getTotalPayable()).isEqualByComparingTo("1800.00");
        // 12 x 150 against a 1799.99 cash price really is interest-free.
        assertThat(twelve.isInterestFree()).isTrue();
    }

    // ------------------------------------------------------------------
    // Irshad — HTML fragment with prices and installments already inline
    // ------------------------------------------------------------------

    @Test
    void irshadParsesPricesColoursAndInstallmentsFromSearchFragment() throws Exception {
        when(fetcher.getHtml(anyString(), anyLong()))
                .thenReturn(html("irshad-search.html", "https://irshad.az/"));

        List<StoreOffer> offers = new IrshadScraper(fetcher, props).search("iphone 16");

        assertThat(offers).hasSize(4);
        assertThat(offers).allSatisfy(o -> {
            assertThat(o.getStore()).isEqualTo(StoreName.IRSHAD);
            assertThat(o.getRawTitle()).isNotBlank();
            assertThat(o.getProductUrl()).startsWith("https://irshad.az/");
            assertThat(o.getPrice()).isNotNull();
            assertThat(o.getImageUrls()).isNotEmpty();
        });

        StoreOffer first = offers.get(0);
        assertThat(first.getPrice()).isEqualByComparingTo("2109.99");
        assertThat(first.isInStock()).isTrue();

        // The search fragment already carries the installment table — no detail
        // fetch needed for credit at this store.
        assertThat(first.getCreditOptions()).isNotEmpty();
        assertThat(first.getCreditOptions())
                .allSatisfy(c -> {
                    assertThat(c.getMonths()).isPositive();
                    assertThat(c.getMonthlyPayment()).isNotNull();
                });
    }

    // ------------------------------------------------------------------
    // Soliton — search has no prices at all; everything comes from detail
    // ------------------------------------------------------------------

    @Test
    void solitonParsesSearchResultsWithoutPrices() throws Exception {
        when(fetcher.getHtml(anyString(), anyLong()))
                .thenReturn(html("soliton-search.html", "https://soliton.az/"));

        List<StoreOffer> offers = new SolitonScraper(fetcher, props).search("iphone 16");

        assertThat(offers).hasSize(3);
        assertThat(offers).allSatisfy(o -> {
            assertThat(o.getRawTitle()).isNotBlank();
            assertThat(o.getProductUrl()).startsWith("https://soliton.az/");
            // Documenting the constraint that forces enrichment for this store.
            assertThat(o.getPrice()).isNull();
        });
        assertThat(offers).extracting(StoreOffer::getRawTitle)
                .contains("iPhone 16 128GB BLACK");
    }

    @Test
    void solitonRecoversPriceCreditAndSpecsFromProductPage() throws Exception {
        when(fetcher.getHtml(anyString(), anyLong()))
                .thenReturn(html("soliton-product.html", "https://soliton.az/"));

        StoreOffer offer = StoreOffer.builder()
                .store(StoreName.SOLITON)
                .rawTitle("iPhone 16 128GB BLACK")
                .productUrl("https://soliton.az/az/telefon/iphone-16-128gb-black.html")
                .build();

        new SolitonScraper(fetcher, props).enrich(offer);

        // The price is split across nested spans; parsing must reassemble it.
        assertThat(offer.getPrice()).isEqualByComparingTo("2299.99");
        assertThat(offer.isInStock()).isTrue();

        assertThat(offer.getCreditOptions()).isNotEmpty();
        CreditOption three = offer.getCreditOptions().stream()
                .filter(c -> c.getMonths() == 3).findFirst().orElseThrow();
        assertThat(three.getMonthlyPayment()).isEqualByComparingTo("766.66");

        assertThat(offer.getSpecs().getProcessor()).isEqualTo("Apple A18");
        assertThat(offer.getSpecs().getStorage()).isEqualTo("128 GB");
        assertThat(offer.getSpecs().getOperatingSystem()).isEqualTo("iOS");
        assertThat(offer.getSpecs().getAdditional()).containsKey("Çəki");
    }

    // ------------------------------------------------------------------
    // Failure behaviour
    // ------------------------------------------------------------------

    @Test
    void searchFailureIsReportedAsAStoreFailureNotSwallowed() throws Exception {
        when(fetcher.getJson(anyString(), anyLong())).thenThrow(new IOException("503"));

        KontaktHomeScraper scraper = new KontaktHomeScraper(fetcher, props);

        assertThat(org.junit.jupiter.api.Assertions
                .assertThrows(StoreScrapeException.class, () -> scraper.search("iphone")))
                .hasMessageContaining("503");
    }

    @Test
    void enrichmentFailureLeavesTheOfferUsable() throws Exception {
        when(fetcher.getHtml(anyString(), anyLong())).thenThrow(new IOException("timeout"));

        StoreOffer offer = StoreOffer.builder()
                .store(StoreName.KONTAKT_HOME)
                .rawTitle("iPhone 16 Pro Max 256 GB Black Titanium")
                .productUrl("https://kontakt.az/x")
                .price(new BigDecimal("2999.99"))
                .build();

        new KontaktHomeScraper(fetcher, props).enrich(offer);

        // Best-effort: a listing without specs still beats no listing.
        assertThat(offer.isEnriched()).isFalse();
        assertThat(offer.getPrice()).isEqualByComparingTo("2999.99");
    }

    @Test
    void disabledStoreReturnsEmptyWithoutFetching() {
        ScraperProperties.StoreConfig cfg = storeConfig("https://kontakt.az");
        cfg.setEnabled(false);
        props.setStores(Map.of("kontakt", cfg));

        assertThat(new KontaktHomeScraper(fetcher, props).search("iphone")).isEmpty();
    }
}
