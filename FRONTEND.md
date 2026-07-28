# Frontend üçün qısa təlimat

Backend Spring Boot layihəsidir. Sənin işin sadəcə **bir endpoint**-dən istifadə etməkdir.

---

## 1. Nə lazımdır

- **JDK 21** — [Temurin 21](https://adoptium.net/) yüklə. Yoxla: `java -version`
- **Docker** (Postgres üçün ən asan yol)

Maven lazım deyil, layihənin içindədir (`./mvnw`).

## 2. Postgres qaldır

Bir dəfə bu əmri işlət:

```bash
docker run --name pricecompare-db -e POSTGRES_DB=pricecompare -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
```

Sonrakı dəfələr sadəcə:

```bash
docker start pricecompare-db
```

Cədvəlləri özü yaradır, əl ilə heç nə etmək lazım deyil.

## 3. Backend-i işə sal

```bash
./mvnw spring-boot:run
```

Windows-dasansa: `.\mvnw.cmd spring-boot:run`

İlk dəfə paketləri yükləyəcək, 1-2 dəqiqə çəkə bilər. Sonra **http://localhost:8080** üzərində işləyir.

Yoxlamaq üçün:

```bash
curl "http://localhost:8080/api/search?q=iphone%2016"
```

---

## 4. İstifadə edəcəyin endpoint

```
GET http://localhost:8080/api/search?q=iphone 16
```

Yalnız bu biridir. Başqa endpoint yoxdur.

**CORS artıq açıqdır** `localhost:3000` və `localhost:5173` üçün — React/Vite-dan birbaşa `fetch` edə bilərsən. Başqa portda işləyirsənsə mənə de.

---

## 5. Cavabın strukturu

```jsonc
{
  "query": "iphone 16",
  "fromCache": false,
  "tookMs": 4391,
  "storesQueried": ["KONTAKT_HOME", "IRSHAD", "SOLITON"],  // cavab verən mağazalar
  "storeErrors": [],                                        // cavab verməyənlər

  "results": [
    {
      "canonicalName": "Apple Iphone 16 128GB",
      "brand": "apple",
      "storage": "128GB",

      "lowestPrice": 2109.99,        // ən ucuz qiymət
      "highestPrice": 2299.99,       // ən baha qiymət
      "cheapestStore": "IRSHAD",     // hansı mağazada ucuzdur
      "maxSaving": 190.00,           // nə qədər qənaət edir
      "allColorsSeen": ["Black", "White", "Pink", "Teal"],  // bütün rənglər

      "stores": [                    // ⭐ ƏSAS HİSSƏ — hər mağaza üçün 1 sətir
        {
          "store": "IRSHAD",
          "storeDisplayName": "İrşad Electronics",   // ekranda bunu göstər
          "price": 2109.99,
          "oldPrice": null,                          // endirim varsa üstündən xətt çək
          "productUrl": "https://irshad.az/...",     // "Mağazaya keç" düyməsi
          "imageUrl": "https://...",
          "inStock": true,

          "colorsAvailable": ["Black", "White", "Pink", "Teal"],
          "colorsMissing": [],                       // ⭐ bu mağazada OLMAYAN rənglər

          "lowestMonthlyPayment": 117.22,            // "aylıq 117.22 ₼-dən"
          "creditOptions": [
            { "months": 12, "monthlyPayment": 117.22, "totalPayable": 1406.64,
              "overpayment": 0.00, "interestFree": true }
          ],

          "variantCount": 4,
          "offers": [ /* hər rəng üçün ayrı — detala girmək istəsən */ ]
        }
        // ... KONTAKT_HOME, SOLITON
      ]
    }
  ]
}
```

### Necə göstərmək

- `results` — hər biri bir məhsuldur (məs. iPhone 16 128GB və iPhone 16 256GB ayrı-ayrı).
- `results[].stores` — müqayisə cədvəlinin sətirləri. **Artıq ucuzdan bahaya sıralanıb.**
- `colorsMissing` — layihənin əsas fikri budur: "Kontakt-da qara yoxdur, İrşad-da var".
- `offers` — hər rəngin öz qiyməti/linki/spesifikasiyası. Detal səhifəsi üçün.
- Spesifikasiyalar (prosessor, RAM, ekran...) `offers[].specs` içindədir.

---

## 6. Bilməli olduğun vacib şeylər

| Mövzu | Nə bilməlisən |
|---|---|
| **Sürət** | İlk axtarış **4-8 saniyə** çəkir (3 sayta gedir). Mütləq loading göstər. |
| **Keş** | Eyni sorğu 30 dəqiqə keşdə qalır → dərhal gəlir. `fromCache: true` olur. |
| **Limit** | Dəqiqədə **20 sorğu**. Aşsan `429` qaytarır — istifadəçiyə "bir az gözlə" yaz. Ona görə hər hərfdə sorğu atma, debounce qoy. |
| **`storeErrors`** | Bəzən mağaza cavab vermir. Boş deyilsə "Soliton cavab vermədi" kimi göstər — gizlətmə. |
| **`price` null ola bilər** | Nadir haldır, amma yoxla. `colorsAvailable` da boş ola bilər. |
| **Axtarış konkret olmalıdır** | "telefon" yox, "iphone 16" / "samsung galaxy s25" kimi. Ümumi sözlərdə nəticə zəif olur. |
| **Xəta formatı** | Bütün xətalar belə gəlir: `{ "status": 400, "error": "...", "message": "..." }` |

Sorğu ən azı **2 simvol** olmalıdır, yoxsa `400` qaytarır.

---

## 7. Nümunə kod

```js
async function search(query) {
  const res = await fetch(
    `http://localhost:8080/api/search?q=${encodeURIComponent(query)}`
  );

  if (res.status === 429) throw new Error("Çox sorğu göndərdin, bir az gözlə");
  if (!res.ok) throw new Error((await res.json()).message);

  return res.json();
}
```

---

## Problem olsa

- **Backend qalxmır** → Postgres işləyirmi? `docker ps` yoxla.
- **Nəticə boş gəlir** → `storeErrors`-a bax, saytlar dəyişmiş ola bilər.
- **CORS xətası** → hansı portda işlədiyini de.
