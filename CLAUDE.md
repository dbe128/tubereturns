# claude.md — TubeReturns (Consolidated MVP Spec)

After completing every task, run: `afplay "/Users/dbe128/IdeaProjects/TubeReturns/mission-accomplished-made-with-Voicemod.mp3"`

Do NOT commit or push changes unless explicitly asked by the user.

## Versioning

- **Patch** — auto-bumped by pre-commit hook on every commit
- **Minor** — bump manually when a new user-facing feature ships (new page, new pipeline step, new data source)
- **Major** — bump manually at public launch or when a breaking API/data-model change is made

Minor and major bumps are done manually by editing `version` in `build.gradle.kts`; the pre-commit hook then auto-increments the patch from that new base.

TubeReturns is a web application that helps retail investors **rank finance YouTubers based on their historical investing performance** and visualize the results with clean, compelling financial dashboards.

This document defines the **single source of truth** for MVP architecture, scope, constraints, and tech stack.

---

# 1. Product Goal

TubeReturns answers:

> “Which finance YouTubers have historically made good investing calls?”

The system:
- Automatically parses stock picks from YouTube videos
- Computes performance from publication date forward
- Ranks creators transparently
- Visualizes results with financial dashboards

---

# 2. Tech Stack (Mandatory)

## Frontend

- **Angular (latest stable version)**
- **TypeScript (strict mode enabled)**
- **Angular CLI** (build tooling and dev server)
- **Zod** for all runtime validations
- **Charting library**: Chart.js (via ng2-charts or direct canvas integration)
- **Styling**: TailwindCSS (recommended) or equivalent modern utility CSS

Constraints:
- Typed API layer
- No `any`
- Centralized API client
- Zod validation on all user inputs and critical responses

---

## Backend

- **Java**
- **Latest LTS JDK**
- **Spring Boot (latest stable)**
- **Spring Web**
- **Spring Data JPA**
- **Bean Validation (Jakarta Validation)**
- **OpenAPI / springdoc-openapi**
- **Gradle** (mandatory build tool)
- Gradle Kotlin DSL (`build.gradle.kts`)

---

## Database

- **PostgreSQL**
- **Liquibase** for schema migrations (mandatory)
- All schema changes versioned
- Never mutate applied migrations

---

## External Tooling

- **yt-dlp** (invoked by backend for transcript download)
- AI model provider (abstracted behind service layer)

---

# 3. MVP Scope

## 3.1 Fixed Set of Channels

- MVP operates on a predefined list of YouTube channels.
- Channels are configured in backend seed data or config.
- No public submission of channels in MVP.

---

## 3.2 Picks Are Parsed From Videos (No Manual Entry)

Picks must be derived automatically using:

1. Video discovery
2. Transcript download using `yt-dlp`
3. AI-based extraction of ticker, company name, and buy/sell signal
4. Backend validation and normalization
5. Performance calculation

Manual CSV-based pick entry is not part of MVP.

---

# 4. Ingestion & Extraction Pipeline

## 4.1 Pipeline Overview

For each configured channel:

### Step 1 — Discover Videos
- Fetch latest videos (e.g., last N or within date range).
- Persist metadata in DB.

---

### Step 2 — Download Transcript (Plain Text Only)

Use `yt-dlp` to download subtitles/transcripts.

Constraints:
- Prefer English captions.
- Prefer human-generated over auto-generated.
- Store **plain text only**.
- Do NOT store timestamps.
- Do NOT store segment timing metadata.

If transcript unavailable:
- Mark video as `NO_TRANSCRIPT`.

---

### Step 3 — AI Extraction

Send full transcript text to AI.

### Required Output Schema

```json
{
  "videoId": "YOUTUBE_VIDEO_ID",
  "extractions": [
    {
      "tickerSymbol": "AAPL",
      "companyName": "Apple",
      "signal": "BUY"
    }
  ]
}```

---

# 5. Code Style

## Clean Code Principles

- **No comments** — code must be self-explanatory through good naming and structure. Do not add Javadoc, inline comments, or block comments.
- **No unused code** — remove dead methods, unused imports, unreachable branches, and stubs immediately. Do not leave placeholder implementations.
