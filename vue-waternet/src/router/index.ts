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
      path: '/cesium',
      redirect: '/dashboard',
    },
    {
      path: '/simulation',
      name: 'simulation',
      component: () => import('@/views/PlaceholderView.vue'),
      meta: { title: '水污染模拟' },
    },
    {
      path: '/warning',
      name: 'warning',
      component: () => import('@/views/WarningView.vue'),
      meta: { title: '水环境业务' },
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
      meta: { title: '水网数据库' },
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
