<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { usePlatformStore } from '@/stores/platform'
import type { HydraulicStructure, RiverSegment, SensorSnapshot, WaterNode } from '@/types/platform'

type TdtStatus = 'idle' | 'loading' | 'ready' | 'missing-token' | 'failed'

declare global {
  interface Window {
    T?: any
    TMAP_SATELLITE_MAP?: any
    __tdtLoadPromise?: Promise<void>
  }
}

const store = usePlatformStore()
const mapContainer = ref<HTMLDivElement | null>(null)
const status = ref<TdtStatus>('idle')
const statusMessage = ref('')

let map: any
let overlays: any[] = []
let waterSurfaces: GeoFeature[] = []
let waterShorelines: GeoFeature[] = []
let pipeShorelines: GeoFeature[] = []
let pipeSurfaces: GeoFeature[] = []
const showPipeShorelines = ref(true)
const showPipeSurfaces   = ref(true)
const token = (import.meta.env.VITE_TIANDITU_TOKEN ?? '').trim()
const WATER_SURFACES_URL = `${import.meta.env.BASE_URL}data/water-surfaces.geojson`
const WATER_SHORELINES_URL = `${import.meta.env.BASE_URL}data/water-shorelines.geojson`
const PIPE_SHORELINES_URL = `${import.meta.env.BASE_URL}data/pipe-shorelines.geojson`
const PIPE_SURFACES_URL   = `${import.meta.env.BASE_URL}data/pipe-surfaces.geojson`
const NODE_LABELS: Record<string, string> = {
  N01: '太湖',
  N03: '京杭大运河南',
  N06: '京杭大运河北',
}

const stationByNode = computed(() => {
  return new Map(store.stations.map((station) => [station.nodeCode, station]))
})

const readableNodeLabels: Record<string, string> = {
  N01: '太湖',
  N03: '京杭大运河南',
  N06: '京杭大运河北',
}

const segmentByCode = computed(() => {
  return new Map(store.network.segments.map((segment) => [segment.code, segment]))
})

const nodeByCode = computed(() => {
  return new Map(store.network.nodes.map((node) => [node.code, node]))
})

const segmentLines = computed(() => {
  return store.network.segments
    .filter((segment) => segment.coordinates.length > 1)
    .map((segment) => ({
      segment,
      line: segment.coordinates.map((coordinate) => [coordinate.lng, coordinate.lat] as LngLatPair),
    }))
})

watch(
  () => [store.network.segments, store.network.nodes, store.network.structures, store.stations, store.mapLayers],
  () => {
    if (status.value === 'ready') {
      drawNetwork()
    }
  },
  { deep: true },
)

onMounted(() => {
  window.addEventListener('resize', refreshMapSize)
  void initializeMap()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', refreshMapSize)
  clearLayers()
  map = undefined
})

async function initializeMap() {
  await nextTick()
  if (!mapContainer.value) return

  if (!token) {
    status.value = 'missing-token'
    statusMessage.value = '配置 VITE_TIANDITU_TOKEN 后可加载真实天地图底图'
    return
  }

  status.value = 'loading'
  statusMessage.value = '正在加载天地图...'

  try {
    await loadTdtScript(token)
    const T = window.T
    map = new T.Map(mapContainer.value)
    map.centerAndZoom(new T.LngLat(120.274, 31.486), 12)
    setSatelliteMapType()
    map.enableScrollWheelZoom?.()
    await loadWaterBoundaryData()
    status.value = 'ready'
    statusMessage.value = ''
    drawNetwork()
    refreshMapSize()
  } catch (error) {
    status.value = 'failed'
    statusMessage.value = error instanceof Error ? error.message : '天地图加载失败'
  }
}

function setSatelliteMapType() {
  const satelliteMapType = window.TMAP_SATELLITE_MAP
  if (satelliteMapType && map?.setMapType) {
    map.setMapType(satelliteMapType)
  }
}

