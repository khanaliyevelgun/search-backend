package az.pricecompare.domain;

/**
 * The stores we aggregate. Add a new store here and provide a matching
 * {@code StoreScraper} implementation to expand coverage.
 */
public enum StoreName {
    KONTAKT_HOME("Kontakt Home"),
    IRSHAD("Irshad Electronics"),
    SOLITON("Soliton");

    private final String displayName;

    StoreName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
