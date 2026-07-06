<script setup lang="ts">
import type { RiverNetworkNodeResult } from '@/types/platform'

defineProps<{
  nodes: RiverNetworkNodeResult[]
  emptyText?: string
}>()
</script>

<template>
  <div class="model-result-table">
    <div class="model-result-table-head">
      <span>节点</span>
      <span>水位(m)</span>
      <span>流量(m3/s)</span>
    </div>
    <div v-if="nodes.length" class="model-result-table-body">
      <div v-for="node in nodes" :key="node.nodeId" class="model-result-table-row">
        <span>{{ node.nodeId }}</span>
        <strong>{{ node.waterLevel.toFixed(2) }}</strong>
        <strong>{{ node.netFlow.toFixed(2) }}</strong>
      </div>
    </div>
    <div v-else class="model-result-empty">{{ emptyText ?? '暂无节点计算结果，请先开始预报' }}</div>
  </div>
</template>
