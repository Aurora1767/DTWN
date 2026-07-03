<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

import { fetchHydroScenarioLatest } from '@/services/api'
import type { HydroChannelKey, HydroScenarioSnapshot } from '@/types/platform'

interface HydroPoint {
  value: number
  timestamp: string
}

interface HydroChannelConfig {
  key: HydroChannelKey
  label: string
  unit: string
  precision: number
}

const channels: HydroChannelConfig[] = [
  { key: 'taihu', label: '太湖水位', unit: 'm', precision: 2 },
  { key: 'canal-north', label: '京杭大运河北流量', unit: 'm3/s', precision: 2 },
  { key: 'canal-south', label: '京杭大运河南水位', unit: 'm', precision: 2 },
]
const fallbackChannel = channels[0]!
const plot = { width: 1200, height: 288, left: 42, right: 1168, top: 18, bottom: 250 }
const storageKey = 'waternet:hydro-process-series'
const zoomWindows = [12, 24, 48, 96, Number.POSITIVE_INFINITY]

const activeKey = ref<HydroChannelKey>('taihu')
const zoomIndex = ref(1)
const hoverIndex = ref<number | null>(null)
const loading = ref(false)
const lastUpdatedAt = ref('')
const series = reactive<Record<HydroChannelKey, HydroPoint[]>>({
  taihu: [],
  'canal-north': [],
  'canal-south': [],
})

let timer: ReturnType<typeof setInterval> | undefined

