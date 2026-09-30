# Dignify — Backend

> A music discovery platform where users swipe through 10–20 second previews to find new tracks.  
> Built as a personal project to gain hands-on experience with production-level server-side challenges — caching, concurrency, cloud infrastructure, and data pipeline design.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5, Spring Security, Spring Data JPA |
| Database | PostgreSQL (Cloud SQL) |
| Cloud | GCP Cloud Run, Artifact Registry |
| Auth | Apple Sign In / Google Sign-In (both ID-token verified server-side), JWT (HS256) |
| Push | APNs direct (pushy) + FCM (firebase-admin) |
| Music Source | iTunes Lookup API |
| Recommendation | CLAP audio embeddings → 32-dim PCA vectors in `track_vectors` |
| Build | Gradle |

---

## Key Implementation Highlights

### 1. Sign In with Apple and Google (JWKS Verification)
Both providers' identity tokens are verified server-side without an auth SDK: fetch the provider's public key set (JWKS) via `JWKSource`, validate the RS256 signature, and check `iss`, `aud` and `exp` directly.

- Signing keys cached for 24 hours to avoid a network round trip to the JWKS endpoint per login
- All verification failures mapped to typed `ErrorCode` values for consistent error responses
- Google's `aud` is a configured value, not a constant — the Android app's *web* OAuth client ID has to be swappable between debug and release. An unset value leaves the app bootable and only fails logins, so a deploy never breaks on a missing secret
- The two clients are deliberately not pulled into a shared parent: what they share is the order of nimbus calls, while what differs (Google issues both `accounts.google.com` and `https://accounts.google.com`, Apple has no `email_verified`) would just push branching up into the parent

### 2. JWT Authentication Filter Chain
Built a stateless authentication layer using Spring Security:

- `JwtAuthenticationFilter` extracts Bearer tokens, validates signatures and expiry, and populates `SecurityContextHolder`
- `JwtAuthenticationEntryPoint` handles authentication failures at the filter level — before the MVC dispatcher — and writes structured JSON error responses directly
- `Clock` injection into `JwtProvider` enables deterministic unit tests for token expiry scenarios without mocking the system clock

### 3. Mood-Ordered Feed (Vector Similarity)
The feed used to be a two-phase genre filter — preferred genres first, general tracks as padding. Since the genre picker was removed from the app (2026-08-24) the server no longer reads `user_genres`: every user sees the whole catalog, and only the **ordering** changes.

**Track vectors.** Each preview is embedded with CLAP (three 10-second windows, averaged), reduced to 32 dimensions by PCA, and L2-normalised so a dot product *is* cosine similarity. They live in `track_vectors` — 32 `real` columns, no JPA entity, loaded by a script. 32 dims is a latency choice, not a quality one: 64 scored the same but pushed a 3-seed scan to 226ms.

**Seeds, not an average.** The 3 most recent hypes (or the tracks the user pinned via `PUT /users/me/seeds`) each act as an independent seed, and tracks are ranked by `GREATEST(dot(v, seed0), …)`. Averaging the seeds would point at the centre of mood space — a track with no character at all.

**Per-seed quota.** `ROW_NUMBER() OVER (PARTITION BY <winning seed>)` then `ORDER BY rn`. Ranking by similarity alone lets whichever seed sits in a dense region take every slot on the page.

**Filter outside the scan.** Inactive, already-hyped, curated and near-duplicate tracks (`sim >= 0.95` — remasters, instrumentals and covers were 24.7% of nearest neighbours) are removed in an outer query. Joining them into the dot-product scan measured +67ms (tracks) and +203ms (hypes) in production.

**Scan window `K = (limit + offset) * 6 + 120`.** Because filtering happens *after* the top-K cut, K has to be generous: with K=40 a seed sitting inside a cover cluster had only 2 of its top 40 survive. K is nearly free — the cost is reading all ~85k vectors, not the sort heap (K=300 → 23.4ms, K=1740 → 25.8ms).

