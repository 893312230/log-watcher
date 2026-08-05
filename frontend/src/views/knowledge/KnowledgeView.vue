<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { listKnowledge, listKnowledgeCategories, getKnowledge, createKnowledge, updateKnowledge, deleteKnowledge } from '../../api'
import type { KnowledgeEntryView } from '../../types'
import { toDisplayPage, toApiPage } from '../../utils/pagination'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from "element-plus"

const md = new MarkdownIt()
const entries = ref<KnowledgeEntryView[]>([])
const total = ref(0)
const page = ref(0) // 后端 0 起始
// el-pagination 显示 1 起始，经 computed 桥接
const displayPage = computed({
  get: () => toDisplayPage(page.value),
  set: (v: number) => { page.value = toApiPage(v) }
})
const keyword = ref('')
const categoryFilter = ref('')
const sourceFilter = ref('')
const categories = ref<string[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const selected = ref<KnowledgeEntryView | null>(null)
const form = ref({
  title: '', errorPattern: '', rootCause: '', suggestion: '',
  actionItems: '', category: '', tags: '', source: 'MANUAL'
})
const detailVisible = ref(false)
const detail = ref<KnowledgeEntryView | null>(null)

async function load() {
  loading.value = true
  const params: Record<string, string> = { page: String(page.value), size: '20' }
  if (keyword.value.trim()) params.keyword = keyword.value.trim()
  if (categoryFilter.value) params.category = categoryFilter.value
  if (sourceFilter.value) params.source = sourceFilter.value
  try {
    const r = await listKnowledge(params)
    entries.value = r.data.items; total.value = r.data.total
  } catch(e) { ElMessage.error("加载失败: " + (e as any).message) } finally { loading.value = false }
}

/** 搜索：切关键字时页码重置。 */
function search() {
  page.value = 0
  load()
}

// 清空关键字立即恢复全量列表
watch(keyword, (v) => { if (!v) { page.value = 0; load() } })

async function view(id: number) {
  try {
    detail.value = (await getKnowledge(id)).data
    detailVisible.value = true
  } catch { ElMessage.error('加载详情失败') }
}

function openCreate() {
  isEdit.value = false; form.value = { title: '', errorPattern: '', rootCause: '', suggestion: '', actionItems: '', category: '', tags: '', source: 'MANUAL' }
  dialogVisible.value = true
}

function openEdit(e: KnowledgeEntryView) {
  isEdit.value = true; selected.value = e
  form.value = {
    title: e.title, errorPattern: e.errorPattern || '', rootCause: e.rootCause || '',
    suggestion: e.suggestion || '', actionItems: (e.actionItems || []).join(','),
    category: e.category || '', tags: (e.tags || []).join(','), source: e.source
  }
  dialogVisible.value = true
}

async function save() {
  const body: Record<string, unknown> = {
    ...form.value,
    actionItems: form.value.actionItems ? form.value.actionItems.split(',').map(s => s.trim()) : [],
    tags: form.value.tags ? form.value.tags.split(',').map(s => s.trim()) : []
  }
  if (isEdit.value && selected.value) { await updateKnowledge(selected.value.id, body) }
  else { await createKnowledge(body) }
  dialogVisible.value = false
  try { categories.value = (await listKnowledgeCategories()).data } catch (_) { /* 忽略 */ }
  await load()
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该知识条目？', '删除确认', { type: 'warning' })
  } catch { return }
  await deleteKnowledge(id); await load()
}

onMounted(async () => {
  try { categories.value = (await listKnowledgeCategories()).data } catch (_) { /* 分类加载失败不阻塞列表 */ }
  await load()
})
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-input v-model="keyword" placeholder="搜索知识库..." @keyup.enter="search" style="width:240px" clearable />
      <el-select v-model="categoryFilter" placeholder="分类" clearable @change="search" style="width:140px">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="sourceFilter" placeholder="来源" clearable @change="search" style="width:130px">
        <el-option label="手动录入" value="MANUAL" />
        <el-option label="logwatch" value="LOGWATCH" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
      <el-button type="success" @click="openCreate">新建条目</el-button>
    </div>
    <el-table :data="entries" v-loading="loading" stripe @row-click="(row: any) => view(row.id)" style="cursor:pointer">
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="source" label="来源" width="100">
        <template #default="{ row }"><el-tag size="small">{{ row.source }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click.stop="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click.stop="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
      <el-empty v-if="!loading && entries.length === 0" description="暂无知识条目" />
    <el-pagination :hide-on-single-page="true" v-model:current-page="displayPage" :page-size="20" :total="total" @current-change="load" layout="prev,next,total" style="margin-top:16px;justify-content:center" />

    <!-- Detail Dialog -->
    <el-dialog v-model="detailVisible" title="知识条目详情" width="800px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="来源"><el-tag size="small">{{ detail.source }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误特征">{{ detail.errorPattern || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <el-tag v-for="t in detail.tags" :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
            <span v-if="!detail.tags?.length" style="color:#c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ new Date(detail.createdAt).toLocaleString('zh-CN') }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ new Date(detail.updatedAt).toLocaleString('zh-CN') }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.rootCause" style="margin-top:16px">
          <h4 style="margin-bottom:8px">🔍 根因分析</h4>
          <div v-html="md.render(detail.rootCause)" style="background:var(--el-color-danger-light-9, #fef0f0);border:1px solid var(--el-color-danger-light-7, #fde2e2);padding:12px;border-radius:6px;line-height:1.7" />
        </div>
        <div v-if="detail.suggestion" style="margin-top:16px">
          <h4 style="margin-bottom:8px">🛠 修复建议</h4>
          <div v-html="md.render(detail.suggestion)" style="background:var(--el-color-success-light-9, #f0f9eb);border:1px solid var(--el-color-success-light-7, #e1f3d8);padding:12px;border-radius:6px;line-height:1.7" />
        </div>
        <div v-if="detail.actionItems?.length" style="margin-top:16px">
          <h4 style="margin-bottom:8px">📋 处置意见</h4>
          <el-timeline>
            <el-timeline-item v-for="(step, i) in detail.actionItems" :key="i" :timestamp="'Step '+(i+1)">
              {{ step }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-dialog>

    <!-- Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑条目' : '新建条目'" width="700px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="错误特征"><el-input v-model="form.errorPattern" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item>
        <el-form-item label="来源"><el-select v-model="form.source"><el-option label="手动录入" value="MANUAL" /><el-option label="logwatch" value="LOGWATCH" /></el-select></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="根因分析"><el-input v-model="form.rootCause" type="textarea" rows="4" /></el-form-item>
        <el-form-item label="修复建议"><el-input v-model="form.suggestion" type="textarea" rows="4" /></el-form-item>
        <el-form-item label="处置意见"><el-input v-model="form.actionItems" type="textarea" rows="3" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
