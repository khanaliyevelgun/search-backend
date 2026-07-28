package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level payload returned to the frontend for a search.
 * Wraps the list of matched product comparisons plus metadata about the search
 * itself (which stores responded, whether the result came from cache, etc.).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /** The original query the user typed. */
    private String query;

    /** Product groups, each comparing the same phone across stores. */
    @Builder.Default
    private List<ProductComparison> results = new ArrayList<>();

    /** Stores that were successfully scraped for this search. */
    @Builder.Default
    private List<StoreName> storesQueried = new ArrayList<>();

    /** Stores that failed (timeout, blocked, parse error) — surfaced, not hidden. */
    @Builder.Default
    private List<StoreErrorInfo> storeErrors = new ArrayList<>();

    /** True when this whole response was served from cache. */
    private boolean fromCache;

    /** When the underlying data was fetched. */
    private Instant fetchedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreErrorInfo {
        private StoreName store;
        private String message;
    }
}
