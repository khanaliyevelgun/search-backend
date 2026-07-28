package az.pricecompare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pool used to scrape all stores concurrently. Scraping is I/O-bound
 * (waiting on remote HTTP), so a modest pool lets all stores run at once and the
 * total search latency is roughly the slowest single store, not the sum.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "scraperExecutor")
    public Executor scraperExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("scraper-");
        executor.initialize();
        return executor;
    }
}
