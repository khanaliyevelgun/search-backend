package az.pricecompare.scraper.impl;

import az.pricecompare.config.ScraperProperties;
import az.pricecompare.domain.CreditOption;
import az.pricecompare.domain.ProductSpecs;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.scraper.AbstractStoreScraper;
import az.pricecompare.scraper.HtmlFetcher;
import az.pricecompare.scraper.ScrapeUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scraper for irshad.az.
 *
 * The public listing page ({@code /az/mehsullar?q=}) ships an empty
 * {@code <div id="productGridItems">} and fills it over AJAX, so we call that
 * AJAX endpoint ({@code /az/products/list?q=}) directly and get the same HTML
 * fragment the browser would.
 *
 * That fragment is unusually generous — it already carries price, old price,
 * stock label and the full installment table — so enrichment is only needed for
 * specs.
 */
@Component
@Slf4j
public class IrshadScraper extends AbstractStoreScraper {

    public IrshadScraper(HtmlFetcher fetcher, ScraperProperties props) {
        super(fetcher, props);
    }

    @Override
    public StoreName storeName() {
        return StoreName.IRSHAD;
    }

    @Override
    protected List<StoreOffer> doSearch(String query) throws Exception {
        Document doc = fetcher.getHtml(searchUrl(query), pace());
        List<StoreOffer> offers = new ArrayList<>();

        for (Element card : doc.select("div.product")) {
            if (offers.size() >= maxResults()) break;
            try {
                StoreOffer offer = parseCard(card);
                if (offer != null) {
                    offers.add(offer);
                }
            } catch (Exception e) {
                // One malformed card must not cost us the whole store.
                log.debug("{}: skipped a card: {}", storeName(), e.toString());
            }
        }
        return offers;
    }

    private StoreOffer parseCard(Element card) {
        Element nameLink = card.selectFirst("a.product__name");
        if (nameLink == null) {
            return null;
        }
        String title = ScrapeUtils.clean(nameLink.text());
        if (ScrapeUtils.isBlank(title)) {
            return null;
        }

        String stockText = text(card, ".product__label");
        BigDecimal price = ScrapeUtils.parsePrice(text(card, ".product__price__current .new-price"));

        StoreOffer offer = StoreOffer.builder()
                .store(storeName())
                .rawTitle(title)
                .sku(card.selectFirst("[data-product-code]") != null
                        ? card.selectFirst("[data-product-code]").attr("data-product-code") : null)
                .productUrl(ScrapeUtils.absoluteUrl(baseUrl(), nameLink.attr("href")))
                .price(price)
                .oldPrice(ScrapeUtils.parsePrice(text(card, ".product__price__current .old-price")))
                .currency("AZN")
                .inStock(stockText != null && stockText.toLowerCase(Locale.ROOT).contains("stokda"))
                .stockText(stockText)
                .imageUrls(images(card))
                .creditOptions(parseCredit(card, price))
                .build();

        return offer;
    }

    private List<String> images(Element card) {
        List<String> images = new ArrayList<>();
        Element img = card.selectFirst(".product__img img");
        String src = imageUrl(img);
        if (src != null) {
            images.add(src);
        }
        return images;
    }

    /**
     * Installment terms are radio inputs, one per term, carrying the monthly
     * payment and the term length as data attributes.
     */
    private List<CreditOption> parseCredit(Element root, BigDecimal cashPrice) {
        List<CreditOption> options = new ArrayList<>();
        for (Element input : root.select("input.ppl-input[data-monthly-payment]")) {
            Integer months = intOrNull(input.attr("value"));
            BigDecimal monthly = ScrapeUtils.parsePrice(input.attr("data-monthly-payment"));
            BigDecimal total = ScrapeUtils.parsePrice(input.attr("data-total-price"));
            CreditOption option = ScrapeUtils.creditOption(
                    months, monthly, cashPrice != null ? cashPrice : total);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }

    @Override
    protected void doEnrich(StoreOffer offer) throws Exception {
        Document doc = fetcher.getHtml(offer.getProductUrl(), pace());

        ProductSpecs specs = offer.getSpecs() != null ? offer.getSpecs() : ProductSpecs.builder().build();
        // Irshad's spec block is a definition-style list of label/value rows.
        for (Element row : doc.select(".product-features__item, .features__item, .product__properties__item")) {
            Element label = row.selectFirst(".name, .title, dt, .product-features__item__name");
            Element value = row.selectFirst(".value, dd, .product-features__item__value");
            if (label == null || value == null) continue;
            String l = ScrapeUtils.clean(label.text());
            String v = ScrapeUtils.clean(value.text());
            if (!ScrapeUtils.isBlank(l) && !ScrapeUtils.isBlank(v)) {
                SpecMapper.apply(l, v, specs);
            }
        }
        offer.setSpecs(specs);

        // The detail page carries the full gallery; the card only had a thumbnail.
        Set<String> images = new LinkedHashSet<>(offer.getImageUrls());
        for (Element img : doc.select(".product-gallery img, .product__gallery img, .product__img img")) {
            String src = imageUrl(img);
            if (src != null) images.add(src);
            if (images.size() >= 6) break;
        }
        offer.setImageUrls(new ArrayList<>(images));

        // Credit terms are on the detail page too; use them if the card had none.
        if (offer.getCreditOptions().isEmpty()) {
            offer.setCreditOptions(parseCredit(doc, offer.getPrice()));
        }
    }

    private static Integer intOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
