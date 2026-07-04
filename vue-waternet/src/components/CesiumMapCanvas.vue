<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import {
  Cartesian2,
  Cartesian3,
  Color,
  ColorMaterialProperty,
  ConstantProperty,
  GeoJsonDataSource,
  ImageryLayer,
  Ion,
  Math as CesiumMath,
  PolylineArrowMaterialProperty,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  Terrain,
  UrlTemplateImageryProvider,
  VerticalOrigin,
  Viewer,
} from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'

import { usePlatformStore } from '@/stores/platform'
import type { RiverSegment, SensorSnapshot, WaterNode } from '@/types/platform'

const store = usePlatformStore()
const cesiumContainer = ref<HTMLDivElement | null>(null)
const layers = reactive({
  surfaces: true,
  shorelines: true,
  pipeShorelines: true,
  pipeSurfaces: true,
  flowArrows: true,
})
const selectedFeature = ref<SelectedFeature | null>(null)
const detailPanelPosition = ref({ x: 0, y: 0 })

let viewer: Viewer | undefined
let surfaceDataSource: GeoJsonDataSource | undefined
let shorelineDataSource: GeoJsonDataSource | undefined
let pipeShorelinesDataSource: GeoJsonDataSource | undefined
let pipeSurfacesDataSource: GeoJsonDataSource | undefined
let nodeEntities: any[] = []
let segmentEntities: any[] = []
let nodeHitEntities: any[] = []
let segmentHitEntities: any[] = []
let flowArrowEntities: any[] = []
let pickHandler: ScreenSpaceEventHandler | undefined
let imageryReadyLogged = false
let tilesIdleLogged = false
const hiddenSegmentColor = Color.fromCssColorString('#00f2ff').withAlpha(0)
const activeSegmentColor = Color.fromCssColorString('#fff06a').withAlpha(0.98)
const hitAreaColor = Color.fromCssColorString('#ffffff').withAlpha(0.05)
const defaultNodeOutlineColor = Color.WHITE
const activeNodeOutlineColor = Color.fromCssColorString('#fff06a')
const defaultNodeOutlineWidth = 2
const activeNodeOutlineWidth = 4

const cesiumIonToken = (import.meta.env.VITE_CESIUM_ION_TOKEN ?? '').trim()
const tiandituToken = (import.meta.env.VITE_TIANDITU_TOKEN ?? '').trim()
const surfacesUrl       = `${import.meta.env.BASE_URL}data/water-surfaces.geojson`
const shorelinesUrl     = `${import.meta.env.BASE_URL}data/water-shorelines.geojson`
const pipeShorelinesUrl = `${import.meta.env.BASE_URL}data/pipe-shorelines.geojson`
const pipeSurfacesUrl   = `${import.meta.env.BASE_URL}data/pipe-surfaces.geojson`
const NODE_LABELS: Record<string, string> = {
  N01: '太湖',
  N03: '京杭大运河南',
  N06: '京杭大运河北',
}
const stationByNode = computed(() => new Map(store.stations.map((station) => [station.nodeCode, station])))
const nodeByCode = computed(() => new Map(store.network.nodes.map((node) => [node.code, node])))
onMounted(() => {
  void initializeCesium()
})

onBeforeUnmount(() => {
  pickHandler?.destroy()
  pickHandler = undefined
  viewer?.destroy()
  viewer = undefined
})

watch(
  () => store.network.nodes,
  () => {
    drawNodeEntities()
  },
  { deep: true },
)

watch(
  () => store.network.segments,
  () => {
    drawSegmentEntities()
  },
  { deep: true },
)

watch(
  () => [store.mapLayers.rivers, store.mapLayers.nodes],
  () => {
    syncNetworkLayerVisibility()
  },
  { deep: true },
)

watch(
  () => selectedFeature.value,
  () => {
    syncSelectionHighlight()
  },
)

