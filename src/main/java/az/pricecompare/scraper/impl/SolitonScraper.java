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
 * Scraper for soliton.az.
 *
 * Soliton is the awkward one. Its search results carry only a title, a thumbnail
 * and a URL — no price — so every candidate we care about costs a detail-page
 * fetch. That's why {@code maxEnrichedPerStore} matters: without a relevance
 * filter in front of it this scraper would issue dozens of requests per search.
 *
 * Its search is also a strict AND over all terms, so "iphone 16 pro max" returns
 * nothing while "iphone 16" returns three phones. {@link #doSearch} compensates by
 * progressively dropping trailing words until something comes back.
 */
@Component
@Slf4j
public class SolitonScraper extends AbstractStoreScraper {

    /** Never relax below this many words, or "iphone" alone matches everything. */
    private static final int MIN_TERMS = 2;

    public SolitonScraper(HtmlFetcher fetcher, ScraperProperties props) {
        super(fetcher, props);
    }

    @Override
    public StoreName storeName() {
        return StoreName.SOLITON;
    }

    @Override
    protected List<StoreOffer> doSearch(String query) throws Exception {
        String[] terms = query.trim().split("\\s+");

        // Try the full phrase first, then shed trailing qualifiers ("pro max",
        // "max") until the store's AND-search finds something.
        for (int len = terms.length; len >= Math.min(MIN_TERMS, terms.length); len--) {
            String attempt = String.join(" ", java.util.Arrays.copyOfRange(terms, 0, len));
            List<StoreOffer> offers = searchExact(attempt);
            if (!offers.isEmpty()) {
                if (len < terms.length) {
                    log.debug("{}: relaxed '{}' to '{}' to get {} results",
                            storeName(), query, attempt, offers.size());
                }
                return offers;
            }
        }
        return new ArrayList<>();
    }

    private List<StoreOffer> searchExact(String query) throws Exception {
        Document doc = fetcher.getHtml(searchUrl(query), pace());
        List<StoreOffer> offers = new ArrayList<>();

        for (Element result : doc.select(".searchResults .result")) {
            if (offers.size() >= maxResults()) break;
            Element titleLink = result.selectFirst("a.title");
            if (titleLink == null) continue;

            String title = ScrapeUtils.clean(titleLink.text());
            if (ScrapeUtils.isBlank(title)) continue;

            List<String> images = new ArrayList<>();
            String thumb = imageUrl(result.selectFirst("a.pic img"));
            if (thumb != null) images.add(thumb);

            offers.add(StoreOffer.builder()
                    .store(storeName())
                    .rawTitle(title)
                    .productUrl(ScrapeUtils.absoluteUrl(baseUrl(), titleLink.attr("href")))
                    .currency("AZN")
                    // Price and stock are unknown until we open the product page.
                    .inStock(false)
                    .imageUrls(images)
                    .build());
        }
        return offers;
    }

    @Override
    protected void doEnrich(StoreOffer offer) throws Exception {
        Document doc = fetcher.getHtml(offer.getProductUrl(), pace());

        parsePrice(doc, offer);
        parseCredit(doc, offer);
        parseSpecs(doc, offer);
        parseImages(doc, offer);
    }

    /**
     * The price is split across nested spans — {@code <span class="price">2299<span
     * class="dec">.99</span>...} — so reading the element's whole text and letting
     * the number parser deal with it is more robust than addressing the parts.
     */
    private void parsePrice(Document doc, StoreOffer offer) {
        Element priceEl = doc.selectFirst(".priceHolder .price");
        if (priceEl == null) {
            return;
        }
        // Drop the currency glyph span so it can't be read as part of the number.
        Element copy = priceEl.clone();
        copy.select(".aznm").remove();

        BigDecimal price = ScrapeUtils.parsePrice(copy.text());
        if (price != null) {
            offer.setPrice(price);
            // A product page that quotes a price and a basket link is purchasable;
            // Soliton has no explicit stock badge to read.
            offer.setInStock(doc.selectFirst("a.buy") != null);
        }
        BigDecimal old = ScrapeUtils.parsePrice(text(doc, ".priceHolder .oldPrice"));
        if (old != null && price != null && old.compareTo(price) > 0) {
            offer.setOldPrice(old);
        }
    }

    /** Installments are a small table: .tableRow[data-month] > .month + .monthlyAmount */
    private void parseCredit(Document doc, StoreOffer offer) {
        List<CreditOption> options = new ArrayList<>();
        for (Element row : doc.select(".hisseHisseTable .tableRow[data-month]")) {
            Integer months = intOrNull(row.attr("data-month"));
            if (months == null) {
                months = ScrapeUtils.parseMonths(text(row, ".month"));
            }
            Element amount = row.selectFirst(".monthlyAmount");
            if (amount == null) continue;
            Element copy = amount.clone();
            copy.select(".aznm").remove();

            CreditOption option = ScrapeUtils.creditOption(
                    months, ScrapeUtils.parsePrice(copy.text()), offer.getPrice());
            if (option != null) {
                options.add(option);
            }
        }
        offer.setCreditOptions(options);
    }

    /** Specs are several <table class="specBox"> blocks of two-cell rows. */
    private void parseSpecs(Document doc, StoreOffer offer) {
        ProductSpecs specs = offer.getSpecs() != null ? offer.getSpecs() : ProductSpecs.builder().build();
        for (Element row : doc.select("table.specBox tr")) {
            Element[] cells = row.select("td").toArray(new Element[0]);
            if (cells.length < 2) continue;   // section heading rows use <th>
            String label = ScrapeUtils.clean(cells[0].text());
            String value = ScrapeUtils.clean(cells[1].text());
            if (!ScrapeUtils.isBlank(label) && !ScrapeUtils.isBlank(value)) {
                SpecMapper.apply(label, value, specs);
            }
        }
        offer.setSpecs(specs);
    }

    private void parseImages(Document doc, StoreOffer offer) {
        Set<String> images = new LinkedHashSet<>(offer.getImageUrls());
        for (Element img : doc.select(".productImages img, .mainImage img, .gallery img")) {
            String src = imageUrl(img);
            if (src != null) images.add(src);
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
