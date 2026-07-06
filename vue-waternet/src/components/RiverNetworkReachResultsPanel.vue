<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import ReachProfileChart from '@/components/ReachProfileChart.vue'
import { isFlowExceeded, isWaterLevelExceeded } from '@/constants/warningThresholds'
import type { RiverNetworkReachProfile, RiverNetworkReachResult } from '@/types/platform'
import { calcReachEmbankmentPressures } from '@/utils/embankmentPressure'

const props = defineProps<{
  reaches: RiverNetworkReachResult[]
  reachProfiles: RiverNetworkReachProfile[]
  emptyText?: string
  warningActive?: boolean
  /** 预警 / 预演 / 预案页展示堤防静水压力列 */
  embankmentPressureActive?: boolean
}>()

type ProfileMode = 'level' | 'flow' | 'both'

const selectedReachId = ref<number | null>(null)
const profileMode = ref<ProfileMode>('both')

const reachThresholdActive = computed(
  () => Boolean(props.warningActive || props.embankmentPressureActive),
)

watch(
  () => props.reachProfiles,
  (profiles) => {
    if (!profiles.length) {
      selectedReachId.value = null
      return
    }
    if (!profiles.some((profile) => profile.reachId === selectedReachId.value)) {
      selectedReachId.value = profiles[0]?.reachId ?? null
    }
  },
  { immediate: true },
)

const selectedProfile = computed(() =>
  props.reachProfiles.find((profile) => profile.reachId === selectedReachId.value) ?? null,
)

const profileMap = computed(() => {
  const map = new Map<number, RiverNetworkReachProfile>()
  for (const profile of props.reachProfiles) {
    map.set(profile.reachId, profile)
  }
  return map
})

const reachRows = computed(() =>
  props.reaches.map((reach) => {
    const profile = profileMap.value.get(reach.reachId)
    const pressure = profile ? calcReachEmbankmentPressures(profile.waterLevels) : null

    return {
      reach,
      pressure,
      startPressureExceeded:
        reachThresholdActive.value && pressure
          ? isWaterLevelExceeded(pressure.startWaterLevel)
          : false,
      endPressureExceeded:
        reachThresholdActive.value && pressure
          ? isWaterLevelExceeded(pressure.endWaterLevel)
          : false,
    }
  }),
)
</script>

<template>
  <div class="reach-results-panel">
    <div class="reach-results-toolbar">
      <label class="reach-filter">
        <span>河段筛选</span>
        <select v-model.number="selectedReachId" :disabled="!reachProfiles.length">
          <option v-for="profile in reachProfiles" :key="profile.reachId" :value="profile.reachId">
            {{ profile.label }}
          </option>
        </select>
      </label>

      <div class="profile-mode-switch" aria-label="沿程曲线展示模式">
        <button type="button" :class="{ active: profileMode === 'level' }" @click="profileMode = 'level'">
          水位
        </button>
        <button type="button" :class="{ active: profileMode === 'flow' }" @click="profileMode = 'flow'">
          流量
        </button>
        <button type="button" :class="{ active: profileMode === 'both' }" @click="profileMode = 'both'">
          集成
        </button>
      </div>
    </div>

    <div
      class="model-result-table reach-summary-table"
      :class="{ 'with-embankment-pressure': embankmentPressureActive }"
    >
      <div class="model-result-table-head reach-summary-head">
        <span>河段</span>
        <span>起止</span>
        <span>均水位</span>
        <span>入口Q</span>
        <span>出口Q</span>
        <span v-if="embankmentPressureActive" class="reach-embankment-pressure-head">堤防静水压力<br />kN/m</span>
      </div>
      <div v-if="reachRows.length" class="model-result-table-body">
        <div
          v-for="row in reachRows"
          :key="row.reach.reachId"
          class="model-result-table-row reach-summary-row"
          :class="{ active: row.reach.reachId === selectedReachId }"
          @click="selectedReachId = row.reach.reachId"
        >
          <span>{{ row.reach.reachId }}</span>
          <span>{{ row.reach.startNode }}-{{ row.reach.endNode }}</span>
          <strong
            :class="{
              'warning-value-exceeded':
                reachThresholdActive && isWaterLevelExceeded(row.reach.avgWaterLevel),
            }"
          >
            {{ row.reach.avgWaterLevel.toFixed(2) }}
          </strong>
          <strong :class="{ 'warning-value-exceeded': warningActive && isFlowExceeded(row.reach.inletFlow) }">
            {{ row.reach.inletFlow.toFixed(2) }}
          </strong>
          <strong :class="{ 'warning-value-exceeded': warningActive && isFlowExceeded(row.reach.outletFlow) }">
            {{ row.reach.outletFlow.toFixed(2) }}
          </strong>
          <div v-if="embankmentPressureActive" class="reach-embankment-pressure-cell">
            <template v-if="row.pressure">
              <strong :class="{ 'warning-value-exceeded': row.startPressureExceeded }">
                始 {{ row.pressure.startPressure.toFixed(1) }}
              </strong>
              <strong :class="{ 'warning-value-exceeded': row.endPressureExceeded }">
                末 {{ row.pressure.endPressure.toFixed(1) }}
              </strong>
            </template>
            <span v-else>--</span>
          </div>
        </div>
      </div>
      <div v-else class="model-result-empty">{{ emptyText ?? '暂无河段计算结果，请先开始预报' }}</div>
    </div>

    <div class="reach-profile-section">
      <div class="reach-profile-caption">
        <strong>沿程曲线</strong>
        <span v-if="selectedProfile">{{ selectedProfile.label }} / {{ selectedProfile.length.toFixed(0) }} m</span>
      </div>
      <ReachProfileChart :profile="selectedProfile" :mode="profileMode" :warning-active="warningActive" />
    </div>
  </div>
</template>
