<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'

import MainMapStage from '@/components/MainMapStage.vue'
import MiniLineChart from '@/components/MiniLineChart.vue'
import PanelShell from '@/components/PanelShell.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()
const leftCollapsed = ref(false)
const rightCollapsed = ref(false)

onMounted(() => {
  void store.loadDashboard()
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
          <div class="station-list">
            <div v-for="station in store.stations" :key="station.stationCode" class="station-item">
              <span>{{ station.stationName }}</span>
              <strong>{{ station.waterLevel.toFixed(2) }} m</strong>
              <em :class="station.status.toLowerCase()">{{ station.status }}</em>
            </div>
          </div>
        </PanelShell>

        <PanelShell title="降雨与流量" eyebrow="TREND">
          <MiniLineChart :values="store.stations.map((station) => station.flow)" />
          <div class="compact-table">
            <div v-for="station in store.stations" :key="station.stationCode">
              <span>{{ station.stationName }}</span>
              <b>{{ station.rainfall.toFixed(1) }} mm</b>
            </div>
          </div>
        </PanelShell>
      </template>

      <div v-else class="side-rail">
        <span>实时水情</span>
      </div>
    </aside>

    <main class="center-stage">
      <MainMapStage />
    </main>

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
          <MiniLineChart
            :values="store.latestSimulation.results[0]?.series.map((point) => point.waterLevel) ?? []"
            color="#2fffa8"
          />
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
      </template>

      <div v-else class="side-rail">
        <span>模型预警</span>
      </div>
    </aside>
  </div>
</template>
