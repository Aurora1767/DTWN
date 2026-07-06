<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import InsightLineChart from '@/components/InsightLineChart.vue'
import PanelShell from '@/components/PanelShell.vue'
import { fetchWaterQualityNodeHistory } from '@/services/api'
import { usePlatformStore } from '@/stores/platform'
import type { WaterQualityHistoryPoint, WaterQualityLevel, WaterQualityNodeHistory, WaterQualityOverview } from '@/types/platform'

const props = defineProps<{
  overview: WaterQualityOverview
  live?: boolean
}>()

const panelMode = ref<'detail' | 'history' | 'bar'>('bar')
const selectedNodeId = ref(1)
const selectedMetric = ref<WaterQualityMetric>('dissolvedOxygen')
const nodeHistory = ref<WaterQualityNodeHistory | null>(null)
const historyLoading = ref(false)
type WaterQualityMetric = Exclude<keyof WaterQualityHistoryPoint, 'time' | 'nodeId' | 'level'>
type WaterQualityMetricOption = { key: WaterQualityMetric; label: string; unit: string; color: string }
type DetailSlotKey = 'slotA' | 'slotB' | 'slotC'

const fallbackMetric: WaterQualityMetricOption = { key: 'dissolvedOxygen', label: 'DO', unit: 'mg/L', color: '#7ddcff' }
const metricOptions: WaterQualityMetricOption[] = [
  { key: 'dissolvedOxygen', label: 'DO', unit: 'mg/L', color: '#7ddcff' },
  { key: 'ph', label: 'pH', unit: '', color: '#53f2c5' },
  { key: 'chemicalOxygenDemand', label: 'COD', unit: 'mg/L', color: '#ffd56a' },
  { key: 'permanganateIndex', label: 'Mn', unit: 'mg/L', color: '#ffbd7a' },
  { key: 'ammoniaNitrogen', label: '氨氮', unit: 'mg/L', color: '#ff8fb0' },
  { key: 'totalPhosphorus', label: '总磷', unit: 'mg/L', color: '#c8a7ff' },
  { key: 'bod5', label: 'BOD', unit: 'mg/L', color: '#8affd2' },
]
const detailMetricOptions: WaterQualityMetricOption[] = [
  { key: 'ph', label: 'PH', unit: '', color: '#53f2c5' },
  { key: 'dissolvedOxygen', label: 'DO', unit: 'mg/L', color: '#7ddcff' },
  { key: 'permanganateIndex', label: 'Mn', unit: 'mg/L', color: '#ffbd7a' },
  { key: 'ammoniaNitrogen', label: '氨氮', unit: 'mg/L', color: '#ff8fb0' },
  { key: 'totalPhosphorus', label: '总磷', unit: 'mg/L', color: '#c8a7ff' },
  { key: 'chemicalOxygenDemand', label: 'COD', unit: 'mg/L', color: '#ffd56a' },
  { key: 'bod5', label: 'BOD', unit: 'mg/L', color: '#8affd2' },
]
const levels: WaterQualityLevel[] = ['I类', 'II类', 'III类', 'IV类', 'V类']
const fallbackLevel = {
  level: 'I类' as WaterQualityLevel,
  count: 0,
  color: '#53f2c5',
  percent: 0,
}
const levelColors: Record<WaterQualityLevel, string> = {
  I类: '#53f2c5',
  II类: '#7ddcff',
  III类: '#ffd56a',
  IV类: '#ff9f6a',
  V类: '#ff6b8a',
}
const detailMetrics = ref<Record<DetailSlotKey, WaterQualityMetric>>({
  slotA: 'ph',
  slotB: 'dissolvedOxygen',
  slotC: 'permanganateIndex',
})

const totalCount = computed(() => props.overview.nodes.length || 92)

const levelStats = computed(() =>
  levels.map((level) => {
    const count = props.overview.summary[level] ?? 0
    return {
      level,
      count,
      color: levelColors[level],
      percent: totalCount.value > 0 ? Math.round((count / totalCount.value) * 100) : 0,
    }
  }),
)

const dominantLevel = computed(() =>
  levelStats.value.reduce((best, current) => (current.count > best.count ? current : best), fallbackLevel),
)

