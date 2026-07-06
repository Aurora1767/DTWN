import {
  WARNING_THRESHOLDS,
  isFlowExceeded,
  isWaterLevelExceeded,
} from '@/constants/warningThresholds'
import { GATE_PRESSURE_STATIONS } from '@/constants/gatePressureStations'
import type { RiverNetworkForecastResult, ScenarioWarningAlert } from '@/types/platform'
import {
  calcPressureFromWaterLevel,
  hydrostaticPressureUpperLimit,
} from '@/utils/embankmentPressure'

interface ScanContext {
  recordId: number | null
  startedAt: string | null
  dt: number
}

function formatStepTime(startedAt: string | null, dt: number, step: number) {
  const baseMs = startedAt ? new Date(startedAt).getTime() : Date.now()
  const stepDate = new Date(baseMs + step * dt * 1000)
  const year = stepDate.getFullYear()
  const month = String(stepDate.getMonth() + 1).padStart(2, '0')
  const day = String(stepDate.getDate()).padStart(2, '0')
  const hours = String(stepDate.getHours()).padStart(2, '0')
  const minutes = String(stepDate.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

function computeLevel(value: number, threshold: number): 'WARNING' | 'DANGER' {
  const exceedRatio = (value - threshold) / Math.max(threshold, 0.001)
  return exceedRatio > 0.15 ? 'DANGER' : 'WARNING'
}

function buildAlert(
  context: ScanContext,
  sourceKey: string,
  targetName: string,
  metric: string,
  rawValue: number,
  threshold: number,
  timeLabel: string,
): ScenarioWarningAlert {
  const value = metric === '流量' ? Math.abs(rawValue) : rawValue
  return {
    id: `${sourceKey}-${Date.now()}`,
    recordId: context.recordId,
    sourceKey,
    targetName,
    metric,
    value: Number(value.toFixed(2)),
    threshold,
    level: computeLevel(value, threshold),
    status: 'PENDING',
    timeLabel,
    triggeredAt: new Date().toISOString(),
  }
}

function pushWaterLevelAlert(
  alerts: ScenarioWarningAlert[],
  context: ScanContext,
  sourceKey: string,
  targetName: string,
  value: number,
  timeLabel: string,
) {
  if (!isWaterLevelExceeded(value)) {
    return
  }
  alerts.push(
    buildAlert(
      context,
      sourceKey,
      targetName,
      '水位',
      value,
      WARNING_THRESHOLDS.waterLevelUpper,
      timeLabel,
    ),
  )
}

function pushFlowAlert(
  alerts: ScenarioWarningAlert[],
  context: ScanContext,
  sourceKey: string,
  targetName: string,
  value: number,
  timeLabel: string,
) {
  if (!isFlowExceeded(value)) {
    return
  }
  alerts.push(
    buildAlert(
      context,
      sourceKey,
      targetName,
      '流量',
      value,
      WARNING_THRESHOLDS.flowUpper,
      timeLabel,
    ),
  )
}

function pushEmbankmentPressureAlert(
  alerts: ScenarioWarningAlert[],
  context: ScanContext,
  sourceKey: string,
  targetName: string,
  waterLevel: number,
  timeLabel: string,
) {
  if (!isWaterLevelExceeded(waterLevel)) {
    return
  }
  const pressure = calcPressureFromWaterLevel(waterLevel)
  alerts.push(
    buildAlert(
      context,
      sourceKey,
      targetName,
      '堤防静水压力',
      pressure,
      hydrostaticPressureUpperLimit(),
      timeLabel,
    ),
  )
}

function pushGatePressureAlert(
  alerts: ScenarioWarningAlert[],
  context: ScanContext,
  sourceKey: string,
  targetName: string,
  waterLevel: number,
  timeLabel: string,
) {
  if (!isWaterLevelExceeded(waterLevel)) {
    return
  }
  const pressure = calcPressureFromWaterLevel(waterLevel)
  alerts.push(
    buildAlert(
      context,
      sourceKey,
      targetName,
      '水闸静水压力',
      pressure,
      hydrostaticPressureUpperLimit(),
      timeLabel,
    ),
  )
}

export function scanScenarioWarnings(
  result: RiverNetworkForecastResult,
  context: ScanContext,
): ScenarioWarningAlert[] {
  const alerts: ScenarioWarningAlert[] = []
  const recordPrefix = context.recordId ?? 'live'

  for (const reach of result.reaches) {
    const target = `河段${reach.reachId} (${reach.startNode}-${reach.endNode})`
    pushWaterLevelAlert(
      alerts,
      context,
      `${recordPrefix}:reach:${reach.reachId}:avg-level`,
      target,
      reach.avgWaterLevel,
      '河段汇总',
    )
    pushFlowAlert(
      alerts,
      context,
      `${recordPrefix}:reach:${reach.reachId}:inlet-summary`,
      `${target} 入口`,
      reach.inletFlow,
      '河段汇总',
    )
    pushFlowAlert(
      alerts,
      context,
      `${recordPrefix}:reach:${reach.reachId}:outlet-summary`,
      `${target} 出口`,
      reach.outletFlow,
      '河段汇总',
    )
  }

  for (const reach of result.reachHistories ?? []) {
    const target = reach.label
    reach.inletWaterLevels.forEach((value, step) => {
      pushWaterLevelAlert(
        alerts,
        context,
        `${recordPrefix}:history:${reach.reachId}:inlet-level:${step}`,
        `${target} 首端`,
        value,
        formatStepTime(context.startedAt, context.dt, step),
      )
    })
    reach.outletWaterLevels.forEach((value, step) => {
      pushWaterLevelAlert(
        alerts,
        context,
        `${recordPrefix}:history:${reach.reachId}:outlet-level:${step}`,
        `${target} 末端`,
        value,
        formatStepTime(context.startedAt, context.dt, step),
      )
    })
    reach.inletFlows.forEach((value, step) => {
      pushFlowAlert(
        alerts,
        context,
        `${recordPrefix}:history:${reach.reachId}:inlet-flow:${step}`,
        `${target} 首端`,
        value,
        formatStepTime(context.startedAt, context.dt, step),
      )
    })
    reach.outletFlows.forEach((value, step) => {
      pushFlowAlert(
        alerts,
        context,
        `${recordPrefix}:history:${reach.reachId}:outlet-flow:${step}`,
        `${target} 末端`,
        value,
        formatStepTime(context.startedAt, context.dt, step),
      )
    })
  }

  for (const profile of result.reachProfiles ?? []) {
    const startLevel = profile.waterLevels[0]
    const endLevel = profile.waterLevels[profile.waterLevels.length - 1]
    if (startLevel === undefined || endLevel === undefined) {
      continue
    }
    pushEmbankmentPressureAlert(
      alerts,
      context,
      `${recordPrefix}:embankment:${profile.reachId}:start`,
      `${profile.label} 始端堤防`,
      startLevel,
      '河段始端',
    )
    pushEmbankmentPressureAlert(
      alerts,
      context,
      `${recordPrefix}:embankment:${profile.reachId}:end`,
      `${profile.label} 末端堤防`,
      endLevel,
      '河段末端',
    )
  }

  const nodeHistoryMap = new Map(result.nodeHistories.map((history) => [history.nodeId, history]))
  for (const gate of GATE_PRESSURE_STATIONS) {
    const history = nodeHistoryMap.get(gate.nodeId)
    if (!history?.waterLevels.length) {
      continue
    }
    const peakLevel = Math.max(...history.waterLevels)
    const peakStep = history.waterLevels.indexOf(peakLevel)
    pushGatePressureAlert(
      alerts,
      context,
      `${recordPrefix}:gate:${gate.id}:peak`,
      gate.label,
      peakLevel,
      formatStepTime(context.startedAt, context.dt, peakStep),
    )
  }

  return alerts.sort((left, right) => {
    if (left.level !== right.level) {
      return left.level === 'DANGER' ? -1 : 1
    }
    return right.value - left.value
  })
}

export function mergeScenarioWarnings(
  existing: ScenarioWarningAlert[] | null | undefined,
  scanned: ScenarioWarningAlert[],
): ScenarioWarningAlert[] {
  const base = Array.isArray(existing) ? existing : []
  const merged = [...base]
  const indexBySource = new Map(base.map((item, index) => [item.sourceKey, index]))

  for (const alert of scanned) {
    const existingIndex = indexBySource.get(alert.sourceKey)
    if (existingIndex === undefined) {
      merged.unshift(alert)
      indexBySource.set(alert.sourceKey, 0)
      for (const [key, index] of indexBySource.entries()) {
        if (key !== alert.sourceKey) {
          indexBySource.set(key, index + 1)
        }
      }
      continue
    }

    const current = merged[existingIndex]
    if (!current) {
      continue
    }
    if (current.status === 'PROCESSED') {
      continue
    }

    merged[existingIndex] = {
      ...current,
      value: alert.value,
      level: alert.level,
      timeLabel: alert.timeLabel,
      triggeredAt: alert.triggeredAt,
    }
  }

  return merged
}

export function mergeRecordScenarioWarnings(
  existing: ScenarioWarningAlert[] | null | undefined,
  scanned: ScenarioWarningAlert[],
  recordId: number | null,
): ScenarioWarningAlert[] {
  const base = Array.isArray(existing) ? existing : []
  const otherRecords = base.filter((item) => item.recordId !== recordId)
  const handledForRecord = base.filter(
    (item) =>
      item.recordId === recordId && (item.status === 'PROCESSED' || item.status === 'CONFIRMED'),
  )
  return mergeScenarioWarnings([...otherRecords, ...handledForRecord], scanned)
}

export function statusLabel(status: ScenarioWarningAlert['status']) {
  if (status === 'CONFIRMED') {
    return '已确认'
  }
  if (status === 'PROCESSED') {
    return '已处理'
  }
  return '待确认'
}

export function levelLabel(level: ScenarioWarningAlert['level']) {
  return level === 'DANGER' ? '严重' : '预警'
}

export const SCENARIO_ALERT_METRICS = ['水位', '流量', '堤防静水压力', '水闸静水压力'] as const

export type ScenarioAlertMetric = (typeof SCENARIO_ALERT_METRICS)[number]
