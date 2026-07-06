<script setup lang="ts">
import { computed } from 'vue'

import ReadonlyBoundaryChart from '@/components/ReadonlyBoundaryChart.vue'
import {
  GATE_OPENING_STATIONS,
  resolveGateOpenings,
} from '@/constants/gateOpeningStations'
import {
  SIMULATION_BOUNDARY_STATIONS,
  toDisplayBoundaryValue,
} from '@/constants/simulationBoundaries'
import type { SimulationRecordSettings } from '@/types/platform'
import { formatBoundaryTimeLabel } from '@/utils/boundaryInterpolation'

const props = defineProps<{
  open: boolean
  settings: SimulationRecordSettings | null
  loading?: boolean
  error?: string
}>()

const emit = defineEmits<{
  close: []
}>()

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

function formatDuration(hours: number) {
  const days = Math.floor(hours / 24)
  const remain = Number((hours - days * 24).toFixed(1))
  if (days > 0 && remain > 0) {
    return `${days} 天 ${remain} 小时`
  }
  if (days > 0) {
    return `${days} 天`
  }
  return `${remain} 小时`
}

const statusClass = computed(() => {
  const status = props.settings?.status?.toLowerCase() ?? ''
  if (status === 'success') {
    return 'success'
  }
  if (status === 'fallback') {
    return 'fallback'
  }
  return 'default'
})

const basicRows = computed(() => {
  if (!props.settings) {
    return []
  }
  return [
    { label: '预演名称', value: props.settings.simulationName || '--' },
    { label: '预演开始时间', value: formatDateTime(props.settings.simulationStartAt) },
    { label: '预演时长', value: formatDuration(props.settings.forecastHours) },
    { label: '时间步长', value: `${props.settings.dt} s` },
    { label: '计算步数', value: `${props.settings.nSteps} 步` },
    { label: '模型状态', value: props.settings.status || '--', status: true },
  ]
})

const boundaryRows = computed(() => {
  if (!props.settings) {
    return []
  }

  const startIso = props.settings.simulationStartAt
  const dt = props.settings.dt

  return SIMULATION_BOUNDARY_STATIONS.map((station) => {
    const modelValue = props.settings?.boundaryValues?.[station.nodeId]
    const series = props.settings?.boundarySeries?.[station.nodeId]
    const hasSeries = Boolean(series?.length)
    const decimals = station.valueKind === 'level' ? 2 : 1
    const kindLabel = station.valueKind === 'level' ? '水位' : '流量'
    const chartColor = station.valueKind === 'level' ? '#00f2ff' : '#7dffb2'

    let summary = '--'
    if (hasSeries && series) {
      const displaySeries = series.map((value) => toDisplayBoundaryValue(station.nodeId, value))
      const start = displaySeries[0]
      const end = displaySeries[displaySeries.length - 1]
      if (start !== undefined && end !== undefined) {
        summary = `起 ${start.toFixed(decimals)} → 止 ${end.toFixed(decimals)} ${station.unit}`
      }
    } else if (modelValue !== undefined) {
      const displayValue = toDisplayBoundaryValue(station.nodeId, modelValue)
      summary = `${displayValue.toFixed(decimals)} ${station.unit}（全程恒定）`
    }

    const chartValues =
      hasSeries && series
        ? series.map((value) => toDisplayBoundaryValue(station.nodeId, value))
        : []

    const chartLabels = hasSeries
      ? Array.from({ length: chartValues.length }, (_, index) =>
          formatBoundaryTimeLabel(startIso, (index * dt) / 3600),
        )
      : []

    return {
      nodeId: station.nodeId,
      label: station.label,
      shortLabel: station.shortLabel,
      kindLabel,
      customized: hasSeries,
      summary,
      chartValues,
      chartLabels,
      unit: station.unit,
      chartColor,
    }
  })
})

