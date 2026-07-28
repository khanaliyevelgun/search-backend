package az.pricecompare.matching;

import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These cases are taken from what the live stores actually return. Both İrşad and
 * Kontakt rank accessories above the phone itself for "iphone 16 pro max", so
 * without this filter the comparison reports a 2.99 AZN case as the cheapest
 * iPhone — the single worst failure mode this service has.
 */
class RelevanceFilterTest {

    private final RelevanceFilter filter = new RelevanceFilter();

    private StoreOffer offer(String title, String price) {
        return StoreOffer.builder()
                .store(StoreName.IRSHAD)
                .rawTitle(title)
                .price(new BigDecimal(price))
                .build();
    }

    @Test
    void dropsAccessoriesWhenTheUserAskedForAPhone() {
        List<StoreOffer> raw = List.of(
                offer("Iphone 16 Pro Max case Silicone Hard Clear", "2.99"),
                offer("Qoruyucu örtük Apple iPhone 16 Pro Max Clear Case W/MagSafe", "79.99"),
                offer("iPhone 16 Pro Max 256 GB Black Titanium", "2999.99")
        );

        List<StoreOffer> kept = filter.filter("iphone 16 pro max", raw, 10);

        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).getPrice()).isEqualByComparingTo("2999.99");
    }

    @Test
    void dropsDifferentModelsThatShareWords() {
        List<StoreOffer> raw = List.of(
                offer("iPhone 16 128 GB Black", "2109.99"),
                offer("iPhone 16 Pro Max 256 GB Black Titanium", "2999.99")
        );

        // "pro max" is not in the first title, so it is a different product.
        List<StoreOffer> kept = filter.filter("iphone 16 pro max", raw, 10);

        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).getRawTitle()).contains("Pro Max");
    }

    @Test
    void keepsAccessoriesWhenTheUserActuallyWantsOne() {
        List<StoreOffer> raw = List.of(
                offer("Iphone 16 Pro Max case Silicone Hard Clear", "2.99"),
                offer("iPhone 16 Pro Max 256 GB Black Titanium", "2999.99")
        );

        List<StoreOffer> kept = filter.filter("iphone 16 pro max case", raw, 10);

        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).getRawTitle()).containsIgnoringCase("case");
    }

    @Test
    void ranksTighterTitlesFirst() {
        List<StoreOffer> raw = List.of(
                offer("iPhone 16 Pro Max 256 GB Black Titanium with extra bundle gift set", "3100"),
                offer("iPhone 16 Pro Max 256 GB Black Titanium", "2999.99")
        );

        List<StoreOffer> kept = filter.filter("iphone 16 pro max", raw, 10);

        assertThat(kept).hasSize(2);
        assertThat(kept.get(0).getRawTitle()).isEqualTo("iPhone 16 Pro Max 256 GB Black Titanium");
    }

    @Test
    void respectsTheLimit() {
        List<StoreOffer> raw = List.of(
                offer("iPhone 16 128 GB Black", "2100"),
                offer("iPhone 16 128 GB White", "2100"),
                offer("iPhone 16 128 GB Pink", "2100")
        );

        assertThat(filter.filter("iphone 16", raw, 2)).hasSize(2);
    }

    @Test
    void matchesAreCaseAndPunctuationInsensitive() {
        List<StoreOffer> raw = List.of(offer("iPHONE 16, 128GB — BLACK", "2100"));

        assertThat(filter.filter("iphone 16", raw, 10)).hasSize(1);
    }
}
