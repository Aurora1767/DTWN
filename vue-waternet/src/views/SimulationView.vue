<script setup lang="ts">
import { reactive } from 'vue'

import MiniLineChart from '@/components/MiniLineChart.vue'
import PanelShell from '@/components/PanelShell.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

const form = reactive({
  scenarioName: '太湖水位突涨预演',
  upstreamFlow: 20,
  downstreamLevel: 2.5,
  timeStep: 300,
  steps: 96,
})

function submit() {
  void store.startSimulation({
    scenarioName: form.scenarioName,
    boundaryType: 'manual',
    upstreamFlow: form.upstreamFlow,
    downstreamLevel: form.downstreamLevel,
    timeStep: form.timeStep,
    steps: form.steps,
    segments: store.network.segments.map((segment) => ({
      code: segment.code,
      length: segment.lengthMeters,
      width: segment.widthMeters,
      manningN: segment.manningN,
    })),
  })
}
</script>

<template>
  <div class="business-grid">
    <PanelShell title="工况配置" eyebrow="SCENARIO">
      <form class="scenario-form" @submit.prevent="submit">
        <label>
          工况名称
          <input v-model="form.scenarioName" />
        </label>
        <label>
          上游流量 m3/s
          <input v-model.number="form.upstreamFlow" type="number" min="0" step="0.1" />
        </label>
        <label>
          下游水位 m
          <input v-model.number="form.downstreamLevel" type="number" min="0" step="0.01" />
        </label>
        <label>
          时间步长 s
          <input v-model.number="form.timeStep" type="number" min="60" step="60" />
        </label>
        <label>
          计算步数
          <input v-model.number="form.steps" type="number" min="12" max="288" />
        </label>
        <button class="primary-action" :disabled="store.loadingSimulation">
          {{ store.loadingSimulation ? '运行中' : '运行仿真' }}
        </button>
      </form>
    </PanelShell>

    <PanelShell title="结果预览" eyebrow="RESULT">
      <div class="run-summary">
        <span>{{ store.latestSimulation.scenarioName }}</span>
        <strong>{{ store.latestSimulation.runId }}</strong>
        <small>{{ store.latestSimulation.status }} / {{ store.latestSimulation.runnerType }}</small>
      </div>
      <MiniLineChart
        :values="store.latestSimulation.results[0]?.series.map((point) => point.waterLevel) ?? []"
        color="#2fffa8"
      />
      <div class="result-list">
        <div v-for="result in store.latestSimulation.results" :key="result.segmentCode">
          <span>{{ result.segmentCode }}</span>
          <b>{{ result.maxWaterLevel }} m</b>
          <em>{{ result.averageVelocity }} m/s</em>
        </div>
      </div>
    </PanelShell>
  </div>
</template>
