export type SimulationBoundaryNodeId = '1' | '3' | '6'

export interface SimulationBoundaryDefinition {
  nodeId: SimulationBoundaryNodeId
  label: string
  shortLabel: string
  valueKind: 'level' | 'flow'
  unit: string
  /** Display positive flow magnitude in UI while model keeps signed value. */
  displayAbsValue?: boolean
}

export const SIMULATION_BOUNDARY_STATIONS: SimulationBoundaryDefinition[] = [
  {
    nodeId: '1',
    label: '太湖边界（节点 1）',
    shortLabel: '太湖',
    valueKind: 'level',
    unit: 'm',
  },
  {
    nodeId: '3',
    label: '京杭运河南（节点 3）',
    shortLabel: '运河南',
    valueKind: 'level',
    unit: 'm',
  },
  {
    nodeId: '6',
    label: '京杭运河北（节点 6）',
    shortLabel: '运河北',
    valueKind: 'flow',
    unit: 'm³/s',
    displayAbsValue: true,
  },
]

export function toDisplayBoundaryValue(
  nodeId: SimulationBoundaryNodeId,
  modelValue: number,
): number {
  const definition = SIMULATION_BOUNDARY_STATIONS.find((item) => item.nodeId === nodeId)
  if (definition?.displayAbsValue) {
    return Math.abs(modelValue)
  }
  return modelValue
}

export function toModelBoundaryValue(
  nodeId: SimulationBoundaryNodeId,
  displayValue: number,
): number {
  const definition = SIMULATION_BOUNDARY_STATIONS.find((item) => item.nodeId === nodeId)
  if (definition?.displayAbsValue) {
    return -Math.abs(displayValue)
  }
  return displayValue
}
