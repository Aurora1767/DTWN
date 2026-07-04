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
  mockWaterQuantityOverview,
} from '@/data/mock'
import type {
  EnvironmentSnapshot,
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
  const latestSimulation = ref<SimulationResult>(mockSimulation)
  const loadingSimulation = ref(false)
  const mapLayers = ref<MapLayerState>({
    rivers: true,
    nodes: true,
    structures: true,
    warnings: true,
    simulation: true,
  })

  const selectedFeature = ref<SelectedFeature | null>(null)
  const segmentProfile = ref<SegmentProfile | null>(null)
  const nodeSeries = ref<NodeHydrologySeries | null>(null)
  const insightLoading = ref(false)
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
        const series = await fetchNodeSeries(feature.code, 72)
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
    ] = await Promise.all([
      fetchNetworkOverview(),
      fetchRiverNetworkSegments(),
      fetchWaterNodes(),
      fetchStations(),
      fetchWarnings(),
      fetchEnvironmentSnapshot(),
      fetchRainfallOverview(),
      fetchWaterQuantityOverview(),
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
    loadDashboard,
    refreshEnvironment,
    startEnvironmentPolling,
    stopEnvironmentPolling,
    refreshWaterQuantity,
    startWaterQuantityPolling,
    stopWaterQuantityPolling,
    startSimulation,
    setViewMode,
    setMapLayer,
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