const customizedBoundaries = computed(() => boundaryRows.value.filter((item) => item.customized))
const constantBoundaries = computed(() => boundaryRows.value.filter((item) => !item.customized))

const gateOpeningRows = computed(() => {
  if (!props.settings) {
    return []
  }
  const resolved = resolveGateOpenings(props.settings.gateOpenings)
  return GATE_OPENING_STATIONS.map((station) => ({
    nodeId: station.nodeId,
    label: station.label,
    widthLabel: station.widthLabel,
    openingPercent: resolved[String(station.nodeId)] ?? 100,
  }))
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="simulation-settings-overlay" @click.self="emit('close')">
      <div class="simulation-settings-modal" role="dialog" aria-label="预演设置详情" @click.stop>
        <header class="simulation-settings-header">
          <div>
            <p class="simulation-settings-eyebrow">SIMULATION SETTINGS</p>
            <h3>预演设置详情</h3>
          </div>
          <button class="simulation-settings-close" type="button" aria-label="关闭" @click="emit('close')">
            ×
          </button>
        </header>

        <div class="simulation-settings-body">
          <p v-if="loading" class="simulation-settings-status">正在加载设置...</p>
          <p v-else-if="error" class="simulation-settings-error">{{ error }}</p>

          <template v-else-if="settings">
            <section class="simulation-settings-section">
              <h4>基本参数</h4>
              <div class="simulation-settings-basic-grid">
                <div
                  v-for="row in basicRows"
                  :key="row.label"
                  class="simulation-settings-basic-item"
                  :class="{ status: row.status }"
                >
                  <span>{{ row.label }}</span>
                  <strong :class="row.status ? statusClass : undefined">{{ row.value }}</strong>
                </div>
              </div>
            </section>

            <section v-if="customizedBoundaries.length" class="simulation-settings-section">
              <div class="simulation-settings-section-head">
                <h4>自定义边界过程线</h4>
                <span>以下节点使用了时变边界（线性插值）</span>
              </div>
              <div class="simulation-settings-boundary-list">
                <article
                  v-for="item in customizedBoundaries"
                  :key="item.nodeId"
                  class="simulation-settings-boundary-card customized"
                >
                  <div class="simulation-settings-boundary-head">
                    <div>
                      <strong>{{ item.label }}</strong>
                      <p>{{ item.kindLabel }} · {{ item.summary }}</p>
                    </div>
                    <em>已编辑</em>
                  </div>
                  <ReadonlyBoundaryChart
                    :values="item.chartValues"
                    :x-labels="item.chartLabels"
                    :unit="item.unit"
                    :y-axis-label="item.kindLabel"
                    :color="item.chartColor"
                    variant="series"
                  />
                </article>
              </div>
            </section>

            <section class="simulation-settings-section">
              <div class="simulation-settings-section-head">
                <h4>恒定边界</h4>
                <span>未编辑的边界按开始时刻监测值全程保持</span>
              </div>
              <div class="simulation-settings-constant-grid">
                <article
                  v-for="item in constantBoundaries"
                  :key="item.nodeId"
                  class="simulation-settings-constant-card"
                >
                  <strong>{{ item.shortLabel }}</strong>
                  <span>{{ item.kindLabel }}</span>
                  <b>{{ item.summary }}</b>
                </article>
              </div>
            </section>

            <section class="simulation-settings-section">
              <div class="simulation-settings-section-head">
                <h4>闸门开度</h4>
                <span>预演计算采用闸孔出流公式；未保存记录按 100% 全开</span>
              </div>
              <div class="simulation-settings-constant-grid">
                <article
                  v-for="item in gateOpeningRows"
                  :key="item.nodeId"
                  class="simulation-settings-constant-card"
                >
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.widthLabel }}</span>
                  <b>{{ item.openingPercent.toFixed(0) }}%</b>
                </article>
              </div>
            </section>
          </template>
        </div>
      </div>
    </div>
  </Teleport>
</template>
