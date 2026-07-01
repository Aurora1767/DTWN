<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import PanelShell from '@/components/PanelShell.vue'
import { fetchHistoryRecords, historyExportUrl } from '@/services/api'
import { usePlatformStore } from '@/stores/platform'
import type { HistoricalSensorRecord } from '@/types/platform'

const store = usePlatformStore()
const records = ref<HistoricalSensorRecord[]>([])
const loading = ref(false)

const filters = reactive({
  nodeCode: '',
  start: '',
  end: '',
})

onMounted(async () => {
  await store.loadDashboard()
  await query()
})

async function query() {
  loading.value = true
  try {
    records.value = await fetchHistoryRecords({
      nodeCode: filters.nodeCode,
      start: toIso(filters.start),
      end: toIso(filters.end),
    })
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  const url = historyExportUrl({
    nodeCode: filters.nodeCode,
    start: toIso(filters.start),
    end: toIso(filters.end),
  })
  window.open(url, '_blank', 'noopener,noreferrer')
}

function toIso(value: string) {
  return value ? new Date(value).toISOString().slice(0, 19) : undefined
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="business-grid history-grid">
    <PanelShell title="查询条件" eyebrow="FILTER">
      <form class="scenario-form" @submit.prevent="query">
        <label>
          节点
          <select v-model="filters.nodeCode">
            <option value="">全部节点</option>
            <option v-for="node in store.network.nodes" :key="node.code" :value="node.code">
              {{ node.name }}
            </option>
          </select>
        </label>
        <label>
          开始时间
          <input v-model="filters.start" type="datetime-local" />
        </label>
        <label>
          结束时间
          <input v-model="filters.end" type="datetime-local" />
        </label>
        <button class="primary-action" :disabled="loading">
          {{ loading ? '查询中' : '查询历史数据' }}
        </button>
        <button type="button" class="secondary-action" @click="exportCsv">导出 CSV</button>
      </form>
    </PanelShell>

    <PanelShell title="历史监测数据" eyebrow="HISTORY">
      <div class="history-summary">
        <div>
          <span>记录数</span>
          <strong>{{ records.length }}</strong>
        </div>
        <div>
          <span>最高水位</span>
          <strong>{{ Math.max(...records.map((record) => record.waterLevel), 0).toFixed(2) }} m</strong>
        </div>
        <div>
          <span>最大流量</span>
          <strong>{{ Math.max(...records.map((record) => record.flow), 0).toFixed(1) }} m3/s</strong>
        </div>
      </div>

      <div class="data-table history-table">
        <div class="table-head">
          <span>时间</span>
          <span>测站</span>
          <span>水位</span>
          <span>流量</span>
          <span>流速</span>
          <span>降雨</span>
          <span>状态</span>
        </div>
        <div v-for="record in records" :key="`${record.stationCode}-${record.observedAt}`" class="table-row">
          <span>{{ formatTime(record.observedAt) }}</span>
          <span>{{ record.stationName }}</span>
          <span>{{ record.waterLevel.toFixed(2) }} m</span>
          <span>{{ record.flow.toFixed(1) }}</span>
          <span>{{ record.velocity.toFixed(2) }}</span>
          <span>{{ record.rainfall.toFixed(1) }}</span>
          <span>{{ record.status }}</span>
        </div>
      </div>
    </PanelShell>
  </div>
</template>
