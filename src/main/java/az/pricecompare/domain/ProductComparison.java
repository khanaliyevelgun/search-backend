package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One physical product (a model at a given storage size) as sold across stores,
 * plus the comparison the frontend would otherwise have to compute itself.
 *
 * Colour is deliberately <em>not</em> part of the identity: the whole point is to
 * show that Kontakt has this phone in black and Irshad doesn't, which is only
 * expressible if both stores' colour variants live in the same comparison.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductComparison {

    /** Normalized display name, e.g. "Apple iPhone 16 Pro Max 256GB". */
    private String canonicalName;

    private String brand;

    /** Model without brand or storage, e.g. "iphone 16 pro max". */
    private String model;

    /** Storage size this comparison is for, e.g. "256GB". Null when unknown. */
    private String storage;

    /** One entry per store, each rolled up across that store's colour variants. */
    @Builder.Default
    private List<StoreSummary> stores = new ArrayList<>();

    // ---- Computed comparison highlights ----

    private BigDecimal lowestPrice;

    private BigDecimal highestPrice;

    private StoreName cheapestStore;

    /** highestPrice - lowestPrice: what the user saves by picking the right store. */
    private BigDecimal maxSaving;

    /** Union of every colour any store carries. */
    @Builder.Default
    private List<String> allColorsSeen = new ArrayList<>();

    /** Total variants across all stores; a rough confidence signal for the match. */
    private int totalOffers;
}