function loadTdtScript(tdtToken: string) {
  if (window.T?.Map) {
    return Promise.resolve()
  }
  if (window.__tdtLoadPromise) {
    return window.__tdtLoadPromise
  }

  window.__tdtLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = `https://api.tianditu.gov.cn/api?v=4.0&tk=${encodeURIComponent(tdtToken)}`
    script.async = true
    script.onload = () => {
      if (window.T?.Map) {
        resolve()
      } else {
        reject(new Error('天地图 API 已加载，但未发现 T.Map'))
      }
    }
    script.onerror = () => reject(new Error('无法加载天地图 API，请检查网络或密钥'))
    document.head.appendChild(script)
  })

  return window.__tdtLoadPromise
}

async function loadWaterBoundaryData() {
  const [surfaces, shorelines, pipeLines, pipePolys] = await Promise.all([
    fetchGeoFeatures(WATER_SURFACES_URL),
    fetchGeoFeatures(WATER_SHORELINES_URL),
    fetchGeoFeatures(PIPE_SHORELINES_URL),
    fetchGeoFeatures(PIPE_SURFACES_URL),
  ])
  waterSurfaces   = surfaces
  waterShorelines = shorelines
  pipeShorelines  = pipeLines
  pipeSurfaces    = pipePolys
}

async function fetchGeoFeatures(url: string) {
  try {
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const data = (await response.json()) as GeoFeatureCollection
    return data.features ?? []
  } catch (error) {
    console.warn(`[waternet] 水面/岸线图层加载失败: ${url}`, error)
    return []
  }
}

function togglePipeAll() {
  const next = !(showPipeShorelines.value || showPipeSurfaces.value)
  showPipeShorelines.value = next
  showPipeSurfaces.value   = next
  drawNetwork()
}

function drawNetwork() {  if (!map || !window.T) return
  const T = window.T
  clearLayers()

  const allPoints: any[] = []

  if (store.mapLayers.rivers) {
    drawWaterSurfaces(T, allPoints)
    drawWaterShorelines(T, allPoints)
    drawSegmentHitLines(T, allPoints)
    if (showPipeShorelines.value) drawPipeShorelines(T, allPoints)
    if (showPipeSurfaces.value)   drawPipeSurfaces(T, allPoints)
  }

  if (store.mapLayers.nodes) {
    for (const node of store.network.nodes) {
      const point = new T.LngLat(node.lng, node.lat)
      const nodeStyle = getNodeStyle(node)
      const marker = new T.Circle(point, 34, {
        color: nodeStyle.stroke,
        weight: 2,
        opacity: 0.95,
        fillColor: nodeStyle.fill,
        fillOpacity: 0.86,
      })
      marker.addEventListener?.('click', () => {
        store.selectFeature({ type: 'node', code: node.code, name: node.name })
        const infoWindow = new T.InfoWindow(buildNodePopup(node, stationByNode.value.get(node.code)), {
          minWidth: 240,
          maxWidth: 300,
        })
        map.openInfoWindow(infoWindow, point)
      })
      map.addOverLay(marker)
      overlays.push(marker)
      allPoints.push(point)

      const labelText = readableNodeLabels[node.code] ?? NODE_LABELS[node.code]
      if (labelText) {
        const label = buildNodeLabel(T, point, labelText)
        map.addOverLay(label)
        overlays.push(label)
      }
    }
  }

  if (store.mapLayers.structures) {
    for (const structure of store.network.structures) {
      const point = new T.LngLat(structure.lng, structure.lat)
      const marker = new T.Marker(point)
      marker.addEventListener?.('click', () => {
        const infoWindow = new T.InfoWindow(buildStructurePopup(structure), {
          minWidth: 240,
          maxWidth: 300,
        })
        map.openInfoWindow(infoWindow, point)
      })
      map.addOverLay(marker)
      overlays.push(marker)
      allPoints.push(point)
    }
  }

  if (allPoints.length > 1) {
    map.setViewport?.(allPoints)
  }
  refreshMapSize()
}

