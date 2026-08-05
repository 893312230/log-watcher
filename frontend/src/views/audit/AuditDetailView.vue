<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listAuditEvents, getAuditEvent } from '../../api'

const route = useRoute()
const router = useRouter()
const event = ref<any>(null)
const traceChain = ref<any[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    event.value = (await getAuditEvent(Number(route.params.id))).data

    if (event.value?.traceId) {
      const chainData = (await listAuditEvents({ traceId: event.value.traceId, size: '50' })).data
      traceChain.value = chainData.items || []
    }
  } finally { loading.value = false }
}

const typeLabel = (t: string) => {
  const map: Record<string, string> = {
    LLM_CALL: 'LLM 调用', TOOL_CALL: '工具调用',
    TASK_EXECUTION: '任务执行', SECURITY_DECISION: '安全决策'
  }
  return map[t] || t
}

const typeTag = (t: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger'> = {
    LLM_CALL: 'primary', TOOL_CALL: 'success',
    TASK_EXECUTION: 'warning', SECURITY_DECISION: 'danger'
  }
  return map[t] || 'info'
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-page-header @back="router.push('/audit')" :content="'事件 #' + route.params.id" style="margin-bottom:16px" />

    <!-- Event Detail Card -->
    <el-card v-if="event" shadow="hover" style="margin-bottom:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-size:16px;font-weight:600">
            <el-tag :type="typeTag(event.eventType)" effect="dark" size="large" style="margin-right:8px">
              {{ typeLabel(event.eventType) }}
            </el-tag>
            {{ event.actor }}
          </span>
          <el-tag :type="event.success ? 'success' : 'danger'" size="large">
            {{ event.success ? '成功' : '失败' }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="事件ID">{{ event.id }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">
          <el-tag :type="typeTag(event.eventType)" effect="light">{{ typeLabel(event.eventType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作者">{{ event.actor }}</el-descriptions-item>
        <el-descriptions-item label="操作目标">{{ event.target || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联会话">{{ event.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ event.latencyMs }}ms</el-descriptions-item>
        <el-descriptions-item label="执行结果">
          <el-tag :type="event.success ? 'success' : 'danger'">{{ event.success ? '成功' : '失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发生时间">
          {{ new Date(event.createdAt).toLocaleString('zh-CN', { year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit' }) }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="event.detail" style="margin-top:16px">
        <h4>操作详情</h4>
        <pre style="background:var(--el-bg-color-page, #f5f7fa);padding:16px;border-radius:6px;max-height:400px;overflow-y:auto;font-size:13px;line-height:1.6;white-space:pre-wrap;word-break:break-all">{{ event.detail }}</pre>
      </div>
    </el-card>

    <!-- Trace Chain -->
    <el-card v-if="traceChain.length > 1" shadow="hover">
      <template #header><span style="font-weight:600">🔗 调用链路追踪 (Trace: {{ event?.traceId }})</span></template>
      <el-timeline>
        <el-timeline-item
          v-for="e in [...traceChain].reverse()"
          :key="e.id"
          :timestamp="new Date(e.createdAt).toLocaleTimeString('zh-CN', { hour:'2-digit',minute:'2-digit',second:'2-digit' })"
          :color="e.id === event?.id ? 'var(--el-color-primary)' : e.success ? 'var(--el-color-success)' : 'var(--el-color-danger)'"
          :type="e.id === event?.id ? 'primary' : undefined"
        >
          <div style="display:flex;align-items:center;gap:8px">
            <el-tag :type="typeTag(e.eventType)" effect="dark" size="small">{{ typeLabel(e.eventType) }}</el-tag>
            <span style="font-weight:600">{{ e.actor }}</span>
            <el-tag :type="e.success ? 'success' : 'danger'" size="small">{{ e.success ? '成功' : '失败' }}</el-tag>
            <span style="color:var(--el-text-color-secondary, #909399);font-size:12px">{{ e.latencyMs }}ms</span>
          </div>
          <div v-if="e.detail" style="color:var(--el-text-color-regular, #606266);margin-top:4px;font-size:13px">
            {{ e.detail?.substring(0, 120) }}{{ (e.detail?.length || 0) > 120 ? '...' : '' }}
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>
