# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

After completing every task:
- If the task involved a `git push`: monitor the deployment with `gh run watch <run-id> --exit-status` in the background, and play `afplay "/Users/dbe128/IdeaProjects/TubeReturns/ready-fight.mp3"` once it completes. Do NOT play any other sound in this case. Only play it once — if the deployment watcher fires multiple times or the sound was already played, do not repeat it.
  - To get the correct run ID, wait a moment after pushing, then run `gh run list --limit 3` and pick the run whose commit message matches the push. Never assume the first result is correct — verify the commit message or SHA before watching.
- Otherwise: run `afplay "/Users/dbe128/IdeaProjects/TubeReturns/mission-accomplished-made-with-Voicemod.mp3"`

Do NOT commit or push changes to this repo unless explicitly asked by the user.

When asked to push: check whether any new frontend pages were added since the last push. If yes, before pushing verify that the following are up to date:
- `SitemapController.java` — new page included with appropriate priority and changefreq
- `frontend/public/llms.txt` — mention of the new page if relevant to AI discoverability
- `frontend/src/index.html` — JSON-LD structured data updated if the page adds significant new content
New pages to check for: routes added to `app.routes.ts` that have a standalone path (not `:param` routes).

For the companion monitoring repo at `~/IdeaProjects/tubereturns-monitoring`, commits and pushes may be made autonomously whenever monitoring-related changes are ready.

### Grafana dashboard conventions
- **Never hardcode a fixed time window in stat panels.** Stat panels that count or sum events (e.g. `increase(...)`) must use `[$__range]` so the value reflects the dashboard's selected time range. Titles must not contain a hardcoded duration (e.g. say "API Calls (window)" not "API Calls (1h)").
- Timeseries panels that compute a rate for display (e.g. `rate(...[5m])`) may keep a fixed sampling interval — `[5m]` is appropriate there.
- **noValue must always be set to `"0"`** on every panel so zero is shown instead of "No data" when no events have occurred.

---

## Commands

```bash
# Full stack (recommended for development)
./dev.sh                        # starts backend (dev profile) + frontend
./dev.sh --frontend-only        # frontend only, assumes backend is already running

# Backend alone
./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend alone
cd frontend && npm start         # http://localhost:4200 — proxies /api → :8080

# Build checks (run before committing)
./gradlew compileJava
cd frontend && npx tsc --noEmit

# Swagger UI (dev)
http://localhost:8080/swagger-ui.html

# H2 console (dev only)
http://localhost:8080/h2-console  # JDBC URL: jdbc:h2:mem:tubereturns
```

---

## Versioning

- **Patch** — auto-bumped by pre-commit hook on every commit
- **Minor** — bump manually when a new user-facing feature ships (new page, new pipeline step, new data source)
- **Major** — bump manually at public launch or when a breaking API/data-model change is made

Minor and major bumps are done manually by editing `version` in `build.gradle.kts`; the pre-commit hook then auto-increments the patch from that new base.

---

## Product Goal

TubeReturns ranks finance YouTubers by their historical stock pick performance. The system automatically discovers videos, downloads transcripts, extracts stock picks via AI, and computes returns from the publication date forward.

---

## Tech Stack

### Frontend
- **Angular 21** (standalone components, `@for`/`@if` control flow syntax — no `*ngFor`/`*ngIf`)
- **TypeScript strict mode** — no `any`
- **Signals** for all reactive state (`signal()`, `computed()`)
- **Zod** — validate all API responses in `api.service.ts` using the `validated()` helper; every new endpoint needs a Zod schema in `types.ts`
- **TailwindCSS** — utility classes only, no custom CSS files

### Backend
- **Java 25 + Spring Boot 4** (Kotlin DSL Gradle: `build.gradle.kts`)
- **Lombok** — `@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED)` on JPA entities; `@Slf4j @RequiredArgsConstructor` on services/controllers; always `log.xxx` not `logger.xxx`
- **Spring Data JPA + PostgreSQL** (prod) / **H2 in-memory** (dev)
- **Liquibase** — all schema changes as new numbered SQL files in `src/main/resources/db/changelog/`; add an `<include>` entry in **both** `db.changelog-master.xml` (prod) and `db.changelog-h2-master.xml` (dev/H2); **never edit or delete existing migration files or changesets** — exception: migration files that have not yet been committed to git may be freely modified, as they have not been applied to any environment
- **OpenAPI** via springdoc — annotate all new endpoints with `@Operation`
- **`@Value` fields** for config injection (not constructor-injected via Lombok)

### Security
- `/api/admin/**` — requires `ROLE_ADMIN`
- All other endpoints — `permitAll()` but JWT filter still sets `Authentication` context when a valid token is present; controllers use optional `Authentication authentication` parameter to distinguish authenticated vs. anonymous

---

## Architecture

### Backend Pipeline (three sequential steps)

```
YouTubeDiscoveryService → TranscriptDownloadService → StockPickExtractionService
       (discovery)              (transcript)                  (extraction)
```

`PipelineSchedulerService` owns the cron schedule and manual trigger logic for all three steps. `PipelineStatusRegistry` is an in-memory store of per-step running state, last run times, and fatal errors — it is the single source of truth polled by the admin dashboard.

**Discovery** (`YouTubeDiscoveryService`): fetches video metadata from the YouTube API for each active channel, persists `Video` rows. Skips channels whose handle starts with `mock-`. Sets `channel.discoveryComplete = true` when done.

