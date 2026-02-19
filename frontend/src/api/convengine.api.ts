import type { MappingConfirmResponse, MappingExportRequest, MappingSaveResponse, StudioResponse } from '../types/studio'

const STUDIO_BASE = 'http://localhost:8081/api/studio'
const CONVENGINE_BASE = 'http://localhost:8081/api/v1/conversation'

export type AuditEvent = {
  auditId?: number
  conversationId?: string
  stage?: string
  payloadJson?: string
  createdAt?: string
}

export type DbStatusResponse = {
  initialized: boolean
  status: string
  checkedAt: string
}

export async function sendStudioMessage(
  message: string,
  conversationId?: string,
  inputParams: Record<string, unknown> = {}
): Promise<StudioResponse> {
  const res = await fetch(`${STUDIO_BASE}/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ conversationId, message, inputParams })
  })
  if (!res.ok) {
    throw new Error(`Studio backend error: ${res.status}`)
  }
  return res.json()
}

export async function saveMappings(request: MappingExportRequest): Promise<MappingSaveResponse> {
  const res = await fetch(`${STUDIO_BASE}/mappings/save`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  })
  if (!res.ok) {
    throw new Error(`Save mappings failed: ${res.status}`)
  }
  return res.json()
}

export async function confirmMappings(request: MappingExportRequest): Promise<MappingConfirmResponse> {
  const res = await fetch(`${STUDIO_BASE}/mappings/confirm`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  })
  if (!res.ok) {
    throw new Error(`Confirm mappings failed: ${res.status}`)
  }
  return res.json()
}

export async function exportMappingsXlsx(request: MappingExportRequest): Promise<Blob> {
  const res = await fetch(`${STUDIO_BASE}/mappings/export`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request)
  })
  if (!res.ok) {
    throw new Error(`Export mappings failed: ${res.status}`)
  }
  return res.blob()
}

export async function fetchConversationAudit(conversationId: string): Promise<AuditEvent[]> {
  const res = await fetch(`${CONVENGINE_BASE}/audit/${conversationId}`)
  if (!res.ok) {
    throw new Error(`Audit API error: ${res.status}`)
  }
  const data = await res.json()
  return Array.isArray(data) ? (data as AuditEvent[]) : []
}

export async function fetchDbStatus(): Promise<DbStatusResponse> {
  const res = await fetch(`${STUDIO_BASE}/admin/db/status`)
  if (!res.ok) {
    throw new Error(`DB status API error: ${res.status}`)
  }
  return res.json() as Promise<DbStatusResponse>
}

export async function initializeDb(): Promise<void> {
  const res = await fetch(`${STUDIO_BASE}/admin/db/init`, { method: 'POST' })
  if (!res.ok) {
    throw new Error(`DB init API error: ${res.status}`)
  }
}
