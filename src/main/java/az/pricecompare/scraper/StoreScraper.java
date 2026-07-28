package az.pricecompare.scraper;

import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;

import java.util.List;

/**
 * Contract every store integration implements. One implementation per store.
 *
 * A scraper is responsible for:
 *   1. running the store's own search for the user's query,
 *   2. parsing the search-results page into a list of {@link StoreOffer}s,
 *   3. (optionally) enriching each offer with details from its product page
 *      (colors, images, specs, credit plans).
 *
 * Implementations must be resilient: a parse failure for one product should not
 * abort the whole store. Throwing from {@link #search} means the store is
 * reported as failed for that query, but other stores still return.
 */
public interface StoreScraper {

    /** Which store this scraper handles. */
    StoreName storeName();

    /** Whether this store is enabled in config. */
    boolean isEnabled();

    /**
     * Search the store and return matched offers.
     *
     * @param query the user's raw search text, e.g. "iphone 16 pro max"
     * @return offers found (possibly empty); never null
     */
    List<StoreOffer> search(String query);
}
