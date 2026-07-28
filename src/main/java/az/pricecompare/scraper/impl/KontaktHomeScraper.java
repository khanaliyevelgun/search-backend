package az.pricecompare.scraper.impl;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.AbstractStoreScraper;
import az.pricecompare.scraper.HtmlFetcher;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KontaktHomeScraper extends AbstractStoreScraper {

    private static final SelectorSet SELECTORS = new SelectorSet(
            /* productCard */ "div.product-card, li.product, div[data-product]",
            /* title       */ "a.product-card__title, .product-title, h3 a",
            /* link        */ "a.product-card__title, a.product-title, h3 a",
            /* price       */ ".product-card__price, .price, .price-current",
            /* oldPrice    */ ".product-card__old-price, .price-old, del",
            /* image       */ "img"
    );

    public KontaktHomeScraper(HtmlFetcher fetcher, ScraperProperties props) {
        super(fetcher, props);
    }

    @Override
    public StoreName storeName() {
        return StoreName.KONTAKT_HOME;
    }

    @Override
    protected String configKey() {
        return "kontakt";
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
