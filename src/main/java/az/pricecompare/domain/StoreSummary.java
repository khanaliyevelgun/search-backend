package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What one store offers for one product, rolled up across that store's colour
 * variants. This is the row the frontend renders per store in a comparison table:
 * "Kontakt — 2999.99 ₼, Black/White/Natural, from 125 ₼/month".
 *
 * The colour list is the point of the whole feature: comparing
 * {@code colorsAvailable} between two summaries answers "who actually has black?".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreSummary {

    private StoreName store;

    /** Human-readable store name, so the frontend doesn't map enum constants. */
    private String storeDisplayName;

    /** Cheapest variant price at this store. */
    private BigDecimal price;

    /** Highest variant price at this store — equal to {@code price} when uniform. */
    private BigDecimal maxPrice;

    /** Crossed-out price on the cheapest variant, when discounted. */
    private BigDecimal oldPrice;

    /** Link to the cheapest variant's product page. */
    private String productUrl;

    /** Primary image for this store's listing. */
    private String imageUrl;

    /** True when at least one variant is in stock here. */
    private boolean inStock;

    /** Colours this store lists for the product. */
    @Builder.Default
    private List<String> colorsAvailable = new ArrayList<>();

    /** Colours other stores have that this one does not. Computed by the matcher. */
    @Builder.Default
    private List<String> colorsMissing = new ArrayList<>();

    /** Installment plans, longest term first. */
    @Builder.Default
    private List<CreditOption> creditOptions = new ArrayList<>();

    /** Lowest monthly payment across this store's plans — the headline credit number. */
    private BigDecimal lowestMonthlyPayment;

    /** How many distinct variants this store listed. */
    private int variantCount;

    /** Every variant, if the frontend wants to drill in. */
    @Builder.Default
    private List<StoreOffer> offers = new ArrayList<>();
}
