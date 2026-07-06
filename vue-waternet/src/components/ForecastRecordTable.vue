<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import SimulationRecordSettingsModal from '@/components/SimulationRecordSettingsModal.vue'
import EmergencyPlanPreviewModal from '@/components/EmergencyPlanPreviewModal.vue'
import { fetchForecastRecordDetail } from '@/services/api'
import type { ForecastRecordSummary, SimulationRecordSettings } from '@/types/platform'

const props = withDefaults(
  defineProps<{
    records: ForecastRecordSummary[]
    activeRecordId: number | null
    loading?: boolean
    switchingId?: number | null
    variant?: 'forecast' | 'simulation' | 'plan'
    durationLabel?: string
    emptyText?: string
    filterLabel?: string
    filterEmptyText?: string
  }>(),
  {
    variant: 'forecast',
    durationLabel: '预报时长',
    emptyText: '暂无记录',
    switchingId: null,
  },
)

const emit = defineEmits<{
  switch: [id: number]
  remove: [id: number]
}>()

const filterStartAt = ref('')
const filterEndAt = ref('')
const settingsModalOpen = ref(false)
const planPreviewOpen = ref(false)
const planPreviewRecordName = ref('')
const planPreviewDocumentId = ref<string | undefined>()
const settingsLoading = ref(false)
const settingsError = ref('')
const activeSettings = ref<SimulationRecordSettings | null>(null)

watch(planPreviewOpen, (open) => {
  if (!open) {
    planPreviewRecordName.value = ''
    planPreviewDocumentId.value = undefined
  }
})

watch(settingsModalOpen, (open) => {
  if (!open) {
    settingsError.value = ''
    activeSettings.value = null
  }
})

const hasActiveFilter = computed(() => Boolean(filterStartAt.value || filterEndAt.value))

const isScenarioVariant = computed(() => props.variant === 'simulation' || props.variant === 'plan')

const visibleRecords = computed(() => {
  if (!isScenarioVariant.value || !hasActiveFilter.value) {
    return props.records
  }

  const startMs = filterStartAt.value ? new Date(filterStartAt.value).getTime() : null
  const endMs = filterEndAt.value ? new Date(filterEndAt.value).getTime() : null

  return props.records.filter((record) => {
    const recordMs = new Date(record.calculatedAt).getTime()
    if (Number.isNaN(recordMs)) {
      return true
    }
    if (startMs !== null && !Number.isNaN(startMs) && recordMs < startMs) {
      return false
    }
    if (endMs !== null && !Number.isNaN(endMs) && recordMs > endMs) {
      return false
    }
    return true
  })
})

const scenarioNameHeader = computed(() => (props.variant === 'plan' ? '预案名称' : '预演名称'))

const scenarioStartHeader = computed(() => (props.variant === 'plan' ? '预案开始时间' : '预演开始时间'))

const displayEmptyText = computed(() => {
  if (isScenarioVariant.value && hasActiveFilter.value && !visibleRecords.value.length) {
    return props.filterEmptyText ?? props.emptyText
  }
  return props.emptyText
})

function isRowBusy(recordId: number) {
  return props.switchingId === recordId || (props.loading && props.switchingId == null)
}

function isTableBusy() {
  return props.switchingId != null || Boolean(props.loading)
}

function clearFilter() {
  filterStartAt.value = ''
  filterEndAt.value = ''
}

function formatCalculatedAt(value: string) {
  const date = new Date(value)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}

function formatForecastDuration(hours: number) {
  const days = Math.floor(hours / 24)
  const remain = Number((hours - days * 24).toFixed(1))
  if (days > 0 && remain > 0) {
    return `${days}天${remain}小时`
  }
  if (days > 0) {
    return `${days}天`
  }
  return `${remain}小时`
}

async function openSettings(recordId: number) {
  settingsModalOpen.value = true
  settingsLoading.value = true
  settingsError.value = ''
  activeSettings.value = null

  try {
    const detail = await fetchForecastRecordDetail(recordId)
    if (detail.settings) {
      activeSettings.value = detail.settings
      return
    }
    if (detail.recordType === 'simulation' || detail.recordType === 'plan') {
      activeSettings.value = {
        simulationName: detail.simulationName ?? '--',
        simulationStartAt: detail.calculatedAt,
        forecastHours: detail.forecastHours,
        dt: detail.result.dt,
        nSteps: detail.result.nSteps,
        status: detail.result.status,
        boundaryValues: detail.result.boundaryValues,
        boundarySeries: null,
        gateOpenings: null,
      }
      return
    }
    settingsError.value = props.variant === 'plan' ? '该记录不含预案设置' : '该记录不含预演设置'
  } catch {
    settingsError.value = props.variant === 'plan' ? '加载预案设置失败' : '加载预演设置失败'
  } finally {
    settingsLoading.value = false
  }
}

function openPlanPreview(record?: ForecastRecordSummary, documentId?: string) {
  planPreviewRecordName.value = record
    ? record.simulationName || formatCalculatedAt(record.calculatedAt)
    : ''
  planPreviewDocumentId.value = documentId
  planPreviewOpen.value = true
}
</script>

