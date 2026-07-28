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
 * Scraper for irshad.az.
 *
 * SELECTOR MAINTENANCE: update {@link #SELECTORS} against the live search page.
 */
@Component
public class IrshadScraper extends AbstractStoreScraper {

    private static final SelectorSet SELECTORS = new SelectorSet(
            /* productCard */ "div.product-item, div.product, article.product",
            /* title       */ ".product-item__name, .product-name, h3 a, a.name",
            /* link        */ ".product-item__name a, a.product-name, h3 a, a.name",
            /* price       */ ".product-item__price, .product-price, .price",
            /* oldPrice    */ ".product-item__old-price, .old-price, del",
            /* image       */ "img"
    );

    public IrshadScraper(HtmlFetcher fetcher, ScraperProperties props) {
        super(fetcher, props);
    }

    @Override
    public StoreName storeName() {
        return StoreName.IRSHAD;
    }

    @Override
    protected String configKey() {
        return "irshad";
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