**A short page is not an empty catalog.** Exhaustion is decided by comparing K against `count(*)` on `track_vectors`, never by page length — a page can come back short simply because the scan window was filtered out, and cutting the cursor there would end that user's feed with 20k tracks still below.

**Three paths, one response shape.** Mood ordering → `ColdStartRecommender` (guests and users with no hype yet: the top-120 tracks by weighted engagement, shuffled to 60, then a greedy pick of 30 that are mood-*distant* from each other — the raw popularity top 30 averages 0.33 pairwise similarity, so the first screen would otherwise be one colour) → plain randomised order. Users who turn digging mode off (`PATCH /users/me/digging-mode`) skip straight to random. Each request logs which path it took.

**Cursor.** `(phase, genreOffset, generalOffset, seed)` Base64-encoded as an opaque string; the client never parses it. The offset advances by a full page even when the page came back short, so a narrow window can't make consecutive requests circle the same spot.

### 4. Asynchronous Cron Job with `@Async`
> **Retired 2026-08-10.** The brute-force ID scan below loaded tracks faster than users could get through them, and too many were noise. The code is still here, but the catalog now grows only through artist requests: an admin resolves the requested name to an iTunes `artistId` (stopping on same-name artists so a human picks the right one) and collects that artist's catalog from the admin page on Cloud Run. See *Data Ingestion* below.

Implemented a long-running iTunes track collection job that runs without blocking the HTTP thread:

- Controller returns `202 Accepted` immediately; the actual loop runs in Spring's async thread pool
- Accepts `endIndex` query parameter to control how many iTunes IDs to scan per run
- Each batch processes 200 sequential iTunes IDs, persists valid tracks, and sleeps 30 seconds to respect iTunes rate limits
- Per-track transactions with `REQUIRES_NEW` propagation — a duplicate key violation on one track rolls back only that track, not the entire batch

### 5. Cloud SQL Auth Proxy for Local Data Ingestion
The brute-force scan (§4) got Broken Pipe from the iTunes API on Cloud Run, so it ran locally and wrote directly to Cloud SQL via a secure tunnel. Per-artist lookups do work from Cloud Run, which is why artist collection moved to the admin page:

- Cloud SQL Auth Proxy creates a local TCP tunnel authenticated via Application Default Credentials
- Spring Boot connects to `localhost:5433`; the proxy forwards to Cloud SQL over an encrypted channel — no public IP exposure
- `run-cron.sh` script automates the full workflow: ADC tunnel → Spring Boot startup → cron trigger → log streaming, with `caffeinate` to prevent macOS sleep during long runs

### 6. Transaction Isolation for Batch Writes
Separated `CronBatchService` and `TrackSaveService` into distinct Spring beans to avoid self-invocation proxy bypass:

- `CronBatchService.processBatch()` runs in its own `@Transactional` context — updates `cron_state.last_processed_id` atomically with each batch
- `TrackSaveService.saveTrack()` uses `REQUIRES_NEW` — each track save is an independent transaction, so `DataIntegrityViolationException` on duplicates is caught and logged without rolling back the parent batch

### 7. Database Index Design
PostgreSQL does not auto-create indexes on foreign key columns (unlike MySQL). Explicitly added indexes on all hot-path FK columns:

| Index | Table | Column | Use Case |
|---|---|---|---|
| `idx_track_genre_id` | `tracks` | `genre_id` | Per-genre stats (digging profile, admin) — the feed stopped filtering by genre on 2026-08-24 |
| `idx_listened_track_user_id` | `listened_tracks` | `user_id` | Listening history lookup |
| `idx_listened_track_track_id` | `listened_tracks` | `track_id` | Cascade / analytics |
| `idx_user_auth_user_id` | `user_auth` | `user_id` | Auth provider lookup per login |
| `idx_user_token_user_id` | `user_tokens` | `user_id` | Token validation per request |
| `idx_artist_request_user_id` | `artist_requests` | `user_id` | My submitted requests |
| `idx_device_token_user_id` | `user_device_tokens` | `user_id` | Push fan-out per user |