async function initializeCesium() {
  if (!cesiumContainer.value) return

  try {
    if (cesiumIonToken) {
      Ion.defaultAccessToken = cesiumIonToken
    } else {
      console.warn('[waternet] Missing VITE_CESIUM_ION_TOKEN, falling back to ellipsoid terrain')
    }

    viewer = new Viewer(cesiumContainer.value, {
      animation: false,
      baseLayer: false,
      baseLayerPicker: false,
      fullscreenButton: false,
      geocoder: false,
      homeButton: false,
      infoBox: false,
      navigationHelpButton: false,
      sceneModePicker: false,
      selectionIndicator: false,
      terrain: cesiumIonToken ? Terrain.fromWorldTerrain() : undefined,
      timeline: false,
    } as ConstructorParameters<typeof Viewer>[1])

    viewer.scene.globe.baseColor = Color.fromCssColorString('#09223a')
    viewer.scene.globe.depthTestAgainstTerrain = false
    viewer.scene.globe.enableLighting = true
    viewer.scene.globe.maximumScreenSpaceError = 1.2
    if (viewer.scene.skyAtmosphere) {
      viewer.scene.skyAtmosphere.show = true
    }
    viewer.scene.fog.enabled = true
    viewer.scene.highDynamicRange = true
    viewer.shadows = true
    viewer.resolutionScale = Math.min(window.devicePixelRatio || 1, 2)

    initializeBaseImagery()
    bindTileLoadDiagnostics()

    await loadWaterLayers()
    setLayerVisibility()
    drawSegmentEntities()
    drawNodeEntities()
    bindPickHandler()
    syncNetworkLayerVisibility()
    flyToWaterNetwork()
    ;(window as any).__cesiumViewer = viewer
    ;(window as any).getCameraView = () => {
      const cam = (window as any).__cesiumViewer?.camera
      if (!cam) return console.warn('viewer not ready')
      const R = 180 / Math.PI
      console.log(
        'lng:', cam.positionCartographic.longitude * R,
        'lat:', cam.positionCartographic.latitude * R,
        'height:', Math.round(cam.positionCartographic.height),
        'heading:', (cam.heading * R).toFixed(1),
        'pitch:', (cam.pitch * R).toFixed(1)
      )
    }
  } catch (error) {
    console.warn('[waternet] Cesium scene failed to initialize', error)
  }
}

function createTiandituImageryProvider() {
  return new UrlTemplateImageryProvider({
    url:
      'https://t{s}.tianditu.gov.cn/img_w/wmts?' +
      'SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&' +
      'TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}' +
      `&tk=${encodeURIComponent(tiandituToken)}`,
    subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
    maximumLevel: 18,
  })
}

function initializeBaseImagery() {
  if (!viewer) return

  if (cesiumIonToken) {
    const worldImageryLayer = ImageryLayer.fromWorldImagery({})
    worldImageryLayer.readyEvent.addEventListener(() => {
      if (!imageryReadyLogged) {
        imageryReadyLogged = true
        console.info('[waternet] Cesium World Imagery ready')
      }
    })
    worldImageryLayer.errorEvent.addEventListener((error) => {
      console.warn('[waternet] Cesium World Imagery failed to load', error)
    })
    viewer.imageryLayers.add(worldImageryLayer)
    return
  }

  if (tiandituToken) {
    const tiandituLayer = viewer.imageryLayers.addImageryProvider(createTiandituImageryProvider())
    tiandituLayer.readyEvent.addEventListener(() => {
      if (!imageryReadyLogged) {
        imageryReadyLogged = true
        console.info('[waternet] Tianditu imagery ready')
      }
    })
    tiandituLayer.errorEvent.addEventListener((error) => {
      console.warn('[waternet] Tianditu imagery failed to load', error)
    })
  }
}

function bindTileLoadDiagnostics() {
  if (!viewer) return

  viewer.scene.globe.tileLoadProgressEvent.addEventListener((queuedTiles) => {
    if (queuedTiles === 0) {
      if (!tilesIdleLogged) {
        tilesIdleLogged = true
        console.info('[waternet] Globe tiles fully loaded')
      }
      return
    }

    tilesIdleLogged = false
  })
}