<template>
  <div class="forecast-record-table" :class="[variant, { simulation: variant === 'simulation' }]">
    <div v-if="isScenarioVariant" class="forecast-record-filter">
      <div class="forecast-record-filter-head">
        <span class="forecast-record-filter-label">{{ filterLabel ?? '开始时间筛选' }}</span>
        <button
          v-if="hasActiveFilter"
          type="button"
          class="forecast-record-filter-clear"
          @click="clearFilter"
        >
          清除
        </button>
      </div>
      <div class="forecast-record-filter-inputs">
        <label class="forecast-record-filter-field">
          起
          <input v-model="filterStartAt" type="datetime-local" step="300" />
        </label>
        <label class="forecast-record-filter-field">
          止
          <input v-model="filterEndAt" type="datetime-local" step="300" />
        </label>
      </div>
      <p v-if="hasActiveFilter" class="forecast-record-filter-summary">
        显示 {{ visibleRecords.length }} / {{ records.length }} 条
      </p>
    </div>

    <div class="forecast-record-table-scroll">
    <template v-if="variant === 'plan'">
      <div class="forecast-record-table-head">
        <span>开始时间</span>
        <span>名称</span>
        <span>{{ durationLabel }}</span>
        <span>设置</span>
        <span>预案查看</span>
        <span>切换</span>
        <span>删除</span>
      </div>
      <div v-if="visibleRecords.length" class="forecast-record-table-body">
        <div
          v-for="record in visibleRecords"
          :key="record.id"
          class="forecast-record-table-row"
          :class="{ active: record.id === activeRecordId }"
        >
          <span class="forecast-record-time">{{ formatCalculatedAt(record.calculatedAt) }}</span>
          <span class="forecast-record-name" :title="record.simulationName ?? ''">
            {{ record.simulationName || '--' }}
          </span>
          <strong>{{ formatForecastDuration(record.forecastHours) }}</strong>
          <div class="forecast-record-action">
            <button
              type="button"
              class="forecast-record-settings-btn"
              :disabled="isTableBusy()"
              @click="openSettings(record.id)"
            >
              查看
            </button>
          </div>
          <div class="forecast-record-action">
            <button
              type="button"
              class="forecast-record-settings-btn forecast-record-plan-btn"
              :disabled="isTableBusy()"
              title="查看无锡市应急预案 PDF"
              @click="openPlanPreview(record)"
            >
              查看
            </button>
          </div>
          <div class="forecast-record-action">
            <button
              type="button"
              class="secondary-action forecast-record-btn"
              :class="{ active: record.id === activeRecordId }"
              :disabled="isRowBusy(record.id)"
              @click="emit('switch', record.id)"
            >
              {{ record.id === activeRecordId ? '当前' : isRowBusy(record.id) ? '加载中' : '切换' }}
            </button>
          </div>
          <div class="forecast-record-action">
            <button
              type="button"
              class="forecast-record-delete-btn"
              :disabled="isTableBusy()"
              @click="emit('remove', record.id)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
      <div v-else class="model-result-empty forecast-record-empty">
        <p>{{ displayEmptyText }}</p>
        <p class="forecast-record-empty-hint">每条预案记录对应一条预演计算记录，可切换后查看 PDF。</p>
      </div>
    </template>

    <template v-else>
    <div class="forecast-record-table-head">
      <template v-if="isScenarioVariant">
        <span>{{ scenarioStartHeader }}</span>
        <span>{{ scenarioNameHeader }}</span>
        <span>{{ durationLabel }}</span>
        <span>设置</span>
      </template>
      <template v-else>
        <span>计算时刻</span>
        <span>{{ durationLabel }}</span>
      </template>
      <span>切换调用</span>
      <span>删除</span>
    </div>
    <div v-if="visibleRecords.length" class="forecast-record-table-body">
      <div
        v-for="record in visibleRecords"
        :key="record.id"
        class="forecast-record-table-row"
        :class="{ active: record.id === activeRecordId }"
      >
        <span>{{ formatCalculatedAt(record.calculatedAt) }}</span>
        <span v-if="isScenarioVariant" class="forecast-record-name" :title="record.simulationName ?? ''">
          {{ record.simulationName || '--' }}
        </span>
        <strong>{{ formatForecastDuration(record.forecastHours) }}</strong>
        <div v-if="isScenarioVariant" class="forecast-record-action">
          <button
            type="button"
            class="forecast-record-settings-btn"
            :disabled="isTableBusy()"
            @click="openSettings(record.id)"
          >
            查看
          </button>
        </div>
        <div class="forecast-record-action">
          <button
            type="button"
            class="secondary-action forecast-record-btn"
            :class="{ active: record.id === activeRecordId }"
            :disabled="isRowBusy(record.id)"
            @click="emit('switch', record.id)"
          >
            {{ record.id === activeRecordId ? '当前' : isRowBusy(record.id) ? '加载中' : '切换' }}
          </button>
        </div>
        <div class="forecast-record-action">
          <button
            type="button"
            class="forecast-record-delete-btn"
            :disabled="isTableBusy()"
            @click="emit('remove', record.id)"
          >
            删除
          </button>
        </div>
      </div>
    </div>
    <div v-else class="model-result-empty">{{ displayEmptyText }}</div>
    </template>
    </div>

    <SimulationRecordSettingsModal
      v-if="isScenarioVariant"
      :open="settingsModalOpen"
      :settings="activeSettings"
      :loading="settingsLoading"
      :error="settingsError"
      @close="settingsModalOpen = false"
    />

    <EmergencyPlanPreviewModal
      v-if="variant === 'plan'"
      :open="planPreviewOpen"
      :record-name="planPreviewRecordName"
      :initial-document-id="planPreviewDocumentId"
      @close="planPreviewOpen = false"
    />
  </div>
</template>
