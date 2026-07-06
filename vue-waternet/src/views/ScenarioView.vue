<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, unref, watch } from 'vue'

import BoundaryConditionEditorModal from '@/components/BoundaryConditionEditorModal.vue'
import ForecastRecordTable from '@/components/ForecastRecordTable.vue'
import MainMapStage from '@/components/MainMapStage.vue'
import PanelShell from '@/components/PanelShell.vue'
import RiverNetworkNodeProcessPanel from '@/components/RiverNetworkNodeProcessPanel.vue'
import RiverNetworkReachResultsPanel from '@/components/RiverNetworkReachResultsPanel.vue'
import ScenarioModelRunPanel from '@/components/ScenarioModelRunPanel.vue'
import ScenarioWarningDrawer from '@/components/ScenarioWarningDrawer.vue'
import { SCENARIO_MODEL_RUN_UI, useScenarioModelRun } from '@/composables/useScenarioModelRun'
import { useScenarioWarningHistory } from '@/composables/useScenarioWarningHistory'
import { toDisplayBoundaryValue } from '@/constants/simulationBoundaries'
import {
  deleteForecastRecord,
  fetchForecastRecordDetail,
  fetchForecastRecords,
  runRiverNetworkForecast,
} from '@/services/api'
import { usePlatformStore } from '@/stores/platform'
import type { ForecastRecordSummary, RiverNetworkForecastResult, ScenarioWarningAlert } from '@/types/platform'
import { mergeRecordScenarioWarnings, scanScenarioWarnings } from '@/utils/scenarioWarningScanner'
import { acquireSwitchLock, releaseSwitchLock } from '@/utils/switchLock'
import { FetchAbortedError, FetchTimeoutError } from '@/utils/fetchWithTimeout'

const store = usePlatformStore()
const ANALYSIS_ALERTS_KEY = 'waternet-scenario-analysis-alerts'
const LEGACY_ALERT_KEYS = [
  'waternet-scenario-warning-history',
  'waternet-scenario-simulation-history',
  'waternet-scenario-plan-history',
] as const
const analysisAlertHistory = useScenarioWarningHistory(ANALYSIS_ALERTS_KEY)

type PrecautionStep = 'forecast' | 'warning' | 'simulation' | 'plan'
type ForecastRunStatus = 'idle' | 'running'
type AnalysisStep = 'warning' | 'simulation' | 'plan'

const activeStep = ref<PrecautionStep>('forecast')
const forecastActive = computed(() => activeStep.value === 'forecast')
const warningActive = computed(() => activeStep.value === 'warning')
const simulationActive = computed(() => activeStep.value === 'simulation')
const planActive = computed(() => activeStep.value === 'plan')
const scenarioModelActive = computed(() => simulationActive.value || planActive.value)
const forecastSummaryPanelVisible = computed(
  () => forecastActive.value || warningActive.value,
)
const forecastRecordsPanelVisible = computed(() => forecastActive.value || warningActive.value)
const analysisActive = computed(() => warningActive.value || scenarioModelActive.value)
const embankmentPressureActive = computed(
  () => warningActive.value || scenarioModelActive.value,
)

const simulationRun = useScenarioModelRun('simulation', computed(() => simulationActive.value || planActive.value))
const scenarioRecordsPanelVisible = computed(() => simulationActive.value || planActive.value)
const scenarioRecordsUi = computed(() =>
  planActive.value ? SCENARIO_MODEL_RUN_UI.plan : SCENARIO_MODEL_RUN_UI.simulation,
)

const activeModelRun = computed(() => (simulationActive.value ? simulationRun : null))

const activeAnalysisStep = computed<AnalysisStep>(() => {
  if (simulationActive.value) {
    return 'simulation'
  }
  if (planActive.value) {
    return 'plan'
  }
  return 'warning'
})

const activeAlertStore = computed(() => analysisAlertHistory)

const activeAlerts = computed(() => {
  const items = unref(activeAlertStore.value.alerts)
  return Array.isArray(items) ? items : []
})

