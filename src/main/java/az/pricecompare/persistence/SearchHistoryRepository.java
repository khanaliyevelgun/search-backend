package az.pricecompare.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistoryEntity, Long> {

    /** Most popular normalized queries, for analytics / cache warming. */
    @Query("""
            select h.normalizedQuery as query, count(h) as cnt
            from SearchHistoryEntity h
            group by h.normalizedQuery
            order by count(h) desc
            """)
    List<PopularQuery> findPopularQueries();

    interface PopularQuery {
        String getQuery();
        long getCnt();
    }
}
