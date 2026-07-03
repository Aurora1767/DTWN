<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

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
  Terrain,
  UrlTemplateImageryProvider,
  VerticalOrigin,
  Viewer,
} from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'

import { usePlatformStore } from '@/stores/platform'
import type { WaterNode } from '@/types/platform'

const store = usePlatformStore()
const cesiumContainer = ref<HTMLDivElement | null>(null)
const layers = reactive({
  surfaces: true,
  shorelines: true,
})

let viewer: Viewer | undefined
let surfaceDataSource: GeoJsonDataSource | undefined
let shorelineDataSource: GeoJsonDataSource | undefined
let nodeEntities: any[] = []
let imageryReadyLogged = false
let tilesIdleLogged = false

const cesiumIonToken = (import.meta.env.VITE_CESIUM_ION_TOKEN ?? '').trim()
const tiandituToken = (import.meta.env.VITE_TIANDITU_TOKEN ?? '').trim()
const surfacesUrl = `${import.meta.env.BASE_URL}data/water-surfaces.geojson`
const shorelinesUrl = `${import.meta.env.BASE_URL}data/water-shorelines.geojson`
const NODE_LABELS: Record<string, string> = {
  N01: '太湖',
  N03: '京杭大运河南',
  N06: '京杭大运河北',
}

onMounted(() => {
  void initializeCesium()
})

onBeforeUnmount(() => {
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
    viewer.scene.globe.depthTestAgainstTerrain = true
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
    drawNodeEntities()
    flyToWaterNetwork()
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
    clampToGround: true,
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
}

function styleSurfaces(dataSource: GeoJsonDataSource) {
  for (const entity of dataSource.entities.values) {
    if (!entity.polygon) continue
    entity.polygon.material = new ColorMaterialProperty(Color.fromCssColorString('#18d8ff').withAlpha(0.26))
    entity.polygon.outline = new ConstantProperty(true)
    entity.polygon.outlineColor = new ConstantProperty(Color.fromCssColorString('#7dffb2').withAlpha(0.66))
    entity.polygon.outlineWidth = new ConstantProperty(2)
    entity.polygon.height = new ConstantProperty(2)
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

function flyToWaterNetwork() {
  viewer?.camera.setView({
    destination: Cartesian3.fromDegrees(120.356, 31.494, 10500),
    orientation: {
      heading: CesiumMath.toRadians(18),
      pitch: CesiumMath.toRadians(-58),
      roll: 0,
    },
  })
}

function setLayerVisibility() {
  if (surfaceDataSource) {
    surfaceDataSource.show = layers.surfaces
  }
  if (shorelineDataSource) {
    shorelineDataSource.show = layers.shorelines
  }
}

function toggleLayer(layer: keyof typeof layers) {
  layers[layer] = !layers[layer]
  setLayerVisibility()
}

function drawNodeEntities() {
  if (!viewer) return

  for (const entity of nodeEntities) {
    viewer.entities.remove(entity)
  }
  nodeEntities = []

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
        code: node.code,
        type: node.type,
        boundaryType: node.boundaryType,
      },
    })
    nodeEntities.push(entity)
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
</script>

<template>
  <div class="cesium-map-wrap">
    <div ref="cesiumContainer" class="cesium-canvas-host"></div>

    <div class="cesium-layer-panel" aria-label="三维图层控制">
      <button type="button" :class="{ active: layers.surfaces }" @click="toggleLayer('surfaces')">水面</button>
      <button type="button" :class="{ active: layers.shorelines }" @click="toggleLayer('shorelines')">岸线</button>
    </div>
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
