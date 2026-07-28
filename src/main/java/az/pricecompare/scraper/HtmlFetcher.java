package az.pricecompare.scraper;

import az.pricecompare.config.ScraperProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single door out to the stores. Everything a polite, resilient HTTP client
 * needs lives here so scrapers stay declarative:
 *
 * <ul>
 *   <li>a browser-like header set — all three sites 403 a bare Jsoup request,</li>
 *   <li>per-host pacing, so a fan-out search doesn't arrive as a burst,</li>
 *   <li>bounded retries for the transient Cloudflare 403s Kontakt throws.</li>
 * </ul>
 *
 * Pacing is per host and process-wide: two concurrent searches for different
 * queries still queue behind each other for a given store. That's intentional.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HtmlFetcher {

    private final ScraperProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** host -> monitor guarding the last-request timestamp for that host. */
    private final Map<String, HostPacer> pacers = new ConcurrentHashMap<>();

    /** Fetch and parse a URL into a Jsoup {@link Document}. */
    public Document getHtml(String url, long minIntervalMs) throws IOException {
        return execute(url, minIntervalMs, Connection.Response::parse);
    }

    /** Fetch a URL and parse the body as JSON. */
    public JsonNode getJson(String url, long minIntervalMs) throws IOException {
        return execute(url, minIntervalMs, r -> objectMapper.readTree(r.body()));
    }

    private <T> T execute(String url, long minIntervalMs, ResponseMapper<T> mapper) throws IOException {
        IOException last = null;
        int attempts = Math.max(1, props.getMaxRetries() + 1);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            pacer(url).await(minIntervalMs);
            try {
                Connection.Response response = Jsoup.connect(url)
                        .userAgent(props.getUserAgent())
                        .timeout(props.getTimeoutMs())
                        .followRedirects(true)
                        .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "az,ru;q=0.9,en;q=0.8")
                        .header("Cache-Control", "no-cache")
                        // Jsoup caps bodies at 1MB by default; Kontakt product
                        // pages are ~1.3MB and would silently truncate.
                        .maxBodySize(8 * 1024 * 1024)
                        .ignoreContentType(true)
                        .execute();
                return mapper.map(response);
            } catch (IOException e) {
                last = e;
                if (attempt < attempts) {
                    // Cloudflare's interstitial clears on its own more often than
                    // not; a short backoff is cheaper than failing the store.
                    long backoff = 300L * attempt;
                    log.debug("Attempt {}/{} failed for {} ({}); retrying in {}ms",
                            attempt, attempts, url, e.getMessage(), backoff);
                    sleep(backoff);
                }
            }
        }
        throw last != null ? last : new IOException("Fetch failed: " + url);
    }

    private HostPacer pacer(String url) {
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (Exception e) {
            host = url;
        }
        return pacers.computeIfAbsent(host == null ? url : host, h -> new HostPacer());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ResponseMapper<T> {
        T map(Connection.Response response) throws IOException;
    }

    /** Serializes requests to one host, holding each caller until its slot is due. */
    private static final class HostPacer {
        private long nextAllowedAt;

        synchronized void await(long minIntervalMs) {
            if (minIntervalMs <= 0) {
                return;
            }
            long now = System.currentTimeMillis();
            long waitFor = nextAllowedAt - now;
            if (waitFor > 0) {
                sleep(waitFor);
                now = System.currentTimeMillis();
            }
            nextAllowedAt = now + minIntervalMs;
        }
    }
}
