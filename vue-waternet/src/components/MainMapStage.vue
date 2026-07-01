<script setup lang="ts">
import { computed } from 'vue'

import TdtMapCanvas from '@/components/TdtMapCanvas.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

const latestSeries = computed(() => store.latestSimulation.results[0]?.series ?? [])
const latestLevel = computed(
  () => latestSeries.value[latestSeries.value.length - 1]?.waterLevel ?? store.averageWaterLevel,
)
</script>

<template>
  <section class="map-stage" :class="`is-${store.viewMode}`">
    <div class="stage-toolbar">
      <div class="stage-title">
        <span>{{ store.viewMode === '2d' ? '2D 天地图' : 'Cesium 3D' }}</span>
        <strong>太湖-京杭运河-蠡河-大溪港片区</strong>
      </div>
      <div class="view-switch">
        <button :class="{ active: store.viewMode === '2d' }" @click="store.setViewMode('2d')">2D</button>
        <button :class="{ active: store.viewMode === '3d' }" @click="store.setViewMode('3d')">3D</button>
      </div>
    </div>

    <div class="stage-canvas">
      <TdtMapCanvas v-if="store.viewMode === '2d'" />
      <div v-if="store.viewMode === '3d'" class="map-grid"></div>
      <svg
        v-if="store.viewMode === '3d'"
        viewBox="0 0 900 520"
        class="water-network"
        aria-label="水网三维预演示意"
      >
        <defs>
          <linearGradient id="riverFlow" x1="0" x2="1">
            <stop offset="0" stop-color="#22f0ff" />
            <stop offset="1" stop-color="#2e77ff" />
          </linearGradient>
        </defs>
        <path class="region" d="M104 92 L338 60 L612 90 L768 205 L742 405 L520 462 L260 430 L116 288 Z" />
        <path class="river main" d="M110 122 C235 168 286 226 382 280 C510 352 608 345 760 410" />
        <path class="river" d="M260 64 C280 148 286 230 306 426" />
        <path class="river" d="M650 106 C610 202 600 298 584 462" />
        <path class="river" d="M120 372 C280 360 420 390 744 350" />
        <path class="river faint" d="M186 210 C316 180 470 176 716 226" />
        <g class="node-group">
          <circle cx="260" cy="64" r="8" />
          <circle cx="306" cy="426" r="8" />
          <circle cx="650" cy="106" r="8" />
          <circle cx="584" cy="462" r="8" />
          <circle cx="382" cy="280" r="10" class="warning-node" />
          <circle cx="650" cy="360" r="10" />
        </g>
      </svg>
      <div class="stage-depth" v-if="store.viewMode === '3d'">
        <span></span>
        <span></span>
        <span></span>
      </div>
      <div class="stage-readout">
        <span>当前水位</span>
        <strong>{{ latestLevel.toFixed(2) }} m</strong>
      </div>
    </div>
  </section>
</template>
