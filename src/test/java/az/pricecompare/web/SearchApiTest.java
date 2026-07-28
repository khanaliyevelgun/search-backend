package az.pricecompare.web;

import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.SearchResponse;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreSummary;
import az.pricecompare.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract of the HTTP surface the frontend consumes: response shape, input
 * validation, admin protection and rate limiting.
 *
 * The scrapers are mocked out — this is about the API, not the stores.
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ratelimit.requests-per-minute=3",
        "admin.api-key=test-secret"
})
class SearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    private SearchResponse sample() {
        StoreSummary kontakt = StoreSummary.builder()
                .store(StoreName.KONTAKT_HOME)
                .storeDisplayName(StoreName.KONTAKT_HOME.getDisplayName())
                .price(new BigDecimal("2999.99"))
                .colorsAvailable(List.of("Black Titanium"))
                .colorsMissing(List.of("White Titanium"))
                .build();

        return SearchResponse.builder()
                .query("iphone 16 pro max")
                .results(List.of(ProductComparison.builder()
                        .canonicalName("Apple Iphone 16 Pro Max 256GB")
                        .brand("apple")
                        .stores(List.of(kontakt))
                        .lowestPrice(new BigDecimal("2999.99"))
                        .cheapestStore(StoreName.KONTAKT_HOME)
                        .allColorsSeen(List.of("Black Titanium", "White Titanium"))
                        .build()))
                .storesQueried(List.of(StoreName.KONTAKT_HOME))
                .fetchedAt(Instant.now())
                .build();
    }

    @Test
    void returnsComparisonsInTheShapeTheFrontendExpects() throws Exception {
        when(searchService.search(anyString())).thenReturn(sample());

        mockMvc.perform(get("/api/search").param("q", "iphone 16 pro max"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("iphone 16 pro max"))
                .andExpect(jsonPath("$.results[0].canonicalName").value("Apple Iphone 16 Pro Max 256GB"))
                .andExpect(jsonPath("$.results[0].cheapestStore").value("KONTAKT_HOME"))
                .andExpect(jsonPath("$.results[0].lowestPrice").value(2999.99))
                .andExpect(jsonPath("$.results[0].stores[0].storeDisplayName").value("Kontakt Home"))
                .andExpect(jsonPath("$.results[0].stores[0].colorsAvailable[0]").value("Black Titanium"))
                .andExpect(jsonPath("$.results[0].stores[0].colorsMissing[0]").value("White Titanium"))
                .andExpect(jsonPath("$.storesQueried[0]").value("KONTAKT_HOME"));
    }

    @Test
    void rejectsAQueryThatIsTooShort() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsAMissingQuery() throws Exception {
        mockMvc.perform(get("/api/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rateLimitsRepeatedSearches() throws Exception {
        when(searchService.search(anyString())).thenReturn(sample());

        // Configured to 3 per minute for this test.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/search").param("q", "iphone 16").with(from("10.1.2.3")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/search").param("q", "iphone 16").with(from("10.1.2.3")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void oneClientsQuotaDoesNotExhaustAnothers() throws Exception {
        when(searchService.search(anyString())).thenReturn(sample());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/search").param("q", "x1").with(from("10.9.9.9")))
                    .andExpect(status().isOk());
        }
        // A different forwarded client still gets its own allowance.
        mockMvc.perform(get("/api/search").param("q", "x1").with(from("10.8.8.8")))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointRequiresTheKey() throws Exception {
        mockMvc.perform(get("/api/admin/popular"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/popular").header("X-Admin-Key", "wrong"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/popular").header("X-Admin-Key", "test-secret"))
                .andExpect(status().isOk());
    }

    /** Sets X-Forwarded-For so each test case gets an independent rate-limit bucket. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String ip) {
        return request -> {
            request.addHeader("X-Forwarded-For", ip);
            return request;
        };
    }
}
