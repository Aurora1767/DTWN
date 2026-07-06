<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    values: number[]
    xLabels: string[]
    unit: string
    yAxisLabel: string
    color?: string
    chartHeight?: number
    variant?: 'series' | 'constant'
  }>(),
  {
    color: '#00f2ff',
    chartHeight: 160,
    variant: 'series',
  },
)

const chartWidth = 560
const padding = { top: 18, right: 18, bottom: 32, left: 56 }
const innerWidth = chartWidth - padding.left - padding.right
const innerHeight = computed(() => props.chartHeight - padding.top - padding.bottom)

const plot = computed(() => {
  if (!props.values.length) {
    return { points: [] as { x: number; y: number; value: number }[], min: 0, max: 0, path: '' }
  }

  const numericValues = props.values.map((value) => Number(value))
  let min = Math.min(...numericValues)
  let max = Math.max(...numericValues)
  const paddingValue = Math.max((max - min) * 0.12, props.unit === 'm' ? 0.05 : 5)
  min -= paddingValue
  max += paddingValue
  const range = Math.max(max - min, 0.001)
  const plotHeight = innerHeight.value

  const points = numericValues.map((value, index) => {
    const x =
      padding.left +
      (numericValues.length === 1
        ? innerWidth / 2
        : (index / (numericValues.length - 1)) * innerWidth)
    const y = padding.top + (1 - (value - min) / range) * plotHeight
    return { x, y, value }
  })

  const path =
    points.length > 0
      ? `M ${points.map((point) => `${point.x} ${point.y}`).join(' L ')}`
      : ''

  return {
    points,
    min,
    max,
    path,
  }
})

const yTicks = computed(() => {
  const { min, max } = plot.value
  const decimals = props.unit === 'm' ? 2 : 1
  return [max, (max + min) / 2, min].map((value) => {
    const y = padding.top + (1 - (value - min) / Math.max(max - min, 0.001)) * innerHeight.value
    return { y, label: value.toFixed(decimals) }
  })
})

const xTicks = computed(() => {
  if (!props.xLabels.length) {
    return [] as { x: number; label: string }[]
  }
  const tickCount = Math.min(5, props.xLabels.length)
  return Array.from({ length: tickCount }, (_, index) => {
    const dataIndex = tickCount === 1 ? 0 : Math.round((index / (tickCount - 1)) * (props.xLabels.length - 1))
    const ratio = props.xLabels.length === 1 ? 0 : dataIndex / (props.xLabels.length - 1)
    return {
      x: padding.left + ratio * innerWidth,
      label: props.xLabels[dataIndex] ?? '',
    }
  })
})
</script>

<template>
  <div class="readonly-boundary-chart">
    <svg
      class="readonly-boundary-chart-svg"
      :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
      preserveAspectRatio="xMidYMid meet"
      role="img"
      :aria-label="`${yAxisLabel}过程线`"
    >
      <rect
        :x="padding.left"
        :y="padding.top"
        :width="innerWidth"
        :height="innerHeight"
        class="readonly-boundary-chart-bg"
      />

      <line
        v-for="tick in 4"
        :key="`grid-${tick}`"
        :x1="padding.left"
        :x2="chartWidth - padding.right"
        :y1="padding.top + ((tick - 1) / 3) * innerHeight"
        :y2="padding.top + ((tick - 1) / 3) * innerHeight"
        class="readonly-boundary-chart-grid"
      />

      <g v-for="(tick, index) in yTicks" :key="`y-${index}`">
        <text :x="padding.left - 8" :y="tick.y + 4" text-anchor="end" class="readonly-boundary-chart-axis">
          {{ tick.label }}
        </text>
      </g>

      <text :x="padding.left - 8" :y="10" text-anchor="end" class="readonly-boundary-chart-unit">
        {{ unit }}
      </text>

      <path
        v-if="plot.path"
        :d="plot.path"
        class="readonly-boundary-chart-line"
        :class="`variant-${variant}`"
        :stroke="color"
        fill="none"
      />

      <circle
        v-for="(point, index) in plot.points"
        :key="`point-${index}`"
        :cx="point.x"
        :cy="point.y"
        :r="variant === 'constant' ? 0 : 3.2"
        :fill="color"
        class="readonly-boundary-chart-dot"
      />

      <g v-for="tick in xTicks" :key="`x-${tick.label}-${tick.x}`">
        <text
          :x="tick.x"
          :y="chartHeight - 8"
          text-anchor="middle"
          class="readonly-boundary-chart-axis"
        >
          {{ tick.label }}
        </text>
      </g>
    </svg>
  </div>
</template>
