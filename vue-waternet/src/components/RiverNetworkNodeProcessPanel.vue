<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import InteractiveHydroChart from '@/components/InteractiveHydroChart.vue'
import { GATE_PRESSURE_STATIONS } from '@/constants/gatePressureStations'
import { isFlowExceeded, isWaterLevelExceeded } from '@/constants/warningThresholds'
import type { RiverNetworkNodeHistory, RiverNetworkReachHistory } from '@/types/platform'
import { calcPressureFromWaterLevel } from '@/utils/embankmentPressure'

const props = defineProps<{
  nodeHistories: RiverNetworkNodeHistory[]
  reachHistories?: RiverNetworkReachHistory[]
  dt: number
  startedAt?: string | null
  emptyText?: string
  warningActive?: boolean
  /** 预警 / 预演页启用水闸静水压力视图 */
  gatePressureEnabled?: boolean
}>()

type PanelMode = 'node' | 'gate'
type ChartMode = 'level' | 'flow' | 'both'
type NodeReachRole = 'start' | 'end'

const panelMode = ref<PanelMode>('node')
const selectedNodeId = ref<number | null>(null)
const selectedReachId = ref<number | null>(null)
const selectedGateId = ref('')
const chartMode = ref<ChartMode>('both')

watch(
  () => props.nodeHistories,
  (histories) => {
    if (!histories.length) {
      selectedNodeId.value = null
      return
    }
    if (!histories.some((history) => history.nodeId === selectedNodeId.value)) {
      selectedNodeId.value = histories[0]?.nodeId ?? null
    }
  },
  { immediate: true },
)

const connectedReaches = computed(() => {
  if (selectedNodeId.value === null) {
    return []
  }
  return (props.reachHistories ?? []).filter(
    (reach) => reach.startNode === selectedNodeId.value || reach.endNode === selectedNodeId.value,
  )
})

watch(
  [connectedReaches, selectedNodeId],
  ([reaches]) => {
    if (!reaches.length) {
      selectedReachId.value = null
      return
    }
    if (!reaches.some((reach) => reach.reachId === selectedReachId.value)) {
      selectedReachId.value = reaches[0]?.reachId ?? null
    }
  },
  { immediate: true },
)

const selectedReachHistory = computed(
  () => connectedReaches.value.find((reach) => reach.reachId === selectedReachId.value) ?? null,
)

const selectedNodeRole = computed<NodeReachRole | null>(() => {
  const reach = selectedReachHistory.value
  if (!reach || selectedNodeId.value === null) {
    return null
  }
  if (reach.startNode === selectedNodeId.value) {
    return 'start'
  }
  if (reach.endNode === selectedNodeId.value) {
    return 'end'
  }
  return null
})

const boundaryCaption = computed(() => {
  if (!selectedReachHistory.value || !selectedNodeRole.value) {
    return ''
  }
  return selectedNodeRole.value === 'start' ? '河段首端' : '河段末端'
})

const boundarySeries = computed(() => {
  const reach = selectedReachHistory.value
  const role = selectedNodeRole.value
  if (!reach || !role) {
    return { waterLevels: [] as number[], flows: [] as number[] }
  }
  if (role === 'start') {
    return {
      waterLevels: reach.inletWaterLevels,
      flows: reach.inletFlows,
    }
  }
  return {
    waterLevels: reach.outletWaterLevels,
    flows: reach.outletFlows,
  }
})