async function loadWaterLayers() {
  if (!viewer) return

  surfaceDataSource = await GeoJsonDataSource.load(surfacesUrl, {
    fill: Color.fromCssColorString('#18d8ff').withAlpha(0.26),
    stroke: Color.fromCssColorString('#7dffb2').withAlpha(0.66),
    strokeWidth: 2,
  })
  surfaceDataSource.name = '水面'
  styleSurfaces(surfaceDataSource)
  await viewer.dataSources.add(surfaceDataSource)

  shorelineDataSource = await GeoJsonDataSource.load(shorelinesUrl, {
    clampToGround: true,
    stroke: Color.fromCssColorString('#fff06a').withAlpha(0.88),
    strokeWidth: 3,
  })
  shorelineDataSource.name = '岸线'
  styleShorelines(shorelineDataSource)
  await viewer.dataSources.add(shorelineDataSource)

  try {
    pipeShorelinesDataSource = await GeoJsonDataSource.load(pipeShorelinesUrl, {
      clampToGround: true,
      stroke: Color.fromCssColorString('#ff6ec7').withAlpha(0.9),
      strokeWidth: 3,
    })
    pipeShorelinesDataSource.name = '管网岸线'
    stylePipeShorelines(pipeShorelinesDataSource)
    pipeShorelinesDataSource.show = layers.pipeShorelines
    await viewer.dataSources.add(pipeShorelinesDataSource)

    pipeSurfacesDataSource = await GeoJsonDataSource.load(pipeSurfacesUrl, {
      fill: Color.fromCssColorString('#a020f0').withAlpha(0.18),
      stroke: Color.fromCssColorString('#c84dff').withAlpha(0.75),
      strokeWidth: 2,
    })
    pipeSurfacesDataSource.name = '管网面'
    stylePipeSurfaces(pipeSurfacesDataSource)
    pipeSurfacesDataSource.show = layers.pipeSurfaces
    await viewer.dataSources.add(pipeSurfacesDataSource)
  } catch (err) {
    console.warn('[waternet] 管网图层加载失败，跳过', err)
  }
}

function styleSurfaces(dataSource: GeoJsonDataSource) {
  for (const entity of dataSource.entities.values) {
    if (!entity.polygon) continue
    entity.polygon.material = new ColorMaterialProperty(Color.fromCssColorString('#18d8ff').withAlpha(0.26))
    entity.polygon.outline = new ConstantProperty(true)
    entity.polygon.outlineColor = new ConstantProperty(Color.fromCssColorString('#7dffb2').withAlpha(0.66))
    entity.polygon.outlineWidth = new ConstantProperty(2)
    entity.polygon.height = new ConstantProperty(0)
    entity.polygon.heightReference = new ConstantProperty(1)
  }
}

function styleShorelines(dataSource: GeoJsonDataSource) {
  for (const entity of dataSource.entities.values) {
    if (!entity.polyline) continue
    entity.polyline.material = new ColorMaterialProperty(Color.fromCssColorString('#fff06a').withAlpha(0.88))
    entity.polyline.width = new ConstantProperty(3)
    entity.polyline.clampToGround = new ConstantProperty(true)
  }
}

function stylePipeShorelines(dataSource: GeoJsonDataSource) {
  for (const entity of dataSource.entities.values) {
    if (!entity.polyline) continue
    entity.polyline.material = new ColorMaterialProperty(Color.fromCssColorString('#ff6ec7').withAlpha(0.9))
    entity.polyline.width = new ConstantProperty(2.5)
    entity.polyline.clampToGround = new ConstantProperty(true)
  }
}

function stylePipeSurfaces(dataSource: GeoJsonDataSource) {
  for (const entity of dataSource.entities.values) {
    if (!entity.polygon) continue
    entity.polygon.material = new ColorMaterialProperty(Color.fromCssColorString('#a020f0').withAlpha(0.35))
    entity.polygon.outline = new ConstantProperty(true)
    entity.polygon.outlineColor = new ConstantProperty(Color.fromCssColorString('#c84dff').withAlpha(0.95))
    entity.polygon.outlineWidth = new ConstantProperty(2.5)
    entity.polygon.height = new ConstantProperty(3)
    entity.polygon.extrudedHeight = new ConstantProperty(3)
  }
}



const presetViews: Array<{ label: string; icon: string; destination: [number, number, number]; heading: number; pitch: number }> = [
  { label: '主视角', icon: '⌂', destination: [120.3874, 31.3972, 6152], heading: 349, pitch: -33 },
  { label: '太湖',   icon: '◎', destination: [120.34861, 31.45926, 80],  heading: 355, pitch: -10 },
  { label: '运河北', icon: '↑', destination: [120.32820, 31.53301, 45],  heading: 128, pitch: -5  },
  { label: '运河南', icon: '↓', destination: [120.39675, 31.48028, 74],  heading: 301, pitch: -10 },
]

