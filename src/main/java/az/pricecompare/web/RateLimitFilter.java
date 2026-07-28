package az.pricecompare.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A per-client request cap on the search endpoint.
 *
 * One uncached search costs us up to twenty requests spread across three stores,
 * so an unthrottled public endpoint is not merely a load problem for us — it's a
 * fast route to having our server IP blocked by every store we depend on. The
 * limit protects them as much as us.
 *
 * A fixed window rather than a token bucket: less precise at window edges, but
 * small enough to read in one sitting and adequate for the traffic this serves.
 */
@Component
@Order(1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final Duration window;
    private final Cache<String, AtomicInteger> counters;

    public RateLimitFilter(
            @Value("${ratelimit.requests-per-minute:20}") int maxRequests,
            @Value("${ratelimit.window-seconds:60}") long windowSeconds) {
        this.maxRequests = maxRequests;
        this.window = Duration.ofSeconds(windowSeconds);
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(this.window)
                .maximumSize(50_000)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the expensive endpoint needs protecting.
        return !request.getRequestURI().startsWith("/api/search");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String client = clientKey(request);
        AtomicInteger counter = counters.get(client, k -> new AtomicInteger());
        int used = counter.incrementAndGet();

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - used)));

        if (used > maxRequests) {
            log.debug("Rate limit exceeded for {} ({} requests)", client, used);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(window.toSeconds()));
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests",\
                    "message":"Too many searches. Please wait a moment and try again."}""");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Prefer the proxy-forwarded address when present — behind a load balancer
     * every request otherwise appears to come from the same host and one user
     * would exhaust everyone's quota.
     */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
