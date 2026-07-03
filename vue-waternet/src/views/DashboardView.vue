<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'

import MainMapStage from '@/components/MainMapStage.vue'
import EnvMetricsPanel from '@/components/EnvMetricsPanel.vue'
import HydrologicalStationsPanel from '@/components/HydrologicalStationsPanel.vue'
import MiniLineChart from '@/components/MiniLineChart.vue'
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
        <PanelShell title="实时水情" eyebrow="MONITOR">
          <div class="metric-row">
            <div>
              <span>平均水位</span>
              <strong>{{ store.averageWaterLevel }} m</strong>
            </div>
            <div>
              <span>总流量</span>
              <strong>{{ store.totalFlow }} m3/s</strong>
            </div>
          </div>
        </PanelShell>

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
        <PanelShell title="模型运行" eyebrow="SIMULATION">
          <div class="run-summary">
            <span>{{ store.latestSimulation.scenarioName }}</span>
            <strong>{{ store.latestSimulation.status }}</strong>
            <small>{{ store.latestSimulation.runnerType }} RUNNER</small>
          </div>
        </PanelShell>

        <PanelShell title="预演曲线" eyebrow="CURVE">
          <MiniLineChart
            :values="store.latestSimulation.results[0]?.series.map((point) => point.waterLevel) ?? []"
            color="#00f2ff"
          />
        </PanelShell>

        <PanelShell title="模拟指标" eyebrow="METRIC">
          <div class="metric-row">
            <div>
              <span>最高水位</span>
              <strong>{{ store.latestSimulation.results[0]?.maxWaterLevel ?? '-' }} m</strong>
            </div>
            <div>
              <span>平均流速</span>
              <strong>{{ store.latestSimulation.results[0]?.averageVelocity ?? '-' }} m/s</strong>
            </div>
          </div>
          <div class="metric-row">
            <div>
              <span>最大流量</span>
              <strong>{{ store.latestSimulation.results[0]?.maxFlow ?? '-' }} m3/s</strong>
            </div>
            <div>
              <span>河段</span>
              <strong>{{ store.latestSimulation.results[0]?.segmentName ?? '-' }}</strong>
            </div>
          </div>
        </PanelShell>

        <PanelShell title="风险预警" eyebrow="WARNING">
          <div class="warning-list">
            <div v-for="warning in store.warnings" :key="warning.id" class="warning-item">
              <div>
                <strong>{{ warning.targetName }}</strong>
                <span>{{ warning.metric }} {{ warning.value }} / {{ warning.threshold }}</span>
              </div>
              <em :class="warning.level.toLowerCase()">{{ warning.level }}</em>
            </div>
          </div>
        </PanelShell>

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
