<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import InsightLineChart from '@/components/InsightLineChart.vue'
import { usePlatformStore } from '@/stores/platform'
import { fetchSegmentProfile } from '@/services/api'
import type { SegmentProfile } from '@/types/platform'

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

// 节点关联河段列表
const nodeConnectedSegments = computed(() => {
  if (!isNode.value || !selected.value) return []
  const node = store.network.nodes.find(n => n.code === selected.value!.code)
  if (!node?.connectedSegmentCodes?.length) return []
  return node.connectedSegmentCodes
    .map(code => store.network.segments.find(s => s.code === code))
    .filter((s): s is NonNullable<typeof s> => s != null)
})

// 节点模式下当前激活的河段 tab
const activeNodeSegCode = ref<string>('')
const nodeSegProfileLoading = ref(false)
const nodeSegProfile = ref<SegmentProfile | null>(null)

watch(nodeConnectedSegments, (segs) => {
  if (segs.length > 0) {
    if (!activeNodeSegCode.value || !segs.find(s => s.code === activeNodeSegCode.value)) {
      activeNodeSegCode.value = segs[0].code
    }
  } else {
    activeNodeSegCode.value = ''
    nodeSegProfile.value = null
  }
}, { immediate: true })

watch(activeNodeSegCode, async (code) => {
  if (!code || !isNode.value) return
  nodeSegProfileLoading.value = true
  try {
    nodeSegProfile.value = await fetchSegmentProfile(code)
  } finally {
    nodeSegProfileLoading.value = false
  }
}, { immediate: true })

// 切换 tab 时加载
async function selectNodeSegTab(code: string) {
  activeNodeSegCode.value = code
}

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

// 河段模式 — 沿程数据
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

// 节点模式 — 关联河段沿程数据
const nodeSegFlowValues = computed(() =>
  (nodeSegProfile.value?.points ?? []).map(p => p.flow ?? NaN),
)
const nodeSegLevelValues = computed(() =>
  (nodeSegProfile.value?.points ?? []).map(p => p.waterLevel ?? NaN),
)
const nodeSegLabels = computed(() =>
  (nodeSegProfile.value?.points ?? []).map(p => `${(p.distanceMeters / 1000).toFixed(1)}km`),
)

// 节点模式 — 下图：节点自身水位时序
const nodeLevelValues = computed(() =>
  isNode.value ? (store.nodeSeries?.points ?? []).map((point) => point.waterLevel ?? NaN) : [],
)
const nodeTimeLabels = computed(() =>
  isNode.value
    ? (store.nodeSeries?.points ?? []).map((point) => {
        const d = new Date(point.hour * 1000)
        const hh = String(d.getHours()).padStart(2, '0')
        const mm = String(d.getMinutes()).padStart(2, '0')
        return hh + ':' + mm
      })
    : [],
)

const upperTitle = computed(() => '末时刻流量沿程过程线')
const lowerTitle = computed(() => isNode.value ? '节点水位过程线' : '末时刻水位沿程过程线')

const hasData = computed(() => {
  if (isSegment.value) return (store.segmentProfile?.points.length ?? 0) > 0
  if (isNode.value) return (nodeSegProfile.value?.points.length ?? 0) > 0 || (store.nodeSeries?.points.length ?? 0) > 0
  return false
})

function fmtProfileHour(ts: number | null | undefined) {
  if (ts == null) return ''
  if (ts > 1e8) {
    const d = new Date(ts * 1000)
    const mo = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const hh = String(d.getHours()).padStart(2, '0')
    const mm = String(d.getMinutes()).padStart(2, '0')
    return `末时刻 ${mo}-${dd} ${hh}:${mm}`
  }
  return ''
}

const profileHourText = computed(() => {
  if (isSegment.value) return fmtProfileHour(store.segmentProfile?.profileHour)
  if (isNode.value) return fmtProfileHour(nodeSegProfile.value?.profileHour)
  return ''
})

