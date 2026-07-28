package az.pricecompare.scraper.impl;

import az.pricecompare.domain.ProductSpecs;

import java.util.Locale;

/**
 * Maps a store's own spec label (Azerbaijani, occasionally Russian) onto the
 * named fields of {@link ProductSpecs}.
 *
 * The three stores use different wording for the same attribute — Kontakt says
 * "Daxili yaddaş", Soliton just "Yaddaş" — so matching is by keyword rather than
 * exact string. Anything unrecognised is preserved verbatim in
 * {@link ProductSpecs#getAdditional()}, so nothing is lost.
 */
final class SpecMapper {

    private SpecMapper() {}

    static void apply(String label, String value, ProductSpecs specs) {
        String l = label.toLowerCase(Locale.ROOT);

        if (contains(l, "daxili yaddaş", "yaddaş həcmi", "yaddaş") && !contains(l, "operativ", "kart")) {
            specs.setStorage(firstSet(specs.getStorage(), value));
        } else if (contains(l, "operativ yaddaş", "ram")) {
            specs.setRam(firstSet(specs.getRam(), value));
        } else if (contains(l, "prosessor")) {
            // "Prosessorun adı" beats "Prosessorun növü" as the display value.
            if (specs.getProcessor() == null || contains(l, "ad")) {
                specs.setProcessor(value);
            }
        } else if (contains(l, "ekran ölçüsü", "displey ölçüsü", "diaqonal")) {
            specs.setDisplaySize(firstSet(specs.getDisplaySize(), value));
        } else if (contains(l, "displey növü", "ekran növü", "matris") || l.equals("ekran")) {
            specs.setDisplayType(firstSet(specs.getDisplayType(), value));
        } else if (contains(l, "ön kamera", "selfi")) {
            specs.setFrontCamera(firstSet(specs.getFrontCamera(), value));
        } else if (contains(l, "əsas kamera", "arxa kamera") || l.equals("kamera")) {
            specs.setMainCamera(firstSet(specs.getMainCamera(), value));
        } else if (contains(l, "batareya", "akkumulyator")) {
            specs.setBattery(firstSet(specs.getBattery(), value));
        } else if (contains(l, "əməliyyat sistemi")) {
            specs.setOperatingSystem(firstSet(specs.getOperatingSystem(), value));
        }

        // Always keep the raw pair too: the named fields are a convenience layer,
        // not a replacement for what the store actually published.
        specs.getAdditional().putIfAbsent(label, value);
    }

    private static boolean contains(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    /** Keep the first value we saw; earlier spec tables are the more specific ones. */
    private static String firstSet(String existing, String candidate) {
        return existing != null ? existing : candidate;
    }
}