function refreshMapSize() {
  map?.checkResize?.()
}

function drawWaterSurfaces(T: any, allPoints: any[]) {
  for (const feature of waterSurfaces) {
    const segment = findSegmentByFeature(feature)
    for (const ring of surfaceRings(feature.geometry)) {
      const points = ringToTdtPoints(T, ring, allPoints)
      if (points.length < 3) continue
      const polygon = new T.Polygon(points, {
        color: '#35f2bd',
        weight: 2,
        opacity: 0.55,
        fillColor: '#18d8ff',
        fillOpacity: 0.22,
      })
      if (segment) {
        polygon.addEventListener?.('click', () => {
          store.selectFeature({ type: 'segment', code: segment.code, name: segment.name })
          const infoWindow = new T.InfoWindow(buildSegmentPopup(segment), {
            minWidth: 260,
            maxWidth: 320,
          })
          const centerPoint = points[Math.floor(points.length / 2)] ?? points[0]
          if (centerPoint) {
            map.openInfoWindow(infoWindow, centerPoint)
          }
        })
      }
      map.addOverLay(polygon)
      overlays.push(polygon)
    }
  }
}

function drawWaterShorelines(T: any, allPoints: any[]) {
  for (const feature of waterShorelines) {
    const segment = findSegmentByFeature(feature)
    for (const line of lineStrings(feature.geometry)) {
      const points = ringToTdtPoints(T, line, allPoints)
      if (points.length < 2) continue
      const polyline = new T.Polyline(points, {
        color: '#fff06a',
        weight: 3,
        opacity: 0.78,
        lineStyle: 'solid',
      })
      if (segment) {
        polyline.addEventListener?.('click', () => {
          store.selectFeature({ type: 'segment', code: segment.code, name: segment.name })
          const infoWindow = new T.InfoWindow(buildSegmentPopup(segment), {
            minWidth: 260,
            maxWidth: 320,
          })
          const centerPoint = points[Math.floor(points.length / 2)] ?? points[0]
          if (centerPoint) {
            map.openInfoWindow(infoWindow, centerPoint)
          }
        })
      }
      map.addOverLay(polyline)
      overlays.push(polyline)
    }
  }
}

function drawSegmentHitLines(T: any, allPoints: any[]) {
  for (const segment of store.network.segments) {
    if (segment.coordinates.length < 2) continue
    const points = segment.coordinates.map((coordinate) => {
      const point = new T.LngLat(coordinate.lng, coordinate.lat)
      allPoints.push(point)
      return point
    })
    const hitLine = new T.Polyline(points, {
      color: '#00f2ff',
      weight: 14,
      opacity: 0.02,
      lineStyle: 'solid',
    })
    hitLine.addEventListener?.('click', () => {
      store.selectFeature({ type: 'segment', code: segment.code, name: segment.name })
      const infoWindow = new T.InfoWindow(buildSegmentPopup(segment), {
        minWidth: 260,
        maxWidth: 320,
      })
      const centerPoint = points[Math.floor(points.length / 2)] ?? points[0]
      if (centerPoint) {
        map.openInfoWindow(infoWindow, centerPoint)
      }
    })
    map.addOverLay(hitLine)
    overlays.push(hitLine)
  }
}

function makeArrowIcon(color: string): HTMLCanvasElement {
  const size = 56
  const cv = document.createElement('canvas')
  cv.width = size
  cv.height = size
  const ctx = cv.getContext('2d')!
  const cx = size / 2
  const cy = size / 2

  // glow pass
  ctx.shadowColor = color
  ctx.shadowBlur = 12

  // shaft
  ctx.strokeStyle = color
  ctx.lineWidth = 5
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(cx - 14, cy)
  ctx.lineTo(cx + 6, cy)
  ctx.stroke()

  // arrowhead (filled chevron pointing right)
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.moveTo(cx + 6,  cy - 9)
  ctx.lineTo(cx + 20, cy)
  ctx.lineTo(cx + 6,  cy + 9)
  ctx.lineTo(cx + 10, cy)
  ctx.closePath()
  ctx.fill()

  return cv
}

