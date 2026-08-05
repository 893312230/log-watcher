<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { chat as sendChat, streamChat } from '../../api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt()
const message = ref('')
const conversationId = ref('')
const replies = ref<{ role: string; content: string }[]>([])
const streaming = ref(false)
const chatContainer = ref<HTMLElement>()
let abortController: AbortController | null = null

async function send() {
  if (!message.value.trim() || streaming.value) return
  const userMsg = message.value
  message.value = ''
  replies.value.push({ role: 'user', content: userMsg })
  streaming.value = true
  abortController = new AbortController()

  try {
    // SSE streaming
    const resp = await streamChat({ message: userMsg, conversationId: conversationId.value }, abortController.signal)
    if (!resp.ok || !resp.body) {
      // fallback to sync
      try {
        const r = await sendChat({ message: userMsg, conversationId: conversationId.value })
        conversationId.value = r.data.conversationId
        replies.value.push({ role: 'assistant', content: r.data.reply || r.data.errorMessage || '' })
      } catch (e: any) { replies.value.push({ role: 'error', content: e.message }) }
      return
    }

    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let assistantIdx = replies.value.push({ role: 'assistant', content: '' }) - 1

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const text = decoder.decode(value, { stream: true })
      for (const line of text.split('\n')) {
        if (line.startsWith('data:')) {
          const content = line.substring(5).trim()
          if (content.startsWith('[CONV] ')) {
            conversationId.value = content.substring(7)
            continue
          }
          if (content === '[DONE]') return
          if (content.startsWith('[ERROR]')) {
            replies.value[assistantIdx].content += content; return
          }
          replies.value[assistantIdx].content += content
          await nextTick(); chatContainer.value?.scrollTo({ top: chatContainer.value.scrollHeight })
        }
      }
    }
  } catch (e: any) {
    if (e?.name !== 'AbortError') {
      replies.value.push({ role: 'error', content: '连接中断，请重试: ' + (e?.message || e) })
    }
  } finally {
    streaming.value = false
    abortController = null
  }
}

onUnmounted(() => abortController?.abort())
</script>

<template>
  <div style="display:flex;flex-direction:column;height:calc(100vh - 120px)">
    <div ref="chatContainer" style="flex:1;overflow-y:auto;padding:16px 24px;background:var(--el-bg-color, #fff);border-radius:8px">
      <div v-for="(r, i) in replies" :key="i" :style="{ marginBottom: '16px', textAlign: r.role === 'user' ? 'right' : 'left' }">
        <div :style="{
          display: 'inline-block', maxWidth: '80%', padding: '12px 16px', borderRadius: '8px',
          background: r.role === 'user' ? '#409eff' : r.role === 'error' ? '#f56c6c' : '#f0f2f5',
          color: r.role === 'user' ? '#fff' : '#333', textAlign: 'left'
        }">
          <div v-if="r.role === 'assistant'" v-html="md.render(r.content)" />
          <div v-else>{{ r.content }}</div>
        </div>
      </div>
      <el-empty v-if="replies.length === 0" description="输入运维问题开始对话" />
    </div>
    <div style="display:flex;gap:8px;padding:12px 0">
      <el-input v-model="message" placeholder="输入运维问题..." @keyup.enter="send" :disabled="streaming" size="large" />
      <el-button type="primary" @click="send" :loading="streaming" size="large">发送</el-button>
    </div>
  </div>
</template>
