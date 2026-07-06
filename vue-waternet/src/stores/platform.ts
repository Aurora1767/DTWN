import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  fetchEnvironmentSnapshot,
  fetchEnvironmentSnapshotLive,
  fetchNetworkOverview,
  fetchNodeSeries,
  fetchRainfallOverview,
  fetchRainfallOverviewLive,
  fetchRiverNetworkSegments,
  fetchSegmentProfile,
  fetchStations,
  fetchWarnings,
  fetchWaterNodes,
  fetchWaterQualityLatest,
  fetchWaterQualityLatestLive,
  fetchWaterQuantityOverview,
  fetchWaterQuantityOverviewLive,
  runSimulation,
} from '@/services/api'
import {
  mockNetwork,
  mockRainfallOverview,
  mockSimulation,
  mockStations,
  mockWarnings,
  mockWaterQualityOverview,
  mockWaterQuantityOverview,
} from '@/data/mock'
import type {
  EnvironmentSnapshot,
  GateInfo,
  MapLayerKey,
  MapLayerState,
  NetworkOverview,
  NodeHydrologySeries,
  RainfallOverview,
  RiverSegment,
  SegmentProfile,
  SelectedFeature,
  SensorSnapshot,
  SimulationRequest,
  SimulationResult,
  ViewMode,
  WarningEvent,
  WaterQualityOverview,
  WaterQuantityOverview,
  WaterNode,
} from '@/types/platform'

