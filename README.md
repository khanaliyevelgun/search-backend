# Price Compare — Search Backend

A Spring Boot monolith that lets a user search one product (e.g. `iphone 16 pro max`)
and get it compared across three Azerbaijani electronics stores — **Kontakt Home,
İrşad, Soliton** — in a single request. Prices, colours, images, specs and
installment plans are scraped from each store, normalized, and matched so the same
phone lines up across stores for side-by-side comparison.

> The frontend is out of scope — this repo is the backend/API only.

---

## What it does

```
GET /api/search?q=iphone 16 pro max
```

1. **Cache check** — a normalized query hits a Caffeine cache (30-min TTL). On a
   hit, the stored comparison is returned instantly.
2. **Parallel search** — on a miss, all enabled store scrapers run concurrently.
   Each store gets a wall-clock budget; one slow or broken store never blocks the
   others, and failures are reported in `storeErrors`, not hidden.
3. **Relevance filter** — the stores' own search engines rank accessories above
   products, so hits that don't contain every query word, or that look like a case
   or cable, are dropped before they cost us anything.
4. **Enrich** — surviving offers get their product pages fetched in parallel for
   specs, installment plans, extra images, and (for Soliton) the price itself.
5. **Normalize & match** — messy titles are parsed into brand / model / storage /
   colour, then grouped so the same product from three stores becomes one row.
6. **Compare** — each group returns per-store summaries: price, colours stocked,
   **colours missing relative to the other stores**, and the cheapest monthly
   installment.

### Example response

```jsonc
{
  "query": "samsung galaxy s25",
  "fromCache": false,
  "fetchedAt": "2026-07-28T06:10:00Z",
  "tookMs": 4391,
  "storesQueried": ["KONTAKT_HOME", "IRSHAD", "SOLITON"],
  "storeErrors": [],
  "results": [
    {
      "canonicalName": "Samsung Galaxy S25 128GB",
      "brand": "samsung",
      "model": "galaxy s25",
      "storage": "128GB",
      "lowestPrice": 1749.99,
      "highestPrice": 1799.99,
      "cheapestStore": "SOLITON",
      "maxSaving": 50.00,
      "allColorsSeen": ["Navy", "Silver"],
      "totalOffers": 3,
      "stores": [
        {
          "store": "SOLITON",
          "storeDisplayName": "Soliton",
          "price": 1749.99,
          "maxPrice": 1749.99,
          "oldPrice": null,
          "productUrl": "https://soliton.az/...",
          "imageUrl": "https://soliton.az/...",
          "inStock": true,
          "colorsAvailable": ["Navy"],
          "colorsMissing": ["Silver"],
          "lowestMonthlyPayment": 88.21,
          "creditOptions": [
            { "months": 3, "monthlyPayment": 583.33, "totalPayable": 1749.99,
              "overpayment": 0.00, "interestFree": true }
          ],
          "variantCount": 1,
          "offers": [ /* one per colour variant, with specs */ ]
        }
        // ... İRŞAD, KONTAKT_HOME
      ]
    }
  ]
}
```

`colorsMissing` is the field that answers "Kontakt has silver but not navy" — it's
computed per store against the union of colours all stores carry.

---

## How each store is actually integrated

This is the part that breaks. All three sites had to be reverse-engineered, and
none of them is scraped from the page a human sees.

| Store | Endpoint used | Format | Why not the normal page |
|---|---|---|---|
| **Kontakt** | `/kontaktcatalog/multisearch/search/` | **JSON** | `/search/` renders products client-side; server HTML has only loading skeletons |
| **İrşad** | `/az/products/list?q=` | HTML fragment | `/az/mehsullar?q=` ships an empty `<div id="productGridItems">` filled over AJAX |
| **Soliton** | `/search.php?q=` | HTML | Server-rendered, but **carries no prices** — every price comes from the product page |

Other quirks handled in code:

- **Soliton's search is a strict AND** over all words. `iphone 16 pro max` returns
  zero results while `iphone 16` returns three, so `SolitonScraper` drops trailing
  words until something matches.
- **Kontakt's stock flag is optimistic.** Its API reports `In Stock` for products
  whose own page says `OutOfStock`; the page wins.
- **Kontakt shows `------` for installments on out-of-stock items** — those terms
  are skipped rather than invented.
- **Colour is a separate SKU everywhere.** "iPhone 16 128 GB Black" and "... White"
  are different listings, which is why colour is a variant dimension rather than
  part of a product's identity.
- **Part numbers differ per store.** İrşad writes `Galaxy S25 SM-S931`, Soliton
  just `Galaxy S25`; the normalizer strips `SM-xxxx` codes or the same phone splits
  into three separate comparisons.

---

## ⚠️ Before you put this in production

**All three stores disallow their search paths in `robots.txt`**, and İrşad
publishes `Crawl-delay: 30`:

```
kontakt.az:  Disallow: */search        ← covers the multisearch API
soliton.az:  Disallow: /search.php
irshad.az:   Crawl-delay: 30
```

This backend does not honour those directives — it paces requests per host
(`min-request-interval-ms`, 500–800ms) and caches aggressively, but 800ms is not
30 seconds. That is a deliberate, documented choice, not an oversight. Two things
follow:

1. **Get permission.** An affiliate or data-sharing agreement with these stores
   turns the whole project from adversarial to supported, and is worth an email
   before you launch publicly.
