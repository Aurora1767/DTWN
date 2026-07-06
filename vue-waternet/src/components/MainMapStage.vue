<script setup lang="ts">
import { defineAsyncComponent, ref } from 'vue'

import HydroProcessPanel from '@/components/HydroProcessPanel.vue'
import TdtMapCanvas from '@/components/TdtMapCanvas.vue'
import { usePlatformStore } from '@/stores/platform'

withDefaults(
  defineProps<{
    showHydroProcess?: boolean
  }>(),
  { showHydroProcess: true },
)

const store = usePlatformStore()
const CesiumMapCanvas = defineAsyncComponent(() => import('@/components/CesiumMapCanvas.vue'))
const processCollapsed = ref(false)
const refreshing = ref(false)

async function refreshAll() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    await Promise.all([
      store.loadDashboard(),
      store.refreshEnvironment(),
      store.refreshWaterQuantity(),
      store.refreshWaterQuality(),
    ])
  } finally {
    refreshing.value = false
  }
}
</script>

<template>
  <section class="map-stage" :class="`is-${store.viewMode}`">
    <div class="stage-toolbar">
      <div class="toolbar-center-group">
        <button
          class="refresh-btn"
          type="button"
          :class="{ spinning: refreshing }"
          :disabled="refreshing"
          title="一键刷新所有模块数据"
          @click="refreshAll"
        >
          <svg viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M13.5 8A5.5 5.5 0 1 1 8 2.5c1.8 0 3.4.87 4.4 2.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
            <path d="M12 2.5 12.4 5l-2.5.4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
          <span>{{ refreshing ? '刷新中' : '刷新' }}</span>
        </button>
        <div class="view-switch" aria-label="地图视图切换">
          <button type="button" :class="{ active: store.viewMode === '2d' }" @click="store.setViewMode('2d')">
            2D
          </button>
          <button type="button" :class="{ active: store.viewMode === '3d' }" @click="store.setViewMode('3d')">
            3D
          </button>
        </div>
      </div>
    </div>

    <div class="stage-canvas">
      <TdtMapCanvas v-if="store.viewMode === '2d'" />
      <CesiumMapCanvas v-else />
      <HydroProcessPanel v-if="showHydroProcess" v-model:collapsed="processCollapsed" />
    </div>
  </section>
</template>

<style scoped>
.toolbar-center-group {
  display: flex;
  align-items: stretch;
  gap: 0;
  pointer-events: auto;
  border: 1px solid rgba(72, 204, 255, 0.58);
  background: rgba(4, 24, 42, 0.62);
  box-shadow: 0 10px 28px rgba(0, 11, 33, 0.36), 0 0 18px rgba(0, 192, 255, 0.18);
  backdrop-filter: blur(8px);
  overflow: hidden;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  padding: 0 12px;
  border: 0;
  border-right: 1px solid rgba(72, 204, 255, 0.35);
  background: rgba(15, 50, 74, 0.72);
  color: rgba(224, 246, 255, 0.85);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 120ms, color 120ms;
}

.refresh-btn:hover:not(:disabled) {
  background: rgba(12, 100, 140, 0.9);
  color: #ffffff;
}

.refresh-btn:disabled {
  opacity: 0.65;
  cursor: wait;
}

.refresh-btn svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.refresh-btn.spinning svg {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
