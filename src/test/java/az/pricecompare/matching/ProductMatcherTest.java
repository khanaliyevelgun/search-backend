package az.pricecompare.matching;

import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.domain.StoreSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the core value of the product: that the same phone from different
 * stores, titled differently, gets grouped into one comparison — that different
 * storage variants stay separate, and that colour variants collapse into one row
 * per store.
 */
class ProductMatcherTest {

    private final ProductMatcher matcher = new ProductMatcher(new ProductNormalizer());

    private StoreOffer offer(StoreName store, String title, String price) {
        return StoreOffer.builder()
                .store(store)
                .rawTitle(title)
                .price(price == null ? null : new BigDecimal(price))
                .currency("AZN")
                .build();
    }

    private StoreSummary summaryFor(ProductComparison c, StoreName store) {
        return c.getStores().stream()
                .filter(s -> s.getStore() == store)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no summary for " + store));
    }

    @Test
    void groupsSamePhoneAcrossStoresDespiteDifferentTitles() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "iPhone 16 Pro Max 256 GB Natural Titanium", "2000"),
                offer(StoreName.IRSHAD, "iPhone 16 Pro Max 256 GB Black", "2200"),
                offer(StoreName.SOLITON, "Smartfon Apple iPhone 16 Pro Max 256Gb Qara", "2100")
        );

        List<ProductComparison> groups = matcher.groupOffers(offers);

        assertThat(groups).hasSize(1);
        ProductComparison g = groups.get(0);
        assertThat(g.getStores()).hasSize(3);
        assertThat(g.getLowestPrice()).isEqualByComparingTo("2000");
        assertThat(g.getHighestPrice()).isEqualByComparingTo("2200");
        assertThat(g.getCheapestStore()).isEqualTo(StoreName.KONTAKT_HOME);
        assertThat(g.getMaxSaving()).isEqualByComparingTo("200");
    }

    @Test
    void keepsDifferentStorageVariantsSeparate() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "Apple iPhone 16 Pro Max 256GB", "2000"),
                offer(StoreName.IRSHAD, "Apple iPhone 16 Pro Max 512GB", "2400")
        );

        assertThat(matcher.groupOffers(offers)).hasSize(2);
    }

    @Test
    void keepsDifferentModelsSeparate() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "Apple iPhone 16 Pro Max 256GB", "2000"),
                offer(StoreName.IRSHAD, "Apple iPhone 16 256GB", "1400"),
                offer(StoreName.SOLITON, "Samsung Galaxy S24 Ultra 256GB", "1800")
        );

        assertThat(matcher.groupOffers(offers)).hasSize(3);
    }

    /**
     * The headline feature. Colour variants must land in one comparison, roll up
     * to one row per store, and expose exactly which colours each store lacks.
     */
    @Test
    void collapsesColourVariantsIntoOneRowPerStoreAndReportsMissingColours() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "iPhone 16 128 GB Black", "2099"),
                offer(StoreName.KONTAKT_HOME, "iPhone 16 128 GB White", "2099"),
                offer(StoreName.KONTAKT_HOME, "iPhone 16 128 GB Pink", "2150"),
                offer(StoreName.IRSHAD, "iPhone 16 128 GB White", "2199"),
                offer(StoreName.IRSHAD, "iPhone 16 128 GB Teal", "2199")
        );

        List<ProductComparison> groups = matcher.groupOffers(offers);

        assertThat(groups).hasSize(1);
        ProductComparison g = groups.get(0);

        // One row per store, not one per colour.
        assertThat(g.getStores()).hasSize(2);
        assertThat(g.getTotalOffers()).isEqualTo(5);

        StoreSummary kontakt = summaryFor(g, StoreName.KONTAKT_HOME);
        StoreSummary irshad = summaryFor(g, StoreName.IRSHAD);

        assertThat(kontakt.getColorsAvailable()).containsExactlyInAnyOrder("Black", "White", "Pink");
        assertThat(irshad.getColorsAvailable()).containsExactlyInAnyOrder("White", "Teal");

        // This is the question the product exists to answer.
        assertThat(irshad.getColorsMissing()).containsExactlyInAnyOrder("Black", "Pink");
        assertThat(kontakt.getColorsMissing()).containsExactly("Teal");

        assertThat(g.getAllColorsSeen())
                .containsExactlyInAnyOrder("Black", "White", "Pink", "Teal");

        // Cheapest-per-store uses the cheapest variant, not an arbitrary one.
        assertThat(kontakt.getPrice()).isEqualByComparingTo("2099");
        assertThat(kontakt.getMaxPrice()).isEqualByComparingTo("2150");
        assertThat(g.getCheapestStore()).isEqualTo(StoreName.KONTAKT_HOME);
    }

    /**
     * A store listing five colours must not out-vote a store listing one when we
     * pick the cheapest — each store gets exactly one say.
     */
    @Test
    void oneStoreWithManyVariantsDoesNotSkewCheapestStore() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.IRSHAD, "iPhone 16 128 GB Black", "2200"),
                offer(StoreName.IRSHAD, "iPhone 16 128 GB White", "2200"),
                offer(StoreName.IRSHAD, "iPhone 16 128 GB Pink", "2200"),
                offer(StoreName.SOLITON, "iPhone 16 128GB BLACK", "2100")
        );

        ProductComparison g = matcher.groupOffers(offers).get(0);

        assertThat(g.getStores()).hasSize(2);
        assertThat(g.getCheapestStore()).isEqualTo(StoreName.SOLITON);
        assertThat(g.getMaxSaving()).isEqualByComparingTo("100");
    }

    @Test
    void offersWithoutPricesStillProduceAUsableRow() {
        // Soliton contributes a title-only offer when enrichment fails.
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "iPhone 16 128 GB Black", "2099"),
                offer(StoreName.SOLITON, "iPhone 16 128GB BLACK", null)
        );

        ProductComparison g = matcher.groupOffers(offers).get(0);

        assertThat(g.getStores()).hasSize(2);
        assertThat(g.getCheapestStore()).isEqualTo(StoreName.KONTAKT_HOME);
        // The price-less store sorts last rather than being treated as free.
        assertThat(g.getStores().get(0).getStore()).isEqualTo(StoreName.KONTAKT_HOME);
        assertThat(summaryFor(g, StoreName.SOLITON).getPrice()).isNull();
    }
}
