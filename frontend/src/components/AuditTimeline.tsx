import { useMemo, useState } from 'react'
import type { AuditEvent } from '../api/convengine.api'

type StageMeta = { icon: string; color: string }
type JsonLike = null | string | number | boolean | JsonLike[] | { [key: string]: JsonLike }

const STAGE_META: Record<string, StageMeta> = {
  USER_INPUT: { icon: '🗣️', color: 'border-sky-500' },
  PIPELINE_TIMING: { icon: '⏱️', color: 'border-slate-500' },
  RESOLVE_RESPONSE: { icon: '🧠', color: 'border-emerald-500' },
  RESOLVE_RESPONSE_SELECTED: { icon: '🧭', color: 'border-emerald-500' },
  RESOLVE_RESPONSE_LLM_INPUT: { icon: '📥', color: 'border-cyan-500' },
  RESOLVE_RESPONSE_LLM_OUTPUT: { icon: '📤', color: 'border-teal-500' },
  RESPONSE_EXACT: { icon: '📝', color: 'border-emerald-500' },
  INTENT_RESOLVE_START: { icon: '🧭', color: 'border-indigo-500' },
  INTENT_CLASSIFICATION_MATCHED: { icon: '🎯', color: 'border-indigo-600' },
  INTENT_AGENT_LLM_INPUT: { icon: '🤖📥', color: 'border-indigo-400' },
  INTENT_AGENT_LLM_OUTPUT: { icon: '🤖📤', color: 'border-violet-500' },
  INTENT_AGENT_ACCEPTED: { icon: '😄', color: 'border-emerald-500' },
  INTENT_AGENT_NEEDS_CLARIFICATION: { icon: '🤔', color: 'border-amber-500' },
  INTENT_AGENT_SCORES: { icon: '📈', color: 'border-indigo-500' },
  INTENT_AGENT_REJECTED: { icon: '🙅', color: 'border-rose-500' },
  INTENT_CLASSIFIED: { icon: '🧩', color: 'border-cyan-500' },
  INTENT_RESOLVED_BY_CLASSIFIER: { icon: '🧭', color: 'border-indigo-600' },
  INTENT_RESOLVED_BY_AGENT: { icon: '🤖', color: 'border-fuchsia-500' },
  INTENT_MISSING: { icon: '🫥', color: 'border-rose-500' },
  RULE_MATCHED: { icon: '✅', color: 'border-purple-500' },
  RULE_APPLIED: { icon: '🛠️', color: 'border-fuchsia-500' },
  RULE_NO_MATCH: { icon: '🫥', color: 'border-slate-400' },
  POLICY_BLOCK: { icon: '🛑', color: 'border-red-500' },
  AUTO_ADVANCE_FACTS: { icon: '🧾', color: 'border-cyan-500' },
  SCHEMA_EXTRACTION_START: { icon: '🧬', color: 'border-lime-500' },
  SCHEMA_EXTRACTION_LLM_INPUT: { icon: '🤖📥', color: 'border-green-500' },
  SCHEMA_EXTRACTION_LLM_OUTPUT: { icon: '🤖📤', color: 'border-emerald-500' },
  SCHEMA_STATUS: { icon: '📊', color: 'border-teal-500' },
  ASSISTANT_OUTPUT: { icon: '🤖💬', color: 'border-emerald-500' },
  CONTAINER_DATA_SKIPPED: { icon: '📦⏭️', color: 'border-amber-500' },
  MCP_NO_TOOLS_AVAILABLE: { icon: '🧰∅', color: 'border-slate-500' },
  SET_INTENT: { icon: '🧭', color: 'border-indigo-600' },
  SET_JSON: { icon: '🧩', color: 'border-cyan-500' },
  SET_TASK: { icon: '⚙️', color: 'border-fuchsia-600' },
  SET_STATE: { icon: '🔁', color: 'border-yellow-500' },
  GET_CONTEXT: { icon: '🧾', color: 'border-violet-500' },
  GET_SESSION: { icon: '📘', color: 'border-indigo-500' },
  GET_SCHEMA_JSON: { icon: '🧬', color: 'border-teal-500' },
  STEP_ENTER: { icon: '🪜⬇️', color: 'border-sky-500' },
  STEP_EXIT: { icon: '🪜⬆️', color: 'border-emerald-500' },
  STEP_ERROR: { icon: '🪜🚫', color: 'border-rose-600' },
  ENGINE_RETURN: { icon: '🏆', color: 'border-lime-600' },
  ENGINE_KNOWN_FAILURE: { icon: '🙈', color: 'border-red-500' },
  ENGINE_UNKNOWN_FAILURE: { icon: '💀', color: 'border-red-700' },
  CLIENT_ERROR: { icon: '🚨', color: 'border-red-600' }
}

function stageLookupKey(stage: string | undefined): string {
  if (!stage) return ''
  return stage
    .replace(/[•\u2022]+/g, '')
    .trim()
    .replace(/\s+\(.*\)$/, '')
}

function metaForStage(stage: string | undefined): StageMeta {
  const key = stageLookupKey(stage)
  if (STAGE_META[key]) {
    return STAGE_META[key]
  }
  if (key.startsWith('INTENT_RESOLVED_BY_')) {
    return { icon: '🧭', color: 'border-indigo-600' }
  }
  if (key.startsWith('INTENT_')) {
    return { icon: '🧠', color: 'border-indigo-500' }
  }
  if (key.startsWith('RESPONSE_')) {
    return { icon: '📝', color: 'border-emerald-500' }
  }
  if (key.startsWith('AUTO_ADVANCE_')) {
    return { icon: '⚡', color: 'border-cyan-500' }
  }
  if (key.startsWith('CONTAINER_')) {
    return { icon: '📦', color: 'border-amber-500' }
  }
  if (key.startsWith('MCP_')) {
    return { icon: '🧰', color: 'border-slate-500' }
  }
  return { icon: '•', color: 'border-slate-300' }
}