const drawerMeta = computed(() => {
  if (simulationActive.value) {
    return {
      eyebrow: simulationRun.ui.drawerEyebrow,
      title: simulationRun.ui.drawerTitle,
      ariaLabel: simulationRun.ui.drawerAriaLabel,
    }
  }
  if (planActive.value) {
    const ui = SCENARIO_MODEL_RUN_UI.plan
    return {
      eyebrow: ui.drawerEyebrow,
      title: ui.drawerTitle,
      ariaLabel: ui.drawerAriaLabel,
    }
  }
  return {
    eyebrow: 'WARNING',
    title: '预警提示',
    ariaLabel: '预警提示抽屉',
  }
})

const leftCollapsed = ref(false)
const rightCollapsed = ref(false)
const forecastRunStatus = ref<ForecastRunStatus>('idle')
const forecastDays = ref(0)
const forecastExtraHours = ref(1)
const forecastResult = ref<RiverNetworkForecastResult | null>(null)
const forecastStartedAt = ref<string | null>(null)
const forecastRecords = ref<ForecastRecordSummary[]>([])
const activeForecastRecordId = ref<number | null>(null)
const forecastSwitching = ref(false)
const forecastSwitchingId = ref<number | null>(null)
const scenarioSwitching = ref(false)
const scenarioSwitchingId = ref<number | null>(null)
const forecastError = ref('')
const analysisScanning = ref(false)
const analysisDrawerExpanded = ref(true)

let forecastSwitchGeneration = 0
let forecastSwitchAbort: AbortController | null = null
let forecastRunAbort: AbortController | null = null
let loadForecastRecordsTask: Promise<void> | null = null
let restoreScenarioRecordsTask: Promise<void> | null = null

const totalForecastHours = computed(() => {
  const days = Math.max(0, Number(forecastDays.value) || 0)
  const hours = Math.max(0, Number(forecastExtraHours.value) || 0)
  return days * 24 + hours
})

const forecastStatusLabel = computed(() =>
  forecastRunStatus.value === 'running' ? '计算中' : '空闲',
)

const rightPanelResult = computed(() => {
  if (scenarioModelActive.value) {
    return simulationRun.result
  }
  return forecastResult.value
})

const rightPanelStartedAt = computed(() => {
  if (scenarioModelActive.value) {
    return simulationRun.startedAt
  }
  return forecastStartedAt.value
})

const activeRecordId = computed(() => {
  if (scenarioModelActive.value) {
    return simulationRun.activeRecordId
  }
  return activeForecastRecordId.value
})

const precautionSteps: { key: PrecautionStep; label: string }[] = [
  { key: 'forecast', label: '预报' },
  { key: 'warning', label: '预警' },
  { key: 'simulation', label: '预演' },
  { key: 'plan', label: '预案' },
]

onMounted(() => {
  migrateLegacyAnalysisAlerts()
  void store.loadDashboard()
  void loadForecastRecords()
  store.startEnvironmentPolling(60_000)
  store.startWaterQuantityPolling(300_000)
  void notifyLayoutChanged()
})

function migrateLegacyAnalysisAlerts() {
  const migratedFlag = `${ANALYSIS_ALERTS_KEY}:legacy-imported`
  if (localStorage.getItem(migratedFlag)) {
    return
  }

  const mergedBySource = new Map<string, ScenarioWarningAlert>()
  for (const item of unref(analysisAlertHistory.alerts)) {
    mergedBySource.set(item.sourceKey, item)
  }

  for (const key of LEGACY_ALERT_KEYS) {
    try {
      const raw = localStorage.getItem(key)
      if (!raw) {
        continue
      }
      const parsed = JSON.parse(raw) as ScenarioWarningAlert[]
      if (!Array.isArray(parsed)) {
        continue
      }
      for (const item of parsed) {
        if (item.status !== 'PROCESSED' && item.status !== 'CONFIRMED') {
          continue
        }
        const existing = mergedBySource.get(item.sourceKey)
        if (!existing || existing.status === 'PENDING') {
          mergedBySource.set(item.sourceKey, item)
        }
      }
    } catch {
      // ignore invalid legacy payload
    }
  }

  if (mergedBySource.size > unref(analysisAlertHistory.alerts).length) {
    analysisAlertHistory.replaceAlerts(Array.from(mergedBySource.values()))
  }

  localStorage.setItem(migratedFlag, new Date().toISOString())
}

