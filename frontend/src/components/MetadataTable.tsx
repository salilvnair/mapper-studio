import { useMemo, useState } from 'react'
import { Checkbox, TextInput } from '@mantine/core'
import type { EditableMapping, SourceType, TargetType } from '../types/studio'

type Props = {
  rows: EditableMapping[]
  sourceType: SourceType
  targetType: TargetType
  saving?: boolean
  manualConfirmed: boolean
  onManualConfirmedChange: (value: boolean) => void
  onRowsChange: (rows: EditableMapping[]) => void
  onSave: () => void
  onSaveDownload: () => void
}

function normalizeToken(value: string): string {
  return (value || '')
    .trim()
    .replace(/\s+/g, '_')
    .replace(/[^a-zA-Z0-9_.-]/g, '')
}

function toXmlPath(path: string): string {
  if (!path) return '/'
  const cleaned = path
    .replace(/\[(\d+)\]/g, '')
    .replace(/\/+/g, '.')
    .replace(/\.+/g, '.')
    .replace(/^\.|\.$/g, '')
  return '/' + cleaned.split('.').filter(Boolean).join('/')
}

function toJsonPath(path: string): string {
  if (!path) return '$'
  const cleaned = path
    .replace(/\[(\d+)\]/g, '[$1]')
    .replace(/\/+/g, '.')
    .replace(/\.+/g, '.')
    .replace(/^\.|\.$/g, '')
  return '$.' + cleaned
}

function fromXmlPath(path: string): string {
  return (path || '')
    .replace(/^\/+/, '')
    .replace(/\/+/g, '.')
    .replace(/\.+/g, '.')
    .replace(/^\.|\.$/g, '')
}

function fromJsonPath(path: string): string {
  return (path || '')
    .replace(/^\$\.?/, '')
    .replace(/\[(\d+)\]/g, '.$1')
    .replace(/\.+/g, '.')
    .replace(/^\.|\.$/g, '')
}

function leaf(path: string): string {
  const cleaned = path.replace(/\.+/g, '.').replace(/^\.|\.$/g, '')
  if (!cleaned) return ''
  const parts = cleaned.split('.').filter(Boolean)
  return parts[parts.length - 1] || ''
}

function replaceLeaf(path: string, nextLeaf: string): string {
  const cleaned = path.replace(/\.+/g, '.').replace(/^\.|\.$/g, '')
  const next = normalizeToken(nextLeaf)
  if (!cleaned) return next
  const parts = cleaned.split('.').filter(Boolean)
  if (parts.length === 0) return next
  parts[parts.length - 1] = next || parts[parts.length - 1]
  return parts.join('.')
}

