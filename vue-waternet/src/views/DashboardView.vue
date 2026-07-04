<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'

import MainMapStage from '@/components/MainMapStage.vue'
import BasinOverviewMap from '@/components/BasinOverviewMap.vue'
import EnvMetricsPanel from '@/components/EnvMetricsPanel.vue'
import HydrologicalStationsPanel from '@/components/HydrologicalStationsPanel.vue'
import MiniLineChart from '@/components/MiniLineChart.vue'
import NetworkInsightPanel from '@/components/NetworkInsightPanel.vue'
import PanelShell from '@/components/PanelShell.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)

onMounted(() => {
  void store.loadDashboard()
  store.startEnvironmentPolling(60_000)
  store.startWaterQuantityPolling(300_000)
  void notifyLayoutChanged()
})

onUnmounted(() => {
  store.stopEnvironmentPolling()
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

        <PanelShell title="流速监测" eyebrow="VELOCITY">
          <div class="compact-table">
            <div v-for="station in store.stations" :key="`${station.stationCode}-velocity`">
              <span>{{ station.stationName }}</span>
              <b>{{ station.velocity.toFixed(2) }} m/s</b>
            </div>
          </div>
        </PanelShell>

        <PanelShell title="降雨统计" eyebrow="RAIN">
          <div class="compact-table">
            <div v-for="station in store.stations" :key="`${station.stationCode}-rain`">
              <span>{{ station.stationName }}</span>
              <b>{{ station.rainfall.toFixed(1) }} mm</b>
            </div>
          </div>
        </PanelShell>

        <PanelShell title="流量趋势" eyebrow="FLOW">
          <MiniLineChart :values="store.stations.map((station) => station.flow)" />
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

        <PanelShell title="预警统计" eyebrow="ALERT">
          <div class="metric-row">
            <div>
              <span>高风险事件</span>
              <strong>{{ store.highRiskCount }}</strong>
            </div>
            <div>
              <span>告警总数</span>
              <strong>{{ store.warnings.length }}</strong>
            </div>
          </div>
        </PanelShell>

        <PanelShell title="水网概览" eyebrow="NETWORK">
          <div class="compact-table">
            <div>
              <span>河段数量</span>
              <b>{{ store.network.segments.length }}</b>
            </div>
            <div>
              <span>节点数量</span>
              <b>{{ store.network.nodes.length }}</b>
            </div>
            <div v-for="segment in store.network.segments" :key="segment.code">
              <span>{{ segment.name }}</span>
              <b>{{ (segment.lengthMeters / 1000).toFixed(1) }} km</b>
            </div>
          </div>
        </PanelShell>
      </template>

    </aside>
  </div>
</template>
