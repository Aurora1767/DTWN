<script setup lang="ts">
import { onMounted, ref } from 'vue'

import PanelShell from '@/components/PanelShell.vue'
import { fetchDispatchPlans } from '@/services/api'
import type { DispatchPlan } from '@/types/platform'

const plans = ref<DispatchPlan[]>([])
const activeCode = ref('')

onMounted(async () => {
  plans.value = await fetchDispatchPlans()
  activeCode.value = plans.value[0]?.code ?? ''
})

function levelClass(level: string) {
  return level.toLowerCase()
}

function formatTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div class="business-grid scenario-grid">
    <PanelShell title="预案库" eyebrow="PLAN">
      <div class="plan-list">
        <button
          v-for="plan in plans"
          :key="plan.code"
          type="button"
          :class="{ active: plan.code === activeCode }"
          @click="activeCode = plan.code"
        >
          <span>{{ plan.type }}</span>
          <strong>{{ plan.name }}</strong>
          <em :class="levelClass(plan.riskLevel)">{{ plan.riskLevel }}</em>
        </button>
      </div>
    </PanelShell>

    <PanelShell title="预案详情" eyebrow="DETAIL">
      <article v-for="plan in plans.filter((item) => item.code === activeCode)" :key="plan.code" class="plan-detail">
        <header>
          <div>
            <span>{{ plan.code }}</span>
            <h2>{{ plan.name }}</h2>
          </div>
          <em :class="levelClass(plan.riskLevel)">{{ plan.riskLevel }}</em>
        </header>

        <section>
          <h3>触发条件</h3>
          <p>{{ plan.triggerCondition }}</p>
        </section>

        <section>
          <h3>调度措施</h3>
          <div class="measure-list">
            <span v-for="measure in plan.measures" :key="measure">{{ measure }}</span>
          </div>
        </section>

        <section>
          <h3>预期效果</h3>
          <p>{{ plan.expectedEffect }}</p>
        </section>

        <footer>
          <span>关联河段：{{ plan.relatedSegments.join(' / ') }}</span>
          <span>更新时间：{{ formatTime(plan.updatedAt) }}</span>
        </footer>
      </article>
    </PanelShell>
  </div>
</template>
