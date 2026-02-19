import { ChangeEvent, FormEvent, useEffect, useMemo, useRef, useState } from 'react'
import Editor from '@monaco-editor/react'
import { Select } from '@mantine/core'
import formatXml from 'xml-formatter'
import {
  confirmMappings,
  exportMappingsXlsx,
  fetchConversationAudit,
  fetchDbStatus,
  initializeDb,
  saveMappings,
  sendStudioMessage,
  type AuditEvent
} from './api/convengine.api'
import type { EditableMapping, MappingExportRequest, MappingSuggestion, SourceType, StudioResponse, TargetType } from './types/studio'
import FlowCanvas from './components/FlowCanvas'
import MetadataTable from './components/MetadataTable'
import AuditTimeline from './components/AuditTimeline'

const defaultProjectCode = 'CAR_MODIFICATION_BNZADPT'
const defaultMappingVersion = '1.0.0'
const defaultSourceType: SourceType = 'JSON'
const defaultTargetType: TargetType = 'JSON'
const defaultSourceSpecByType: Record<SourceType, string> = {
  JSON: '{"agreement":{"customerName":"Robert King","agreementNumber":"AGR-77881"},"car":{"vehicleVin":"1HGCM82633A004352"}}',
  XML: '<PolicyRequest><CustomerName>Robert King</CustomerName><ContractId>C-5005</ContractId><VehicleVin>KMHCT4AE1FU123789</VehicleVin><Premium>1325.20</Premium></PolicyRequest>',
  DATABASE: '{"table":"policy_request","columns":["customer_name","contract_id","vehicle_vin","premium"]}',
}
const defaultXsdSchema = '<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="http://example.com/policy" xmlns:tns="http://example.com/policy" elementFormDefault="qualified"><xsd:element name="CreatePolicyRequest" type="tns:CreatePolicyRequestType"/><xsd:complexType name="CreatePolicyRequestType"><xsd:sequence><xsd:element name="CustomerName" type="xsd:string"/><xsd:element name="ContractId" type="xsd:string"/><xsd:element name="VehicleVin" type="xsd:string"/><xsd:element name="Premium" type="xsd:decimal"/></xsd:sequence></xsd:complexType></xsd:schema>'
const defaultWsdlSchema = '<wsdl:definitions xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/" xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:tns="http://example.com/policy" targetNamespace="http://example.com/policy"><wsdl:types><xsd:schema><xsd:import namespace="http://example.com/policy" schemaLocation="policy.xsd"/></xsd:schema></wsdl:types><wsdl:message name="CreatePolicyInput"><wsdl:part name="parameters" element="tns:CreatePolicyRequest"/></wsdl:message><wsdl:portType name="PolicyPortType"><wsdl:operation name="CreatePolicy"><wsdl:input message="tns:CreatePolicyInput"/></wsdl:operation></wsdl:portType><wsdl:binding name="PolicyBinding" type="tns:PolicyPortType"><soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http"/></wsdl:binding></wsdl:definitions>'
const defaultJsonSchemaFileName = 'schema.json'
const defaultJsonFileName = 'target.json'
const defaultXmlFileName = 'target.xml'
const defaultTargetSchemaByType: Record<TargetType, string> = {
  JSON: '{"customer":{"legalName":"Robert King"},"contract":{"contractId":"AGR-77881"},"vehicle":{"vin":"1HGCM82633A004352"}}',
  JSON_SCHEMA: '{"type":"object","properties":{"customer.legalName":{"type":"string"},"contract.contractId":{"type":"string"},"vehicle.vin":{"type":"string"}},"required":["customer.legalName","contract.contractId"]}',
  XML: '<CreatePolicyRequest><ContractNumber>AGR-77881</ContractNumber><CustomerFullName>Robert King</CustomerFullName><VehicleIdentifier>1HGCM82633A004352</VehicleIdentifier></CreatePolicyRequest>',
  XSD: '<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" elementFormDefault="qualified"><xsd:element name="PolicyRequest"><xsd:complexType><xsd:sequence><xsd:element name="CustomerName" type="xsd:string"/><xsd:element name="ContractId" type="xsd:string"/><xsd:element name="VehicleVin" type="xsd:string"/></xsd:sequence></xsd:complexType></xsd:element></xsd:schema>',
  'XSD+WSDL': defaultXsdSchema,
}
let xmlFormatterRegistered = false

const sourceTypeOptions = [
  { value: 'JSON', label: 'JSON' },
  { value: 'XML', label: 'XML' },
  { value: 'DATABASE', label: 'DATABASE' },
]

const targetTypeOptions = [
  { value: 'JSON', label: 'JSON' },
  { value: 'JSON_SCHEMA', label: 'JSON_SCHEMA' },
  { value: 'XML', label: 'XML' },
  { value: 'XSD', label: 'XSD' },
  { value: 'XSD+WSDL', label: 'XSD+WSDL' },
]

const isJsonTargetType = (type: TargetType) => type === 'JSON' || type === 'JSON_SCHEMA'

type UiToast = {
  kind: 'success' | 'error'
  title: string
  message: string
  infoChip?: string
  detail?: string
}

type SchemaArtifact = {
  name: string
  content: string
}

type ThemeMode = 'light' | 'dark'
type ViewMode = 'studio' | 'settings'
type ChatRole = 'user' | 'assistant'

type ChatBubble = {
  id: string
  role: ChatRole
  text: string
}

function formatSmallContent(content: string, language: 'json' | 'xml'): string {
  const trimmed = content.trim()
  if (!trimmed || trimmed.length >= 500) return content
  try {
    if (language === 'json') {
      return JSON.stringify(JSON.parse(trimmed), null, 2)
    }
    return formatXml(trimmed, { indentation: '  ' })
  } catch {
    return content
  }
}

function parseSuggestions(contextJson: string): MappingSuggestion[] {
  try {
    const ctx = JSON.parse(contextJson)
    if (Array.isArray(ctx?.mapping_suggestions)) {
      return ctx.mapping_suggestions as MappingSuggestion[]
    }
  } catch {
    // ignore
  }
  return []
}

function parseSuggestionsFromAudit(audits: AuditEvent[]): MappingSuggestion[] {
  for (let i = audits.length - 1; i >= 0; i -= 1) {
    const row = audits[i]
    const rawPayload =
      (row as unknown as Record<string, unknown>)?.payloadJson ??
      (row as unknown as Record<string, unknown>)?.payload_json
    if (!rawPayload || typeof rawPayload !== 'string') continue
    try {
      const payload = JSON.parse(rawPayload) as Record<string, unknown>
      const inputParams = payload?.inputParams as Record<string, unknown> | undefined
      const suggestions = inputParams?.mapping_suggestions
      if (Array.isArray(suggestions)) {
        return suggestions as MappingSuggestion[]
      }
      const inputParamsSnake = payload?.input_params as Record<string, unknown> | undefined
      const snakeSuggestions = inputParamsSnake?.mapping_suggestions
      if (Array.isArray(snakeSuggestions)) {
        return snakeSuggestions as MappingSuggestion[]
      }
      const directSuggestions = payload?.mapping_suggestions
      if (Array.isArray(directSuggestions)) {
        return directSuggestions as MappingSuggestion[]
      }
    } catch {
      // ignore bad payload row
    }
  }
  return []
}

