<script setup lang="ts">
import { computed, ref } from 'vue'

import { WARNING_THRESHOLDS } from '@/constants/warningThresholds'

export interface HydroChartSeries {
  key: string
  label: string
  color: string
  values: number[]
  unit: string
}

const props = withDefaults(
  defineProps<{
    series: HydroChartSeries[]
    xLabels: string[]
    xTickLabels?: string[]
    mode: 'level' | 'flow' | 'both'
    chartHeight?: number
    emptyText?: string
    warningActive?: boolean
  }>(),
  {
    chartHeight: 200,
    emptyText: '暂无数据',
    warningActive: false,
  },
)

const chartWidth = 640
const padding = { top: 22, right: 24, bottom: 34, left: 76 }

const hoveredIndex = ref<number | null>(null)
const plotRef = ref<HTMLDivElement | null>(null)

const visibleSeries = computed(() => {
  if (props.mode === 'level') {
    return props.series.filter((item) => item.key === 'level')
  }
  if (props.mode === 'flow') {
    return props.series.filter((item) => item.key === 'flow')
  }
  return props.series
})

const pointCount = computed(() => props.xLabels.length)

const innerWidth = computed(() => chartWidth - padding.left - padding.right)
const innerHeight = computed(() => props.chartHeight - padding.top - padding.bottom)

function seriesPlot(series: HydroChartSeries) {
  const values = series.values
  if (!values.length) {
    return { points: [] as { x: number; y: number; value: number }[], min: 0, max: 0 }
  }
  let min = Math.min(...values)
  let max = Math.max(...values)
  if (props.warningActive) {
    if (series.key === 'level') {
      max = Math.max(max, WARNING_THRESHOLDS.waterLevelUpper)
    }
    if (series.key === 'flow') {
      max = Math.max(max, WARNING_THRESHOLDS.flowUpper)
    }
  }
  const range = Math.max(max - min, 0.001)
  const points = values.map((value, index) => {
    const x =
      padding.left +
      (values.length === 1 ? innerWidth.value / 2 : (index / (values.length - 1)) * innerWidth.value)
    const y = padding.top + (1 - (value - min) / range) * innerHeight.value
    return { x, y, value }
  })
  return { points, min, max }
}

function thresholdLinesForSeries(seriesKey: string, min: number, max: number) {
  if (!props.warningActive) {
    return [] as { y: number; label: string }[]
  }
  const range = Math.max(max - min, 0.001)
  const toY = (value: number) => padding.top + (1 - (value - min) / range) * innerHeight.value
  if (seriesKey === 'level') {
    return [{ y: toY(WARNING_THRESHOLDS.waterLevelUpper), label: `${WARNING_THRESHOLDS.waterLevelUpper}` }]
  }
  if (seriesKey === 'flow') {
    return [{ y: toY(WARNING_THRESHOLDS.flowUpper), label: `${WARNING_THRESHOLDS.flowUpper}` }]
  }
  return []
}

const plottedSeries = computed(() =>
  visibleSeries.value.map((series) => {
    const plot = seriesPlot(series)
    return {
      ...series,
      plot,
      thresholdLines: thresholdLinesForSeries(series.key, plot.min, plot.max),
    }
  }),
)

const yAxisLabel = computed(() => {
  const primary = plottedSeries.value[0]
  if (!primary) {
    return ''
  }
  return primary.unit
})

const yTicks = computed(() => {
  const primary = plottedSeries.value[0]
  if (!primary || !primary.plot.points.length) {
    return [] as { y: number; label: string }[]
  }
  const { min, max } = primary.plot
  return [max, (max + min) / 2, min].map((value) => {
    const y = padding.top + (1 - (value - min) / Math.max(max - min, 0.001)) * innerHeight.value
    const decimals = primary.unit === 'm' ? 2 : 1
    return { y, label: `${value.toFixed(decimals)}` }
  })
})

const xTicks = computed(() => {
  if (!pointCount.value) {
    return [] as { x: number; label: string }[]
  }
  const labels = props.xTickLabels?.length ? props.xTickLabels : props.xLabels
  const tickCount = Math.min(5, labels.length)
  return Array.from({ length: tickCount }, (_, index) => {
    const dataIndex = tickCount === 1 ? 0 : Math.round((index / (tickCount - 1)) * (labels.length - 1))
    const ratio = labels.length === 1 ? 0 : dataIndex / (labels.length - 1)
    return {
      x: padding.left + ratio * innerWidth.value,
      label: labels[dataIndex] ?? '',
    }
  })
})

const hoverGuideX = computed(() => {
  if (hoveredIndex.value === null || pointCount.value <= 1) {
    return null
  }
  const ratio = hoveredIndex.value / (pointCount.value - 1)
  return padding.left + ratio * innerWidth.value
})

const tooltipRows = computed(() => {
  if (hoveredIndex.value === null) {
    return null
  }
  const index = hoveredIndex.value
  return {
    title: props.xLabels[index] ?? '',
    items: visibleSeries.value.map((series) => ({
      label: series.label,
      color: series.color,
      value: series.values[index],
      unit: series.unit,
    })),
  }
})

