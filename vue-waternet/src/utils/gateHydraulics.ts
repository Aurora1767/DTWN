import { GATE_OPENING_STATIONS } from '@/constants/gateOpeningStations'

const G = 9.81

/** Q = mu * b * e * sqrt(2 g H_u), e = openingRatio * H_u */
export function calcGateDischarge(
  waterDepth: number,
  openingPercent: number,
  widths: number[],
): number {
  if (waterDepth <= 1e-6 || openingPercent <= 0) {
    return 0
  }
  const openingRatio = openingPercent / 100
  const e = openingRatio * waterDepth
  const mu = Math.max(0.1, Math.min(0.6, 0.6 - 0.176 * openingRatio))
  const sqrtTerm = Math.sqrt(2 * G * waterDepth)
  return widths.reduce((sum, width) => sum + mu * width * e * sqrtTerm, 0)
}

export function formatGateOpeningLabel(nodeId: number, openingPercent: number) {
  const station = GATE_OPENING_STATIONS.find((item) => item.nodeId === nodeId)
  const label = station?.label ?? `闸门 ${nodeId}`
  return `${label} · ${openingPercent.toFixed(0)}%`
}
