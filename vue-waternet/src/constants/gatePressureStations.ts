export interface GatePressureStation {
  id: string
  label: string
  nodeId: number
}

/** 8 个水闸：节点 21、28（2 个）、29、32、34、40、47 */
export const GATE_PRESSURE_STATIONS: GatePressureStation[] = [
  { id: 'gate-21', label: '水闸 21', nodeId: 21 },
  { id: 'gate-28-1', label: '水闸 28-1', nodeId: 28 },
  { id: 'gate-28-2', label: '水闸 28-2', nodeId: 28 },
  { id: 'gate-29', label: '水闸 29', nodeId: 29 },
  { id: 'gate-32', label: '水闸 32', nodeId: 32 },
  { id: 'gate-34', label: '水闸 34', nodeId: 34 },
  { id: 'gate-40', label: '水闸 40', nodeId: 40 },
  { id: 'gate-47', label: '水闸 47', nodeId: 47 },
]