function resolveHoverIndex(clientX: number) {
  const element = plotRef.value
  if (!element || pointCount.value <= 1) {
    hoveredIndex.value = pointCount.value === 1 ? 0 : null
    return
  }
  const rect = element.getBoundingClientRect()
  const ratio = (clientX - rect.left) / rect.width
  const clamped = Math.min(1, Math.max(0, ratio))
  hoveredIndex.value = Math.round(clamped * (pointCount.value - 1))
}

function onMouseMove(event: MouseEvent) {
  resolveHoverIndex(event.clientX)
}

function onMouseLeave() {
  hoveredIndex.value = null
}

const hasData = computed(() => pointCount.value > 0 && visibleSeries.value.some((series) => series.values.length))
</script>

<template>
  <div class="interactive-hydro-chart">
    <div
      v-if="hasData"
      ref="plotRef"
      class="interactive-hydro-chart-plot"
      @mousemove="onMouseMove"
      @mouseleave="onMouseLeave"
    >
      <svg
        class="interactive-hydro-chart-svg"
        :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
        preserveAspectRatio="xMidYMid meet"
        role="img"
      >
        <rect
          :x="padding.left"
          :y="padding.top"
          :width="innerWidth"
          :height="innerHeight"
          class="reach-profile-grid-bg"
        />

        <line
          v-for="tick in 4"
          :key="`grid-${tick}`"
          :x1="padding.left"
          :x2="chartWidth - padding.right"
          :y1="padding.top + ((tick - 1) / 3) * innerHeight"
          :y2="padding.top + ((tick - 1) / 3) * innerHeight"
          class="reach-profile-grid-line"
        />

        <g v-for="(tick, index) in yTicks" :key="`y-${index}`">
          <line
            :x1="padding.left - 4"
            :x2="padding.left"
            :y1="tick.y"
            :y2="tick.y"
            class="reach-profile-axis-tick"
          />
          <text :x="padding.left - 8" :y="tick.y + 4" text-anchor="end" class="reach-profile-axis-label">
            {{ tick.label }}
          </text>
        </g>

        <text :x="padding.left - 8" :y="12" text-anchor="end" class="reach-profile-axis-unit">
          {{ yAxisLabel }}
        </text>

        <g v-for="item in plottedSeries" :key="`threshold-${item.key}`">
          <template v-if="warningActive">
            <line
              v-for="(threshold, tIndex) in item.thresholdLines"
              :key="`${item.key}-threshold-${tIndex}`"
              :x1="padding.left"
              :x2="chartWidth - padding.right"
              :y1="threshold.y"
              :y2="threshold.y"
              class="interactive-hydro-chart-threshold-line"
              :class="item.key"
            />
            <text
              v-for="(threshold, tIndex) in item.thresholdLines"
              :key="`${item.key}-threshold-label-${tIndex}`"
              :x="padding.left + 4"
              :y="threshold.y - 3"
              class="interactive-hydro-chart-threshold-label"
            >
              {{ threshold.label }}
            </text>
          </template>
        </g>

        <g v-for="item in plottedSeries" :key="item.key">
          <polyline
            :points="item.plot.points.map((point) => `${point.x},${point.y}`).join(' ')"
            class="reach-profile-line"
            :class="item.key"
            :stroke="item.color"
          />
          <circle
            v-for="(point, index) in item.plot.points"
            :key="`${item.key}-${index}`"
            :cx="point.x"
            :cy="point.y"
            r="3.2"
            class="interactive-hydro-chart-dot"
            :class="{ active: hoveredIndex === index }"
            :fill="item.color"
          />
        </g>

        <line
          v-if="hoverGuideX !== null"
          :x1="hoverGuideX"
          :x2="hoverGuideX"
          :y1="padding.top"
          :y2="chartHeight - padding.bottom"
          class="interactive-hydro-chart-guide"
        />

        <g v-for="tick in xTicks" :key="`x-${tick.label}-${tick.x}`">
          <line
            :x1="tick.x"
            :x2="tick.x"
            :y1="chartHeight - padding.bottom"
            :y2="chartHeight - padding.bottom + 4"
            class="reach-profile-axis-tick"
          />
          <text :x="tick.x" :y="chartHeight - 10" text-anchor="middle" class="reach-profile-axis-label">
            {{ tick.label }}
          </text>
        </g>
      </svg>

      <div v-if="tooltipRows" class="interactive-hydro-chart-tooltip">
        <strong>{{ tooltipRows.title }}</strong>
        <div v-for="item in tooltipRows.items" :key="item.label" class="interactive-hydro-chart-tooltip-row">
          <span class="dot" :style="{ background: item.color }"></span>
          <span>{{ item.label }}</span>
          <b>{{ item.value?.toFixed(item.unit === 'm' ? 2 : 2) }} {{ item.unit }}</b>
        </div>
      </div>
    </div>

    <div v-else class="model-result-empty">{{ emptyText }}</div>
  </div>
</template>
