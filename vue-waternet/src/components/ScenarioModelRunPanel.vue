<script setup lang="ts">
import GateOpeningSettings from '@/components/GateOpeningSettings.vue'
import { SIMULATION_BOUNDARY_STATIONS } from '@/constants/simulationBoundaries'
import type { useScenarioModelRun } from '@/composables/useScenarioModelRun'

type ScenarioModelRun = ReturnType<typeof useScenarioModelRun>

defineProps<{
  run: ScenarioModelRun | null
}>()
</script>

<template>
  <div v-if="run" class="forecast-control">
    <label class="forecast-duration-field simulation-start-field">
      {{ run.ui.nameLabel }}
      <input
        v-model="run.name"
        type="text"
        maxlength="64"
        :placeholder="run.ui.namePlaceholder"
        :disabled="run.runStatus === 'running'"
      />
    </label>
    <label class="forecast-duration-field simulation-start-field">
      {{ run.ui.startLabel }}
      <input
        v-model="run.startAt"
        type="datetime-local"
        step="300"
        :disabled="run.runStatus === 'running'"
      />
    </label>
    <p class="forecast-duration-summary">{{ run.ui.summaryNote }}</p>
    <div class="simulation-boundary-group">
      <span class="simulation-boundary-label">边界条件</span>
      <div class="simulation-boundary-buttons">
        <button
          v-for="station in SIMULATION_BOUNDARY_STATIONS"
          :key="station.nodeId"
          type="button"
          class="simulation-boundary-btn"
          :disabled="run.runStatus === 'running' || run.boundaryDefaultsLoading"
          @click="run.openBoundaryEditor(station.nodeId)"
        >
          <strong>{{ station.shortLabel }}</strong>
          <span>
            {{ station.valueKind === 'level' ? '水位' : '流量' }} ·
            {{ run.boundaryDisplayValue(station.nodeId) }}
          </span>
          <em :class="{ customized: run.boundaryStationStates[station.nodeId].customized }">
            {{ run.boundaryStationStates[station.nodeId].customized ? '已编辑' : '恒定' }}
          </em>
        </button>
      </div>
      <p v-if="run.boundaryDefaultsLoading" class="simulation-boundary-loading">
        正在读取开始时刻边界...
      </p>
      <p v-else-if="run.boundaryDefaultsError" class="simulation-boundary-error">
        {{ run.boundaryDefaultsError }}
      </p>
    </div>
    <GateOpeningSettings v-model="run.gateOpeningInputs" :disabled="run.runStatus === 'running'" />
    <div class="forecast-duration-group">
      <span class="forecast-duration-label">{{ run.ui.durationLabel }}</span>
      <div class="forecast-duration-inputs">
        <label class="forecast-duration-field">
          天数
          <input
            v-model.number="run.days"
            type="number"
            min="0"
            step="1"
            :disabled="run.runStatus === 'running'"
          />
        </label>
        <label class="forecast-duration-field">
          小时
          <input
            v-model.number="run.extraHours"
            type="number"
            min="0"
            step="0.1"
            :disabled="run.runStatus === 'running'"
          />
        </label>
      </div>
      <p class="forecast-duration-summary">合计 {{ run.totalHours.toFixed(1) }} 小时</p>
    </div>
    <label class="forecast-duration-field simulation-dt-field">
      时间步长 (s)
      <input
        v-model.number="run.dt"
        type="number"
        min="1"
        step="1"
        :disabled="run.runStatus === 'running'"
      />
    </label>
    <p class="forecast-duration-summary">
      预计计算 {{ run.stepCount }} 步，每步 {{ Math.max(1, Number(run.dt) || 300) }} 秒
    </p>
    <button
      class="primary-action forecast-start-btn"
      type="button"
      :disabled="run.runStatus === 'running'"
      @click="run.startRun()"
    >
      {{ run.runStatus === 'running' ? run.ui.runningButton : run.ui.startButton }}
    </button>
    <div class="forecast-status-row">
      <span>模型状态</span>
      <strong :class="run.runStatus">{{ run.statusLabel }}</strong>
    </div>
    <p v-if="run.error" class="forecast-error">{{ run.error }}</p>
    <p v-else-if="run.result?.status === 'fallback'" class="forecast-error">
      {{ run.ui.fallbackError }}
    </p>
    <p v-else-if="run.result" class="forecast-meta">
      {{ run.ui.recentResultPrefix }}：{{ run.result.forecastHours }} 小时 /
      {{ run.result.nSteps }} 步 / {{ run.result.simulatedSeconds }} s（步长
      {{ run.result.dt }} s）
    </p>
  </div>
</template>