Two index families sit outside JPA because `ddl-auto` cannot express them:

- **Trigram GIN indexes for search.** The search query runs `translate(LOWER(col), …) LIKE '%kw%'` over four columns, and a leading `%` cannot use a B-tree, so every search scanned the whole table. Four `pg_trgm` GIN indexes on the exact same expression fixed it — 314ms → 2.0ms for a worst-case keyword locally, at 18MB total (18% of the table). The index expression must match `TrackRepository.FOLD_FROM/FOLD_TO` character for character; a mismatch silently falls back to a sequential scan instead of erroring.
- **A partial index on `picks`** — the list query filters `WHERE is_deleted = FALSE`, which `@Index` cannot express — and the whole of `track_vectors`, which has no entity at all.

### 8. Test-Gated CI/CD to Cloud Run
A GitHub Actions workflow (`.github/workflows/deploy.yml`) builds, tests, and deploys on every push to `main`:

- **`test` job** spins up a `postgres:16` service container and runs `./gradlew test`. If tests fail, the workflow stops and `deploy` never runs.
- **`deploy` job** (`needs: test`) authenticates to GCP via **Workload Identity Federation** (OIDC, `id-token: write`) — no long-lived service account keys stored in the repo.
- Builds a `linux/amd64` image, pushes to Artifact Registry tagged with the commit SHA, and rolls it out to Cloud Run.

> **Known limitation:** the CI Postgres uses the same `ddl-auto=create-drop` as tests, so the schema is regenerated from JPA entities each run, while production runs `ddl-auto=update`. This validates that entities map cleanly, but does **not** catch drift against the production schema — there are no migrations yet, and hand-written DDL (trigram indexes, `track_vectors`) is invisible to both. A Flyway/Liquibase baseline is the natural next step.

---

## Testing

Wrote tests at multiple layers with a clear separation of concerns between unit, slice, and integration tests.

| Test Class | Type | What It Covers |
|---|---|---|
| `JwtProviderTest` | Unit (`@ExtendWith`) | Token generation/validation, expiry via injected `Clock` |
| `AppleAuthClientTest` | Unit (`@ExtendWith`) | 8 scenarios: malformed token, algorithm mismatch, empty JWK set, wrong signing key, invalid claims, expiry, happy path |
| `GoogleAuthClientTest` | Unit (`@ExtendWith`) | Google ID token: signature, `aud`/`iss` mismatch, expiry |
| `MoodRecommenderTest` | Unit | Generated scan SQL: parameter order, placeholder count, per-seed quota, where each exclusion is applied, scan window depth, `bestSeed` |
| `ColdStartRecommenderTest` | Unit | Pool query conditions, greedy spread picks the most distant next track, window boundary → empty list |
| `OnboardingServiceTest` | Unit | One HIGH/LOW pair per axis, axis with a missing pole skipped, pair order shuffled, random pick within a pole |
| `GenreServiceTest` | Unit (Mockito) | Locale-based genre name selection (ko / en fallback) |
| `GlobalExceptionHandlerTest` | Slice (`@WebMvcTest`) | All 5 exception handlers mapped to correct HTTP status and `ErrorCode` |
| `JwtAuthenticationTest` | Integration (`@SpringBootTest`) | Filter chain: missing token / malformed / expired / valid / public path |
| `AuthServiceIntegrationTest` | Integration (`@SpringBootTest`) | Full auth lifecycle: sign-in → token rotation → soft-delete → re-registration cascade |
| `TrackRepositoryTest` | Slice (`@DataJpaTest`) | Feed queries: limit/offset, `isActive`, `user_genres` no longer narrows candidates, search, ko enrichment |
| `UserHypeRepositoryTest` | Slice (`@DataJpaTest`) | Keyset pagination, exists/find queries |
| `OnboardingCandidateRepositoryTest` | Slice (`@DataJpaTest`) | Deactivated candidates excluded |
| `FeedServiceTest` | Slice (`@DataJpaTest`) | Path selection (mood / cold start / random), seed pinning, digging mode off, hype exclusion, page shuffle stable across refetch |
| `HypeServiceTest` | Integration (`@SpringBootTest`) | Hype register / delete / duplicate detection |

