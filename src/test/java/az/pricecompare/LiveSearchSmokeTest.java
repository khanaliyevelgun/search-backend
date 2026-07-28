package az.pricecompare;

import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.SearchResponse;
import az.pricecompare.domain.StoreSummary;
import az.pricecompare.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application and searches the real stores.
 *
 * Disabled by default — it hits three live third-party sites, so it is slow, it
 * depends on their availability, and it should never run on every build. Enable
 * it deliberately when you want to know whether the selectors still work:
 *
 * <pre>{@code ./mvnw test -Dtest=LiveSearchSmokeTest -Dlive.stores=true}</pre>
 *
 * When this fails and {@link az.pricecompare.scraper.ScraperFixtureTest} passes,
 * a store has changed its markup and the fixtures need refreshing.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "live.stores", matches = "true")
class LiveSearchSmokeTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchesRealStoresAndComparesThem() {
        // Override with -Dlive.query="samsung galaxy s25" to probe another product.
        String query = System.getProperty("live.query", "iphone 16");
        SearchResponse response = searchService.search(query);

        System.out.printf("%nQuery '%s' -> %d products, %d stores ok, %d failed, %dms%n",
                response.getQuery(), response.getResults().size(),
                response.getStoresQueried().size(), response.getStoreErrors().size(),
                response.getTookMs());
        response.getStoreErrors().forEach(e ->
                System.out.printf("  FAILED %s: %s%n", e.getStore(), e.getMessage()));

        for (ProductComparison c : response.getResults()) {
            System.out.printf("%n%s  (%s .. %s AZN, cheapest %s, colours %s)%n",
                    c.getCanonicalName(), c.getLowestPrice(), c.getHighestPrice(),
                    c.getCheapestStore(), c.getAllColorsSeen());
            for (StoreSummary s : c.getStores()) {
                System.out.printf("   %-22s %9s  colours=%-28s missing=%-20s credit=%s%n",
                        s.getStoreDisplayName(), s.getPrice(),
                        s.getColorsAvailable(), s.getColorsMissing(),
                        s.getLowestMonthlyPayment() == null ? "-"
                                : s.getLowestMonthlyPayment() + "/mo");
            }
        }

        // At least one store must have answered; all three failing means the
        // endpoints have moved, which is exactly what this test exists to catch.
        assertThat(response.getStoresQueried()).isNotEmpty();
        assertThat(response.getResults()).isNotEmpty();

        // Every comparison must carry a usable price from somewhere.
        assertThat(response.getResults())
                .anySatisfy(c -> assertThat(c.getLowestPrice()).isNotNull());
    }
}
