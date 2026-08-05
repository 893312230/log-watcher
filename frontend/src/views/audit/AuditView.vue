<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import { listAuditEvents } from '../../api'
import type { AuditEventView } from '../../types'
import { toDisplayPage, toApiPage } from '../../utils/pagination'

const events = ref<AuditEventView[]>([])
const total = ref(0)
const page = ref(0) // 后端 0 起始
// el-pagination 显示 1 起始，经 computed 桥接
const displayPage = computed({
  get: () => toDisplayPage(page.value),
  set: (v: number) => { page.value = toApiPage(v) }
})
const typeFilter = ref('')
const successFilter = ref('')
const actorFilter = ref('')
const traceFilter = ref('')
const timeRange = ref<[string, string] | null>(null)
const loading = ref(false)
const router = useRouter()

function reload() { page.value = 0; load() }

async function load() {
  loading.value = true
  const params: Record<string, string> = { page: String(page.value), size: '20' }
  if (typeFilter.value) params.eventType = typeFilter.value
  if (successFilter.value !== '') params.success = successFilter.value
  if (actorFilter.value.trim()) params.actor = actorFilter.value.trim()
  if (traceFilter.value.trim()) params.traceId = traceFilter.value.trim()
  if (timeRange.value?.[0]) params.from = new Date(timeRange.value[0]).toISOString()
  if (timeRange.value?.[1]) params.to = new Date(timeRange.value[1]).toISOString()
  try { const r = await listAuditEvents(params); events.value = r.data.items; total.value = r.data.total }
  catch (e: any) { ElMessage.error('审计事件加载失败: ' + (e?.response?.data?.message || e?.message || e)) }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap">
      <el-select v-model="typeFilter" placeholder="事件类型" clearable @change="reload" style="width:160px">
        <el-option label="LLM 调用" value="LLM_CALL" />
        <el-option label="工具调用" value="TOOL_CALL" />
        <el-option label="任务执行" value="TASK_EXECUTION" />
        <el-option label="安全决策" value="SECURITY_DECISION" />
      </el-select>
      <el-select v-model="successFilter" placeholder="操作结果" clearable @change="reload" style="width:120px">
        <el-option label="成功" value="true" />
        <el-option label="失败" value="false" />
      </el-select>
      <el-input v-model="actorFilter" placeholder="发起者" clearable style="width:140px"
                @keyup.enter="reload" @clear="reload" />
      <el-input v-model="traceFilter" placeholder="Trace ID" clearable style="width:200px"
                @keyup.enter="reload" @clear="reload" />
      <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
                      start-placeholder="开始时间" end-placeholder="结束时间"
                      @change="reload" style="width:340px" />
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="events" @row-click="(row: any) => router.push(`/audit/${row.id}`)" style="cursor:pointer" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="eventType" label="类型" width="150">
        <template #default="{ row }"><el-tag>{{ row.eventType }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="actor" label="发起者" width="140" />
      <el-table-column label="摘要" min-width="200">
        <template #default="{ row }">
          <el-tooltip :content="row.detail" placement="top" :show-after="500" effect="light">
            <span>{{ (row.detail || '').substring(0, 60) }}{{ (row.detail || '').length > 60 ? '…' : '' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="success" label="结果" width="80">
        <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
      <el-table-column prop="traceId" label="Trace" width="150" show-overflow-tooltip />
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
    </el-table>
    <el-pagination :hide-on-single-page="true" v-model:current-page="displayPage" :page-size="20" :total="total" @current-change="load" layout="prev,next,total" style="margin-top:16px;justify-content:center" />
  </div>
</template>
