<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import type { SimulationBoundaryDefinition } from '@/constants/simulationBoundaries'
import {
  buildDefaultControlPoints,
  formatBoundaryTimeLabel,
  type BoundaryControlPoint,
} from '@/utils/boundaryInterpolation'

const props = defineProps<{
  open: boolean
  station: SimulationBoundaryDefinition
  durationHours: number
  simulationStartIso: string
  defaultValue: number
  controlPoints: BoundaryControlPoint[]
  customized: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [points: BoundaryControlPoint[], customized: boolean]
  reset: []
}>()

const draftPoints = ref<BoundaryControlPoint[]>([])
const selectedIndex = ref<number | null>(null)

watch(
  () => [props.open, props.controlPoints, props.defaultValue, props.durationHours] as const,
  () => {
    if (!props.open) {
      return
    }
    draftPoints.value = props.customized
      ? props.controlPoints.map((point) => ({ ...point }))
      : buildDefaultControlPoints(props.durationHours, props.defaultValue)
    selectedIndex.value = null
  },
  { immediate: true },
)

const chartWidth = 640
const chartHeight = 280
const padding = { top: 24, right: 24, bottom: 40, left: 72 }
const innerWidth = chartWidth - padding.left - padding.right
const innerHeight = chartHeight - padding.top - padding.bottom

const sortedPoints = computed(() => [...draftPoints.value].sort((left, right) => left.t - right.t))

const valueRange = computed(() => {
  const values = sortedPoints.value.map((point) => point.value)
  const min = Math.min(...values, props.defaultValue)
  const max = Math.max(...values, props.defaultValue)
  const paddingValue = Math.max((max - min) * 0.12, props.station.valueKind === 'level' ? 0.05 : 5)
  return {
    min: min - paddingValue,
    max: max + paddingValue,
  }
})

const plottedPoints = computed(() => {
  const { min, max } = valueRange.value
  const range = Math.max(max - min, 0.001)
  const duration = Math.max(props.durationHours, 0.001)

  return sortedPoints.value.map((point, index) => {
    const x = padding.left + (point.t / duration) * innerWidth
    const y = padding.top + (1 - (point.value - min) / range) * innerHeight
    return { ...point, x, y, index }
  })
})

const linePath = computed(() => {
  if (plottedPoints.value.length < 2) {
    return ''
  }
  return plottedPoints.value.map((point) => `${point.x},${point.y}`).join(' ')
})

const previewPath = computed(() => {
  const duration = Math.max(props.durationHours, 0.001)
  const { min, max } = valueRange.value
  const range = Math.max(max - min, 0.001)
  const samples = 40
  const points: string[] = []

  for (let index = 0; index <= samples; index += 1) {
    const t = (index / samples) * duration
    const value = interpolateDraft(t)
    const x = padding.left + (t / duration) * innerWidth
    const y = padding.top + (1 - (value - min) / range) * innerHeight
    points.push(`${x},${y}`)
  }

  return points.join(' ')
})

const yTicks = computed(() => {
  const { min, max } = valueRange.value
  const decimals = props.station.valueKind === 'level' ? 2 : 1
  return [max, (max + min) / 2, min].map((value) => {
    const y = padding.top + (1 - (value - min) / Math.max(max - min, 0.001)) * innerHeight
    return { y, label: value.toFixed(decimals) }
  })
})

const xTicks = computed(() => {
  const duration = Math.max(props.durationHours, 0.001)
  const tickCount = Math.min(5, Math.max(2, Math.ceil(duration)))
  return Array.from({ length: tickCount }, (_, index) => {
    const ratio = tickCount === 1 ? 0 : index / (tickCount - 1)
    const t = ratio * duration
    return {
      x: padding.left + ratio * innerWidth,
      label: formatBoundaryTimeLabel(props.simulationStartIso, t),
    }
  })
})

const isConstant = computed(() => {
  if (draftPoints.value.length <= 1) {
    return true
  }
  const values = draftPoints.value.map((point) => point.value)
  const firstValue = values[0] ?? props.defaultValue
  return values.every((value) => Math.abs(value - firstValue) < 1e-6)
})

function interpolateDraft(tHours: number): number {
  const points = sortedPoints.value
  const first = points[0]
  if (!first) {
    return props.defaultValue
  }
  if (tHours <= first.t) {
    return first.value
  }
  const last = points[points.length - 1]
  if (!last) {
    return first.value
  }
  if (tHours >= last.t) {
    return last.value
  }
  for (let index = 0; index < points.length - 1; index += 1) {
    const start = points[index]
    const end = points[index + 1]
    if (!start || !end) {
      continue
    }
    if (tHours >= start.t && tHours <= end.t) {
      const span = end.t - start.t
      if (span <= 0) {
        return end.value
      }
      const ratio = (tHours - start.t) / span
      return start.value + ratio * (end.value - start.value)
    }
  }
  return last.value
}

function toChartPoint(clientX: number, clientY: number, element: HTMLElement) {
  const rect = element.getBoundingClientRect()
  const ratioX = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
  const ratioY = Math.min(1, Math.max(0, (clientY - rect.top) / rect.height))
  const duration = Math.max(props.durationHours, 0.001)
  const { min, max } = valueRange.value
  const range = Math.max(max - min, 0.001)

  return {
    t: ratioX * duration,
    value: max - ratioY * range,
  }
}

function onPlotClick(event: MouseEvent) {
  const element = event.currentTarget as HTMLElement
  const point = toChartPoint(event.clientX, event.clientY, element)
  const duration = Math.max(props.durationHours, 0.001)

  if (point.t <= 0.02 || point.t >= duration - 0.02) {
    return
  }

  const tooClose = draftPoints.value.some(
    (existing) => Math.abs(existing.t - point.t) < duration * 0.04,
  )
  if (tooClose) {
    return
  }

  draftPoints.value = [...draftPoints.value, point]
  selectedIndex.value = draftPoints.value.length - 1
}

