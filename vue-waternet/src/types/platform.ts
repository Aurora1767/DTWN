export type ViewMode = '2d' | '3d'

export type MapLayerKey = 'rivers' | 'nodes' | 'structures' | 'warnings' | 'simulation'

export type MapLayerState = Record<MapLayerKey, boolean>

export interface Coordinate {
  lng: number
  lat: number
}

export interface RiverSegment {
  code: string
  name: string
  reachId?: number
  lengthMeters: number
  widthMeters: number
  manningN: number
  chezy?: number
  dx?: number
  bedElevation?: number
  startNodeCode: string
  endNodeCode: string
  coordinates: Coordinate[]
}

export interface WaterNode {
  code: string
  name: string
  type: string
  lng: number
  lat: number
  initialWaterLevel: number
  boundaryType: string
  connectedNodeCodes?: string[]
  connectedSegmentCodes?: string[]
  connectedReachIds?: number[]
}

export interface HydraulicStructure {
  code: string
  name: string
  type: string
  nodeCode: string
  designFlow: number
  status: string
  lng: number
  lat: number
}

export interface SensorSnapshot {
  stationCode: string
  stationName: string
  nodeCode: string
  waterLevel: number
  flow: number
  velocity: number
  rainfall: number
  status: string
  observedAt: string
}

export interface HistoricalSensorRecord {
  stationCode: string
  stationName: string
  nodeCode: string
  observedAt: string
  waterLevel: number
  flow: number
  velocity: number
  rainfall: number
  status: string
}

export interface WarningEvent {
  id: string
  targetCode: string
  targetName: string
  metric: string
  value: number
  threshold: number
  level: string
  status: string
  triggeredAt: string
}

export interface DispatchPlan {
  code: string
  name: string
  type: string
  triggerCondition: string
  measures: string[]
  expectedEffect: string
  riskLevel: string
  relatedSegments: string[]
  updatedAt: string
}

export interface SegmentParameter {
  code: string
  length: number
  width: number
  manningN: number
}

export interface SimulationRequest {
  scenarioName: string
  boundaryType: string
  upstreamFlow: number
  downstreamLevel: number
  timeStep: number
  steps: number
  segments: SegmentParameter[]
}

export interface TimeSeriesPoint {
  step: number
  timeSeconds: number
  waterLevel: number
  flow: number
  velocity: number
  riskLevel: string
}

export interface SegmentResult {
  segmentCode: string
  segmentName: string
  maxWaterLevel: number
  maxFlow: number
  averageVelocity: number
  series: TimeSeriesPoint[]
}

export interface SimulationResult {
  runId: string
  scenarioName: string
  status: string
  runnerType: string
  startedAt: string
  finishedAt: string
  results: SegmentResult[]
}

export interface NetworkOverview {
  segments: RiverSegment[]
  nodes: WaterNode[]
  structures: HydraulicStructure[]
}

export interface EnvironmentSnapshot {
  weatherText: string
  temperature: string
  windSpeed: string
  windScale: string
  observedAt: string
}

export interface WaterStationSnapshot {
  stationCode: string
  stationName: string
  waterLevel: number
  flowRate: number
  observedAt: string
}

export interface WaterHistoryPoint {
  date: string
  waterLevel: number
  flowRate: number
  rainfall: number
}

export interface WaterQuantityOverview {
  status: string
  timestamp: string
  live: boolean
  stations: WaterStationSnapshot[]
  historyByCode: Record<string, WaterHistoryPoint[]>
}

export interface RainfallHistoryPoint {
  time: string
  upstream: number
  downstream: number
  rainfall: number
}

export interface RainfallOverview {
  status: string
  timestamp: string
  live: boolean
  points: RainfallHistoryPoint[]
}

export type HydroChannelKey = 'taihu' | 'canal-north' | 'canal-south'

export interface HydroChannelValue {
  value: number
  timestamp: string
}

export interface HydroScenarioSnapshot {
  channels: Record<HydroChannelKey, HydroChannelValue>
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}
