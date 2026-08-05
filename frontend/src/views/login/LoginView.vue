<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../../api'
import { setAuth } from '../../utils/auth'

const router = useRouter()
const route = useRoute()
const form = ref({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const r = (await login(form.value.username, form.value.password)).data
    setAuth(r.token, { username: r.username, role: r.role })
    ElMessage.success(`欢迎，${r.username}`)
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/chat')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="height:100vh;display:flex;align-items:center;justify-content:center;background:var(--el-bg-color-page)">
    <el-card style="width:380px" shadow="always">
      <template #header>
        <div style="text-align:center;font-size:20px;font-weight:600">SmartOps 智能运维平台</div>
      </template>
      <el-form :model="form" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="admin" autofocus />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码见部署文档" />
        </el-form-item>
        <el-button type="primary" style="width:100%" :loading="loading" @click="submit">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>
