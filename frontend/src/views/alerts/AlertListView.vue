<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from "element-plus"
import { listAlerts, ackAlert, listServers } from '../../api'
import type { AlertView, ServerConfigView } from '../../types'

const router = useRouter()
const alerts = ref<AlertView[]>([])
const servers = ref<ServerConfigView[]>([])
const total = ref(0)
const currentPage = ref(1)  // Element Plus 从 1 开始
const size = ref(20)
const levelFilter = ref('')
const appFilter = ref('')
const keywordFilter = ref('')
const loading = ref(false)

/** 根据告警 source 匹配服务器配置 */
function findServer(alert: AlertView): ServerConfigView | undefined {
  return servers.value.find(s => {
    const src = alert.source || ''
    return (s.logPath && src.includes(s.logPath)) || (s.name && src.includes(s.name))
  })
}

const serverMap = computed(() => {
  const map = new Map<number, { name: string; host: string }>()
  for (const a of alerts.value) {
    const s = findServer(a)
    if (s) map.set(a.id, { name: s.name, host: s.host || '' })
  }
  return map
})

async function load() {
  loading.value = true
  // 后端 page 从 0 开始，前端 currentPage 从 1 开始
  const params: Record<string, string> = {
    page: String(currentPage.value - 1),
    size: String(size.value)
  }
  if (levelFilter.value) params.level = levelFilter.value
  if (appFilter.value) params.source = appFilter.value
  if (keywordFilter.value.trim()) params.keyword = keywordFilter.value.trim()
  try {
    const r = await listAlerts(params)
    alerts.value = r.data.items
    total.value = r.data.total
  } catch(e) { ElMessage.error("告警加载失败") } finally {
    loading.value = false
  }
}

const acking = ref<Record<number, boolean>>({})

async function ack(id: number) {
  acking.value[id] = true
  try {
    await ackAlert(id)
    await load()
  } catch (e: any) {
    ElMessage.error('确认失败: ' + (e?.response?.data?.message || e?.message || e))
  } finally {
    acking.value[id] = false
  }
}

onMounted(async () => {
  try { servers.value = (await listServers()).data } catch (_) { /* 无服务器配置也不影响 */ }
  await load()
})
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-select v-model="appFilter" placeholder="应用" clearable @change="currentPage=1;load()" style="width:170px">
        <el-option v-for="s in servers" :key="s.id" :label="s.name" :value="s.logPath || s.name" />
      </el-select>
      <el-select v-model="levelFilter" placeholder="告警级别" clearable @change="currentPage=1;load()" style="width:140px">
        <el-option label="ERROR" value="ERROR" /><el-option label="WARN" value="WARN" /><el-option label="INFO" value="INFO" />
      </el-select>
      <el-input v-model="keywordFilter" placeholder="关键字（包含匹配）" clearable style="width:180px"
                @keyup.enter="currentPage=1;load()" @clear="currentPage=1;load()" />
      <el-button type="primary" @click="currentPage=1;load()">查询</el-button>
      <el-button @click="load">刷新</el-button>
      <el-input v-model="size" placeholder="每页" style="width:100px" @change="currentPage=1;load()" type="number" />
    </div>
    <el-table :data="alerts" v-loading="loading" stripe @row-click="(row: AlertView) => router.push(`/alerts/${row.id}`)" style="cursor:pointer">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="level" label="级别" width="90">
        <template #default="{ row }"><el-tag :type="row.level === 'ERROR' ? 'danger' : row.level === 'WARN' ? 'warning' : 'info'" size="small">{{ row.level }}</el-tag></template>
      </el-table-column>
      <!-- 服务器信息 -->
      <el-table-column label="应用" width="130">
        <template #default="{ row }">
          <template v-if="serverMap.get(row.id)">
            <div style="font-weight:600;font-size:13px">{{ serverMap.get(row.id)!.name }}</div>
            <div style="font-size:11px;color:var(--el-text-color-secondary, #909399)">{{ serverMap.get(row.id)!.host }}</div>
          </template>
          <span v-else style="color:#c0c4cc;font-size:12px">未关联</span>
        </template>
      </el-table-column>
      <el-table-column prop="source" label="日志来源" width="180" show-overflow-tooltip />
      <el-table-column prop="message" label="错误摘要" show-overflow-tooltip min-width="200" />
      <el-table-column prop="keyword" label="关键字" width="100" />
      <el-table-column prop="layerReached" label="分析层" width="70">
        <template #default="{ row }">
          <el-tag size="small" :type="row.layerReached >= 3 ? 'success' : row.layerReached >= 2 ? 'warning' : ''">L{{ row.layerReached }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="occurrence" label="次数" width="55" />
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString('zh-CN', { month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit' }) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }"><el-tag :type="row.status === 'OPEN' ? 'danger' : 'success'" size="small">{{ row.status === 'OPEN' ? '待处理' : row.status === 'ACKED' ? '已确认' : '已解决' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'OPEN'" size="small" type="primary" :loading="acking[row.id]" @click.stop="ack(row.id)">确认</el-button>
        </template>
      </el-table-column>
    </el-table>
      <el-empty v-if="!loading && alerts.length === 0" description="暂无告警数据" />
    <el-pagination :hide-on-single-page="true"
      v-model:current-page="currentPage"
      :page-size="size"
      :total="total"
      @current-change="load"
      layout="total, prev, pager, next, jumper"
      style="margin-top:16px;justify-content:center"
    />
  </div>
</template>
