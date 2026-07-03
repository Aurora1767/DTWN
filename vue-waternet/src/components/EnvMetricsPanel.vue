<script setup lang="ts">
import PanelShell from '@/components/PanelShell.vue'
import RainfallBarChart from '@/components/RainfallBarChart.vue'
import type { EnvironmentSnapshot, RainfallHistoryPoint } from '@/types/platform'

const props = defineProps<{
  snapshot: EnvironmentSnapshot
  rainfallPoints?: RainfallHistoryPoint[]
  live?: boolean
  rainfallLive?: boolean
}>()

function formatObservedAt(value: string) {
  if (!value) {
    return '暂无观测时间'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <PanelShell title="环境量" eyebrow="ENVIRONMENT" class="env-panel">
    <div class="env-panel-content">
      <div class="env-metric-grid">
        <div class="env-metric-card">
          <div class="env-metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="4.5" stroke="currentColor" stroke-width="1.6" />
              <path
                d="M12 2.5v2.2M12 19.3v2.2M4.8 12H2.5M21.5 12h-2.3M6.2 6.2l-1.6-1.6M19.4 19.4l-1.6-1.6M6.2 17.8l-1.6 1.6M19.4 4.6l-1.6 1.6"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
              />
            </svg>
          </div>
          <span class="env-metric-label">天气</span>
          <strong class="env-metric-value">{{ snapshot.weatherText }}</strong>
        </div>

        <div class="env-metric-card">
          <div class="env-metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M10 3.5h4v3.2c3.1.8 5.5 3.5 5.5 6.8 0 3.9-3.2 7.1-7.1 7.1S5.3 17.4 5.3 13.5c0-3.3 2.4-6 5.5-6.8V3.5z"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linejoin="round"
              />
              <path d="M12 10.8v5.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              <circle cx="12" cy="17.2" r="1.1" fill="currentColor" />
            </svg>
          </div>
          <span class="env-metric-label">气温</span>
          <strong class="env-metric-value">{{ snapshot.temperature }}<small class="env-unit">°C</small></strong>
        </div>

        <div class="env-metric-card">
          <div class="env-metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path
                d="M4 12c2.8-4.2 6.2-6.2 8-6.2s5.2 2 8 6.2"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
              />
              <path
                d="M4 16c2.8-4.2 6.2-6.2 8-6.2s5.2 2 8 6.2"
                stroke="currentColor"
                stroke-width="1.6"
                stroke-linecap="round"
              />
              <path d="M7 12h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </div>
          <span class="env-metric-label">风速</span>
          <strong class="env-metric-value">{{ snapshot.windSpeed }}<small class="env-unit">m/s</small></strong>
        </div>
      </div>

      <RainfallBarChart :points="props.rainfallPoints ?? []" :live="props.rainfallLive" />

      <p class="env-metric-status" :class="{ live: props.live || props.rainfallLive }">
        {{ props.live || props.rainfallLive ? '实时接入' : '离线' }} · {{ formatObservedAt(snapshot.observedAt) }}
      </p>
    </div>
  </PanelShell>
</template>
