<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

const WATER_LEVEL_HIGH = 14.88
const WATER_LEVEL_LOW  = 12.16
const FLOW_HIGH        = 143.08
const FLOW_LOW         = 12.90

let timer: ReturnType<typeof setInterval> | undefined
const lastRefreshed = ref(new Date())

onMounted(() => {
  timer = setInterval(() => {
    void store.loadDashboard()
    lastRefreshed.value = new Date()
  }, 60_000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

interface SegmentAlert {
  code: string
  name: string
  metric: '水位' | '流量'
  value: number
  direction: 'high' | 'low'
}

const segmentAlerts = computed<SegmentAlert[]>(() => {
  const result: SegmentAlert[] = []
  for (const seg of store.network.segments) {
    const s = seg.hydrologyStats
    if (!s) continue
    if (s.maxWaterLevel != null && s.maxWaterLevel > WATER_LEVEL_HIGH)
      result.push({ code: seg.code, name: seg.name, metric: '水位', value: s.maxWaterLevel, direction: 'high' })
    else if (s.minWaterLevel != null && s.minWaterLevel < WATER_LEVEL_LOW)
      result.push({ code: seg.code, name: seg.name, metric: '水位', value: s.minWaterLevel, direction: 'low' })
    else if (s.maxFlow != null && s.maxFlow > FLOW_HIGH)
      result.push({ code: seg.code, name: seg.name, metric: '流量', value: s.maxFlow, direction: 'high' })
    else if (s.minFlow != null && s.minFlow > 0 && s.minFlow < FLOW_LOW)
      result.push({ code: seg.code, name: seg.name, metric: '流量', value: s.minFlow, direction: 'low' })
  }
  return result
})

const highCount  = computed(() => segmentAlerts.value.filter(a => a.direction === 'high').length)
const lowCount   = computed(() => segmentAlerts.value.filter(a => a.direction === 'low').length)
const totalCount = computed(() => segmentAlerts.value.length)

function fmtTime(d: Date) {
  return [d.getHours(), d.getMinutes(), d.getSeconds()]
    .map(n => String(n).padStart(2, '0')).join(':')
}
function fmtVal(item: SegmentAlert) {
  return item.metric === '水位'
    ? `${item.value.toFixed(2)} m`
    : `${item.value.toFixed(1)} m³/s`
}

const activeCode = computed(() => store.selectedFeature?.code ?? null)

function selectSegment(item: SegmentAlert) {
  if (activeCode.value === item.code) {
    void store.selectFeature(null)
  } else {
    void store.selectFeature({ type: 'segment', code: item.code, name: item.name })
  }
}
</script>

<style scoped>
.alert-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

/* 统计卡片行 — 复用 env-metric-card 风格 */
.alert-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
  flex-shrink: 0;
}
.alert-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 7px 4px 6px;
  border: 1px solid rgba(0, 242, 255, 0.18);
  border-radius: 4px;
  background: linear-gradient(180deg, rgba(0, 242, 255, 0.06), rgba(0, 11, 33, 0.38)),
              rgba(0, 11, 33, 0.46);
  border-top: 2px solid transparent;
}
.alert-card-high  { border-top-color: #ff6060; }
.alert-card-low   { border-top-color: #4dc9f6; }
.alert-card-total { border-top-color: rgba(255, 255, 255, 0.18); }

.alert-card-num {
  font-size: 20px;
  font-weight: 700;
  line-height: 1;
  color: #fff;
}
.alert-card-high  .alert-card-num { color: #ff8080; }
.alert-card-low   .alert-card-num { color: #4dc9f6; }
.alert-card-total .alert-card-num { color: rgba(255,255,255,0.55); }

.alert-card-label {
  font-size: 10px;
  color: #a0d8ef;
}

/* 阈值参考 + 刷新时间同行 */
.alert-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 0 1px;
  overflow: hidden;
}
.alert-threshold {
  display: flex;
  gap: 8px;
  font-size: 10px;
  color: rgba(180, 220, 255, 0.38);
}
.alert-threshold em { font-style: normal; color: rgba(255, 210, 100, 0.6); }

.alert-refresh {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: rgba(180, 220, 255, 0.3);
}
.alert-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: #333;
}
.alert-dot-live {
  background: #ff6060;
  box-shadow: 0 0 4px #ff6060;
  animation: adot 1.6s infinite;
}
@keyframes adot { 0%,100%{opacity:1} 50%{opacity:0.2} }

/* 无预警 */
.alert-ok {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 16px 0 12px;
  color: rgba(160, 216, 239, 0.35);
  font-size: 11px;
}
.alert-ok-icon { font-size: 18px; color: #06d6a0; opacity: 0.6; }

/* 列表 — 复用 model-result-table-row 风格 */
.alert-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 0 2px;
}
.alert-list-head span {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: rgba(0, 242, 255, 0.45);
}
.alert-count {
  font-size: 10px;
  color: rgba(180, 220, 255, 0.35);
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: 260px;
  padding-right: 2px;
  scrollbar-color: rgba(0, 242, 255, 0.35) rgba(0, 11, 33, 0.45);
  scrollbar-width: thin;
}
.alert-list::-webkit-scrollbar { width: 4px; }
.alert-list::-webkit-scrollbar-track { background: rgba(0,11,33,0.45); border-radius: 2px; }
.alert-list::-webkit-scrollbar-thumb { background: rgba(0,242,255,0.35); border-radius: 2px; }

.alert-row {
  display: grid;
  grid-template-columns: 16px 1fr auto auto;
  align-items: center;
  gap: 0 8px;
  padding: 8px 10px;
  border-radius: 3px;
  background: rgba(18, 58, 86, 0.42);
  border-left: 2px solid transparent;
  overflow: hidden;
  min-width: 0;
  flex-shrink: 0;
  width: 100%;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}
.alert-row:hover {
  background: rgba(26, 112, 158, 0.52);
}
.alert-row-active {
  background: rgba(26, 112, 158, 0.62) !important;
  box-shadow: inset 0 0 0 1px rgba(0, 242, 255, 0.32);
}
.alert-row-active .alert-name {
  color: #ffffff;
}
.alert-row-active.alert-row-high {
  box-shadow: inset 0 0 0 1px rgba(255, 96, 96, 0.45);
}
.alert-row-active.alert-row-low {
  box-shadow: inset 0 0 0 1px rgba(77, 201, 246, 0.45);
}
.alert-row-high { border-left-color: #ff6060; }
.alert-row-low  { border-left-color: #4dc9f6; }

.alert-arrow { font-size: 9px; }
.alert-row-high .alert-arrow { color: #ff8080; }
.alert-row-low  .alert-arrow { color: #4dc9f6; }

.alert-name {
  font-size: 11px;
  color: #d9f7ff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.alert-tag {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.07);
  color: rgba(180, 220, 255, 0.55);
  white-space: nowrap;
  flex-shrink: 0;
}

.alert-val {
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
  text-align: right;
}
.alert-row-high .alert-val { color: #ff8080; }
.alert-row-low  .alert-val { color: #4dc9f6; }
</style>

<template>
  <div class="alert-wrap">
    <!-- 三卡统计 -->
    <div class="alert-cards">
      <div class="alert-card alert-card-high">
        <span class="alert-card-num">{{ highCount }}</span>
        <span class="alert-card-label">超上限</span>
      </div>
      <div class="alert-card alert-card-low">
        <span class="alert-card-num">{{ lowCount }}</span>
        <span class="alert-card-label">低于下限</span>
      </div>
      <div class="alert-card alert-card-total">
        <span class="alert-card-num">{{ totalCount }}</span>
        <span class="alert-card-label">预警总数</span>
      </div>
    </div>

    <!-- 阈值 + 刷新时间 -->
    <div class="alert-meta">
      <div class="alert-threshold">
        <span>水位 <em>{{ WATER_LEVEL_LOW }}–{{ WATER_LEVEL_HIGH }}m</em></span>
        <span>流量 <em>{{ FLOW_LOW }}–{{ FLOW_HIGH }}m³/s</em></span>
      </div>
      <div class="alert-refresh">
        <span class="alert-dot" :class="{ 'alert-dot-live': totalCount > 0 }"></span>
        <span>{{ fmtTime(lastRefreshed) }}</span>
      </div>
    </div>

    <!-- 无预警 -->
    <div v-if="totalCount === 0" class="alert-ok">
      <span class="alert-ok-icon">✓</span>
      <span>所有河段指标正常</span>
    </div>

    <!-- 预警列表 -->
    <template v-else>
      <div class="alert-list-head">
        <span>SEGMENT ALERTS</span>
        <span class="alert-count">{{ totalCount }} 条</span>
      </div>
      <div class="alert-list">
        <button
          v-for="item in segmentAlerts"
          :key="item.code"
          type="button"
          class="alert-row"
          :class="[
            item.direction === 'high' ? 'alert-row-high' : 'alert-row-low',
            activeCode === item.code ? 'alert-row-active' : ''
          ]"
          @click="selectSegment(item)"
        >
          <span class="alert-arrow">{{ item.direction === 'high' ? '▲' : '▼' }}</span>
          <span class="alert-name">{{ item.name }}</span>
          <span class="alert-tag">{{ item.metric }}</span>
          <span class="alert-val">{{ fmtVal(item) }}</span>
        </button>
      </div>
    </template>
  </div>
</template>
