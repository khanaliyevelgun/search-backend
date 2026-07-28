package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Technical specs for one offer.
 *
 * The named fields are the ones users actually compare on; everything else the
 * store publishes lands in {@link #additional} keyed by the store's own label
 * (Azerbaijani), because normalizing hundreds of attribute names across three
 * stores would be a lot of work for little gain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecs {

    private String processor;

    private String storage;

    private String ram;

    private String displaySize;

    private String displayType;

    private String mainCamera;

    private String frontCamera;

    private String battery;

    private String operatingSystem;

    /** Everything else the store listed, in the store's own wording. */
    @Builder.Default
    private Map<String, String> additional = new LinkedHashMap<>();

    /** True when no field carries a value. */
    public boolean isEmpty() {
        return processor == null && storage == null && ram == null
                && displaySize == null && displayType == null
                && mainCamera == null && frontCamera == null
                && battery == null && operatingSystem == null
                && (additional == null || additional.isEmpty());
    }
}