export default function MetadataTable({
  rows,
  sourceType,
  targetType,
  saving,
  manualConfirmed,
  onManualConfirmedChange,
  onRowsChange,
  onSave,
  onSaveDownload,
}: Props) {
  const [editingIds, setEditingIds] = useState<Record<string, boolean>>({})
  const xmlMode = sourceType === 'XML'
  const pathLabel = xmlMode ? 'XML Path' : 'JSON Path'
  const editingActive = useMemo(() => Object.values(editingIds).some(Boolean), [editingIds])

  const updateRow = (idx: number, patch: Partial<EditableMapping>) => {
    const next = rows.map((row, i) => {
      if (i !== idx) return row
      return { ...row, ...patch, manualOverride: true, mappingOrigin: 'EDITED' }
    })
    onRowsChange(next)
  }

  const removeRow = (idx: number) => {
    const row = rows[idx]
    if (row?.id) {
      const next = { ...editingIds }
      delete next[row.id]
      setEditingIds(next)
    }
    onRowsChange(rows.filter((_, i) => i !== idx))
  }

  const toggleRowEdit = (idx: number) => {
    const row = rows[idx]
    if (!row?.id) return
    setEditingIds((prev) => ({ ...prev, [row.id]: !prev[row.id] }))
  }

  const addRow = () => {
    const id = `manual-${Date.now()}`
    onRowsChange([
      ...rows,
      {
        id,
        sourcePath: '',
        targetPath: '',
        confidence: 1,
        transformType: 'DIRECT',
        reason: 'Manually added mapping',
        notes: '',
        selected: true,
        manualOverride: true,
        mappingOrigin: 'EDITED',
      },
    ])
    setEditingIds((prev) => ({ ...prev, [id]: true }))
  }

  const confidenceClass = (value: number) => {
    if (value >= 0.8) return 'meta-chip-conf-high'
    if (value >= 0.7) return 'meta-chip-conf-mid'
    return 'meta-chip-conf-low'
  }

  return (
    <div className="ce-table-card">
      <div className="ce-table-card-head">
        <h3 className="ce-table-card-title">Mappings</h3>
        <div className="ce-mapping-actions">
          <label className="ce-confirm-toggle">
            <Checkbox size="xs" checked={manualConfirmed} onChange={(e) => onManualConfirmedChange(e.currentTarget.checked)} />
            Manually confirmed final mappings
          </label>
          {/* Add Row intentionally hidden for now; S+ in Flow is the supported source-creation path. */}
          <button type="button" className="ce-table-action" onClick={onSave} disabled={saving || rows.length === 0}>Save</button>
          <button
            type="button"
            className="ce-table-action primary"
            onClick={onSaveDownload}
            disabled={saving || rows.length === 0 || !manualConfirmed}
            title={!manualConfirmed ? 'Check manual confirmation before export' : 'Save and download XLSX'}
          >
            Save &amp; Download
          </button>
        </div>
      </div>
      <div className="ce-table-wrap">
        <table className="ce-mapping-table ce-mapping-table-editable">
          <colgroup>
            <col className="meta-col-check" />
            <col className="meta-col-source" />
            <col className="meta-col-xml" />
            <col className="meta-col-target" />
            <col className="meta-col-transform" />
            <col className="meta-col-confidence" />
            <col className="meta-col-origin" />
            <col className="meta-col-reason" />
            <col className="meta-col-remove" />
          </colgroup>
          <thead>
            <tr>
              <th>Use</th>
              <th>Source</th>
              <th>{pathLabel}</th>
              <th>Target</th>
              <th>Transform</th>
              <th>Confidence</th>
              <th>Origin</th>
              <th>{editingActive ? 'Notes' : 'Reason'}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={9}>No mappings available for this turn.</td>
              </tr>
            ) : (
              rows.map((r, idx) => {
                const isEditing = !!editingIds[r.id]
                const pathValue = xmlMode ? toXmlPath(r.sourcePath) : toJsonPath(r.sourcePath)
                const origin = r.mappingOrigin ?? (r.manualOverride ? 'EDITED' : 'LLM_DERIVED')
                return (
                  <tr key={r.id || `${r.sourcePath}-${idx}`}>
                    <td>
                      <div className="ce-cell-flex">
                        <Checkbox size="xs" checked={r.selected !== false} onChange={(e) => updateRow(idx, { selected: e.currentTarget.checked })} />
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        {isEditing ? (
                          <TextInput
                            className="ce-mapping-input-mantine"
                            value={leaf(r.sourcePath)}
                            placeholder="Enter source field"
                            onChange={(e) => {
                              const token = e.currentTarget.value
                              const nextPath = replaceLeaf(r.sourcePath, token)
                              updateRow(idx, { sourcePath: nextPath })
                            }}
                          />
                        ) : (
                          <span className="meta-chip meta-chip-source">{leaf(r.sourcePath) || '-'}</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        {isEditing ? (
                          <TextInput
                            className="ce-mapping-input-mantine"
                            value={pathValue}
                            placeholder={xmlMode ? '/root/field' : '$.root.field'}
                            onChange={(e) => {
                              const raw = e.currentTarget.value
                              const nextPath = xmlMode ? fromXmlPath(raw) : fromJsonPath(raw)
                              updateRow(idx, { sourcePath: nextPath })
                            }}
                          />
                        ) : (
                          <span className="meta-chip meta-chip-xml">{pathValue}</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        {isEditing ? (
                          <TextInput
                            className="ce-mapping-input-mantine"
                            value={r.targetPath}
                            placeholder="Enter target field"
                            onChange={(e) => updateRow(idx, { targetPath: normalizeToken(e.currentTarget.value) })}
                          />
                        ) : (
                          <span className="meta-chip meta-chip-target">{r.targetPath || '-'}</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        <span className="meta-chip meta-chip-transform">{r.transformType}</span>
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        <span className={`meta-chip ${confidenceClass(r.confidence)}`}>{Math.round((r.confidence || 0) * 100)}%</span>
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        <span className={`meta-chip ${origin === 'EDITED' ? 'meta-chip-origin-edited' : 'meta-chip-origin-llm'}`}>{origin}</span>
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        {editingActive ? (
                          isEditing ? (
                            <TextInput
                              className="ce-mapping-input-mantine"
                              value={r.notes ?? ''}
                              placeholder="Add reviewer notes"
                              onChange={(e) => updateRow(idx, { notes: e.currentTarget.value })}
                            />
                          ) : (
                            <span className="meta-reason-text">{r.notes || '-'}</span>
                          )
                        ) : (
                          <span className="meta-reason-text">{r.reason}</span>
                        )}
                      </div>
                    </td>
                    <td>
                      <div className="ce-cell-flex">
                        <div className="ce-row-icon-actions">
                          <button
                            type="button"
                            className="ce-row-icon-btn"
                            onClick={() => toggleRowEdit(idx)}
                            title={isEditing ? 'Stop editing' : 'Edit row'}
                            aria-label={isEditing ? 'Stop editing' : 'Edit row'}
                          >
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                              <path d="M4 20H8L18 10L14 6L4 16V20Z" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
                              <path d="M12.5 7.5L16.5 11.5" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
                            </svg>
                          </button>
                          <button type="button" className="ce-row-icon-btn ce-row-icon-btn-danger" onClick={() => removeRow(idx)} aria-label="Remove row" title="Remove row">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                              <path d="M6 19C6 20.1 6.9 21 8 21H16C17.1 21 18 20.1 18 19V8H6V19ZM19 5H15.5L14.5 4H9.5L8.5 5H5V7H19V5Z" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
