# Price Compare — Search Backend

A Spring Boot monolith that lets a user search one product (e.g. `iphone 16 pro max`)
and get it compared across several Azerbaijani electronics stores — **Kontakt Home,
Irshad, Soliton** — in a single request. Prices, colors, images, specs and credit
plans are scraped from each store, normalized, and fuzzy-matched so the same phone
lines up across stores for side-by-side comparison.

> The frontend is out of scope — this repo is the backend/API only.

---

## What it does

```
GET /api/search?q=iphone 16 pro max
```

1. **Cache check** — a normalized query hits a Caffeine cache (30-min TTL). On a
   hit, the stored comparison is returned instantly.
2. **Parallel scrape** — on a miss, all enabled store scrapers run concurrently.
   Each store is bounded by its own timeout; one slow/broken store never blocks
   the others, and failures are reported, not hidden.
3. **Normalize** — each store's messy title (`Smartfon Apple iPhone 16 Pro Max
   256Gb Qara`) is parsed into brand / model / storage / RAM.
4. **Fuzzy match** — offers that refer to the same physical product are grouped
   using token-set similarity + brand/storage agreement.
5. **Compare** — each group is returned with computed highlights: cheapest store,
   price range, potential saving, and the union of all colors seen.

### Example response shape

```jsonc
{
  "query": "iphone 16 pro max",
  "fromCache": false,
  "fetchedAt": "2026-07-13T10:00:00Z",
  "storesQueried": ["KONTAKT_HOME", "IRSHAD", "SOLITON"],
  "storeErrors": [],
  "results": [
    {
      "canonicalName": "Apple Iphone 16 Pro Max 256GB",
      "brand": "apple",
      "lowestPrice": 2000,
      "highestPrice": 2200,
      "cheapestStore": "KONTAKT_HOME",
      "maxSaving": 200,
      "allColorsSeen": ["Titanium", "Qara"],
      "offers": [
        {
          "store": "KONTAKT_HOME",
          "rawTitle": "Apple iPhone 16 Pro Max 256GB Natural Titanium",
          "productUrl": "https://kontakt.az/...",
          "price": 2000,
          "oldPrice": null,
          "currency": "AZN",
          "inStock": true,
          "availableColors": ["Titanium"],
          "imageUrls": ["https://..."],
          "creditOptions": [
            { "months": 12, "monthlyPayment": 175.50, "totalPayable": 2106, "interestFree": false }
          ],
          "specs": { "storage": "256GB", "ram": null, "processor": null }
        }
        // ... IRSHAD, SOLITON offers
      ]
    }
  ]
}
```

---

## Tech stack

- **Java 21**, **Spring Boot 3.3**
- **Spring Web** (REST), **Spring Validation**
- **Spring Data JPA + PostgreSQL** (search history / analytics)
- **Caffeine** (in-memory result cache, 30-min TTL)
- **Jsoup** (HTML scraping)
- **Lombok** (boilerplate reduction)
- **JUnit 5 + AssertJ + H2** (tests, no DB needed)

---

## Project layout

```
src/main/java/az/pricecompare/
├── SearchBackendApplication.java     # entry point
├── config/                           # scraper props, async pool, cache
├── domain/                           # API DTOs (StoreOffer, ProductComparison, ...)
├── scraper/                          # scraping abstraction + shared parsing
│   └── impl/                         # KontaktHome / Irshad / Soliton scrapers
├── matching/                         # ProductNormalizer + ProductMatcher (fuzzy)
├── service/                          # ScrapingOrchestrator + SearchService (cache)
├── persistence/                      # SearchHistory entity + repository
└── web/                              # controllers, CORS, error handling
```

---

## Running it

### Prerequisites
- **JDK 21** (only requirement — Maven is bundled via the wrapper)
- **PostgreSQL** running locally (or point env vars at a remote one)

Verify Java:
```powershell
java -version    # must be 21+
```
If you don't have it: install [Temurin JDK 21](https://adoptium.net/) (Windows).

### 1. Start PostgreSQL and create the DB
```sql
CREATE DATABASE pricecompare;
```
Or with Docker:
```bash
docker run --name pricecompare-db -e POSTGRES_DB=pricecompare \
  -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