function drawFlowArrows(T: any) {
  for (const segment of store.network.segments) {
    const coords = segment.coordinates
    if (coords.length < 2) continue
    const flow = segment.hydrologyStats?.maxFlow
    if (flow == null || Math.abs(flow) < 0.01) continue

    // pick midpoint pair
    const mid = Math.floor(coords.length / 2)
    const a = coords[mid - 1]!
    const b = coords[mid]!

    const lng = (a.lng + b.lng) / 2
    const lat = (a.lat + b.lat) / 2

    // geographic heading: dx east+, dy north+
    // screen: east = +x (right=0°), north = -y (up)
    // atan2 gives angle from east axis, clockwise on screen = negate y
    const dLng = b.lng - a.lng
    const dLat = b.lat - a.lat
    // convert to screen angle (east=0, clockwise positive)
    let angleDeg = Math.atan2(-dLat, dLng) * (180 / Math.PI)
    // flip if flow is negative (reversed direction)
    if (flow < 0) angleDeg += 180

    const color = flow > 0 ? '#00ffc8' : '#ffb830'
    const cv = makeArrowIcon(color)
    const dataUrl = cv.toDataURL()

    const icon = new T.Icon({
      iconUrl: dataUrl,
      iconSize: new T.Point(56, 56),
      iconAnchor: new T.Point(28, 28),
    })
    const marker = new T.Marker(new T.LngLat(lng, lat), { icon })
    marker.addEventListener?.('add', () => {
      const el = marker.getElement?.() as HTMLElement | undefined
      if (el) {
        const img = el.querySelector('img') as HTMLImageElement | null ?? el
        img.style.transformOrigin = '50% 50%'
        img.style.transform = `rotate(${angleDeg}deg)`
      }
    })
    map.addOverLay(marker)
    overlays.push(marker)
  }
}


function drawPipeShorelines(T: any, allPoints: any[]) {
  for (const feature of pipeShorelines) {
    for (const line of lineStrings(feature.geometry)) {
      const pts = ringToTdtPoints(T, webMercNormalizeLine(line), allPoints)
      if (pts.length < 2) continue
      const pl = new T.Polyline(pts, {
        color: '#ff6ec7',
        weight: 2.5,
        opacity: 0.88,
        lineStyle: 'solid',
      })
      map.addOverLay(pl)
      overlays.push(pl)
    }
  }
}

function drawPipeSurfaces(T: any, allPoints: any[]) {
  for (const feature of pipeSurfaces) {
    for (const ring of surfaceRings(feature.geometry)) {
      const pts = ringToTdtPoints(T, webMercNormalizeLine(ring), allPoints)
      if (pts.length < 3) continue
      const poly = new T.Polygon(pts, {
        color: '#c84dff',
        weight: 1.5,
        opacity: 0.72,
        fillColor: '#a020f0',
        fillOpacity: 0.18,
      })
      map.addOverLay(poly)
      overlays.push(poly)
    }
  }
}

function webMercNormalizeLine(coords: [number, number][]): [number, number][] {
  if (!coords.length) return coords
  const [x, y] = coords[0]!
  if (Math.abs(x) > 180 || Math.abs(y) > 90) {
    return coords.map(([cx, cy]) => {
      const R = 6378137
      return [(cx / R) * (180 / Math.PI), (2 * Math.atan(Math.exp(cy / R)) - Math.PI / 2) * (180 / Math.PI)] as [number, number]
    })
  }
  return coords
}

function findSegmentByFeature(feature: GeoFeature) {
  const spatialMatch = findNearestSegmentByGeometry(feature.geometry)
  if (spatialMatch && spatialMatch.distanceMeters <= 250) {
    return spatialMatch.segment
  }

  const riverCode = typeof feature.properties?.riverCode === 'string' ? feature.properties.riverCode : undefined
  if (!riverCode) return undefined
  return segmentByCode.value.get(riverCode)
}

