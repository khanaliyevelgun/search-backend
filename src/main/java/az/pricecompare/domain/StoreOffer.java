package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One store's listing for one specific SKU — i.e. a single colour/storage variant.
 *
 * All three stores model colour as a separate product rather than an option on a
 * shared page ("iPhone 16 128 GB Black" and "... White" are distinct listings with
 * distinct URLs and sometimes distinct prices), so one {@code StoreOffer} is one
 * colour. {@link ProductComparison} rolls the variants back up per store.
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

    /** The store's own product code, when it exposes one. Useful for debugging. */
    private String sku;

    /** Current selling price in AZN. Null if the store didn't expose one. */
    private BigDecimal price;

    /** Original/crossed-out price when the item is discounted; else null. */
    private BigDecimal oldPrice;

    @Builder.Default
    private String currency = "AZN";

    /** Whether the store shows the item as in stock. */
    private boolean inStock;

    /** The store's own stock wording, e.g. "Stokda var". */
    private String stockText;

    /**
     * The colour of this specific variant, normalized to English where we
     * recognise it (e.g. "Black"). Null when the title carries no colour.
     */
    private String color;

    /** Image URLs for this variant. */
    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    /** Installment plans this store advertises for this variant. */
    @Builder.Default
    private List<CreditOption> creditOptions = new ArrayList<>();

    @Builder.Default
    private ProductSpecs specs = ProductSpecs.builder().build();

    /** True once the detail page has been fetched and folded in. */
    private boolean enriched;
}
