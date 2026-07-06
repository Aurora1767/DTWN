<script setup lang="ts">
import ScenarioWarningPanel from '@/components/ScenarioWarningPanel.vue'
import type { ScenarioWarningAlert } from '@/types/platform'

withDefaults(
  defineProps<{
    expanded: boolean
    alerts: ScenarioWarningAlert[]
    activeRecordId: number | null
    hasForecastData: boolean
    scanning?: boolean
    eyebrow?: string
    title?: string
    ariaLabel?: string
  }>(),
  {
    eyebrow: 'WARNING',
    title: '预警提示',
    ariaLabel: '预警提示抽屉',
    alerts: () => [],
  },
)

const emit = defineEmits<{
  toggle: []
  scan: []
  confirm: [id: string]
  process: [id: string]
  clearProcessed: []
}>()
</script>

<template>
  <section
    class="scenario-warning-drawer"
    :class="{ collapsed: !expanded, simulation: eyebrow === 'SIMULATION', plan: eyebrow === 'PLAN' }"
    :aria-label="ariaLabel"
  >
    <header class="scenario-warning-drawer-head">
      <div class="scenario-warning-drawer-title">
        <span class="scenario-warning-drawer-eyebrow">{{ eyebrow }}</span>
        <strong>{{ title }}</strong>
      </div>
      <button type="button" class="scenario-warning-drawer-toggle" @click="emit('toggle')">
        {{ expanded ? '收起' : '展开' }}
      </button>
    </header>

    <div v-show="expanded" class="scenario-warning-drawer-body">
      <ScenarioWarningPanel
        :alerts="alerts"
        :active-record-id="activeRecordId"
        :has-forecast-data="hasForecastData"
        :scanning="scanning"
        @scan="emit('scan')"
        @confirm="emit('confirm', $event)"
        @process="emit('process', $event)"
        @clear-processed="emit('clearProcessed')"
      />
    </div>
  </section>
</template>
