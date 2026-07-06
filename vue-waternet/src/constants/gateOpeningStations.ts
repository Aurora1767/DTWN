export interface GateOpeningStation {
  id: string
  label: string
  nodeId: number
  widths: number[]
  widthLabel: string
}

/** 7 个节点、8 孔闸门；28 号双孔开度同步 */
export const GATE_OPENING_STATIONS: GateOpeningStation[] = [
  { id: '21', label: '闸门 21', nodeId: 21, widths: [22.0], widthLabel: '22.00 m' },
  {
    id: '28',
    label: '闸门 28（双孔）',
    nodeId: 28,
    widths: [13.6, 40.46],
    widthLabel: '13.6 + 40.46 m',
  },
  { id: '29', label: '闸门 29', nodeId: 29, widths: [20.64], widthLabel: '20.64 m' },
  { id: '32', label: '闸门 32', nodeId: 32, widths: [22.35], widthLabel: '22.35 m' },
  { id: '34', label: '闸门 34', nodeId: 34, widths: [30.36], widthLabel: '30.36 m' },
  { id: '40', label: '闸门 40', nodeId: 40, widths: [25.22], widthLabel: '25.22 m' },
  { id: '47', label: '闸门 47', nodeId: 47, widths: [23.22], widthLabel: '23.22 m' },
]

export const DEFAULT_GATE_OPENING_PERCENT = 100

export function normalizeGateOpeningPercent(value: unknown): number {
  if (value === null || value === undefined || value === '') {
    return DEFAULT_GATE_OPENING_PERCENT
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return DEFAULT_GATE_OPENING_PERCENT
  }
  return Math.max(0, Math.min(100, parsed))
}

export function buildDefaultGateOpenings(): Record<string, number> {
  return GATE_OPENING_STATIONS.reduce<Record<string, number>>((acc, station) => {
    acc[String(station.nodeId)] = DEFAULT_GATE_OPENING_PERCENT
    return acc
  }, {})
}

export function resolveGateOpenings(
  source?: Record<string, number> | null,
): Record<string, number> {
  const resolved = buildDefaultGateOpenings()
  if (!source) {
    return resolved
  }
  for (const station of GATE_OPENING_STATIONS) {
    const key = String(station.nodeId)
    if (source[key] !== undefined) {
      resolved[key] = normalizeGateOpeningPercent(source[key])
    }
  }
  return resolved
}
