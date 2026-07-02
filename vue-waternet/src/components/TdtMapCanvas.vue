<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { usePlatformStore } from '@/stores/platform'
import type { HydraulicStructure, SensorSnapshot, WaterNode } from '@/types/platform'

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

const token = (import.meta.env.VITE_TIANDITU_TOKEN ?? '').trim()

const stationByNode = computed(() => {
  return new Map(store.stations.map((station) => [station.nodeCode, station]))
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
  void initializeMap()
})

onBeforeUnmount(() => {
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
    status.value = 'ready'
    statusMessage.value = ''
    drawNetwork()
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

function drawNetwork() {
  if (!map || !window.T) return
  const T = window.T
  clearLayers()

  const allPoints: any[] = []

  if (store.mapLayers.rivers) {
    for (const segment of store.network.segments) {
      const points = segment.coordinates.map((coordinate) => {
        const point = new T.LngLat(coordinate.lng, coordinate.lat)
        allPoints.push(point)
        return point
      })
      const line = new T.Polyline(points, {
        color: segment.widthMeters >= 80 ? '#21d4ff' : '#35f2bd',
        weight: segment.widthMeters >= 80 ? 6 : 4,
        opacity: 0.9,
        lineStyle: 'solid',
      })
      map.addOverLay(line)
      overlays.push(line)
    }
  }

  if (store.mapLayers.nodes) {
    for (const node of store.network.nodes) {
      const point = new T.LngLat(node.lng, node.lat)
      const marker = new T.Marker(point)
      marker.addEventListener?.('click', () => {
        const infoWindow = new T.InfoWindow(buildNodePopup(node, stationByNode.value.get(node.code)), {
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

function buildNodePopup(node: WaterNode, station?: SensorSnapshot) {
  const stationHtml = station
    ? `
      <div class="tdt-popup-row"><span>水位</span><strong>${station.waterLevel.toFixed(2)} m</strong></div>
      <div class="tdt-popup-row"><span>流量</span><strong>${station.flow.toFixed(1)} m3/s</strong></div>
      <div class="tdt-popup-row"><span>流速</span><strong>${station.velocity.toFixed(2)} m/s</strong></div>
      <div class="tdt-popup-row"><span>状态</span><strong>${station.status}</strong></div>
    `
    : '<div class="tdt-popup-row"><span>测站</span><strong>未关联实时数据</strong></div>'

  return `
    <div class="tdt-popup">
      <h3>${node.name}</h3>
      <div class="tdt-popup-row"><span>节点编码</span><strong>${node.code}</strong></div>
      <div class="tdt-popup-row"><span>节点类型</span><strong>${node.type}</strong></div>
      <div class="tdt-popup-row"><span>边界类型</span><strong>${node.boundaryType}</strong></div>
      ${stationHtml}
    </div>
  `
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
</script>

<template>
  <div class="tdt-map-wrap">
    <div ref="mapContainer" class="tdt-map"></div>
    <div v-if="status !== 'ready'" class="tdt-map-state">
      <strong>{{ status === 'missing-token' ? '等待天地图密钥' : '天地图状态' }}</strong>
      <span>{{ statusMessage }}</span>
      <small>河网、节点与弹窗逻辑已接入；配置密钥后会自动加载真实底图。</small>
    </div>
  </div>
</template>
