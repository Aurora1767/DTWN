import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  fetchEnvironmentSnapshot,
  fetchEnvironmentSnapshotLive,
  fetchNetworkOverview,
  fetchRainfallOverview,
  fetchRainfallOverviewLive,
  fetchRiverNetworkSegments,
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
  RainfallOverview,
  SensorSnapshot,
  SimulationRequest,
  SimulationResult,
  ViewMode,
  WarningEvent,
  WaterQuantityOverview,
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
      segments: riverSegments.length > 0 ? riverSegments : networkData.segments,
      nodes: waterNodes.length > 0 ? waterNodes : networkData.nodes,
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
    averageWaterLevel,
    totalFlow,
    highRiskCount,
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
