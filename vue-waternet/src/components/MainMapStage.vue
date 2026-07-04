<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'

import HydroProcessPanel from '@/components/HydroProcessPanel.vue'
import TdtMapCanvas from '@/components/TdtMapCanvas.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()
const CesiumMapCanvas = defineAsyncComponent(() => import('@/components/CesiumMapCanvas.vue'))
const processCollapsed = ref(false)
</script>

<template>
  <section class="map-stage" :class="`is-${store.viewMode}`">
    <div class="stage-toolbar">
      <div class="view-switch" aria-label="地图视图切换">
        <button type="button" :class="{ active: store.viewMode === '2d' }" @click="store.setViewMode('2d')">
          2D
        </button>
        <button type="button" :class="{ active: store.viewMode === '3d' }" @click="store.setViewMode('3d')">
          3D
        </button>
      </div>
    </div>

    <div class="stage-canvas">
      <TdtMapCanvas v-if="store.viewMode === '2d'" />
      <CesiumMapCanvas v-else />
      <HydroProcessPanel v-model:collapsed="processCollapsed" />
    </div>
  </section>
</template>
