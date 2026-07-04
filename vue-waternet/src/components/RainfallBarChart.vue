<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

import type { RainfallHistoryPoint, WeatherForecast } from '@/types/platform'

const props = defineProps<{
  points: RainfallHistoryPoint[]
  live?: boolean
  forecast?: WeatherForecast | null
}>()

const hoverIndex = ref<number | null>(null)
const plotWrapRef = ref<HTMLElement | null>(null)
const measuredHeight = ref(92)

const chartPoints = computed(() => (props.live ? props.points.slice(-24) : []))

const forecastPoints = computed(() => {
  if (!props.forecast?.minutely?.length) return []
  // aggregate 5-min minutely into hourly buckets to match history density
  const buckets = new Map<string, { sum: number; count: number }>()
  for (const m of props.forecast.minutely) {
    const t = m.fxTime
    // extract HH:MM, use HH:00 as bucket key
    const hhmm = t.includes('T') ? t.slice(11, 16) : t.slice(0, 5)
    const hourKey = hhmm.slice(0, 2) + ':00'
    const val = parseFloat(m.precip) || 0
    const b = buckets.get(hourKey) ?? { sum: 0, count: 0 }
    b.sum += val
    b.count += 1
    buckets.set(hourKey, b)
  }
  return Array.from(buckets.entries()).map(([hour, b]) => ({
    time: hour,
    rainfall: parseFloat((b.sum).toFixed(2)),
    isForecast: true,
  }))
})

const allDisplayPoints = computed(() => {
  const hist = chartPoints.value.map((p) => ({ ...p, isForecast: false }))
  return [...hist, ...forecastPoints.value]
})

const chartWidth = 320
const chartHeight = computed(() => measuredHeight.value)
const padding = computed(() => ({ top: 8, right: 6, bottom: 4, left: 6 }))
const plotWidth = computed(() => chartWidth - padding.value.left - padding.value.right)
const plotHeight = computed(() => chartHeight.value - padding.value.top - padding.value.bottom)

const rainfallMax = computed(() =>
  Math.max(...allDisplayPoints.value.map((point) => point.rainfall), 0.5),
)

function xAt(index: number) {
  const count = Math.max(allDisplayPoints.value.length, 1)
  const slotWidth = plotWidth.value / count
  return padding.value.left + slotWidth * index + slotWidth / 2
}

function rainfallY(value: number) {
  return padding.value.top + plotHeight.value - (value / rainfallMax.value) * plotHeight.value
}

const bars = computed(() =>
  allDisplayPoints.value.map((point, index) => {
    const count = Math.max(allDisplayPoints.value.length, 1)
    const slotWidth = plotWidth.value / count
    const barWidth = Math.max(3, slotWidth * 0.68)
    const x = padding.value.left + slotWidth * index + (slotWidth - barWidth) / 2
    const y = rainfallY(point.rainfall)
    return {
      x,
      y,
      width: barWidth,
      height: padding.value.top + plotHeight.value - y,
      value: point.rainfall,
      active: hoverIndex.value === index,
      isForecast: point.isForecast,
    }
  }),
)

const axisTicks = computed(() =>
  allDisplayPoints.value.map((point, index) => ({
    index,
    label: formatHourLabel(point.time, index),
    leftPercent: (xAt(index) / chartWidth) * 100,
    visible: shouldShowAxisLabel(index, allDisplayPoints.value.length),
  })),
)

const activePoint = computed(() => {
  if (hoverIndex.value === null) {
    return null
  }
  return allDisplayPoints.value[hoverIndex.value] ?? null
})

const activeX = computed(() => (hoverIndex.value === null ? null : xAt(hoverIndex.value)))

const tooltipStyle = computed(() => {
  if (activeX.value === null) {
    return {}
  }
  const leftPercent = (activeX.value / chartWidth) * 100
  let transform = 'translateX(-50%)'
  if (leftPercent >= 78) {
    transform = 'translateX(calc(-100% - 6px))'
  } else if (leftPercent <= 22) {
    transform = 'translateX(6px)'
  }
  return { left: `${leftPercent}%`, transform }
})

const displayValue = computed(() => {
  if (activePoint.value) {
    return `${activePoint.value.rainfall.toFixed(2)} mm`
  }
  const last = chartPoints.value[chartPoints.value.length - 1]
  return last ? `${last.rainfall.toFixed(2)} mm` : '--'
})

