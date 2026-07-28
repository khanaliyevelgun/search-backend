package az.pricecompare.web;

import az.pricecompare.persistence.SearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Analytics surface: what people are searching for.
 *
 * Gated behind a shared token from {@code admin.api-key}. This is a deliberately
 * modest guard — it keeps search analytics off the open internet without dragging
 * Spring Security into a service that has no user accounts. If real accounts ever
 * arrive, replace this with proper authentication rather than extending it.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SearchHistoryRepository historyRepository;

    @Value("${admin.api-key:}")
    private String configuredKey;

    @GetMapping("/popular")
    public List<Map<String, Object>> popular(
            @RequestHeader(value = "X-Admin-Key", required = false) String providedKey,
            @RequestParam(defaultValue = "50") int limit) {

        requireAuth(providedKey);

        int capped = Math.max(1, Math.min(limit, 500));
        return historyRepository.findPopularQueries(PageRequest.of(0, capped)).stream()
                .map(p -> Map.<String, Object>of(
                        "query", p.getQuery() == null ? "" : p.getQuery(),
                        "count", p.getCnt()))
                .toList();
    }

    private void requireAuth(String providedKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            // Failing closed: an unset key must not mean "open to everyone".
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Admin API is disabled (set admin.api-key to enable it)");
        }
        if (!configuredKey.equals(providedKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin key");
        }
    }
}
