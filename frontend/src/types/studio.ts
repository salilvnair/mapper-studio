export type SourceType = 'XML' | 'JSON' | 'DATABASE'
export type TargetType = 'JSON' | 'JSON_SCHEMA' | 'XML' | 'XSD' | 'XSD+WSDL'
export type MappingOrigin = 'LLM_DERIVED' | 'EDITED'

export type MappingSuggestion = {
  sourcePath: string
  targetPath: string
  confidence: number
  transformType: 'DIRECT' | 'EXPRESSION' | 'ENUM_MAP' | 'LOOKUP' | 'CONDITIONAL'
  reason: string
  targetArtifactName?: string
  targetArtifactType?: 'XSD' | 'WSDL' | 'JSON_SCHEMA' | 'JSON' | 'XML'
}

export type EditableMapping = MappingSuggestion & {
  id: string
  selected: boolean
  manualOverride?: boolean
  notes?: string
  mappingOrigin?: MappingOrigin
}

export type StudioResponse = {
  conversationId: string
  intent: string
  state: string
  payloadType: 'TEXT' | 'JSON'
  payload: unknown
  contextJson: string
}

export type MappingExportRow = {
  sourcePath: string
  targetPath: string
  transformType: string
  confidence: number
  reason: string
  notes?: string
  mappingOrigin?: MappingOrigin
  selected: boolean
  manualOverride?: boolean
  targetArtifactName?: string
  targetArtifactType?: string
}

export type MappingExportRequest = {
  projectCode: string
  mappingVersion: string
  sourceType: SourceType
  targetType: TargetType
  pathType: 'XML_PATH' | 'JSON_PATH'
  mappings: MappingExportRow[]
}

export type MappingSaveResponse = {
  projectCode: string
  mappingVersion: string
  savedCount: number
  selectedCount: number
  savedAt: string
}

export type MappingConfirmResponse = {
  projectCode: string
  mappingVersion: string
  confirmed: boolean
  selectedCount: number
  confirmedAt: string
}