function shouldShowAxisLabel(index: number, total: number) {
  if (total <= 1) {
    return true
  }
  if (index === 0 || index === total - 1) {
    return true
  }
  return index % 3 === 0
}

function formatHourLabel(value: string, index: number) {
  const timeMatch = value.match(/\b(\d{1,2}):\d{2}/)
  const timeValue = timeMatch?.[1]
  if (timeValue) {
    return timeValue.padStart(2, '0')
  }
  const hourMatch = value.match(/(\d{1,2})(?:\D*)$/)
  const hourValue = hourMatch?.[1]
  if (hourValue) {
    return hourValue.padStart(2, '0')
  }
  return String(index).padStart(2, '0')
}

function resolveHoverIndex(clientX: number) {
  const wrap = plotWrapRef.value
  if (!wrap || allDisplayPoints.value.length === 0) {
    hoverIndex.value = null
    return
  }
  const rect = wrap.getBoundingClientRect()
  const relativeX = ((clientX - rect.left) / rect.width) * chartWidth
  let nearest = 0
  let nearestDistance = Number.POSITIVE_INFINITY
  allDisplayPoints.value.forEach((_, index) => {
    const distance = Math.abs(relativeX - xAt(index))
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearest = index
    }
  })
  hoverIndex.value = nearest
}

function onPointerMove(event: MouseEvent) {
  resolveHoverIndex(event.clientX)
}

function onPointerLeave() {
  hoverIndex.value = null
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  if (!plotWrapRef.value) {
    return
  }
  resizeObserver = new ResizeObserver((entries) => {
    const height = entries[0]?.contentRect.height
    if (height && height > 0) {
      measuredHeight.value = Math.max(88, Math.round(height))
    }
  })
  resizeObserver.observe(plotWrapRef.value)
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})
</script>

<template>
  <div class="env-rainfall-chart">
    <div class="env-rainfall-chart-head">
      <span>逐时降雨（含预报）</span>
      <strong>{{ displayValue }}</strong>
    </div>

    <div v-if="!props.live && !forecastPoints.length" class="env-rainfall-empty">离线</div>

    <div v-else-if="allDisplayPoints.length === 0" class="env-rainfall-empty">暂无降雨时序数据</div>

    <div v-else class="env-rainfall-chart-body">
      <div
        ref="plotWrapRef"
        class="env-rainfall-plot-wrap"
        @mousemove="onPointerMove"
        @mouseleave="onPointerLeave"
      >
        <svg class="env-rainfall-svg" :viewBox="`0 0 ${chartWidth} ${chartHeight}`" preserveAspectRatio="none">
          <rect
            :x="padding.left"
            :y="padding.top"
            :width="plotWidth"
            :height="plotHeight"
            class="env-rainfall-plot-bg"
          />

          <line
            :x1="padding.left"
            :x2="padding.left + plotWidth"
            :y1="padding.top + plotHeight"
            :y2="padding.top + plotHeight"
            class="env-rainfall-axis-line"
          />

          <rect
            v-for="(bar, index) in bars"
            :key="`bar-${index}`"
            :x="bar.x"
            :y="bar.y"
            :width="bar.width"
            :height="bar.height"
            :class="['env-rainfall-bar', { active: bar.active, forecast: bar.isForecast }]"
            rx="1"
          />

          <line
            v-if="activeX !== null"
            :x1="activeX"
            :x2="activeX"
            :y1="padding.top"
            :y2="padding.top + plotHeight"
            class="env-rainfall-cursor"
          />
        </svg>

        <div
          v-if="activePoint && activeX !== null"
          class="env-rainfall-tooltip"
          :style="tooltipStyle"
        >
          <strong>{{ activePoint.time }}</strong>
          <span>降雨量 {{ activePoint.rainfall.toFixed(2) }} mm</span>
        </div>
      </div>

      <div class="env-rainfall-axis-labels" aria-hidden="true">
        <span
          v-for="tick in axisTicks"
          v-show="tick.visible"
          :key="`tick-${tick.index}`"
          class="env-rainfall-axis-tick"
          :style="{ left: `${tick.leftPercent}%` }"
        >
          {{ tick.label }}
        </span>
      </div>
    </div>
  </div>
</template>
