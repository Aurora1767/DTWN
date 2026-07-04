<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { usePlatformStore } from '@/stores/platform'
import type { RiverSegment } from '@/types/platform'

const BASE = import.meta.env.BASE_URL
const CENTERLINES_URL = `${BASE}data/river-centerlines-all.geojson`
const SURFACES_URL    = `${BASE}data/basin-surfaces-all.geojson`

const BBOX = { minLng: 120.3258, maxLng: 120.3939, minLat: 31.4624, maxLat: 31.5314 }
const SVG_W = 400
const SVG_H = 360
const PAD   = 20

const STATIONS = [
  { name: '苏州（枫桥）', lng: 120.3331, lat: 31.5255, ax:  4, ay: -16 },
  { name: '浯溪桥',       lng: 120.3319, lat: 31.5072, ax:  4, ay: -16 },
  { name: '洛社',         lng: 120.3387, lat: 31.4893, ax: -44, ay: -16 },
  { name: '百渎口',       lng: 120.3463, lat: 31.4873, ax:  4, ay:  10 },
  { name: '望亭（大）',   lng: 120.3465, lat: 31.4756, ax:  4, ay: -16 },
  { name: '黄埝桥',       lng: 120.3608, lat: 31.4922, ax:  4, ay: -16 },
  { name: '漕桥（三）',   lng: 120.3795, lat: 31.4951, ax:  4, ay:  10 },
  { name: '张桥',         lng: 120.3730, lat: 31.4899, ax: -42, ay: -16 },
]

type Coord   = [number, number]
type Mode    = 'flow' | 'level'
interface GeoLine { coords: Coord[]; code: string; reachId: number | null }
interface GeoPoly { rings: Coord[][] }

const store    = usePlatformStore()
const mode     = ref<Mode>('flow')
const geoLines = ref<GeoLine[]>([])
const geoPolys = ref<GeoPoly[]>([])
let pollTimer: ReturnType<typeof setInterval> | undefined

// ─── projection ───────────────────────────────────────────────
function project(lng: number, lat: number): [number, number] {
  const uw = SVG_W - PAD * 2
  const uh = SVG_H - PAD * 2
  return [
    PAD + ((lng - BBOX.minLng) / (BBOX.maxLng - BBOX.minLng)) * uw,
    PAD + ((BBOX.maxLat - lat) / (BBOX.maxLat - BBOX.minLat)) * uh,
  ]
}

function toPoints(coords: Coord[]): string {
  return coords.map(([lng, lat]) => project(lng, lat).join(',')).join(' ')
}

function toPolyPath(rings: Coord[][]): string {
  return rings.map((ring) => {
    const pts = ring.map(([lng, lat]) => project(lng, lat))
    return pts.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ') + ' Z'
  }).join(' ')
}

// ─── colour scale ─────────────────────────────────────────────
const PALETTE: Array<[number, number, number]> = [
  [0, 40, 90], [0, 120, 200], [0, 220, 255],
  [80, 240, 120], [255, 220, 0], [255, 100, 0],
]

function lerpC(a: [number, number, number], b: [number, number, number], t: number): string {
  return `rgb(${Math.round(a[0] + (b[0] - a[0]) * t)},${Math.round(a[1] + (b[1] - a[1]) * t)},${Math.round(a[2] + (b[2] - a[2]) * t)})`
}

function val2col(norm: number): string {
  const n = Math.max(0, Math.min(1, norm))
  const step = 1 / (PALETTE.length - 1)
  const idx = Math.min(Math.floor(n / step), PALETTE.length - 2)
  return lerpC(PALETTE[idx]!, PALETTE[idx + 1]!, (n - idx * step) / step)
}

// ─── hydrology lookup by reachId ──────────────────────────────
function buildReachMap(): Map<number, RiverSegment> {
  const m = new Map<number, RiverSegment>()
  for (const s of store.network.segments) {
    if (s.reachId != null) m.set(s.reachId, s)
  }
  return m
}

interface ColourLine { coords: Coord[]; colour: string; width: number }

