export type ViewMode = '2d' | '3d'

export type MapLayerKey = 'rivers' | 'nodes' | 'structures' | 'warnings' | 'simulation' | 'surveyPoints'

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
  hydrologyStats?: SegmentHydrologyStats
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
  latestHydrology?: NodeLatestHydrology
}

export interface SegmentHydrologyStats {
  maxFlow?: number | null
  minFlow?: number | null
  maxWaterLevel?: number | null
  minWaterLevel?: number | null
  profileHour?: number | null
  sampleCount?: number | null
}

export interface NodeLatestHydrology {
  hour?: number | null
  waterLevel?: number | null
  flow?: number | null
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

export type ScenarioWarningStatus = 'PENDING' | 'CONFIRMED' | 'PROCESSED'

export interface ScenarioWarningAlert {
  id: string
  recordId: number | null
  sourceKey: string
  targetName: string
  metric: string
  value: number
  threshold: number
  level: 'WARNING' | 'DANGER'
  status: ScenarioWarningStatus
  timeLabel: string
  triggeredAt: string
  confirmedAt?: string
  processedAt?: string
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

export interface SegmentProfilePoint {
  sectionNo: number
  distanceMeters: number
  waterLevel: number | null
  flow: number | null
}

export interface SegmentProfile {
  segmentCode: string
  reachId: number | null
  startNodeCode: string | null
  endNodeCode: string | null
  profileHour: number | null
  points: SegmentProfilePoint[]
}

export interface NodeHydrologyPoint {
  hour: number
  waterLevel: number | null
  flow: number | null
}

export interface NodeHydrologySeries {
  nodeCode: string
  points: NodeHydrologyPoint[]
}

export type SelectedFeature =
  | { type: 'node'; code: string; name: string }
  | { type: 'segment'; code: string; name: string }

export interface EnvironmentSnapshot {
  weatherText: string
  temperature: string
  windSpeed: string
  windScale: string
  observedAt: string
}

export interface WeatherForecast {
  daily: DailyForecast[]
  minutely: MinutelyPrecip[]
  updatedAt: string
}

export interface DailyForecast {
  fxDate: string
  textDay: string
  textNight: string
  tempMax: string
  tempMin: string
  windSpeedDay: string
  windScaleDay: string
  windDirDay: string
  precip: string
  humidity: string
}

export interface MinutelyPrecip {
  fxTime: string
  precip: string
  type: string
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

export type WaterQualityLevel = 'I类' | 'II类' | 'III类' | 'IV类' | 'V类'

export interface WaterQualityPoint {
  nodeId: number
  ph: number
  dissolvedOxygen: number
  permanganateIndex: number
  ammoniaNitrogen: number
  totalPhosphorus: number
  chemicalOxygenDemand: number
  bod5: number
  level: WaterQualityLevel
}

export interface WaterQualityOverview {
  status: string
  recordTime: string
  live: boolean
  summary: Record<WaterQualityLevel, number>
  nodes: WaterQualityPoint[]
}

export interface WaterQualityHistoryPoint extends WaterQualityPoint {
  time: string
}

export interface WaterQualityNodeHistory {
  nodeId: number
  points: WaterQualityHistoryPoint[]
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

export interface HydroBoundarySnapshot {
  timestamp: string
  boundaryValues: Record<string, number>
}

export type SimulationBoundarySeries = Record<string, number[]>

export interface RiverNetworkNodeResult {
  nodeId: number
  waterLevel: number
  netFlow: number
}

export interface RiverNetworkReachResult {
  reachId: number
  startNode: number
  endNode: number
  length: number
  width: number
  inletFlow: number
  outletFlow: number
  avgWaterLevel: number
  maxWaterLevel: number
  minWaterLevel: number
  avgFlow: number
}

export interface RiverNetworkReachProfile {
  reachId: number
  startNode: number
  endNode: number
  label: string
  length: number
  distances: number[]
  waterLevels: number[]
  flows: number[]
}

export interface RiverNetworkReachHistory {
  reachId: number
  startNode: number
  endNode: number
  label: string
  inletFlows: number[]
  outletFlows: number[]
  inletWaterLevels: number[]
  outletWaterLevels: number[]
}

export interface RiverNetworkNodeHistory {
  nodeId: number
  waterLevels: number[]
  netFlows: number[]
}

export interface RiverNetworkForecastResult {
  status: string
  forecastHours: number
  nSteps: number
  dt: number
  simulatedSeconds: number
  timestamp: string
  nodeHeads: Record<string, number>
  nodeFlows: Record<string, number>
  boundaryValues: Record<string, number>
  nodes: RiverNetworkNodeResult[]
  nodeHistories: RiverNetworkNodeHistory[]
  reaches: RiverNetworkReachResult[]
  reachProfiles: RiverNetworkReachProfile[]
  reachHistories?: RiverNetworkReachHistory[]
}

export type ScenarioRecordType = 'forecast' | 'simulation' | 'plan'

export interface ForecastRecordSummary {
  id: number
  calculatedAt: string
  forecastHours: number
  recordType?: ScenarioRecordType
  simulationName?: string | null
}

export interface SimulationRecordSettings {
  simulationName: string
  simulationStartAt: string
  forecastHours: number
  dt: number
  nSteps: number
  status: string
  boundaryValues: Record<string, number>
  boundarySeries?: SimulationBoundarySeries | null
  gateOpenings?: Record<string, number> | null
}

export interface ForecastRecordDetail extends ForecastRecordSummary {
  settings?: SimulationRecordSettings | null
  result: RiverNetworkForecastResult
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface GateInfo {
  id: number
  name: string
  gateAssetId: number
  pierAssetId: number
  lng: number
  lat: number
  height: number
  openingPct: number
}
