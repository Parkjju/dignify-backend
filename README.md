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
| Auth | Apple Sign In (JWKS verification), JWT (HS256) |
| Music Source | iTunes Lookup API |
| Build | Gradle |

---

## Key Implementation Highlights

### 1. Apple Sign In with JWKS Verification
Implemented Apple identity token verification without relying on third-party libraries. Fetches Apple's public key set (JWKS) via `JWKSource`, validates the RS256 signature, and verifies `iss`, `aud`, and `exp` claims directly.

- JWT signing key cached for 24 hours to avoid repeated network calls to Apple's JWKS endpoint
- All verification failures mapped to typed `ErrorCode` values for consistent error responses

### 2. JWT Authentication Filter Chain
Built a stateless authentication layer using Spring Security:

- `JwtAuthenticationFilter` extracts Bearer tokens, validates signatures and expiry, and populates `SecurityContextHolder`
- `JwtAuthenticationEntryPoint` handles authentication failures at the filter level — before the MVC dispatcher — and writes structured JSON error responses directly
- `Clock` injection into `JwtProvider` enables deterministic unit tests for token expiry scenarios without mocking the system clock

### 3. Feed Pagination with Opaque Cursor
Designed a two-phase feed algorithm that prioritizes user-preferred genres (70%) before falling back to general tracks (30%):

- Phase 1: `INNER JOIN user_genres` to filter tracks matching user preferences
- Phase 2: `LEFT JOIN user_genres ... WHERE IS NULL` anti-join pattern to serve non-preferred tracks
- Cursor encodes `(phase, genreOffset, generalOffset, seed)` as a Base64 opaque string — client never parses the internals
- Native queries with explicit `LIMIT`/`OFFSET` binding (JPQL doesn't support literal `LIMIT`)

### 4. Asynchronous Cron Job with `@Async`
Implemented a long-running iTunes track collection job that runs without blocking the HTTP thread:

- Controller returns `202 Accepted` immediately; the actual loop runs in Spring's async thread pool
- Accepts `endIndex` query parameter to control how many iTunes IDs to scan per run
- Each batch processes 200 sequential iTunes IDs, persists valid tracks, and sleeps 30 seconds to respect iTunes rate limits
- Per-track transactions with `REQUIRES_NEW` propagation — a duplicate key violation on one track rolls back only that track, not the entire batch

### 5. Cloud SQL Auth Proxy for Local Data Ingestion
iTunes API blocks GCP datacenter IPs (Broken Pipe on Cloud Run). Solved by running the cron job locally and writing directly to Cloud SQL via a secure tunnel:

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
| `idx_track_genre_id` | `tracks` | `genre_id` | Feed genre filter |
| `idx_listened_track_user_id` | `listened_tracks` | `user_id` | Listening history lookup |
| `idx_listened_track_track_id` | `listened_tracks` | `track_id` | Cascade / analytics |
| `idx_user_auth_user_id` | `user_auth` | `user_id` | Auth provider lookup per login |
| `idx_user_token_user_id` | `user_tokens` | `user_id` | Token validation per request |

---

## Testing

Wrote tests at multiple layers with a clear separation of concerns between unit, slice, and integration tests.

| Test Class | Type | What It Covers |
|---|---|---|
| `JwtProviderTest` | Unit (`@ExtendWith`) | Token generation/validation, expiry via injected `Clock` |
| `AppleAuthClientTest` | Unit (`@ExtendWith`) | 8 scenarios: malformed token, algorithm mismatch, empty JWK set, wrong signing key, invalid claims, expiry, happy path |
| `GenreServiceTest` | Unit (Mockito) | Locale-based genre name selection (ko / en fallback) |
| `GlobalExceptionHandlerTest` | Slice (`@WebMvcTest`) | All 5 exception handlers mapped to correct HTTP status and `ErrorCode` |
| `JwtAuthenticationTest` | Integration (`@SpringBootTest`) | Filter chain: missing token / malformed / expired / valid / public path |
| `AuthServiceIntegrationTest` | Integration (`@SpringBootTest`) | Full auth lifecycle: sign-in → token rotation → soft-delete → re-registration cascade |
| `TrackRepositoryTest` | Slice (`@DataJpaTest`) | Feed queries: genre filter (inner join), general filter (anti-join), hype exclusion (multi-user), limit/offset, `isActive` |
| `UserHypeTrackRepositoryTest` | Slice (`@DataJpaTest`) | Keyset pagination, exists/find queries |
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

---

## API Overview

| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/apple` | Sign in with Apple identity token |
| POST | `/auth/refresh` | Rotate refresh token, issue new access token |
| POST | `/auth/logout` | Invalidate refresh token |
| POST | `/auth/withdraw` | Soft-delete account, cascade token cleanup |
| GET | `/genres` | List genres (i18n: `Accept-Language` ko/en) |
| GET | `/feed` | Paginated track feed with opaque cursor |
| GET | `/feed/search` | Keyword search across track/artist name |
| GET | `/tracks/{trackId}` | Track detail + first 5 users who hyped it |
| POST | `/tracks/{trackId}/hypes` | Hype a track |
| DELETE | `/tracks/{trackId}/hypes` | Remove hype |
| GET | `/users/me` | User profile |
| PATCH | `/users/me/nickname` | Update nickname |
| PUT | `/users/me/genres` | Replace preferred genres (0–3) |
| POST | `/users/me/onboarding/complete` | Mark onboarding as done |
| POST | `/users/me/listens` | Record a listen event (fire-and-forget) |
| GET | `/users/me/hypes` | Paginated hype history (keyset pagination) |

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
│  └──────────────────────┘     │   (scales to 0 on idle)   │    │
│                               └────────────┬──────────────┘    │
│                                            │ Unix Socket        │
│                                            │ (no public IP)     │
│                               ┌────────────▼──────────────┐    │
│                               │   Cloud SQL               │    │
│                               │   PostgreSQL 16           │    │
│                               │   db-f1-micro / Enterprise│    │
│                               └───────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
         ▲
         │ HTTPS
         │
  ┌──────┴──────┐
  │  iOS Client │
  │  (SwiftUI)  │
  └─────────────┘
```

### Data Ingestion Pipeline (Local → Cloud SQL)

iTunes API blocks GCP datacenter IPs. The collection job runs locally and writes directly to Cloud SQL via an encrypted proxy tunnel.

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
├── client/          # External API clients (Apple JWKS, iTunes Lookup)
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
└── service/         # Business logic, cron job orchestration
```

---

## Running Locally (with Cloud SQL)

```bash
# Terminal 1 — open tunnel to Cloud SQL
cloud-sql-proxy PROJECT:REGION:INSTANCE --port=5433

# Terminal 2 — start app
./run-cron.sh <endIndex>
# e.g. ./run-cron.sh 50000000
```

`run-cron.sh` handles: ADC tunnel verification → Spring Boot startup → cron job trigger → log streaming. Uses `caffeinate` to prevent macOS sleep during long-running ingestion jobs.
