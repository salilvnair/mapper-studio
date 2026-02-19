# AGENT.md - Frontend (mapper-studio)

## Purpose
`frontend/` is the operator UI for Mapper Studio. It drives the full mapping workflow:
- capture source and target artifacts (JSON/XML/XSD/WSDL)
- run conversation turns
- review and manually adjust mappings
- save/confirm/export mapping outcomes
- inspect audit timeline
- manage DB initialization from Settings page

## Stack
- React 18 + TypeScript
- Vite
- Monaco editor (`@monaco-editor/react`)
- Mantine (`@mantine/core`)
- React Flow (`reactflow`)

## Main Files
- App orchestration/UI state:
  - `src/App.tsx`
- API calls:
  - `src/api/convengine.api.ts`
- Components:
  - `src/components/FlowCanvas.tsx`
  - `src/components/MetadataTable.tsx`
  - `src/components/AuditTimeline.tsx`
- Shared types:
  - `src/types/studio.ts`
- Theme + layout styles:
  - `src/styles.css`

## Key UX Flows
1. **Studio mode**
   - User enters project/version/source/target.
   - UI posts turn to `POST /api/studio/message`.
   - Mapping suggestions rendered in table + flow graph.
   - User can manually correct source/target mappings and notes.
2. **Mapping lifecycle actions**
   - Save: `POST /api/studio/mappings/save`
   - Confirm final mappings: `POST /api/studio/mappings/confirm`
   - Export XLSX: `POST /api/studio/mappings/export`
3. **Audit timeline**
   - Pulls from ConvEngine audit API (`/api/v1/conversation/audit/{conversationId}`).
4. **Settings mode**
   - DB status: `GET /api/studio/admin/db/status`
   - Init DB: `POST /api/studio/admin/db/init`

## Data Contract Notes
- Mapping row type includes manual curation fields:
  - `selected`, `manualOverride`, `notes`, `mappingOrigin`
- `mappingOrigin` values:
  - `LLM_DERIVED`, `EDITED`
- Path type is explicit in export payload:
  - `XML_PATH` or `JSON_PATH`

## Guardrails
1. Keep backend contract source-of-truth in `src/types/studio.ts`.
2. Do not encode business transitions in UI; backend rules/tasks own state flow.
3. Preserve non-destructive edit behavior for manual mapping corrections.
4. Keep dark/light parity for all newly added controls.
5. Any new mapping attribute must flow through:
   - table state
   - flow node details
   - save/confirm/export request payload

## Local Dev
- Install: `npm install`
- Start: `npm run dev`
- Build: `npm run build`
- Preview: `npm run preview`

## Validation Checklist
1. Studio run updates mappings and flow graph.
2. Save/confirm/export all work with edited rows.
3. Settings page reflects DB status and init behavior.
4. Audit drawer still loads timeline for active conversation.