function findNearestSegmentByGeometry(geometry?: GeoGeometry) {
  const coordinates = sampleCoordinates(flattenGeometryCoordinates(geometry), 60)
  if (coordinates.length === 0) return undefined

  let bestMatch: { segment: RiverSegment; distanceMeters: number } | undefined
  for (const candidate of segmentLines.value) {
    const distanceMeters =
      coordinates.reduce((sum, coordinate) => sum + minDistanceToLineMeters(coordinate, candidate.line), 0) /
      coordinates.length

    if (!bestMatch || distanceMeters < bestMatch.distanceMeters) {
      bestMatch = { segment: candidate.segment, distanceMeters }
    }
  }
  return bestMatch
}

function flattenGeometryCoordinates(geometry?: GeoGeometry): LngLatPair[] {
  if (!geometry) return []
  if (geometry.type === 'LineString') {
    return geometry.coordinates
  }
  if (geometry.type === 'MultiLineString') {
    return geometry.coordinates.flat()
  }
  if (geometry.type === 'Polygon') {
    return geometry.coordinates.flat()
  }
  return geometry.coordinates.flat(2)
}

function sampleCoordinates(coordinates: LngLatPair[], maxSamples: number) {
  if (coordinates.length <= maxSamples) return coordinates
  const step = Math.max(1, Math.floor(coordinates.length / maxSamples))
  return coordinates.filter((_, index) => index % step === 0)
}

function minDistanceToLineMeters(point: LngLatPair, line: LngLatPair[]) {
  let minDistance = Number.POSITIVE_INFINITY
  for (let index = 1; index < line.length; index += 1) {
    const from = line[index - 1]
    const to = line[index]
    if (!from || !to) continue
    minDistance = Math.min(minDistance, distanceToSegmentMeters(point, from, to))
  }
  return minDistance
}

function distanceToSegmentMeters(point: LngLatPair, from: LngLatPair, to: LngLatPair) {
  const [pointX, pointY] = projectToMeters(point)
  const [fromX, fromY] = projectToMeters(from)
  const [toX, toY] = projectToMeters(to)
  const deltaX = toX - fromX
  const deltaY = toY - fromY
  if (deltaX === 0 && deltaY === 0) {
    return Math.hypot(pointX - fromX, pointY - fromY)
  }

  const ratio = Math.max(
    0,
    Math.min(1, ((pointX - fromX) * deltaX + (pointY - fromY) * deltaY) / (deltaX * deltaX + deltaY * deltaY)),
  )
  return Math.hypot(pointX - (fromX + ratio * deltaX), pointY - (fromY + ratio * deltaY))
}

function projectToMeters([lng, lat]: LngLatPair): [number, number] {
  return [lng * 111_320 * Math.cos((lat * Math.PI) / 180), lat * 110_540]
}

function ringToTdtPoints(T: any, ring: LngLatPair[], allPoints: any[]) {
  return ring.map(([lng, lat]) => {
    const point = new T.LngLat(lng, lat)
    allPoints.push(point)
    return point
  })
}

function surfaceRings(geometry?: GeoGeometry) {
  if (!geometry) return []
  if (geometry.type === 'Polygon') {
    return geometry.coordinates.map((ring) => normalizeClosedRing(ring))
  }
  if (geometry.type === 'MultiPolygon') {
    return geometry.coordinates.flatMap((polygon) => polygon.map((ring) => normalizeClosedRing(ring)))
  }
  return []
}

function lineStrings(geometry?: GeoGeometry) {
  if (!geometry) return []
  if (geometry.type === 'LineString') {
    return [geometry.coordinates]
  }
  if (geometry.type === 'MultiLineString') {
    return geometry.coordinates
  }
  return []
}

