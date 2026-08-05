import { createRouter, createWebHistory } from 'vue-router'
import { onUnauthorized } from '../api'
import { hasJwt, isLoggedOut } from '../utils/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/chat' },
    { path: '/login', name: 'Login', component: () => import('../views/login/LoginView.vue'), meta: { public: true } },
    { path: '/chat', name: 'Chat', component: () => import('../views/chat/ChatView.vue') },
    { path: '/alerts', name: 'Alerts', component: () => import('../views/alerts/AlertListView.vue') },
    { path: '/alerts/:id', name: 'AlertDetail', component: () => import('../views/alerts/AlertDetailView.vue') },
    { path: '/audit', name: 'Audit', component: () => import('../views/audit/AuditView.vue') },
    { path: '/audit/:id', name: 'AuditDetail', component: () => import('../views/audit/AuditDetailView.vue') },
    { path: '/knowledge', name: 'Knowledge', component: () => import('../views/knowledge/KnowledgeView.vue') },
    { path: '/servers', name: 'Servers', component: () => import('../views/servers/ServerConfigView.vue') },
    { path: '/topology', name: 'Topology', component: () => import('../views/topology/TopologyView.vue') },
    { path: '/incidents', name: 'Incidents', component: () => import('../views/incidents/IncidentView.vue') },
    { path: '/runbooks', name: 'Runbooks', component: () => import('../views/runbooks/RunbookView.vue') },
    { path: '/notifications', name: 'Notifications', component: () => import('../views/notifications/NotificationView.vue') },
    { path: '/dashboard', name: 'Dashboard', component: () => import('../views/dashboard/DashboardView.vue') }
  ]
})

// 登录守卫：未登录访问受保护页面时跳 /login（已通过静态 Token 部署的环境不受影响——
// 无 JWT 时后端静态 Token 兜底仍可调通，此处守卫仅在用户显式登出后生效的场景兜底）。
// 保留 redirect query，登录成功后回跳原路由。
router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!hasJwt() && isLoggedOut()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

// 401 登出闭环：api 拦截器登出后回调此处跳转登录页，保留原路由
onUnauthorized(() => {
  const cur = router.currentRoute.value
  router.push({ path: '/login', query: cur.meta.public ? {} : { redirect: cur.fullPath } })
})

export default router