function flyToPreset(index: number) {
  const preset = presetViews[index]
  if (!viewer || !preset) return
  viewer.camera.flyTo({
    destination: Cartesian3.fromDegrees(...preset.destination),
    orientation: {
      heading: CesiumMath.toRadians(preset.heading),
      pitch: CesiumMath.toRadians(preset.pitch),
      roll: 0,
    },
    duration: 2.2,
  })
}

function flyToWaterNetwork() {
  viewer?.camera.setView({
    destination: Cartesian3.fromDegrees(120.3874, 31.3972, 6152),
    orientation: {
      heading: CesiumMath.toRadians(349),
      pitch: CesiumMath.toRadians(-33),
      roll: 0,
    },
  })
}

function setLayerVisibility() {
  if (surfaceDataSource)        surfaceDataSource.show        = layers.surfaces
  if (shorelineDataSource)      shorelineDataSource.show      = layers.shorelines
  if (pipeShorelinesDataSource) pipeShorelinesDataSource.show = layers.pipeShorelines
  if (pipeSurfacesDataSource)   pipeSurfacesDataSource.show   = layers.pipeSurfaces
  for (const e of flowArrowEntities) {
    if (e.polyline) e.polyline.show = new ConstantProperty(layers.flowArrows)
  }
}

function toggleLayer(layer: keyof typeof layers) {
  layers[layer] = !layers[layer]
  setLayerVisibility()
}

function togglePipe() {
  const next = !(layers.pipeShorelines || layers.pipeSurfaces)
  layers.pipeShorelines = next
  layers.pipeSurfaces   = next
  setLayerVisibility()
}

function drawSegmentEntities() {
  if (!viewer) return

  for (const entity of segmentEntities) {
    viewer.entities.remove(entity)
  }
  for (const entity of segmentHitEntities) {
    viewer.entities.remove(entity)
  }
  segmentEntities = []
  segmentHitEntities = []

  for (const segment of store.network.segments) {
    if (segment.coordinates.length < 2) continue
    const entity = viewer.entities.add({
      id: `segment-${segment.code}`,
      name: segment.name,
      polyline: {
        positions: segment.coordinates.map((coordinate) => Cartesian3.fromDegrees(coordinate.lng, coordinate.lat, 8)),
        width: 15,
        material: hiddenSegmentColor,
        clampToGround: true,
      },
      properties: {
        featureKind: 'segment',
        code: segment.code,
      },
    })
    segmentEntities.push(entity)

    const hitEntity = viewer.entities.add({
      id: `segment-hit-${segment.code}`,
      polyline: {
        positions: segment.coordinates.map((coordinate) => Cartesian3.fromDegrees(coordinate.lng, coordinate.lat, 8)),
        width: 28,
        material: hitAreaColor,
        clampToGround: true,
      },
      properties: {
        featureKind: 'segment',
        code: segment.code,
      },
    })
    segmentHitEntities.push(hitEntity)
  }
  drawFlowArrows()
}

function drawFlowArrows() {
  if (!viewer) return
  for (const e of flowArrowEntities) viewer.entities.remove(e)
  flowArrowEntities = []

  for (const segment of store.network.segments) {
    const coords = segment.coordinates
    if (coords.length < 2) continue

    const flow = segment.hydrologyStats?.maxFlow
    if (flow == null || Math.abs(flow) < 0.01) continue

    // pick midpoint index
    const mid = Math.floor(coords.length / 2)
    const a = coords[flow >= 0 ? mid - 1 : mid]!
    const b = coords[flow >= 0 ? mid : mid - 1]!

    // lerp slightly toward centre so arrow sits on the line
    const t = 0.35
    const p1 = Cartesian3.fromDegrees(
      a.lng + (b.lng - a.lng) * t, a.lat + (b.lat - a.lat) * t, 12)
    const p2 = Cartesian3.fromDegrees(
      a.lng + (b.lng - a.lng) * (1 - t), a.lat + (b.lat - a.lat) * (1 - t), 12)

    // colour by sign: positive = cyan-green, negative = amber
    const col = flow > 0
      ? Color.fromCssColorString('#00ffc8').withAlpha(0.85)
      : Color.fromCssColorString('#ffb830').withAlpha(0.85)

    const entity = viewer.entities.add({
      id: `arrow-${segment.code}`,
      polyline: {
        positions: [p1, p2],
        width: 10,
        material: new PolylineArrowMaterialProperty(col),
        clampToGround: false,
      },
    })
    flowArrowEntities.push(entity)
  }
}

