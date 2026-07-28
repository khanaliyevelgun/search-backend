package az.pricecompare.matching;

import az.pricecompare.domain.StoreName;
import az.pricecompare.domain.StoreOffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Title parsing, driven by real titles copied from the three stores. */
class ProductNormalizerTest {

    private final ProductNormalizer normalizer = new ProductNormalizer();

    private NormalizedProduct normalize(String title) {
        return normalizer.normalize(StoreOffer.builder()
                .store(StoreName.KONTAKT_HOME)
                .rawTitle(title)
                .build());
    }

    @ParameterizedTest
    @CsvSource({
            "'iPhone 16 Pro Max 256 GB Black Titanium', Black Titanium",
            "'iPhone 16 128 GB Ultramarine',            Ultramarine",
            "'iPhone 16 128GB PINK',                    Pink",
            "'Smartfon Apple iPhone 16 Pro Max Qara',   Black",
            "'iPhone 17 256 GB Mist Blue',              Mist Blue",
            "'Samsung Galaxy S25 12/128 GB Silver',     Silver",
    })
    void detectsColour(String title, String expected) {
        assertThat(normalize(title).color()).isEqualTo(expected);
    }

    @Test
    void multiWordFinishesBeatTheirSingleWordSubstrings() {
        // "Natural Titanium" must not degrade to "Titanium".
        assertThat(normalize("iPhone 16 Pro Max 512 GB Natural Titanium").color())
                .isEqualTo("Natural Titanium");
    }

    @ParameterizedTest
    @CsvSource({
            "'iPhone 16 Pro Max 256 GB Black Titanium', 256GB, ",
            "'Samsung Galaxy S25 SM-S931B 12/128 GB',   128GB, 12GB",
            "'iPhone 16 128GB BLACK',                   128GB, ",
            "'Laptop Asus 16 GB RAM 1 TB',              1TB,   16GB",
    })
    void detectsStorageAndRam(String title, String storage, String ram) {
        NormalizedProduct n = normalize(title);
        assertThat(n.storage()).isEqualTo(storage);
        assertThat(n.ram()).isEqualTo(ram);
    }

    @Test
    void doesNotMistakeRamForStorage() {
        // 12 GB is below the storage floor, so it must be read as RAM only.
        NormalizedProduct n = normalize("Samsung Galaxy S25 12 GB");
        assertThat(n.storage()).isNull();
        assertThat(n.ram()).isEqualTo("12GB");
    }

    @Test
    void shortBrandNamesDoNotMatchInsideOtherWords() {
        // An earlier version used contains(), so "hp" fired on "Philips" and
        // "lg" on anything containing those letters.
        assertThat(normalize("Philips Sonicare toothbrush").brand()).isNotEqualTo("hp");
        assertThat(normalize("Bulgaria travel adapter").brand()).isNotEqualTo("lg");
    }

    @Test
    void appleFamilyWordsResolveToTheAppleBrand() {
        assertThat(normalize("iPhone 16 Pro Max").brand()).isEqualTo("apple");
        assertThat(normalize("MacBook Air M3").brand()).isEqualTo("apple");
    }

    /**
     * Taken from a live run: the same Galaxy S25 appeared as three separate
     * products because each store wrote the part number differently.
     */
    @Test
    void signatureIgnoresManufacturerPartNumbers() {
        String kontakt = normalize("Samsung Galaxy S25 SM-S931B 12/128 GB Silver").signature();
        String irshad = normalize("Samsung Galaxy S25 SM-S931 128 GB Navy").signature();
        String soliton = normalize("Samsung Galaxy S25 12/128GB NAVY").signature();

        assertThat(kontakt).isEqualTo(irshad).isEqualTo(soliton);
        assertThat(kontakt).isEqualTo("galaxy s25");
    }

    @Test
    void storageWrittenWithoutASpaceStillParses() {
        // "12/128GB" has no word boundary before "GB", which used to leave a
        // stray "12" in the signature.
        NormalizedProduct n = normalize("Samsung Galaxy S25 12/128GB NAVY");
        assertThat(n.storage()).isEqualTo("128GB");
        assertThat(n.ram()).isEqualTo("12GB");
        assertThat(n.signature()).doesNotContain("12");
    }

    @Test
    void differentModelTiersStayDistinct() {
        // Stripping part numbers must not collapse the S25 into the S25 FE.
        assertThat(normalize("Samsung Galaxy S25 SM-S931 128 GB").signature())
                .isNotEqualTo(normalize("Samsung Galaxy S25 FE SM-S731 128 GB").signature());
    }

    @Test
    void signatureIgnoresColourStorageAndStoreFluff() {
        // Three stores, three wordings, one signature.
        String a = normalize("iPhone 16 Pro Max 256 GB Black Titanium").signature();
        String b = normalize("Smartfon Apple iPhone 16 Pro Max 256Gb Qara").signature();
        String c = normalize("iPhone 16 Pro Max 256GB White Titanium").signature();

        assertThat(a).isEqualTo(b).isEqualTo(c);
        assertThat(a).isEqualTo("iphone 16 pro max");
    }
}
