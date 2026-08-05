<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { getTopology, addTopologyNode as apiAddNode, type TopologyNode, type TopologyEdge } from '../../api'

const nodes = ref<TopologyNode[]>([])
const edges = ref<TopologyEdge[]>([])
const loading = ref(false)
const chartEl = ref<HTMLElement>()
const dialogVisible = ref(false)
const form = ref({ name: '', type: 'SERVICE', host: '', status: 'UP' })
let chart: echarts.ECharts | null = null
const handleResize = () => chart?.resize()

async function load() {
  loading.value = true
  try {
    const r = (await getTopology()).data
    nodes.value = r.nodes || []; edges.value = r.edges || []
    renderChart()
  } finally { loading.value = false }
}

function renderChart() {
  if (!chartEl.value) return
  // 复用实例：重复 init 会泄漏旧实例；notMerge=true 全量替换配置
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { formatter: (p: any) => p.data?.name || p.name },
    series: [{
      type: 'graph', layout: 'force', roam: true,
      symbolSize: 40, label: { show: true, fontSize: 12 },
      force: { repulsion: 200, edgeLength: 150 },
      data: nodes.value.map(n => ({
        id: n.id, name: n.name, category: n.type,
        symbolSize: 40,
        itemStyle: { color: n.status === 'UP' ? '#67c23a' : n.status === 'DOWN' ? '#f56c6c' : '#909399' },
        tooltip: { formatter: `${n.name}\n${n.host || ''}\n${n.status}` }
      })),
      categories: [{ name: 'SERVICE' }, { name: 'DATABASE' }, { name: 'QUEUE' }, { name: 'EXTERNAL' }],
      links: edges.value.map(e => ({ source: String(e.sourceId), target: String(e.targetId), label: { show: true, formatter: e.type } }))
    }]
  }, true)
}

async function addNode() {
  if (!form.value.name.trim()) { ElMessage.warning('请填写节点名称'); return }
  await apiAddNode(form.value)
  dialogVisible.value = false
  form.value = { name: '', type: 'SERVICE', host: '', status: 'UP' }
  await load()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  load()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div v-loading="loading">
    <div style="display:flex;gap:8px;margin-bottom:16px">
      <el-button type="primary" @click="dialogVisible = true">添加节点</el-button>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :span="18">
        <div ref="chartEl" style="width:100%;height:500px;background:var(--el-bg-color, #fff);border-radius:8px;border:1px solid var(--el-border-color, #e4e7ed)" />
      </el-col>
      <el-col :span="6">
        <el-card header="节点列表" style="max-height:500px;overflow-y:auto">
          <div v-for="n in nodes" :key="n.id" style="display:flex;justify-content:space-between;align-items:center;padding:8px;border-bottom:1px solid #f0f0f0">
            <div>
              <div style="font-weight:600">{{ n.name }}</div>
              <div style="font-size:12px;color:var(--el-text-color-secondary, #909399)">{{ n.type }} · {{ n.host }}</div>
            </div>
            <el-tag :type="n.status==='UP'?'success':'danger'" size="small">{{ n.status }}</el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogVisible" title="添加节点" width="400px">
      <el-form :model="form" label-width="70px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.type"><el-option v-for="t in ['SERVICE','DATABASE','QUEUE','EXTERNAL']" :key="t" :label="t" :value="t" /></el-select></el-form-item>
        <el-form-item label="主机"><el-input v-model="form.host" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="UP" value="UP" /><el-option label="DOWN" value="DOWN" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="addNode">保存</el-button></template>
    </el-dialog>
  </div>
</template>
