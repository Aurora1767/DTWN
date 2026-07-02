import { mockDispatchPlans, mockHistoryRecords, mockNetwork, mockSimulation, mockStations, mockWarnings } from '@/data/mock'
import type {
  ApiResponse,
  DispatchPlan,
  HistoricalSensorRecord,
  NetworkOverview,
  RiverSegment,
  SensorSnapshot,
  SimulationRequest,
  SimulationResult,
  WarningEvent,
} from '@/types/platform'

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api'
const RIVER_NETWORK_URL = `${import.meta.env.BASE_URL}data/river-network.geojson`

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
    return geojson.features
      .map((feature, index) => toRiverSegment(feature, index))
      .filter((segment): segment is RiverSegment => Boolean(segment))
  } catch {
    return []
  }
}

export function fetchStations() {
  return getData<SensorSnapshot[]>('/realtime/stations', mockStations)
}

export function fetchWarnings() {
  return getData<WarningEvent[]>('/warnings', mockWarnings)
}

export function fetchDispatchPlans() {
  return getData<DispatchPlan[]>('/scenarios/plans', mockDispatchPlans)
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

interface RiverGeoJson {
  features: RiverFeature[]
}

interface RiverFeature {
  properties?: {
    code?: string
    name?: string
  }
  geometry?: {
    type: 'LineString' | 'MultiLineString'
    coordinates: LngLatPair[] | LngLatPair[][]
  }
}

type LngLatPair = [number, number]

function toRiverSegment(feature: RiverFeature, index: number): RiverSegment | null {
  const line = normalizeLineCoordinates(feature.geometry)
  if (line.length < 2) return null

  const code = feature.properties?.code ?? `REAL_RIVER_${String(index + 1).padStart(2, '0')}`
  return {
    code,
    name: feature.properties?.name ?? `河段 ${index + 1}`,
    lengthMeters: Math.round(calculateLengthMeters(line)),
    widthMeters: 24,
    manningN: 0.03,
    startNodeCode: `${code}_START`,
    endNodeCode: `${code}_END`,
    coordinates: line.map(([lng, lat]) => ({ lng, lat })),
  }
}

function normalizeLineCoordinates(geometry: RiverFeature['geometry']) {
  if (!geometry) return []
  if (geometry.type === 'LineString') {
    return geometry.coordinates as LngLatPair[]
  }
  return (geometry.coordinates as LngLatPair[][]).flat()
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
