<script setup lang="ts">
import { computed } from 'vue'

import {
  DEFAULT_GATE_OPENING_PERCENT,
  GATE_OPENING_STATIONS,
  normalizeGateOpeningPercent,
} from '@/constants/gateOpeningStations'

const props = defineProps<{
  modelValue: Record<string, number | ''>
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, number | ''>]
}>()

const hasCustomOpening = computed(() =>
  GATE_OPENING_STATIONS.some((station) => {
    const raw = props.modelValue[String(station.nodeId)]
    return raw !== '' && normalizeGateOpeningPercent(raw) !== DEFAULT_GATE_OPENING_PERCENT
  }),
)

function displayValue(nodeId: number) {
  const raw = props.modelValue[String(nodeId)]
  return raw === '' ? '' : String(raw)
}

function updateOpening(nodeId: number, raw: string) {
  const next = { ...props.modelValue }
  if (raw.trim() === '') {
    next[String(nodeId)] = ''
  } else {
    next[String(nodeId)] = normalizeGateOpeningPercent(raw)
  }
  emit('update:modelValue', next)
}

function resetAll() {
  const next: Record<string, number | ''> = {}
  for (const station of GATE_OPENING_STATIONS) {
    next[String(station.nodeId)] = ''
  }
  emit('update:modelValue', next)
}
</script>

<template>
  <div class="gate-opening-settings">
    <div class="gate-opening-head">
      <span class="gate-opening-label">闸门开度</span>
      <button
        v-if="hasCustomOpening"
        type="button"
        class="gate-opening-reset"
        :disabled="disabled"
        @click="resetAll"
      >
        恢复默认
      </button>
    </div>
    <p class="gate-opening-note">
      不填写则默认 100% 全开；28 号双孔同步同一开度。预演计算采用
      Q = μbe√(2gH<sub>u</sub>)，μ = 0.60 − 0.176·(e/H<sub>u</sub>)，H<sub>u</sub> 为节点水深。
    </p>
    <div class="gate-opening-grid">
      <label
        v-for="station in GATE_OPENING_STATIONS"
        :key="station.id"
        class="gate-opening-field"
      >
        <span class="gate-opening-field-head">
          <strong>{{ station.label }}</strong>
          <em>{{ station.widthLabel }}</em>
        </span>
        <div class="gate-opening-input-wrap">
          <input
            :value="displayValue(station.nodeId)"
            type="number"
            min="0"
            max="100"
            step="1"
            placeholder="100"
            :disabled="disabled"
            @input="updateOpening(station.nodeId, ($event.target as HTMLInputElement).value)"
          />
          <span>%</span>
        </div>
      </label>
    </div>
  </div>
</template>
