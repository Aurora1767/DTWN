export const WARNING_THRESHOLDS = {
  waterLevelUpper: 14.88,
  flowUpper: 143.08,
} as const

export function isWaterLevelExceeded(value: number) {
  return value > WARNING_THRESHOLDS.waterLevelUpper
}

export function isFlowExceeded(value: number) {
  return Math.abs(value) > WARNING_THRESHOLDS.flowUpper
}
