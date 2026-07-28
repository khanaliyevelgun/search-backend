package az.pricecompare.service;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.SearchResponse;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.matching.RelevanceFilter;
import az.pricecompare.scraper.StoreScrapeException;
import az.pricecompare.scraper.StoreScraper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs every enabled {@link StoreScraper} for a query and returns what they found,
 * along with per-store errors so the response can be honest about coverage.
 *
 * Each store runs the same three steps concurrently with the others:
 * <ol>
 *   <li>search the store,</li>
 *   <li>drop irrelevant hits (accessories, other models),</li>
 *   <li>enrich the survivors from their product pages.</li>
 * </ol>
 *
 * Filtering sits between the two fetch phases on purpose: enrichment is the
 * expensive part (one HTTP request per offer), and there's no reason to spend it
 * on a phone case we're about to discard.
 */
@Service
@Slf4j
public class ScrapingOrchestrator {

    private final List<StoreScraper> scrapers;   // Spring injects all implementations
    private final Executor storeExecutor;
    private final Executor enrichExecutor;
    private final RelevanceFilter relevanceFilter;
    private final ScraperProperties props;

    // Explicit constructor so @Qualifier is honored for the executor beans.
    public ScrapingOrchestrator(List<StoreScraper> scrapers,
                                @Qualifier("scraperExecutor") Executor storeExecutor,
                                @Qualifier("enrichExecutor") Executor enrichExecutor,
                                RelevanceFilter relevanceFilter,
                                ScraperProperties props) {
        this.scrapers = scrapers;
        this.storeExecutor = storeExecutor;
        this.enrichExecutor = enrichExecutor;
        this.relevanceFilter = relevanceFilter;
        this.props = props;
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
            futures.add(runStore(scraper, query));
        }

        List<StoreOffer> allOffers = new ArrayList<>();
        List<StoreName> succeeded = new ArrayList<>();
        List<SearchResponse.StoreErrorInfo> errors = new ArrayList<>();

        for (CompletableFuture<StoreOutcome> f : futures) {
            StoreOutcome outcome = f.join();   // never throws: see runStore
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

    /**
     * Run one store's whole pipeline, bounded by a wall-clock budget and
     * guaranteed not to throw.
     *
     * The budget matters because the per-request socket timeout doesn't bound a
     * response that trickles in slowly, and enrichment issues several requests in
     * sequence. Without it one wedged store holds the user's request open forever.
     */
    private CompletableFuture<StoreOutcome> runStore(StoreScraper scraper, String query) {
        StoreName store = scraper.storeName();
        CompletableFuture<StoreOutcome> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                List<StoreOffer> found = scraper.search(query);
                List<StoreOffer> relevant = relevanceFilter.filter(
                        query, found, props.getMaxEnrichedPerStore());
                enrich(scraper, relevant);
                return StoreOutcome.ok(store, relevant);
            }, storeExecutor);
        } catch (Exception e) {
            // supplyAsync throws synchronously if the pool rejects the task, which
            // would otherwise escape scrapeAll and fail the entire search.
            log.warn("{}: could not be scheduled: {}", store, e.toString());
            return CompletableFuture.completedFuture(StoreOutcome.fail(store, "Not scheduled: " + e));
        }

        return future
                .orTimeout(props.getStoreBudgetMs(), TimeUnit.MILLISECONDS)
                .handle((outcome, ex) -> ex == null ? outcome : StoreOutcome.fail(store, describe(ex)));
    }

    /**
     * Fetch product pages for the surviving offers, in parallel within the store.
     * Per-host pacing in the fetcher keeps this from turning into a burst.
     */
    private void enrich(StoreScraper scraper, List<StoreOffer> offers) {
        if (!scraper.isEnrichEnabled() || offers.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (StoreOffer offer : offers) {
            tasks.add(CompletableFuture.runAsync(() -> scraper.enrich(offer), enrichExecutor));
        }
        for (CompletableFuture<Void> t : tasks) {
            try {
                t.join();
            } catch (Exception e) {
                log.debug("{}: enrichment task failed: {}", scraper.storeName(), e.toString());
            }
        }
    }

    private static String describe(Throwable ex) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        if (cause instanceof TimeoutException) {
            return "Store did not respond in time";
        }
        if (cause instanceof StoreScrapeException sse) {
            return sse.getMessage();
        }
        return cause.toString();
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