export const usePlatformStore = defineStore('platform', () => {
  const viewMode = ref<ViewMode>('3d')
  const network = ref<NetworkOverview>(mockNetwork)
  const stations = ref<SensorSnapshot[]>(mockStations)
  const warnings = ref<WarningEvent[]>(mockWarnings)
  const environment = ref<EnvironmentSnapshot>({
    weatherText: '--',
    temperature: '--',
    windSpeed: '--',
    windScale: '--',
    observedAt: '',
  })
  const environmentLive = ref(false)
  let environmentTimer: ReturnType<typeof setInterval> | undefined
  const rainfallHistory = ref<RainfallOverview>(mockRainfallOverview)
  const rainfallLive = ref(mockRainfallOverview.live)
  const waterQuantity = ref<WaterQuantityOverview>(mockWaterQuantityOverview)
  const waterQuantityLive = ref(false)
  let waterQuantityTimer: ReturnType<typeof setInterval> | undefined
  const waterQuality = ref<WaterQualityOverview>(mockWaterQualityOverview)
  const waterQualityLive = ref(mockWaterQualityOverview.live)
  let waterQualityTimer: ReturnType<typeof setInterval> | undefined
  const latestSimulation = ref<SimulationResult>(mockSimulation)
  const loadingSimulation = ref(false)
  const mapLayers = ref<MapLayerState>({
    rivers: true,
    nodes: true,
    structures: true,
    warnings: true,
    simulation: true,
    surveyPoints: false,
  })

  const selectedFeature = ref<SelectedFeature | null>(null)
  const segmentProfile = ref<SegmentProfile | null>(null)
  const nodeSeries = ref<NodeHydrologySeries | null>(null)
  const insightLoading = ref(false)
  const selectedSurveyPointId = ref<number | null>(null)
  let insightRequestId = 0

  async function selectFeature(feature: SelectedFeature | null) {
    selectedFeature.value = feature
    const requestId = insightRequestId + 1
    insightRequestId = requestId

    if (!feature) {
      segmentProfile.value = null
      nodeSeries.value = null
      insightLoading.value = false
      return
    }

    insightLoading.value = true
    try {
      if (feature.type === 'segment') {
        const profile = await fetchSegmentProfile(feature.code)
        if (insightRequestId !== requestId) return
        segmentProfile.value = profile
        nodeSeries.value = null
      } else {
        const series = await fetchNodeSeries(feature.code, 96)
        if (insightRequestId !== requestId) return
        nodeSeries.value = series
        segmentProfile.value = null
      }
    } finally {
      if (insightRequestId === requestId) {
        insightLoading.value = false
      }
    }
  }

  function clearSelection() {
    void selectFeature(null)
  }

  function setSurveyPoint(id: number) {
    selectedSurveyPointId.value = id
  }

  const averageWaterLevel = computed(() => {
    const sum = stations.value.reduce((total, station) => total + station.waterLevel, 0)
    return Number((sum / Math.max(stations.value.length, 1)).toFixed(2))
  })

  const totalFlow = computed(() => {
    const sum = stations.value.reduce((total, station) => total + station.flow, 0)
    return Number(sum.toFixed(1))
  })

  const highRiskCount = computed(() =>
    warnings.value.filter((warning) => ['WARNING', 'DANGER'].includes(warning.level)).length,
  )

  async function refreshEnvironment() {
    const [environmentData, rainfallData] = await Promise.all([
      fetchEnvironmentSnapshotLive(),
      fetchRainfallOverviewLive(),
    ])
    if (environmentData) {
      environment.value = environmentData
      environmentLive.value = Boolean(environmentData.observedAt)
    } else {
      environmentLive.value = false
    }
    if (rainfallData) {
      rainfallHistory.value = rainfallData
      rainfallLive.value = rainfallData.live
    }
  }

  function startEnvironmentPolling(intervalMs = 60_000) {
    stopEnvironmentPolling()
    environmentTimer = setInterval(() => {
      void refreshEnvironment()
    }, intervalMs)
  }

  function stopEnvironmentPolling() {
    if (environmentTimer) {
      clearInterval(environmentTimer)
      environmentTimer = undefined
    }
  }

  async function refreshWaterQuantity() {
    const overview = await fetchWaterQuantityOverviewLive()
    if (!overview) {
      waterQuantityLive.value = false
      return
    }
    waterQuantity.value = overview
    waterQuantityLive.value = overview.live
  }

  function startWaterQuantityPolling(intervalMs = 300_000) {
    stopWaterQuantityPolling()
    waterQuantityTimer = setInterval(() => {
      void refreshWaterQuantity()
    }, intervalMs)
  }

  function stopWaterQuantityPolling() {
    if (waterQuantityTimer) {
      clearInterval(waterQuantityTimer)
      waterQuantityTimer = undefined
    }
  }

  async function refreshWaterQuality() {
    const overview = await fetchWaterQualityLatestLive()
    if (!overview) {
      waterQualityLive.value = false
      return
    }
    waterQuality.value = overview
    waterQualityLive.value = overview.live
  }

  function startWaterQualityPolling(intervalMs = 60_000) {
    stopWaterQualityPolling()
    waterQualityTimer = setInterval(() => {
      void refreshWaterQuality()
    }, intervalMs)
  }

  function stopWaterQualityPolling() {
    if (waterQualityTimer) {
      clearInterval(waterQualityTimer)
      waterQualityTimer = undefined
    }
  }

  async function loadDashboard() {
    const [
      networkData,
      riverSegments,
      waterNodes,
      stationData,
      warningData,
      environmentData,
      rainfallData,
      waterQuantityData,
      waterQualityData,
    ] = await Promise.all([
      fetchNetworkOverview(),
      fetchRiverNetworkSegments(),
      fetchWaterNodes(),
      fetchStations(),
      fetchWarnings(),
      fetchEnvironmentSnapshot(),
      fetchRainfallOverview(),
      fetchWaterQuantityOverview(),
      fetchWaterQualityLatest(),
    ])
    network.value = {
      ...networkData,
      segments:
        riverSegments.length > 0
          ? mergeSegmentHydrology(riverSegments, networkData.segments)
          : networkData.segments,
      nodes: waterNodes.length > 0 ? mergeNodeHydrology(waterNodes, networkData.nodes) : networkData.nodes,
    }
    stations.value = stationData
    warnings.value = warningData
    environment.value = environmentData
    environmentLive.value = Boolean(environmentData.observedAt)
    rainfallHistory.value = rainfallData
    rainfallLive.value = rainfallData.live
    waterQuantity.value = waterQuantityData
    waterQuantityLive.value = waterQuantityData.live
    waterQuality.value = waterQualityData
    waterQualityLive.value = waterQualityData.live
  }

  async function startSimulation(request: SimulationRequest) {
    loadingSimulation.value = true
    try {
      latestSimulation.value = await runSimulation(request)
    } finally {
      loadingSimulation.value = false
    }
  }

  function setViewMode(mode: ViewMode) {
    viewMode.value = mode
  }

  function setMapLayer(layer: MapLayerKey, enabled: boolean) {
    mapLayers.value[layer] = enabled
  }

  const GATE_STORAGE_KEY = 'waternet:gate-openings'

  function loadSavedOpenings(): Record<number, number> {
    try {
      return JSON.parse(localStorage.getItem(GATE_STORAGE_KEY) ?? '{}')
    } catch {
      return {}
    }
  }

  function saveOpenings() {
    const record: Record<number, number> = {}
    for (const g of gates.value) record[g.id] = g.openingPct
    localStorage.setItem(GATE_STORAGE_KEY, JSON.stringify(record))
  }

  const savedOpenings = loadSavedOpenings()
  const gates = ref<GateInfo[]>([
    { id: 1, name: '水闸1', gateAssetId: 5018623, pierAssetId: 5018612, lng: 120.34826, lat: 31.46240, height: 0, openingPct: savedOpenings[1] ?? 0 },
    { id: 2, name: '水闸2', gateAssetId: 5018638, pierAssetId: 5018637, lng: 120.34282, lat: 31.47871, height: 0, openingPct: savedOpenings[2] ?? 0 },
    { id: 3, name: '水闸3', gateAssetId: 5018648, pierAssetId: 5018643, lng: 120.34950, lat: 31.47569, height: 0, openingPct: savedOpenings[3] ?? 0 },
    { id: 4, name: '水闸4', gateAssetId: 5018660, pierAssetId: 5018659, lng: 120.39387, lat: 31.48177, height: 0, openingPct: savedOpenings[4] ?? 0 },
    { id: 5, name: '水闸5', gateAssetId: 5018670, pierAssetId: 5018667, lng: 120.33061, lat: 31.53142, height: 0, openingPct: savedOpenings[5] ?? 0 },
    { id: 6, name: '水闸6', gateAssetId: 5018696, pierAssetId: 5018694, lng: 120.35075, lat: 31.48702, height: 0, openingPct: savedOpenings[6] ?? 0 },
    { id: 7, name: '水闸7', gateAssetId: 5018717, pierAssetId: 5018708, lng: 120.36081, lat: 31.49217, height: 0, openingPct: savedOpenings[7] ?? 0 },
    { id: 8, name: '水闸8', gateAssetId: 5018815, pierAssetId: 5018804, lng: 120.36886, lat: 31.50488, height: 0, openingPct: savedOpenings[8] ?? 0 },
  ])

  const avgGateOpening = computed(() => {
    if (!gates.value.length) return 0
    return Math.round(gates.value.reduce((sum, g) => sum + g.openingPct, 0) / gates.value.length)
  })

  const activeGateId = ref<number | null>(null)

  function setActiveGate(id: number | null) {
    activeGateId.value = id
  }

  function setGateOpening(id: number, pct: number) {
    const gate = gates.value.find((g) => g.id === id)
    if (gate) {
      gate.openingPct = Math.max(0, Math.min(100, pct))
      saveOpenings()
      // Directly drive the Cesium model without relying on watch
      const updater = (window as any).__updateGateHeight
      if (typeof updater === 'function') updater(gate.gateAssetId, gate.openingPct)
    }
  }

  return {
    viewMode,
    network,
    stations,
    warnings,
    environment,
    environmentLive,
    rainfallHistory,
    rainfallLive,
    waterQuantity,
    waterQuantityLive,
    waterQuality,
    waterQualityLive,
    latestSimulation,
    loadingSimulation,
    mapLayers,
    selectedFeature,
    segmentProfile,
    nodeSeries,
    insightLoading,
    averageWaterLevel,
    totalFlow,
    highRiskCount,
    selectFeature,
    clearSelection,
    selectedSurveyPointId,
    setSurveyPoint,
    loadDashboard,
    refreshEnvironment,
    startEnvironmentPolling,
    stopEnvironmentPolling,
    refreshWaterQuantity,
    startWaterQuantityPolling,
    stopWaterQuantityPolling,
    refreshWaterQuality,
    startWaterQualityPolling,
    stopWaterQualityPolling,
    startSimulation,
    setViewMode,
    setMapLayer,
    gates,
    avgGateOpening,
    setGateOpening,
    activeGateId,
    setActiveGate,
  }
})