function formatStepTime(stepIndex: number, compact = false) {
  const baseMs = props.startedAt ? new Date(props.startedAt).getTime() : Date.now()
  const stepDate = new Date(baseMs + stepIndex * props.dt * 1000)
  const year = stepDate.getFullYear()
  const month = String(stepDate.getMonth() + 1).padStart(2, '0')
  const day = String(stepDate.getDate()).padStart(2, '0')
  const hours = String(stepDate.getHours()).padStart(2, '0')
  const minutes = String(stepDate.getMinutes()).padStart(2, '0')
  if (compact) {
    return `${month}-${day} ${hours}:${minutes}`
  }
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

const tableRows = computed(() => {
  const { waterLevels, flows } = boundarySeries.value
  if (!waterLevels.length) {
    return []
  }
  return waterLevels.map((waterLevel, step) => ({
    step,
    timeLabel: formatStepTime(step),
    chartTimeLabel: formatStepTime(step, true),
    waterLevel,
    flow: flows[step] ?? 0,
  }))
})

const chartXLabels = computed(() => tableRows.value.map((row) => row.timeLabel))
const chartXTickLabels = computed(() => tableRows.value.map((row) => row.chartTimeLabel))

const chartSeries = computed(() => {
  const { waterLevels, flows } = boundarySeries.value
  if (!waterLevels.length) {
    return []
  }
  return [
    {
      key: 'level',
      label: '水位',
      color: '#00f2ff',
      values: waterLevels,
      unit: 'm',
    },
    {
      key: 'flow',
      label: '流量',
      color: '#2fffa8',
      values: flows.map((value) => Math.abs(value)),
      unit: 'm3/s',
    },
  ]
})

const showLevel = computed(() => chartMode.value === 'level' || chartMode.value === 'both')
const showFlow = computed(() => chartMode.value === 'flow' || chartMode.value === 'both')
const hasReachBoundaryData = computed(() => boundarySeries.value.waterLevels.length > 0)

const nodeHistoryMap = computed(() => {
  const map = new Map<number, RiverNetworkNodeHistory>()
  for (const history of props.nodeHistories) {
    map.set(history.nodeId, history)
  }
  return map
})

const gatePressureTimeRows = computed(() => {
  const rows = GATE_PRESSURE_STATIONS.flatMap((gate) => {
    const history = nodeHistoryMap.value.get(gate.nodeId)
    if (!history?.waterLevels.length) {
      return []
    }

    return history.waterLevels.map((waterLevel, step) => ({
      id: `${gate.id}-${step}`,
      gateId: gate.id,
      gateLabel: gate.label,
      nodeId: gate.nodeId,
      step,
      timeLabel: formatStepTime(step),
      waterLevel,
      pressure: calcPressureFromWaterLevel(waterLevel),
      exceeded: props.warningActive ? isWaterLevelExceeded(waterLevel) : false,
    }))
  })

  return rows.sort((left, right) => left.step - right.step || left.gateLabel.localeCompare(right.gateLabel))
})

const visibleGatePressureTimeRows = computed(() => {
  if (!selectedGateId.value) {
    return gatePressureTimeRows.value
  }
  return gatePressureTimeRows.value.filter((row) => row.gateId === selectedGateId.value)
})

const hasGatePressureRows = computed(() => gatePressureTimeRows.value.length > 0)
const hasVisibleGatePressureRows = computed(() => visibleGatePressureTimeRows.value.length > 0)
const gateFilterActive = computed(() => Boolean(selectedGateId.value))
</script>

<template>
  <div class="node-process-panel">
    <div v-if="gatePressureEnabled" class="node-process-mode-switch" aria-label="分析视图切换">
      <button type="button" :class="{ active: panelMode === 'node' }" @click="panelMode = 'node'">
        节点过程
      </button>
      <button type="button" :class="{ active: panelMode === 'gate' }" @click="panelMode = 'gate'">
        水闸静水压力
      </button>
    </div>

    <template v-if="panelMode === 'node' || !gatePressureEnabled">
    <div class="node-process-upper">
      <div class="node-process-selector">
        <strong>节点筛选</strong>
        <div v-if="nodeHistories.length" class="node-process-selector-list">
          <button
            v-for="history in nodeHistories"
            :key="history.nodeId"
            type="button"
            class="node-process-selector-item"
            :class="{ active: history.nodeId === selectedNodeId }"
            @click="selectedNodeId = history.nodeId"
          >
            节点 {{ history.nodeId }}
          </button>
        </div>
        <div v-else class="model-result-empty">{{ emptyText ?? '暂无节点过程数据' }}</div>

        <label v-if="selectedNodeId !== null" class="node-process-reach-filter">
          <span>关联河段</span>
          <select v-model.number="selectedReachId" :disabled="!connectedReaches.length">
            <option v-for="reach in connectedReaches" :key="reach.reachId" :value="reach.reachId">
              {{ reach.label }}
            </option>
          </select>
        </label>
        <div v-if="selectedNodeId !== null && !connectedReaches.length" class="node-process-reach-hint">
          该节点暂无关联河段时序数据，请重新预报
        </div>
        <div v-else-if="boundaryCaption" class="node-process-reach-hint">
          展示节点作为{{ boundaryCaption }}的水位与流量
        </div>
      </div>

      <div class="node-process-table-wrap">
        <div class="model-result-table node-process-table">
          <div class="model-result-table-head node-process-table-head">
            <span>时间</span>
            <span>水位(m)</span>
            <span>流量(m3/s)</span>
          </div>
          <div v-if="tableRows.length" class="model-result-table-body">
            <div v-for="row in tableRows" :key="`${selectedNodeId}-${selectedReachId}-${row.step}`" class="model-result-table-row">
              <span>{{ row.timeLabel }}</span>
              <strong :class="{ 'warning-value-exceeded': warningActive && isWaterLevelExceeded(row.waterLevel) }">
                {{ row.waterLevel.toFixed(2) }}
              </strong>
              <strong :class="{ 'warning-value-exceeded': warningActive && isFlowExceeded(row.flow) }">
                {{ row.flow.toFixed(2) }}
              </strong>
            </div>
          </div>
          <div v-else class="model-result-empty">请选择节点与关联河段查看过程数据</div>
        </div>
      </div>
    </div>
    </template>

    <div v-else class="gate-pressure-panel">
      <div class="gate-pressure-toolbar">
        <label class="node-process-reach-filter gate-pressure-filter">
          <span>水闸筛选</span>
          <select v-model="selectedGateId" :disabled="!hasGatePressureRows">
            <option value="">全部水闸</option>
            <option v-for="gate in GATE_PRESSURE_STATIONS" :key="gate.id" :value="gate.id">
              {{ gate.label }}（节点 {{ gate.nodeId }}）
            </option>
          </select>
        </label>
        <p v-if="gateFilterActive && hasGatePressureRows" class="gate-pressure-filter-summary">
          显示 {{ visibleGatePressureTimeRows.length }} / {{ gatePressureTimeRows.length }} 条
        </p>
      </div>
      <p class="gate-pressure-caption">F = ½γw·h²，γw = 9.81 kN/m³；h 取各时刻节点水位</p>
      <div class="gate-pressure-table-wrap">
        <div class="model-result-table gate-pressure-table">
          <div class="model-result-table-head gate-pressure-table-head">
            <span>时间</span>
            <span>水闸</span>
            <span>节点</span>
            <span>水位(m)</span>
            <span>静水压力(kN/m)</span>
          </div>
          <div v-if="hasVisibleGatePressureRows" class="model-result-table-body gate-pressure-table-body">
            <div
              v-for="row in visibleGatePressureTimeRows"
              :key="row.id"
              class="model-result-table-row gate-pressure-table-row"
            >
              <span>{{ row.timeLabel }}</span>
              <span>{{ row.gateLabel }}</span>
              <span>{{ row.nodeId }}</span>
              <strong :class="{ 'warning-value-exceeded': row.exceeded }">
                {{ row.waterLevel.toFixed(2) }}
              </strong>
              <strong :class="{ 'warning-value-exceeded': row.exceeded }">
                {{ row.pressure.toFixed(1) }}
              </strong>
            </div>
          </div>
          <div v-else-if="hasGatePressureRows && gateFilterActive" class="model-result-empty">
            所选水闸暂无过程数据
          </div>
          <div v-else class="model-result-empty">{{ emptyText ?? '暂无节点过程数据' }}</div>
        </div>
      </div>
    </div>

    <div v-if="panelMode === 'node' || !gatePressureEnabled" class="node-process-lower">
      <div class="node-process-lower-toolbar">
        <strong>过程线</strong>
        <div class="profile-mode-switch" aria-label="节点过程线展示模式">
          <button type="button" :class="{ active: chartMode === 'level' }" @click="chartMode = 'level'">水位</button>
          <button type="button" :class="{ active: chartMode === 'flow' }" @click="chartMode = 'flow'">流量</button>
          <button type="button" :class="{ active: chartMode === 'both' }" @click="chartMode = 'both'">集成</button>
        </div>
      </div>

      <div class="node-process-chart">
        <InteractiveHydroChart
          v-if="hasReachBoundaryData"
          :series="chartSeries"
          :x-labels="chartXLabels"
          :x-tick-labels="chartXTickLabels"
          :mode="chartMode"
          :chart-height="180"
          :warning-active="warningActive"
          empty-text="请选择节点与关联河段查看过程线"
        />
        <div v-else class="model-result-empty">请选择节点与关联河段查看过程线</div>
        <div v-if="hasReachBoundaryData" class="reach-profile-legend">
          <span v-if="showLevel" class="legend-item level">水位</span>
          <span v-if="showFlow" class="legend-item flow">流量</span>
          <span v-if="selectedReachHistory" class="legend-note">{{ selectedReachHistory.label }} / {{ boundaryCaption }}</span>
          <span v-if="chartMode === 'both'" class="legend-note">集成模式下两序列分别归一化展示</span>
          <span v-if="warningActive" class="legend-note warning-legend-note">预警阈值线已标注</span>
          <span class="legend-note">鼠标悬停查看各时刻数值</span>
        </div>
      </div>
    </div>
  </div>
</template>
