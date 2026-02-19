import { MouseEvent, useEffect, useMemo, useRef, useState } from 'react'
import ReactFlow, {
  addEdge,
  Background,
  Connection,
  Controls,
  Edge,
  Handle,
  MarkerType,
  Node,
  NodeProps,
  Position,
  useEdgesState,
  useNodesState,
} from 'reactflow'
import 'reactflow/dist/style.css'
import type { EditableMapping, SourceType, TargetType } from '../types/studio'

type Props = {
  rows: EditableMapping[]
  missingTargets: string[]
  sourceType: SourceType
  targetType: TargetType
  onRowsChange: (rows: EditableMapping[]) => void
}

type NodeMeta = {
  role: 'source' | 'target'
  keyPath: string
  label: string
  pathLabel: string
  missing?: boolean
}

function canonical(path: string): string {
  return (path || '').replace(/\/+/g, '.').replace(/\.+/g, '.').replace(/^\.|\.$/g, '')
}

function toXmlPath(path: string): string {
  if (!path) return '/'
  return '/' + canonical(path).split('.').filter(Boolean).join('/')
}

function toJsonPath(path: string): string {
  if (!path) return '$'
  return '$.' + canonical(path)
}

function leaf(path: string): string {
  const cleaned = canonical(path)
  if (!cleaned) return '-'
  const parts = cleaned.split('.')
  return parts[parts.length - 1] || cleaned
}

function renamePath(path: string, nextLeaf: string): string {
  const cleaned = canonical(path)
  if (!cleaned) return nextLeaf.trim()
  const parts = cleaned.split('.').filter(Boolean)
  parts[parts.length - 1] = nextLeaf.trim() || parts[parts.length - 1]
  return parts.join('.')
}

function FlowNodeCard({ data, selected }: NodeProps<NodeMeta>) {
  const tone = data.role === 'source' ? 'maroon' : data.missing ? 'maroon' : 'green'
  return (
    <div className={`ms-rf-node ms-rf-node-${tone} ${selected ? 'ms-rf-node-active' : ''}`}>
      <Handle type="target" position={Position.Left} className="ms-rf-handle" />
      <div className="ms-rf-node-head">
        <span className="ms-rf-node-badge">{data.role === 'source' ? 'S' : 'T'}</span>
        <span className="ms-rf-node-title">{data.label}</span>
      </div>
      <div className="ms-rf-chip-row">
        <span className={`ms-rf-chip ${tone}`}>{data.pathLabel}</span>
      </div>
      <Handle type="source" position={Position.Right} className="ms-rf-handle" />
    </div>
  )
}

