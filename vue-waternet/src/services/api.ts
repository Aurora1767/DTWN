import { mockDispatchPlans, mockHistoryRecords, mockNetwork, mockSimulation, mockStations, mockWarnings } from '@/data/mock'
import type {
  ApiResponse,
  DispatchPlan,
  HistoricalSensorRecord,
  NetworkOverview,
  SensorSnapshot,
  SimulationRequest,
  SimulationResult,
  WarningEvent,
} from '@/types/platform'

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080/api'

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
