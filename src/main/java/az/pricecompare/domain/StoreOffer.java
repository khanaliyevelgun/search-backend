package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One store's offer for a product: what a single store sells this exact item for,
 * with its price, colors, images, specs and credit plans.
 *
 * Multiple {@code StoreOffer}s for the same physical phone are grouped together
 * into a {@link ProductComparison} so the frontend can compare them side by side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOffer {

    private StoreName store;

    /** The product title exactly as the store listed it. */
    private String rawTitle;

    /** Direct link to the product page on the store's site. */
    private String productUrl;

    /** Current selling price in AZN. Null if the store didn't expose one. */
    private BigDecimal price;

    /** Original/crossed-out price when the item is discounted; else null. */
    private BigDecimal oldPrice;

    private String currency;

    /** Whether the store shows the item as in stock. */
    private boolean inStock;

    /** Colors this store lists as available for the product. */
    @Builder.Default
    private List<String> availableColors = new ArrayList<>();

    /** Image URLs from the store's product page. */
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /** Installment/credit plans this store advertises. */
    @Builder.Default
    private List<CreditOption> creditOptions = new ArrayList<>();

    private ProductSpecs specs;
}
