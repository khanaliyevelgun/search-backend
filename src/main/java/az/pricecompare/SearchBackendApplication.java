package az.pricecompare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the price-comparison aggregator.
 *
 * The app takes a user's product search (e.g. "iphone 16 pro max"), scrapes
 * several Azerbaijani electronics stores in parallel, normalizes and fuzzy-matches
 * the results so the same phone lines up across stores, and returns a comparison.
 */
@SpringBootApplication
@EnableCaching   // enables the Caffeine-backed search-result cache
@EnableAsync     // enables parallel scraping across stores
public class SearchBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchBackendApplication.class, args);
    }
}
