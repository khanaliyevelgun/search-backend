package az.pricecompare.scraper;

import az.pricecompare.domain.StoreName;
import lombok.Getter;

/**
 * Thrown when a single store's scrape fails. Carries the store so the
 * orchestrator can report exactly which store failed while others succeed.
 */
@Getter
public class StoreScrapeException extends RuntimeException {

    private final StoreName store;

    public StoreScrapeException(StoreName store, String message, Throwable cause) {
        super(message, cause);
        this.store = store;
    }
}
