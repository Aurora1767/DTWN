<script setup lang="ts">
import { computed, ref } from 'vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

type TabKind = 'node' | 'segment'
const activeTab = ref<TabKind>('node')
const searchText = ref('')

const filteredNodes = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  const nodes = store.network.nodes.filter(n => n.latestHydrology)
  if (!q) return nodes
  return nodes.filter(n =>
    n.code.toLowerCase().includes(q) || n.name.toLowerCase().includes(q)
  )
})

const filteredSegments = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  const segs = store.network.segments.filter(s => s.hydrologyStats)
  if (!q) return segs
  return segs.filter(s =>
    s.code.toLowerCase().includes(q) || s.name.toLowerCase().includes(q)
  )
})

const activeCode = computed(() => store.selectedFeature?.code ?? null)

function selectNode(code: string, name: string) {
  if (activeCode.value === code) {
    void store.selectFeature(null)
  } else {
    void store.selectFeature({ type: 'node', code, name })
  }
}

function selectSegment(code: string, name: string) {
  if (activeCode.value === code) {
    void store.selectFeature(null)
  } else {
    void store.selectFeature({ type: 'segment', code, name })
  }
}

function fmtNum(v: number | null | undefined, d = 2) {
  return v != null && Number.isFinite(v) ? v.toFixed(d) : '--'
}

function fmtTs(v: number | null | undefined) {
  if (v == null || !Number.isFinite(v)) return '--'
  if (v > 1e8) {
    const date = new Date(v * 1000)
    const hh = String(date.getHours()).padStart(2, '0')
    const mm = String(date.getMinutes()).padStart(2, '0')
    return `${hh}:${mm}`
  }
  return String(v)
}
</script>

<style scoped>
.hdt-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.hdt-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  margin-bottom: 6px;
}

.hdt-tabs {
  display: flex;
  overflow: hidden;
  border: 1px solid rgba(0, 242, 255, 0.32);
  border-radius: 3px;
}
.hdt-tabs button {
  min-width: 48px;
  height: 26px;
  padding: 0 10px;
  border: 0;
  background: transparent;
  color: #a9dcf0;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}
.hdt-tabs button + button {
  border-left: 1px solid rgba(0, 242, 255, 0.24);
}
.hdt-tabs button.active {
  background: rgba(12, 143, 196, 0.78);
  color: #ffffff;
}

.hdt-search {
  flex: 1;
  min-width: 0;
  height: 26px;
  padding: 0 8px;
  border: 1px solid rgba(0, 242, 255, 0.24);
  border-radius: 3px;
  background: rgba(0, 21, 43, 0.72);
  color: #e5f7ff;
  font-size: 11px;
}
.hdt-search::placeholder { color: rgba(160, 216, 239, 0.4); }
.hdt-search:focus { outline: 1px solid rgba(0, 242, 255, 0.6); outline-offset: -1px; }

.hdt-count {
  flex-shrink: 0;
  font-size: 10px;
  color: rgba(180, 220, 255, 0.4);
}

/* 表格 */
.hdt-table {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.hdt-thead {
  display: grid;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  flex-shrink: 0;
  border-bottom: 1px solid rgba(0, 242, 255, 0.14);
  color: #7ea8bf;
  font-size: 10px;
}
.hdt-thead-node { grid-template-columns: 42px 1fr 64px 64px 48px; }
.hdt-thead-seg  { grid-template-columns: 42px 1fr 56px 56px 56px 56px; }

.hdt-tbody {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 242, 255, 0.35) rgba(0, 11, 33, 0.45);
}
.hdt-tbody::-webkit-scrollbar { width: 4px; }
.hdt-tbody::-webkit-scrollbar-track { background: rgba(0, 11, 33, 0.45); border-radius: 2px; }
.hdt-tbody::-webkit-scrollbar-thumb { background: rgba(0, 242, 255, 0.35); border-radius: 2px; }

.hdt-row {
  display: grid;
  align-items: center;
  gap: 4px;
  padding: 5px 6px;
  border-bottom: 1px solid rgba(0, 242, 255, 0.06);
  font-size: 11px;
  color: #d9f7ff;
  cursor: pointer;
  transition: background 0.12s;
  width: 100%;
  text-align: left;
  background: transparent;
  border-left: 2px solid transparent;
  border-right: 0;
  border-top: 0;
}
.hdt-row:hover { background: rgba(26, 112, 158, 0.38); }
.hdt-row-active {
  background: rgba(26, 112, 158, 0.55);
  border-left-color: #00f2ff;
}

.hdt-row-node { grid-template-columns: 42px 1fr 64px 64px 48px; }
.hdt-row-seg  { grid-template-columns: 42px 1fr 56px 56px 56px 56px; }

.hdt-cell-code {
  font-size: 10px;
  color: #8fbcd5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hdt-cell-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #cce8ff;
}
.hdt-cell-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  white-space: nowrap;
}
.hdt-cell-time {
  text-align: right;
  font-size: 10px;
  color: #8fbcd5;
  white-space: nowrap;
}

