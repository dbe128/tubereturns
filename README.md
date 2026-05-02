# TubeReturns

TubeReturns ranks finance YouTubers based on their historical investing performance by automatically parsing stock picks from video transcripts and tracking returns over time.

## Tech Stack

### Backend
- **Java 25** · **Spring Boot 4.0.3**
- **PostgreSQL** with **Liquibase** migrations
- **Spring Data JPA** · **Bean Validation** · **OpenAPI/Swagger**
- **Gradle** (Kotlin DSL)

### Frontend
- **Angular** (latest stable) · **TypeScript** (strict)
- **TailwindCSS** · **Chart.js** · **Zod**

### External
- **ytbsd.py** — transcript download
- **OpenRouter** — AI-powered stock pick extraction

## Running Locally

### Prerequisites
- Java 25
- Node.js 20+
- Python 3 + ytbsd.py (for transcript downloads)

### Backend (dev profile — uses H2)
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```
Available at `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`

### Frontend
```bash
cd frontend && npm start
```
Available at `http://localhost:4200` (proxies `/api` to `:8080`).

## Running with Docker

```bash
cp .env.example .env   # fill in secrets
docker compose up -d
```

The app is served at `http://localhost:80`.

### Required environment variables
| Variable | Description |
|---|---|
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | Secret for signing JWT tokens |
| `YOUTUBE_API_KEY` | YouTube Data API v3 key |
| `OPENROUTER_API_KEY` | OpenRouter API key for AI extraction |

## Pipeline

The ingestion pipeline runs automatically on a schedule:

1. **Video Discovery** — fetches new videos from configured channels
2. **Transcript Download** — runs ytbsd.py; transcripts stored in `transcripts/` (local) or the `transcripts_data` Docker volume
3. **Pick Extraction** — sends transcripts to AI; extracts ticker symbols and buy/sell signals
4. **Performance Calculation** — computes returns from pick date forward

Each step can also be triggered manually via `POST /api/admin/pipeline/{step}/trigger`.

## Configuration

| Profile | Database | Notes |
|---|---|---|
| `dev` | H2 in-memory | No external services required |
| *(default)* | PostgreSQL | Used by Docker Compose |

Transcript and AI settings are under `tubereturns.transcript` and `tubereturns.ai` in `application.yml`.

## Architecture

```
├── Controller Layer  (REST endpoints)
├── Service Layer     (business logic)
├── Repository Layer  (data access)
└── Model Layer       (domain entities)
```

Key services:
- `YouTubeDiscoveryService` — channel and video discovery
- `TranscriptDownloadService` — ytbsd.py integration
- `StockPickExtractionService` — AI-powered pick extraction
- `PipelineSchedulerService` — pipeline orchestration and scheduling

## Database Schema

- `channels` — YouTube channel metadata
- `videos` — video info, transcript text, and processing status
- `picks` — extracted stock picks (ticker, signal, video)
- `stocks` / `stock_prices` — stock metadata and historical prices
- `roles` / `users` — authentication

Migrations are managed by Liquibase (`db/changelog/001-initial-schema.sql`).
