import axios from 'axios'
import type {
  ChatRequest, ChatResponse, AlertView, AuditEventView,
  KnowledgeEntryView, ServerConfigView, PageResult
} from '../types'
import { currentToken, hasJwt, clearAuth } from '../utils/auth'

export { currentToken, hasJwt } from '../utils/auth'

/**
 * 401 登出回调（由 router 模块注册，跳转登录页并保留原路由）。
 * 经注册回调间接调 router，避免 api → router 循环依赖。
 */
let unauthorizedHandler: (() => void) | null = null
export const onUnauthorized = (cb: () => void) => { unauthorizedHandler = cb }

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use(config => {
  const token = currentToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  resp => resp,
  err => {
    if (err?.response?.status === 401 && hasJwt()) {
      clearAuth()
      if (unauthorizedHandler) unauthorizedHandler()
      else window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

/** 供 axios 无法覆盖的场景（如 SSE fetch）复用的认证头。 */
export const authHeaders = (): Record<string, string> => {
  const token = currentToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

// ====== Auth ======
export interface LoginResult { token: string; username: string; role: string }

export const login = (username: string, password: string) =>
  api.post<LoginResult>('/auth/login', { username, password })

// ====== Chat ======
export const chat = (data: ChatRequest) =>
  api.post<ChatResponse>('/agent/chat', data)

export const streamChat = (data: ChatRequest, signal?: AbortSignal) =>
  fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(data),
    signal
  })

// ====== Alerts ======
export const listAlerts = (params: Record<string, string>) =>
  api.get<PageResult<AlertView>>('/alerts', { params })

export const getAlert = (id: number) =>
  api.get<AlertView>(`/alerts/${id}`)

export const ackAlert = (id: number) =>
  api.post<void>(`/alerts/${id}/ack`)

export const alertToKnowledge = (id: number) =>
  api.post<KnowledgeEntryView>(`/alerts/${id}/to-knowledge`)

// ====== Alert Stats ======
export interface DailyAlertStat { date: string; count: number }

export const getDailyAlertStats = (days = 7) =>
  api.get<DailyAlertStat[]>('/alerts/stats/daily', { params: { days } })

// ====== Audit ======
export const listAuditEvents = (params: Record<string, string>) =>
  api.get<PageResult<AuditEventView>>('/audit/events', { params })

export const getAuditEvent = (id: number) =>
  api.get<AuditEventView>(`/audit/events/${id}`)

// ====== Knowledge ======
export const listKnowledge = (params: Record<string, string>) =>
  api.get<PageResult<KnowledgeEntryView>>('/knowledge', { params })

export const searchKnowledge = (body: Record<string, unknown>) =>
  api.post<PageResult<KnowledgeEntryView>>('/knowledge/search', body)

export const listKnowledgeCategories = () =>
  api.get<string[]>('/knowledge/categories')

export const getKnowledge = (id: number) =>
  api.get<KnowledgeEntryView>(`/knowledge/${id}`)

export const createKnowledge = (body: Record<string, unknown>) =>
  api.post<KnowledgeEntryView>('/knowledge', body)

export const updateKnowledge = (id: number, body: Record<string, unknown>) =>
  api.put<KnowledgeEntryView>(`/knowledge/${id}`, body)

export const deleteKnowledge = (id: number) =>
  api.delete(`/knowledge/${id}`)

// ====== Servers ======
export const listServers = () =>
  api.get<ServerConfigView[]>('/servers')

export const createServer = (body: Record<string, unknown>) =>
  api.post<ServerConfigView>('/servers', body)

export const updateServer = (id: number, body: Record<string, unknown>) =>
  api.put<ServerConfigView>(`/servers/${id}`, body)

export const deleteServer = (id: number) =>
  api.delete(`/servers/${id}`)

// ====== Health ======
export const health = () => api.get('/health')

// ====== Runbooks ======
export interface RunbookView {
  id: number; name: string; description: string; triggerKeyword: string
  steps: string[]; safetyLevel: number; rollbackSteps: string; enabled: boolean
}

export const listRunbooks = () => api.get<RunbookView[]>('/runbooks')

export const createRunbook = (body: Record<string, unknown>) =>
  api.post<RunbookView>('/runbooks', body)

export interface RunbookStepResultView { step: number; command: string; status: string; output: string }
export interface RunbookExecuteResult {
  runbook: string; status?: string; summary?: string; executionId?: number
  steps?: RunbookStepResultView[]; pendingConfirmation?: boolean
  confirmationToken?: string; safetyLevel?: number
}

export const executeRunbook = (id: number, confirmToken?: string) =>
  api.post<RunbookExecuteResult>(`/runbooks/${id}/execute`, null,
    confirmToken ? { headers: { 'X-Confirm-Token': confirmToken } } : undefined)

export interface RunbookExecutionView {
  id: number; runbookId: number; startedAt: string; finishedAt: string
  status: string; stepResults: { seq: number; command: string; status: string; output: string }[]
}

export const listRunbookHistory = (id: number) =>
  api.get<RunbookExecutionView[]>(`/runbooks/${id}/history`)

/** 查询单条执行记录（异步执行轮询入口）。 */
export const getRunbookExecution = (execId: number) =>
  api.get<RunbookExecutionView>(`/runbooks/executions/${execId}`)

export const deleteRunbook = (id: number) =>
  api.delete(`/runbooks/${id}`)

// ====== Incidents ======
export interface IncidentView {
  id: number; source: string; alertCount: number; level: string; firstAt: string
}

export const listIncidents = () => api.get<IncidentView[]>('/incidents')

export const triggerPostmortem = (id: number) =>
  api.post<Record<string, unknown>>(`/incidents/${id}/postmortem`, {})

// ====== Topology ======
export interface TopologyNode { id: number; name: string; type: string; host: string; status: string }
export interface TopologyEdge { id: number; sourceId: number; targetId: number; type: string }

export const getTopology = () =>
  api.get<{ nodes: TopologyNode[]; edges: TopologyEdge[] }>('/topology')

export const addTopologyNode = (body: Record<string, unknown>) =>
  api.post<TopologyNode>('/topology/nodes', body)

// ====== Notifications ======
export interface NotificationChannelView {
  id: number; name: string; type: string; targetUrl: string; enabled: boolean
}

export const listNotificationChannels = () =>
  api.get<NotificationChannelView[]>('/notifications')

// ====== Webhook Subscriptions ======
export interface WebhookSubscriptionView {
  id: number; name: string; url: string; eventTypes: string
  hasSecret: boolean; enabled: boolean; retryCount: number; createdAt: string
}

export interface WebhookDeliveryView {
  id: number; subscriptionId: number; eventType: string; payload: string
  statusCode?: number; success: boolean; attempt: number; createdAt: string
}

export const listWebhookSubscriptions = () =>
  api.get<WebhookSubscriptionView[]>('/webhooks')

export const createWebhookSubscription = (body: Record<string, unknown>) =>
  api.post<WebhookSubscriptionView>('/webhooks', body)

export const updateWebhookSubscription = (id: number, body: Record<string, unknown>) =>
  api.put<WebhookSubscriptionView>(`/webhooks/${id}`, body)

export const deleteWebhookSubscription = (id: number) =>
  api.delete(`/webhooks/${id}`)

export const listWebhookDeliveries = (subscriptionId?: number) =>
  api.get<WebhookDeliveryView[]>('/webhooks/deliveries',
    { params: subscriptionId ? { subscriptionId } : {} })
