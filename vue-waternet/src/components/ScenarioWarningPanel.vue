<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { WARNING_THRESHOLDS } from '@/constants/warningThresholds'
import type { ScenarioWarningAlert } from '@/types/platform'
import {
  SCENARIO_ALERT_METRICS,
  levelLabel,
  statusLabel,
} from '@/utils/scenarioWarningScanner'

const props = withDefaults(
  defineProps<{
    alerts: ScenarioWarningAlert[]
    activeRecordId: number | null
    hasForecastData: boolean
    scanning?: boolean
  }>(),
  {
    alerts: () => [],
  },
)

const emit = defineEmits<{
  scan: []
  confirm: [id: string]
  process: [id: string]
  clearProcessed: []
}>()

const selectedMetrics = ref<string[]>([])

watch(
  () => props.activeRecordId,
  () => {
    selectedMetrics.value = []
  },
)

const recordScopedAlerts = (item: ScenarioWarningAlert) =>
  item.recordId === props.activeRecordId || (props.activeRecordId === null && item.recordId === null)

function matchesMetric(metric: string) {
  if (!selectedMetrics.value.length) {
    return true
  }
  return selectedMetrics.value.includes(metric)
}

function toggleMetric(metric: string) {
  if (selectedMetrics.value.includes(metric)) {
    selectedMetrics.value = selectedMetrics.value.filter((item) => item !== metric)
    return
  }
  selectedMetrics.value = [...selectedMetrics.value, metric]
}

function clearMetricFilter() {
  selectedMetrics.value = []
}

const filterActive = computed(() => selectedMetrics.value.length > 0)

const recordCurrentAlerts = computed(() =>
  props.alerts.filter((item) => recordScopedAlerts(item) && item.status !== 'PROCESSED'),
)

const recordHistoryAlerts = computed(() =>
  props.alerts.filter((item) => recordScopedAlerts(item) && item.status === 'PROCESSED'),
)

const currentAlerts = computed(() => recordCurrentAlerts.value.filter((item) => matchesMetric(item.metric)))

const historyAlerts = computed(() =>
  [...recordHistoryAlerts.value]
    .filter((item) => matchesMetric(item.metric))
    .sort((left, right) => (right.processedAt ?? right.triggeredAt).localeCompare(left.processedAt ?? left.triggeredAt)),
)

const pendingCount = computed(() => currentAlerts.value.filter((item) => item.status === 'PENDING').length)
const confirmedCount = computed(() => currentAlerts.value.filter((item) => item.status === 'CONFIRMED').length)
const processedCount = computed(() => historyAlerts.value.length)

function metricCount(metric: string) {
  return (
    recordCurrentAlerts.value.filter((item) => item.metric === metric).length +
    recordHistoryAlerts.value.filter((item) => item.metric === metric).length
  )
}

function metricUnit(metric: string) {
  if (metric === '水位') {
    return 'm'
  }
  if (metric === '堤防静水压力' || metric === '水闸静水压力') {
    return 'kN/m'
  }
  return 'm³/s'
}

function formatMetricValue(metric: string, value: number) {
  if (metric === '堤防静水压力' || metric === '水闸静水压力') {
    return value.toFixed(1)
  }
  return value.toFixed(2)
}
</script>

