package az.pricecompare.service;

import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.SearchResponse;
import az.pricecompare.matching.ProductMatcher;
import az.pricecompare.persistence.SearchHistoryEntity;
import az.pricecompare.persistence.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Top-level search use case: cache-first, then scrape + filter + match.
 *
 * The cache is not a nicety. A cold search costs three store searches plus up to
 * eighteen product-page fetches; serving repeat queries from memory is what keeps
 * the stores tolerant of us and the endpoint responsive.
 *
 * We manage the cache explicitly (rather than via {@code @Cacheable}) so we can
 * honestly report {@code fromCache} to the frontend — a plain {@code @Cacheable}
 * would return a stored object whose flag was captured on the miss path, so the
 * client could never distinguish a hit from a miss.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private static final String CACHE_NAME = "searchResults";

    private final ScrapingOrchestrator orchestrator;
    private final ProductMatcher matcher;
    private final SearchHistoryRepository historyRepository;
    private final CacheManager cacheManager;

    public SearchResponse search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String normalized = normalize(query);

        SearchResponse response = fromCacheOrScrape(normalized, query);
        recordHistory(query, normalized, response);
        return response;
    }

    private SearchResponse fromCacheOrScrape(String normalized, String originalQuery) {
        Cache cache = cacheManager.getCache(CACHE_NAME);

        // 1) Cache hit — return a copy flagged as cached.
        if (cache != null) {
            SearchResponse cached = cache.get(normalized, SearchResponse.class);
            if (cached != null) {
                log.info("Cache hit for '{}'", normalized);
                return cached.toBuilder().fromCache(true).tookMs(0).build();
            }
        }

        // 2) Cache miss — scrape, match, store, return fresh.
        log.info("Cache miss — scraping stores for '{}'", normalized);
        long startedAt = System.currentTimeMillis();

        ScrapingOrchestrator.ScrapeResult result = orchestrator.scrapeAll(originalQuery);
        List<ProductComparison> comparisons = matcher.groupOffers(result.offers());

        SearchResponse fresh = SearchResponse.builder()
                .query(originalQuery)
                .results(comparisons)
                .storesQueried(result.succeeded())
                .storeErrors(result.errors())
                .fromCache(false)
                .fetchedAt(Instant.now())
                .tookMs(System.currentTimeMillis() - startedAt)
                .build();

        // Only cache responses that actually produced results, so a transient
        // total failure (all stores down) isn't cached for the full TTL.
        if (cache != null && !comparisons.isEmpty()) {
            cache.put(normalized, fresh);
        }

        log.info("Search '{}' -> {} products from {} stores in {}ms",
                originalQuery, comparisons.size(), result.succeeded().size(), fresh.getTookMs());
        return fresh;
    }

    private void recordHistory(String query, String normalized, SearchResponse response) {
        try {
            historyRepository.save(SearchHistoryEntity.builder()
                    .query(query)
                    .normalizedQuery(normalized)
                    .resultCount(response.getResults() == null ? 0 : response.getResults().size())
                    .servedFromCache(response.isFromCache())
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            // Analytics must never break the actual search.
            log.warn("Failed to record search history: {}", e.toString());
        }
    }

    private String normalize(String query) {
        return query.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
