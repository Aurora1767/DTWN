<script setup lang="ts">
import { computed, ref } from 'vue'

import type { WaterHistoryPoint, WaterStationSnapshot } from '@/types/platform'

const props = defineProps<{
  station: WaterStationSnapshot | null
  history: WaterHistoryPoint[]
  visible: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const hoverIndex = ref<number | null>(null)

const chartPoints = computed(() =>
  [...props.history]
    .sort((left, right) => left.date.localeCompare(right.date))
    .slice(-5),
)

const chartWidth = 640
const chartHeight = 280
const padding = { top: 24, right: 48, bottom: 36, left: 48 }
const plotWidth = chartWidth - padding.left - padding.right
const plotHeight = chartHeight - padding.top - padding.bottom

const waterLevelMin = computed(() =>
  Math.min(...chartPoints.value.map((point) => point.waterLevel), Number.POSITIVE_INFINITY),
)
const waterLevelMax = computed(() =>
  Math.max(...chartPoints.value.map((point) => point.waterLevel), waterLevelMin.value + 0.01),
)
const flowMax = computed(() => Math.max(...chartPoints.value.map((point) => point.flowRate), 1))

function xAt(index: number) {
  const count = Math.max(chartPoints.value.length - 1, 1)
  return padding.left + (index / count) * plotWidth
}

function waterLevelY(value: number) {
  const range = waterLevelMax.value - waterLevelMin.value
  const normalized = range === 0 ? 0.5 : (value - waterLevelMin.value) / range
  return padding.top + plotHeight - normalized * plotHeight
}

function flowY(value: number) {
  return padding.top + plotHeight - (value / flowMax.value) * plotHeight
}

const waterLevelBars = computed(() =>
  chartPoints.value.map((point, index) => {
    const x = xAt(index)
    const barWidth = Math.min(28, plotWidth / Math.max(chartPoints.value.length, 1) - 8)
    const y = waterLevelY(point.waterLevel)
    return {
      x: x - barWidth / 2,
      y,
      width: barWidth,
      height: padding.top + plotHeight - y,
      value: point.waterLevel,
    }
  }),
)

const flowLine = computed(() =>
  chartPoints.value.map((point, index) => `${xAt(index)},${flowY(point.flowRate)}`).join(' '),
)

const flowDots = computed(() =>
  chartPoints.value.map((point, index) => ({
    cx: xAt(index),
    cy: flowY(point.flowRate),
    value: point.flowRate,
  })),
)

const waterLevelTicks = computed(() => {
  const min = waterLevelMin.value
  const max = waterLevelMax.value
  return [min, (min + max) / 2, max]
})
const flowTicks = computed(() => [0, flowMax.value / 2, flowMax.value])

const activePoint = computed(() => {
  if (hoverIndex.value === null) {
    return null
  }
  return chartPoints.value[hoverIndex.value] ?? null
})

const activeX = computed(() => (hoverIndex.value === null ? null : xAt(hoverIndex.value)))

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function onPointerMove(event: MouseEvent) {
  const svg = event.currentTarget as SVGSVGElement
  const rect = svg.getBoundingClientRect()
  const relativeX = ((event.clientX - rect.left) / rect.width) * chartWidth
  if (chartPoints.value.length === 0) {
    hoverIndex.value = null
    return
  }
  let nearest = 0
  let nearestDistance = Number.POSITIVE_INFINITY
  chartPoints.value.forEach((_, index) => {
    const distance = Math.abs(relativeX - xAt(index))
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearest = index
    }
  })
  hoverIndex.value = nearest
}

function onPointerLeave() {
  hoverIndex.value = null
}

function closeModal() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible && station" class="station-history-overlay" @click.self="closeModal">
      <section class="station-history-modal" role="dialog" aria-modal="true">
        <header class="station-history-header">
          <div>
            <span class="station-history-eyebrow">HYDRO STATION</span>
            <h3>{{ station.stationName }}</h3>
            <p>最近 5 天水位与流量过程线 · 08:00 观测</p>
          </div>
          <button type="button" class="station-history-close" @click="closeModal">关闭</button>
        </header>

        <div v-if="loading" class="station-history-loading">历史数据加载中…</div>

        <div v-else-if="chartPoints.length === 0" class="station-history-loading">暂无历史数据</div>

        <div v-else class="station-history-chart-wrap">
          <svg
            class="station-history-chart"
            :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
            @mousemove="onPointerMove"
            @mouseleave="onPointerLeave"
          >
            <rect
              :x="padding.left"
              :y="padding.top"
              :width="plotWidth"
              :height="plotHeight"
              class="station-history-plot-bg"
            />

            <g v-for="(tick, index) in waterLevelTicks" :key="`level-${index}`">
              <line
                :x1="padding.left"
                :x2="padding.left + plotWidth"
                :y1="waterLevelY(tick)"
                :y2="waterLevelY(tick)"
                class="station-history-grid"
              />
              <text :x="8" :y="waterLevelY(tick) + 4" class="station-history-axis-label level">
                {{ tick.toFixed(2) }}
              </text>
            </g>

            <g v-for="(tick, index) in flowTicks" :key="`flow-${index}`">
              <text
                :x="chartWidth - 8"
                :y="flowY(tick) + 4"
                text-anchor="end"
                class="station-history-axis-label flow"
              >
                {{ tick.toFixed(0) }}
              </text>
            </g>

            <text :x="8" :y="14" class="station-history-axis-title level">水位 m</text>
            <text :x="chartWidth - 8" :y="14" text-anchor="end" class="station-history-axis-title flow">
              流量 m³/s
            </text>

            <rect
              v-for="(bar, index) in waterLevelBars"
              :key="`bar-${index}`"
              :x="bar.x"
              :y="bar.y"
              :width="bar.width"
              :height="bar.height"
              class="station-history-bar"
              :class="{ active: hoverIndex === index }"
            />

            <polyline :points="flowLine" class="station-history-flow-line" />

            <circle
              v-for="(dot, index) in flowDots"
              :key="`dot-${index}`"
              :cx="dot.cx"
              :cy="dot.cy"
              r="4.5"
              class="station-history-flow-dot"
              :class="{ active: hoverIndex === index }"
            />

            <line
              v-if="activeX !== null"
              :x1="activeX"
              :x2="activeX"
              :y1="padding.top"
              :y2="padding.top + plotHeight"
              class="station-history-crosshair"
            />

            <text
              v-for="(point, index) in chartPoints"
              :key="`label-${index}`"
              :x="xAt(index)"
              :y="chartHeight - 8"
              text-anchor="middle"
              class="station-history-date-label"
            >
              {{ formatDate(point.date) }}
            </text>
          </svg>

          <div
            v-if="activePoint && activeX !== null"
            class="station-history-tooltip"
            :style="{ left: `${(activeX / chartWidth) * 100}%` }"
          >
            <strong>{{ formatDate(activePoint.date) }}</strong>
            <span>水位 {{ activePoint.waterLevel.toFixed(2) }} m</span>
            <span>流量 {{ activePoint.flowRate.toFixed(1) }} m³/s</span>
          </div>
        </div>

        <footer v-if="!loading && chartPoints.length > 0" class="station-history-legend">
          <span><i class="legend-bar" /> 日水位</span>
          <span><i class="legend-line" /> 日流量</span>
          <span>移动鼠标查看各日详情</span>
        </footer>
      </section>
    </div>
  </Teleport>
</template>