onUnmounted(() => {
  forecastSwitchAbort?.abort()
  forecastRunAbort?.abort()
  store.stopEnvironmentPolling()
  store.stopWaterQuantityPolling()
})

function switchPrecautionStep(step: PrecautionStep) {
  activeStep.value = step
  void notifyLayoutChanged()
}

async function notifyLayoutChanged() {
  await nextTick()
  window.dispatchEvent(new Event('resize'))
}

async function toggleLeftPanel() {
  leftCollapsed.value = !leftCollapsed.value
  await notifyLayoutChanged()
}

async function toggleRightPanel() {
  rightCollapsed.value = !rightCollapsed.value
  await notifyLayoutChanged()
}

async function loadForecastRecords() {
  if (loadForecastRecordsTask) {
    return loadForecastRecordsTask
  }

  loadForecastRecordsTask = (async () => {
    try {
      const records = await fetchForecastRecords('forecast')
      forecastRecords.value = records.filter((record) => record.recordType === 'forecast')
      if (
        (forecastActive.value || warningActive.value) &&
        forecastRecords.value.length > 0 &&
        activeForecastRecordId.value == null &&
        !forecastSwitching.value
      ) {
        const firstRecord = forecastRecords.value[0]
        if (firstRecord) {
          await switchForecastRecord(firstRecord.id)
        }
      }
    } catch {
      forecastRecords.value = []
    }
  })().finally(() => {
    loadForecastRecordsTask = null
  })

  return loadForecastRecordsTask
}

function applyForecastDetail(
  id: number,
  calculatedAt: string,
  result: RiverNetworkForecastResult,
) {
  activeForecastRecordId.value = id
  forecastStartedAt.value = calculatedAt
  forecastResult.value = result
}

async function switchForecastRecord(id: number) {
  if (activeForecastRecordId.value === id) {
    return
  }

  forecastSwitchAbort?.abort()
  const generation = ++forecastSwitchGeneration
  const controller = new AbortController()
  forecastSwitchAbort = controller

  acquireSwitchLock(forecastSwitching)
  forecastSwitchingId.value = id
  forecastError.value = ''
  try {
    const detail = await fetchForecastRecordDetail(id, controller.signal)
    if (generation !== forecastSwitchGeneration) {
      return
    }
    if (detail.recordType !== 'forecast') {
      forecastError.value = '该记录不是预报记录'
      return
    }
    applyForecastDetail(detail.id, detail.calculatedAt, detail.result)
    scanAnalysisAlerts()
  } catch (error) {
    if (error instanceof FetchAbortedError) {
      return
    }
    if (generation !== forecastSwitchGeneration) {
      return
    }
    forecastError.value =
      error instanceof FetchTimeoutError ? error.message : '加载预报记录失败'
  } finally {
    if (generation === forecastSwitchGeneration) {
      forecastSwitchingId.value = null
    }
    releaseSwitchLock(forecastSwitching)
  }
}

async function removeForecastRecord(id: number) {
  try {
    await deleteForecastRecord(id)
  } catch {
    forecastError.value = '删除预报记录失败'
    return
  }

  await loadForecastRecords()
  if (activeForecastRecordId.value !== id) {
    return
  }
  const nextRecord = forecastRecords.value[0]
  if (nextRecord) {
    await switchForecastRecord(nextRecord.id)
    return
  }
  activeForecastRecordId.value = null
  forecastResult.value = null
  forecastStartedAt.value = null
}

function scanAnalysisAlerts() {
  if (!analysisActive.value) {
    return
  }
  const result = rightPanelResult.value
  if (!result) {
    return
  }
  analysisScanning.value = true
  try {
    const scanned = scanScenarioWarnings(result, {
      recordId: activeRecordId.value,
      startedAt: rightPanelStartedAt.value,
      dt: result.dt,
    })
    const storeRef = activeAlertStore.value
    const existingAlerts = unref(storeRef.alerts)
    storeRef.replaceAlerts(
      mergeRecordScenarioWarnings(
        Array.isArray(existingAlerts) ? existingAlerts : [],
        scanned,
        activeRecordId.value,
      ),
    )
  } catch (error) {
    console.warn('[waternet] scan analysis alerts failed', error)
  } finally {
    analysisScanning.value = false
  }
}

