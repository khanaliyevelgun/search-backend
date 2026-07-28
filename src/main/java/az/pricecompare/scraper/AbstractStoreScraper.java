package az.pricecompare.scraper;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.StoreOffer;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared scaffolding for store scrapers: config lookup, URL building, and the
 * error/enabled handling that would otherwise be copy-pasted three times.
 *
 * Parsing is deliberately <em>not</em> shared. An earlier version drove all three
 * stores through one selector-set abstraction, but the stores turned out to have
 * nothing in common — one returns JSON, one an HTML fragment, one a page with no
 * prices — so each subclass owns its own parsing and this class stays thin.
 */
@Slf4j
public abstract class AbstractStoreScraper implements StoreScraper {

    protected final HtmlFetcher fetcher;
    protected final ScraperProperties props;

    protected AbstractStoreScraper(HtmlFetcher fetcher, ScraperProperties props) {
        this.fetcher = fetcher;
        this.props = props;
    }

    /** Phase-one implementation, already guarded against exceptions by {@link #search}. */
    protected abstract List<StoreOffer> doSearch(String query) throws Exception;

    /** Phase-two implementation, already guarded by {@link #enrich}. */
    protected abstract void doEnrich(StoreOffer offer) throws Exception;

    protected String configKey() {
        return storeName().getConfigKey();
    }

    protected ScraperProperties.StoreConfig config() {
        ScraperProperties.StoreConfig cfg = props.store(configKey());
        return cfg != null ? cfg : new ScraperProperties.StoreConfig();
    }

    @Override
    public boolean isEnabled() {
        ScraperProperties.StoreConfig cfg = props.store(configKey());
        return cfg != null && cfg.isEnabled();
    }

    @Override
    public boolean isEnrichEnabled() {
        return config().isEnrich();
    }

    protected String baseUrl() {
        String base = config().getBaseUrl();
        return base == null ? "" : base;
    }

    protected long pace() {
        return config().getMinRequestIntervalMs();
    }

    protected int maxResults() {
        return props.getMaxResultsPerStore();
    }

    /** Substitute the URL-encoded query into the configured search URL template. */
    protected String searchUrl(String query) {
        String template = config().getSearchUrl();
        if (template == null || template.isBlank()) {
            throw new IllegalStateException(
                    "No searchUrl configured for scraper.stores." + configKey());
        }
        return template.replace("{query}", encode(query));
    }

    @Override
    public List<StoreOffer> search(String query) {
        if (!isEnabled()) {
            log.debug("{} is disabled, skipping", storeName());
            return new ArrayList<>();
        }
        try {
            List<StoreOffer> offers = doSearch(query);
            log.info("{}: {} candidates for '{}'", storeName(), offers.size(), query);
            return offers;
        } catch (Exception e) {
            // Surface as a per-store failure; the orchestrator records it and
            // continues with the other stores.
            log.warn("{}: search failed for '{}': {}", storeName(), query, e.toString());
            throw new StoreScrapeException(storeName(), e.getMessage(), e);
        }
    }

    @Override
    public StoreOffer enrich(StoreOffer offer) {
        if (offer == null || offer.getProductUrl() == null || !isEnrichEnabled()) {
            return offer;
        }
        try {
            doEnrich(offer);
            offer.setEnriched(true);
        } catch (Exception e) {
            // Best-effort: a listing without specs still beats no listing.
            log.debug("{}: enrichment failed for {}: {}",
                    storeName(), offer.getProductUrl(), e.toString());
        }
        return offer;
    }

    // ---- small DOM helpers shared by subclasses ----

    protected static String text(Element root, String selector) {
        if (root == null) return null;
        Element el = root.selectFirst(selector);
        return el != null ? ScrapeUtils.clean(el.text()) : null;
    }

    protected static String attr(Element root, String selector, String attribute) {
        if (root == null) return null;
        Element el = root.selectFirst(selector);
        return el != null ? el.attr(attribute) : null;
    }

    /** Lazy-loaded images usually hide the real URL in a data attribute. */
    protected String imageUrl(Element img) {
        if (img == null) return null;
        String src = ScrapeUtils.firstNonBlank(
                img.attr("data-src"), img.attr("data-original"),
                img.attr("data-lazy"), img.attr("src"));
        return ScrapeUtils.absoluteUrl(baseUrl(), src);
    }

    protected static String encode(String q) {
        return URLEncoder.encode(q == null ? "" : q, StandardCharsets.UTF_8);
    }
}
