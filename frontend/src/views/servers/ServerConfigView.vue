<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listServers, createServer, updateServer, deleteServer } from '../../api'
import type { ServerConfigView } from '../../types'

const servers = ref<ServerConfigView[]>([])
const loading = ref(false)
const keywordFilter = ref('')

/** 客户端筛选：名称或主机地址包含（大小写不敏感）。 */
const filteredServers = computed(() => {
  const kw = keywordFilter.value.trim().toLowerCase()
  if (!kw) return servers.value
  return servers.value.filter(s =>
    (s.name || '').toLowerCase().includes(kw) || (s.host || '').toLowerCase().includes(kw))
})
const dialogVisible = ref(false)
const isEdit = ref(false)
const selected = ref<ServerConfigView | null>(null)
const form = ref({ name: '', host: '', deployPath: '', codeRepo: '', logPath: '', description: '', tags: '' })

async function load() {
  loading.value = true
  try { servers.value = (await listServers()).data }
  catch (e: any) { ElMessage.error('应用服务加载失败: ' + (e?.response?.data?.message || e?.message || e)) }
  finally { loading.value = false }
}

function openCreate() {
  isEdit.value = false; form.value = { name: '', host: '', deployPath: '', codeRepo: '', logPath: '', description: '', tags: '' }
  dialogVisible.value = true
}

function openEdit(s: ServerConfigView) {
  isEdit.value = true; selected.value = s
  form.value = { name: s.name, host: s.host || '', deployPath: s.deployPath || '', codeRepo: s.codeRepo || '', logPath: s.logPath || '', description: s.description || '', tags: (s.tags || []).join(',') }
  dialogVisible.value = true
}

async function save() {
  const body: Record<string, unknown> = { ...form.value, tags: form.value.tags ? form.value.tags.split(',').map(s => s.trim()) : [] }
  if (isEdit.value && selected.value) { await updateServer(selected.value.id, body) }
  else { await createServer(body) }
  dialogVisible.value = false; await load()
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该应用服务？', '删除确认', { type: 'warning' })
  } catch { return }
  await deleteServer(id); await load()
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-button type="primary" @click="openCreate">新增应用服务</el-button>
      <el-input v-model="keywordFilter" placeholder="名称 / 主机包含" clearable style="width:180px" />
      <el-button @click="load">刷新</el-button>
    </div>
    <el-table :data="filteredServers" v-loading="loading" stripe>
      <el-table-column prop="name" label="应用名称" width="140" />
      <el-table-column prop="host" label="主机地址" width="160" show-overflow-tooltip />
      <el-table-column prop="deployPath" label="应用部署路径" show-overflow-tooltip />
      <el-table-column prop="codeRepo" label="代码仓库URL" show-overflow-tooltip />
      <el-table-column prop="logPath" label="日志文件路径" show-overflow-tooltip />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
      <el-empty v-if="!loading && servers.length === 0" description="暂无应用服务" />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑应用服务' : '新增应用服务'" width="600px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="应用名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="主机地址"><el-input v-model="form.host" placeholder="IP:端口" /></el-form-item>
        <el-form-item label="应用部署路径"><el-input v-model="form.deployPath" /></el-form-item>
        <el-form-item label="代码仓库URL"><el-input v-model="form.codeRepo" /></el-form-item>
        <el-form-item label="日志文件路径"><el-input v-model="form.logPath" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </div>
</template>