const sampleNodes = computed(() => props.overview.nodes)
const nodeOptions = computed(() => props.overview.nodes.map((node) => node.nodeId))
const activeMetric = computed(() => metricOptions.find((metric) => metric.key === selectedMetric.value) ?? fallbackMetric)
const detailColumns = computed(() => [
  detailMetricOptions.find((option) => option.key === detailMetrics.value.slotA) ?? detailMetricOptions[0]!,
  detailMetricOptions.find((option) => option.key === detailMetrics.value.slotB) ?? detailMetricOptions[1]!,
  detailMetricOptions.find((option) => option.key === detailMetrics.value.slotC) ?? detailMetricOptions[2]!,
])
const historyPoints = computed(() => nodeHistory.value?.points ?? [])
const historyValues = computed(() => historyPoints.value.map((point) => Number(point[selectedMetric.value])))
const historyLabels = computed(() =>
  historyPoints.value.map((point) => {
    const normalized = point.time.replace('T', ' ')
    const match = normalized.match(/(\d{1,2})-(\d{1,2})\s+(\d{1,2}):/)
    if (match) {
      return `${match[2]}/${match[3]}`
    }
    return normalized.slice(5, 13)
  }),
)

function formatRecordTime(value: string) {
  if (!value) {
    return '--'
  }
  return value.replace('T', ' ').slice(0, 19)
}

async function selectNode(nodeId: number) {
  selectedNodeId.value = nodeId
  historyLoading.value = true
  try {
    nodeHistory.value = await fetchWaterQualityNodeHistory(nodeId, 24)
  } finally {
    historyLoading.value = false
  }
}

function onNodeSelect() {
  void selectNode(selectedNodeId.value)
}

function formatMetricValue(point: WaterQualityOverview['nodes'][number], metricKey: WaterQualityMetric) {
  const value = point[metricKey]
  return typeof value === 'number' ? value.toFixed(2) : String(value)
}

selectNode(selectedNodeId.value)

const platformStore = usePlatformStore()
watch(() => platformStore.selectedSurveyPointId, (id) => {
  if (id === null) return
  panelMode.value = 'history'
  void selectNode(id)
})
</script>

<template>
  <PanelShell title="水质监测" eyebrow="WATER QUALITY" class="water-quality-panel">
    <div class="water-quality-content">
      <div class="water-quality-summary">
        <div>
          <span>监测测点</span>
          <strong>{{ totalCount }}</strong>
        </div>
        <div>
          <span>主要等级</span>
          <strong :style="{ color: dominantLevel.color }">{{ dominantLevel.level }}</strong>
        </div>
        <div>
          <span>{{ props.live ? '实时模拟' : '离线/缓存' }}</span>
          <strong>{{ dominantLevel.count }}</strong>
        </div>
      </div>

      <div class="water-quality-toolbar">
        <span>{{ formatRecordTime(props.overview.recordTime) }}</span>
        <div class="water-quality-mode-switch">
          <button
            type="button"
            :class="{ active: panelMode === 'detail' }"
            title="测点详情"
            @click="panelMode = 'detail'"
          >
            详情
          </button>
          <button
            type="button"
            :class="{ active: panelMode === 'history' }"
            title="测点过程线"
            @click="panelMode = 'history'"
          >
            过程
          </button>
          <button
            type="button"
            :class="{ active: panelMode === 'bar' }"
            title="柱状图"
            @click="panelMode = 'bar'"
          >
            柱状
          </button>
        </div>
      </div>

      <template v-if="panelMode === 'bar'">
        <div class="water-quality-chart water-quality-chart-tall">
          <div class="water-quality-bars">
            <div v-for="item in levelStats" :key="item.level" class="water-quality-bar-row">
              <span class="water-quality-bar-label" :style="{ color: item.color }">{{ item.level }}</span>
              <div class="water-quality-bar-track">
                <i :style="{ width: `${item.percent}%`, background: `linear-gradient(90deg, ${item.color}55, ${item.color})`, boxShadow: `0 0 12px ${item.color}99` }" />
                <span v-if="item.percent > 8" class="water-quality-bar-pct" :style="{ color: item.color }">{{ item.percent }}%</span>
              </div>
              <b class="water-quality-bar-count" :style="{ color: item.color }">{{ item.count }}</b>
            </div>
          </div>
        </div>
      </template>

      <template v-if="panelMode === 'detail'">
        <div class="water-quality-detail-table">
          <div class="water-quality-detail-config">
            <label v-for="(column, index) in detailColumns" :key="column.key">
              <select
                :value="column.key"
                @change="detailMetrics[(index === 0 ? 'slotA' : index === 1 ? 'slotB' : 'slotC') as DetailSlotKey] = ($event.target as HTMLSelectElement).value as WaterQualityMetric"
              >
                <option v-for="option in detailMetricOptions" :key="option.key" :value="option.key">
                  {{ option.label }}
                </option>
              </select>
            </label>
          </div>
          <div class="water-quality-detail-head">
            <span>测点</span>
            <span v-for="column in detailColumns" :key="`head-${column.key}`">{{ column.label }}</span>
            <span>等级</span>
          </div>
          <button
            v-for="node in sampleNodes"
            :key="node.nodeId"
            type="button"
            class="water-quality-detail-row"
            :class="{ active: selectedNodeId === node.nodeId }"
            @click="selectNode(node.nodeId); panelMode = 'history'"
          >
            <span class="water-quality-detail-node">N{{ String(node.nodeId).padStart(2, '0') }}</span>
            <b v-for="column in detailColumns" :key="`${node.nodeId}-${column.key}`">
              {{ formatMetricValue(node, column.key) }}
            </b>
            <strong :style="{ color: levelColors[node.level] }">{{ node.level }}</strong>
          </button>
        </div>
      </template>

      <div v-else-if="panelMode === 'history'" class="water-quality-history">
        <div class="water-quality-history-head">
          <label>
            <span>测点</span>
            <select v-model.number="selectedNodeId" title="选择测点" @change="onNodeSelect">
              <option v-for="nodeId in nodeOptions" :key="nodeId" :value="nodeId">
                N{{ String(nodeId).padStart(2, '0') }}
              </option>
            </select>
          </label>
          <label>
            <span>指标</span>
            <select v-model="selectedMetric" title="选择过程线指标">
              <option v-for="metric in metricOptions" :key="metric.key" :value="metric.key">
                {{ metric.label }}
              </option>
            </select>
          </label>
        </div>
        <div class="water-quality-history-chart">
          <InsightLineChart
            v-if="!historyLoading"
            :values="historyValues"
            :labels="historyLabels"
            :color="activeMetric.color"
            :unit="activeMetric.unit"
            :value-digits="selectedMetric === 'ph' ? 2 : 2"
          />
          <div v-else class="water-quality-history-loading">加载中</div>
        </div>
      </div>
    </div>
  </PanelShell>
