package az.pricecompare.scraper.impl;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.AbstractStoreScraper;
import az.pricecompare.scraper.HtmlFetcher;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scraper for soliton.az.
 *
 * SELECTOR MAINTENANCE: update {@link #SELECTORS} against the live search page.
 */
@Component
public class SolitonScraper extends AbstractStoreScraper {

    private static final SelectorSet SELECTORS = new SelectorSet(
            /* productCard */ "div.product, li.product-item, div.catalog-item",
            /* title       */ ".product__title, .catalog-item__title, h3 a, a.title",
            /* link        */ ".product__title a, a.title, h3 a",
            /* price       */ ".product__price, .catalog-item__price, .price",
            /* oldPrice    */ ".product__old-price, .old-price, del",
            /* image       */ "img"
    );

    public SolitonScraper(HtmlFetcher fetcher, ScraperProperties props) {
        super(fetcher, props);
    }

    @Override
    public StoreName storeName() {
        return StoreName.SOLITON;
    }

    @Override
    protected String configKey() {
        return "soliton";
    }

    @Override
    protected String buildSearchUrl(String query) {
        // TODO: confirm the real search path/param against the live site.
        return baseUrl() + "/search?q=" + encode(query);
    }

    @Override
    protected List<StoreOffer> parseSearchResults(Document doc) {
        return parseWithSelectors(doc, SELECTORS);
    }
}