2. **Live scraping doesn't scale.** The long-term architecture is a scheduled
   crawler that walks each store's sitemap (İrşad and Soliton both publish one)
   into your own Postgres, with `/api/search` querying your own database. That
   also gets you price history, sub-50ms responses, and immunity to Cloudflare
   challenges. The existing `StoreScraper` implementations move over unchanged —
   they'd just run on a schedule instead of on a request.

Kontakt and Soliton both sit behind Cloudflare and will intermittently return a
`403 Just a moment...` challenge. `HtmlFetcher` retries with backoff, which clears
it most of the time; a sustained challenge shows up honestly in `storeErrors`.

---

## Tech stack

- **Java 21**, **Spring Boot 3.3**
- **Spring Web** (REST), **Spring Validation**
- **Spring Data JPA + PostgreSQL** (search history / analytics)
- **Caffeine** (result cache + rate-limit counters)
- **Jsoup** (HTML fetching and parsing), **Jackson** (Kontakt's JSON API)
- **Lombok**
- **JUnit 5 + AssertJ + Mockito + H2**

---

## Project layout

```
src/main/java/az/pricecompare/
├── SearchBackendApplication.java     # entry point
├── config/                           # scraper props, thread pools, cache
├── domain/                           # API DTOs (StoreOffer, StoreSummary, ...)
├── scraper/                          # fetching + shared scaffolding
│   └── impl/                         # KontaktHome / İrşad / Soliton scrapers
├── matching/                         # normalizer, colour vocabulary,
│                                     #   relevance filter, matcher
├── service/                          # ScrapingOrchestrator + SearchService
├── persistence/                      # SearchHistory entity + repository
└── web/                              # controllers, CORS, rate limit, errors
```

---

## Running it

### Prerequisites
- **JDK 21** (Maven is bundled via the wrapper)
- **PostgreSQL** running locally (or point env vars at a remote one)

### 1. Start PostgreSQL and create the DB
```bash
docker run --name pricecompare-db -e POSTGRES_DB=pricecompare \
  -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16
```

### 2. Configure (defaults shown; override via env vars)
```
DB_URL=jdbc:postgresql://localhost:5432/pricecompare
DB_USER=postgres
DB_PASSWORD=postgres
ADMIN_API_KEY=            # unset ⇒ /api/admin/** returns 404
CORS_ORIGINS=http://localhost:3000,http://localhost:5173
```

### 3. Run

```bash
./mvnw spring-boot:run
```

On Windows use `.\mvnw.cmd spring-boot:run`. App starts on **http://localhost:8080**.

### 4. Try it

```bash
curl "http://localhost:8080/api/search?q=samsung%20galaxy%20s25"
```

---

## Tests

```bash
./mvnw test
```

No database or network needed — H2 in-memory, and the scrapers are driven against
recorded fixtures.

| Suite | What it protects |
|---|---|
| `ScraperFixtureTest` | Every CSS selector and JSON path, against markup captured verbatim from the live sites (`src/test/resources/fixtures`) |
| `RelevanceFilterTest` | That a ₼2.99 phone case never gets reported as the cheapest iPhone |
| `ProductNormalizerTest` | Title parsing: colours, storage vs RAM, part numbers |
| `ProductMatcherTest` | Cross-store grouping and per-store colour rollups |
| `SearchApiTest` | HTTP contract, validation, rate limiting, admin auth |

### Checking the live stores

`LiveSearchSmokeTest` boots the app and hits the real sites. It is disabled by
default and must be asked for:

```bash
./mvnw test -Dtest=LiveSearchSmokeTest -Dlive.stores=true
```

Add `-Dlive.query="samsung galaxy s25"` to probe a different product. It prints a
full comparison table.

**If this fails while `ScraperFixtureTest` passes, a store changed its markup.**
Re-capture the relevant fixture and fix the selector.

> On a network behind a TLS-intercepting proxy the JDK won't trust the injected
> certificate even though `curl` does. Add
> `-DargLine="-Djavax.net.ssl.trustStoreType=KeychainStore -Djavax.net.ssl.trustStore=NONE"`
> on macOS, or import the proxy CA into the JDK truststore.

---

## Configuration reference (`application.yml`)

| Key | Meaning |
|-----|---------|
| `scraper.timeout-ms` | Per-request socket timeout |
| `scraper.store-budget-ms` | Wall-clock ceiling for one store's search + enrichment |
| `scraper.max-results-per-store` | Candidates pulled before relevance filtering |
| `scraper.max-enriched-per-store` | Survivors we spend a detail-page fetch on |
| `scraper.max-retries` | Retries per request (Cloudflare 403s usually clear) |
| `scraper.stores.<slug>.enabled` | Toggle a store without code changes |
| `scraper.stores.<slug>.search-url` | Real search endpoint; `{query}` is substituted |
| `scraper.stores.<slug>.min-request-interval-ms` | Politeness gap between requests to that host |
| `scraper.stores.<slug>.enrich` | Whether to fetch product pages for that store |
| `ratelimit.requests-per-minute` | Per-client cap on `/api/search` |
| `admin.api-key` | Token for `/api/admin/**`; unset disables the endpoint |
| `cors.allowed-origins` | Comma-separated frontend origins |

---

## Suggested next steps

- **Move to a scheduled crawler + own database** (see the warning above). This is
  the single highest-value change and everything else here survives it.
- Add `POST /api/refresh?q=` to force-bypass the cache.
- Move the schema to Flyway migrations (currently `ddl-auto: update`).
- Track price history so the UI can show "cheapest it's been in 3 months".
- Add more stores — implement `StoreScraper`, add a `StoreName` constant and a
  config block. Nothing else needs to change.