function onPointPointerDown(index: number, event: PointerEvent) {
  event.stopPropagation()
  selectedIndex.value = index
  const element = (event.currentTarget as SVGElement).closest('.boundary-editor-plot') as HTMLElement
  if (!element) {
    return
  }

  const move = (moveEvent: PointerEvent) => {
    const point = toChartPoint(moveEvent.clientX, moveEvent.clientY, element)
    const duration = Math.max(props.durationHours, 0.001)
    const clampedT =
      index === 0
        ? 0
        : index === draftPoints.value.length - 1 && draftPoints.value.length > 1
          ? duration
          : Math.min(duration - 0.02, Math.max(0.02, point.t))

    draftPoints.value = draftPoints.value.map((existing, existingIndex) =>
      existingIndex === index ? { t: clampedT, value: point.value } : existing,
    )
  }

  const up = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', up)
    draftPoints.value = [...draftPoints.value].sort((left, right) => left.t - right.t)
  }

  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', up)
}

function removeSelectedPoint() {
  if (selectedIndex.value === null) {
    return
  }
  const index = selectedIndex.value
  if (index === 0 || index === draftPoints.value.length - 1) {
    return
  }
  draftPoints.value = draftPoints.value.filter((_, pointIndex) => pointIndex !== index)
  selectedIndex.value = null
}

function handleSave() {
  const duration = Math.max(props.durationHours, 0.001)
  const normalized = [...draftPoints.value]
    .sort((left, right) => left.t - right.t)
    .map((point, index, array) => {
      if (index === 0) {
        return { t: 0, value: point.value }
      }
      if (index === array.length - 1) {
        return { t: duration, value: point.value }
      }
      return point
    })

  emit('save', normalized, !isConstant.value)
}

function handleReset() {
  draftPoints.value = buildDefaultControlPoints(props.durationHours, props.defaultValue)
  selectedIndex.value = null
  emit('reset')
}
</script>

<template>
  <div v-if="open" class="boundary-editor-overlay" @click.self="emit('close')">
    <div class="boundary-editor-modal" role="dialog" :aria-label="`${station.label}边界编辑`">
      <header class="boundary-editor-header">
        <div>
          <p class="boundary-editor-eyebrow">BOUNDARY</p>
          <h3>{{ station.label }}</h3>
          <p class="boundary-editor-subtitle">
            点击图表添加控制点，拖拽调整；未编辑时按预演开始时刻监测值恒定
          </p>
        </div>
        <button class="boundary-editor-close" type="button" @click="emit('close')">×</button>
      </header>

      <div class="boundary-editor-meta">
        <span>纵轴：{{ station.valueKind === 'level' ? '水位' : '流量' }} ({{ station.unit }})</span>
        <span>时长：{{ durationHours.toFixed(1) }} 小时</span>
        <span>默认：{{ defaultValue.toFixed(station.valueKind === 'level' ? 2 : 1) }} {{ station.unit }}</span>
      </div>

      <div class="boundary-editor-plot" @click="onPlotClick">
        <svg
          class="boundary-editor-svg"
          :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
          preserveAspectRatio="xMidYMid meet"
        >
          <rect
            :x="padding.left"
            :y="padding.top"
            :width="innerWidth"
            :height="innerHeight"
            class="boundary-editor-grid-bg"
          />

          <line
            v-for="tick in 4"
            :key="`grid-${tick}`"
            :x1="padding.left"
            :x2="chartWidth - padding.right"
            :y1="padding.top + ((tick - 1) / 3) * innerHeight"
            :y2="padding.top + ((tick - 1) / 3) * innerHeight"
            class="boundary-editor-grid-line"
          />

          <g v-for="(tick, index) in yTicks" :key="`y-${index}`">
            <text :x="padding.left - 8" :y="tick.y + 4" text-anchor="end" class="boundary-editor-axis-label">
              {{ tick.label }}
            </text>
          </g>

          <text :x="padding.left - 8" :y="12" text-anchor="end" class="boundary-editor-axis-unit">
            {{ station.unit }}
          </text>

          <polyline :points="previewPath" class="boundary-editor-preview-line" />
          <polyline v-if="linePath" :points="linePath" class="boundary-editor-control-line" />

          <circle
            v-for="point in plottedPoints"
            :key="`${point.t}-${point.value}`"
            :cx="point.x"
            :cy="point.y"
            r="6"
            class="boundary-editor-point"
            :class="{ selected: selectedIndex === point.index }"
            @pointerdown="onPointPointerDown(point.index, $event)"
          />

          <g v-for="tick in xTicks" :key="`x-${tick.label}`">
            <text
              :x="tick.x"
              :y="chartHeight - 12"
              text-anchor="middle"
              class="boundary-editor-axis-label"
            >
              {{ tick.label }}
            </text>
          </g>
        </svg>
      </div>

      <p class="boundary-editor-hint">
        左/右端点固定对应预演起止时刻；中间点可删除。其余时刻按线性插值补全。
      </p>

      <div class="boundary-editor-actions">
        <button
          type="button"
          class="boundary-editor-action secondary"
          :disabled="selectedIndex === null || selectedIndex === 0 || selectedIndex === draftPoints.length - 1"
          @click="removeSelectedPoint"
        >
          删除选中点
        </button>
        <button type="button" class="boundary-editor-action secondary" @click="handleReset">恢复恒定</button>
        <button type="button" class="boundary-editor-action primary" @click="handleSave">确定</button>
      </div>
    </div>
  </div>
</template>
