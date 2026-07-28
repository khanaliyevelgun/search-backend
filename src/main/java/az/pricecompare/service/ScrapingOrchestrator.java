package az.pricecompare.service;

import az.pricecompare.domain.SearchResponse;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.StoreScrapeException;
import az.pricecompare.scraper.StoreScraper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Runs every enabled {@link StoreScraper} in parallel for a query, collecting
 * both the offers that succeeded and the per-store errors, so the response can
 * tell the frontend exactly which stores contributed and which failed.
 */
@Service
@Slf4j
public class ScrapingOrchestrator {

    private final List<StoreScraper> scrapers;   // Spring injects all implementations
    private final Executor executor;

    // Explicit constructor so @Qualifier is honored for the executor bean.
    public ScrapingOrchestrator(List<StoreScraper> scrapers,
                                @Qualifier("scraperExecutor") Executor executor) {
        this.scrapers = scrapers;
        this.executor = executor;
    }

    /** Result bundle of a fan-out scrape. */
    public record ScrapeResult(List<StoreOffer> offers,
                               List<StoreName> succeeded,
                               List<SearchResponse.StoreErrorInfo> errors) {}

    public ScrapeResult scrapeAll(String query) {
        List<CompletableFuture<StoreOutcome>> futures = new ArrayList<>();

        for (StoreScraper scraper : scrapers) {
            if (!scraper.isEnabled()) {
                continue;
            }
            CompletableFuture<StoreOutcome> f = CompletableFuture.supplyAsync(() -> {
                try {
                    List<StoreOffer> offers = scraper.search(query);
                    return StoreOutcome.ok(scraper.storeName(), offers);
                } catch (StoreScrapeException e) {
                    return StoreOutcome.fail(e.getStore(), e.getMessage());
                } catch (Exception e) {
                    return StoreOutcome.fail(scraper.storeName(), e.toString());
                }
            }, executor);
            futures.add(f);
        }

        // Wait for all stores to finish (each already bounded by its own timeout).
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<StoreOffer> allOffers = new ArrayList<>();
        List<StoreName> succeeded = new ArrayList<>();
        List<SearchResponse.StoreErrorInfo> errors = new ArrayList<>();

        for (CompletableFuture<StoreOutcome> f : futures) {
            StoreOutcome outcome = f.join();
            if (outcome.error() == null) {
                succeeded.add(outcome.store());
                allOffers.addAll(outcome.offers());
            } else {
                errors.add(SearchResponse.StoreErrorInfo.builder()
                        .store(outcome.store())
                        .message(outcome.error())
                        .build());
            }
        }
        return new ScrapeResult(allOffers, succeeded, errors);
    }

    private record StoreOutcome(StoreName store, List<StoreOffer> offers, String error) {
        static StoreOutcome ok(StoreName s, List<StoreOffer> o) {
            return new StoreOutcome(s, o, null);
        }
        static StoreOutcome fail(StoreName s, String err) {
            return new StoreOutcome(s, List.of(), err);
        }
    }
}
