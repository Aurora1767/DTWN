<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { label: '数据大屏', to: '/dashboard' },
  { label: '水污染模拟', to: '/simulation' },
  { label: '水环境业务', to: '/warning' },
  { label: '防洪四预业务', to: '/scenario' },
  { label: '水网数据库', to: '/history' },
  { label: '系统管理', to: '/admin' },
]

const pageTitle = computed(() => String(route.meta.title ?? '数据大屏'))
const isCockpitRoute = computed(() => route.name === 'dashboard')
</script>

<template>
  <div class="app-shell" :class="{ 'is-cockpit': isCockpitRoute }">
    <div class="app-header">
      <header class="topbar">
        <div class="brand-block">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 42 42" focusable="false">
              <path class="brand-icon-frame" d="M21 4 34.5 11.8v16.4L21 38 7.5 28.2V11.8L21 4Z" />
              <path class="brand-icon-core" d="M21 11.5 29 16.2v9.6l-8 4.7-8-4.7v-9.6L21 11.5Z" />
              <path class="brand-icon-line" d="M21 4v7.5M21 30.5V38M7.5 11.8 13 16.2M29 25.8l5.5 2.4M13 25.8l-5.5 2.4M29 16.2l5.5-4.4" />
              <circle cx="21" cy="21" r="3.2" />
            </svg>
          </span>
          <div>
            <strong>无锡市某片区数字孪生水网系统</strong>
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
    </div>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped></style>