export default function FlowCanvas({ rows, missingTargets, sourceType, targetType, onRowsChange }: Props) {
  const sourceXmlMode = sourceType === 'XML'
  const targetJsonMode = targetType === 'JSON_SCHEMA' || targetType === 'JSON'
  const nodeTypes = useMemo(() => ({ mappingNode: FlowNodeCard }), [])
  const shellRef = useRef<HTMLDivElement | null>(null)
  const [selectedNode, setSelectedNode] = useState<Node<NodeMeta> | null>(null)
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [orphanSources, setOrphanSources] = useState<string[]>([])
  const [orphanTargets, setOrphanTargets] = useState<string[]>([])
  const [editValue, setEditValue] = useState('')
  const [isFullscreen, setIsFullscreen] = useState(false)

  const [nodes, setNodes, onNodesChange] = useNodesState<NodeMeta>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const nodeStructureKey = useMemo(
    () => JSON.stringify(rows.map((r) => [canonical(r.sourcePath), canonical(r.targetPath)])),
    [rows]
  )
  const edgeStructureKey = useMemo(
    () =>
      JSON.stringify(
        rows.map((r) => [
          canonical(r.sourcePath),
          canonical(r.targetPath),
          r.selected !== false,
          Number.isFinite(r.confidence) ? Number(r.confidence) : 0,
          r.transformType,
        ])
      ),
    [rows]
  )

  useEffect(() => {
    const sourceUnique = Array.from(new Set(
      rows
        .map((r) => canonical(r.sourcePath))
        .filter(Boolean)
        .concat(orphanSources.map(canonical))
        .filter(Boolean)
    ))
    const targetUnique = Array.from(new Set(
      rows
        .map((r) => canonical(r.targetPath))
        .filter(Boolean)
        .concat(missingTargets.map(canonical))
        .concat(orphanTargets.map(canonical))
        .filter(Boolean)
    ))

    const prevPos = new Map(nodes.map((n) => [n.id, n.position]))

    const nextNodes: Node<NodeMeta>[] = [
      ...sourceUnique.map((src, i) => ({
        id: `s:${src}`,
        type: 'mappingNode',
        position: prevPos.get(`s:${src}`) ?? { x: 40, y: 56 + i * 104 },
        data: {
          role: 'source',
          keyPath: src,
          label: leaf(src),
          pathLabel: sourceXmlMode ? toXmlPath(src) : toJsonPath(src),
        },
        style: { width: 300 },
      })),
      ...targetUnique.map((tgt, i) => ({
        id: `t:${tgt}`,
        type: 'mappingNode',
        position: prevPos.get(`t:${tgt}`) ?? { x: 500, y: 56 + i * 104 },
        data: {
          role: 'target',
          keyPath: tgt,
          label: leaf(tgt),
          pathLabel: targetJsonMode ? toJsonPath(tgt) : toXmlPath(tgt),
          missing: missingTargets.map(canonical).includes(tgt),
        },
        style: { width: 300 },
      })),
    ]

    const nextEdges: Edge[] = rows
      .filter((r) => r.selected !== false)
      .filter((r) => canonical(r.sourcePath) && canonical(r.targetPath))
      .map((r, idx) => {
        const edgeId = `e|${r.id || idx}`
        const selected = selectedEdgeId === edgeId
        return {
          id: edgeId,
          source: `s:${canonical(r.sourcePath)}`,
          target: `t:${canonical(r.targetPath)}`,
          markerEnd: { type: MarkerType.ArrowClosed, width: 18, height: 18 },
          type: 'smoothstep',
          animated: !selected,
          className: selected ? 'ms-rf-edge is-selected' : 'ms-rf-edge',
          label: `${Math.round((r.confidence || 0) * 100)}% ${r.transformType}`,
          style: selected
            ? { stroke: '#2563eb', strokeWidth: 2.6, filter: 'drop-shadow(0 0 6px rgba(37,99,235,0.45))' }
            : { stroke: '#8b1538', strokeWidth: 2.1 },
          labelStyle: { fill: '#ffffff', fontSize: 10, fontWeight: 800 },
          labelBgPadding: [7, 3],
          labelBgBorderRadius: 8,
          labelBgStyle: selected
            ? { fill: '#2563eb', stroke: '#1d4ed8' }
            : { fill: '#8b1538', stroke: '#9f1239' },
        } as Edge
      })

    setNodes(nextNodes)
    setEdges(nextEdges)
  }, [
    nodeStructureKey,
    edgeStructureKey,
    missingTargets,
    orphanSources,
    orphanTargets,
    sourceXmlMode,
    targetJsonMode,
    selectedEdgeId,
    setNodes,
    setEdges,
  ])

  useEffect(() => {
    if (!selectedNode) {
      setEditValue('')
      return
    }
    setEditValue(selectedNode.data.label || '')
  }, [selectedNode])

  const onConnect = (connection: Connection) => {
    if (!connection.source || !connection.target) return
    const sourcePath = connection.source.replace(/^s:/, '')
    const targetPath = connection.target.replace(/^t:/, '')

    const next = [...rows]
    const exactIndex = next.findIndex(
      (r) => canonical(r.sourcePath) === sourcePath && canonical(r.targetPath) === targetPath
    )
    if (exactIndex >= 0) {
      next[exactIndex] = {
        ...next[exactIndex],
        selected: true,
        manualOverride: true,
        mappingOrigin: 'EDITED',
        reason: 'Manually connected in flow',
      }
    } else {
      next.push({
        id: `manual-flow-${Date.now()}`,
        sourcePath,
        targetPath,
        transformType: 'DIRECT',
        confidence: 1,
        reason: 'Manually connected in flow',
        selected: true,
        manualOverride: true,
        mappingOrigin: 'EDITED',
      })
    }
    onRowsChange(next)

    setEdges((eds) => addEdge({ ...connection, type: 'smoothstep' }, eds))
  }

  const disconnectEdge = (edgeId: string) => {
    const edge = edges.find((e) => e.id === edgeId)
    if (!edge?.source || !edge?.target) return
    const sourcePath = canonical(edge.source.replace(/^s:/, ''))
    const targetPath = canonical(edge.target.replace(/^t:/, ''))
    setEdges((eds) => eds.filter((e) => e.id !== edgeId))
    setOrphanTargets((prev) => Array.from(new Set([...prev, targetPath])))
    onRowsChange(rows.map((row) => {
      const isMatch = canonical(row.sourcePath) === sourcePath && canonical(row.targetPath) === targetPath
      return isMatch ? { ...row, selected: false, manualOverride: true, mappingOrigin: 'EDITED', notes: row.notes || 'Disconnected in flow' } : row
    }))
  }

  const onEdgeDoubleClick = (_: MouseEvent, edge: Edge) => {
    disconnectEdge(edge.id)
    setSelectedEdgeId(null)
  }

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Backspace' && event.key !== 'Delete') return
      const target = event.target as HTMLElement | null
      const tag = target?.tagName?.toLowerCase()
      const editable = target?.getAttribute('contenteditable')
      if (tag === 'input' || tag === 'textarea' || editable === 'true') return

      if (selectedEdgeId) {
        event.preventDefault()
        disconnectEdge(selectedEdgeId)
        setSelectedEdgeId(null)
        return
      }
      if (!selectedNode || selectedNode.data.role !== 'source') return
      const sourceKey = selectedNode.data.keyPath
      const hasActiveEdges = rows.some((row) => row.selected !== false && canonical(row.sourcePath) === sourceKey && canonical(row.targetPath))
      if (!hasActiveEdges) {
        event.preventDefault()
        setOrphanSources((prev) => prev.filter((path) => canonical(path) !== sourceKey))
        onRowsChange(rows.filter((row) => canonical(row.sourcePath) !== sourceKey))
        setSelectedNode(null)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [rows, selectedNode, selectedEdgeId, onRowsChange])

  useEffect(() => {
    if (!selectedEdgeId) return
    const exists = edges.some((edge) => edge.id === selectedEdgeId)
    if (!exists) setSelectedEdgeId(null)
  }, [edges, selectedEdgeId])

  useEffect(() => {
    if (!selectedNode) return
    const exists = nodes.some((node) => node.id === selectedNode.id)
    if (!exists) setSelectedNode(null)
  }, [nodes, selectedNode])

  useEffect(() => {
    const activeTargets = new Set(rows.map((row) => canonical(row.targetPath)).filter(Boolean))
    setOrphanTargets((prev) => prev.filter((path) => !activeTargets.has(canonical(path))))
  }, [rows])

  useEffect(() => {
    const activeSources = new Set(rows.map((row) => canonical(row.sourcePath)).filter((path, idx, arr) => {
      if (!path) return false
      return rows.some((row) => row.selected !== false && canonical(row.sourcePath) === path && canonical(row.targetPath))
    }))
    setOrphanSources((prev) => prev.filter((path) => !activeSources.has(canonical(path))))
  }, [rows])

  useEffect(() => {
    const onFullscreenChange = () => {
      setIsFullscreen(document.fullscreenElement === shellRef.current)
    }
    document.addEventListener('fullscreenchange', onFullscreenChange)
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange)
  }, [])

  const toggleFullscreen = async () => {
    if (!shellRef.current) return
    if (document.fullscreenElement === shellRef.current) {
      await document.exitFullscreen()
      return
    }
    await shellRef.current.requestFullscreen()
  }

  const addSourceNode = () => {
    const existing = new Set(
      rows.map((row) => canonical(row.sourcePath))
        .concat(orphanSources.map(canonical))
        .filter(Boolean)
    )
    let idx = 1
    let candidate = `source.newField${idx}`
    while (existing.has(candidate)) {
      idx += 1
      candidate = `source.newField${idx}`
    }
    setOrphanSources((prev) => Array.from(new Set([...prev, candidate])))
  }

  const onEdgeClick = (_: MouseEvent, edge: Edge) => {
    setSelectedNode(null)
    setSelectedEdgeId(edge.id)
  }

  const selected = selectedNode ?? (nodes.length > 0 ? (nodes[0] as Node<NodeMeta>) : null)
  const selectedTone = selected?.data?.role === 'source' ? 'source' : selected?.data?.missing ? 'source' : 'target'

  const applyNodeEdit = () => {
    if (!selected || !editValue.trim()) return
    const nextLeaf = editValue.trim()
    if (selected.data.role === 'source') {
      const oldPath = selected.data.keyPath
      const newPath = renamePath(oldPath, nextLeaf)
      onRowsChange(rows.map((r) => (
        canonical(r.sourcePath) === oldPath
          ? { ...r, sourcePath: newPath, manualOverride: true, mappingOrigin: 'EDITED' }
          : r
      )))
    } else {
      const oldPath = selected.data.keyPath
      const newPath = renamePath(oldPath, nextLeaf)
      onRowsChange(rows.map((r) => (
        canonical(r.targetPath) === oldPath
          ? { ...r, targetPath: newPath, manualOverride: true, mappingOrigin: 'EDITED' }
          : r
      )))
    }
  }

  return (
    <div className="flow-shell" ref={shellRef}>
      <div className="flow-canvas-panel">
        <div className="flow-top-tools">
          <button
            type="button"
            className="flow-fullscreen-btn flow-add-source-btn"
            onClick={addSourceNode}
            title="Add Source"
            aria-label="Add Source"
          >
            <span className="flow-add-source-text">S+</span>
          </button>
          <button
            type="button"
            className="flow-fullscreen-btn"
            onClick={() => void toggleFullscreen()}
            title={isFullscreen ? 'Exit full screen' : 'Full screen'}
            aria-label={isFullscreen ? 'Exit full screen' : 'Full screen'}
          >
            {isFullscreen ? (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M9 4H4V9" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M15 4H20V9" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M9 20H4V15" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M15 20H20V15" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M10 10L7.5 7.5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
                <path d="M14 10L16.5 7.5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
                <path d="M10 14L7.5 16.5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
                <path d="M14 14L16.5 16.5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
              </svg>
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M9 4H4V9" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M15 4H20V9" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M9 20H4V15" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M15 20H20V15" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                <path d="M8 8L11 5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
                <path d="M16 8L13 5" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
                <path d="M8 16L11 19" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
                <path d="M16 16L13 19" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
              </svg>
            )}
          </button>
        </div>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          fitView
          fitViewOptions={{ padding: 0.16, maxZoom: 1.06 }}
          onNodeClick={(_, node) => {
            setSelectedEdgeId(null)
            setSelectedNode(node as Node<NodeMeta>)
          }}
          onPaneClick={() => {
            setSelectedNode(null)
            setSelectedEdgeId(null)
          }}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onEdgeClick={onEdgeClick}
          onConnect={onConnect}
          onEdgeDoubleClick={onEdgeDoubleClick}
          proOptions={{ hideAttribution: true }}
        >
          <Background variant="dots" color="#cbd5e1" gap={18} size={1.15} />
          <Controls showInteractive />
        </ReactFlow>
      </div>
      <aside className={`flow-detail-panel ${selectedTone}`}>
        <div className="flow-detail-title">Node Detail</div>
        {selected ? (
          <div className="flow-detail-card">
            <div className="flow-node-bubble-title">
              {selected.data.role === 'source' ? 'Source Node' : 'Target Node'}
            </div>
            <div className="flow-bubble-meta-row">
              <b>Path</b>
              <span className="flow-bubble-inline-chip">{selected.data.pathLabel}</span>
            </div>
            <div className="flow-node-edit-row">
              <input
                className="ce-mapping-input"
                value={editValue}
                onChange={(e) => setEditValue(e.target.value)}
                placeholder="Rename field"
              />
              <button type="button" className="ce-table-action accent" onClick={applyNodeEdit}>Apply</button>
            </div>
            <div className="flow-detail-note">Connect source -&gt; target to manually correct mappings. Select node/edge and press Backspace/Delete to remove.</div>
          </div>
        ) : (
          <div className="flow-detail-empty">Click a node to inspect or edit details.</div>
        )}
      </aside>
    </div>
  )
}
