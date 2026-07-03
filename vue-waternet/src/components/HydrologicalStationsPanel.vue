<script setup lang="ts">
import { ref } from 'vue'

import PanelShell from '@/components/PanelShell.vue'
import StationHistoryModal from '@/components/StationHistoryModal.vue'
import { fetchWaterStationHistory } from '@/services/api'
import type { WaterHistoryPoint, WaterStationSnapshot } from '@/types/platform'

const props = defineProps<{
  stations: WaterStationSnapshot[]
  live?: boolean
  updatedAt?: string
}>()

const selectedStation = ref<WaterStationSnapshot | null>(null)
const modalVisible = ref(false)
const selectedHistory = ref<WaterHistoryPoint[]>([])
const historyLoading = ref(false)

async function openHistory(station: WaterStationSnapshot) {
  selectedStation.value = station
  modalVisible.value = true
  historyLoading.value = true
  selectedHistory.value = []
  try {
    selectedHistory.value = await fetchWaterStationHistory(station.stationCode)
  } finally {
    historyLoading.value = false
  }
}

function closeHistory() {
  modalVisible.value = false
}
</script>

<template>
  <PanelShell title="核心水文站" eyebrow="HYDRO" class="hydro-panel">
    <div class="hydro-panel-content">
      <div class="hydro-table">
        <div class="hydro-table-head">
          <span class="hydro-col-station">测站</span>
          <span class="hydro-col-level">水位</span>
          <span class="hydro-col-flow">流量</span>
          <span class="hydro-col-action">历史</span>
        </div>
        <div class="hydro-table-body">
          <div v-for="station in stations" :key="station.stationCode" class="hydro-table-row">
            <span class="hydro-station-name hydro-col-station" :title="station.stationName">{{
              station.stationName
            }}</span>
            <strong class="hydro-col-level">{{ station.waterLevel.toFixed(2) }}</strong>
            <strong class="hydro-col-flow">{{ station.flowRate.toFixed(1) }}</strong>
            <div class="hydro-col-action">
              <button type="button" class="hydro-history-btn" @click="openHistory(station)">查看</button>
            </div>
          </div>
        </div>
      </div>

      <p class="hydro-table-status" :class="{ live: props.live }">
        {{ props.live ? '实时接入' : '离线/缓存' }}
        <template v-if="updatedAt"> · {{ updatedAt }}</template>
      </p>
    </div>

    <StationHistoryModal
      :visible="modalVisible"
      :station="selectedStation"
      :history="selectedHistory"
      :loading="historyLoading"
      @close="closeHistory"
    />
  </PanelShell>
</template>