<template>
  <div class="scenario-warning-panel">
    <div class="scenario-warning-toolbar">
      <div class="scenario-warning-thresholds">
        <div class="scenario-warning-threshold-tags">
          <span>水位上限 {{ WARNING_THRESHOLDS.waterLevelUpper }} m</span>
          <span>流量上限 {{ WARNING_THRESHOLDS.flowUpper }} m³/s</span>
        </div>
        <p class="scenario-warning-rule">
          对当前选中记录中超限水位、流量、堤防/水闸静水压力按规律预警；超出 15% 记为严重。支持告警历史与人工确认、处理标记。
        </p>
      </div>

      <button class="primary-action scenario-warning-scan-btn" type="button" :disabled="!hasForecastData || scanning" @click="emit('scan')">
        {{ scanning ? '扫描中...' : '扫描当前记录' }}
      </button>

      <div class="scenario-warning-stats">
        <div>
          <span>待确认</span>
          <strong>{{ pendingCount }}</strong>
        </div>
        <div>
          <span>已确认</span>
          <strong>{{ confirmedCount }}</strong>
        </div>
        <div>
          <span>已处理</span>
          <strong>{{ processedCount }}</strong>
        </div>
      </div>
    </div>

    <div v-if="hasForecastData" class="scenario-warning-metric-filter">
      <span class="scenario-warning-metric-filter-label">种类筛选</span>
      <div class="scenario-warning-metric-chips" aria-label="告警种类筛选">
        <button
          type="button"
          :class="{ active: !filterActive }"
          @click="clearMetricFilter"
        >
          全部
        </button>
        <button
          v-for="metric in SCENARIO_ALERT_METRICS"
          :key="metric"
          type="button"
          :class="{ active: selectedMetrics.includes(metric) }"
          :disabled="!metricCount(metric)"
          @click="toggleMetric(metric)"
        >
          {{ metric }}
          <em v-if="metricCount(metric)">{{ metricCount(metric) }}</em>
        </button>
      </div>
      <p v-if="filterActive" class="scenario-warning-metric-filter-summary">
        显示 {{ currentAlerts.length + historyAlerts.length }} /
        {{ recordCurrentAlerts.length + recordHistoryAlerts.length }} 条
      </p>
    </div>

    <div v-if="!hasForecastData" class="model-result-empty">请先完成预报或切换一条预报记录</div>

    <div v-else class="scenario-warning-content">
      <section class="scenario-warning-section">
        <div class="scenario-warning-section-head">
          <strong>当前告警</strong>
          <span>{{ currentAlerts.length }} 条</span>
        </div>
        <div v-if="currentAlerts.length" class="scenario-warning-list">
          <article v-for="alert in currentAlerts" :key="alert.id" class="scenario-warning-card">
            <div class="scenario-warning-card-head">
              <strong>{{ alert.targetName }}</strong>
              <em :class="alert.level.toLowerCase()">{{ levelLabel(alert.level) }}</em>
            </div>
            <div class="scenario-warning-card-body">
              <span>{{ alert.metric }} {{ formatMetricValue(alert.metric, alert.value) }} / {{ formatMetricValue(alert.metric, alert.threshold) }} {{ metricUnit(alert.metric) }}</span>
              <span>{{ alert.timeLabel }}</span>
            </div>
            <div class="scenario-warning-card-foot">
              <em class="watch">{{ statusLabel(alert.status) }}</em>
              <div class="scenario-warning-card-actions">
                <button
                  v-if="alert.status === 'PENDING'"
                  type="button"
                  class="scenario-warning-action-btn"
                  @click="emit('confirm', alert.id)"
                >
                  确认
                </button>
                <button type="button" class="scenario-warning-action-btn process" @click="emit('process', alert.id)">
                  标记处理
                </button>
              </div>
            </div>
          </article>
        </div>
        <div v-else class="model-result-empty">
          {{ filterActive ? '当前筛选条件下暂无告警' : '当前记录暂无超限告警' }}
        </div>
      </section>

      <section class="scenario-warning-section">
        <div class="scenario-warning-section-head">
          <strong>告警历史</strong>
          <button
            v-if="historyAlerts.length"
            type="button"
            class="scenario-warning-clear-btn"
            @click="emit('clearProcessed')"
          >
            清空已处理
          </button>
        </div>
        <div v-if="historyAlerts.length" class="scenario-warning-history">
          <div v-for="alert in historyAlerts" :key="alert.id" class="scenario-warning-history-row">
            <div>
              <strong>{{ alert.targetName }}</strong>
              <span>{{ alert.metric }} {{ formatMetricValue(alert.metric, alert.value) }} {{ metricUnit(alert.metric) }} / {{ alert.timeLabel }}</span>
            </div>
            <em class="normal">已处理</em>
          </div>
        </div>
        <div v-else class="model-result-empty">
          {{ filterActive ? '当前筛选条件下暂无已处理告警' : '暂无已处理告警' }}
        </div>
      </section>
    </div>
  </div>
</template>
