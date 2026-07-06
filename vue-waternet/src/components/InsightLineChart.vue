<script setup lang="ts">
import { computed, ref } from 'vue'

interface ChartPoint {
  label: string
  x: number
  value: number
}

const props = defineProps<{
  values: number[]
  labels: string[]
  color?: string
  unit?: string
  valueDigits?: number
  xAxisTitle?: string
  yAxisTitle?: string
}>()

const plot = { width: 560, height: 216, left: 52, right: 544, top: 16, bottom: 192 }
const hoverIndex = ref<number | null>(null)

const strokeColor = computed(() => props.color ?? '#00f2ff')
const digits = computed(() => props.valueDigits ?? 2)

const cleaned = computed<ChartPoint[]>(() => {
  const filtered = props.values
    .map((value, index) => ({ value, label: props.labels[index] ?? String(index) }))
    .filter((item) => Number.isFinite(item.value))
  return filtered.map((item, index) => ({
    label: item.label,
    value: item.value,
    x:
      filtered.length <= 1
        ? (plot.left + plot.right) / 2
        : plot.left + (index / (filtered.length - 1)) * (plot.right - plot.left),
  }))
})

const range = computed(() => {
  const values = cleaned.value.map((point) => point.value)
  if (values.length === 0) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  if (min === max) {
    const pad = Math.max(Math.abs(min) * 0.05, 0.5)
    return { min: min - pad, max: max + pad }
  }
  const pad = (max - min) * 0.12
  return { min: min - pad, max: max + pad }
})

function yForValue(value: number) {
  const span = range.value.max - range.value.min || 1
  return plot.bottom - ((value - range.value.min) / span) * (plot.bottom - plot.top)
}

const chartPoints = computed(() =>
  cleaned.value.map((point) => ({ ...point, y: yForValue(point.value) })),
)

const pathD = computed(() =>
  chartPoints.value
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
    .join(' '),
)

const areaD = computed(() => {
  if (chartPoints.value.length < 2) return ''
  const first = chartPoints.value[0]!
  const last = chartPoints.value[chartPoints.value.length - 1]!
  return `${pathD.value} L ${last.x.toFixed(2)} ${plot.bottom} L ${first.x.toFixed(2)} ${plot.bottom} Z`
})

const gridLines = computed(() => {
  const count = 4
  return Array.from({ length: count + 1 }, (_, index) => {
    const value = range.value.max - ((range.value.max - range.value.min) / count) * index
    return { y: yForValue(value), value }
  })
})

const hovered = computed(() => (hoverIndex.value === null ? undefined : chartPoints.value[hoverIndex.value]))

const axisTicks = computed(() => {
  const points = chartPoints.value
  if (points.length === 0) return []
  const maxTicks = 6
  const step = Math.max(1, Math.ceil(points.length / maxTicks))
  const ticks: { x: number; label: string }[] = []
  for (let index = 0; index < points.length; index += step) {
    ticks.push({ x: points[index]!.x, label: points[index]!.label })
  }
  const last = points[points.length - 1]!
  if (ticks[ticks.length - 1]?.label !== last.label) {
    ticks.push({ x: last.x, label: last.label })
  }
  return ticks
})

function handlePointerMove(event: PointerEvent) {
  const target = event.currentTarget as SVGElement
  const rect = target.getBoundingClientRect()
  const svgX = ((event.clientX - rect.left) / rect.width) * plot.width
  const points = chartPoints.value
  if (points.length === 0) {
    hoverIndex.value = null
    return
  }
  let nearest = 0
  let distance = Number.POSITIVE_INFINITY
  for (let index = 0; index < points.length; index += 1) {
    const next = Math.abs(points[index]!.x - svgX)
    if (next < distance) {
      nearest = index
      distance = next
    }
  }
  hoverIndex.value = nearest
}

function formatValue(value: number) {
  return `${value.toFixed(digits.value)}${props.unit ? ` ${props.unit}` : ''}`
}
</script>

<template>
  <div class="insight-chart-wrap">
    <svg
      class="insight-chart"
      :viewBox="`0 0 ${plot.width} ${plot.height}`"
      preserveAspectRatio="none"
      @pointermove="handlePointerMove"
      @pointerleave="hoverIndex = null"
    >
      <defs>
        <linearGradient :id="`insightFill-${(color ?? 'cyan').replace('#', '')}`" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0" :stop-color="strokeColor" stop-opacity="0.26" />
          <stop offset="1" :stop-color="strokeColor" stop-opacity="0.02" />
        </linearGradient>
      </defs>

      <rect
        :x="plot.left"
        :y="plot.top"
        :width="plot.right - plot.left"
        :height="plot.bottom - plot.top"
        class="insight-chart-bg"
      />

      <g v-for="(line, index) in gridLines" :key="`grid-${index}`">
        <path class="insight-grid-line" :d="`M ${plot.left} ${line.y.toFixed(2)} H ${plot.right}`" />
        <text :x="plot.left - 6" :y="line.y + 3" text-anchor="end" class="insight-axis-value">
          {{ line.value.toFixed(digits) }}
        </text>
      </g>

      <text v-for="(tick, index) in axisTicks" :key="`tick-${index}`" :x="tick.x" :y="plot.bottom + 16" text-anchor="middle" class="insight-axis-tick">
        {{ tick.label }}
      </text>

      <path v-if="areaD" class="insight-area" :d="areaD" :fill="`url(#insightFill-${(color ?? 'cyan').replace('#', '')})`" />
      <path v-if="chartPoints.length > 1" class="insight-line" :d="pathD" :stroke="strokeColor" />
      <circle
        v-for="(point, index) in chartPoints"
        :key="`dot-${index}`"
        class="insight-dot"
        :class="{ active: hoverIndex === index }"
        :cx="point.x"
        :cy="point.y"
        r="2.8"
        :stroke="strokeColor"
      />

      <g v-if="hovered">
        <path class="insight-crosshair" :d="`M ${hovered.x} ${plot.top} V ${plot.bottom}`" />
        <circle class="insight-hover-dot" :cx="hovered.x" :cy="hovered.y" r="4.5" :stroke="strokeColor" />
      </g>

      <text v-if="xAxisTitle" :x="plot.right" :y="plot.height - 2" text-anchor="end" class="insight-axis-caption">
        {{ xAxisTitle }}
      </text>
    </svg>

    <div
      v-if="hovered"
      class="insight-tooltip"
      :style="{ left: `${(hovered.x / plot.width) * 100}%`, top: `${(hovered.y / plot.height) * 100}%` }"
    >
      <strong>{{ hovered.label }}</strong>
      <span>{{ formatValue(hovered.value) }}</span>
    </div>

    <div v-if="chartPoints.length === 0" class="insight-empty">暂无数据</div>
  </div>
</template>