function parseMissing(contextJson: string): string[] {
  try {
    const ctx = JSON.parse(contextJson)
    if (Array.isArray(ctx?.missing_required_target_fields)) {
      return ctx.missing_required_target_fields as string[]
    }
  } catch {
    // ignore
  }
  return []
}

function toEditableRows(rows: MappingSuggestion[]): EditableMapping[] {
  return rows.map((row, idx) => ({
    ...row,
    id: `${row.sourcePath}|${row.targetPath}|${idx}`,
    selected: true,
    notes: '',
    mappingOrigin: 'LLM_DERIVED',
  }))
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

function isGenerateExcelCommand(message: string): boolean {
  const normalized = message.trim().toLowerCase().replace(/\s+/g, ' ')
  return normalized === 'generate excel'
    || normalized === 'download excel'
    || normalized === 'export excel'
    || normalized === 'generate xlsx'
    || normalized === 'download xlsx'
    || normalized === 'export xlsx'
}

function stringifyPayload(payload: unknown): string {
  if (payload == null) return ''
  if (typeof payload === 'string') return payload
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
}

export default function App() {
  const [projectCode, setProjectCode] = useState(defaultProjectCode)
  const [mappingVersion, setMappingVersion] = useState(defaultMappingVersion)
  const [sourceType, setSourceType] = useState<SourceType>(defaultSourceType)
  const [targetType, setTargetType] = useState<TargetType>(defaultTargetType)
  const [sourceSpec, setSourceSpec] = useState(defaultSourceSpecByType[defaultSourceType])
  const [targetSchema, setTargetSchema] = useState(defaultTargetSchemaByType[defaultTargetType])
  const [targetSchemaXsd, setTargetSchemaXsd] = useState(defaultXsdSchema)
  const [targetSchemaWsdl, setTargetSchemaWsdl] = useState(defaultWsdlSchema)
  const [targetXsdArtifacts, setTargetXsdArtifacts] = useState<SchemaArtifact[]>([{ name: 'policy.xsd', content: defaultXsdSchema }])
  const [activeXsdArtifactIndex, setActiveXsdArtifactIndex] = useState(0)
  const [targetArtifactTab, setTargetArtifactTab] = useState<'XSD' | 'WSDL'>('XSD')
  const [targetSchemaFileName, setTargetSchemaFileName] = useState(defaultJsonFileName)
  const [targetXsdFileName, setTargetXsdFileName] = useState('policy.xsd')
  const [targetWsdlFileName, setTargetWsdlFileName] = useState('policy.wsdl')
  const [targetSchemaAttached, setTargetSchemaAttached] = useState(false)
  const [targetXsdAttached, setTargetXsdAttached] = useState(false)
  const [targetWsdlAttached, setTargetWsdlAttached] = useState(false)
  const [conversationId, setConversationId] = useState<string | undefined>(undefined)
  const [chatMessage, setChatMessage] = useState('')
  const [chatBubbles, setChatBubbles] = useState<ChatBubble[]>([])
  const [response, setResponse] = useState<StudioResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [savingMappings, setSavingMappings] = useState(false)
  const [manualConfirmed, setManualConfirmed] = useState(false)
  const [error, setError] = useState('')
  const [toast, setToast] = useState<UiToast | null>(null)
  const [themeMode, setThemeMode] = useState<ThemeMode>(() => {
    if (typeof window === 'undefined') return 'light'
    const saved = window.localStorage.getItem('mapper_studio_theme')
    return saved === 'dark' ? 'dark' : 'light'
  })
  const [currentPath, setCurrentPath] = useState<string>(() => (typeof window !== 'undefined' ? window.location.pathname : '/'))
  const [dbInitialized, setDbInitialized] = useState(false)
  const [dbStatusLoading, setDbStatusLoading] = useState(false)
  const [dbInitLoading, setDbInitLoading] = useState(false)

  const [auditOpen, setAuditOpen] = useState(false)
  const [auditLoading, setAuditLoading] = useState(false)
  const [auditError, setAuditError] = useState('')
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([])
  const [copiedConvId, setCopiedConvId] = useState(false)
  const [auditDrawerWidth, setAuditDrawerWidth] = useState(460)
  const [auditResizing, setAuditResizing] = useState(false)
  const auditResizingRef = useRef(false)
  const [stableSuggestions, setStableSuggestions] = useState<MappingSuggestion[]>([])
  const [editableMappings, setEditableMappings] = useState<EditableMapping[]>([])
  const suggestionsSignatureRef = useRef<string>('[]')
  const viewMode: ViewMode = currentPath.endsWith('/settings') ? 'settings' : 'studio'
  const sourceLanguage = sourceType === 'XML' ? 'xml' : 'json'
  const targetLanguage = isJsonTargetType(targetType) ? 'json' : 'xml'

  const suggestions = useMemo(() => {
    if (!response) return []
    const fromContext = parseSuggestions(response.contextJson)
    if (fromContext.length > 0) return fromContext
    return parseSuggestionsFromAudit(auditEvents)
  }, [response, auditEvents])

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', themeMode)
    window.localStorage.setItem('mapper_studio_theme', themeMode)
  }, [themeMode])

  useEffect(() => {
    const onPop = () => setCurrentPath(window.location.pathname)
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  useEffect(() => {
    const onMouseMove = (event: MouseEvent) => {
      if (!auditResizingRef.current) return
      const minWidth = 460
      const maxWidth = Math.floor(window.innerWidth * 0.8)
      const next = Math.min(Math.max(window.innerWidth - event.clientX, minWidth), maxWidth)
      setAuditDrawerWidth(next)
    }

    const stopResize = () => {
      if (!auditResizingRef.current) return
      auditResizingRef.current = false
      setAuditResizing(false)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', stopResize)
    window.addEventListener('mouseleave', stopResize)

    return () => {
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', stopResize)
      window.removeEventListener('mouseleave', stopResize)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
  }, [])

  useEffect(() => {
    if (viewMode !== 'settings') return
    let cancelled = false
    const loadStatus = async () => {
      setDbStatusLoading(true)
      try {
        const status = await fetchDbStatus()
        if (!cancelled) setDbInitialized(!!status.initialized)
      } catch {
        if (!cancelled) {
          setDbInitialized(false)
          notifyError('Failed to fetch DB status', 'Unable to check DB initialization status.', 'Settings')
        }
      } finally {
        if (!cancelled) setDbStatusLoading(false)
      }
    }
    loadStatus()
    return () => {
      cancelled = true
    }
  }, [viewMode])

  useEffect(() => {
    const formatted = formatSmallContent(sourceSpec, sourceLanguage)
    if (formatted !== sourceSpec) {
      setSourceSpec(formatted)
    }
  }, [sourceSpec, sourceLanguage])

  useEffect(() => {
    if (targetType === 'XSD+WSDL') {
      const formattedXsd = formatSmallContent(targetSchemaXsd, 'xml')
      if (formattedXsd !== targetSchemaXsd) {
        setTargetSchemaXsd(formattedXsd)
        setTargetXsdArtifacts((prev) => prev.map((row, idx) => (idx === activeXsdArtifactIndex ? { ...row, content: formattedXsd } : row)))
      }
      const formattedWsdl = formatSmallContent(targetSchemaWsdl, 'xml')
      if (formattedWsdl !== targetSchemaWsdl) {
        setTargetSchemaWsdl(formattedWsdl)
      }
      return
    }
    const formatted = formatSmallContent(targetSchema, targetLanguage)
    if (formatted !== targetSchema) {
      setTargetSchema(formatted)
    }
  }, [targetType, targetLanguage, targetSchema, targetSchemaXsd, targetSchemaWsdl, activeXsdArtifactIndex])

  useEffect(() => {
    const signature = JSON.stringify(suggestions)
    if (signature !== suggestionsSignatureRef.current) {
      suggestionsSignatureRef.current = signature
      setStableSuggestions(suggestions)
      setEditableMappings(toEditableRows(suggestions))
      setManualConfirmed(false)
    }
  }, [suggestions])

  const missingTargets = useMemo(() => (response ? parseMissing(response.contextJson) : ['customer.legalName']), [response])

  useEffect(() => {
    if (!conversationId || !auditOpen) {
      return
    }

    let cancelled = false

    const load = async () => {
      setAuditLoading(true)
      setAuditError('')
      try {
        const events = await fetchConversationAudit(conversationId)
        if (!cancelled) {
          setAuditEvents(events)
        }
      } catch (err) {
        if (!cancelled) {
          setAuditError(err instanceof Error ? err.message : 'Failed to load audit')
        }
      } finally {
        if (!cancelled) {
          setAuditLoading(false)
        }
      }
    }

    load()
    const timer = setInterval(load, 2000)

    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [conversationId, auditOpen])

  useEffect(() => {
    if (!toast) return
    const timer = setTimeout(() => setToast(null), 5000)
    return () => clearTimeout(timer)
  }, [toast])

  function notifyError(message: string, detail?: string, infoChip: string = 'Error') {
    setToast({ kind: 'error', title: 'Request Failed', message, infoChip, detail })
  }

  function notifySuccess(message: string, detail?: string) {
    setToast({ kind: 'success', title: 'Response Received', message, infoChip: 'Success', detail })
  }

  function buildMappingPayload(): MappingExportRequest {
    const pathType = sourceType === 'XML' ? 'XML_PATH' : 'JSON_PATH'
    return {
      projectCode: projectCode.trim() || defaultProjectCode,
      mappingVersion: mappingVersion.trim() || defaultMappingVersion,
      sourceType,
      targetType,
      pathType,
      mappings: editableMappings.map((row) => ({
        sourcePath: row.sourcePath,
        targetPath: row.targetPath,
        transformType: row.transformType,
        confidence: Number.isFinite(row.confidence) ? row.confidence : 0,
        reason: row.reason,
        notes: row.notes,
        mappingOrigin: row.mappingOrigin ?? (row.manualOverride ? 'EDITED' : 'LLM_DERIVED'),
        selected: row.selected !== false,
        manualOverride: !!row.manualOverride,
        targetArtifactName: row.targetArtifactName,
        targetArtifactType: row.targetArtifactType,
      })),
    }
  }

  async function onSaveMappings() {
    if (editableMappings.length === 0) {
      notifyError('No mappings available to save.')
      return
    }
    setSavingMappings(true)
    try {
      const payload = buildMappingPayload()
      const result = await saveMappings(payload)
      notifySuccess(`Saved ${result.savedCount} mappings`, `Project ${result.projectCode} v${result.mappingVersion}`)
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to save mappings'
      notifyError(msg, 'Backend save failed. Check backend logs for details.', 'Save')
    } finally {
      setSavingMappings(false)
    }
  }

  async function onSaveAndDownloadMappings(options?: { forceConfirm?: boolean }) {
    const forceConfirm = options?.forceConfirm === true
    if (editableMappings.length === 0) {
      notifyError('No mappings available to export.')
      return
    }
    if (!forceConfirm && !manualConfirmed) {
      notifyError('Manual confirmation is required before export.', 'Check the confirmation box in Mappings and try again.', 'Confirm')
      return
    }
    setSavingMappings(true)
    try {
      const payload = buildMappingPayload()
      await saveMappings(payload)
      await confirmMappings(payload)
      const blob = await exportMappingsXlsx(payload)
      const fileName = `${payload.projectCode}_${payload.mappingVersion}_mappings.xlsx`
      downloadBlob(blob, fileName)
      notifySuccess('Mappings confirmed, saved, and XLSX downloaded', fileName)
      return true
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Failed to export mappings'
      notifyError(msg, 'Export failed. Ensure manual confirmation exists and backend is reachable.', 'Export')
      return false
    } finally {
      setSavingMappings(false)
    }
  }

  function appendBubble(role: ChatRole, text: string) {
    const content = text.trim()
    if (!content) return
    setChatBubbles((prev) => [...prev, { id: `${Date.now()}-${prev.length}`, role, text: content }])
  }

  function appendAuditError(stage: string, message: string, convId?: string) {
    const payload = JSON.stringify({
      error: true,
      message,
      source: 'mapper-studio-ui'
    })
    setAuditEvents((prev) => [
      ...prev,
      {
        conversationId: convId || conversationId,
        stage,
        payloadJson: payload,
        createdAt: new Date().toISOString()
      }
    ])
  }

  async function runStudioTurn(message: string, e?: FormEvent, reuseConversation: boolean = true) {
    e?.preventDefault()
    const normalizedSourceSpec = sourceSpec.trim()
    const normalizedTargetSchema = targetSchema.trim()
    const normalizedTargetSchemaXsd = targetSchemaXsd.trim()
    const normalizedTargetSchemaWsdl = targetSchemaWsdl.trim()
    const isDualArtifactTarget = targetType === 'XSD+WSDL'
    const effectiveTargetSchema = isDualArtifactTarget
      ? (normalizedTargetSchemaXsd || normalizedTargetSchemaWsdl)
      : normalizedTargetSchema

    if (!normalizedSourceSpec || !effectiveTargetSchema) {
      setError('Please provide both API input and target schema.')
      notifyError('Please provide both API input and target schema.')
      return
    }
    if (isDualArtifactTarget && (!normalizedTargetSchemaXsd || !normalizedTargetSchemaWsdl)) {
      setError('Please provide both XSD and WSDL artifacts for XSD+WSDL mode.')
      notifyError('Please provide both XSD and WSDL artifacts for XSD+WSDL mode.')
      return
    }
    setLoading(true)
    setError('')
    setToast(null)
    const convId = reuseConversation ? conversationId : undefined
    if (!reuseConversation) {
      setResponse(null)
      setChatBubbles([])
      setStableSuggestions([])
      setEditableMappings([])
      setManualConfirmed(false)
      suggestionsSignatureRef.current = '[]'
    }
    appendBubble('user', message)
    try {
      const res = await sendStudioMessage(message, convId, {
        source_payload_type: sourceType === 'XML' ? 'XML' : 'JSON',
        target_payload_type: isJsonTargetType(targetType) ? 'JSON' : 'XML',
        projectCode: projectCode.trim(),
        mappingVersion: mappingVersion.trim(),
        sourceType,
        targetType,
        sourceSpec: normalizedSourceSpec,
        targetSchema: effectiveTargetSchema,
        targetSchemaJson: isJsonTargetType(targetType) ? effectiveTargetSchema : '',
        targetSchemaXsd: targetType === 'XSD+WSDL' ? normalizedTargetSchemaXsd : targetType === 'XSD' ? effectiveTargetSchema : '',
        targetSchemaWsdl: targetType === 'XSD+WSDL' ? normalizedTargetSchemaWsdl : '',
        targetSchemaXsdName: targetXsdFileName,
        targetSchemaWsdlName: targetWsdlFileName,
        targetSchemaXsdList: (targetType === 'XSD+WSDL')
          ? targetXsdArtifacts.map((a) => ({ name: a.name, content: a.content }))
          : []
      })
      setConversationId(res.conversationId)
      setResponse(res)
      appendBubble('assistant', stringifyPayload(res.payload))
      setChatMessage('')
      try {
        const events = await fetchConversationAudit(res.conversationId)
        setAuditEvents(events)
      } catch {
        // ignore audit fetch error on initial render; polling effect will retry.
      }
      if (res.state === 'ERROR') {
        const msg = stringifyPayload(res.payload) || 'Request failed'
        setError(msg)
        notifyError(msg, 'Backend handled the failure gracefully. Check audit timeline for details.', 'Backend')
        appendAuditError('ENGINE_KNOWN_FAILURE', msg, res.conversationId)
      } else {
        notifySuccess('Response received')
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Unknown error'
      const normalized = msg.toLowerCase().includes('failed to fetch') ? 'Failed to fetch' : msg
      setError(normalized)
      const infoChip = normalized === 'Failed to fetch' ? 'Network' : 'Backend'
      const detail = normalized === 'Failed to fetch'
        ? 'Cannot reach backend endpoint. Check server status, API URL, and CORS/network access.'
        : 'Request failed at backend processing. Check logs and validate input artifacts/schema.'
      notifyError(normalized, detail, infoChip)
      appendAuditError('CLIENT_ERROR', normalized, convId)
    } finally {
      setLoading(false)
    }
  }

  async function onSubmit(e: FormEvent) {
    await runStudioTurn('Start mapping studio.', e, false)
  }

  async function onSendMessage() {
    const msg = chatMessage.trim()
    if (!msg) {
      setError('Please enter a message to send.')
      notifyError('Please enter a message to send.')
      return
    }
    setChatMessage('')
    if (isGenerateExcelCommand(msg)) {
      appendBubble('user', msg)
      const exported = await onSaveAndDownloadMappings({ forceConfirm: true })
      if (exported) {
        appendBubble('assistant', 'XLSX generated and downloaded.')
      }
      return
    }
    await runStudioTurn(msg)
  }

  async function onCopyConversationId() {
    if (!conversationId) return
    try {
      await navigator.clipboard.writeText(conversationId)
      setCopiedConvId(true)
      setTimeout(() => setCopiedConvId(false), 1200)
    } catch {
      // ignore clipboard failures
    }
  }

  function ensureXmlFormatter(monaco: any) {
    if (xmlFormatterRegistered) return
    xmlFormatterRegistered = true
    monaco.languages.registerDocumentFormattingEditProvider('xml', {
      provideDocumentFormattingEdits(model: any, options: any) {
        try {
          const tabSize = Number(options?.tabSize || 2)
          const indentation = ' '.repeat(Math.max(1, tabSize))
          const text = formatXml(model.getValue(), {
            indentation,
            collapseContent: false,
            lineSeparator: '\n',
          })
          return [{ range: model.getFullModelRange(), text }]
        } catch {
          return []
        }
      },
    })
  }

  async function onTargetSchemaFileUpload(kind: 'JSON' | 'JSON_SCHEMA' | 'XML' | 'XSD' | 'WSDL', e: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (!files.length) return
    try {
      if (kind === 'JSON_SCHEMA' || kind === 'JSON') {
        const file = files[0]
        const content = await file.text()
        setTargetSchema(content)
        setTargetSchemaFileName(file.name)
        setTargetSchemaAttached(true)
      } else if (kind === 'XML') {
        const file = files[0]
        const content = await file.text()
        setTargetSchema(content)
        setTargetXsdFileName(file.name)
        setTargetXsdAttached(true)
      } else if (kind === 'XSD') {
        const artifacts = await Promise.all(
          files.map(async (file) => ({ name: file.name, content: await file.text() }))
        )
        const first = artifacts[0]
        setTargetXsdArtifacts(artifacts)
        setActiveXsdArtifactIndex(0)
        setTargetSchemaXsd(first.content)
        setTargetXsdFileName(first.name)
        setTargetXsdAttached(true)
        if (targetType === 'XSD' || targetType === 'XML') {
          setTargetSchema(first.content)
        }
        setTargetArtifactTab('XSD')
      } else {
        const file = files[0]
        const content = await file.text()
        setTargetSchemaWsdl(content)
        setTargetWsdlFileName(file.name)
        setTargetWsdlAttached(true)
        setTargetArtifactTab('WSDL')
      }
      setError('')
      setToast(null)
    } catch {
      const msg = `Failed to read ${kind} file.`
      setError(msg)
      notifyError(msg, 'File read failed. Verify file encoding/content and try re-uploading.', 'Attachment')
    } finally {
      e.target.value = ''
    }
  }

  function openSettingsPage() {
    window.history.pushState({}, '', '/settings')
    setCurrentPath('/settings')
  }

  function openStudioPage() {
    window.history.pushState({}, '', '/')
    setCurrentPath('/')
  }

  async function onInitDb() {
    setDbInitLoading(true)
    try {
      await initializeDb()
      const status = await fetchDbStatus()
      setDbInitialized(!!status.initialized)
      notifySuccess('DB entries initialized')
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'DB initialization failed'
      notifyError(msg, 'Admin DB init failed.', 'Settings')
    } finally {
      setDbInitLoading(false)
    }
  }

  function onAuditResizeMouseDown(event: { preventDefault: () => void }) {
    event.preventDefault()
    auditResizingRef.current = true
    setAuditResizing(true)
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  function onAuditResizeDoubleClick() {
    const defaultWidth = 460
    const maxWidth = Math.floor(window.innerWidth * 0.8)
    const threshold = 16
    setAuditDrawerWidth((prev) => (Math.abs(prev - maxWidth) <= threshold ? defaultWidth : maxWidth))
  }

  return (
    <>
      {viewMode === 'studio' && (
        <button
          type="button"
          className="settings-toggle"
          onClick={openSettingsPage}
          title="Open settings"
          style={{ display: auditOpen ? 'none' : 'inline-flex' }}
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8" />
            <path d="M19.4 15A1.65 1.65 0 0 0 19.73 16.82L19.79 16.88A2 2 0 1 1 16.96 19.71L16.9 19.65A1.65 1.65 0 0 0 15.08 19.32A1.65 1.65 0 0 0 14.1 20.83V20.99A2 2 0 1 1 10.1 20.99V20.9A1.65 1.65 0 0 0 9.02 19.38A1.65 1.65 0 0 0 7.2 19.71L7.14 19.77A2 2 0 1 1 4.31 16.94L4.37 16.88A1.65 1.65 0 0 0 4.7 15.06A1.65 1.65 0 0 0 3.19 14.08H3A2 2 0 1 1 3 10.08H3.09A1.65 1.65 0 0 0 4.61 9A1.65 1.65 0 0 0 4.28 7.18L4.22 7.12A2 2 0 1 1 7.05 4.29L7.11 4.35A1.65 1.65 0 0 0 8.93 4.68H9.01A1.65 1.65 0 0 0 9.99 3.17V3A2 2 0 1 1 13.99 3V3.09A1.65 1.65 0 0 0 14.97 4.6A1.65 1.65 0 0 0 16.79 4.27L16.85 4.21A2 2 0 1 1 19.68 7.04L19.62 7.1A1.65 1.65 0 0 0 19.29 8.92V9A1.65 1.65 0 0 0 20.8 9.98H21A2 2 0 1 1 21 13.98H20.91A1.65 1.65 0 0 0 19.4 15Z" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      )}

      {viewMode === 'studio' && (
      <button
        type="button"
        className="audit-toggle"
        onClick={() => setAuditOpen((v) => !v)}
        title="Show audit panel"
        style={{ display: auditOpen ? 'none' : 'inline-flex' }}
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <path d="M8 7H18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <path d="M8 12H18" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <path d="M8 17H14" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="5" cy="7" r="1.2" fill="currentColor" />
          <circle cx="5" cy="12" r="1.2" fill="currentColor" />
          <circle cx="5" cy="17" r="1.2" fill="currentColor" />
        </svg>
      </button>
      )}

      {viewMode === 'studio' && (
      <aside className={`audit-drawer ${auditOpen ? 'open' : ''} ${auditResizing ? 'resizing' : ''}`} style={{ width: `${auditDrawerWidth}px` }}>
        <div className="audit-resize-handle" onMouseDown={onAuditResizeMouseDown} onDoubleClick={onAuditResizeDoubleClick} title="Drag to resize (double-click to toggle max/default)" />
        <div className="audit-head">
          <h3>Audit Timeline</h3>
          <div className="audit-head-actions">
            <span className="audit-conv-id" title={conversationId ?? 'no-conversation'}>
              {conversationId ?? 'no-conversation'}
            </span>
            <button
              type="button"
              className={`audit-icon-btn audit-copy ${copiedConvId ? 'is-copied' : ''}`}
              onClick={onCopyConversationId}
              disabled={!conversationId}
              title={!conversationId ? 'No conversation id' : copiedConvId ? 'Conversation id copied' : 'Copy conversation id'}
              aria-label={copiedConvId ? 'Copied' : 'Copy'}
            >
              {copiedConvId ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M9 9.5H6.8C5.8 9.5 5 10.3 5 11.3V17.2C5 18.2 5.8 19 6.8 19H12.7C13.7 19 14.5 18.2 14.5 17.2V15" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                  <path d="M10 6.8H17.2C18.2 6.8 19 7.6 19 8.6V15.8C19 16.8 18.2 17.6 17.2 17.6H10C9 17.6 8.2 16.8 8.2 15.8V8.6C8.2 7.6 9 6.8 10 6.8Z" stroke="currentColor" strokeWidth="1.8" />
                  <path d="M11.3 12.3L12.9 13.9L16 10.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect x="9" y="7" width="10" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
                  <rect x="5" y="3" width="10" height="12" rx="2" stroke="currentColor" strokeWidth="1.8" />
                </svg>
              )}
            </button>
            <button type="button" className="audit-icon-btn audit-close" onClick={() => setAuditOpen(false)} title="Hide audit panel" aria-label="Close audit">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M7 7L17 17M17 7L7 17" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>

        <AuditTimeline audits={auditEvents} loading={auditLoading} error={auditError} />
      </aside>
      )}

      {toast && (
        <div className={`studio-alert ${toast.kind}`}>
          <button type="button" className="studio-alert-close" onClick={() => setToast(null)} aria-label="Close alert" title="Close">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M7 7L17 17M17 7L7 17" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>
          <div className="studio-alert-head">
            <span className="studio-alert-icon" aria-hidden="true">{toast.kind === 'success' ? '✓' : '!'}</span>
            <strong className="studio-alert-title">{toast.title}</strong>
            {toast.infoChip && <span className="studio-alert-chip">{toast.infoChip}</span>}
          </div>
          <div className="studio-alert-text">{toast.message}</div>
          {toast.detail && <div className="studio-alert-detail">{toast.detail}</div>}
        </div>
      )}

      <div className={`layout ${viewMode === 'settings' ? 'settings-layout' : ''}`}>
        {viewMode === 'settings' ? (
          <>
            <header className="hero-head">
              <div className="settings-kicker-row">
                <button type="button" className="settings-back-btn" onClick={openStudioPage}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M20 12H6M6 12L12 6M6 12L12 18" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  <span>Back to Studio</span>
                </button>
                <span className="hero-kicker">ConvEngine Mapping Suite</span>
              </div>
              <div className="hero-title-row">
                <h1 className="hero-title">
                  <span className="hero-title-accent">Mapper</span> Studio Settings
                </h1>
                <button
                  type="button"
                  className={`hero-theme-toggle ${themeMode === 'light' ? 'moon' : 'sun'}`}
                  onClick={() => setThemeMode((prev) => (prev === 'light' ? 'dark' : 'light'))}
                  aria-label={themeMode === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
                  title={themeMode === 'light' ? 'Dark mode' : 'Light mode'}
                >
                  {themeMode === 'light' ? (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M21 12.8A9 9 0 1 1 11.2 3C10.8 4.3 10.8 5.7 11.3 7C12.4 10 15 12 18.2 12.6C19.1 12.8 20.1 12.8 21 12.8Z" fill="currentColor" />
                    </svg>
                  ) : (
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <circle cx="12" cy="12" r="4.2" fill="currentColor" />
                      <path d="M12 2V5M12 19V22M22 12H19M5 12H2M19.1 4.9L17 7M7 17L4.9 19.1M19.1 19.1L17 17M7 7L4.9 4.9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
                    </svg>
                  )}
                </button>
              </div>
              <p className="hero-subtitle">Admin control for initial SQLite bootstrap.</p>
            </header>

            <div className="card settings-card settings-ios-card">
              <div className="settings-row settings-row-header">
                <div className="settings-row-stack">
                  <div className="settings-row-title">DB Settings</div>
                  <div className="settings-row-subtitle">Initial bootstrap and seed state.</div>
                </div>
              </div>

              <div className="settings-subcard">
                <span className={`settings-status-dot ${dbInitialized ? 'done' : 'pending'}`} aria-hidden="true">
                  {dbInitialized ? '✓' : '✕'}
                </span>
                <div className="settings-subcard-title-row">
                  <div className="settings-subcard-title">Initialize DB</div>
                  {dbInitialized && <span className="settings-chip-ok">DB entries initialized</span>}
                  {!dbInitialized && !dbStatusLoading && <span className="settings-chip-pending">DB entries not initialized</span>}
                </div>
                <div className="settings-subcard-subtitle">Create required entries for Mapper Studio runtime.</div>
                <div className="settings-subcard-actions">
                  <button
                    type="button"
                    className="ce-table-action primary settings-init-btn"
                    onClick={onInitDb}
                    disabled={dbInitialized || dbStatusLoading || dbInitLoading}
                  >
                    {dbInitLoading ? 'Initializing...' : 'Init DB'}
                  </button>
                </div>
              </div>
            </div>
          </>
        ) : (
          <>
        <header className="hero-head">
          <span className="hero-kicker">ConvEngine Mapping Suite</span>
          <div className="hero-title-row">
            <h1 className="hero-title">
              <span className="hero-title-accent">Mapper</span> Studio
            </h1>
            <button
              type="button"
              className={`hero-theme-toggle ${themeMode === 'light' ? 'moon' : 'sun'}`}
              onClick={() => setThemeMode((prev) => (prev === 'light' ? 'dark' : 'light'))}
              aria-label={themeMode === 'light' ? 'Switch to dark mode' : 'Switch to light mode'}
              title={themeMode === 'light' ? 'Dark mode' : 'Light mode'}
            >
              {themeMode === 'light' ? (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M21 12.8A9 9 0 1 1 11.2 3C10.8 4.3 10.8 5.7 11.3 7C12.4 10 15 12 18.2 12.6C19.1 12.8 20.1 12.8 21 12.8Z" fill="currentColor" />
                </svg>
              ) : (
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle cx="12" cy="12" r="4.2" fill="currentColor" />
                  <path d="M12 2V5M12 19V22M22 12H19M5 12H2M19.1 4.9L17 7M7 17L4.9 19.1M19.1 19.1L17 17M7 7L4.9 4.9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
                </svg>
              )}
            </button>
          </div>
          <p className="hero-subtitle">Map XML/JSON sources to JSON, JSON Schema, XML, XSD, or XSD+WSDL with AI-assisted suggestions and manual correction workflow.</p>
        </header>

        <form onSubmit={onSubmit} className="card">
          <div className="mapping-input-meta mapping-input-meta--4">
            <div>
              <label>Project Code</label>
              <input value={projectCode} onChange={(e) => setProjectCode(e.target.value)} />
            </div>
            <div>
              <label>Version</label>
              <input value={mappingVersion} onChange={(e) => setMappingVersion(e.target.value)} />
            </div>
            <div>
              <label>Source Type</label>
              <Select
                className="studio-select"
                value={sourceType}
                data={sourceTypeOptions}
                searchable={false}
                checkIconPosition="right"
                onChange={(value) => {
                  const next = (value ?? defaultSourceType) as SourceType
                  setSourceType(next)
                  setSourceSpec(defaultSourceSpecByType[next])
                }}
              />
            </div>
            <div>
              <label>Target Type</label>
              <Select
                className="studio-select"
                value={targetType}
                data={targetTypeOptions}
                searchable={false}
                checkIconPosition="right"
                onChange={(value) => {
                  const next = (value ?? defaultTargetType) as TargetType
                  setTargetType(next)
                  setTargetSchema(defaultTargetSchemaByType[next])
                  if (next === 'XSD+WSDL') {
                    setTargetArtifactTab('XSD')
                    setTargetXsdAttached(false)
                    setTargetWsdlAttached(false)
                    setTargetXsdFileName('policy.xsd')
                    setTargetWsdlFileName('policy.wsdl')
                    setTargetXsdArtifacts([{ name: 'policy.xsd', content: defaultXsdSchema }])
                    setActiveXsdArtifactIndex(0)
                    setTargetSchemaXsd(defaultXsdSchema)
                    setTargetSchemaWsdl(defaultWsdlSchema)
                  } else if (isJsonTargetType(next)) {
                    setTargetSchemaFileName(next === 'JSON_SCHEMA' ? defaultJsonSchemaFileName : defaultJsonFileName)
                    setTargetSchemaAttached(false)
                  } else if (next === 'XSD') {
                    setTargetXsdFileName('policy.xsd')
                    setTargetXsdAttached(false)
                    setTargetXsdArtifacts([{ name: 'policy.xsd', content: defaultTargetSchemaByType.XSD }])
                    setActiveXsdArtifactIndex(0)
                    setTargetSchemaXsd(defaultTargetSchemaByType.XSD)
                  } else if (next === 'XML') {
                    setTargetXsdFileName(defaultXmlFileName)
                    setTargetXsdAttached(false)
                  }
                }}
              />
            </div>
          </div>

          <div className="mapping-editors-wrap">
            <div className="mapping-input-grid">
              <div>
                <div className="ce-code-panel">
                  <div className="ce-code-panel-header">
                    <div className="ce-code-panel-head-left">
                      <div className="ce-code-panel-title">Source Spec</div>
                      <div className="ce-code-panel-path-wrap">
                        <span className="ce-code-panel-path ce-code-panel-path-package">type: {sourceType}</span>
                      </div>
                    </div>
                    <div className="ce-code-panel-head-right">
                      <span className="ce-code-lang">{sourceLanguage.toUpperCase()}</span>
                    </div>
                  </div>
                  <div className="ce-code-panel-body editor-shell">
                    <Editor
                      height="350px"
                      language={sourceLanguage}
                      value={sourceSpec}
                      onChange={(value) => setSourceSpec(value ?? '')}
                      onMount={(_, monaco) => ensureXmlFormatter(monaco)}
                      theme={themeMode === 'dark' ? 'vs-dark' : 'vs'}
                      options={{
                        minimap: { enabled: false },
                        fontSize: 13,
                        fontLigatures: true,
                        roundedSelection: true,
                        smoothScrolling: true,
                        wordWrap: 'on',
                        scrollBeyondLastLine: false,
                        tabSize: 2,
                        padding: { top: 12, bottom: 12 },
                        overviewRulerBorder: false,
                        lineNumbersMinChars: 3,
                      }}
                    />
                  </div>
                </div>
              </div>
              <div>
                <div className="ce-code-panel">
                  <div className="ce-code-panel-header">
                    <div className="ce-code-panel-head-left">
                      <div className="ce-code-panel-title">Target Schema</div>
                      <div className="ce-code-panel-path-wrap">
                        <span className="ce-code-panel-path ce-code-panel-path-package">type: {targetType}</span>
                      </div>
                    </div>
                    <div className="ce-code-panel-head-right">
                      {targetType === 'XSD+WSDL' ? (
                        <>
                          <label className={`ce-attach-chip ${targetXsdAttached ? 'is-attached' : 'is-pending'}`}>
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                              <path d="M8.5 12.5L13.8 7.2C15.3 5.7 17.7 5.7 19.2 7.2C20.7 8.7 20.7 11.1 19.2 12.6L11.8 20C9.8 22 6.5 22 4.5 20C2.5 18 2.5 14.7 4.5 12.7L12 5.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                            {targetXsdAttached ? `${targetXsdFileName}${targetXsdArtifacts.length > 1 ? ` +${targetXsdArtifacts.length - 1}` : ''}` : 'Attach XSD'}
                            <input type="file" multiple accept=".xsd,.xml,text/xml,application/xml" onChange={(e) => onTargetSchemaFileUpload('XSD', e)} />
                          </label>
                          <label className={`ce-attach-chip ${targetWsdlAttached ? 'is-attached' : 'is-pending'}`}>
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                              <path d="M8.5 12.5L13.8 7.2C15.3 5.7 17.7 5.7 19.2 7.2C20.7 8.7 20.7 11.1 19.2 12.6L11.8 20C9.8 22 6.5 22 4.5 20C2.5 18 2.5 14.7 4.5 12.7L12 5.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                            </svg>
                            {targetWsdlAttached ? targetWsdlFileName : 'Attach WSDL'}
                            <input type="file" accept=".wsdl,.xml,text/xml,application/xml" onChange={(e) => onTargetSchemaFileUpload('WSDL', e)} />
                          </label>
                        </>
                      ) : (
                        <label className={`ce-attach-chip ${isJsonTargetType(targetType) ? (targetSchemaAttached ? 'is-attached' : 'is-pending') : (targetXsdAttached ? 'is-attached' : 'is-pending')}`}>
                          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                            <path d="M8.5 12.5L13.8 7.2C15.3 5.7 17.7 5.7 19.2 7.2C20.7 8.7 20.7 11.1 19.2 12.6L11.8 20C9.8 22 6.5 22 4.5 20C2.5 18 2.5 14.7 4.5 12.7L12 5.2" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                          </svg>
                          {isJsonTargetType(targetType)
                            ? (targetSchemaAttached ? targetSchemaFileName : (targetType === 'JSON' ? 'Attach JSON' : 'Attach JSON Schema'))
                            : (targetXsdAttached ? targetXsdFileName : (targetType === 'XML' ? 'Attach XML' : 'Attach XSD'))}
                          <input
                            type="file"
                            multiple={targetType === 'XSD'}
                            accept={isJsonTargetType(targetType)
                              ? '.json,application/json,text/plain'
                              : targetType === 'XML'
                                ? '.xml,text/xml,application/xml'
                                : '.xsd,.xml,text/xml,application/xml'}
                            onChange={(e) => onTargetSchemaFileUpload(
                              isJsonTargetType(targetType)
                                ? (targetType === 'JSON' ? 'JSON' : 'JSON_SCHEMA')
                                : (targetType === 'XML' ? 'XML' : 'XSD'),
                              e
                            )}
                          />
                        </label>
                      )}
                      <span className="ce-code-lang">{targetLanguage.toUpperCase()}</span>
                    </div>
                  </div>
                  <div className="ce-code-panel-body editor-shell">
                    {targetType === 'XSD+WSDL' ? (
                      <div className="target-artifact-wrap">
                        <div className="target-artifact-tab-row">
                          <button type="button" className={`target-artifact-tab target-artifact-tab-xsd ${targetArtifactTab === 'XSD' ? 'active' : ''}`} onClick={() => setTargetArtifactTab('XSD')}>
                            XSD
                          </button>
                          <button type="button" className={`target-artifact-tab target-artifact-tab-wsdl ${targetArtifactTab === 'WSDL' ? 'active' : ''}`} onClick={() => setTargetArtifactTab('WSDL')}>
                            WSDL
                          </button>
                          <span className="target-artifact-file-name">
                            {targetArtifactTab === 'XSD'
                              ? `${targetXsdFileName}${targetXsdArtifacts.length > 1 ? ` (+${targetXsdArtifacts.length - 1})` : ''}`
                              : targetWsdlFileName}
                          </span>
                        </div>
                        {targetArtifactTab === 'XSD' && targetXsdArtifacts.length > 1 && (
                          <div className="target-artifact-file-chip-row">
                            {targetXsdArtifacts.map((artifact, index) => (
                              <button
                                key={artifact.name + index}
                                type="button"
                                className={`target-artifact-file-chip ${index === activeXsdArtifactIndex ? 'active' : ''}`}
                                onClick={() => {
                                  setActiveXsdArtifactIndex(index)
                                  setTargetXsdFileName(artifact.name)
                                  setTargetSchemaXsd(artifact.content)
                                }}
                              >
                                {artifact.name}
                              </button>
                            ))}
                          </div>
                        )}
                        <Editor
                          height="100%"
                          language="xml"
                          value={targetArtifactTab === 'XSD' ? targetSchemaXsd : targetSchemaWsdl}
                          onChange={(value) => {
                            if (targetArtifactTab === 'XSD') {
                              const nextValue = value ?? ''
                              setTargetSchemaXsd(nextValue)
                              setTargetXsdArtifacts((prev) => prev.map((item, index) => (
                                index === activeXsdArtifactIndex ? { ...item, content: nextValue } : item
                              )))
                            } else {
                              setTargetSchemaWsdl(value ?? '')
                            }
                          }}
                          onMount={(_, monaco) => ensureXmlFormatter(monaco)}
                          theme={themeMode === 'dark' ? 'vs-dark' : 'vs'}
                          options={{
                            minimap: { enabled: false },
                            fontSize: 13,
                            fontLigatures: true,
                            roundedSelection: true,
                            smoothScrolling: true,
                            wordWrap: 'on',
                            scrollBeyondLastLine: false,
                            tabSize: 2,
                            padding: { top: 12, bottom: 12 },
                            overviewRulerBorder: false,
                            lineNumbersMinChars: 3,
                          }}
                        />
                      </div>
                    ) : (
                      <Editor
                        height="350px"
                        language={targetLanguage}
                        value={targetSchema}
                        onChange={(value) => setTargetSchema(value ?? '')}
                        onMount={(_, monaco) => ensureXmlFormatter(monaco)}
                        theme={themeMode === 'dark' ? 'vs-dark' : 'vs'}
                        options={{
                          minimap: { enabled: false },
                          fontSize: 13,
                          fontLigatures: true,
                          roundedSelection: true,
                          smoothScrolling: true,
                          wordWrap: 'on',
                          scrollBeyondLastLine: false,
                          tabSize: 2,
                          padding: { top: 12, bottom: 12 },
                          overviewRulerBorder: false,
                          lineNumbersMinChars: 3,
                        }}
                      />
                    )}
                  </div>
                </div>
              </div>
            </div>
            <button
              type="submit"
              className={`studio-run-icon-btn ${loading ? 'is-running' : ''}`}
              disabled={loading}
              title={loading ? 'Running' : 'Run mapping turn'}
              aria-label={loading ? 'Running' : 'Run'}
            >
              {loading ? (
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <rect x="4.5" y="4.5" width="15" height="15" rx="2" fill="currentColor" />
                </svg>
              ) : (
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M5 3L21 12L5 21V3Z" fill="currentColor" />
                </svg>
              )}
            </button>
          </div>

          <div className="studio-chat-row">
            <div className="studio-chat-card">
              <div className="studio-chat-thread">
              {chatBubbles.length === 0 ? (
                <div className="studio-chat-empty">No messages yet.</div>
              ) : (
                chatBubbles.map((bubble) => (
                    <div
                      key={bubble.id}
                      className={`studio-chat-bubble ${bubble.role === 'user' ? 'studio-chat-bubble-user' : 'studio-chat-bubble-assistant'}`}
                    >
                      <div className="studio-chat-bubble-role">
                        <span className={`studio-chat-bubble-icon ${bubble.role === 'user' ? 'studio-chat-bubble-icon-user' : 'studio-chat-bubble-icon-assistant'}`} aria-hidden="true">
                          {bubble.role === 'user' ? (
                            <svg viewBox="0 0 24 24" fill="none">
                              <path d="M12 12C14.2 12 16 10.2 16 8C16 5.8 14.2 4 12 4C9.8 4 8 5.8 8 8C8 10.2 9.8 12 12 12Z" fill="currentColor" />
                              <path d="M4 20C4.5 16.8 7 15 12 15C17 15 19.5 16.8 20 20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                            </svg>
                          ) : (
                            <svg viewBox="0 0 24 24" fill="none">
                              <rect x="5" y="4" width="14" height="16" rx="3" stroke="currentColor" strokeWidth="2" />
                              <circle cx="9" cy="10" r="1.2" fill="currentColor" />
                              <circle cx="15" cy="10" r="1.2" fill="currentColor" />
                              <path d="M9 14.5H15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                            </svg>
                          )}
                        </span>
                        <span>{bubble.role === 'user' ? 'You' : 'Mapper Studio Agent'}</span>
                      </div>
                      <pre className="studio-chat-bubble-text">{bubble.text}</pre>
                    </div>
                ))
              )}
              {loading && (
                <div className="studio-chat-bubble studio-chat-bubble-assistant studio-chat-thinking" aria-live="polite">
                  <div className="studio-chat-thinking-text">
                    Agent is thinking
                    <span className="studio-thinking-dots" aria-hidden="true">
                      <span />
                      <span />
                      <span />
                    </span>
                  </div>
                </div>
              )}
            </div>
              <div className="studio-chat-compose">
                <textarea
                  className="studio-chat-textarea"
                  value={chatMessage}
                  onChange={(e) => setChatMessage(e.target.value)}
                  placeholder="Type a message to send to ConvEngine..."
                  disabled={loading}
                  rows={3}
                />
                <button type="button" className="studio-chat-send" onClick={onSendMessage} disabled={loading || !chatMessage.trim()} title="Send">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M3 20L21 12L3 4L3 10L15 12L3 14L3 20Z" fill="currentColor" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </form>

        {response && (
          <>
            <div className="card">
              <h3>Conversation</h3>
              <p><b>Conversation ID:</b> {response.conversationId}</p>
              <p><b>Intent:</b> {response.intent}</p>
              <p><b>State:</b> {response.state}</p>
              <p><b>Assistant:</b> {String(response.payload ?? '')}</p>
            </div>

            {response.state !== 'PUBLISHED' && (
              <>
                <div className="card">
                  <h3>Mapping Flow</h3>
                  <FlowCanvas
                    rows={editableMappings}
                    missingTargets={missingTargets}
                    sourceType={sourceType}
                    targetType={targetType}
                    onRowsChange={(rows) => { setEditableMappings(rows); setManualConfirmed(false) }}
                  />
                </div>

                <MetadataTable
                  rows={editableMappings}
                  sourceType={sourceType}
                  targetType={targetType}
                  saving={savingMappings}
                  manualConfirmed={manualConfirmed}
                  onManualConfirmedChange={setManualConfirmed}
                  onRowsChange={(rows) => { setEditableMappings(rows); setManualConfirmed(false) }}
                  onSave={onSaveMappings}
                  onSaveDownload={onSaveAndDownloadMappings}
                />
              </>
            )}
          </>
        )}
          </>
        )}
      </div>
    </>
  )
}
