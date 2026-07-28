package az.pricecompare.domain;

/**
 * The stores we aggregate. Add a constant here and a matching
 * {@code StoreScraper} implementation to expand coverage.
 */
public enum StoreName {
    KONTAKT_HOME("Kontakt Home", "kontakt"),
    IRSHAD("İrşad Electronics", "irshad"),
    SOLITON("Soliton", "soliton");

    private final String displayName;
    private final String configKey;

    StoreName(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey = configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The slug used under {@code scraper.stores.*} in application.yml. */
    public String getConfigKey() {
        return configKey;
    }
}
