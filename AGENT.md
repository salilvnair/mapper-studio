# AGENT.md - Mapper Studio Monorepo

## Scope
This repository is a single git project with two apps:
- `frontend/` - React + TypeScript UI for Mapping Studio
- `backend/` - Spring Boot + ConvEngine orchestration + SQLite persistence

## Current Architecture
- UI collects source/target schemas (JSON, XML, XSD, XSD+WSDL), renders mapping table + flow graph, and allows manual correction.
- Backend executes rule/task-based mapping pipeline through ConvEngine (`MAPPING_STUDIO` intent).
- SQLite is the default runtime DB; bootstrap + seed are handled at startup and via admin endpoint.
- Mapping results can be saved, confirmed, and exported to XLSX.

## Key Runtime Contracts
- Studio turn API: `POST /api/studio/message`
- Mapping management:
  - `POST /api/studio/mappings/save`
  - `POST /api/studio/mappings/confirm`
  - `POST /api/studio/mappings/export`
- DB admin:
  - `GET /api/studio/admin/db/status`
  - `POST /api/studio/admin/db/init`

## Data + SQL
- ConvEngine SQLite DDL: `backend/src/main/resources/sql/ce-ddl_sqlite.sql`
- OBE SQLite DDL: `backend/src/main/resources/sql/obe-ddl_sqlite.sql`
- Base seed: `backend/src/main/resources/sql/ce-seed-mapping-studio_sqlite.sql`
- Upsert seed: `backend/src/main/resources/sql/ce-seed-mapping-studio-upsert_sqlite.sql`

## Working Rules
1. Keep frontend and backend API contracts aligned (`frontend/src/types/studio.ts` <-> backend DTOs).
2. Preserve deterministic rule/task flow in backend; avoid UI-only business logic.
3. For schema support changes, update parsing + session keys + UI payload assembly together.
4. When adding mapping attributes (for example origin/notes/artifact metadata), update:
   - UI state/types
   - save/confirm/export backend services
   - DB columns/serialization
5. Keep docs current in:
   - `frontend/AGENT.md`
   - `backend/AGENT.md`
   - `backend/README.md`

## Quick Start
- Frontend: see `frontend/AGENT.md`
- Backend: see `backend/README.md`
