package az.pricecompare.matching;

import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the core value of the product: that the same phone from different
 * stores, titled differently, gets grouped into one comparison — and that
 * different storage variants stay separate.
 */
class ProductMatcherTest {

    private final ProductMatcher matcher = new ProductMatcher(new ProductNormalizer());

    private StoreOffer offer(StoreName store, String title, String price) {
        return StoreOffer.builder()
                .store(store)
                .rawTitle(title)
                .price(new BigDecimal(price))
                .currency("AZN")
                .build();
    }

    @Test
    void groupsSamePhoneAcrossStoresDespiteDifferentTitles() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "Apple iPhone 16 Pro Max 256GB Natural Titanium", "2000"),
                offer(StoreName.IRSHAD, "iPhone 16 Pro Max (256 GB) - Titanium", "2200"),
                offer(StoreName.SOLITON, "Smartfon Apple iPhone 16 Pro Max 256Gb Qara", "2100")
        );

        List<ProductComparison> groups = matcher.groupOffers(offers);

        assertThat(groups).hasSize(1);
        ProductComparison g = groups.get(0);
        assertThat(g.getOffers()).hasSize(3);
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

        List<ProductComparison> groups = matcher.groupOffers(offers);

        assertThat(groups).hasSize(2);
    }

    @Test
    void keepsDifferentModelsSeparate() {
        List<StoreOffer> offers = List.of(
                offer(StoreName.KONTAKT_HOME, "Apple iPhone 16 Pro Max 256GB", "2000"),
                offer(StoreName.IRSHAD, "Apple iPhone 16 256GB", "1400"),
                offer(StoreName.SOLITON, "Samsung Galaxy S24 Ultra 256GB", "1800")
        );

        List<ProductComparison> groups = matcher.groupOffers(offers);

        // Three distinct products.
        assertThat(groups).hasSize(3);
    }
}
