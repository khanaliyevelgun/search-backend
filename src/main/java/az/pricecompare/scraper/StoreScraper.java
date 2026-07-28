package az.pricecompare.scraper;

import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;

import java.util.List;

/**
 * Contract every store integration implements. One implementation per store.
 *
 * Scraping happens in two phases because the stores' search results are thin:
 * Kontakt's search API has no installment plans, Irshad's has no specs, and
 * Soliton's has no price at all. Phase one gets candidates cheaply, the
 * orchestrator filters them for relevance, and only the survivors cost us a
 * detail-page fetch in phase two.
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

    /** Whether detail-page enrichment is enabled for this store. */
    boolean isEnrichEnabled();

    /**
     * Phase one: run the store's own search and return candidate offers.
     *
     * @param query the user's raw search text, e.g. "iphone 16 pro max"
     * @return offers found (possibly empty); never null
     */
    List<StoreOffer> search(String query);

    /**
     * Phase two: fetch the offer's product page and fold in whatever the search
     * results couldn't give us — specs, installment plans, extra images, and for
     * Soliton the price itself.
     *
     * Mutates and returns the same offer. Failures are swallowed and logged: an
     * offer without specs is still worth showing, so enrichment is best-effort.
     */
    StoreOffer enrich(StoreOffer offer);
}