</template>

<style scoped>
.water-quality-panel.panel-shell > :not(.panel-heading) {
  overflow: hidden;
}

.water-quality-content {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-height: 0;
  height: 100%;
}

.water-quality-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  flex-shrink: 0;
}

.water-quality-summary > div {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 7px 8px;
  border: 1px solid rgba(0, 242, 255, 0.16);
  background: rgba(0, 11, 33, 0.48);
}

.water-quality-summary span,
.water-quality-toolbar span,
.water-quality-detail-head {
  color: #8fbfd5;
  font-size: 10px;
  line-height: 1.2;
}

.water-quality-summary strong {
  color: #ffffff;
  font-size: 15px;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.water-quality-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
}

.water-quality-mode-switch {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  width: 132px;
  border: 1px solid rgba(0, 242, 255, 0.28);
}

.water-quality-mode-switch button {
  height: 24px;
  padding: 0;
  border: 0;
  background: rgba(9, 30, 61, 0.74);
  color: #9cc7dd;
  font-size: 10px;
}

.water-quality-mode-switch button.active {
  background: rgba(0, 132, 168, 0.62);
  color: #ffffff;
}

.water-quality-chart {
  flex: 0 0 100px;
  min-height: 100px;
  overflow: hidden;
}

.water-quality-chart-tall {
  flex-basis: 188px;
  min-height: 188px;
}

.water-quality-bars {
  display: grid;
  gap: 12px;
  height: 100%;
  align-content: center;
  padding: 10px 4px 4px;
}

