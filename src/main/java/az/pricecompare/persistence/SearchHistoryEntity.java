package az.pricecompare.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A record of each search performed, useful for analytics (popular queries),
 * rate-limiting, and warming the cache for frequent searches later.
 */
@Entity
@Table(name = "search_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String query;

    /** Normalized/lowercased query, for grouping "iPhone 16" and "iphone 16". */
    @Column(name = "normalized_query", length = 300)
    private String normalizedQuery;

    /** How many product groups were returned. */
    @Column(name = "result_count")
    private int resultCount;

    /** Whether this particular search was served from cache. */
    @Column(name = "served_from_cache")
    private boolean servedFromCache;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
