<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRunbooks, createRunbook, executeRunbook, deleteRunbook, listRunbookHistory,
  getRunbookExecution,
  type RunbookView, type RunbookExecuteResult, type RunbookExecutionView
} from '../../api'

const runbooks = ref<RunbookView[]>([])
const loading = ref(false)
const nameFilter = ref('')
const levelFilter = ref<number | ''>('')
const enabledFilter = ref('')

/** 客户端筛选（列表由后端 PageSlice 限制 ≤500 条）。 */
const filteredRunbooks = computed(() => runbooks.value.filter(r =>
  (!nameFilter.value.trim() || r.name.toLowerCase().includes(nameFilter.value.trim().toLowerCase())) &&
  (levelFilter.value === '' || r.safetyLevel === levelFilter.value) &&
  (enabledFilter.value === '' || String(r.enabled) === enabledFilter.value)
))
const dialogVisible = ref(false)
const form = ref({ name: '', description: '', triggerKeyword: '', steps: '', safetyLevel: 1, rollbackSteps: '', enabled: true })

const resultVisible = ref(false)
const executing = ref<Record<number, boolean>>({})
const result = ref<RunbookExecuteResult | null>(null)

const historyVisible = ref(false)
const historyLoading = ref(false)
const history = ref<RunbookExecutionView[]>([])
const historyRunbookName = ref('')

async function load() {
  loading.value = true
  try { runbooks.value = (await listRunbooks()).data }
  catch (e: any) { ElMessage.error('Runbook 加载失败: ' + (e?.response?.data?.message || e?.message || e)) }
  finally { loading.value = false }
}

const emptyForm = () => ({ name: '', description: '', triggerKeyword: '', steps: '', safetyLevel: 1, rollbackSteps: '', enabled: true })

async function create() {
  if (!form.value.name.trim()) { ElMessage.warning('请填写名称'); return }
  try {
    await createRunbook({ ...form.value, steps: form.value.steps.split('\n').filter(s => s.trim()) })
    dialogVisible.value = false
    form.value = emptyForm()
    await load()
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e?.response?.data?.message || e?.message || e))
  }
}

const POLL_INTERVAL_MS = 2000
const POLL_TIMEOUT_MS = 120_000

function executionToResult(exec: RunbookExecutionView, runbookName: string): RunbookExecuteResult {
  return {
    runbook: runbookName,
    status: exec.status,
    executionId: exec.id,
    summary: `${exec.stepResults?.length || 0} 步已执行`,
    steps: (exec.stepResults || []).map(s => ({ step: s.seq, command: s.command, status: s.status, output: s.output }))
  }
}

/** 轮询执行记录至终态（间隔 2s，超时 2min）。 */
async function pollExecution(execId: number, runbookName: string): Promise<RunbookExecuteResult> {
  const deadline = Date.now() + POLL_TIMEOUT_MS
  for (;;) {
    const exec = (await getRunbookExecution(execId)).data
    if (exec.status !== 'RUNNING') return executionToResult(exec, runbookName)
    if (Date.now() > deadline) throw new Error('执行超时（2 分钟），请在执行历史中查看结果')
    await new Promise(r => setTimeout(r, POLL_INTERVAL_MS))
  }
}

