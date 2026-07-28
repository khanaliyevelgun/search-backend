package az.pricecompare.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSpecs {

    private String processor;

    private String storage;

    private String ram;

    private String displaySize;

    private String battery;

    @Builder.Default
    private Map<String, String> additional = new LinkedHashMap<>();
}