const activeChannel = computed<HydroChannelConfig>(
  () => channels.find((item) => item.key === activeKey.value) ?? fallbackChannel,
)
const activeSeries = computed(() => series[activeKey.value])
const visibleSeries = computed(() => {
  const windowSize = zoomWindows[zoomIndex.value] ?? Number.POSITIVE_INFINITY
  return Number.isFinite(windowSize) ? activeSeries.value.slice(-windowSize) : activeSeries.value
})
const range = computed(() => {
  const values = visibleSeries.value.map((point) => point.value)
  if (values.length === 0) return { min: 0, max: 1 }
  const min = Math.min(...values)
  const max = Math.max(...values)
  if (min === max) {
    const pad = Math.max(Math.abs(min) * 0.02, activeKey.value === 'canal-north' ? 2 : 0.1)
    return { min: min - pad, max: max + pad }
  }
  const pad = (max - min) * 0.14
  return { min: min - pad, max: max + pad }
})
const chartPoints = computed(() =>
  visibleSeries.value.map((point, index, list) => ({
    ...point,
    x: xForIndex(index, list.length),
    y: yForValue(point.value),
  })),
)
const pathD = computed(() =>
  chartPoints.value
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(2)} ${point.y.toFixed(2)}`)
    .join(' '),
)
const latestValues = computed(
  () =>
    Object.fromEntries(
      channels.map((channel) => [channel.key, series[channel.key][series[channel.key].length - 1]]),
    ) as Record<HydroChannelKey, HydroPoint | undefined>,
)
const hoveredPoint = computed(() => (hoverIndex.value === null ? undefined : chartPoints.value[hoverIndex.value]))
const zoomText = computed(() => {
  const windowSize = zoomWindows[zoomIndex.value] ?? Number.POSITIVE_INFINITY
  return Number.isFinite(windowSize) ? `近 ${windowSize} 点` : '全部'
})

onMounted(() => {
  restoreSeries()
  void refreshLatest()
  timer = setInterval(() => {
    void refreshLatest()
  }, 60_000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
})

async function refreshLatest() {
  loading.value = true
  try {
    appendSnapshot(await fetchHydroScenarioLatest())
    persistSeries()
  } finally {
    loading.value = false
  }
}

function appendSnapshot(snapshot: HydroScenarioSnapshot) {
  for (const channel of channels) {
    const value = snapshot.channels[channel.key]
    if (!value) continue
    const points = series[channel.key]
    const last = points[points.length - 1]
    if (last?.timestamp === value.timestamp && last.value === value.value) continue
    points.push({ value: value.value, timestamp: value.timestamp })
    if (points.length > 240) points.splice(0, points.length - 240)
    lastUpdatedAt.value = value.timestamp
  }
}

function restoreSeries() {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) ?? '{}') as Partial<Record<HydroChannelKey, HydroPoint[]>>
    for (const channel of channels) {
      const points = stored[channel.key]
      if (Array.isArray(points)) {
        series[channel.key].push(...points.filter(isHydroPoint).slice(-240))
      }
    }
  } catch {
    localStorage.removeItem(storageKey)
  }
}

function persistSeries() {
  localStorage.setItem(
    storageKey,
    JSON.stringify({
      taihu: series.taihu,
      'canal-north': series['canal-north'],
      'canal-south': series['canal-south'],
    }),
  )
}

function isHydroPoint(value: unknown): value is HydroPoint {
  const point = value as HydroPoint
  return Number.isFinite(point?.value) && typeof point?.timestamp === 'string'
}

function setActiveChannel(channel: HydroChannelKey) {
  activeKey.value = channel
  hoverIndex.value = null
}

function zoomIn() {
  zoomIndex.value = Math.max(0, zoomIndex.value - 1)
  hoverIndex.value = null
}

function zoomOut() {
  zoomIndex.value = Math.min(zoomWindows.length - 1, zoomIndex.value + 1)
  hoverIndex.value = null
}

function resetZoom() {
  zoomIndex.value = 1
  hoverIndex.value = null
}

function handleWheel(event: WheelEvent) {
  event.preventDefault()
  event.deltaY < 0 ? zoomIn() : zoomOut()
}

function handlePointerMove(event: PointerEvent) {
  const rect = (event.currentTarget as SVGElement).getBoundingClientRect()
  const svgX = ((event.clientX - rect.left) / rect.width) * plot.width
  const points = chartPoints.value
  if (points.length === 0) {
    hoverIndex.value = null
    return
  }

  let nearest = 0
  let distance = Number.POSITIVE_INFINITY
  for (let index = 0; index < points.length; index += 1) {
    const nextDistance = Math.abs(points[index]!.x - svgX)
    if (nextDistance < distance) {
      nearest = index
      distance = nextDistance
    }
  }
  hoverIndex.value = nearest
}

function xForIndex(index: number, total: number) {
  if (total <= 1) return (plot.left + plot.right) / 2
  return plot.left + (index / (total - 1)) * (plot.right - plot.left)
}

function yForValue(value: number) {
  const span = range.value.max - range.value.min || 1
  return plot.bottom - ((value - range.value.min) / span) * (plot.bottom - plot.top)
}

function formatValue(point?: HydroPoint) {
  if (!point) return '--'
  return `${point.value.toFixed(activeChannel.value.precision)} ${activeChannel.value.unit}`
}

function formatChannelValue(channelKey: HydroChannelKey) {
  const channel = channels.find((item) => item.key === channelKey) ?? fallbackChannel
  const point = latestValues.value[channelKey]
  return point ? `${point.value.toFixed(channel.precision)} ${channel.unit}` : '--'
}

function formatTime(timestamp?: string) {
  if (!timestamp) return '--'
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return timestamp
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}
</script>

<template>
  <section class="hydro-process-panel" @wheel="handleWheel">
    <header class="hydro-process-header">
      <div class="hydro-process-title">
        <span>PROCESS</span>
        <strong>水情过程线</strong>
      </div>
      <div class="hydro-channel-tabs" aria-label="过程线切换">
        <button
          v-for="channel in channels"
          :key="channel.key"
          type="button"
          :class="{ active: activeKey === channel.key }"
          @click="setActiveChannel(channel.key)"
        >
          <span>{{ channel.label }}</span>
          <strong>{{ formatChannelValue(channel.key) }}</strong>
        </button>
      </div>
      <div class="hydro-zoom-actions">
        <button type="button" title="放大" @click="zoomIn">+</button>
        <button type="button" title="缩小" @click="zoomOut">-</button>
        <button type="button" title="重置缩放" @click="resetZoom">{{ zoomText }}</button>
      </div>
    </header>

    <div class="hydro-chart-wrap">
      <svg
        class="hydro-chart"
        :viewBox="`0 0 ${plot.width} ${plot.height}`"
        role="img"
        :aria-label="`${activeChannel.label}过程线`"
        @pointermove="handlePointerMove"
        @pointerleave="hoverIndex = null"
      >
        <defs>
          <linearGradient id="hydroFill" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0" stop-color="rgba(0, 242, 255, 0.28)" />
            <stop offset="1" stop-color="rgba(0, 242, 255, 0.02)" />
          </linearGradient>
        </defs>
        <rect
          :x="plot.left"
          :y="plot.top"
          :width="plot.right - plot.left"
          :height="plot.bottom - plot.top"
          class="hydro-chart-bg"
        />
        <path
          v-for="tick in 5"
          :key="tick"
          class="hydro-grid-line"
          :d="`M ${plot.left} ${plot.top + tick * ((plot.bottom - plot.top) / 6)} H ${plot.right}`"
        />
        <text :x="plot.left" :y="plot.top - 6" class="hydro-axis-label">
          {{ range.max.toFixed(activeChannel.precision) }}
        </text>
        <text :x="plot.left" :y="plot.bottom + 18" class="hydro-axis-label">
          {{ range.min.toFixed(activeChannel.precision) }}
        </text>
        <path
          v-if="chartPoints.length > 1"
          class="hydro-area"
          :d="`${pathD} L ${chartPoints[chartPoints.length - 1]?.x ?? plot.right} ${plot.bottom} L ${chartPoints[0]?.x ?? plot.left} ${plot.bottom} Z`"
        />
        <path v-if="chartPoints.length > 1" class="hydro-line" :d="pathD" />
        <circle
          v-for="(point, index) in chartPoints"
          :key="`${point.timestamp}-${index}`"
          class="hydro-dot"
          :class="{ active: hoverIndex === index }"
          :cx="point.x"
          :cy="point.y"
          r="3.6"
        />
        <g v-if="hoveredPoint">
          <path class="hydro-crosshair" :d="`M ${hoveredPoint.x} ${plot.top} V ${plot.bottom}`" />
          <circle class="hydro-hover-dot" :cx="hoveredPoint.x" :cy="hoveredPoint.y" r="6" />
        </g>
        <text :x="plot.left" :y="plot.height - 10" class="hydro-time-label">
          {{ formatTime(visibleSeries[0]?.timestamp) }}
        </text>
        <text :x="plot.right" :y="plot.height - 10" text-anchor="end" class="hydro-time-label">
          {{ formatTime(visibleSeries[visibleSeries.length - 1]?.timestamp) }}
        </text>
      </svg>

      <div
        v-if="hoveredPoint"
        class="hydro-tooltip"
        :style="{
          left: `${(hoveredPoint.x / plot.width) * 100}%`,
          top: `${(hoveredPoint.y / plot.height) * 100}%`,
        }"
      >
        <strong>{{ activeChannel.label }}</strong>
        <span>{{ formatValue(hoveredPoint) }}</span>
        <em>{{ formatTime(hoveredPoint.timestamp) }}</em>
      </div>

      <div v-if="visibleSeries.length === 0" class="hydro-empty">等待实时数据接入</div>
    </div>

    <footer class="hydro-process-footer">
      <span :class="{ live: !loading }">{{ loading ? '同步中' : '实时同步' }}</span>
      <em>最近更新：{{ formatTime(lastUpdatedAt) }}</em>
      <b>鼠标滚轮可缩放</b>
    </footer>
  </section>
</template>