### Notable Testing Patterns

**Clock injection for deterministic JWT expiry tests**  
`JwtProvider` accepts a `java.time.Clock` via constructor injection. Tests pass a fixed past-time `Clock` to generate already-expired tokens without manipulating the system clock or mocking JJWT internals.

**Test Data Builder for Apple token scenarios**  
`AppleAuthClientTest` uses a `TestBuilder` inner class that defaults to a valid token and lets each test override only the field it needs (issuer, audience, signing key, expiry). Avoids repetitive setup while keeping each scenario explicit.

**`@WebMvcTest` with a dedicated top-level `TestController`**  
`GlobalExceptionHandlerTest` uses a separate top-level controller class that intentionally throws each exception type. Nested static controllers inside the test class are silently ignored by Spring's `RequestMappingHandlerMapping` — a non-obvious pitfall discovered during development.

**`@DataJpaTest` with `@Import(JpaAuditingConfig.class)`**  
`@DataJpaTest` excludes custom `@Configuration` beans by default. `JpaAuditingConfig` must be explicitly imported, otherwise `created_at NOT NULL` violations occur at test time — the opposite problem of `@WebMvcTest`, where the same config class causes "JPA metamodel must not be empty."

**`@DataJpaTest` pinned to real PostgreSQL**  
`@DataJpaTest` swaps in an embedded H2 database by default. The feed queries use PostgreSQL-specific SQL (`md5()`, `::text` casts, native `LIMIT`/`OFFSET`), which H2 cannot execute. The slice is pinned to a real Postgres instance (`@AutoConfigureTestDatabase(replace = NONE)`) so repository tests exercise the exact SQL that runs in production — the same `postgres:16` container CI uses.

---