**Transcript** (`TranscriptDownloadService`): runs `ytbsd.py` (a Python proxy-rotation script) as a subprocess via `ProcessBuilder`. Videos are batched; the batch queue runs on a single background thread (`ytbsd-worker`). ytbsd writes `.md` files to `transcripts-dir`; the service reads them, extracts the text under `### Transcript`, stores it on the `Video`, then immediately enqueues the video for extraction. Key config: `tubereturns.transcript.ytbsd-path` and `tubereturns.transcript.transcripts-dir`.

**Extraction** (`StockPickExtractionService`): sends transcript text to the AI model (OpenRouter, model `openrouter/owl-alpha`) and parses the JSON response into `Pick` rows. Runs on a single background thread (`extraction-worker`). Sets `video.processingStatus = COMPLETED` when done.

### Dev profile differences
- Uses H2 in-memory DB (no PostgreSQL needed)
- Mock channels loaded from `classpath:mock-channels/*.json` via `MockChannelDataSeedService` — these are seeded with `discoveryComplete = true` and all videos as `COMPLETED`
- `tubereturns.transcript.ytbsd-path = scripts/ytbsd.py` (local path)
- AI is always enabled; no flag to disable it

### Prod channel seed
`src/main/resources/channels-config.yml` lists channels with `handle`, `channelName`, and `enabled`. `ChannelInitializationService` seeds them on startup.

### Frontend architecture
- `ApiService` (`frontend/src/app/api/api.service.ts`) — the **only** place HTTP calls are made; all responses validated with Zod
- `AuthService` — decodes JWT from `localStorage`, exposes `isAuthenticated` and `isAdmin`
- `AuthInterceptor` — adds `Authorization: Bearer <token>` header to all requests
- `AuthGuard` — protects routes that require login
- `BackendRecoveryService` — polls `/api/admin/health` and retries failed loads
- Pages use `signal<T>()` and `forkJoin` for parallel data loading; no RxJS subjects except `searchSubject` in leaderboard

### Adding a database migration
Create a new file `src/main/resources/db/changelog/NNN-description.sql` and add an `<include>` entry in **both**:
- `db.changelog-master.xml` (PostgreSQL / prod)
- `db.changelog-h2-master.xml` (H2 / dev) — if the SQL is H2-incompatible, add an inline `<changeSet>` block instead of an `<include>`

Use standard PostgreSQL DDL; Liquibase runs it on startup. **Never modify or delete existing migration files or changesets** — they may already be applied to production.

### Multi-currency returns (USD-denominated)
Stock prices are stored in their local currency (the `currency` column on `Stock`). Portfolio returns are shown in USD by converting each price point via historical exchange rates.

- `currencies` table: one row per currency code (EUR, GBP, HUF, etc.)
- `exchange_rates` table: daily `rate_to_usd` for each currency (e.g., for EUR: `EURUSD=X` from Yahoo Finance)
- `ExchangeRateService`: seeds popular currencies with 10 yr of history on first startup (if `currencies` table is empty); refreshed alongside stock prices on the price-refresh cron
- When a new non-USD currency appears during extraction, `ExchangeRateService.ensureCurrencyHistoricalRates(code)` is called to fetch historical rates immediately
- `PortfolioController.buildPortfolioPrices` applies the floor-entry FX rate to each local-currency price before computing % returns; USD stocks (null or "USD" currency) are unaffected (rate = 1.0)
- Yahoo Finance FX ticker format: `<CODE>USD=X` (e.g., `EURUSD=X`, `GBPUSD=X`)

### Notification flow
`ChannelProcessingNotification` rows are created when a user subscribes (via `POST /api/channels/{handle}/notify`). `ChannelNotificationService` checks every 120 s: if `channel.discoveryComplete` is true and no videos have PENDING/DOWNLOADING transcript or PENDING/PROCESSING extraction status, it sends the email and marks the row with `sentAt`.

---

### Caching

Two Caffeine caches, both 30-minute TTL, max size 1:
- `allChannels` — full channel list (evicted on any channel add/update/delete via `ChannelListService.evictAllChannels()`)
- `siteStats` — site-wide stats for the stats endpoint

---

## Code Style

- **No comments** — self-explanatory naming only; no Javadoc, no inline comments
- **No unused code** — remove dead methods, unused imports, stubs immediately
- Always wrap `if`/`for`/`while` bodies in `{}`
- Frontend: use `@for`/`@if` Angular control-flow blocks, not structural directives
- **No `@Value` defaults in Java** — `@Value("${some.key}")` only; all defaults belong in `application.yml` (or `application-dev.yml` for dev-only overrides)
- **No `alert()` or `confirm()`** — user feedback goes through `showToast()` (green/red fixed overlay, auto-dismisses after 6 s) and `openConfirm()` (modal with backdrop, `destructive: true` makes the confirm button red); both helpers are established in `LeaderboardComponent` and should be replicated in any new component that needs them
- **Avoid `@Transactional` self-invocation** — Spring's transaction proxy is bypassed when a bean calls its own `@Transactional` method directly, so the annotation has no effect. Instead, extract the transactional logic into a dedicated helper class (e.g. `FooTransaction`) and inject it; calls across bean boundaries go through the proxy correctly.
