package az.pricecompare.scraper.impl;

/**
 * A bundle of CSS selectors for one store's search-results page. Keeping these
 * in one small value object means adapting to a site redesign is a matter of
 * editing selector strings, not logic.
 */
public record SelectorSet(
        String productCard,
        String title,
        String link,
        String price,
        String oldPrice,
        String image
) {}
