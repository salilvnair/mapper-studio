# Mapper Studio Backend

Spring Boot backend for Mapper Studio. This service runs ConvEngine-powered mapping orchestration, persists mapping data, and exposes save/confirm/export + DB admin APIs.

## Tech
- Java 21
- Spring Boot 4.0.1
- ConvEngine 1.0.11
- SQLite (default)
- Apache POI (XLSX export)

## Prerequisites
- JDK 21+
- Maven (`mvn`)
- Optional: `OPENAI_API_KEY` for OpenAI-backed suggestion flow

## Run Locally
From `backend/`:

```bash
mvn spring-boot:run
```

Service starts on:
- `http://localhost:8081`

## Configuration
Primary config file:
- `src/main/resources/application.yml`

Important properties:
- `spring.datasource.url`: defaults to `jdbc:sqlite:./data/mapper-studio.db`
- `spring.jpa.properties.hibernate.dialect`: `org.hibernate.community.dialect.SQLiteDialect`
- `mapper.sqlite.path`: file path used by early bootstrap initializer
- `mapper.sqlite.bootstrap-enabled`: when true, SQL bootstrap runs at startup
- `convengine.llm.provider`: `openai` or `lmstudio`

Env overrides commonly used:
- `MAPPER_SQLITE_URL`
- `OPENAI_API_KEY`
- `LMSTUDIO_API_KEY`

## Startup Bootstrap Behavior
Before Spring datasource/JPA fully initializes, `SqliteBootstrapInitializer`:
1. Resolves DB file path (`mapper.sqlite.path`)
2. Ensures directory/file exists
3. Sets system property `MAPPER_SQLITE_URL`
4. Applies schema SQL:
   - `sql/ce-ddl_sqlite.sql`
   - `sql/mps-ddl_sqlite.sql`
5. Applies data SQL:
   - `sql/ce-seed-mapping-studio_sqlite.sql` (base, when needed)
   - `sql/ce-seed-mapping-studio-upsert_sqlite.sql` (idempotent updates)

## API Endpoints
Base: `/api/studio`

- `POST /message`
  - Run one studio turn through ConvEngine.
- `POST /mappings/save`
  - Persist selected/edited mappings.
- `POST /mappings/confirm`
  - Mark manual confirmation for export eligibility.
- `POST /mappings/export`
  - Export mapping workbook (`.xlsx`).
- `POST /admin/db/init`
  - Execute schema + seed scripts on demand.
- `GET /admin/db/status`
  - Returns initialized/not-initialized status.

## Quick Curl Samples
### 1) Studio turn
```bash
curl -X POST http://localhost:8081/api/studio/message \
  -H 'Content-Type: application/json' \
  -d '{
    "message": "generate mappings",
    "inputParams": {
      "projectCode": "DEMO_PROJECT",
      "mappingVersion": "1.0.0",
      "sourceType": "SOAP",
      "sourceSpec": "<PolicyRequest><CustomerName>Robert</CustomerName></PolicyRequest>",
      "targetType": "XSD",
      "targetSchema": "<xsd:schema xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"></xsd:schema>"
    }
  }'
```

### 2) DB status
```bash
curl http://localhost:8081/api/studio/admin/db/status
```

### 3) Init DB
```bash
curl -X POST http://localhost:8081/api/studio/admin/db/init
```

## Codebase Map
- App entry:
  - `src/main/java/com/obe/mapperstudio/MapperStudioApplication.java`
- Bootstrap:
  - `src/main/java/com/obe/mapperstudio/bootstrap/SqliteBootstrapInitializer.java`
- API:
  - `src/main/java/com/obe/mapperstudio/api/StudioController.java`
  - `src/main/java/com/obe/mapperstudio/api/dto/*`
- Studio services:
  - `src/main/java/com/obe/mapperstudio/service/studio/*`
- ConvEngine task orchestration:
  - `src/main/java/com/obe/mapperstudio/task/MappingStudioTask.java`
  - `src/main/java/com/obe/mapperstudio/task/service/*`
  - `src/main/java/com/obe/mapperstudio/task/model/*`
- SQL + examples:
  - `src/main/resources/sql/*`
  - `src/main/resources/examples/*`

## Notes for Contributors
- Keep session key strings centralized in `StudioSessionKeys`.
- Keep mapping save/confirm/export contract aligned with frontend `src/types/studio.ts`.
- If you add new mapping attributes, update DTOs, persistence SQL writes, and workbook export sheets together.