const colourLines = computed<ColourLine[]>(() => {
  const segs = buildReachMap()
  const isFlow = mode.value === 'flow'
  const vals: number[] = []
  for (const seg of store.network.segments) {
    const v = isFlow ? seg.hydrologyStats?.maxFlow : seg.hydrologyStats?.maxWaterLevel
    if (v != null) vals.push(isFlow ? Math.abs(v) : v)
  }
  const hasData = vals.length > 0
  const vMin   = hasData ? Math.min(...vals) : 0
  const vRange = hasData ? (Math.max(...vals) - vMin || 1) : 1

  return geoLines.value.map((gl) => {
    const seg = gl.reachId != null ? segs.get(gl.reachId) : undefined
    const raw = seg
      ? (isFlow ? seg.hydrologyStats?.maxFlow : seg.hydrologyStats?.maxWaterLevel)
      : undefined
    const absVal = raw != null ? (isFlow ? Math.abs(raw) : raw) : undefined
    const norm   = absVal != null ? (absVal - vMin) / vRange : 0.45
    const colour = hasData && absVal != null ? val2col(norm) : '#00e5ff'
    const width  = hasData && absVal != null ? 1.8 + norm * 3.2 : 2
    return { coords: gl.coords, colour, width }
  })
})

const barTicks = computed(() => {
  const isFlow = mode.value === 'flow'
  const vals = store.network.segments
    .map((s) => isFlow ? s.hydrologyStats?.maxFlow : s.hydrologyStats?.maxWaterLevel)
    .filter((v): v is number => v != null)
    .map((v) => isFlow ? Math.abs(v) : v)
  if (!vals.length) return []
  const vMin = isFlow ? 0 : Math.min(...vals)
  const vMax = Math.max(...vals)
  const vRange = vMax - vMin || 1
  return [0, 0.25, 0.5, 0.75, 1].map((t) => ({
    t,
    label: isFlow
      ? `${(vMin + t * vRange).toFixed(0)}`
      : `${(vMin + t * vRange).toFixed(1)}`,
    colour: val2col(t),
  }))
})

// ─── data loading ──────────────────────────────────────────────
async function readJson(url: string) {
  try {
    const r = await fetch(url)
    return r.ok ? (await r.json() as { features: any[] }) : null
  } catch { return null }
}

function extractRings(geom: any): Coord[][] {
  if (!geom) return []
  if (geom.type === 'Polygon') return geom.coordinates as Coord[][]
  if (geom.type === 'MultiPolygon') return (geom.coordinates as Coord[][][]).flat()
  return []
}

onMounted(async () => {
  const [clData, sfData] = await Promise.all([readJson(CENTERLINES_URL), readJson(SURFACES_URL)])

  const lines: GeoLine[] = []
  for (const f of (clData?.features ?? [])) {
    const g = f.geometry
    if (!g) continue
    const groups: Coord[][] = g.type === 'LineString'
      ? [g.coordinates]
      : g.type === 'MultiLineString' ? g.coordinates : []
    const reachId: number | null = f.properties?.reachId ?? null
    for (const raw of groups) {
      if ((raw as Coord[]).length >= 2) {
        lines.push({ coords: raw as Coord[], code: f.properties?.code ?? '', reachId })
      }
    }
  }
  geoLines.value = lines

  const polys: GeoPoly[] = []
  for (const f of (sfData?.features ?? [])) {
    const rings = extractRings(f.geometry)
    if (rings.length) polys.push({ rings })
  }
  geoPolys.value = polys

  pollTimer = setInterval(() => {}, 60_000)
})

onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })

const stationSvg = computed(() =>
  STATIONS.map((s) => {
    const [cx, cy] = project(s.lng, s.lat)
    return { ...s, cx, cy, lx: cx + s.ax, ly: cy + s.ay }
  }),
)
</script>