## API Overview

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/apple` | Sign in with Apple identity token |
| POST | `/auth/google` | Sign in with Google ID token (Android) |
| POST | `/auth/refresh` | Rotate refresh token, issue new access token |
| POST | `/auth/logout` | Invalidate refresh token |
| POST | `/auth/withdraw` | Soft-delete account, cascade token cleanup |
| GET | `/genres` | List genres that have active tracks (i18n: `Accept-Language` ko/en) |
| GET | `/feed` | Paginated track feed, mood-ordered, opaque cursor (guests allowed) |
| GET | `/feed/curation` | This week's curated set — same for everyone, no paging |
| GET | `/feed/search` | Keyword search across track/artist name (accent- and quote-folded) |
| GET | `/onboarding/seed-pool` | Fixed list of tracks the onboarding screen lets a new user pick from |
| GET | `/onboarding/candidates` | Two-choice sound rounds — kept for app builds before iOS 1.1.1 |
| GET | `/tracks/{trackId}` | Track detail + first 5 users who hyped it |
| POST | `/tracks/{trackId}/hype` | Hype a track |
| DELETE | `/tracks/{trackId}/hype` | Remove hype |
| POST | `/tracks/{trackId}/listen` | Record a listen event (fire-and-forget, append-only) |
| GET | `/users/me` | User profile |
| PATCH | `/users/me/nickname` | Update nickname |
| PUT | `/users/me/genres` | Replace preferred genres — kept for older clients; the feed no longer reads them |
| PATCH | `/users/me/digging-mode` | Toggle personalisation off (feed falls back to random) |
| PUT | `/users/me/seeds` | Pin the tracks recommendations are based on (empty array clears) |
| POST | `/users/me/onboarding/complete` | Mark onboarding as done |
| GET | `/users/me/hypes` | Paginated hype history (keyset pagination) |
| GET | `/users/me/stats` | Listening/hype aggregates for the digging profile (`range=all\|week`) |
| POST | `/users/me/device-token` | Register a push token (APNs or FCM, by platform) |
| GET | `/picks` | Shared track collections, keyset cursor (`mine=true` for my own) |
| POST | `/picks` | Create a pick from 1–30 tracks |
| GET | `/picks/{pickId}` | Tracks in a pick, in feed response shape; counts a play |
| DELETE | `/picks/{pickId}` | Delete my pick |
| PUT/DELETE | `/picks/{pickId}/reaction` | Set or clear my emoji reaction (5 allowed) |
| PUT | `/picks/{pickId}/title` | Rename my pick (profanity-filtered) |
| POST | `/reports` | Report a track or pick |
| POST | `/artist-requests` | Request an artist to be added to the catalog |
| GET | `/artist-requests` | My submitted requests |
| DELETE | `/artist-requests/{id}` | Cancel my own request |

Internal routes are guarded by an `X-Cron-Secret` header instead of a JWT: `/internal/cron/*` (collection, Korean-name enrichment) checks `CRON_SECRET`, while `/internal/admin/*` (curation sets, artist requests, backfill batches, push) checks a separate `ADMIN_SECRET` that falls back to the cron one. The admin routes back a single-page UI served at `/internal/admin.html`.

---

## Architecture

### GCP Infrastructure

```
┌─────────────────────────────────────────────────────────────────┐
│  GCP Project: dignify  (us-central1)                            │
│                                                                 │
│  ┌──────────────────────┐     ┌───────────────────────────┐    │
│  │  Artifact Registry   │     │       Cloud Run           │    │
│  │  (Docker Image)      │────▶│   Spring Boot 3.5 / Java  │    │
│  └──────────────────────┘     │   1Gi · min 1 / max 20    │    │
│                               └────────────┬──────────────┘    │
│                                            │ Java connector     │
│                                            │ mTLS, no public IP │
│                               ┌────────────▼──────────────┐    │
│                               │   Cloud SQL               │    │
│                               │   PostgreSQL 16           │    │
│                               │   db-f1-micro / Enterprise│    │
│                               └───────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
         ▲
         │ HTTPS
         │
  ┌───────────────┐
  │  iOS (SwiftUI)│
  │  + Android    │
  └───────────────┘
```

> **Why `min-instances 1` rather than scale-to-zero?** A cold start takes ~22 seconds — JVM boot plus the first Cloud SQL connection — and at this traffic level almost every request would pay it. Memory is 1Gi because 512Mi was OOM-killed roughly once a day; the app's baseline footprint is flat at about half of 1Gi regardless of traffic.

### Data Ingestion Pipeline

**Today (since 2026-08-10):** tracks are added per artist from the admin page (`/internal/admin.html` → artist requests → collect). It calls the live Cloud Run service, so no local process is needed. Korean-name enrichment and the `artist_id` backfill also run there, one 190-track batch per request, because Cloud Run throttles CPU outside a request and a long `@Async` loop would stall.

**Retired — brute-force scan (Local → Cloud SQL).** Kept for reference. The job ran locally and wrote directly to Cloud SQL via an encrypted proxy tunnel.

```
┌──────────────────────────────────────────────────────┐
│  Local Machine (macOS)                               │
│                                                      │
│  ┌─────────────────┐    localhost:5433               │
│  │  Spring Boot    │──────────────────┐              │
│  │  (bootRun)      │                  │              │
│  └────────┬────────┘     ┌────────────▼───────────┐  │
│           │              │  Cloud SQL Auth Proxy  │  │
│  POST /internal/         │  (ADC authenticated)   │  │
│  cron/collect            └────────────┬───────────┘  │
│           │                           │ TLS tunnel    │
│  ┌────────▼────────┐                  │              │
│  │  iTunes Lookup  │       ┌──────────▼──────────┐   │
│  │  API (brute     │       │  Cloud SQL          │   │
│  │  force scan)    │       │  PostgreSQL 16      │   │
│  └─────────────────┘       └─────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

> **Why not Cloud Run for ingestion?**  
> Apple's iTunes API returns `Broken Pipe` errors for requests originating from GCP datacenter IP ranges. Running the cron job locally via Cloud SQL Auth Proxy is a practical workaround that keeps the data pipeline functional without a proxy or residential IP service.

---

## Project Structure

```
src/main/java/com/rta/dignify/
├── client/          # External API clients (Apple JWKS, Google, iTunes Lookup)
├── config/          # APNs / FCM client beans
├── controller/      # REST controllers
├── domain/          # JPA entities
├── dto/             # Request/response DTOs
├── global/
│   ├── config/      # Spring configs (Security, JPA Auditing, Async, Clock)
│   ├── exception/   # BusinessException, ErrorCode, GlobalExceptionHandler
│   ├── jwt/         # JwtProvider
│   ├── security/    # JwtAuthenticationFilter, JwtAuthenticationEntryPoint
│   └── util/        # TokenHasher (SHA-256)
├── repository/      # Spring Data JPA repositories
└── service/
    ├── cron/        # Collection and enrichment batches
    └── *.java       # Feed, mood/cold-start recommenders, picks, push, stats
```

---

## Running Locally

### Everyday development (local Postgres)

```bash
docker compose up -d postgres
set -a && source .env && set +a
./gradlew bootRun
```

**`.env` must be exported by hand.** Docker Compose reads it automatically; Gradle does not.
`bootRun` only sees shell environment variables, so skipping the `source` line fails startup with
`Could not resolve placeholder 'CRON_SECRET'`. The database connects fine either way — the app
falls back to `localhost:5432`, which is the Postgres container.

**The `app` service is intentionally left down.** Its `Dockerfile` copies `src` into the image and
builds the jar there, with no bind mount, so a running container serves whatever the source looked
like when the image was built. Every code change needs `docker compose up -d --build app`.
`bootRun` recompiles on each run instead. Use the container only for a final pre-deploy check.

The three lines above are wrapped in a `dgrun` shell function in `~/.zshrc` (personal, not in this
repo). Its body is parenthesised rather than braced so that `cd` and `set -a` run in a subshell and
never leak into the calling shell:

```bash
dgrun() (
  cd ~/Desktop/toy_project/digging/dignify-backend || return 1
  docker compose up -d postgres || return 1
  set -a; source .env; set +a
  ./gradlew bootRun
)
```

### Tests

```bash
docker compose exec postgres psql -U dignify -d dignify -c 'CREATE DATABASE dignify_test'  # once
./gradlew test
```

Tests run against real Postgres, not H2 — the feed SQL is PostgreSQL-specific and H2 also differs on cascade and flush ordering. They use a **separate `dignify_test` database** because `ddl-auto=create-drop` would otherwise take the development data with it (it did, once).

### Data ingestion (with Cloud SQL)

Day-to-day ingestion happens in the admin page; nothing needs to run locally. `run-cron.sh` is still around for the retired brute-force scan and as a terminal fallback for the admin actions (`./run-cron.sh -h` lists them):

```bash
./run-cron.sh collect <endIndex>        # retired brute-force scan, e.g. 50000000
./run-cron.sh collect-artist "Radiohead"
```

It starts its own `cloud-sql-proxy` on port 5433, runs `bootRun` against it, triggers the job and streams logs, with `caffeinate` to keep macOS awake. Note it opens a second connection pool against the same Cloud SQL instance as production.
