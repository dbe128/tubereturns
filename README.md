# TubeReturns

TubeReturns is a web application that ranks finance YouTubers based on their historical investing performance by automatically parsing stock picks from video transcripts and tracking their performance over time.

## Features

- **Automatic Video Discovery**: Discovers new videos from configured YouTube channels
- **Transcript Extraction**: Downloads video transcripts using yt-dlp
- **AI-Powered Stock Pick Extraction**: Extracts stock picks (ticker symbols, buy/sell signals) from transcripts
- **Performance Tracking**: Calculates and tracks investment performance over multiple time periods
- **REST API**: Comprehensive API for accessing channel rankings, stock picks, and performance data
- **Financial Dashboards**: React frontend with charts and visualizations

## Tech Stack

### Backend
- **Java 25** with **Spring Boot 4.0.3**
- **PostgreSQL** database with **Liquibase** migrations
- **Spring Data JPA** for data access
- **OpenAPI/Swagger** for API documentation
- **Bean Validation** for input validation

### Frontend
- **React 19** with **TypeScript**
- **Vite** for build tooling
- **TailwindCSS** for styling
- **Recharts** for financial charts
- **Axios** for API calls
- **Zod** for runtime validation

### External Tools
- **yt-dlp** for transcript download
- **AI model integration** for stock pick extraction
- **Stock data APIs** for performance calculation

## Getting Started

### Prerequisites
- Java 25
- Node.js 18+
- PostgreSQL 12+
- yt-dlp installed and available in PATH

### Database Setup
1. Create PostgreSQL database:
```sql
CREATE DATABASE tubereturns;
CREATE USER tubereturns WITH PASSWORD 'tubereturns_dev';
GRANT ALL PRIVILEGES ON DATABASE tubereturns TO tubereturns;
```

### Backend Setup
1. Clone the repository
2. Configure database connection in `application-dev.yml`
3. Run the application:
```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Frontend Setup
```bash
cd frontend
npm install
npm run dev
```

## Configuration

The application supports multiple configuration profiles:

- `dev`: Development mode with mock data and debug logging
- `prod`: Production mode with real APIs and optimized logging

### Environment Variables (Production)
- `DB_HOST`, `DB_PORT`, `DB_NAME`: Database connection
- `DB_USERNAME`, `DB_PASSWORD`: Database credentials
- `YOUTUBE_API_KEY`: YouTube Data API key
- `AI_API_KEY`: AI model API key (OpenAI, etc.)
- `STOCK_API_KEY`: Stock data API key (Alpha Vantage, etc.)

## API Documentation

Once the application is running, API documentation is available at:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Key API Endpoints

- `GET /api/channels` - List all active channels
- `GET /api/channels/top-performers` - Get top performing channels
- `GET /api/picks` - Get recent stock picks
- `GET /api/picks/ticker/{ticker}` - Get picks for specific stock
- `GET /api/performance/top-performers` - Get best performing picks
- `POST /api/admin/ingestion/run` - Manually trigger data ingestion

## Data Pipeline

The application runs an automated data ingestion pipeline:

1. **Channel Discovery** (hourly): Discovers new videos from configured channels
2. **Transcript Download** (hourly): Downloads transcripts for new videos
3. **Stock Pick Extraction** (hourly): Extracts stock picks using AI
4. **Performance Calculation** (hourly): Calculates returns for new picks
5. **Performance Updates** (every 15 minutes): Updates existing performance data

## Architecture

The application follows a layered architecture:

```
├── Controller Layer (REST endpoints)
├── Service Layer (business logic)
├── Repository Layer (data access)
└── Model Layer (domain entities)
```

Key services:
- `YouTubeDiscoveryService`: Channel and video discovery
- `TranscriptDownloadService`: yt-dlp integration
- `StockPickExtractionService`: AI-powered extraction
- `StockPerformanceService`: Performance calculation
- `DataIngestionOrchestrationService`: Pipeline orchestration

## Database Schema

- `channels`: YouTube channel metadata
- `videos`: Video information and transcripts
- `picks`: Extracted stock picks
- `performance`: Performance metrics and returns

## Development

### Running Tests
```bash
./gradlew test
```

### Code Style
- Java: Follow Spring Boot conventions
- TypeScript: Strict mode enabled, no `any` types
- Use Bean Validation and Zod for input validation

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License.