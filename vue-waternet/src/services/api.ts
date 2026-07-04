import {
  mockHistoryRecords,
  mockNetwork,
  mockRainfallOverview,
  mockSimulation,
  mockStations,
  mockWarnings,
  mockWaterQuantityOverview,
} from '@/data/mock'
import type {
  ApiResponse,
  EnvironmentSnapshot,
  HistoricalSensorRecord,
  HydroScenarioSnapshot,
  NetworkOverview,
  NodeHydrologySeries,
  RainfallOverview,
  RiverSegment,
  SegmentProfile,
  SensorSnapshot,
  SimulationRequest,
  SimulationResult,
  WaterNode,
  WarningEvent,
  WaterHistoryPoint,
  WaterQuantityOverview,
  WaterStationSnapshot,
  WeatherForecast,
} from '@/types/platform'

const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'
const RIVER_NETWORK_URL = `${import.meta.env.BASE_URL}data/river-segments-67.geojson`
const WATER_NODES_URL = `${import.meta.env.BASE_URL}data/water-nodes.geojson`
const NODE_TOPOLOGY_URL = `${import.meta.env.BASE_URL}data/node-topology.json`
const HYDRO_SCENARIO_DIRECT_URL = 'https://waterlevel.gd.hydrosim.cn/api/scenario/latest'

export { API_BASE }

