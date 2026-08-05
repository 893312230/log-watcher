<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listIncidents, triggerPostmortem, type IncidentView } from '../../api'

const incidents = ref<IncidentView[]>([])
const loading = ref(false)
const postmortemLoading = ref<Record<number, boolean>>({})
const sourceFilter = ref('')
const levelFilter = ref('')

/** 客户端筛选（事件分组上限 100 条）。 */
const filteredIncidents = computed(() => incidents.value.filter(i =>
  (!sourceFilter.value.trim() || (i.source || '').toLowerCase().includes(sourceFilter.value.trim().toLowerCase())) &&
  (!levelFilter.value || i.level === levelFilter.value)
))

async function load() {
  loading.value = true
  try { incidents.value = (await listIncidents()).data }
  catch (e: any) { ElMessage.error('事件列表加载失败: ' + (e?.response?.data?.message || e?.message || e)) }
  finally { loading.value = false }
}

async function postmortem(id: number) {
  postmortemLoading.value[id] = true
  try {
    await triggerPostmortem(id)
    ElMessage.success('复盘报告已生成，请查看知识库')
  } catch (e: any) {
    ElMessage.error('复盘生成失败: ' + (e?.response?.data?.message || e?.message || e))
  } finally {
    postmortemLoading.value[id] = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-input v-model="sourceFilter" placeholder="来源包含" clearable style="width:180px" />
      <el-select v-model="levelFilter" placeholder="级别" clearable style="width:120px">
        <el-option label="ERROR" value="ERROR" /><el-option label="WARN" value="WARN" /><el-option label="INFO" value="INFO" />
      </el-select>
      <el-button type="primary" @click="load">刷新</el-button>
    </div>
    <el-table :data="filteredIncidents" v-loading="loading" stripe>
      <el-table-column prop="id" label="事件ID" width="100" />
      <el-table-column prop="source" label="来源" show-overflow-tooltip />
      <el-table-column prop="alertCount" label="告警数" width="80" />
      <el-table-column prop="level" label="级别" width="100">
        <template #default="{ row }"><el-tag :type="row.level==='ERROR'?'danger':'warning'" size="small">{{ row.level }}</el-tag></template>
      </el-table-column>
      <el-table-column label="首次" width="160">
        <template #default="{ row }">{{ new Date(row.firstAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="success" :loading="postmortemLoading[row.id]" @click="postmortem(row.id)">生成复盘</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