const activeProfile = computed(() =>
  isSegment.value ? store.segmentProfile : nodeSegProfile.value
)
const activeFlowValues = computed(() =>
  isSegment.value ? segmentFlowValues.value : nodeSegFlowValues.value
)
const activeLevelValues = computed(() =>
  isSegment.value ? segmentLevelValues.value : nodeSegLevelValues.value
)
const activeLabels = computed(() =>
  isSegment.value ? segmentLabels.value : nodeSegLabels.value
)
</script>

<template>
  <section class="insight-panel">
    <header class="insight-header">
      <div class="insight-title">
        <span>INSIGHT</span>
        <strong>{{ isNode ? '节点关联河段' : isSegment ? '河段沿程线' : '动态查看' }}</strong>
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

    <!-- 节点模式：关联河段 tab 切换 -->
    <div v-if="isNode && nodeConnectedSegments.length > 1" class="insight-seg-tabs">
      <button
        v-for="seg in nodeConnectedSegments"
        :key="seg.code"
        type="button"
        class="insight-seg-tab"
        :class="{ active: activeNodeSegCode === seg.code }"
        @click="selectNodeSegTab(seg.code)"
      >
        {{ seg.reachId != null ? `河段${seg.reachId}` : seg.name }}
      </button>
    </div>
    <div v-else-if="isNode && nodeConnectedSegments.length === 1" class="insight-seg-single">
      <span>关联河段：{{ nodeConnectedSegments[0].name }}</span>
    </div>

    <div class="insight-body">
      <div v-if="!selected" class="insight-hint">
        <p>在地图上点击<strong>河段</strong>或<strong>节点</strong></p>
        <ul>
          <li>点击河段：展示末时刻流量、水位沿程过程线</li>
          <li>点击节点：展示关联河段末时刻沿程过程线</li>
        </ul>
      </div>

      <div v-else-if="store.insightLoading || nodeSegProfileLoading" class="insight-hint">
        <p>数据加载中...</p>
      </div>

      <div v-else-if="isNode && nodeConnectedSegments.length === 0" class="insight-hint">
        <p>该节点无关联河段数据</p>
      </div>

      <div v-else-if="!hasData" class="insight-hint">
        <p>该{{ isNode ? '河段' : '河段' }}暂无数据库过程数据</p>
      </div>

      <template v-else>
        <div class="insight-chart-block">
          <div class="insight-chart-head">
            <span>{{ upperTitle }}</span>
          </div>
          <InsightLineChart
            :values="activeFlowValues"
            :labels="activeLabels"
            color="#00f2ff"
            unit="m3/s"
            :value-digits="2"
            x-axis-title="距离 (km)"
          />
        </div>
        <div class="insight-chart-block">
          <div class="insight-chart-head">
            <span>{{ lowerTitle }}</span>
          </div>
          <InsightLineChart
            :values="isNode ? nodeLevelValues : activeLevelValues"
            :labels="isNode ? nodeTimeLabels : activeLabels"
            color="#7dffb2"
            unit="m"
            :value-digits="3"
            :x-axis-title="isNode ? '时间' : '距离 (km)'"
          />
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.insight-seg-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
  padding: 4px 0 6px;
  border-bottom: 1px solid rgba(0, 242, 255, 0.14);
  margin-bottom: 2px;
}

.insight-seg-tab {
  height: 24px;
  padding: 0 10px;
  border: 1px solid rgba(0, 242, 255, 0.24);
  border-radius: 3px;
  background: rgba(0, 21, 43, 0.58);
  color: #a9dcf0;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s;
}

.insight-seg-tab:hover {
  border-color: rgba(0, 242, 255, 0.6);
  color: #ffffff;
}

.insight-seg-tab.active {
  border-color: rgba(0, 242, 255, 0.8);
  background: rgba(12, 143, 196, 0.78);
  color: #ffffff;
}

.insight-seg-single {
  flex-shrink: 0;
  padding: 4px 0 6px;
  border-bottom: 1px solid rgba(0, 242, 255, 0.14);
  margin-bottom: 2px;
  color: #83aeca;
  font-size: 11px;
}
</style>
