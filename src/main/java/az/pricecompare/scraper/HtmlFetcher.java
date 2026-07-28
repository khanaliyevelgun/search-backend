package az.pricecompare.scraper;

import az.pricecompare.config.ScraperProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Thin wrapper around Jsoup that applies our configured User-Agent and timeout,
 * so individual scrapers don't repeat that boilerplate. Centralizing it also
 * makes it easy to later add retries, proxy rotation, or rate limiting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HtmlFetcher {

    private final ScraperProperties props;

    /**
     * Fetch and parse a URL into a Jsoup {@link Document}.
     *
     * @throws IOException if the request fails or times out; callers decide
     *                     whether to treat this as a per-store failure.
     */
    public Document get(String url) throws IOException {
        log.debug("Fetching {}", url);
        return Jsoup.connect(url)
                .userAgent(props.getUserAgent())
                .timeout(props.getTimeoutMs())
                .followRedirects(true)
                // Some stores return 403 for unknown accept headers; be explicit.
                .header("Accept-Language", "az,ru;q=0.9,en;q=0.8")
                .ignoreHttpErrors(false)
                .get();
    }
}
