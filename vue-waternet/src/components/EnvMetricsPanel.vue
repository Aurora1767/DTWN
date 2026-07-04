<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PanelShell from '@/components/PanelShell.vue'
import RainfallBarChart from '@/components/RainfallBarChart.vue'
import { fetchWeatherForecast } from '@/services/api'
import type { DailyForecast, EnvironmentSnapshot, RainfallHistoryPoint, WeatherForecast } from '@/types/platform'

const props = defineProps<{
  snapshot: EnvironmentSnapshot
  rainfallPoints?: RainfallHistoryPoint[]
  live?: boolean
  rainfallLive?: boolean
}>()

type PopupType = 'weather' | 'temp' | 'wind' | null
const activePopup = ref<PopupType>(null)
const forecast = ref<WeatherForecast | null>(null)

function togglePopup(type: PopupType) {
  activePopup.value = activePopup.value === type ? null : type
}

onMounted(async () => {
  const data = await fetchWeatherForecast()
  if (data) forecast.value = data
})

function formatObservedAt(value: string) {
  if (!value) return '暂无观测时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function shortDate(fxDate: string) {
  if (!fxDate) return ''
  const d = new Date(fxDate)
  if (Number.isNaN(d.getTime())) return fxDate
  return `${d.getMonth() + 1}/${d.getDate()}`
}
</script>

<template>
  <PanelShell title="环境量" eyebrow="ENVIRONMENT" class="env-panel">
    <div class="env-panel-content">
      <div class="env-metric-grid">
        <div class="env-metric-card env-clickable" @click="togglePopup('weather')">
          <div class="env-metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="4.5" stroke="currentColor" stroke-width="1.6" />
              <path d="M12 2.5v2.2M12 19.3v2.2M4.8 12H2.5M21.5 12h-2.3M6.2 6.2l-1.6-1.6M19.4 19.4l-1.6-1.6M6.2 17.8l-1.6 1.6M19.4 4.6l-1.6 1.6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </div>
          <span class="env-metric-label">天气</span>
          <strong class="env-metric-value">{{ snapshot.weatherText }}</strong>
        </div>

        <div class="env-metric-card env-clickable" @click="togglePopup('temp')">
          <div class="env-metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M10 3.5h4v3.2c3.1.8 5.5 3.5 5.5 6.8 0 3.9-3.2 7.1-7.1 7.1S5.3 17.4 5.3 13.5c0-3.3 2.4-6 5.5-6.8V3.5z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
              <path d="M12 10.8v5.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              <circle cx="12" cy="17.2" r="1.1" fill="currentColor" />
            </svg>
          </div>
          <span class="env-metric-label">气温</span>
          <strong class="env-metric-value">{{ snapshot.temperature }}<small class="env-unit">°C</small></strong>
        </div>

        <div class="env-metric-card env-clickable" @click="togglePopup('wind')">
          <div class="env-metric-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <path d="M4 12c2.8-4.2 6.2-6.2 8-6.2s5.2 2 8 6.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              <path d="M4 16c2.8-4.2 6.2-6.2 8-6.2s5.2 2 8 6.2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
              <path d="M7 12h10" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
          </div>
          <span class="env-metric-label">风速</span>
          <strong class="env-metric-value">{{ snapshot.windSpeed }}<small class="env-unit">m/s</small></strong>
        </div>
      </div>

      <!-- forecast popup -->
      <Transition name="env-popup-fade">
        <div v-if="activePopup && forecast" class="env-forecast-popup">
          <button class="env-popup-close" @click="activePopup = null" aria-label="关闭">×</button>

          <!-- weather forecast -->
          <template v-if="activePopup === 'weather'">
            <h4>未来天气预报</h4>
            <div class="env-forecast-table">
              <div v-for="d in forecast.daily" :key="d.fxDate" class="env-forecast-row">
                <span class="env-fc-date">{{ shortDate(d.fxDate) }}</span>
                <span class="env-fc-text">{{ d.textDay }} / {{ d.textNight }}</span>
                <span class="env-fc-precip">{{ d.precip }}mm</span>
                <span class="env-fc-hum">{{ d.humidity }}%</span>
              </div>
            </div>
          </template>

          <!-- temperature forecast -->
          <template v-if="activePopup === 'temp'">
            <h4>未来气温预报</h4>
            <div class="env-forecast-table">
              <div v-for="d in forecast.daily" :key="d.fxDate" class="env-forecast-row">
                <span class="env-fc-date">{{ shortDate(d.fxDate) }}</span>
                <span class="env-fc-temp-range">
                  <span class="env-temp-lo">{{ d.tempMin }}°</span>
                  <span class="env-temp-bar">
                    <span class="env-temp-fill" :style="{ width: `${((+d.tempMax - +d.tempMin) / 20) * 100}%` }"></span>
                  </span>
                  <span class="env-temp-hi">{{ d.tempMax }}°</span>
                </span>
                <span class="env-fc-text-sm">{{ d.textDay }}</span>
              </div>
            </div>
          </template>

          <!-- wind forecast -->
          <template v-if="activePopup === 'wind'">
            <h4>未来风速预报</h4>
            <div class="env-forecast-table">
              <div v-for="d in forecast.daily" :key="d.fxDate" class="env-forecast-row">
                <span class="env-fc-date">{{ shortDate(d.fxDate) }}</span>
                <span class="env-fc-wind">{{ d.windDirDay }} {{ d.windSpeedDay }}m/s</span>
                <span class="env-fc-scale">{{ d.windScaleDay }}级</span>
              </div>
            </div>
          </template>
        </div>
      </Transition>

      <RainfallBarChart :points="props.rainfallPoints ?? []" :live="props.rainfallLive" :forecast="forecast" />

      <p class="env-metric-status" :class="{ live: props.live || props.rainfallLive }">
        {{ props.live || props.rainfallLive ? '实时接入' : '离线' }} · {{ formatObservedAt(snapshot.observedAt) }}
      </p>
    </div>
  </PanelShell>