async function getData<T>(path: string, fallback: T): Promise<T> {
  try {
    const response = await fetch(`${API_BASE}${path}`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<T>
    return body.data
  } catch {
    return fallback
  }
}

async function postData<T>(path: string, payload: unknown, fallback: T): Promise<T> {
  try {
    const response = await fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<T>
    return body.data
  } catch {
    return fallback
  }
}

export function fetchNetworkOverview() {
  return getData<NetworkOverview>('/network/overview', mockNetwork)
}

export async function fetchRiverNetworkSegments() {
  try {
    const response = await fetch(RIVER_NETWORK_URL)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const geojson = (await response.json()) as RiverGeoJson
    const sourceIsWebMercator = isWebMercatorGeoJson(geojson)
    return geojson.features
      .map((feature, index) => toRiverSegment(feature, index, sourceIsWebMercator))
      .filter((segment): segment is RiverSegment => Boolean(segment))
  } catch {
    return []
  }
}

export async function fetchWaterNodes() {
  try {
    const [response, topologyMap] = await Promise.all([fetch(WATER_NODES_URL), fetchNodeTopologyMap()])
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const geojson = (await response.json()) as NodeGeoJson
    return geojson.features
      .map((feature) => toWaterNode(feature, topologyMap))
      .filter((node): node is WaterNode => Boolean(node))
      .sort((left, right) => Number(left.code.replace('N', '')) - Number(right.code.replace('N', '')))
  } catch {
    return []
  }
}

export function fetchStations() {
  return getData<SensorSnapshot[]>('/realtime/stations', mockStations)
}

export async function fetchSegmentProfile(segmentCode: string): Promise<SegmentProfile | null> {
  try {
    const response = await fetch(`${API_BASE}/network/segments/${encodeURIComponent(segmentCode)}/profile`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<SegmentProfile>
    if (body.code !== 0 || !body.data) {
      return null
    }
    return body.data
  } catch (error) {
    console.warn('[waternet] segment profile request failed', error)
    return null
  }
}

export async function fetchNodeSeries(nodeCode: string, recentHours = 72): Promise<NodeHydrologySeries | null> {
  try {
    const response = await fetch(
      `${API_BASE}/network/nodes/${encodeURIComponent(nodeCode)}/series?recentHours=${recentHours}`,
    )
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<NodeHydrologySeries>
    if (body.code !== 0 || !body.data) {
      return null
    }
    return body.data
  } catch (error) {
    console.warn('[waternet] node series request failed', error)
    return null
  }
}

const mockEnvironment: EnvironmentSnapshot = {
  weatherText: '晴',
  temperature: '25.6',
  windSpeed: '3.2',
  windScale: '2',
  observedAt: new Date().toISOString(),
}

export function fetchEnvironmentSnapshot() {
  return getData<EnvironmentSnapshot>('/weather/environment', mockEnvironment)
}

export async function fetchEnvironmentSnapshotLive(): Promise<EnvironmentSnapshot | null> {
  try {
    const response = await fetch(`${API_BASE}/weather/environment`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<EnvironmentSnapshot>
    return body.data
  } catch (error) {
    console.warn('[waternet] environment snapshot request failed; keeping last value', error)
    return null
  }
}

export async function fetchWeatherForecast(): Promise<WeatherForecast | null> {
  try {
    const response = await fetch(`${API_BASE}/weather/forecast`)
    if (!response.ok) throw new Error(`${response.status}`)
    const body = (await response.json()) as ApiResponse<WeatherForecast>
    return body.data
  } catch {
    return mockForecast
  }
}

const mockForecast: WeatherForecast = {
  daily: [
    { fxDate: new Date().toISOString().slice(0, 10), textDay: '多云', textNight: '阴', tempMax: '28', tempMin: '22', windSpeedDay: '3.5', windScaleDay: '2', windDirDay: '东南风', precip: '2.0', humidity: '72' },
    { fxDate: new Date(Date.now() + 86400000).toISOString().slice(0, 10), textDay: '小雨', textNight: '中雨', tempMax: '26', tempMin: '20', windSpeedDay: '4.2', windScaleDay: '3', windDirDay: '东风', precip: '12.5', humidity: '85' },
    { fxDate: new Date(Date.now() + 172800000).toISOString().slice(0, 10), textDay: '阴', textNight: '多云', tempMax: '27', tempMin: '21', windSpeedDay: '2.8', windScaleDay: '2', windDirDay: '北风', precip: '0.5', humidity: '68' },
  ],
  minutely: Array.from({ length: 24 }, (_, i) => ({
    fxTime: new Date(Date.now() + i * 300000).toISOString(),
    precip: i < 4 ? '0.0' : i < 12 ? (0.3 + i * 0.2).toFixed(1) : '0.0',
    type: 'rain',
  })),
  updatedAt: new Date().toISOString(),
}

export function fetchWarnings() {
  return getData<WarningEvent[]>('/warnings', mockWarnings)
}

export function runSimulation(request: SimulationRequest) {
  return postData<SimulationResult>('/simulations/run', request, mockSimulation)
}

export function fetchHistoryRecords(params: { nodeCode?: string; start?: string; end?: string }) {
  const query = new URLSearchParams()
  if (params.nodeCode) query.set('nodeCode', params.nodeCode)
  if (params.start) query.set('start', params.start)
  if (params.end) query.set('end', params.end)
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return getData<HistoricalSensorRecord[]>(`/history/sensor-records${suffix}`, mockHistoryRecords)
}

export function historyExportUrl(params: { nodeCode?: string; start?: string; end?: string }) {
  const query = new URLSearchParams()
  if (params.nodeCode) query.set('nodeCode', params.nodeCode)
  if (params.start) query.set('start', params.start)
  if (params.end) query.set('end', params.end)
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return `${API_BASE}/history/sensor-records/export${suffix}`
}

export function fetchWaterQuantityOverview() {
  return getData<WaterQuantityOverview>('/water-quantity/overview', mockWaterQuantityOverview)
}

export function fetchWaterStations() {
  return getData<WaterStationSnapshot[]>('/water-quantity/stations', mockWaterQuantityOverview.stations)
}

export function fetchWaterStationHistory(stationCode: string) {
  return getData<WaterHistoryPoint[]>(
    `/water-quantity/stations/${stationCode}/history`,
    mockWaterQuantityOverview.historyByCode[stationCode] ?? [],
  )
}

export function fetchRainfallOverview() {
  return getData<RainfallOverview>('/rainfall/history-24h', mockRainfallOverview)
}

export async function fetchRainfallOverviewLive(): Promise<RainfallOverview | null> {
  try {
    const response = await fetch(`${API_BASE}/rainfall/history-24h`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<RainfallOverview>
    return body.data
  } catch (error) {
    console.warn('[waternet] rainfall history request failed; keeping last value', error)
    return null
  }
}

export async function fetchHydroScenarioLatest() {
  try {
    const response = await fetch(`${API_BASE}/hydro-scenario/latest`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<HydroScenarioSnapshot>
    return body.data
  } catch {
    try {
      const response = await fetch(HYDRO_SCENARIO_DIRECT_URL)
      if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`)
      }
      return (await response.json()) as HydroScenarioSnapshot
    } catch {
      return fallbackHydroScenarioSnapshot()
    }
  }
}

export async function fetchWaterQuantityOverviewLive(): Promise<WaterQuantityOverview | null> {
  try {
    const response = await fetch(`${API_BASE}/water-quantity/overview`)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    const body = (await response.json()) as ApiResponse<WaterQuantityOverview>
    return body.data
  } catch (error) {
    console.warn('[waternet] water quantity request failed; keeping last value', error)
    return null
  }
}

interface RiverGeoJson {
  crs?: {
    properties?: {
      name?: string
    }
  }
  features: RiverFeature[]
}

interface NodeGeoJson {
  features: NodeFeature[]
}

interface RiverFeature {
  properties?: {
    fid?: number | string
    code?: string
    name?: string
    reachId?: number
    startNodeCode?: string
    endNodeCode?: string
    width?: number
    length?: number
    chezy?: number
    dx?: number
    bed?: number
  }
  geometry?: {
    type: 'LineString' | 'MultiLineString'
    coordinates: LngLatPair[] | LngLatPair[][]
  }
}

interface NodeFeature {
  properties?: {
    name?: string | number
  }
  geometry?: {
    type: 'Point'
    coordinates: LngLatPair
  }
}

type LngLatPair = [number, number]

interface NodeTopologyRecord {
  connectedNodeCodes?: string[]
  connectedSegmentCodes?: string[]
  connectedReachIds?: number[]
}

function toRiverSegment(feature: RiverFeature, index: number, sourceIsWebMercator = false): RiverSegment | null {
  const line = normalizeLineCoordinates(feature.geometry, sourceIsWebMercator)
  if (line.length < 2) return null

  const fid = toFiniteNumber(feature.properties?.fid)
  const reachId = feature.properties?.reachId ?? fid
  const segmentNo = reachId ?? index + 1
  const code = feature.properties?.code ?? `RIVER_${String(segmentNo).padStart(2, '0')}`
  return {
    code,
    name: feature.properties?.name ?? `河段 ${segmentNo}`,
    reachId,
    lengthMeters: Math.round(feature.properties?.length ?? calculateLengthMeters(line)),
    widthMeters: Number(feature.properties?.width ?? 24),
    manningN: 0.03,
    chezy: feature.properties?.chezy,
    dx: feature.properties?.dx,
    bedElevation: feature.properties?.bed,
    startNodeCode: feature.properties?.startNodeCode ?? `${code}_START`,
    endNodeCode: feature.properties?.endNodeCode ?? `${code}_END`,
    coordinates: line.map(([lng, lat]) => ({ lng, lat })),
  }
}

function toWaterNode(feature: NodeFeature, topologyMap: Record<string, NodeTopologyRecord>): WaterNode | null {
  if (feature.geometry?.type !== 'Point') return null
  const nodeNo = Number(feature.properties?.name)
  const [lng, lat] = feature.geometry.coordinates
  if (!Number.isFinite(nodeNo) || !Number.isFinite(lng) || !Number.isFinite(lat)) return null

  const category = classifyNode(nodeNo)
  const code = `N${String(nodeNo).padStart(2, '0')}`
  const topology = topologyMap[code]
  return {
    code,
    name: `${category.label} ${nodeNo}`,
    type: category.type,
    lng,
    lat,
    initialWaterLevel: 2.44,
    boundaryType: category.boundaryType,
    connectedNodeCodes: topology?.connectedNodeCodes ?? [],
    connectedSegmentCodes: topology?.connectedSegmentCodes ?? [],
    connectedReachIds: topology?.connectedReachIds ?? [],
  }
}

async function fetchNodeTopologyMap(): Promise<Record<string, NodeTopologyRecord>> {
  try {
    const response = await fetch(NODE_TOPOLOGY_URL)
    if (!response.ok) {
      throw new Error(`${response.status} ${response.statusText}`)
    }
    return (await response.json()) as Record<string, NodeTopologyRecord>
  } catch {
    return {}
  }
}

function classifyNode(nodeNo: number) {
  if ([1, 3, 6].includes(nodeNo)) {
    return { label: '边界节点', type: 'BOUNDARY', boundaryType: 'BOUNDARY' }
  }
  if (nodeNo >= 7 && nodeNo <= 20) {
    return { label: '断头河节点', type: 'DEAD_END', boundaryType: 'DEAD_END' }
  }
  return { label: '节点', type: 'JUNCTION', boundaryType: 'NONE' }
}

function normalizeLineCoordinates(geometry: RiverFeature['geometry'], sourceIsWebMercator = false) {
  if (!geometry) return []
  if (geometry.type === 'LineString') {
    return normalizeCoordinatePairs(geometry.coordinates as LngLatPair[], sourceIsWebMercator)
  }
  return normalizeCoordinatePairs((geometry.coordinates as LngLatPair[][]).flat(), sourceIsWebMercator)
}

function normalizeCoordinatePairs(coordinates: LngLatPair[], sourceIsWebMercator: boolean) {
  if (!sourceIsWebMercator) return coordinates
  return coordinates.map(([x, y]) => webMercatorToLngLat(x, y))
}

function webMercatorToLngLat(x: number, y: number): LngLatPair {
  const radius = 6378137
  const lng = (x / radius) * (180 / Math.PI)
  const lat = (2 * Math.atan(Math.exp(y / radius)) - Math.PI / 2) * (180 / Math.PI)
  return [lng, lat]
}

function isWebMercatorGeoJson(geojson: RiverGeoJson) {
  const crsName = geojson.crs?.properties?.name?.toLowerCase() ?? ''
  if (crsName.includes('3857') || crsName.includes('webmercator')) {
    return true
  }
  const firstCoordinate = geojson.features
    .flatMap((feature) => normalizeLineCoordinates(feature.geometry, false))
    .find(Boolean)
  return Boolean(firstCoordinate && (Math.abs(firstCoordinate[0]) > 180 || Math.abs(firstCoordinate[1]) > 90))
}

function toFiniteNumber(value: unknown) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) ? numberValue : undefined
}

function calculateLengthMeters(line: LngLatPair[]) {
  let total = 0
  for (let index = 1; index < line.length; index += 1) {
    const from = line[index - 1]
    const to = line[index]
    if (from && to) {
      total += distanceMeters(from, to)
    }
  }
  return total
}

function distanceMeters(from: LngLatPair, to: LngLatPair) {
  const earthRadius = 6371000
  const fromLat = toRadians(from[1])
  const toLat = toRadians(to[1])
  const deltaLat = toRadians(to[1] - from[1])
  const deltaLng = toRadians(to[0] - from[0])
  const a =
    Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
    Math.cos(fromLat) * Math.cos(toLat) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

function toRadians(value: number) {
  return (value * Math.PI) / 180
}

function fallbackHydroScenarioSnapshot(): HydroScenarioSnapshot {
  const timestamp = new Date().toISOString()
  return {
    channels: {
      taihu: { value: 14.02, timestamp },
      'canal-north': { value: 132.04, timestamp },
      'canal-south': { value: 14.03, timestamp },
    },
  }
}
