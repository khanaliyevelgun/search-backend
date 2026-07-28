package az.pricecompare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds the {@code scraper.*} section of application.yml.
 * Keeps timeouts, User-Agent and per-store toggles configurable without code changes.
 */
@Component
@ConfigurationProperties(prefix = "scraper")
@Data
public class ScraperProperties {

    private int timeoutMs = 8000;
    private String userAgent = "Mozilla/5.0";
    private int maxResultsPerStore = 8;

    /** Keyed by store slug: "kontakt", "irshad", "soliton". */
    private Map<String, StoreConfig> stores = new HashMap<>();

    @Data
    public static class StoreConfig {
        private boolean enabled = true;
        private String baseUrl;
    }
}