<template>
  <div class="basin-overview">
    <div class="basin-map-header">
      <span>MONITOR</span>
      <strong>流域总览</strong>
      <div class="basin-mode-switch">
        <button :class="{ active: mode === 'flow' }"  @click="mode = 'flow'">流量</button>
        <button :class="{ active: mode === 'level' }" @click="mode = 'level'">水位</button>
      </div>
    </div>

    <div class="basin-svg-wrap">
      <svg
        class="basin-svg"
        :viewBox="`0 0 ${SVG_W} ${SVG_H}`"
        preserveAspectRatio="xMidYMid meet"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <filter id="bov-river-glow" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="2.2" result="blur" />
            <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
          <filter id="bov-dot-glow" x="-100%" y="-100%" width="300%" height="300%">
            <feGaussianBlur stdDeviation="4" result="blur" />
            <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
          </filter>
          <radialGradient id="bov-bg" cx="50%" cy="50%" r="55%">
            <stop offset="0%"   stop-color="#083060" stop-opacity="0.5"/>
            <stop offset="100%" stop-color="#010a14" stop-opacity="0"/>
          </radialGradient>
          <linearGradient id="bov-bar-grad" x1="0" x2="1" y1="0" y2="0">
            <stop
              v-for="tick in barTicks"
              :key="tick.t"
              :offset="`${tick.t * 100}%`"
              :stop-color="tick.colour"
            />
          </linearGradient>
        </defs>

        <rect width="400" height="360" fill="url(#bov-bg)" />

        <!-- water surface fill polygons -->
        <g>
          <path
            v-for="(poly, i) in geoPolys"
            :key="`poly-${i}`"
            :d="toPolyPath(poly.rings)"
            fill="rgba(0, 180, 230, 0.13)"
            stroke="rgba(0, 220, 255, 0.22)"
            stroke-width="0.6"
          />
        </g>

        <!-- river lines shadow layer -->
        <g>
          <polyline
            v-for="(cl, i) in colourLines"
            :key="`shadow-${i}`"
            :points="toPoints(cl.coords)"
            fill="none"
            stroke="#021428"
            :stroke-width="cl.width + 2"
            stroke-linecap="round"
            stroke-linejoin="round"
            opacity="0.55"
          />
        </g>

        <!-- river lines with colour + glow -->
        <g filter="url(#bov-river-glow)">
          <polyline
            v-for="(cl, i) in colourLines"
            :key="`colour-${i}`"
            :points="toPoints(cl.coords)"
            fill="none"
            :stroke="cl.colour"
            :stroke-width="cl.width"
            stroke-linecap="round"
            stroke-linejoin="round"
            opacity="0.95"
          />
        </g>

        <!-- monitoring stations -->
        <g v-for="s in stationSvg" :key="s.name">
          <circle :cx="s.cx" :cy="s.cy" r="8"   fill="rgba(255,200,40,0.14)" stroke="rgba(255,200,40,0.45)" stroke-width="1" />
          <circle :cx="s.cx" :cy="s.cy" r="4.5" fill="#ffcf28" filter="url(#bov-dot-glow)" />
          <circle :cx="s.cx" :cy="s.cy" r="3.5" fill="#ffe466" />
          <rect
            :x="s.lx - 2"
            :y="s.ly - 11"
            :width="s.name.length * 10 + 6"
            height="15"
            rx="2"
            fill="rgba(2, 14, 32, 0.82)"
          />
          <text
            :x="s.lx + 1"
            :y="s.ly"
            font-size="13"
            font-weight="bold"
            font-family="'Microsoft YaHei', sans-serif"
            fill="#e8f6ff"
            letter-spacing="0.4"
          >{{ s.name }}</text>
        </g>

        <!-- colour bar -->
        <g v-if="barTicks.length">
          <text
            v-for="tick in barTicks"
            :key="`t-${tick.t}`"
            :x="(SVG_W - 240) / 2 + tick.t * 240"
            :y="SVG_H - 32"
            font-size="13"
            font-weight="700"
            fill="rgba(200,235,255,0.9)"
            text-anchor="middle"
            font-family="'Microsoft YaHei', sans-serif"
          >{{ tick.label }}</text>
          <rect
            :x="(SVG_W - 240) / 2"
            :y="SVG_H - 22"
            width="240"
            height="11"
            rx="5"
            fill="url(#bov-bar-grad)"
            opacity="0.95"
          />
          <text
            :x="(SVG_W - 240) / 2 + 248"
            :y="SVG_H - 13"
            font-size="13"
            font-weight="700"
            fill="rgba(200,235,255,0.8)"
            font-family="'Microsoft YaHei', sans-serif"
          >{{ mode === 'flow' ? 'm³/s' : 'm' }}</text>
        </g>
      </svg>
    </div>
  </div>
</template>
