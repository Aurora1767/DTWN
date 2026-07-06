import { WARNING_THRESHOLDS } from '@/constants/warningThresholds'

/** 水的重度 γw，kN/m³ */
export const WATER_UNIT_WEIGHT = 9.81

/** 静水压力 F = 0.5 · γw · h²，单位 kN/m */
export function calcEmbankmentHydrostaticPressure(waterDepth: number): number {
  const depth = Math.max(0, waterDepth)
  return 0.5 * WATER_UNIT_WEIGHT * depth * depth
}

export function calcWaterDepth(waterLevel: number, bedElevation = 0): number {
  return Math.max(0, waterLevel - bedElevation)
}

export function calcPressureFromWaterLevel(waterLevel: number, bedElevation = 0): number {
  return calcEmbankmentHydrostaticPressure(calcWaterDepth(waterLevel, bedElevation))
}

/** 与水位上限 14.88 m 对应的静水压力上限（kN/m） */
export function hydrostaticPressureUpperLimit(bedElevation = 0): number {
  return calcPressureFromWaterLevel(WARNING_THRESHOLDS.waterLevelUpper, bedElevation)
}

export interface ReachEmbankmentPressure {
  startWaterLevel: number
  endWaterLevel: number
  startPressure: number
  endPressure: number
}

export function calcReachEmbankmentPressures(
  waterLevels: number[],
  bedElevation = 0,
): ReachEmbankmentPressure | null {
  const startWaterLevel = waterLevels[0]
  const endWaterLevel = waterLevels[waterLevels.length - 1]
  if (startWaterLevel === undefined || endWaterLevel === undefined) {
    return null
  }

  return {
    startWaterLevel,
    endWaterLevel,
    startPressure: calcPressureFromWaterLevel(startWaterLevel, bedElevation),
    endPressure: calcPressureFromWaterLevel(endWaterLevel, bedElevation),
  }
}
