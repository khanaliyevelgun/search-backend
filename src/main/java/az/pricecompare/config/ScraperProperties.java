package az.pricecompare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds the {@code scraper.*} section of application.yml.
 *
 * Every URL a scraper hits comes from here rather than being hard-coded, because
 * these stores change their endpoints without notice and swapping a YAML string
 * beats a redeploy.
 */
@Component
@ConfigurationProperties(prefix = "scraper")
@Data
public class ScraperProperties {

    /** Per-request socket timeout when fetching a store page (ms). */
    private int timeoutMs = 8000;

    /** Hard ceiling on one store's whole contribution to a search (ms). */
    private int storeBudgetMs = 12000;

    private String userAgent = "Mozilla/5.0";

    /** How many raw candidates to pull from each store before relevance filtering. */
    private int maxResultsPerStore = 24;

    /** How many surviving offers per store we spend detail-page fetches on. */
    private int maxEnrichedPerStore = 6;

    /** Transient-failure retries per request (0 = no retry). */
    private int maxRetries = 2;

    /** Keyed by store slug: "kontakt", "irshad", "soliton". */
    private Map<String, StoreConfig> stores = new HashMap<>();

    @Data
    public static class StoreConfig {
        private boolean enabled = true;

        private String baseUrl;

        /**
         * The store's real search endpoint, with {@code {query}} as the placeholder
         * for the URL-encoded search text.
         */
        private String searchUrl;

        /**
         * Minimum gap between two requests to this host, in ms. These are small
         * shops; hammering them is both rude and the fastest way to get banned.
         */
        private long minRequestIntervalMs = 400;

        /** Whether to spend extra requests fetching product detail pages. */
        private boolean enrich = true;
    }

    public StoreConfig store(String key) {
        return stores.get(key);
    }
}
