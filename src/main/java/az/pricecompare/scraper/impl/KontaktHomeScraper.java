package az.pricecompare.scraper.impl;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.CreditOption;
import az.pricecompare.domain.ProductSpecs;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.AbstractStoreScraper;
import az.pricecompare.scraper.HtmlFetcher;
import az.pricecompare.scraper.ScrapeUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scraper for kontakt.az.
 *
 * Kontakt runs Magento with the Elasticsuite "multisearch" module, whose autocomplete
 * endpoint is a plain public JSON API. That's far better than parsing the search
 * page — the page itself renders products client-side and contains nothing but
 * loading skeletons in the server HTML.
 *
 * The JSON gives price, old price, stock and image; the detail page is needed for
 * specs ({@code .har__row}) and the installment calculator ({@code .calks__circle}).
 */
@Component
public class KontaktHomeScraper extends AbstractStoreScraper {

    public KontaktHomeScraper(HtmlFetcher fetcher, ScraperProperties props) {
        super(fetcher, props);
    }

    @Override
    public StoreName storeName() {
        return StoreName.KONTAKT_HOME;
    }

    @Override
    protected List<StoreOffer> doSearch(String query) throws Exception {
        JsonNode root = fetcher.getJson(searchUrl(query), pace());
        List<StoreOffer> offers = new ArrayList<>();

        JsonNode items = root.path("results").path("items");
        if (!items.isArray()) {
            return offers;
        }
        for (JsonNode item : items) {
            if (offers.size() >= maxResults()) break;
            StoreOffer offer = parseItem(item);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return offers;
    }

    private StoreOffer parseItem(JsonNode item) {
        String name = ScrapeUtils.clean(item.path("name").asText(null));
        if (ScrapeUtils.isBlank(name)) {
            return null;
        }
        List<String> images = new ArrayList<>();
        String picture = item.path("picture").asText(null);
        if (!ScrapeUtils.isBlank(picture)) {
            images.add(picture);
        }
        return StoreOffer.builder()
                .store(storeName())
                .rawTitle(name)
                .sku(item.path("id").asText(null))
                .productUrl(item.path("url").asText(null))
                .price(decimal(item, "price"))
                .oldPrice(decimal(item, "oldprice"))
                .currency("AZN")
                .inStock(item.path("is_presence").asBoolean(false))
                .stockText(item.path("presence").asText(null))
                .imageUrls(images)
                .build();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.decimalValue() : null;
    }

    @Override
    protected void doEnrich(StoreOffer offer) throws Exception {
        Document doc = fetcher.getHtml(offer.getProductUrl(), pace());
        parseAvailability(doc, offer);
        parseSpecs(doc, offer);
        parseCredit(doc, offer);
        parseImages(doc, offer);
    }

    /**
     * The search API's {@code is_presence} flag is optimistic — it reports "In
     * Stock" for products whose own page says OutOfStock. The page's schema.org
     * markup is the more trustworthy signal, so it wins when both are present.
     */
    private void parseAvailability(Document doc, StoreOffer offer) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (!json.contains("\"Product\"") || !json.contains("availability")) {
                continue;
            }
            boolean inStock = json.contains("schema.org/InStock");
            boolean outOfStock = json.contains("schema.org/OutOfStock");
            if (inStock || outOfStock) {
                offer.setInStock(inStock);
                offer.setStockText(inStock ? "In Stock" : "Out of Stock");
                return;
            }
        }
    }

    /** Specs live in a plain label/value grid: .har__row > .har__title + .har__znach */
    private void parseSpecs(Document doc, StoreOffer offer) {
        ProductSpecs specs = offer.getSpecs() != null ? offer.getSpecs() : ProductSpecs.builder().build();
        for (Element row : doc.select(".har__row")) {
            Element titleEl = row.selectFirst(".har__title");
            Element valueEl = row.selectFirst(".har__znach");
            if (titleEl == null || valueEl == null) continue;

            // The label carries a nested tooltip div; drop it before reading text.
            Element labelCopy = titleEl.clone();
            labelCopy.select(".har__tooltip").remove();

            String label = ScrapeUtils.clean(labelCopy.text());
            String value = ScrapeUtils.clean(valueEl.text());
            if (ScrapeUtils.isBlank(label) || ScrapeUtils.isBlank(value)) continue;

            SpecMapper.apply(label, value, specs);
        }
        offer.setSpecs(specs);
    }

    /**
     * The installment calculator renders one circle per term, carrying the monthly
     * payment as a data attribute. Out-of-stock products render "------" instead of
     * a number, in which case we skip the term rather than invent one.
     */
    private void parseCredit(Document doc, StoreOffer offer) {
        List<CreditOption> options = new ArrayList<>();
        for (Element circle : doc.select(".calks__circle[data-month]")) {
            Integer months = intOrNull(circle.attr("data-month"));
            BigDecimal monthly = ScrapeUtils.parsePrice(circle.attr("data-mountly-payment"));
            CreditOption option = ScrapeUtils.creditOption(months, monthly, offer.getPrice());
            if (option != null) {
                options.add(option);
            }
        }
        offer.setCreditOptions(options);
    }

    private void parseImages(Document doc, StoreOffer offer) {
        Set<String> images = new LinkedHashSet<>(offer.getImageUrls());
        for (Element img : doc.select(".product-image-photo, .gallery img, .fotorama__img")) {
            String src = imageUrl(img);
            if (src != null && src.contains("/media/catalog/product/")) {
                images.add(src);
            }
            if (images.size() >= 6) break;
        }
        offer.setImageUrls(new ArrayList<>(images));
    }

    private static Integer intOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
