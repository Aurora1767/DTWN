import { computed, reactive, ref, watch, type Ref } from 'vue'

import {
  SIMULATION_BOUNDARY_STATIONS,
  toDisplayBoundaryValue,
  toModelBoundaryValue,
  type SimulationBoundaryNodeId,
} from '@/constants/simulationBoundaries'
import {
  DEFAULT_GATE_OPENING_PERCENT,
  GATE_OPENING_STATIONS,
  resolveGateOpenings,
} from '@/constants/gateOpeningStations'
import { useScenarioWarningHistory } from '@/composables/useScenarioWarningHistory'
import {
  deleteForecastRecord,
  fetchForecastRecordDetail,
  fetchForecastRecords,
  fetchHydroBoundaryAt,
  runRiverNetworkScenarioRun,
} from '@/services/api'
import type {
  ForecastRecordSummary,
  RiverNetworkForecastResult,
  SimulationBoundarySeries,
} from '@/types/platform'
import {
  buildDefaultControlPoints,
  interpolateBoundarySeries,
  type BoundaryControlPoint,
} from '@/utils/boundaryInterpolation'
import { formatLocalDateTimeInput } from '@/utils/simulationInitialState'
import { acquireSwitchLock, releaseSwitchLock } from '@/utils/switchLock'
import { FetchAbortedError, FetchTimeoutError } from '@/utils/fetchWithTimeout'

export type ScenarioModelRunKind = 'simulation' | 'plan'
type ScenarioRunRecordType = 'simulation' | 'plan'

export interface ScenarioModelRunUi {
  eyebrow: string
  panelTitle: string
  nameLabel: string
  namePlaceholder: string
  startLabel: string
  summaryNote: string
  durationLabel: string
  startButton: string
  runningButton: string
  recordsTitle: string
  recordsEyebrow: string
  recordsDurationLabel: string
  recordsEmptyText: string
  recordsFilterLabel: string
  recordsFilterEmptyText: string
  drawerEyebrow: string
  drawerTitle: string
  drawerAriaLabel: string
  recordType: ScenarioRunRecordType
  wrongRecordError: string
  loadRecordError: string
  deleteRecordError: string
  runError: string
  fallbackError: string
  boundaryFetchError: string
  durationRequiredError: string
  nameRequiredError: string
  recentResultPrefix: string
}

export const SCENARIO_MODEL_RUN_UI: Record<ScenarioModelRunKind, ScenarioModelRunUi> = {
  simulation: {
    eyebrow: 'SIMULATION',
    panelTitle: '预演概要',
    nameLabel: '预演名称',
    namePlaceholder: '请输入本次预演名称',
    startLabel: '预演开始时间',
    summaryNote:
      '与预报使用同一河网模型；默认取开始时刻监测水情并保持恒定，也可编辑三处边界未来变化',
    durationLabel: '预演时长',
    startButton: '开始预演',
    runningButton: '计算中...',
    recordsTitle: '预演记录',
    recordsEyebrow: 'SIMULATION',
    recordsDurationLabel: '预演时长',
    recordsEmptyText: '暂无预演记录',
    recordsFilterLabel: '预演开始时间筛选',
    recordsFilterEmptyText: '当前筛选条件下暂无预演记录',
    drawerEyebrow: 'SIMULATION',
    drawerTitle: '预演提示',
    drawerAriaLabel: '预演提示抽屉',
    recordType: 'simulation',
    wrongRecordError: '该记录不是预演记录',
    loadRecordError: '加载预演记录失败',
    deleteRecordError: '删除预演记录失败',
    runError: '河网预演计算失败，请检查预演参数与后端环境',
    fallbackError: '最近预演结果为占位数据（模型未完整计算），请删除该记录后重新预演',
    boundaryFetchError: '获取预演开始时刻边界数据失败',
    durationRequiredError: '预演时长必须大于 0，请填写天数或小时数',
    nameRequiredError: '请填写预演名称',
    recentResultPrefix: '最近预演',
  },
  plan: {
    eyebrow: 'PLAN',
    panelTitle: '预案概要',
    nameLabel: '预案名称',
    namePlaceholder: '请输入本次预案名称',
    startLabel: '预案开始时间',
    summaryNote:
      '与预演使用同一河网模型与闸门公式；默认取开始时刻监测水情并保持恒定，也可编辑边界与闸门开度',
    durationLabel: '预案时长',
    startButton: '开始预案演算',
    runningButton: '计算中...',
    recordsTitle: '预案记录',
    recordsEyebrow: 'PLAN',
    recordsDurationLabel: '预案时长',
    recordsEmptyText: '暂无预案记录',
    recordsFilterLabel: '预案开始时间筛选',
    recordsFilterEmptyText: '当前筛选条件下暂无预案记录',
    drawerEyebrow: 'PLAN',
    drawerTitle: '预案提示',
    drawerAriaLabel: '预案提示抽屉',
    recordType: 'plan',
    wrongRecordError: '该记录不是预案记录',
    loadRecordError: '加载预案记录失败',
    deleteRecordError: '删除预案记录失败',
    runError: '河网预案演算失败，请检查预案参数与后端环境',
    fallbackError: '最近预案结果为占位数据（模型未完整计算），请删除该记录后重新演算',
    boundaryFetchError: '获取预案开始时刻边界数据失败',
    durationRequiredError: '预案时长必须大于 0，请填写天数或小时数',
    nameRequiredError: '请填写预案名称',
    recentResultPrefix: '最近预案',
  },
}