function parsePayload(payloadJson?: string): JsonLike | null {
  if (!payloadJson) return null
  try {
    return JSON.parse(payloadJson) as JsonLike
  } catch {
    return null
  }
}

function stageDisplayName(auditRow: AuditEvent): string {
  const stage = auditRow?.stage ?? ''
  const normalized = stageLookupKey(stage)
  if (!normalized.startsWith('STEP_')) return stage

  const payload = parsePayload(auditRow?.payloadJson)
  const stepName = payload && typeof payload === 'object' && !Array.isArray(payload) ? payload.step : null
  if (typeof stepName === 'string' && stepName.trim()) {
    return `${stage} - ${stepName.trim()}`
  }
  return stage
}

function primitiveText(value: JsonLike): string {
  if (value === null) return 'null'
  if (typeof value === 'string') return `"${value}"`
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value)
}

type JsonNodeViewProps = {
  label?: string
  value: JsonLike
  depth?: number
  defaultOpen?: boolean
}

function JsonNodeView({ label, value, depth = 0, defaultOpen = false }: JsonNodeViewProps) {
  const leftPad = `${Math.max(0, depth) * 14}px`

  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return (
      <div className="audit-json-line font-mono text-[11px] text-slate-700" style={{ paddingLeft: leftPad }}>
        {label ? <span className="text-slate-500">{label}: </span> : null}
        <span className="audit-json-value">{primitiveText(value)}</span>
      </div>
    )
  }

  if (Array.isArray(value)) {
    const count = value.length
    return (
      <details open={defaultOpen} className="audit-json-details font-mono text-[11px] text-slate-700">
        <summary className="audit-json-summary cursor-pointer select-none" style={{ paddingLeft: leftPad }}>
          {label ? <span className="text-slate-500">{label}: </span> : null}
          <span>[{count}]</span>
        </summary>
        <div>
          {value.map((item, idx) => (
            <JsonNodeView key={`${depth}-${idx}`} label={String(idx)} value={item} depth={depth + 1} defaultOpen={false} />
          ))}
        </div>
      </details>
    )
  }

  const entries = Object.entries(value)
  return (
    <details open={defaultOpen} className="audit-json-details font-mono text-[11px] text-slate-700">
      <summary className="audit-json-summary cursor-pointer select-none" style={{ paddingLeft: leftPad }}>
        {label ? <span className="text-slate-500">{label}: </span> : null}
        <span>{'{'}{entries.length}{'}'}</span>
      </summary>
      <div>
        {entries.map(([k, v]) => (
          <JsonNodeView key={`${depth}-${k}`} label={k} value={v} depth={depth + 1} defaultOpen={false} />
        ))}
      </div>
    </details>
  )
}

type Props = {
  audits: AuditEvent[]
  loading: boolean
  error: string
}

export default function AuditTimeline({ audits, loading, error }: Props) {
  const [openIndex, setOpenIndex] = useState<number | null>(null)

  const sorted = useMemo(() => {
    return [...audits].sort((a, b) => {
      const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
      const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
      return ta - tb
    })
  }, [audits])

  if (loading && !sorted.length) {
    return <div className="border-b bg-white p-4 text-slate-400 text-sm">Loading audit timeline...</div>
  }

  if (error && !sorted.length) {
    return <div className="border-b bg-white p-4 text-rose-500 text-sm">{error}</div>
  }

  return (
    <div className="border-b bg-white h-full min-h-0">
      <div className="audit-scroll-hidden h-full overflow-y-auto px-4 py-3 space-y-3 text-xs">
        {!sorted.length && <div className="text-slate-400 text-sm">No audit events yet.</div>}

        {sorted.map((a, i) => {
          const meta = metaForStage(a.stage)
          const stageLabel = stageDisplayName(a)
          const isOpen = openIndex === i
          const payloadObj = parsePayload(a.payloadJson)

          return (
            <div key={`${a.auditId ?? i}-${a.createdAt ?? ''}`} className="relative pl-6">
              <div className={`absolute left-[11px] top-0 bottom-0 border-l-2 ${meta.color}`} />

              <div className="flex items-start gap-3 cursor-pointer group" onClick={() => setOpenIndex(isOpen ? null : i)}>
                <div className="text-sm mt-0.5">{meta.icon}</div>

                <div className="flex-1 min-w-0">
                  <div className="flex justify-between items-center gap-3">
                    <div className="font-medium text-slate-800 truncate">{stageLabel}</div>
                    <div className="flex items-center gap-2 text-slate-400 shrink-0">
                      <span>{a.createdAt ? new Date(a.createdAt).toLocaleTimeString() : '--:--:--'}</span>
                      <span className="group-hover:text-slate-600">{isOpen ? '▾' : '▸'}</span>
                    </div>
                  </div>
                </div>
              </div>

              {isOpen && a.payloadJson && (
                <div className="audit-json-card w-full min-w-0 mt-2 bg-slate-50 border rounded p-2 overflow-x-hidden">
                  {payloadObj !== null ? (
                    <JsonNodeView value={payloadObj} defaultOpen />
                  ) : (
                    <pre className="font-mono text-[11px] text-slate-700 whitespace-pre-wrap break-words overflow-x-hidden">{a.payloadJson}</pre>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