function drawNodeEntities() {
  if (!viewer) return

  for (const entity of nodeEntities) {
    viewer.entities.remove(entity)
  }
  for (const entity of nodeHitEntities) {
    viewer.entities.remove(entity)
  }
  nodeEntities = []
  nodeHitEntities = []

  for (const node of store.network.nodes) {
    const style = getNodeStyle(node)
    const entity = viewer.entities.add({
      id: `node-${node.code}`,
      name: node.name,
      position: Cartesian3.fromDegrees(node.lng, node.lat, 18),
      point: {
        pixelSize: style.size,
        color: style.color,
        outlineColor: Color.WHITE,
        outlineWidth: 2,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
      label: NODE_LABELS[node.code]
        ? {
            text: NODE_LABELS[node.code],
            font: '700 16px "Microsoft YaHei"',
            fillColor: Color.WHITE,
            outlineColor: Color.fromCssColorString('#02192c'),
            outlineWidth: 4,
            showBackground: true,
            backgroundColor: Color.fromCssColorString('#08243d').withAlpha(0.8),
            pixelOffset: new Cartesian2(0, -26),
            verticalOrigin: VerticalOrigin.BOTTOM,
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          }
        : undefined,
      properties: {
        featureKind: 'node',
        code: node.code,
        type: node.type,
        boundaryType: node.boundaryType,
      },
    })
    nodeEntities.push(entity)

    const hitEntity = viewer.entities.add({
      id: `node-hit-${node.code}`,
      position: Cartesian3.fromDegrees(node.lng, node.lat, 18),
      point: {
        pixelSize: Math.max(style.size + 16, 22),
        color: hitAreaColor,
        outlineColor: hitAreaColor,
        outlineWidth: 0,
        disableDepthTestDistance: Number.POSITIVE_INFINITY,
      },
      properties: {
        featureKind: 'node',
        code: node.code,
        type: node.type,
        boundaryType: node.boundaryType,
      },
    })
    nodeHitEntities.push(hitEntity)
  }
}

function bindPickHandler() {
  if (!viewer) return

  pickHandler?.destroy()
  pickHandler = new ScreenSpaceEventHandler(viewer.scene.canvas)
  pickHandler.setInputAction((movement: { position: Cartesian2 }) => {
    const pickedEntity = pickNetworkEntity(movement.position)
    const properties = pickedEntity?.properties
    if (!properties) {
      selectedFeature.value = null
      return
    }

    const featureKind = String(properties.featureKind?.getValue?.() ?? '')
    const code = String(properties.code?.getValue?.() ?? '')
    if (featureKind === 'node') {
      const node = store.network.nodes.find((item) => item.code === code)
      selectedFeature.value = node ? { type: 'node', item: node } : null
      if (node) {
        void store.selectFeature({ type: 'node', code: node.code, name: node.name })
      }
      updateDetailPanelPosition(movement.position)
      return
    }
    if (featureKind === 'segment') {
      const segment = store.network.segments.find((item) => item.code === code)
      selectedFeature.value = segment ? { type: 'segment', item: segment } : null
      if (segment) {
        void store.selectFeature({ type: 'segment', code: segment.code, name: segment.name })
      }
      updateDetailPanelPosition(movement.position)
      return
    }
    selectedFeature.value = null
  }, ScreenSpaceEventType.LEFT_CLICK)
}

function pickNetworkEntity(position: Cartesian2) {
  if (!viewer) return undefined
  const pickedItems = viewer.scene.drillPick(position, 12)
  const nodePick = pickedItems
    .map((item) => item.id)
    .find((entity) => entity?.properties?.featureKind?.getValue?.() === 'node')
  if (nodePick) {
    return nodePick
  }
  const segmentPick = pickedItems
    .map((item) => item.id)
    .find((entity) => entity?.properties?.featureKind?.getValue?.() === 'segment')
  if (segmentPick) {
    return segmentPick
  }
  return viewer.scene.pick(position)?.id
}

function syncNetworkLayerVisibility() {
  for (const entity of nodeEntities) {
    entity.show = store.mapLayers.nodes
  }
  for (const entity of nodeHitEntities) {
    entity.show = store.mapLayers.nodes
  }
  for (const entity of segmentEntities) {
    entity.show = false
  }
  for (const entity of segmentHitEntities) {
    entity.show = store.mapLayers.rivers
  }
}

function syncSelectionHighlight() {
  const selectedNode = selectedFeature.value?.type === 'node' ? selectedFeature.value.item : null
  const selectedSegment = selectedFeature.value?.type === 'segment' ? selectedFeature.value.item : null

  for (const entity of segmentEntities) {
    const isActive = Boolean(selectedSegment && entity.properties?.code?.getValue?.() === selectedSegment.code)
    entity.show = Boolean(store.mapLayers.rivers && isActive)
    entity.polyline!.material = isActive ? activeSegmentColor : hiddenSegmentColor
    entity.polyline!.width = isActive ? 17 : 15
  }

  for (const entity of nodeEntities) {
    const isActive = Boolean(selectedNode && entity.properties?.code?.getValue?.() === selectedNode.code)
    entity.point!.outlineColor = isActive ? activeNodeOutlineColor : defaultNodeOutlineColor
    entity.point!.outlineWidth = isActive ? activeNodeOutlineWidth : defaultNodeOutlineWidth
    entity.point!.pixelSize = isActive && selectedNode ? getNodeStyle(selectedNode).size + 3 : entity.point!.pixelSize
    if (!isActive) {
      const code = entity.properties?.code?.getValue?.()
      const node = store.network.nodes.find((item) => item.code === code)
      if (node) {
        entity.point!.pixelSize = getNodeStyle(node).size
      }
    }
  }
}

function getNodeStyle(node: WaterNode) {
  if (node.type === 'BOUNDARY') {
    return { color: Color.fromCssColorString('#ff6b3d'), size: 12 }
  }
  if (node.type === 'DEAD_END') {
    return { color: Color.fromCssColorString('#ffd84d'), size: 10 }
  }
  return { color: Color.fromCssColorString('#28dfff'), size: 8 }
}

function closeDetailPanel() {
  selectedFeature.value = null
}

function updateDetailPanelPosition(position: Cartesian2) {
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const panelWidth = 360
  const panelHeight = 320
  const gap = 18

  let x = position.x + gap
  let y = position.y - 24

  if (x + panelWidth > viewportWidth - 16) {
    x = Math.max(16, position.x - panelWidth - gap)
  }
  if (y + panelHeight > viewportHeight - 16) {
    y = viewportHeight - panelHeight - 16
  }
  if (y < 16) {
    y = 16
  }

  detailPanelPosition.value = { x, y }
}

function nodeStation(node: WaterNode): SensorSnapshot | undefined {
  return stationByNode.value.get(node.code)
}

function formatNumber(value: number | null | undefined, digits: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value.toFixed(digits) : '--'
}

function formatHour(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? `第 ${value} 小时` : '--'
}

type SelectedFeature = { type: 'node'; item: WaterNode } | { type: 'segment'; item: RiverSegment }
</script>

<template>
  <div class="cesium-map-wrap">
    <div ref="cesiumContainer" class="cesium-canvas-host"></div>


    <div class="cesium-view-presets" aria-label="预设视角">
      <button v-for="(preset, index) in presetViews" :key="preset.label" type="button" :title="preset.label" @click="flyToPreset(index)">
        <span class="preset-icon">{{ preset.icon }}</span>
        <span>{{ preset.label }}</span>
      </button>
    </div>

    <div class="cesium-layer-panel" aria-label="三维图层控制">
      <button type="button" :class="{ active: layers.surfaces }" @click="toggleLayer('surfaces')">水面</button>
      <button type="button" :class="{ active: layers.shorelines }" @click="toggleLayer('shorelines')">岸线</button>
      <span class="layer-divider"></span>
      <button type="button" :class="{ active: layers.pipeShorelines || layers.pipeSurfaces }" @click="togglePipe()">管网</button>
      <span class="layer-divider"></span>
      <button type="button" :class="{ active: layers.flowArrows }" @click="toggleLayer('flowArrows')">流向</button>
    </div>

    <aside
      v-if="selectedFeature"
      class="cesium-detail-panel"
      :style="{ left: `${detailPanelPosition.x}px`, top: `${detailPanelPosition.y}px` }"
    >
      <div class="cesium-detail-header">
        <div>
          <span>{{ selectedFeature.type === 'node' ? 'NODE' : 'SEGMENT' }}</span>
          <strong>{{ selectedFeature.item.name }}</strong>
        </div>
        <button type="button" @click="closeDetailPanel">关闭</button>
      </div>

      <template v-if="selectedFeature.type === 'node'">
        <div class="cesium-detail-row"><span>节点编码</span><strong>{{ selectedFeature.item.code }}</strong></div>
        <div class="cesium-detail-row"><span>节点类型</span><strong>{{ selectedFeature.item.type }}</strong></div>
        <div class="cesium-detail-row"><span>边界类型</span><strong>{{ selectedFeature.item.boundaryType }}</strong></div>
        <div class="cesium-detail-row">
          <span>相邻节点</span>
          <strong>{{ selectedFeature.item.connectedNodeCodes?.join(', ') || '无' }}</strong>
        </div>
        <div class="cesium-detail-row">
          <span>关联河段</span>
          <strong>{{ selectedFeature.item.connectedSegmentCodes?.join(', ') || '无' }}</strong>
        </div>
        <div class="cesium-detail-row">
          <span>测站</span>
          <strong>{{ nodeStation(selectedFeature.item)?.stationName ?? '未关联实时数据' }}</strong>
        </div>
        <div v-if="selectedFeature.item.latestHydrology" class="cesium-detail-row">
          <span>最新小时</span>
          <strong>{{ formatHour(selectedFeature.item.latestHydrology.hour) }}</strong>
        </div>
        <div v-if="selectedFeature.item.latestHydrology" class="cesium-detail-row">
          <span>最新水位</span>
          <strong>{{ formatNumber(selectedFeature.item.latestHydrology.waterLevel, 2) }} m</strong>
        </div>
        <div v-if="selectedFeature.item.latestHydrology" class="cesium-detail-row">
          <span>最新流量</span>
          <strong>{{ formatNumber(selectedFeature.item.latestHydrology.flow, 1) }} m3/s</strong>
        </div>
        <div v-if="!selectedFeature.item.latestHydrology && nodeStation(selectedFeature.item)" class="cesium-detail-row">
          <span>水位</span>
          <strong>{{ nodeStation(selectedFeature.item)?.waterLevel.toFixed(2) }} m</strong>
        </div>
        <div v-if="!selectedFeature.item.latestHydrology && nodeStation(selectedFeature.item)" class="cesium-detail-row">
          <span>流量</span>
          <strong>{{ nodeStation(selectedFeature.item)?.flow.toFixed(1) }} m3/s</strong>
        </div>
        <div v-if="!selectedFeature.item.latestHydrology && nodeStation(selectedFeature.item)" class="cesium-detail-row">
          <span>流速</span>
          <strong>{{ nodeStation(selectedFeature.item)?.velocity.toFixed(2) }} m/s</strong>
        </div>
      </template>

      <template v-else>
        <div class="cesium-detail-row"><span>河段编码</span><strong>{{ selectedFeature.item.code }}</strong></div>
        <div class="cesium-detail-row"><span>拓扑编号</span><strong>{{ selectedFeature.item.reachId ?? '--' }}</strong></div>
        <div class="cesium-detail-row">
          <span>起点节点</span>
          <strong>{{ selectedFeature.item.startNodeCode }} / {{ nodeByCode.get(selectedFeature.item.startNodeCode)?.name ?? '--' }}</strong>
        </div>
        <div class="cesium-detail-row">
          <span>终点节点</span>
          <strong>{{ selectedFeature.item.endNodeCode }} / {{ nodeByCode.get(selectedFeature.item.endNodeCode)?.name ?? '--' }}</strong>
        </div>
        <div class="cesium-detail-row"><span>河段长度</span><strong>{{ selectedFeature.item.lengthMeters.toFixed(0) }} m</strong></div>
        <div class="cesium-detail-row"><span>近似宽度</span><strong>{{ selectedFeature.item.widthMeters.toFixed(2) }} m</strong></div>
        <div class="cesium-detail-row"><span>最大流量</span><strong>{{ formatNumber(selectedFeature.item.hydrologyStats?.maxFlow, 1) }} m3/s</strong></div>
        <div class="cesium-detail-row"><span>最小流量</span><strong>{{ formatNumber(selectedFeature.item.hydrologyStats?.minFlow, 1) }} m3/s</strong></div>
        <div class="cesium-detail-row"><span>最高水位</span><strong>{{ formatNumber(selectedFeature.item.hydrologyStats?.maxWaterLevel, 2) }} m</strong></div>
        <div class="cesium-detail-row"><span>最低水位</span><strong>{{ formatNumber(selectedFeature.item.hydrologyStats?.minWaterLevel, 2) }} m</strong></div>
        <div class="cesium-detail-row"><span>统计时刻</span><strong>{{ formatHour(selectedFeature.item.hydrologyStats?.profileHour) }}</strong></div>
      </template>
    </aside>
  </div>
</template>

<style scoped>
.cesium-map-wrap {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: #020d18;
}

.cesium-canvas-host {
  position: absolute;
  inset: 0;
}

.cesium-layer-panel {
  position: absolute;
  z-index: 21;
  top: 14px;
  left: calc(50% + 74px);
  display: flex;
  overflow: hidden;
  border: 1px solid rgba(80, 204, 255, 0.42);
  background: rgba(3, 22, 39, 0.66);
  color: #e5f7ff;
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 28px rgba(0, 11, 33, 0.34), 0 0 18px rgba(0, 192, 255, 0.14);
}

.cesium-layer-panel button {
  min-width: 62px;
  height: 38px;
  border: 0;
  border-left: 1px solid rgba(100, 205, 255, 0.24);
  background: rgba(7, 39, 65, 0.72);
  color: rgba(224, 246, 255, 0.78);
  font-size: 13px;
  font-weight: 800;
}

.cesium-layer-panel button:first-child {
  border-left: 0;
}

.cesium-layer-panel button.active {
  background: rgba(16, 119, 128, 0.88);
  color: #ffffff;
  box-shadow: inset 0 0 16px rgba(43, 243, 188, 0.2);
}

.cesium-detail-panel {
  position: absolute;
  z-index: 24;
  width: min(360px, calc(100% - 32px));
  padding: 14px 16px;
  border: 1px solid rgba(80, 204, 255, 0.42);
  background: rgba(3, 22, 39, 0.84);
  color: #e5f7ff;
  backdrop-filter: blur(12px);
  box-shadow: 0 10px 28px rgba(0, 11, 33, 0.34), 0 0 18px rgba(0, 192, 255, 0.14);
  pointer-events: auto;
}

.cesium-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.cesium-detail-header span {
  display: block;
  color: #83d6ff;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.cesium-detail-header strong {
  display: block;
  margin-top: 4px;
  color: #ffffff;
  font-size: 18px;
}

.cesium-detail-header button {
  height: 30px;
  min-width: 56px;
  border: 1px solid rgba(100, 205, 255, 0.24);
  background: rgba(7, 39, 65, 0.72);
  color: rgba(224, 246, 255, 0.9);
  font-size: 12px;
  font-weight: 700;
}

.cesium-detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-top: 1px solid rgba(100, 205, 255, 0.12);
  font-size: 12px;
}

.cesium-detail-row span {
  flex: 0 0 auto;
  color: #9fcfe6;
}

.cesium-detail-row strong {
  min-width: 0;
  color: #ffffff;
  text-align: right;
  overflow-wrap: anywhere;
}

:deep(.cesium-viewer),
:deep(.cesium-viewer-cesiumWidgetContainer),
:deep(.cesium-widget),
:deep(.cesium-widget canvas) {
  width: 100%;
  height: 100%;
}

:deep(.cesium-viewer-bottom),
:deep(.cesium-credit-logoContainer) {
  display: none;
}
</style>
