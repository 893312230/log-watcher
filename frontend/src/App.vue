<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { ref, computed, onMounted } from 'vue'
import { currentUser, clearAuth } from './utils/auth'
const router = useRouter()
const route = useRoute()
const isDark = ref(false)
function toggleDark() { isDark.value = !isDark.value; document.documentElement.classList.toggle("dark", isDark.value); localStorage.setItem("darkMode", String(isDark.value)); }
onMounted(() => { if (localStorage.getItem("darkMode") === "true") { isDark.value = true; document.documentElement.classList.add("dark") } })
const isLoginPage = computed(() => route.path === '/login')
function logout() {
  clearAuth()
  router.push('/login')
}
const navItems = [
  { path: '/chat', title: '智能对话', icon: 'ChatDotRound' },
  { path: '/alerts', title: '告警中心', icon: 'Bell' },
  { path: '/audit', title: '审计日志', icon: 'Document' },
  { path: '/knowledge', title: '知识库', icon: 'Collection' },
  { path: '/servers', title: '应用服务', icon: 'Monitor' },
  { path: '/topology', title: '服务拓扑', icon: 'Share' },
  { path: '/incidents', title: '事件管理', icon: 'Warning' },
  { path: '/runbooks', title: 'Runbook', icon: 'Operation' },
  { path: '/notifications', title: '通知订阅', icon: 'Promotion' },
  { path: '/dashboard', title: '仪表盘', icon: 'Odometer' },
]
</script>

<template>
  <router-view v-if="isLoginPage" />
  <el-container v-else style="height:100vh">
    <el-aside width="200px" style="background:var(--smartops-sidebar-bg);color:#fff">
      <div style="padding:20px 16px;font-size:18px;font-weight:bold;border-bottom:1px solid var(--smartops-sidebar-border)">
        SmartOps
      </div>
      <el-menu
        :default-active="router.currentRoute.value.path"
        background-color="var(--smartops-sidebar-bg)"
        text-color="var(--smartops-sidebar-text)"
        active-text-color="#409eff"
        router
        style="border-right:none"
      >
        <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:var(--el-bg-color, #fff);border-bottom:1px solid var(--el-border-color, #e4e7ed);display:flex;align-items:center;padding:0 20px">
        <h3 style="margin:0">{{ navItems.find(n => n.path === router.currentRoute.value.path)?.title || 'SmartOps' }}</h3>
        <div style="position:absolute;right:20px;display:flex;align-items:center;gap:10px">
          <span v-if="currentUser" style="font-size:13px;color:var(--el-text-color-secondary)">
            {{ currentUser.username }}（{{ currentUser.role }}）
          </span>
          <el-button @click="toggleDark" size="small">{{ isDark ? "☀️" : "🌙" }}</el-button>
          <el-button v-if="currentUser" @click="logout" size="small" type="danger" plain>登出</el-button>
        </div>
      </el-header>
      <el-main style="background:var(--el-bg-color-page, #f5f7fa)">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style>
:root {
  --smartops-sidebar-bg: #1d1e2c;
  --smartops-sidebar-text: #bfcbd9;
  --smartops-sidebar-border: #333;
}
</style>