.water-quality-bar-row {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 32px;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.water-quality-bar-label,
.water-quality-bar-count {
  color: #d9f7ff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
}

.water-quality-bar-count {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.water-quality-bar-track {
  position: relative;
  height: 18px;
  overflow: hidden;
  border: 1px solid rgba(0, 242, 255, 0.1);
  background: rgba(0, 11, 33, 0.72);
  border-radius: 2px;
}

.water-quality-bar-track i {
  display: block;
  height: 100%;
  min-width: 2px;
  border-radius: 2px;
  transition: width 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.water-quality-bar-pct {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  pointer-events: none;
  text-shadow: 0 0 6px rgba(0,0,0,0.8);
}

.water-quality-history {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-height: 0;
  border: 1px solid rgba(0, 242, 255, 0.12);
  background: rgba(0, 11, 33, 0.28);
  overflow: hidden;
}

.water-quality-history-head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding: 8px 8px 0;
}

.water-quality-history-head label {
  display: grid;
  gap: 4px;
  min-width: 0;
  color: #8fbfd5;
  font-size: 10px;
  line-height: 1.2;
}

.water-quality-history-head select {
  width: 100%;
  height: 26px;
  border: 1px solid rgba(0, 242, 255, 0.28);
  background: rgba(9, 30, 61, 0.86);
  color: #d9f7ff;
  font-size: 10px;
}

.water-quality-history-chart {
  flex: 1;
  min-height: 0;
  padding: 0 6px 6px;
}

.water-quality-history-chart :deep(.insight-chart-wrap) {
  height: 100%;
  min-height: 0;
}

.water-quality-history-chart :deep(.insight-chart) {
  height: 100%;
}

.water-quality-history-chart :deep(.insight-axis-caption) {
  display: none;
}

.water-quality-history-chart :deep(.insight-tooltip) {
  font-size: 11px;
  min-width: 84px;
  padding: 4px 7px;
}

.water-quality-history-chart :deep(.insight-axis-value),
.water-quality-history-chart :deep(.insight-axis-tick) {
  font-size: 9px;
}

.water-quality-history-loading {
  display: grid;
  place-items: center;
  height: 100%;
  color: #8fbfd5;
  font-size: 10px;
}

.water-quality-detail-table {
  display: grid;
  gap: 4px;
  flex: 1;
  min-height: 220px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
  scrollbar-color: rgba(0, 242, 255, 0.35) rgba(0, 11, 33, 0.45);
  scrollbar-width: thin;
}

.water-quality-detail-table::-webkit-scrollbar {
  width: 5px;
}

.water-quality-detail-table::-webkit-scrollbar-track {
  background: rgba(0, 11, 33, 0.45);
}

.water-quality-detail-table::-webkit-scrollbar-thumb {
  background: rgba(0, 242, 255, 0.35);
}

.water-quality-detail-head,
.water-quality-detail-row {
  display: grid;
  grid-template-columns: 54px repeat(3, minmax(0, 1fr)) 56px;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.water-quality-detail-config {
  position: sticky;
  top: 0;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 4px 6px;
  background: rgba(9, 30, 61, 0.98);
  border-bottom: 1px solid rgba(0, 242, 255, 0.12);
}

.water-quality-detail-config label {
  display: block;
  min-width: 0;
}

.water-quality-detail-config select {
  width: 100%;
  height: 24px;
  border: 1px solid rgba(0, 242, 255, 0.24);
  background: rgba(7, 24, 46, 0.9);
  color: #d9f7ff;
  font-size: 10px;
}

.water-quality-detail-head {
  position: sticky;
  top: 33px;
  z-index: 2;
  min-height: 28px;
  padding: 6px 6px 4px;
  background: rgba(9, 30, 61, 0.98);
  border-bottom: 1px solid rgba(0, 242, 255, 0.12);
}

.water-quality-detail-head-select {
  min-width: 0;
}

.water-quality-detail-head-select select {
  width: 100%;
  height: 22px;
}

.water-quality-detail-row {
  width: 100%;
  min-height: 24px;
  padding: 4px 6px;
  border-radius: 0;
  border: 1px solid rgba(0, 242, 255, 0.12);
  background: rgba(0, 11, 33, 0.36);
  text-align: left;
}

.water-quality-detail-head span:first-child,
.water-quality-detail-row .water-quality-detail-node {
  position: sticky;
  left: 0;
  z-index: 1;
  background: inherit;
}

.water-quality-detail-row .water-quality-detail-node {
  font-weight: 700;
}

.water-quality-detail-row.active,
.water-quality-detail-row:hover {
  border-color: rgba(0, 242, 255, 0.42);
  background: rgba(0, 82, 116, 0.42);
}

.water-quality-detail-row span,
.water-quality-detail-row b,
.water-quality-detail-row strong {
  min-width: 0;
  overflow: hidden;
  color: #d9f7ff;
  font-size: 10px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.water-quality-detail-row strong {
  font-weight: 700;
  text-align: center;
}
</style>
