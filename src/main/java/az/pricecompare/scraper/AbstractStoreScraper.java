package az.pricecompare.scraper;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.ProductSpecs;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.impl.SelectorSet;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared scaffolding for store scrapers: config lookup, enabled flag, and a
 * template {@link #search(String)} that fetches the search page, delegates
 * per-store parsing to subclasses, and guards each product parse so one bad
 * item can't kill the whole store.
 */
@Slf4j
public abstract class AbstractStoreScraper implements StoreScraper {

    protected final HtmlFetcher fetcher;
    protected final ScraperProperties props;

    protected AbstractStoreScraper(HtmlFetcher fetcher, ScraperProperties props) {
        this.fetcher = fetcher;
        this.props = props;
    }

    /** The config slug used in application.yml (e.g. "kontakt"). */
    protected abstract String configKey();

    /** Build the store's search URL for a query. */
    protected abstract String buildSearchUrl(String query);

    /**
     * Parse the fetched search-results document into offers.
     * Subclasses implement the store-specific selectors here.
     */
    protected abstract List<StoreOffer> parseSearchResults(org.jsoup.nodes.Document doc);

    @Override
    public boolean isEnabled() {
        ScraperProperties.StoreConfig cfg = props.getStores().get(configKey());
        return cfg != null && cfg.isEnabled();
    }

    protected String baseUrl() {
        ScraperProperties.StoreConfig cfg = props.getStores().get(configKey());
        return cfg != null ? cfg.getBaseUrl() : "";
    }

    protected int maxResults() {
        return props.getMaxResultsPerStore();
    }

    @Override
    public List<StoreOffer> search(String query) {
        if (!isEnabled()) {
            log.info("{} is disabled, skipping", storeName());
            return new ArrayList<>();
        }
        String url = buildSearchUrl(query);
        try {
            var doc = fetcher.get(url);
            List<StoreOffer> offers = parseSearchResults(doc);
            log.info("{}: found {} offers for '{}'", storeName(), offers.size(), query);
            return offers;
        } catch (Exception e) {
            // Surface as a per-store failure; the orchestrator records it and
            // continues with the other stores.
            log.warn("{}: scrape failed for '{}': {}", storeName(), query, e.toString());
            throw new StoreScrapeException(storeName(), e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------
    // Reusable generic parsing. Most Azerbaijani electronics stores render a
    // grid of product cards with the same conceptual fields, so a single
    // selector-driven parser handles all of them. A store with unusual markup
    // can still override parseSearchResults() directly.
    // ---------------------------------------------------------------------

    /**
     * Parse a results document using a {@link SelectorSet}. Each card is parsed
     * inside a try/catch so one malformed card is skipped rather than failing
     * the store.
     */
    protected List<StoreOffer> parseWithSelectors(org.jsoup.nodes.Document doc, SelectorSet sel) {
        List<StoreOffer> offers = new ArrayList<>();
        Elements cards = doc.select(sel.productCard());
        int limit = Math.min(cards.size(), maxResults());

        for (int i = 0; i < limit; i++) {
            Element card = cards.get(i);
            try {
                StoreOffer offer = parseCard(card, sel);
                if (offer != null && offer.getRawTitle() != null && !offer.getRawTitle().isBlank()) {
                    offers.add(offer);
                }
            } catch (Exception e) {
                log.debug("{}: skipped a card: {}", storeName(), e.toString());
            }
        }
        return offers;
    }

    private StoreOffer parseCard(Element card, SelectorSet sel) {
        String title = ScrapeUtils.clean(text(card, sel.title()));
        String href = attr(card, sel.link(), "href");
        String price = text(card, sel.price());
        String oldPrice = text(card, sel.oldPrice());

        List<String> images = new ArrayList<>();
        for (Element img : card.select(sel.image())) {
            // Lazy-loaded images frequently live in data-src, not src.
            String src = firstNonBlank(img.attr("data-src"), img.attr("data-original"), img.attr("src"));
            if (src != null && !src.isBlank()) {
                images.add(ScrapeUtils.absoluteUrl(baseUrl(), src));
            }
        }

        return StoreOffer.builder()
                .store(storeName())
                .rawTitle(title)
                .productUrl(ScrapeUtils.absoluteUrl(baseUrl(), href))
                .price(ScrapeUtils.parsePrice(price))
                .oldPrice(ScrapeUtils.parsePrice(oldPrice))
                .currency("AZN")
                .inStock(true)
                .imageUrls(images)
                .specs(ProductSpecs.builder().build())
                .build();
    }

    // ---- small DOM helpers shared by subclasses ----

    protected String text(Element root, String sel) {
        Element el = root.selectFirst(sel);
        return el != null ? el.text() : null;
    }

    protected String attr(Element root, String sel, String attr) {
        Element el = root.selectFirst(sel);
        return el != null ? el.attr(attr) : null;
    }

    protected static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    protected static String encode(String q) {
        try {
            return URLEncoder.encode(q, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return q;
        }
    }
}
