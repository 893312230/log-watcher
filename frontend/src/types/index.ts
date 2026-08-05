export interface ChatRequest {
  conversationId?: string
  message: string
  confirmationToken?: string
}

export interface ChatResponse {
  conversationId: string
  reply: string
  timestamp: string
  mode: string
  iterations: number
  success: boolean
  errorMessage?: string
  pendingConfirmation: boolean
  confirmationToken?: string
}

export interface AlertView {
  id: number
  fingerprint: string
  source: string
  level: 'ERROR' | 'WARN' | 'INFO'
  keyword: string
  message: string
  stackTrace: string
  analysis: string
  suggestion: string
  layerReached: number
  occurrence: number
  status: 'OPEN' | 'ACKED' | 'RESOLVED'
  createdAt: string
  updatedAt: string
}

export interface AuditEventView {
  id: number
  eventType: 'LLM_CALL' | 'TOOL_CALL' | 'TASK_EXECUTION' | 'SECURITY_DECISION'
  traceId: string
  actor: string
  target: string
  detail: string
  success: boolean
  latencyMs: number
  createdAt: string
}

export interface KnowledgeEntryView {
  id: number
  title: string
  errorPattern: string
  rootCause: string
  suggestion: string
  actionItems: string[]
  category: string
  tags: string[]
  source: string
  sourceAlertId: number
  serverConfigId: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface ServerConfigView {
  id: number
  name: string
  host: string
  deployPath: string
  codeRepo: string
  logPath: string
  description: string
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}