.hdt-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px 0;
  color: rgba(160, 216, 239, 0.35);
  font-size: 11px;
}
</style>

<template>
  <div class="hdt-wrap">
    <div class="hdt-toolbar">
      <div class="hdt-tabs">
        <button :class="{ active: activeTab === 'node' }" @click="activeTab = 'node'">节点</button>
        <button :class="{ active: activeTab === 'segment' }" @click="activeTab = 'segment'">河段</button>
      </div>
      <input
        v-model="searchText"
        class="hdt-search"
        placeholder="搜索编码或名称..."
        type="text"
      />
      <span class="hdt-count">
        {{ activeTab === 'node' ? filteredNodes.length : filteredSegments.length }} 条
      </span>
    </div>

    <!-- 节点表 -->
    <div v-if="activeTab === 'node'" class="hdt-table">
      <div class="hdt-thead hdt-thead-node">
        <span>编码</span><span>名称</span><span style="text-align:right">水位(m)</span><span style="text-align:right">流量</span><span style="text-align:right">时间</span>
      </div>
      <div class="hdt-tbody">
        <div v-if="filteredNodes.length === 0" class="hdt-empty">暂无数据</div>
        <button
          v-for="node in filteredNodes"
          :key="node.code"
          type="button"
          class="hdt-row hdt-row-node"
          :class="{ 'hdt-row-active': activeCode === node.code }"
          @click="selectNode(node.code, node.name)"
        >
          <span class="hdt-cell-code">{{ node.code }}</span>
          <span class="hdt-cell-name">{{ node.name }}</span>
          <span class="hdt-cell-num">{{ fmtNum(node.latestHydrology?.waterLevel) }}</span>
          <span class="hdt-cell-num">{{ fmtNum(node.latestHydrology?.flow, 1) }}</span>
          <span class="hdt-cell-time">{{ fmtTs(node.latestHydrology?.hour) }}</span>
        </button>
      </div>
    </div>

    <!-- 河段表 -->
    <div v-if="activeTab === 'segment'" class="hdt-table">
      <div class="hdt-thead hdt-thead-seg">
        <span>编码</span><span>名称</span><span style="text-align:right">Q max</span><span style="text-align:right">Q min</span><span style="text-align:right">Z max</span><span style="text-align:right">Z min</span>
      </div>
      <div class="hdt-tbody">
        <div v-if="filteredSegments.length === 0" class="hdt-empty">暂无数据</div>
        <button
          v-for="seg in filteredSegments"
          :key="seg.code"
          type="button"
          class="hdt-row hdt-row-seg"
          :class="{ 'hdt-row-active': activeCode === seg.code }"
          @click="selectSegment(seg.code, seg.name)"
        >
          <span class="hdt-cell-code">{{ seg.reachId ?? seg.code }}</span>
          <span class="hdt-cell-name">{{ seg.name }}</span>
          <span class="hdt-cell-num">{{ fmtNum(seg.hydrologyStats?.maxFlow, 1) }}</span>
          <span class="hdt-cell-num">{{ fmtNum(seg.hydrologyStats?.minFlow, 1) }}</span>
          <span class="hdt-cell-num">{{ fmtNum(seg.hydrologyStats?.maxWaterLevel) }}</span>
          <span class="hdt-cell-num">{{ fmtNum(seg.hydrologyStats?.minWaterLevel) }}</span>
        </button>
      </div>
    </div>
  </div>
</template>