function normalizeClosedRing(ring: LngLatPair[]) {
  if (ring.length < 2) return ring
  const first = ring[0]
  const last = ring[ring.length - 1]
  if (!first || !last) return ring
  if (first[0] === last[0] && first[1] === last[1]) {
    return ring
  }
  return [...ring, first]
}

function clearLayers() {
  if (!map) return
  for (const overlay of overlays) {
    try {
      map.removeOverLay?.(overlay)
    } catch {
      // Some TDT overlay implementations ignore missing overlays.
    }
  }
  overlays = []
}

function getNodeStyle(node: WaterNode) {
  if (node.type === 'BOUNDARY') {
    return { fill: '#ff6b3d', stroke: '#fff1a8' }
  }
  if (node.type === 'DEAD_END') {
    return { fill: '#ffd84d', stroke: '#ffffff' }
  }
  return { fill: '#28dfff', stroke: '#ffffff' }
}

function buildNodeLabel(T: any, point: any, text: string) {
  const label = new T.Label({
    text,
    position: point,
    offset: new T.Point(12, -16),
  })
  label.setBackgroundColor?.('rgba(4, 23, 42, 0.86)')
  label.setBorderLine?.(1)
  label.setBorderColor?.('#00f2ff')
  label.setFontColor?.('#ffffff')
  label.setFontSize?.(14)
  label.setFontWeight?.('bold')
  return label
}

function buildNodePopup(node: WaterNode, station?: SensorSnapshot) {
  const connectedNodes =
    node.connectedNodeCodes && node.connectedNodeCodes.length > 0 ? node.connectedNodeCodes.join(', ') : '无'
  const connectedSegments =
    node.connectedSegmentCodes && node.connectedSegmentCodes.length > 0 ? node.connectedSegmentCodes.join(', ') : '无'
  const latest = node.latestHydrology
  const latestHtml = latest
    ? `
      <div class="tdt-popup-row"><span>最新小时</span><strong>${formatHour(latest.hour)}</strong></div>
      <div class="tdt-popup-row"><span>最新水位</span><strong>${formatNumber(latest.waterLevel, 2)} m</strong></div>
      <div class="tdt-popup-row"><span>最新流量</span><strong>${formatNumber(latest.flow, 1)} m3/s</strong></div>
    `
    : ''
  const stationHtml =
    !latest && station
      ? `
      <div class="tdt-popup-row"><span>水位</span><strong>${station.waterLevel.toFixed(2)} m</strong></div>
      <div class="tdt-popup-row"><span>流量</span><strong>${station.flow.toFixed(1)} m3/s</strong></div>
      <div class="tdt-popup-row"><span>流速</span><strong>${station.velocity.toFixed(2)} m/s</strong></div>
      <div class="tdt-popup-row"><span>状态</span><strong>${station.status}</strong></div>
    `
      : !latest
        ? '<div class="tdt-popup-row"><span>测站</span><strong>未关联实时数据</strong></div>'
        : ''

  return `
    <div class="tdt-popup">
      <h3>${node.name}</h3>
      <div class="tdt-popup-row"><span>节点编码</span><strong>${node.code}</strong></div>
      <div class="tdt-popup-row"><span>节点类型</span><strong>${node.type}</strong></div>
      <div class="tdt-popup-row"><span>边界类型</span><strong>${node.boundaryType}</strong></div>
      <div class="tdt-popup-row"><span>相邻节点</span><strong>${connectedNodes}</strong></div>
      <div class="tdt-popup-row"><span>关联河段</span><strong>${connectedSegments}</strong></div>
      ${latestHtml}
      ${stationHtml}
    </div>
  `
}

