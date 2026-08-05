<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listWebhookSubscriptions, createWebhookSubscription, updateWebhookSubscription,
  deleteWebhookSubscription, listWebhookDeliveries,
  type WebhookSubscriptionView, type WebhookDeliveryView
} from '../../api'

const EVENT_TYPE_OPTIONS = [
  { value: 'ALERT_CREATED', label: '告警创建' },
  { value: 'ALERT_ACKED', label: '告警确认' },
  { value: 'RUNBOOK_FAILED', label: 'Runbook 失败' },
  { value: 'RUNBOOK_COMPLETED', label: 'Runbook 成功' },
  { value: 'INCIDENT_POSTMORTEM', label: '复盘报告' },
]

const subscriptions = ref<WebhookSubscriptionView[]>([])
const loading = ref(false)
const nameFilter = ref('')
const eventFilter = ref('')
const enabledFilter = ref('')

/** 客户端筛选（列表由后端 PageSlice 限制 ≤500 条）。 */
const filteredSubscriptions = computed(() => subscriptions.value.filter(s =>
  (!nameFilter.value.trim() || s.name.toLowerCase().includes(nameFilter.value.trim().toLowerCase())) &&
  (!eventFilter.value || (s.eventTypes || '').split(',').map(t => t.trim()).includes(eventFilter.value)) &&
  (enabledFilter.value === '' || String(s.enabled) === enabledFilter.value)
))
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingHasSecret = ref(false)
const form = ref({ name: '', url: '', eventTypes: [] as string[], secret: '', enabled: true, retryCount: 3 })

const deliveriesVisible = ref(false)
const deliveriesLoading = ref(false)
const deliveries = ref<WebhookDeliveryView[]>([])
const deliveriesTitle = ref('')

async function load() {
  loading.value = true
  try { subscriptions.value = (await listWebhookSubscriptions()).data }
  finally { loading.value = false }
}

function openCreate() {
  editingId.value = null
  editingHasSecret.value = false
  form.value = { name: '', url: '', eventTypes: [], secret: '', enabled: true, retryCount: 3 }
  dialogVisible.value = true
}

function openEdit(row: WebhookSubscriptionView) {
  editingId.value = row.id
  form.value = {
    name: row.name, url: row.url,
    eventTypes: row.eventTypes ? row.eventTypes.split(',').map(s => s.trim()).filter(Boolean) : [],
    secret: '', enabled: row.enabled, retryCount: row.retryCount
  }
  editingHasSecret.value = row.hasSecret
  dialogVisible.value = true
}

async function save() {
  const body = { ...form.value, secret: form.value.secret || undefined }
  try {
    if (editingId.value == null) {
      await createWebhookSubscription(body)
    } else {
      await updateWebhookSubscription(editingId.value, body)
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e?.response?.data?.message || e.message))
  }
}

async function remove(row: WebhookSubscriptionView) {
  await ElMessageBox.confirm(`确认删除订阅「${row.name}」？`, '删除确认', { type: 'warning' })
  await deleteWebhookSubscription(row.id); await load()
}

async function showDeliveries(row?: WebhookSubscriptionView) {
  deliveriesTitle.value = row ? `投递日志 - ${row.name}` : '全部投递日志'
  deliveriesVisible.value = true
  deliveriesLoading.value = true
  try { deliveries.value = (await listWebhookDeliveries(row?.id)).data }
  finally { deliveriesLoading.value = false }
}

const fmtTime = (t?: string) => t ? new Date(t).toLocaleString('zh-CN') : '-'

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-button type="primary" @click="openCreate">新建订阅</el-button>
      <el-input v-model="nameFilter" placeholder="名称包含" clearable style="width:150px" />
      <el-select v-model="eventFilter" placeholder="事件类型" clearable style="width:170px">
        <el-option v-for="o in EVENT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select v-model="enabledFilter" placeholder="启用状态" clearable style="width:110px">
        <el-option label="启用" value="true" />
        <el-option label="停用" value="false" />
      </el-select>
      <el-button @click="showDeliveries()">全部投递日志</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="filteredSubscriptions" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="url" label="投递地址" show-overflow-tooltip />
      <el-table-column label="事件类型" width="220">
        <template #default="{ row }">
          <el-tag v-for="t in row.eventTypes.split(',').filter(Boolean)" :key="t"
                  size="small" style="margin-right:4px">{{ t.trim() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签名" width="70">
        <template #default="{ row }">
          <el-tag :type="row.hasSecret ? 'success' : 'info'" size="small">{{ row.hasSecret ? 'HMAC' : '无' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="重试" width="60" />
      <el-table-column label="启用" width="70">
        <template #default="{ row }"><el-tag :type="row.enabled?'success':'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="showDeliveries(row)">日志</el-button>
          <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && subscriptions.length === 0" description="暂无 Webhook 订阅" />

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建订阅' : '编辑订阅'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="投递地址"><el-input v-model="form.url" placeholder="https://example.com/hook（禁止内网地址）" /></el-form-item>
        <el-form-item label="事件类型">
          <el-select v-model="form.eventTypes" multiple style="width:100%">
            <el-option v-for="o in EVENT_TYPE_OPTIONS" :key="o.value" :label="`${o.label} (${o.value})`" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="签名密钥"><el-input v-model="form.secret" :placeholder="editingHasSecret ? '已设置，留空保持不变' : '留空则不签名；配置后附加 X-SmartOps-Signature 头'" show-password /></el-form-item>
        <el-form-item label="重试次数"><el-input-number v-model="form.retryCount" :min="0" :max="5" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="deliveriesVisible" :title="deliveriesTitle" width="860px">
      <el-table :data="deliveries" v-loading="deliveriesLoading" size="small" border max-height="480">
        <el-table-column type="expand">
          <template #default="{ row }">
            <pre style="margin:4px 16px;padding:12px;background:var(--el-bg-color-page);border-radius:6px;font-size:12px;white-space:pre-wrap;word-break:break-all">{{ row.payload }}</pre>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="eventType" label="事件" width="170" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="状态码" width="80">
          <template #default="{ row }">{{ row.statusCode ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="attempt" label="第几次" width="70" />
        <el-table-column label="投递时间" width="180">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!deliveriesLoading && deliveries.length === 0" description="暂无投递记录" />
      <div v-if="deliveries.length >= 100" style="margin-top:8px;color:var(--el-text-color-secondary);font-size:12px">仅显示最近 100 条投递记录</div>
    </el-dialog>
  </div>
</template>