function toggleAnalysisDrawer() {
  analysisDrawerExpanded.value = !analysisDrawerExpanded.value
}

function confirmAnalysisAlert(id: string) {
  activeAlertStore.value.updateStatus(id, 'CONFIRMED')
}

function processAnalysisAlert(id: string) {
  activeAlertStore.value.updateStatus(id, 'PROCESSED')
}

function clearProcessedAnalysisAlerts() {
  activeAlertStore.value.clearProcessed(activeRecordId.value)
}

watch(
  () => activeStep.value,
  (step) => {
    if (step === 'warning' || step === 'simulation' || step === 'plan') {
      analysisDrawerExpanded.value = true
      if (rightPanelResult.value) {
        scanAnalysisAlerts()
      }
    }
    if (step === 'simulation' || step === 'plan') {
      void restoreScenarioRunRecords()
    }
    if (step === 'forecast' || step === 'warning') {
      void loadForecastRecords()
    }
  },
  { immediate: true },
)

async function restoreScenarioRunRecords() {
  if (restoreScenarioRecordsTask) {
    return restoreScenarioRecordsTask
  }

  restoreScenarioRecordsTask = (async () => {
    await simulationRun.loadRecords()
    await simulationRun.ensureActiveRecordLoaded(scenarioSwitching)
    if (simulationRun.result && analysisActive.value) {
      scanAnalysisAlerts()
    }
  })().finally(() => {
    restoreScenarioRecordsTask = null
  })

  return restoreScenarioRecordsTask
}

async function handleScenarioSwitch(id: number) {
  scenarioSwitchingId.value = id
  try {
    await simulationRun.switchRecord(id, scenarioSwitching)
    scanAnalysisAlerts()
    await notifyLayoutChanged()
  } finally {
    scenarioSwitchingId.value = null
  }
}

async function handleScenarioRemove(id: number) {
  await simulationRun.removeRecord(id, scenarioSwitching)
  scanAnalysisAlerts()
}

watch(
  () => activeRecordId.value,
  () => {
    if (analysisActive.value && rightPanelResult.value) {
      scanAnalysisAlerts()
    }
  },
)

async function startForecast() {
  if (forecastRunStatus.value === 'running') {
    return
  }

  forecastRunAbort?.abort()
  const controller = new AbortController()
  forecastRunAbort = controller

  forecastRunStatus.value = 'running'
  forecastError.value = ''

  try {
    const days = Math.max(0, Math.floor(Number(forecastDays.value) || 0))
    const extraHours = Math.max(0, Number(forecastExtraHours.value) || 0)
    const hours = days * 24 + extraHours

    forecastDays.value = days
    forecastExtraHours.value = extraHours

    if (hours <= 0) {
      forecastError.value = '预报时长必须大于 0，请填写天数或小时数'
      return
    }

    const startedAt = new Date().toISOString()
    const detail = await runRiverNetworkForecast(hours, startedAt, controller.signal)
    await loadForecastRecords()
    applyForecastDetail(detail.id, detail.calculatedAt, detail.result)
    await notifyLayoutChanged()
    if (warningActive.value) {
      scanAnalysisAlerts()
    }
  } catch (error) {
    if (error instanceof FetchAbortedError) {
      return
    }
    forecastError.value =
      error instanceof Error ? error.message : '河网模型计算失败，请检查后端与 Python 环境'
  } finally {
    forecastRunStatus.value = 'idle'
  }
}
</script>

