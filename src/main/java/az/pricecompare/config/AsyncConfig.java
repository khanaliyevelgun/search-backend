package az.pricecompare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread pools for scraping. Scraping is I/O-bound (waiting on remote HTTP), so
 * generous pools are fine — total search latency is the slowest single store
 * rather than the sum of all of them.
 *
 * There are deliberately <em>two</em> pools. A store task waits on its own
 * enrichment tasks, so if both ran on one pool a burst of concurrent searches
 * could fill it with parents that are all blocked waiting for children that can
 * never be scheduled. Separate pools make that deadlock structurally impossible.
 */
@Configuration
public class AsyncConfig {

    /** One task per store per search. */
    @Bean(name = "scraperExecutor")
    public Executor scraperExecutor() {
        return pool("scraper-", 6, 12, 50);
    }

    /** One task per product detail page. Fans out wider than the store pool. */
    @Bean(name = "enrichExecutor")
    public Executor enrichExecutor() {
        return pool("enrich-", 12, 32, 200);
    }

    private Executor pool(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(max);
        executor.setQueueCapacity(queue);
        executor.setThreadNamePrefix(prefix);
        // Under overload, run the work on the calling thread rather than throwing.
        // Slower beats a rejected task turning into a failed search.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
