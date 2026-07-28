package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A group of {@link StoreOffer}s that all refer to the same physical product
 * (matched across stores by the fuzzy matcher), plus computed comparison hints
 * the frontend can use directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductComparison {

    /** Normalized canonical name, e.g. "Apple iPhone 16 Pro Max 256GB". */
    private String canonicalName;

    /** The brand, e.g. "Apple", "Samsung". */
    private String brand;

    /** One offer per store for this product. */
    @Builder.Default
    private List<StoreOffer> offers = new ArrayList<>();

    // ---- Computed comparison highlights ----

    /** Lowest price found across all offers. */
    private BigDecimal lowestPrice;

    /** Highest price found across all offers. */
    private BigDecimal highestPrice;

    /** Which store has the lowest price. */
    private StoreName cheapestStore;

    /** highestPrice - lowestPrice, so the frontend can show potential savings. */
    private BigDecimal maxSaving;

    /** Union of all colors any store offers for this product. */
    @Builder.Default
    private List<String> allColorsSeen = new ArrayList<>();
}
