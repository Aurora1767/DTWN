<script setup lang="ts">
import { computed } from 'vue'

import InteractiveHydroChart from '@/components/InteractiveHydroChart.vue'
import type { RiverNetworkReachProfile } from '@/types/platform'

const props = defineProps<{
  profile: RiverNetworkReachProfile | null
  mode: 'level' | 'flow' | 'both'
  warningActive?: boolean
}>()

const xLabels = computed(() =>
  props.profile?.distances.map((distance) => `${(distance / 1000).toFixed(2)} km`) ?? [],
)

const xTickLabels = computed(() => xLabels.value)

const series = computed(() => {
  if (!props.profile) {
    return []
  }
  return [
    {
      key: 'level',
      label: '水位',
      color: '#00f2ff',
      values: props.profile.waterLevels,
      unit: 'm',
    },
    {
      key: 'flow',
      label: '流量',
      color: '#2fffa8',
      values: props.profile.flows.map((value) => Math.abs(value)),
      unit: 'm3/s',
    },
  ]
})

const showLevel = computed(() => props.mode === 'level' || props.mode === 'both')
const showFlow = computed(() => props.mode === 'flow' || props.mode === 'both')
</script>

<template>
  <div class="reach-profile-chart">
    <InteractiveHydroChart
      v-if="profile"
      :series="series"
      :x-labels="xLabels"
      :x-tick-labels="xTickLabels"
      :mode="mode"
      :chart-height="220"
      :warning-active="warningActive"
      empty-text="请选择河段或先完成模型计算"
    />
    <div v-else class="model-result-empty">请选择河段或先完成模型计算</div>

    <div v-if="profile" class="reach-profile-legend">
      <span v-if="showLevel" class="legend-item level">水位</span>
      <span v-if="showFlow" class="legend-item flow">流量</span>
      <span v-if="mode === 'both'" class="legend-note">集成模式下两序列分别归一化展示</span>
      <span v-if="warningActive" class="legend-note warning-legend-note">预警阈值线已标注</span>
      <span class="legend-note">鼠标悬停查看沿程数值</span>
    </div>
  </div>
</template>