function buildSegmentPopup(segment: RiverSegment) {
  const startNode = nodeByCode.value.get(segment.startNodeCode)
  const endNode = nodeByCode.value.get(segment.endNodeCode)
  const startLabel = startNode ? `${segment.startNodeCode} / ${startNode.name}` : segment.startNodeCode
  const endLabel = endNode ? `${segment.endNodeCode} / ${endNode.name}` : segment.endNodeCode
  const segmentTitle = segment.reachId ? `河段 ${segment.reachId}` : segment.name

  return `
    <div class="tdt-popup">
      <h3>${segmentTitle}</h3>
      <div class="tdt-popup-row"><span>河段编码</span><strong>${segment.code}</strong></div>
      <div class="tdt-popup-row"><span>拓扑编号</span><strong>${segment.reachId ?? '--'}</strong></div>
      <div class="tdt-popup-row"><span>起点节点</span><strong>${startLabel}</strong></div>
      <div class="tdt-popup-row"><span>终点节点</span><strong>${endLabel}</strong></div>
      <div class="tdt-popup-row"><span>河段长度</span><strong>${segment.lengthMeters.toFixed(0)} m</strong></div>
      <div class="tdt-popup-row"><span>近似宽度</span><strong>${segment.widthMeters.toFixed(2)} m</strong></div>
      <div class="tdt-popup-row"><span>最大流量</span><strong>${formatNumber(segment.hydrologyStats?.maxFlow, 1)} m3/s</strong></div>
      <div class="tdt-popup-row"><span>最小流量</span><strong>${formatNumber(segment.hydrologyStats?.minFlow, 1)} m3/s</strong></div>
      <div class="tdt-popup-row"><span>最高水位</span><strong>${formatNumber(segment.hydrologyStats?.maxWaterLevel, 2)} m</strong></div>
      <div class="tdt-popup-row"><span>最低水位</span><strong>${formatNumber(segment.hydrologyStats?.minWaterLevel, 2)} m</strong></div>
      <div class="tdt-popup-row"><span>统计时刻</span><strong>${formatHour(segment.hydrologyStats?.profileHour)}</strong></div>
    </div>
  `
}

function formatNumber(value: number | null | undefined, digits: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(digits) : '--'
}

function formatHour(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? `第 ${value} 小时` : '--'
}

function buildStructurePopup(structure: HydraulicStructure) {
  return `
    <div class="tdt-popup">
      <h3>${structure.name}</h3>
      <div class="tdt-popup-row"><span>工程编码</span><strong>${structure.code}</strong></div>
      <div class="tdt-popup-row"><span>工程类型</span><strong>${structure.type === 'PUMP' ? '泵站' : '闸门'}</strong></div>
      <div class="tdt-popup-row"><span>关联节点</span><strong>${structure.nodeCode}</strong></div>
      <div class="tdt-popup-row"><span>设计流量</span><strong>${structure.designFlow.toFixed(1)} m3/s</strong></div>
      <div class="tdt-popup-row"><span>运行状态</span><strong>${structure.status}</strong></div>
    </div>
  `
}

interface GeoFeatureCollection {
  features?: GeoFeature[]
}

interface GeoFeature {
  geometry?: GeoGeometry
  properties?: Record<string, unknown>
}

type GeoGeometry =
  | {
      type: 'Polygon'
      coordinates: LngLatPair[][]
    }
  | {
      type: 'MultiPolygon'
      coordinates: LngLatPair[][][]
    }
  | {
      type: 'LineString'
      coordinates: LngLatPair[]
    }
  | {
      type: 'MultiLineString'
      coordinates: LngLatPair[][]
    }

type LngLatPair = [number, number]
</script>

<template>
  <div class="tdt-map-wrap">
    <div ref="mapContainer" class="tdt-map"></div>
    <div v-if="status !== 'ready'" class="tdt-map-state">
      <strong>{{ status === 'missing-token' ? '等待天地图密钥' : '天地图状态' }}</strong>
      <span>{{ statusMessage }}</span>
      <small>河网、节点与弹窗逻辑已接入；配置密钥后会自动加载真实底图。</small>
    </div>
    <div class="tdt-pipe-panel" aria-label="管网图层控制">
      <button
        type="button"
        :class="{ active: showPipeShorelines || showPipeSurfaces }"
        @click="togglePipeAll()"
      >管网</button>
    </div>
  </div>
</template>