function mergeSegmentHydrology(localSegments: RiverSegment[], apiSegments: RiverSegment[]) {
  const byCode = new Map(apiSegments.map((segment) => [segment.code, segment]))
  const byReachId = new Map(
    apiSegments
      .filter((segment) => typeof segment.reachId === 'number')
      .map((segment) => [segment.reachId as number, segment]),
  )

  return localSegments.map((segment) => {
    const apiSegment = byCode.get(segment.code) ?? (segment.reachId ? byReachId.get(segment.reachId) : undefined)
    return apiSegment
      ? {
          ...segment,
          code: apiSegment.code,
          name: apiSegment.name ?? segment.name,
          reachId: apiSegment.reachId ?? segment.reachId,
          hydrologyStats: apiSegment.hydrologyStats,
          startNodeCode: apiSegment.startNodeCode ?? segment.startNodeCode,
          endNodeCode: apiSegment.endNodeCode ?? segment.endNodeCode,
        }
      : segment
  })
}

function mergeNodeHydrology(localNodes: WaterNode[], apiNodes: WaterNode[]) {
  const byCode = new Map(apiNodes.map((node) => [node.code, node]))
  return localNodes.map((node) => {
    const apiNode = byCode.get(node.code)
    return apiNode
      ? {
          ...node,
          latestHydrology: apiNode.latestHydrology,
          connectedNodeCodes: apiNode.connectedNodeCodes ?? node.connectedNodeCodes,
          connectedSegmentCodes: apiNode.connectedSegmentCodes ?? node.connectedSegmentCodes,
          connectedReachIds: apiNode.connectedReachIds ?? node.connectedReachIds,
        }
      : node
  })
}
