<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'

import MainMapStage from '@/components/MainMapStage.vue'
import BasinOverviewMap from '@/components/BasinOverviewMap.vue'
import EnvMetricsPanel from '@/components/EnvMetricsPanel.vue'
import HydrologicalStationsPanel from '@/components/HydrologicalStationsPanel.vue'
import RealtimeAlertPanel from '@/components/RealtimeAlertPanel.vue'
import HydrologyDataTable from '@/components/HydrologyDataTable.vue'
import NetworkInsightPanel from '@/components/NetworkInsightPanel.vue'
import PanelShell from '@/components/PanelShell.vue'
import GateControlPanel from '@/components/GateControlPanel.vue'
import GateMonitorPanel from '@/components/GateMonitorPanel.vue'
import WaterQualityPanel from '@/components/WaterQualityPanel.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)

onMounted(() => {
  void store.loadDashboard()
  store.startEnvironmentPolling(60_000)
  store.startWaterQualityPolling(60_000)
  store.startWaterQuantityPolling(300_000)
  void notifyLayoutChanged()
})

onUnmounted(() => {
  store.stopEnvironmentPolling()
  store.stopWaterQualityPolling()
  store.stopWaterQuantityPolling()
})

async function toggleLeftPanel() {
  leftCollapsed.value = !leftCollapsed.value
  await notifyLayoutChanged()
}

async function toggleRightPanel() {
  rightCollapsed.value = !rightCollapsed.value
  await notifyLayoutChanged()
}

async function notifyLayoutChanged() {
  await nextTick()
  window.setTimeout(() => window.dispatchEvent(new Event('resize')), 220)
}
</script>

<template>
  <div class="cockpit-grid" :class="{ 'left-collapsed': leftCollapsed, 'right-collapsed': rightCollapsed }">
    <main class="center-stage">
      <MainMapStage />
    </main>

    <aside class="side-stack side-panel" :class="{ collapsed: leftCollapsed }">
      <button
        class="side-collapse-button side-collapse-button-left"
        type="button"
        :title="leftCollapsed ? '展开左侧展示栏' : '收起左侧展示栏'"
        @click="toggleLeftPanel"
      >
        {{ leftCollapsed ? '展开' : '收起' }}
      </button>

      <template v-if="!leftCollapsed">
        <BasinOverviewMap />

        <EnvMetricsPanel
          :snapshot="store.environment"
          :rainfall-points="store.rainfallHistory.points"
          :live="store.environmentLive"
          :rainfall-live="store.rainfallLive"
        />

        <HydrologicalStationsPanel
          :stations="store.waterQuantity.stations"
          :live="store.waterQuantityLive"
          :updated-at="store.waterQuantity.timestamp"
        />

        <WaterQualityPanel :overview="store.waterQuality" :live="store.waterQualityLive" />

        <GateControlPanel />

        <PanelShell title="实时预警" eyebrow="ALERT">
          <RealtimeAlertPanel />
        </PanelShell>
      </template>

    </aside>

    <aside class="side-stack side-panel" :class="{ collapsed: rightCollapsed }">
      <button
        class="side-collapse-button side-collapse-button-right"
        type="button"
        :title="rightCollapsed ? '展开右侧展示栏' : '收起右侧展示栏'"
        @click="toggleRightPanel"
      >
        {{ rightCollapsed ? '展开' : '收起' }}
      </button>

      <template v-if="!rightCollapsed">
        <NetworkInsightPanel class="insight-panel-slot" />

        <PanelShell title="实时数据" eyebrow="DATA" class="data-table-slot">
          <HydrologyDataTable />
        </PanelShell>

        <GateMonitorPanel class="monitor-slot" />
      </template>

    </aside>
  </div>
</template>
