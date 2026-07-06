<script setup lang="ts">
import { computed } from 'vue'
import PanelShell from '@/components/PanelShell.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

const avgColor = computed(() => {
  const v = store.avgGateOpening
  if (v >= 80) return '#53f2c5'
  if (v >= 40) return '#ffd56a'
  return '#ff6b8a'
})

const GATE_VIEWS: Record<number, { lng: number; lat: number; height: number; heading: number; pitch: number }> = {
  1: { lng: 120.3549629353556,  lat: 31.51400482575897,  height: 61, heading: 47.2,  pitch: -18.2 },
  2: { lng: 120.35460074292405, lat: 31.511814290951428, height: 65, heading: 45.8,  pitch: -11.8 },
  3: { lng: 120.33812729333435, lat: 31.508428020435232, height: 28, heading: 256.9, pitch: -8.4  },
  4: { lng: 120.35532559482394, lat: 31.508529829761887, height: 56, heading: 332.2, pitch: -10.4 },
  5: { lng: 120.36095310584776, lat: 31.50685473668009,  height: 65, heading: 44.9,  pitch: -14.7 },
  6: { lng: 120.36181697881254, lat: 31.499055036004666, height: 37, heading: 43.4,  pitch: -12.5 },
  7: { lng: 120.34492218153132, lat: 31.522741981040646, height: 29, heading: 284.8, pitch: -7.7  },
  8: { lng: 120.36279424360026, lat: 31.487899943142036, height: 19, heading: 153.3, pitch: -4.1  },
}

function flyToGate(gateId: number) {
  const view = GATE_VIEWS[gateId]
  if (!view) return
  store.setActiveGate(gateId)
  ;(window as any).__cesiumFlyTo?.(view.lng, view.lat, view.height, view.heading, view.pitch)
}
</script>

<template>
  <PanelShell title="闸门监测" eyebrow="GATE CONTROL" class="gate-panel">
    <div class="gate-panel-content">

      <div class="gate-avg-row">
        <span class="gate-avg-label">综合平均开度</span>
        <strong class="gate-avg-value" :style="{ color: avgColor }">
          {{ store.avgGateOpening }}<small>%</small>
        </strong>
        <div class="gate-avg-bar">
          <div
            class="gate-avg-fill"
            :style="{ width: `${store.avgGateOpening}%`, background: avgColor, boxShadow: `0 0 10px ${avgColor}88` }"
          />
        </div>
      </div>

      <div class="gate-list">
        <div v-for="gate in store.gates" :key="gate.id" class="gate-item">
          <div class="gate-item-head">
            <span class="gate-item-name">{{ gate.name }}</span>
            <div class="gate-item-head-right">
              <button
                v-if="GATE_VIEWS[gate.id]"
                class="gate-view-btn"
                type="button"
                title="切换到该水闸视角"
                @click="flyToGate(gate.id)"
              >视角切换</button>
              <span class="gate-item-pct" :style="{ color: gate.openingPct > 0 ? '#53f2c5' : 'rgba(180,220,255,0.4)' }">
                {{ gate.openingPct }}%
              </span>
            </div>
          </div>

          <div class="gate-slider-row">
            <button
              class="gate-btn"
              type="button"
              :disabled="gate.openingPct <= 0"
              @click="store.setGateOpening(gate.id, gate.openingPct - 10)"
              aria-label="降低开度"
            >−</button>

            <div class="gate-slider-wrap">
              <input
                type="range"
                min="0"
                max="100"
                step="5"
                :value="gate.openingPct"
                class="gate-slider"
                :style="{ '--fill': `${gate.openingPct}%` }"
                @input="store.setGateOpening(gate.id, +($event.target as HTMLInputElement).value)"
              />
            </div>

            <button
              class="gate-btn"
              type="button"
              :disabled="gate.openingPct >= 100"
              @click="store.setGateOpening(gate.id, gate.openingPct + 10)"
              aria-label="提升开度"
            >＋</button>
          </div>

          <div class="gate-status-bar">
            <div
              class="gate-status-fill"
              :style="{
                width: `${gate.openingPct}%`,
                background: gate.openingPct === 0
                  ? 'rgba(100,160,200,0.2)'
                  : `linear-gradient(90deg, #4dc9f655, #4dc9f6)`,
                boxShadow: gate.openingPct > 0 ? '0 0 8px #4dc9f666' : 'none',
              }"
            />
          </div>
        </div>
      </div>

    </div>
  </PanelShell>
