<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import { health, listAlerts, listKnowledge, listNotificationChannels, getDailyAlertStats, type DailyAlertStat } from '../../api'
import { readLayout, persistLayout, type DashboardLayoutItem } from '../../utils/dashboardLayout'

/** 卡片注册表：id → 标题与栅格宽度（24 栅格）。 */
const CARD_REGISTRY: { id: string; title: string; span: number }[] = [
  { id: 'status', title: '系统状态', span: 6 },
  { id: 'alertTotal', title: '告警总数', span: 6 },
  { id: 'knowledgeTotal', title: '知识库条目', span: 6 },
  { id: 'channels', title: '通知渠道', span: 6 },
  { id: 'trend', title: '近 7 天告警趋势', span: 16 },
  { id: 'nav', title: '快速导航', span: 8 },
]
const REGISTRY_IDS = CARD_REGISTRY.map(c => c.id)
const spanOf = (id: string) => CARD_REGISTRY.find(c => c.id === id)?.span ?? 6

const layout = ref<DashboardLayoutItem[]>(readLayout(REGISTRY_IDS))
const customizeVisible = ref(false)

/** 拖拽排序的可见卡片（set 时隐藏卡片保持在原相对位置之后，顺序持久化）。 */
const visibleCards = computed<string[]>({
  get: () => layout.value.filter(i => i.visible).map(i => i.id),
  set: (ordered: string[]) => {
    layout.value = [
      ...ordered.map(id => ({ id, visible: true })),
      ...layout.value.filter(i => !i.visible)
    ]
    persistLayout(layout.value)
  }
})

function toggleCard(id: string, visible: boolean) {
  const item = layout.value.find(i => i.id === id)
  if (item) item.visible = visible
  persistLayout(layout.value)
  if (visible) nextTick(() => renderChart())
}

function resetLayout() {
  layout.value = REGISTRY_IDS.map(id => ({ id, visible: true }))
  persistLayout(layout.value)
  nextTick(() => renderChart())
}

const status = ref('loading')
const alertTotal = ref(0)
const knowledgeTotal = ref(0)
const notificationCount = ref(0)
const dailyStats = ref<DailyAlertStat[]>([])
const chartEl = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
let timer: ReturnType<typeof setInterval> | null = null
const handleResize = () => chart?.resize()

async function load() {
  const [h, a, k, n, s] = await Promise.allSettled([
    health(), listAlerts({ size: '1' }), listKnowledge({ size: '1' }),
    listNotificationChannels(), getDailyAlertStats(7)
  ])
  status.value = h.status === 'fulfilled' ? (h.value.data?.status || 'UP') : 'DOWN'
  if (a.status === 'fulfilled') alertTotal.value = a.value.data.total || 0
  if (k.status === 'fulfilled') knowledgeTotal.value = k.value.data.total || 0
  if (n.status === 'fulfilled') notificationCount.value = n.value.data.length
  if (s.status === 'fulfilled') dailyStats.value = s.value.data
  const failed = [h, a, k, n, s].filter(r => r.status === 'rejected').length
  if (failed > 0) ElMessage.warning(`${failed} 项仪表盘数据加载失败，下次轮询自动重试`)
  await nextTick()
  renderChart()
}

function renderChart() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dailyStats.value.map(d => d.date.substring(5)) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      name: '告警数', type: 'bar',
      data: dailyStats.value.map(d => d.count),
      itemStyle: { color: '#f56c6c', borderRadius: [4, 4, 0, 0] }
    }]
  }, true)
}

onMounted(() => {
  load()
  // 页面不可见时暂停轮询，回到前台立即刷新
  timer = setInterval(() => { if (!document.hidden) load() }, 15000)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div>
    <div style="display:flex;justify-content:flex-end;margin-bottom:12px">
      <el-button size="small" @click="customizeVisible = true">自定义</el-button>
    </div>
    <draggable
      v-model="visibleCards"
      item-key="id"
      handle=".drag-handle"
      :animation="150"
      style="display:flex;flex-wrap:wrap;gap:16px"
    >
      <template #item="{ element: id }">
        <div :style="{ width: `calc(${spanOf(id) / 24 * 100}% - ${16 * (24 - spanOf(id)) / 24}px)` }">
          <el-card shadow="hover" style="height:100%">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span style="font-size:14px;color:var(--el-text-color-secondary)">
                  {{ CARD_REGISTRY.find(c => c.id === id)?.title }}
                </span>
                <span class="drag-handle" style="cursor:move;color:var(--el-text-color-placeholder)">⠿</span>
              </div>
            </template>

            <template v-if="id === 'status'">
              <el-statistic :value="status" :value-style="{ color: status === 'UP' ? '#67c23a' : '#f56c6c', fontSize: '28px' }" />
            </template>
            <template v-else-if="id === 'alertTotal'">
              <el-statistic :value="alertTotal" :value-style="{ fontSize: '28px' }" />
            </template>
            <template v-else-if="id === 'knowledgeTotal'">
              <el-statistic :value="knowledgeTotal" :value-style="{ fontSize: '28px' }" />
            </template>
            <template v-else-if="id === 'channels'">
              <el-statistic :value="notificationCount" :value-style="{ fontSize: '28px' }" />
            </template>
            <template v-else-if="id === 'trend'">
              <div ref="chartEl" style="height:280px" />
            </template>
            <template v-else-if="id === 'nav'">
              <el-menu router>
                <el-menu-item index="/chat">💬 智能对话</el-menu-item>
                <el-menu-item index="/alerts">🔔 告警中心</el-menu-item>
                <el-menu-item index="/audit">📋 审计日志</el-menu-item>
                <el-menu-item index="/knowledge">📚 知识库</el-menu-item>
                <el-menu-item index="/servers">🖥 应用服务</el-menu-item>
                <el-menu-item index="/topology">🔗 服务拓扑</el-menu-item>
                <el-menu-item index="/runbooks">📖 Runbook</el-menu-item>
              </el-menu>
            </template>
          </el-card>
        </div>
      </template>
    </draggable>

    <el-drawer v-model="customizeVisible" title="自定义仪表盘" size="300px">
      <p style="margin-top:0;color:var(--el-text-color-secondary);font-size:13px">
        勾选要显示的卡片；在卡片右上角拖拽 ⠿ 调整顺序。
      </p>
      <el-checkbox
        v-for="c in CARD_REGISTRY" :key="c.id"
        :model-value="layout.find(i => i.id === c.id)?.visible !== false"
        @change="(v: boolean) => toggleCard(c.id, v)"
        style="display:flex;margin-bottom:8px"
      >{{ c.title }}</el-checkbox>
      <el-button size="small" style="margin-top:12px" @click="resetLayout">恢复默认布局</el-button>
    </el-drawer>
  </div>
</template>
