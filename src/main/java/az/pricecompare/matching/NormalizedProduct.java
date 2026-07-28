package az.pricecompare.matching;

/**
 * The normalized view of a single offer, produced by {@link ProductNormalizer}
 * and consumed by {@link ProductMatcher} to group offers across stores.
 *
 * @param brand         detected brand, lowercased (e.g. "apple"), or "unknown"
 * @param signature     cleaned model token string used for fuzzy comparison
 * @param storage       normalized storage like "256GB", or null
 * @param ram           normalized RAM like "8GB", or null
 * @param color         canonical colour like "Black Titanium", or null
 * @param canonicalName human-friendly display name for the matched product
 */
public record NormalizedProduct(
        String brand,
        String signature,
        String storage,
        String ram,
        String color,
        String canonicalName
) {
    public boolean brandKnown() {
        return brand != null && !"unknown".equals(brand);
    }
}