```

### 2. Configure connection (defaults shown; override via env vars)
```
DB_URL=jdbc:postgresql://localhost:5432/pricecompare
DB_USER=postgres
DB_PASSWORD=postgres
```

### 3. Build & run
```powershell
.\mvnw.cmd spring-boot:run
```
(First run downloads Maven, then dependencies — give it a minute.)

App starts on **http://localhost:8080**.

### 4. Try it
```
http://localhost:8080/api/search?q=iphone 16 pro max
```

### Run tests (no Postgres needed — uses H2)
```powershell
.\mvnw.cmd test
```

---

## ⚠️ Important: the scrapers need real selectors

I could not inspect the live sites while writing this, so the CSS selectors in
each scraper are **educated-guess placeholders**. The architecture, matching,
caching and API are complete and tested; the selectors are the one part you must
calibrate against each store's real HTML.

Each scraper keeps its selectors in one small block:

- `scraper/impl/KontaktHomeScraper.java` → `SELECTORS`
- `scraper/impl/IrshadScraper.java` → `SELECTORS`
- `scraper/impl/SolitonScraper.java` → `SELECTORS`
- `buildSearchUrl(...)` in each → confirm the real search URL/param

### How to fix a scraper
1. Open the store's search page in a browser, e.g. search "iphone 16".
2. Right-click a product card → **Inspect**.
3. Note the class/structure of: the card container, the title link, the price,
   the old price, the image.
4. Update the matching strings in that store's `SELECTORS` record.
5. Update `buildSearchUrl` if the search path/param differs from `/search?q=`.
6. Re-run and check the logs: each scraper logs how many offers it found.

### Things to watch when scraping these sites
- **JS-rendered content**: if a store renders products with JavaScript, Jsoup
  (which sees only initial HTML) won't find them. You'd then switch that store to
  a headless browser (e.g. Playwright/Selenium) or call the store's internal JSON
  API if it has one (check the Network tab). The `StoreScraper` interface makes
  this a drop-in swap for a single store.
- **Blocking**: a realistic User-Agent is set. If a store blocks you, add delays,
  rotate proxies, or reduce frequency. Respect each site's `robots.txt` and terms.
- **Detail pages**: colors, full spec tables and credit plans usually live on the
  product page, not the search grid. The current scrapers populate what's on the
  grid; extend each scraper to fetch `productUrl` and parse those fields. The DTOs
  (`ProductSpecs`, `CreditOption`, `availableColors`) are already there for it.

---

## Configuration reference (`application.yml`)

| Key | Meaning |
|-----|---------|
| `scraper.timeout-ms` | Per-store fetch timeout |
| `scraper.user-agent` | Browser UA sent to stores |
| `scraper.max-results-per-store` | Cap on offers parsed per store |
| `scraper.stores.<slug>.enabled` | Toggle a store without code changes |
| `scraper.stores.<slug>.base-url` | Store base URL |

---

## Suggested next steps
- Add detail-page enrichment (colors / specs / credit plans) per store.
- Add a `POST /api/refresh?q=` to force-bypass the cache.
- Move DB schema to Flyway migrations (currently `ddl-auto: update` for dev).
- Add rate limiting + auth on `/api/admin/**`.
- Add integration tests that feed saved HTML fixtures into each scraper (so you
  can test parsing without hitting the live sites).
```



Nə etməlidir (3 addım):

JDK 21 yükləsin (Temurin)
Postgres — bir dəfə bu əmr, vəssalam:
docker run --name pricecompare-db -e POSTGRES_DB=pricecompare -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
İşə salsın:
./mvnw spring-boot:run
Cədvəlləri backend özü yaradır, SQL yazmağa ehtiyac yoxdur. Maven də layihənin içindədir.

Nə bilməlidir:

Cəmi bir endpoint var: GET localhost:8080/api/search?q=iphone 16
CORS artıq açıqdır localhost:3000 və 5173 üçün — başqa portda işləyirsə sən mənə de, əlavə edim
İlk axtarış 4-8 saniyə çəkir (3 sayta gedir) → loading mütləqdir. Sonra 30 dəqiqə keşdən ani gəlir
Dəqiqədə 20 sorğu limiti var, 429 qaytarır → axtarış inputuna debounce qoysun
Əsas hissə results[].stores[] — hər mağaza üçün bir sətir, artıq ucuzdan bahaya sıralanıb
colorsMissing — layihənin əsas fikri. "Kontakt-da qara yoxdur, İrşad-da var" məhz bu sahədən çıxır
storeErrors boş deyilsə göstərsin — bəzən mağaza cavab vermir, gizlətmək düzgün deyil
Sənədin içində cavabın tam JSON strukturu sahə-sahə izahla və hazır fetch nümunəsi də var.


