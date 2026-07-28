package az.pricecompare.matching;

import az.pricecompare.domain.StoreOffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which of a store's search hits actually answer the user's query.
 *
 * This is not optional polish. The stores' own search engines are loose: asking
 * İrşad for "iphone 16 pro max" returns phone cases before it returns phones, and
 * Kontakt's API puts four accessories above the first handset. Without this
 * filter the comparison confidently reports "cheapest iPhone 16 Pro Max: 2.99 AZN"
 * — an accessory — which is worse than returning nothing.
 *
 * Two rules, both deliberately blunt so failures are easy to reason about:
 * <ol>
 *   <li>every meaningful word of the query must appear in the title;</li>
 *   <li>unless the user asked for one, accessory titles are dropped.</li>
 * </ol>
 */
@Component
@Slf4j
public class RelevanceFilter {

    /**
     * Words that mark a title as an accessory for a product rather than the
     * product. Azerbaijani first, since that's what these titles are written in.
     */
    private static final List<String> ACCESSORY_WORDS = List.of(
            "qoruyucu", "örtük", "ortuk", "qılıf", "qilif", "kabel", "adapter",
            "adaptor", "şarj", "sarj", "çexol", "çanta", "kart", "stend", "tripod",
            "case", "cover", "cable", "charger", "screen protector", "protector",
            "glass", "şüşə", "susa", "holder", "mount", "dock", "strap", "band",
            "qayış", "keys", "sticker", "film", "lens", "чехол", "кабель",
            "защитное", "стекло", "adaptör", "powerbank", "power bank"
    );

    /**
     * Query words that mean the user genuinely wants an accessory, which switches
     * the accessory rule off.
     */
    private static final Set<String> ACCESSORY_INTENT = Set.of(
            "case", "cover", "qılıf", "qilif", "örtük", "ortuk", "qoruyucu",
            "kabel", "cable", "adapter", "adaptor", "şarj", "sarj", "charger",
            "чехол", "кабель", "powerbank", "stend", "tripod", "glass", "şüşə"
    );

    /** Words in a query that carry no filtering value. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "и", "и/или", "ile", "ilə", "və", "ve", "with", "for"
    );

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Keep only the offers that plausibly are the product the user asked for,
     * best match first.
     *
     * @param limit maximum offers to return per call
     */
    public List<StoreOffer> filter(String query, List<StoreOffer> offers, int limit) {
        if (offers == null || offers.isEmpty()) {
            return new ArrayList<>();
        }
        // Dedupe: "iphone iphone 16" must not make the coverage rule stricter.
        List<String> queryTerms = queryTerms(query);
        boolean accessorySearch = queryTerms.stream().anyMatch(ACCESSORY_INTENT::contains);

        record Scored(StoreOffer offer, double score) {}
        List<Scored> kept = new ArrayList<>();

        for (StoreOffer offer : offers) {
            String title = offer.getRawTitle();
            if (title == null || title.isBlank()) continue;

            String lowerTitle = title.toLowerCase(Locale.ROOT);

            if (!accessorySearch && isAccessory(lowerTitle)) {
                continue;
            }
            double score = score(queryTerms, lowerTitle);
            if (score < 1.0) {
                // Not every query word is present — different product.
                continue;
            }
            kept.add(new Scored(offer, score));
        }

        // Ties are common (all colour variants score identically); keep the
        // store's own ordering within a score by using a stable sort.
        kept.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<StoreOffer> result = new ArrayList<>();
        for (Scored s : kept) {
            if (result.size() >= limit) break;
            result.add(s.offer());
        }

        if (result.size() < offers.size()) {
            log.debug("Relevance filter kept {}/{} offers for '{}'",
                    result.size(), offers.size(), query);
        }
        return result;
    }

    /**
     * Fraction of query terms present in the title, plus a small bonus for a
     * tight title. The bonus breaks ties toward "iPhone 16 Pro Max 256 GB Black"
     * over "iPhone 16 Pro Max 256 GB Black + free case bundle".
     */
    private double score(List<String> queryTerms, String lowerTitle) {
        if (queryTerms.isEmpty()) {
            return 1.0;
        }
        List<String> titleTerms = terms(lowerTitle);
        Set<String> titleSet = new LinkedHashSet<>(titleTerms);

        int matched = 0;
        for (String term : queryTerms) {
            if (titleSet.contains(term)) {
                matched++;
            }
        }
        double coverage = (double) matched / queryTerms.size();
        if (coverage < 1.0) {
            return coverage;
        }
        // All terms matched. Prefer titles with less filler around them.
        double brevity = 1.0 / (1.0 + Math.max(0, titleTerms.size() - queryTerms.size()));
        return 1.0 + brevity;
    }

    private boolean isAccessory(String lowerTitle) {
        for (String word : ACCESSORY_WORDS) {
            if (lowerTitle.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private List<String> terms(String text) {
        if (text == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String t : SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (t.isBlank() || STOPWORDS.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    /** Exposed for tests and for scrapers that want the same tokenisation. */
    public List<String> queryTerms(String query) {
        return new ArrayList<>(new LinkedHashSet<>(terms(query)));
    }
}