</template>

<style scoped>
.gate-panel-content {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
}

.gate-avg-row {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-rows: auto auto;
  gap: 4px 8px;
  padding: 8px 10px;
  border: 1px solid rgba(0, 242, 255, 0.18);
  background: rgba(0, 11, 33, 0.5);
  border-radius: 4px;
}

.gate-avg-label {
  font-size: 10px;
  color: #8fbfd5;
  align-self: center;
}

.gate-avg-value {
  font-size: 22px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  text-align: right;
}

.gate-avg-value small {
  font-size: 12px;
  font-weight: 400;
  margin-left: 2px;
}

.gate-avg-bar {
  grid-column: 1 / -1;
  height: 5px;
  border-radius: 3px;
  background: rgba(0, 242, 255, 0.1);
  overflow: hidden;
}

.gate-avg-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease, background 0.4s ease;
}

.gate-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 2px;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 242, 255, 0.35) rgba(0, 11, 33, 0.45);
}

.gate-list::-webkit-scrollbar {
  width: 4px;
}

.gate-list::-webkit-scrollbar-track {
  border-radius: 2px;
  background: rgba(0, 11, 33, 0.45);
}

.gate-list::-webkit-scrollbar-thumb {
  border-radius: 2px;
  background: rgba(0, 242, 255, 0.35);
}

.gate-list::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 242, 255, 0.55);
}

.gate-item {
  padding: 6px 8px;
  border: 1px solid rgba(0, 242, 255, 0.12);
  background: rgba(0, 11, 33, 0.36);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.gate-item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.gate-item-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.gate-view-btn {
  height: 20px;
  padding: 0 6px;
  border: 1px solid rgba(0, 242, 255, 0.35);
  border-radius: 3px;
  background: rgba(0, 50, 90, 0.6);
  color: rgba(0, 242, 255, 0.8);
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  flex-shrink: 0;
  white-space: nowrap;
}

.gate-view-btn:hover {
  background: rgba(0, 100, 160, 0.8);
  border-color: rgba(0, 242, 255, 0.8);
  color: #ffffff;
}

.gate-item-name {
  font-size: 11px;
  font-weight: 700;
  color: #b8deff;
}

.gate-item-pct {
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  transition: color 0.3s;
}

.gate-slider-row {
  display: grid;
  grid-template-columns: 22px 1fr 22px;
  align-items: center;
  gap: 5px;
}

.gate-btn {
  width: 22px;
  height: 22px;
  border: 1px solid rgba(0, 242, 255, 0.28);
  background: rgba(0, 50, 90, 0.6);
  color: #7ddcff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  transition: background 0.15s;
}

.gate-btn:hover:not(:disabled) {
  background: rgba(0, 100, 160, 0.7);
}

.gate-btn:disabled {
  opacity: 0.3;
  cursor: default;
}

.gate-slider-wrap {
  position: relative;
}

.gate-slider {
  -webkit-appearance: none;
  appearance: none;
  width: 100%;
  height: 4px;
  border-radius: 2px;
  background: linear-gradient(
    to right,
    rgba(0, 200, 255, 0.7) 0%,
    rgba(0, 200, 255, 0.7) var(--fill, 0%),
    rgba(0, 242, 255, 0.1) var(--fill, 0%),
    rgba(0, 242, 255, 0.1) 100%
  );
  outline: none;
  cursor: pointer;
}

.gate-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #00c8ff;
  border: 2px solid #031427;
  box-shadow: 0 0 6px #00c8ffaa;
  cursor: pointer;
}

.gate-slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #00c8ff;
  border: 2px solid #031427;
  box-shadow: 0 0 6px #00c8ffaa;
  cursor: pointer;
}

.gate-status-bar {
  height: 3px;
  border-radius: 2px;
  background: rgba(0, 242, 255, 0.08);
  overflow: hidden;
}

.gate-status-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.4s ease;
}
</style>