<template>
  <div
    class="scenario-grid"
    :class="{ 'left-collapsed': leftCollapsed, 'right-collapsed': rightCollapsed }"
  >
    <main class="center-stage">
      <MainMapStage :show-hydro-process="false" />
    </main>

    <aside
      class="scenario-side scenario-side-left side-panel"
      :class="{ collapsed: leftCollapsed, 'plan-records-only': planActive }"
      aria-label="左侧业务栏"
    >
      <button
        class="side-collapse-button side-collapse-button-left"
        type="button"
        :title="leftCollapsed ? '展开左侧展示栏' : '收起左侧展示栏'"
        @click="toggleLeftPanel"
      >
        {{ leftCollapsed ? '展开' : '收起' }}
      </button>

      <template v-if="!leftCollapsed">
        <PanelShell
          v-if="activeModelRun"
          :title="activeModelRun.ui.panelTitle"
          :eyebrow="activeModelRun.ui.eyebrow"
        >
          <ScenarioModelRunPanel :run="activeModelRun" />
        </PanelShell>

        <PanelShell
          v-else-if="forecastSummaryPanelVisible"
          title="预报概要"
          eyebrow="FORECAST"
        >
          <div class="forecast-control">
            <div class="forecast-duration-group">
              <span class="forecast-duration-label">预报时长</span>
              <div class="forecast-duration-inputs">
                <label class="forecast-duration-field">
                  天数
                  <input
                    v-model.number="forecastDays"
                    type="number"
                    min="0"
                    step="1"
                    :disabled="forecastRunStatus === 'running'"
                  />
                </label>
                <label class="forecast-duration-field">
                  小时
                  <input
                    v-model.number="forecastExtraHours"
                    type="number"
                    min="0"
                    step="0.1"
                    :disabled="forecastRunStatus === 'running'"
                  />
                </label>
              </div>
              <p class="forecast-duration-summary">合计 {{ totalForecastHours.toFixed(1) }} 小时</p>
            </div>
            <button
              class="primary-action forecast-start-btn"
              type="button"
              :disabled="forecastRunStatus === 'running'"
              @click="startForecast"
            >
              {{ forecastRunStatus === 'running' ? '计算中...' : '开始预报' }}
            </button>
            <div class="forecast-status-row">
              <span>模型状态</span>
              <strong :class="forecastRunStatus">{{ forecastStatusLabel }}</strong>
            </div>
            <p v-if="forecastError" class="forecast-error">{{ forecastError }}</p>
            <p v-else-if="forecastResult" class="forecast-meta">
              最近计算：{{ forecastResult.forecastHours }} 小时 / {{ forecastResult.nSteps }} 步 /
              {{ forecastResult.simulatedSeconds }} s
            </p>
          </div>
        </PanelShell>

        <PanelShell
          v-if="forecastRecordsPanelVisible"
          title="预报记录"
          eyebrow="RECORDS"
          class="forecast-records-shell"
        >
          <ForecastRecordTable
            :records="forecastRecords"
            :active-record-id="activeForecastRecordId"
            :loading="forecastSwitching"
            :switching-id="forecastSwitchingId"
            duration-label="预报时长"
            empty-text="暂无预报记录"
            @switch="switchForecastRecord"
            @remove="removeForecastRecord"
          />
        </PanelShell>

        <PanelShell
          v-if="scenarioRecordsPanelVisible"
          :title="scenarioRecordsUi.recordsTitle"
          :eyebrow="scenarioRecordsUi.recordsEyebrow"
          class="forecast-records-shell"
          :class="{ 'scenario-records-full': planActive }"
        >
          <ForecastRecordTable
            :variant="planActive ? 'plan' : 'simulation'"
            :records="simulationRun.records"
            :active-record-id="simulationRun.activeRecordId"
            :loading="scenarioSwitching"
            :switching-id="scenarioSwitchingId"
            :duration-label="scenarioRecordsUi.recordsDurationLabel"
            :empty-text="planActive ? '暂无预演记录，请先在「预演」中创建计算记录' : scenarioRecordsUi.recordsEmptyText"
            :filter-label="scenarioRecordsUi.recordsFilterLabel"
            :filter-empty-text="planActive ? '当前筛选条件下无对应预演记录' : scenarioRecordsUi.recordsFilterEmptyText"
            @switch="handleScenarioSwitch"
            @remove="handleScenarioRemove"
          />
        </PanelShell>
      </template>
    </aside>

    <aside
      class="scenario-side scenario-side-right side-panel"
      :class="{ collapsed: rightCollapsed }"
      aria-label="右侧业务栏"
    >
      <button
        class="side-collapse-button side-collapse-button-right"
        type="button"
        :title="rightCollapsed ? '展开右侧展示栏' : '收起右侧展示栏'"
        @click="toggleRightPanel"
      >
        {{ rightCollapsed ? '展开' : '收起' }}
      </button>

      <template v-if="!rightCollapsed">
        <PanelShell title="节点过程分析" eyebrow="NODES" class="node-process-shell">
          <RiverNetworkNodeProcessPanel
            :node-histories="rightPanelResult?.nodeHistories ?? []"
            :reach-histories="rightPanelResult?.reachHistories ?? []"
            :dt="rightPanelResult?.dt ?? 300"
            :started-at="rightPanelStartedAt"
            :warning-active="analysisActive"
            :gate-pressure-enabled="analysisActive"
          />
        </PanelShell>

        <PanelShell title="河段计算结果" eyebrow="REACHES" class="reach-results-shell">
          <RiverNetworkReachResultsPanel
            :reaches="rightPanelResult?.reaches ?? []"
            :reach-profiles="rightPanelResult?.reachProfiles ?? []"
            :warning-active="analysisActive"
            :embankment-pressure-active="embankmentPressureActive"
          />
        </PanelShell>
      </template>
    </aside>

    <ScenarioWarningDrawer
      v-if="analysisActive"
      :key="`${activeAnalysisStep}-${activeRecordId ?? 'none'}`"
      :expanded="analysisDrawerExpanded"
      :alerts="activeAlerts"
      :active-record-id="activeRecordId"
      :has-forecast-data="Boolean(rightPanelResult)"
      :scanning="analysisScanning"
      :eyebrow="drawerMeta.eyebrow"
      :title="drawerMeta.title"
      :aria-label="drawerMeta.ariaLabel"
      @toggle="toggleAnalysisDrawer"
      @scan="scanAnalysisAlerts"
      @confirm="confirmAnalysisAlert"
      @process="processAnalysisAlert"
      @clear-processed="clearProcessedAnalysisAlerts"
    />

    <nav class="four-precaution-bar" aria-label="防洪四预流程">
      <template v-for="(step, index) in precautionSteps" :key="step.key">
        <button
          type="button"
          class="precaution-btn"
          :class="{ active: activeStep === step.key }"
          :aria-current="activeStep === step.key ? 'step' : undefined"
          @click="switchPrecautionStep(step.key)"
        >
          <span class="precaution-btn-label">{{ step.label }}</span>
        </button>

        <div v-if="index < precautionSteps.length - 1" class="precaution-connector" aria-hidden="true">
          <span class="connector-track">
            <span class="connector-flow"></span>
          </span>
          <span class="connector-head"></span>
        </div>
      </template>
    </nav>

    <BoundaryConditionEditorModal
      v-if="activeModelRun?.activeBoundaryEditorStation && activeModelRun?.activeBoundaryEditorState"
      :open="activeModelRun.activeBoundaryEditorId !== null"
      :station="activeModelRun.activeBoundaryEditorStation"
      :duration-hours="activeModelRun.totalHours"
      :simulation-start-iso="new Date(activeModelRun.startAt).toISOString()"
      :default-value="toDisplayBoundaryValue(
        activeModelRun.activeBoundaryEditorStation.nodeId,
        activeModelRun.activeBoundaryEditorState.defaultModelValue,
      )"
      :control-points="activeModelRun.activeBoundaryEditorState.controlPoints.map((point) => ({
        t: point.t,
        value: toDisplayBoundaryValue(activeModelRun!.activeBoundaryEditorStation!.nodeId, point.value),
      }))"
      :customized="activeModelRun.activeBoundaryEditorState.customized"
      @close="activeModelRun!.closeBoundaryEditor()"
      @save="activeModelRun!.saveBoundaryEditor"
      @reset="activeModelRun!.resetBoundaryEditor()"
    />
  </div>
</template>
