<script setup lang="ts">
import { onMounted } from 'vue'

import PanelShell from '@/components/PanelShell.vue'
import { usePlatformStore } from '@/stores/platform'

const store = usePlatformStore()

onMounted(() => {
  void store.loadDashboard()
})
</script>

<template>
  <div class="business-grid single">
    <PanelShell title="告警事件" eyebrow="WARNING">
      <div class="data-table">
        <div class="table-head">
          <span>对象</span>
          <span>指标</span>
          <span>当前值</span>
          <span>阈值</span>
          <span>等级</span>
          <span>状态</span>
        </div>
        <div v-for="warning in store.warnings" :key="warning.id" class="table-row">
          <span>{{ warning.targetName }}</span>
          <span>{{ warning.metric }}</span>
          <span>{{ warning.value }}</span>
          <span>{{ warning.threshold }}</span>
          <span>{{ warning.level }}</span>
          <span>{{ warning.status }}</span>
        </div>
      </div>
    </PanelShell>
  </div>
</template>
