<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { label: '综合态势', to: '/dashboard' },
  { label: '数据底板', to: '/map' },
  { label: '仿真预演', to: '/simulation' },
  { label: '风险预警', to: '/warning' },
  { label: '预案管理', to: '/scenario' },
  { label: '历史查询', to: '/history' },
  { label: '系统管理', to: '/admin' },
]

const pageTitle = computed(() => String(route.meta.title ?? '综合态势'))
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand-block">
        <span class="brand-mark">水</span>
        <div>
          <strong>数字孪生水网平台</strong>
          <small>Wuxi Water Network Twin</small>
        </div>
      </div>
      <div class="top-status">
        <span>模型同步</span>
        <strong>ONLINE</strong>
      </div>
    </header>

    <section class="page-title">
      <span>{{ pageTitle }}</span>
      <nav class="top-nav" aria-label="业务模块">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
          {{ item.label }}
        </RouterLink>
      </nav>
      <em>2D 天地图主视图 / Cesium 3D 辅助视图</em>
    </section>

    <RouterView />
  </div>
</template>

<style scoped></style>
