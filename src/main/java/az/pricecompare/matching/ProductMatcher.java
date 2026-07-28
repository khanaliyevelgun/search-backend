package az.pricecompare.matching;

import az.pricecompare.domain.ProductComparison;
import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups offers from different stores that refer to the same physical product,
 * using fuzzy comparison of normalized attributes, then builds a
 * {@link ProductComparison} per group with computed highlights (cheapest store,
 * price range, union of colors).
 *
 * The algorithm is deliberately simple and explainable (greedy clustering by a
 * similarity threshold) rather than a heavyweight ML approach — it's easy to
 * tune and debug, which matters when store titles are inconsistent.
 */
@Component
@RequiredArgsConstructor
public class ProductMatcher {

    private final ProductNormalizer normalizer;

    /**
     * Two offers are considered the same product when their brands agree, their
     * storage agrees (or one is unknown), and their model-token similarity is at
     * least this threshold. Tune as needed.
     */
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /**
     * Cluster raw offers (from all stores) into comparison groups.
     */
    public List<ProductComparison> groupOffers(List<StoreOffer> offers) {
        // Normalize once; keep offer + its normalized view together.
        List<Item> items = new ArrayList<>();
        for (StoreOffer o : offers) {
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

        // Show groups that have the most stores first (best comparisons on top),
        // then cheapest first.
        comparisons.sort(Comparator
                .comparingInt((ProductComparison c) -> c.getOffers().size()).reversed()
                .thenComparing(c -> nullSafe(c.getLowestPrice())));
        return comparisons;
    }

    /** Decide whether an item matches the representative of an existing cluster. */
    private boolean belongsTo(Item item, Item representative) {
        NormalizedProduct a = item.norm;
        NormalizedProduct b = representative.norm;

        // Brand must agree (unless one is unknown).
        if (!"unknown".equals(a.brand()) && !"unknown".equals(b.brand())
                && !a.brand().equals(b.brand())) {
            return false;
        }
        // Storage must agree when both are known — 128GB vs 256GB are different SKUs.
        if (a.storage() != null && b.storage() != null
                && !a.storage().equalsIgnoreCase(b.storage())) {
            return false;
        }
        return tokenSimilarity(a.signature(), b.signature()) >= SIMILARITY_THRESHOLD;
    }

    /**
     * Jaccard-style token-set similarity: |intersection| / |union| of the word
     * tokens in each signature. Robust to word ordering and minor extra words.
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

    private ProductComparison buildComparison(List<Item> cluster) {
        List<StoreOffer> offers = new ArrayList<>();
        for (Item i : cluster) offers.add(i.offer);

        // Pick the richest normalized name (longest canonical) as the group name.
        NormalizedProduct best = cluster.stream()
                .map(i -> i.norm)
                .max(Comparator.comparingInt(n -> n.canonicalName() == null ? 0 : n.canonicalName().length()))
                .orElse(cluster.get(0).norm);

        BigDecimal lowest = null, highest = null;
        StoreName cheapest = null;
        Set<String> colors = new LinkedHashSet<>();

        for (StoreOffer o : offers) {
            if (o.getAvailableColors() != null) colors.addAll(o.getAvailableColors());
            BigDecimal p = o.getPrice();
            if (p == null) continue;
            if (lowest == null || p.compareTo(lowest) < 0) {
                lowest = p;
                cheapest = o.getStore();
            }
            if (highest == null || p.compareTo(highest) > 0) {
                highest = p;
            }
        }

        BigDecimal saving = (lowest != null && highest != null)
                ? highest.subtract(lowest) : null;

        return ProductComparison.builder()
                .canonicalName(best.canonicalName())
                .brand(best.brand())
                .offers(offers)
                .lowestPrice(lowest)
                .highestPrice(highest)
                .cheapestStore(cheapest)
                .maxSaving(saving)
                .allColorsSeen(new ArrayList<>(colors))
                .build();
    }

    private static BigDecimal nullSafe(BigDecimal b) {
        return b == null ? new BigDecimal(Long.MAX_VALUE) : b;
    }

    /** Pairs an offer with its normalized view during clustering. */
    private record Item(StoreOffer offer, NormalizedProduct norm) {}
}
