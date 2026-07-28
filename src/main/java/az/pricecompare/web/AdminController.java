package az.pricecompare.web;

import az.pricecompare.persistence.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Small analytics surface. In production, protect this behind auth — it's open
 * here purely for development visibility into what people are searching.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SearchHistoryRepository historyRepository;

    @GetMapping("/popular")
    public List<Map<String, Object>> popular() {
        return historyRepository.findPopularQueries().stream()
                .map(p -> Map.<String, Object>of(
                        "query", p.getQuery() == null ? "" : p.getQuery(),
                        "count", p.getCnt()))
                .toList();
    }
}
