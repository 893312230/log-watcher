<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAlert, ackAlert, alertToKnowledge, listServers } from '../../api'
import type { AlertView, ServerConfigView } from '../../types'
import { escapeHtml } from '../../utils/escape'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ breaks: true })
const route = useRoute()
const router = useRouter()
const alert = ref<AlertView | null>(null)
const server = ref<ServerConfigView | null>(null)
const loading = ref(false)

function parseSection(text: string, marker: string): string {
  // Extract content under a Chinese section header like 【原因分析】 or 【解决建议】
  const idx = text.indexOf(marker)
  if (idx < 0) return ''
  const remaining = text.substring(idx + marker.length)
  const nextMarker = remaining.search(/【[^】]+】/)
  return nextMarker > 0 ? remaining.substring(0, nextMarker).trim() : remaining.trim()
}

/** 将 Java 堆栈帧转为可点击的 GitHub 代码链接（先整体转义防 XSS，再注入 <a>） */
const linkedStackTrace = computed(() => {
  const trace = alert.value?.stackTrace || ''
  if (!trace) return ''
  const escaped = escapeHtml(trace)
  if (!server.value?.codeRepo) return escaped
  const repo = server.value.codeRepo.replace('https://github.com/', '')
  // at com.example.Class.method(File.java:123)
  return escaped.replace(
    /at\s+([\w.$]+)\(([\w.$]+):(\d+)\)/g,
    (_, cls: string, file: string, line: string) => {
      const url = `https://github.com/${repo}/blob/main/${cls.replace(/\./g, '/')}.java#L${line}`
      return `at <a href="${url}" target="_blank" rel="noopener" style="color:#409eff">${cls}</a>(${file}:${line})`
    }
  )
})

const rootCause = computed(() => {
  if (!alert.value?.analysis) return ''
  return parseSection(alert.value.analysis, '【原因分析】') || parseSection(alert.value.analysis, '根因') || alert.value.analysis
})

const fixSuggestion = computed(() => {
  const a = alert.value
  if (!a) return ''
  return parseSection(a.analysis || '', '【解决建议】') || parseSection(a.analysis || '', '【修复建议】') || a.suggestion || ''
})

const actionItems = computed(() => {
  const actions = parseSection(alert.value?.analysis || '', '【处置意见】')
  if (actions) return actions.split('\n').filter(l => l.trim())
  // fallback: parse numbered steps from suggestion
  const s = fixSuggestion.value
  return s ? s.split('\n').filter(l => /^\d+[\.\)、]/.test(l.trim()) || l.startsWith('- ')) : []
})

async function load() {
  loading.value = true
  try {
    const [a, servers] = await Promise.all([
      getAlert(Number(route.params.id)),
      listServers()
    ])
    alert.value = a.data
    // Match server by source path
    const src = a.data.source || ''
    server.value = servers.data.find(s => src.includes(s.logPath || '') || src.includes(s.name || '')) || null
  } finally { loading.value = false }
}

const acking = ref(false)
const converting = ref(false)