</template>

<style scoped>
.env-clickable {
  cursor: pointer;
  transition: background 150ms, box-shadow 150ms;
}
.env-clickable:hover {
  background: rgba(0, 200, 255, 0.08);
  box-shadow: 0 0 12px rgba(0, 200, 255, 0.18);
}

.env-forecast-popup {
  position: relative;
  margin: 10px 0;
  padding: 12px 14px;
  background: rgba(6, 18, 38, 0.92);
  border: 1px solid rgba(0, 200, 255, 0.3);
  border-radius: 8px;
  backdrop-filter: blur(8px);
}
.env-forecast-popup h4 {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 700;
  color: #8cf;
  letter-spacing: 0.04em;
}
.env-popup-close {
  position: absolute;
  top: 6px;
  right: 10px;
  background: none;
  border: none;
  color: rgba(200, 230, 255, 0.6);
  font-size: 18px;
  cursor: pointer;
  line-height: 1;
}
.env-popup-close:hover { color: #fff; }

.env-forecast-table {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.env-forecast-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: rgba(200, 235, 255, 0.85);
}
.env-fc-date {
  min-width: 36px;
  font-weight: 700;
  color: #aedcff;
}
.env-fc-text { flex: 1; }
.env-fc-precip { color: #6cf; min-width: 44px; text-align: right; }
.env-fc-hum { color: rgba(180, 220, 255, 0.6); min-width: 32px; text-align: right; }
.env-fc-wind { flex: 1; }
.env-fc-scale { color: #ffcf60; min-width: 30px; text-align: right; }
.env-fc-text-sm { color: rgba(180, 220, 255, 0.6); min-width: 30px; text-align: right; }

.env-fc-temp-range {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}
.env-temp-lo { color: #6cf; font-size: 11px; }
.env-temp-hi { color: #ffa040; font-size: 11px; }
.env-temp-bar {
  flex: 1;
  height: 5px;
  border-radius: 3px;
  background: rgba(80, 160, 220, 0.2);
  overflow: hidden;
}
.env-temp-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #4dc9f6, #f77f00);
}

.env-popup-fade-enter-active,
.env-popup-fade-leave-active {
  transition: opacity 200ms, transform 200ms;
}
.env-popup-fade-enter-from,
.env-popup-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
