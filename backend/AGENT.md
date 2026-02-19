# AGENT.md - Backend (mapper-studio)

## Purpose
`backend/` is the orchestration and persistence layer for Mapper Studio.
It integrates ConvEngine intent/rule execution, schema parsing, mapping suggestion generation, manual-confirmation workflow, and XLSX export.

## Stack
- Java 21
- Spring Boot 4
- ConvEngine (`com.github.salilvnair:convengine:1.0.11`)
- JDBC + SQLite (`org.xerial:sqlite-jdbc`)
- Apache POI (XLSX export)

## Startup Model
- Main app: `src/main/java/com/obe/mapperstudio/MapperStudioApplication.java`
- Pre-boot initializer:
  - `src/main/java/com/obe/mapperstudio/bootstrap/SqliteBootstrapInitializer.java`
- Initializer responsibilities:
  1. Resolve SQLite file path (`mapper.sqlite.path`)
  2. Create DB file if absent
  3. Optionally copy classpath seed DB
  4. Execute schema scripts
  5. Execute base seed (if required) + upsert seed
  6. Set `MAPPER_SQLITE_URL` system property for datasource wiring

## HTTP API (StudioController)
File: `src/main/java/com/obe/mapperstudio/api/StudioController.java`
- `POST /api/studio/message`
- `POST /api/studio/mappings/save`
- `POST /api/studio/mappings/confirm`
- `POST /api/studio/mappings/export`
- `POST /api/studio/admin/db/init`
- `GET /api/studio/admin/db/status`

## Core Services
- Conversation turn handling:
  - `service/studio/StudioConversationService.java`
- DB init/status:
  - `service/studio/DbInitializationService.java`
- Save/confirm/export policy:
  - `service/studio/MappingManagementService.java`
- Workbook generation:
  - `service/studio/WorkbookExportService.java`

## Task Layer (ConvEngine Task Methods)
File: `task/MappingStudioTask.java`
- `parseSchemas`
- `generateSuggestions`
- `validateMappings`
- `publishMappingArtifact`

Task dependencies are intentionally split into focused services:
- `task/service/SessionInputService.java`
- `task/service/SchemaParserService.java`
- `task/service/MappingSuggestionService.java`
- `task/service/MappingValidationService.java`
- `task/service/MappingStudioPersistenceService.java`

## Session Key Source
- `task/model/StudioSessionKeys.java`
- Use constants from this class for all session input/output keys.

## Persistence + Confirmation Rules
- Save operation clears prior `mps_mapping_field` rows for `(project_code, version_code)` and inserts selected rows.
- Confirm operation writes confirmation audit (`mps_mapping_manual_confirm_audit`).
- Export operation is blocked unless manual confirmation exists.

## SQL Artifacts
- `src/main/resources/sql/ce-ddl_sqlite.sql`
- `src/main/resources/sql/mps-ddl_sqlite.sql`
- `src/main/resources/sql/ce-seed-mapping-studio_sqlite.sql`
- `src/main/resources/sql/ce-seed-mapping-studio-upsert_sqlite.sql`
- `src/main/resources/sql/initdb.http`

## Guardrails
1. Keep rule/task-driven flow deterministic (no hidden branching in controller).
2. Preserve compatibility for `inputParams` consumed by UI.
3. Use `StudioSessionKeys` constants; avoid hardcoded strings.
4. For schema-type changes, update:
   - `TargetType`
   - parser services
   - UI payload expectations
   - seed SQL if rules/required fields change
5. Any new mapping row attribute must be wired through:
   - DTOs
   - save/confirm persistence
   - export workbook sheets

## Local Run and API Examples
See `backend/README.md`.
