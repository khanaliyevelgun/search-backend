package az.pricecompare.web;

import az.pricecompare.domain.SearchResponse;
import az.pricecompare.service.SearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single search endpoint the frontend calls.
 *
 * GET /api/search?q=iphone 16 pro max
 *
 * Returns a {@link SearchResponse} with product groups compared across stores.
 * CORS is configured centrally in {@link WebConfig}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam("q")
            @NotBlank(message = "Search query must not be blank")
            @Size(min = 2, max = 200, message = "Search query must be 2–200 characters")
            String q) {
        return searchService.search(q);
    }
}