type ForecastRunStatus = 'idle' | 'running'

interface BoundaryStationState {
  defaultModelValue: number
  customized: boolean
  controlPoints: BoundaryControlPoint[]
}

function createEmptyBoundaryStates(): Record<SimulationBoundaryNodeId, BoundaryStationState> {
  return {
    '1': { defaultModelValue: 0, customized: false, controlPoints: [] },
    '3': { defaultModelValue: 0, customized: false, controlPoints: [] },
    '6': { defaultModelValue: 0, customized: false, controlPoints: [] },
  }
}

function createEmptyGateOpeningInputs(): Record<string, number | ''> {
  return GATE_OPENING_STATIONS.reduce<Record<string, number | ''>>((acc, station) => {
    acc[String(station.nodeId)] = ''
    return acc
  }, {})
}

export function useScenarioModelRun(kind: ScenarioModelRunKind, isActive: Ref<boolean>) {
  const ui = SCENARIO_MODEL_RUN_UI[kind]
  const alertStore = useScenarioWarningHistory(
    kind === 'simulation' ? 'waternet-scenario-simulation-history' : 'waternet-scenario-plan-history',
  )

  const runStatus = ref<ForecastRunStatus>('idle')
  const days = ref(0)
  const extraHours = ref(1)
  const dt = ref(300)
  const result = ref<RiverNetworkForecastResult | null>(null)
  const startedAt = ref<string | null>(null)
  const records = ref<ForecastRecordSummary[]>([])
  const activeRecordId = ref<number | null>(null)
  const error = ref('')
  const name = ref('')
  const startAt = ref(formatLocalDateTimeInput(new Date()))
  const gateOpeningInputs = ref<Record<string, number | ''>>(createEmptyGateOpeningInputs())
  const boundaryDefaultsLoading = ref(false)
  const boundaryDefaultsError = ref('')
  const boundaryStationStates = ref<Record<SimulationBoundaryNodeId, BoundaryStationState>>(
    createEmptyBoundaryStates(),
  )
  const activeBoundaryEditorId = ref<SimulationBoundaryNodeId | null>(null)

  let recordSwitchGeneration = 0
  let recordSwitchAbort: AbortController | null = null
  let runAbort: AbortController | null = null
  let loadRecordsTask: Promise<void> | null = null

  const totalHours = computed(() => {
    const dayCount = Math.max(0, Number(days.value) || 0)
    const hourCount = Math.max(0, Number(extraHours.value) || 0)
    return dayCount * 24 + hourCount
  })

  const stepCount = computed(() => {
    const stepDt = Math.max(1, Number(dt.value) || 300)
    return Math.max(1, Math.round((totalHours.value * 3600) / stepDt))
  })

  const statusLabel = computed(() => (runStatus.value === 'running' ? '计算中' : '空闲'))

  const hasCustomizedBoundary = computed(() =>
    SIMULATION_BOUNDARY_STATIONS.some((station) => boundaryStationStates.value[station.nodeId].customized),
  )

  const activeBoundaryEditorStation = computed(
    () =>
      SIMULATION_BOUNDARY_STATIONS.find((station) => station.nodeId === activeBoundaryEditorId.value) ??
      null,
  )

  const activeBoundaryEditorState = computed(() =>
    activeBoundaryEditorId.value ? boundaryStationStates.value[activeBoundaryEditorId.value] : null,
  )

  function applyGateOpeningsFromSettings(source?: Record<string, number> | null) {
    const resolved = resolveGateOpenings(source)
    gateOpeningInputs.value = GATE_OPENING_STATIONS.reduce<Record<string, number | ''>>((acc, station) => {
      const key = String(station.nodeId)
      const value = resolved[key] ?? DEFAULT_GATE_OPENING_PERCENT
      acc[key] = value === DEFAULT_GATE_OPENING_PERCENT ? '' : value
      return acc
    }, {})
  }

  function buildGateOpeningsPayload() {
    const raw: Record<string, number | ''> = {}
    for (const station of GATE_OPENING_STATIONS) {
      raw[String(station.nodeId)] = gateOpeningInputs.value[String(station.nodeId)] ?? ''
    }
    return resolveGateOpenings(raw as Record<string, number>)
  }

  function buildBoundarySeriesPayload(): SimulationBoundarySeries | undefined {
    if (!hasCustomizedBoundary.value) {
      return undefined
    }

    const count = stepCount.value
    const stepDt = Math.max(1, Number(dt.value) || 300)
    const payload: SimulationBoundarySeries = {}

    for (const station of SIMULATION_BOUNDARY_STATIONS) {
      const state = boundaryStationStates.value[station.nodeId]
      if (!state.customized) {
        continue
      }

      const displayPoints = state.controlPoints.map((point) => ({
        t: point.t,
        value: toDisplayBoundaryValue(station.nodeId, point.value),
      }))
      const series = interpolateBoundarySeries(displayPoints, count, stepDt).map((value) =>
        toModelBoundaryValue(station.nodeId, value),
      )
      payload[station.nodeId] = series
    }

    return Object.keys(payload).length ? payload : undefined
  }

  function buildConstantBoundaryValues(): Record<string, number> {
    return Object.fromEntries(
      SIMULATION_BOUNDARY_STATIONS.map((station) => [
        station.nodeId,
        boundaryStationStates.value[station.nodeId].defaultModelValue,
      ]),
    )
  }

  function boundaryDisplayValue(nodeId: SimulationBoundaryNodeId): string {
    const state = boundaryStationStates.value[nodeId]
    const displayValue = toDisplayBoundaryValue(nodeId, state.defaultModelValue)
    const station = SIMULATION_BOUNDARY_STATIONS.find((item) => item.nodeId === nodeId)
    const decimals = station?.valueKind === 'level' ? 2 : 1
    return `${displayValue.toFixed(decimals)} ${station?.unit ?? ''}`.trim()
  }

  async function loadRecords() {
    if (loadRecordsTask) {
      return loadRecordsTask
    }

    loadRecordsTask = (async () => {
      try {
        records.value = await fetchForecastRecords(ui.recordType)
      } catch {
        records.value = []
      }
    })().finally(() => {
      loadRecordsTask = null
    })

    return loadRecordsTask
  }

  async function ensureActiveRecordLoaded(switching: Ref<boolean>) {
    const targetId =
      activeRecordId.value ?? (records.value.length > 0 ? records.value[0]?.id ?? null : null)
    if (targetId == null) {
      return
    }
    if (activeRecordId.value === targetId && result.value) {
      return
    }
    await switchRecord(targetId, switching)
  }

  function applyDetail(id: number, calculatedAt: string, runResult: RiverNetworkForecastResult) {
    activeRecordId.value = id
    startedAt.value = calculatedAt
    result.value = runResult
  }

  function applySettingsFromDetail(settings: NonNullable<Awaited<ReturnType<typeof fetchForecastRecordDetail>>['settings']>) {
    name.value = settings.simulationName || name.value
    startAt.value = formatLocalDateTimeInput(new Date(settings.simulationStartAt))
    dt.value = settings.dt
    const hours = settings.forecastHours
    days.value = Math.floor(hours / 24)
    extraHours.value = Number((hours - days.value * 24).toFixed(1))
    applyGateOpeningsFromSettings(settings.gateOpenings)
  }

  async function switchRecord(id: number, switching: Ref<boolean>) {
    const generation = ++recordSwitchGeneration
    recordSwitchAbort?.abort()
    const controller = new AbortController()
    recordSwitchAbort = controller

    acquireSwitchLock(switching)
    error.value = ''
    try {
      const detail = await fetchForecastRecordDetail(id, controller.signal)
      if (generation !== recordSwitchGeneration) {
        return
      }
      if (detail.recordType !== ui.recordType) {
        error.value = ui.wrongRecordError
        return
      }
      applyDetail(detail.id, detail.calculatedAt, detail.result)
      if (detail.settings) {
        applySettingsFromDetail(detail.settings)
      }
    } catch (err) {
      if (err instanceof FetchAbortedError) {
        return
      }
      if (generation === recordSwitchGeneration) {
        error.value = err instanceof FetchTimeoutError ? err.message : ui.loadRecordError
      }
    } finally {
      releaseSwitchLock(switching)
    }
  }

  async function removeRecord(id: number, switching: Ref<boolean>) {
    try {
      await deleteForecastRecord(id)
    } catch {
      error.value = ui.deleteRecordError
      return
    }

    await loadRecords()
    if (activeRecordId.value !== id) {
      return
    }
    const nextRecord = records.value[0]
    if (nextRecord) {
      await switchRecord(nextRecord.id, switching)
      return
    }
    activeRecordId.value = null
    result.value = null
    startedAt.value = null
  }

  async function refreshBoundaryDefaults() {
    if (!startAt.value) {
      return
    }

    boundaryDefaultsLoading.value = true
    boundaryDefaultsError.value = ''

    try {
      const snapshot = await fetchHydroBoundaryAt(new Date(startAt.value).toISOString())
      const durationHours = totalHours.value

      boundaryStationStates.value = SIMULATION_BOUNDARY_STATIONS.reduce(
        (accumulator, station) => {
          const modelValue = snapshot.boundaryValues[station.nodeId] ?? 0
          const existing = boundaryStationStates.value[station.nodeId]
          const displayDefault = toDisplayBoundaryValue(station.nodeId, modelValue)
          accumulator[station.nodeId] = {
            defaultModelValue: modelValue,
            customized: existing.customized,
            controlPoints: existing.customized
              ? existing.controlPoints
              : buildDefaultControlPoints(durationHours, displayDefault),
          }
          return accumulator
        },
        {} as Record<SimulationBoundaryNodeId, BoundaryStationState>,
      )
    } catch (fetchError) {
      boundaryDefaultsError.value =
        fetchError instanceof Error ? fetchError.message : ui.boundaryFetchError
    } finally {
      boundaryDefaultsLoading.value = false
    }
  }

  function openBoundaryEditor(nodeId: SimulationBoundaryNodeId) {
    activeBoundaryEditorId.value = nodeId
  }

  function closeBoundaryEditor() {
    activeBoundaryEditorId.value = null
  }

  function saveBoundaryEditor(points: BoundaryControlPoint[], customized: boolean) {
    const nodeId = activeBoundaryEditorId.value
    if (!nodeId) {
      return
    }

    const station = SIMULATION_BOUNDARY_STATIONS.find((item) => item.nodeId === nodeId)
    const modelPoints = points.map((point) => ({
      t: point.t,
      value: station ? toModelBoundaryValue(nodeId, point.value) : point.value,
    }))

    boundaryStationStates.value[nodeId] = {
      ...boundaryStationStates.value[nodeId],
      customized,
      controlPoints: modelPoints,
    }
    activeBoundaryEditorId.value = null
  }

  function resetBoundaryEditor() {
    const nodeId = activeBoundaryEditorId.value
    if (!nodeId) {
      return
    }

    const displayDefault = toDisplayBoundaryValue(
      nodeId,
      boundaryStationStates.value[nodeId].defaultModelValue,
    )
    boundaryStationStates.value[nodeId] = {
      ...boundaryStationStates.value[nodeId],
      customized: false,
      controlPoints: buildDefaultControlPoints(totalHours.value, displayDefault),
    }
  }

  async function startRun() {
    if (runStatus.value === 'running') {
      return
    }

    runAbort?.abort()
    const controller = new AbortController()
    runAbort = controller

    runStatus.value = 'running'
    error.value = ''

    try {
      const dayCount = Math.max(0, Math.floor(Number(days.value) || 0))
      const hourCount = Math.max(0, Number(extraHours.value) || 0)
      const hours = dayCount * 24 + hourCount
      const stepDt = Math.max(1, Number(dt.value) || 300)

      days.value = dayCount
      extraHours.value = hourCount
      dt.value = stepDt

      if (hours <= 0) {
        error.value = ui.durationRequiredError
        return
      }

      const trimmedName = name.value.trim()
      if (!trimmedName) {
        error.value = ui.nameRequiredError
        return
      }

      const startIso = new Date(startAt.value).toISOString()
      const detail = await runRiverNetworkScenarioRun(
        ui.recordType,
        hours,
        startIso,
        stepDt,
        trimmedName,
        {
          boundaryValues: buildConstantBoundaryValues(),
          boundarySeries: buildBoundarySeriesPayload(),
          gateOpenings: buildGateOpeningsPayload(),
        },
        controller.signal,
      )
      await loadRecords()
      applyDetail(detail.id, detail.calculatedAt, detail.result)
      if (detail.settings) {
        applyGateOpeningsFromSettings(detail.settings.gateOpenings)
      }
    } catch (runError) {
      if (runError instanceof FetchAbortedError) {
        return
      }
      error.value = runError instanceof Error ? runError.message : ui.runError
    } finally {
      runStatus.value = 'idle'
    }
  }

  watch(
    () => startAt.value,
    () => {
      if (isActive.value) {
        void refreshBoundaryDefaults()
      }
    },
  )

  watch(
    () => totalHours.value,
    (hours) => {
      if (hours <= 0) {
        return
      }
      for (const station of SIMULATION_BOUNDARY_STATIONS) {
        const state = boundaryStationStates.value[station.nodeId]
        if (state.customized) {
          continue
        }
        const displayDefault = toDisplayBoundaryValue(station.nodeId, state.defaultModelValue)
        boundaryStationStates.value[station.nodeId] = {
          ...state,
          controlPoints: buildDefaultControlPoints(hours, displayDefault),
        }
      }
    },
  )

  watch(
    isActive,
    (active) => {
      if (active) {
        void loadRecords()
        void refreshBoundaryDefaults()
      }
    },
    { immediate: true },
  )

  return Object.assign(
    reactive({
      ui,
      runStatus,
      days,
      extraHours,
      dt,
      result,
      startedAt,
      records,
      activeRecordId,
      error,
      name,
      startAt,
      gateOpeningInputs,
      boundaryDefaultsLoading,
      boundaryDefaultsError,
      boundaryStationStates,
      activeBoundaryEditorId,
      totalHours,
      stepCount,
      statusLabel,
      hasCustomizedBoundary,
      activeBoundaryEditorStation,
      activeBoundaryEditorState,
    }),
    {
      alertStore,
      loadRecords,
      ensureActiveRecordLoaded,
      switchRecord,
      removeRecord,
      startRun,
      refreshBoundaryDefaults,
      openBoundaryEditor,
      closeBoundaryEditor,
      saveBoundaryEditor,
      resetBoundaryEditor,
      boundaryDisplayValue,
    },
  )
}