async function doExecute(row: RunbookView, token?: string) {
  const r = (await executeRunbook(row.id, token)).data
  if (r.pendingConfirmation) {
    try {
      await ElMessageBox.confirm(
        `「${row.name}」安全等级 ${r.safetyLevel}，属于高危操作，确认执行？`,
        '高危操作确认', { confirmButtonText: '确认执行', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { return }
    await doExecute(row, r.confirmationToken)
    return
  }
  if (r.steps) {
    // 旧后端同步响应（灰度兼容）：直接展示步骤结果
    result.value = r
  } else if (r.executionId != null) {
    // 新后端异步受理：先弹 RUNNING，再轮询至终态
    result.value = { runbook: r.runbook || row.name, status: 'RUNNING', executionId: r.executionId, summary: '执行中…', steps: [] }
    resultVisible.value = true
    result.value = await pollExecution(r.executionId, r.runbook || row.name)
  } else {
    result.value = r
  }
  resultVisible.value = true
}

async function execute(row: RunbookView) {
  executing.value[row.id] = true
  try { await doExecute(row) }
  catch (e: any) { ElMessage.error('执行失败: ' + (e?.response?.data?.message || e.message)) }
  finally { executing.value[row.id] = false }
}

async function showHistory(row: RunbookView) {
  historyRunbookName.value = row.name
  historyVisible.value = true
  historyLoading.value = true
  try { history.value = (await listRunbookHistory(row.id)).data }
  finally { historyLoading.value = false }
}

async function remove(row: RunbookView) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteRunbook(row.id); await load()
}

const statusTag = (s: string) =>
  s === 'SUCCESS' ? 'success' : s === 'FAILED' ? 'danger' : s === 'SKIPPED' ? 'info' : 'warning'

const fmtTime = (t?: string) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-button type="primary" @click="dialogVisible=true">新建 Runbook</el-button>
      <el-input v-model="nameFilter" placeholder="名称包含" clearable style="width:160px" />
      <el-select v-model="levelFilter" placeholder="安全等级" clearable style="width:120px">
        <el-option v-for="l in [1,2,3,4,5]" :key="l" :label="`等级 ${l}`" :value="l" />
      </el-select>
      <el-select v-model="enabledFilter" placeholder="启用状态" clearable style="width:110px">
        <el-option label="启用" value="true" />
        <el-option label="停用" value="false" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="filteredRunbooks" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" width="180" />
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="triggerKeyword" label="触发关键字" width="140" />
      <el-table-column prop="safetyLevel" label="安全等级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.safetyLevel >= 4 ? 'danger' : 'info'" size="small">{{ row.safetyLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }"><el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="240">
        <template #default="{ row }">
          <el-button size="small" type="success" :loading="executing[row.id]" @click="execute(row)">执行</el-button>
          <el-button size="small" @click="showHistory(row)">历史</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
      <el-empty v-if="!loading && runbooks.length === 0" description="暂无 Runbook" />

    <el-dialog v-model="dialogVisible" title="新建 Runbook" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="触发关键字"><el-input v-model="form.triggerKeyword" /></el-form-item>
        <el-form-item label="执行步骤"><el-input v-model="form.steps" type="textarea" rows="4" placeholder="每行一个步骤；HTTP GET/POST url、WEBHOOK POST url、SCRIPT 命令或 LLM 指令" /></el-form-item>
        <el-form-item label="安全等级"><el-input-number v-model="form.safetyLevel" :min="1" :max="5" /></el-form-item>
        <el-form-item label="回滚方案"><el-input v-model="form.rollbackSteps" type="textarea" rows="2" placeholder="每行一个回滚步骤，执行失败时依次执行" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="create">保存</el-button></template>
    </el-dialog>

    <!-- 执行结果 -->
    <el-dialog v-model="resultVisible" :title="`执行结果 - ${result?.runbook || ''}`" width="720px">
      <div v-if="result" style="margin-bottom:12px">
        <el-tag :type="statusTag(result.status || '')" size="large">{{ result.status }}</el-tag>
        <span style="margin-left:12px;color:var(--el-text-color-secondary)">{{ result.summary }}（执行ID: {{ result.executionId }}）</span>
      </div>
      <el-table :data="result?.steps || []" size="small" border max-height="420">
        <el-table-column prop="step" label="#" width="50" />
        <el-table-column prop="command" label="步骤" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="output" label="输出" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- 执行历史 -->
    <el-dialog v-model="historyVisible" :title="`执行历史 - ${historyRunbookName}`" width="820px">
      <el-table :data="history" v-loading="historyLoading" size="small" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.stepResults" size="small" border style="margin:4px 16px">
              <el-table-column prop="seq" label="#" width="50" />
              <el-table-column prop="command" label="步骤" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row: s }"><el-tag :type="statusTag(s.status)" size="small">{{ s.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="output" label="输出" show-overflow-tooltip />
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="执行ID" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="开始时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.finishedAt) }}</template>
        </el-table-column>
        <el-table-column label="步骤数" width="80">
          <template #default="{ row }">{{ row.stepResults?.length || 0 }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyLoading && history.length === 0" description="暂无执行历史" />
      <div v-if="history.length >= 100" style="margin-top:8px;color:var(--el-text-color-secondary);font-size:12px">仅显示最近 100 条执行记录</div>
    </el-dialog>
  </div>
</template>
