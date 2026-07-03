import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { title: '数据大屏' },
    },
    {
      path: '/map',
      name: 'map',
      component: () => import('@/views/MapWorkspaceView.vue'),
      meta: { title: '数据底板' },
    },
    {
      path: '/cesium',
      redirect: '/dashboard',
    },
    {
      path: '/simulation',
      name: 'simulation',
      component: () => import('@/views/SimulationView.vue'),
      meta: { title: '仿真预演' },
    },
    {
      path: '/warning',
      name: 'warning',
      component: () => import('@/views/WarningView.vue'),
      meta: { title: '风险预警' },
    },
    {
      path: '/scenario',
      name: 'scenario',
      component: () => import('@/views/ScenarioView.vue'),
      meta: { title: '防洪四预业务' },
    },
    {
      path: '/history',
      name: 'history',
      component: () => import('@/views/HistoryView.vue'),
      meta: { title: '历史查询' },
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/PlaceholderView.vue'),
      meta: { title: '系统管理' },
    },
  ],
})

export default router
