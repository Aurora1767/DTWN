<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import PanelShell from '@/components/PanelShell.vue'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? '业务模块'))
</script>

<template>
  <div class="business-grid single">
    <PanelShell :title="title" eyebrow="MODULE">
      <div class="empty-state">
        <strong>{{ title }}</strong>
        <span>模块骨架已接入导航，后续按任务清单继续补充业务表单、查询与图表。</span>
      </div>
    </PanelShell>
  </div>
</template>
