package az.pricecompare.matching;

import az.pricecompare.domain.CreditOption;
import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import az.pricecompare.domain.StoreSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups offers from different stores that refer to the same physical product,
 * then rolls each store's colour variants into one {@link StoreSummary}.
 *
 * Two decisions shape everything here:
 *
 * <ul>
 *   <li><b>Colour is not part of identity.</b> A comparison covers "iPhone 16 Pro
 *       Max 256GB" across all colours, because the question the product answers is
 *       "who has it, in which colours, for how much" — which is unanswerable if
 *       each colour becomes its own comparison.</li>
 *   <li><b>Storage is part of identity.</b> 256GB and 512GB are different products
 *       at a 600 AZN difference; merging them would make the "cheapest" number a
 *       lie.</li>
 * </ul>
 *
 * The clustering itself is greedy by similarity threshold — simple and
 * explainable, which matters when store titles are this inconsistent.
 */
@Component
@RequiredArgsConstructor
public class ProductMatcher {

    private final ProductNormalizer normalizer;

    /**
     * Minimum token-set similarity between two model signatures for them to be
     * considered the same model.
     */
    private static final double SIMILARITY_THRESHOLD = 0.7;

    /** Cluster raw offers (from all stores) into comparison groups. */
    public List<ProductComparison> groupOffers(List<StoreOffer> offers) {
        List<Item> items = new ArrayList<>();
        for (StoreOffer o : offers) {
            if (o == null || o.getRawTitle() == null) continue;
            items.add(new Item(o, normalizer.normalize(o)));
        }

        List<List<Item>> clusters = new ArrayList<>();
        for (Item item : items) {
            List<Item> target = null;
            for (List<Item> cluster : clusters) {
                if (belongsTo(item, cluster.get(0))) {
                    target = cluster;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                clusters.add(target);
            }
            target.add(item);
        }

        List<ProductComparison> comparisons = new ArrayList<>();
        for (List<Item> cluster : clusters) {
            comparisons.add(buildComparison(cluster));
        }

        // Best comparisons first: groups covering the most stores, then cheapest.
        comparisons.sort(Comparator
                .comparingInt((ProductComparison c) -> c.getStores().size()).reversed()
                .thenComparing(c -> nullSafe(c.getLowestPrice())));
        return comparisons;
    }

    /** Decide whether an item matches the representative of an existing cluster. */
    private boolean belongsTo(Item item, Item representative) {
        NormalizedProduct a = item.norm;
        NormalizedProduct b = representative.norm;

        // Brand must agree when both are known.
        if (a.brandKnown() && b.brandKnown() && !a.brand().equals(b.brand())) {
            return false;
        }
        // Storage must agree when both are known — 128GB and 256GB are different
        // SKUs at materially different prices.
        if (a.storage() != null && b.storage() != null
                && !a.storage().equalsIgnoreCase(b.storage())) {
            return false;
        }
        return tokenSimilarity(a.signature(), b.signature()) >= SIMILARITY_THRESHOLD;
    }

    /**
     * Jaccard token-set similarity: |intersection| / |union| of the word tokens in
     * each signature. Robust to word ordering and minor extra words.
     */
    private double tokenSimilarity(String s1, String s2) {
        Set<String> t1 = tokens(s1);
        Set<String> t2 = tokens(s2);
        if (t1.isEmpty() || t2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(t1);
        intersection.retainAll(t2);
        Set<String> union = new HashSet<>(t1);
        union.addAll(t2);
        return (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String s) {
        Set<String> set = new HashSet<>();
        if (s == null) return set;
        for (String t : s.split(" ")) {
            if (!t.isBlank()) set.add(t);
        }
        return set;
    }

    // ------------------------------------------------------------------
    // Building the comparison
    // ------------------------------------------------------------------

    private ProductComparison buildComparison(List<Item> cluster) {
        // The richest title in the cluster gives the best display name.
        NormalizedProduct best = cluster.stream()
                .map(i -> i.norm)
                .max(Comparator.comparingInt(n -> n.canonicalName() == null ? 0 : n.canonicalName().length()))
                .orElse(cluster.get(0).norm);

        // Group by store first: each store becomes exactly one row, however many
        // colour variants it listed. Without this a store with five colours would
        // occupy five slots and skew every "cheapest store" calculation.
        Map<StoreName, List<StoreOffer>> byStore = new LinkedHashMap<>();
        for (Item i : cluster) {
            byStore.computeIfAbsent(i.offer.getStore(), s -> new ArrayList<>()).add(i.offer);
        }

        List<StoreSummary> summaries = new ArrayList<>();
        Set<String> allColors = new LinkedHashSet<>();
        for (Map.Entry<StoreName, List<StoreOffer>> e : byStore.entrySet()) {
            StoreSummary summary = summarize(e.getKey(), e.getValue());
            summaries.add(summary);
            allColors.addAll(summary.getColorsAvailable());
        }

        // Now that the union of colours is known, tell each store what it lacks.
        for (StoreSummary summary : summaries) {
            List<String> missing = new ArrayList<>(allColors);
            missing.removeAll(summary.getColorsAvailable());
            summary.setColorsMissing(missing);
        }

        summaries.sort(Comparator.comparing(s -> nullSafe(s.getPrice())));

        BigDecimal lowest = null;
        BigDecimal highest = null;
        StoreName cheapest = null;
        int totalOffers = 0;

        for (StoreSummary s : summaries) {
            totalOffers += s.getVariantCount();
            BigDecimal p = s.getPrice();
            if (p == null) continue;
            if (lowest == null || p.compareTo(lowest) < 0) {
                lowest = p;
                cheapest = s.getStore();
            }
            BigDecimal top = s.getMaxPrice() != null ? s.getMaxPrice() : p;
            if (highest == null || top.compareTo(highest) > 0) {
                highest = top;
            }
        }

        return ProductComparison.builder()
                .canonicalName(best.canonicalName())
                .brand(best.brand())
                .model(best.signature())
                .storage(best.storage())
                .stores(summaries)
                .lowestPrice(lowest)
                .highestPrice(highest)
                .cheapestStore(cheapest)
                .maxSaving(lowest != null && highest != null ? highest.subtract(lowest) : null)
                .allColorsSeen(new ArrayList<>(allColors))
                .totalOffers(totalOffers)
                .build();
    }

    /** Roll one store's colour variants into a single comparable row. */
    private StoreSummary summarize(StoreName store, List<StoreOffer> offers) {
        Set<String> colors = new LinkedHashSet<>();
        BigDecimal min = null;
        BigDecimal max = null;
        StoreOffer cheapest = null;
        boolean anyInStock = false;

        for (StoreOffer o : offers) {
            if (o.getColor() != null) {
                colors.add(o.getColor());
            }
            anyInStock |= o.isInStock();

            BigDecimal p = o.getPrice();
            if (p == null) continue;
            if (min == null || p.compareTo(min) < 0) {
                min = p;
                cheapest = o;
            }
            if (max == null || p.compareTo(max) > 0) {
                max = p;
            }
        }
        // Every variant may be price-less (a Soliton result we chose not to
        // enrich); fall back to the first so the row still links somewhere.
        if (cheapest == null) {
            cheapest = offers.get(0);
        }

        List<CreditOption> credit = bestCreditPlans(offers);

        return StoreSummary.builder()
                .store(store)
                .storeDisplayName(store.getDisplayName())
                .price(min)
                .maxPrice(max)
                .oldPrice(cheapest.getOldPrice())
                .productUrl(cheapest.getProductUrl())
                .imageUrl(cheapest.getImageUrls().isEmpty() ? null : cheapest.getImageUrls().get(0))
                .inStock(anyInStock)
                .colorsAvailable(new ArrayList<>(colors))
                .creditOptions(credit)
                .lowestMonthlyPayment(credit.stream()
                        .map(CreditOption::getMonthlyPayment)
                        .filter(java.util.Objects::nonNull)
                        .min(BigDecimal::compareTo)
                        .orElse(null))
                .variantCount(offers.size())
                .offers(offers)
                .build();
    }

    /**
     * Installment plans differ per variant only by rounding, so we take the plan
     * set from the variant that has the most of them, deduped by term.
     */
    private List<CreditOption> bestCreditPlans(List<StoreOffer> offers) {
        List<CreditOption> richest = offers.stream()
                .map(StoreOffer::getCreditOptions)
                .filter(c -> c != null && !c.isEmpty())
                .max(Comparator.comparingInt(List::size))
                .orElse(List.of());

        Map<Integer, CreditOption> byMonths = new LinkedHashMap<>();
        for (CreditOption o : richest) {
            if (o.getMonths() != null) {
                byMonths.putIfAbsent(o.getMonths(), o);
            }
        }
        List<CreditOption> sorted = new ArrayList<>(byMonths.values());
        sorted.sort(Comparator.comparingInt(CreditOption::getMonths));
        return sorted;
    }

    /** Sorts null prices last without the caller having to special-case them. */
    private static BigDecimal nullSafe(BigDecimal b) {
        return b == null ? new BigDecimal(Long.MAX_VALUE) : b;
    }

    /** Pairs an offer with its normalized view during clustering. */
    private record Item(StoreOffer offer, NormalizedProduct norm) {}
}