async function ack() {
  if (!alert.value || acking.value) return
  acking.value = true
  try { await ackAlert(alert.value.id); await load() }
  catch (e: any) { ElMessage.error('确认失败: ' + (e?.response?.data?.message || e?.message || e)) }
  finally { acking.value = false }
}
async function toKnowledge() {
  if (!alert.value || converting.value) return
  converting.value = true
  try {
    await alertToKnowledge(alert.value.id)
    router.push(`/knowledge`)
  } catch (e: any) { ElMessage.error('转知识库失败: ' + (e?.response?.data?.message || e?.message || e)) }
  finally { converting.value = false }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-page-header @back="router.push('/alerts')" :content="'告警详情'" style="margin-bottom:16px" />

    <template v-if="alert">
      <!-- Incident Overview -->
      <el-card style="margin-bottom:16px" shadow="hover">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span style="font-size:16px;font-weight:600">
              <el-tag :type="alert.level==='ERROR'?'danger':alert.level==='WARN'?'warning':'info'" size="large" style="margin-right:8px">{{ alert.level }}</el-tag>
              {{ alert.message }}
            </span>
            <el-tag :type="alert.status==='OPEN'?'danger':'success'" size="large">{{ alert.status === 'OPEN' ? '未处理' : alert.status === 'ACKED' ? '已确认' : '已解决' }}</el-tag>
          </div>
        </template>
        <el-row :gutter="20">
          <!-- Server Info -->
          <el-col :span="8" v-if="server">
            <div style="background:var(--el-color-success-light-9, #f0f9eb);padding:12px;border-radius:6px;border:1px solid #b7eb8f">
              <div style="font-size:13px;color:var(--el-text-color-secondary, #909399);margin-bottom:4px">关联服务器</div>
              <div style="font-weight:600;font-size:15px">{{ server.name }}</div>
              <div style="font-size:13px;color:var(--el-text-color-regular, #606266);margin-top:2px">
                <div v-if="server.host">🖥 {{ server.host }}</div>
                <div v-if="server.deployPath">📦 {{ server.deployPath }}</div>
                <div v-if="server.codeRepo">📝 {{ server.codeRepo }}</div>
                <div v-if="server.logPath">📋 {{ server.logPath }}</div>
              </div>
            </div>
          </el-col>
          <!-- Error Meta -->
          <el-col :span="server ? 16 : 24">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="日志来源">{{ alert.source }}</el-descriptions-item>
              <el-descriptions-item label="发生时间">{{ new Date(alert.createdAt).toLocaleString('zh-CN', { year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit' }) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ new Date(alert.updatedAt).toLocaleString('zh-CN', { year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit' }) }}</el-descriptions-item>
              <el-descriptions-item label="事件指纹">{{ alert.fingerprint?.substring(0,16) }}...</el-descriptions-item>
              <el-descriptions-item label="命中关键字">{{ alert.keyword || '-' }}</el-descriptions-item>
              <el-descriptions-item label="窗口发生次数">{{ alert.occurrence }} 次</el-descriptions-item>
              <el-descriptions-item label="分析到达层级">L{{ alert.layerReached }}（{{ ['','规则抑制','关键字定级','知识库RAG','LLM根因分析','多Agent会诊'][alert.layerReached] || 'L'+alert.layerReached }}）</el-descriptions-item>
              <el-descriptions-item label="处理状态">
                <el-steps :active="alert.status==='OPEN'?0:alert.status==='ACKED'?1:2" finish-status="success" align-center style="margin-top:-4px">
                  <el-step title="OPEN" />
                  <el-step title="ACKED" />
                  <el-step title="RESOLVED" />
                </el-steps>
              </el-descriptions-item>
            </el-descriptions>
          </el-col>
        </el-row>
      </el-card>

      <!-- Analysis Report -->
      <el-card v-if="alert.analysis" style="margin-bottom:16px" shadow="hover">
        <template #header><span style="font-weight:600">📊 分析报告</span></template>

        <!-- Root Cause -->
        <div v-if="rootCause" style="margin-bottom:16px">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <el-tag type="danger" effect="dark">🔍 原因分析</el-tag>
          </div>
          <div v-html="md.render(rootCause)" style="background:var(--el-color-danger-light-9, #fef0f0);border:1px solid var(--el-color-danger-light-7, #fde2e2);padding:12px 16px;border-radius:6px;line-height:1.7" />
        </div>

        <!-- Fix Suggestion -->
        <div v-if="fixSuggestion" style="margin-bottom:16px">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <el-tag type="success" effect="dark">🛠 修复建议</el-tag>
          </div>
          <div v-html="md.render(fixSuggestion)" style="background:var(--el-color-success-light-9, #f0f9eb);border:1px solid var(--el-color-success-light-7, #e1f3d8);padding:12px 16px;border-radius:6px;line-height:1.7" />
        </div>

        <!-- Action Items -->
        <div v-if="actionItems.length" style="margin-bottom:8px">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
            <el-tag type="warning" effect="dark">📋 处置意见</el-tag>
          </div>
          <el-timeline>
            <el-timeline-item v-for="(step, i) in actionItems" :key="i"
              :timestamp="`Step ${i+1}`" placement="top">
              {{ step.replace(/^[\d\.\)\-\s]+/, '').trim() }}
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- Full Analysis Raw -->
        <el-collapse style="margin-top:12px">
          <el-collapse-item title="查看原始分析报告">
            <div v-html="md.render(alert.analysis)" style="padding:8px;line-height:1.7;background:#fafafa;border-radius:4px" />
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <!-- Error Detail (Stack Trace) -->
      <el-card v-if="alert.stackTrace" style="margin-bottom:16px" shadow="hover">
        <template #header><span style="font-weight:600">📋 错误详情</span></template>
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px">
          <el-tag size="small">异常类型</el-tag>
          <span>{{ alert.message }}</span>
        </div>
        <pre style="background:#1a1a2e;color:#cdd6f4;padding:16px;border-radius:6px;overflow-x:auto;max-height:500px;font-size:13px;line-height:1.6;font-family:'JetBrains Mono','Fira Code',monospace"><span v-html="linkedStackTrace" /></pre>
      </el-card>

      <!-- Actions -->
      <el-card shadow="hover">
        <el-space>
          <el-button v-if="alert.status === 'OPEN'" type="primary" size="large" :loading="acking" @click="ack">✅ 确认告警</el-button>
          <el-button type="success" size="large" :loading="converting" @click="toKnowledge">📚 转入知识库</el-button>
          <el-button @click="router.push('/alerts')">返回列表</el-button>
        </el-space>
      </el-card>
    </template>
  </div>
</template>
