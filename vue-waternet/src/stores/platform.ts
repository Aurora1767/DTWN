import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { fetchNetworkOverview, fetchStations, fetchWarnings, runSimulation } from '@/services/api'
import { mockNetwork, mockSimulation, mockStations, mockWarnings } from '@/data/mock'
import type {
  MapLayerKey,
  MapLayerState,
  NetworkOverview,
  SensorSnapshot,
  SimulationRequest,
  SimulationResult,
  ViewMode,
  WarningEvent,
} from '@/types/platform'

export const usePlatformStore = defineStore('platform', () => {
  const viewMode = ref<ViewMode>('2d')
  const network = ref<NetworkOverview>(mockNetwork)
  const stations = ref<SensorSnapshot[]>(mockStations)
  const warnings = ref<WarningEvent[]>(mockWarnings)
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

  async function loadDashboard() {
    const [networkData, stationData, warningData] = await Promise.all([
      fetchNetworkOverview(),
      fetchStations(),
      fetchWarnings(),
    ])
    network.value = networkData
    stations.value = stationData
    warnings.value = warningData
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
    latestSimulation,
    loadingSimulation,
    mapLayers,
    averageWaterLevel,
    totalFlow,
    highRiskCount,
    loadDashboard,
    startSimulation,
    setViewMode,
    setMapLayer,
  }
})
