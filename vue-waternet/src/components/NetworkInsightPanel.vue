<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import InsightLineChart from '@/components/InsightLineChart.vue'
import { usePlatformStore } from '@/stores/platform'

type PickerKind = 'segment' | 'node'

const store = usePlatformStore()

const selected = computed(() => store.selectedFeature)
const isSegment = computed(() => selected.value?.type === 'segment')
const isNode = computed(() => selected.value?.type === 'node')

const pickerKind = ref<PickerKind>('segment')
const pickerCode = ref<string>('')

const segmentOptions = computed(() =>
  [...store.network.segments]
    .filter((segment) => segment.code)
    .sort((left, right) => {
      const leftId = left.reachId ?? Number.MAX_SAFE_INTEGER
      const rightId = right.reachId ?? Number.MAX_SAFE_INTEGER
      if (leftId !== rightId) return leftId - rightId
      return left.code.localeCompare(right.code)
    })
    .map((segment) => ({
      code: segment.code,
      label: segment.reachId != null ? `河段 ${segment.reachId} · ${segment.name}` : segment.name,
    })),
)

const nodeOptions = computed(() =>
  [...store.network.nodes]
    .sort((left, right) => {
      const leftId = Number(left.code.replace(/\D/g, ''))
      const rightId = Number(right.code.replace(/\D/g, ''))
      if (Number.isFinite(leftId) && Number.isFinite(rightId) && leftId !== rightId) return leftId - rightId
      return left.code.localeCompare(right.code)
    })
    .map((node) => ({
      code: node.code,
      label: `${node.code} · ${node.name}`,
    })),
)

const pickerOptions = computed(() => (pickerKind.value === 'segment' ? segmentOptions.value : nodeOptions.value))

watch(
  selected,
  (feature) => {
    if (feature) {
      pickerKind.value = feature.type
      pickerCode.value = feature.code
    }
  },
  { immediate: true },
)

watch(pickerKind, () => {
  if (selected.value?.type !== pickerKind.value) {
    pickerCode.value = ''
  }
})

function onPickerChange(event: Event) {
  const target = event.target as HTMLSelectElement
  const code = target.value
  pickerCode.value = code
  if (!code) return
  const options = pickerOptions.value
  const option = options.find((item) => item.code === code)
  if (!option) return
  void store.selectFeature({ type: pickerKind.value, code: option.code, name: option.label })
}

const segmentFlowValues = computed(() =>
  isSegment.value ? (store.segmentProfile?.points ?? []).map((point) => point.flow ?? NaN) : [],
)
const segmentLevelValues = computed(() =>
  isSegment.value ? (store.segmentProfile?.points ?? []).map((point) => point.waterLevel ?? NaN) : [],
)
const segmentLabels = computed(() =>
  isSegment.value
    ? (store.segmentProfile?.points ?? []).map((point) => `${(point.distanceMeters / 1000).toFixed(1)}km`)
    : [],
)

const nodeFlowValues = computed(() =>
  isNode.value ? (store.nodeSeries?.points ?? []).map((point) => point.flow ?? NaN) : [],
)
const nodeLevelValues = computed(() =>
  isNode.value ? (store.nodeSeries?.points ?? []).map((point) => point.waterLevel ?? NaN) : [],
)
const nodeLabels = computed(() =>
  isNode.value ? (store.nodeSeries?.points ?? []).map((point) => `${point.hour}h`) : [],
)

const upperTitle = computed(() => (isNode.value ? '近72小时流量过程线' : '末时刻流量沿程过程线'))
const lowerTitle = computed(() => (isNode.value ? '近72小时水位过程线' : '末时刻水位沿程过程线'))
const upperAxis = computed(() => (isNode.value ? '时间 (h)' : '距离 (km)'))
const lowerAxis = computed(() => (isNode.value ? '时间 (h)' : '距离 (km)'))

const hasData = computed(() => {
  if (isSegment.value) return (store.segmentProfile?.points.length ?? 0) > 0
  if (isNode.value) return (store.nodeSeries?.points.length ?? 0) > 0
  return false
})

const profileHourText = computed(() => {
  if (isSegment.value && store.segmentProfile?.profileHour != null) {
    return `末时刻 第 ${store.segmentProfile.profileHour} 小时`
  }
  return ''
})
</script>

<template>
  <section class="insight-panel">
    <header class="insight-header">
      <div class="insight-title">
        <span>INSIGHT</span>
        <strong>{{ isNode ? '节点过程线' : isSegment ? '河段沿程线' : '动态查看' }}</strong>
      </div>

      <div class="insight-picker">
        <div class="insight-picker-tabs" role="tablist" aria-label="选择类型">
          <button
            type="button"
            role="tab"
            :aria-selected="pickerKind === 'segment'"
            :class="{ active: pickerKind === 'segment' }"
            @click="pickerKind = 'segment'"
          >
            河段
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="pickerKind === 'node'"
            :class="{ active: pickerKind === 'node' }"
            @click="pickerKind = 'node'"
          >
            节点
          </button>
        </div>
        <select
          class="insight-picker-select"
          :value="pickerCode"
          :aria-label="pickerKind === 'segment' ? '选择河段' : '选择节点'"
          @change="onPickerChange"
        >
          <option value="" disabled>{{ pickerKind === 'segment' ? '选择河段...' : '选择节点...' }}</option>
          <option v-for="option in pickerOptions" :key="option.code" :value="option.code">
            {{ option.label }}
          </option>
        </select>
        <small v-if="profileHourText" class="insight-picker-hint">{{ profileHourText }}</small>
      </div>

      <button v-if="selected" type="button" class="insight-clear" @click="store.clearSelection()">清除</button>
    </header>

    <div class="insight-body">
      <div v-if="!selected" class="insight-hint">
        <p>在地图上点击<strong>河段</strong>或<strong>节点</strong></p>
        <ul>
          <li>点击河段：展示末时刻流量、水位沿程过程线</li>
          <li>点击节点：展示近72小时流量、水位过程线</li>
        </ul>
      </div>

      <div v-else-if="store.insightLoading" class="insight-hint">
        <p>数据加载中...</p>
      </div>

      <div v-else-if="!hasData" class="insight-hint">
        <p>该{{ isNode ? '节点' : '河段' }}暂无数据库过程数据</p>
      </div>

      <template v-else>
        <div class="insight-chart-block">
          <div class="insight-chart-head">
            <span>{{ upperTitle }}</span>
          </div>
          <InsightLineChart
            :values="isNode ? nodeFlowValues : segmentFlowValues"
            :labels="isNode ? nodeLabels : segmentLabels"
            color="#00f2ff"
            unit="m3/s"
            :value-digits="2"
            :x-axis-title="upperAxis"
          />
        </div>
        <div class="insight-chart-block">
          <div class="insight-chart-head">
            <span>{{ lowerTitle }}</span>
          </div>
          <InsightLineChart
            :values="isNode ? nodeLevelValues : segmentLevelValues"
            :labels="isNode ? nodeLabels : segmentLabels"
            color="#7dffb2"
            unit="m"
            :value-digits="3"
            :x-axis-title="lowerAxis"
          />
        </div>
      </template>
    </div>
  </section>
</template>
