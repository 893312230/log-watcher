import { describe, it, expect, vi, beforeEach } from 'vitest'

function stubLocalStorage() {
  const store = new Map<string, string>()
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
    setItem: (k: string, v: string) => { store.set(k, String(v)) },
    removeItem: (k: string) => { store.delete(k) },
    clear: () => store.clear(),
    key: (i: number) => [...store.keys()][i] ?? null,
    get length() { return store.size }
  })
}

beforeEach(() => {
  vi.resetModules()
  stubLocalStorage()
})

describe('auth token 分支', () => {
  it('无 JWT 且未配置 VITE_API_TOKEN 时凭据为空', async () => {
    const { currentToken } = await import('./auth')
    expect(currentToken()).toBe('')
  })

  it('无 JWT 且配置 VITE_API_TOKEN 时回退静态 Token', async () => {
    vi.stubEnv('VITE_API_TOKEN', 'static-token-x')
    const { currentToken } = await import('./auth')
    expect(currentToken()).toBe('static-token-x')
    vi.unstubAllEnvs()
  })

  it('有 JWT 时优先 JWT', async () => {
    const { setAuth, currentToken } = await import('./auth')
    setAuth('jwt-1', { username: 'admin', role: 'ADMIN' })
    expect(currentToken()).toBe('jwt-1')
  })

  it('显式登出后不回退静态 Token', async () => {
    const { setAuth, clearAuth, currentToken, isLoggedOut, hasJwt } = await import('./auth')
    setAuth('jwt-1', { username: 'admin', role: 'ADMIN' })
    clearAuth()
    expect(isLoggedOut()).toBe(true)
    expect(hasJwt()).toBe(false)
    expect(currentToken()).toBe('')
  })

  it('重新登录清除登出标记并同步响应式用户', async () => {
    const { setAuth, clearAuth, currentUser, isLoggedOut } = await import('./auth')
    clearAuth()
    setAuth('jwt-2', { username: 'viewer', role: 'VIEWER' })
    expect(isLoggedOut()).toBe(false)
    expect(currentUser.value).toEqual({ username: 'viewer', role: 'VIEWER' })
  })

  it('登出清空响应式用户', async () => {
    const { setAuth, clearAuth, currentUser } = await import('./auth')
    setAuth('jwt-1', { username: 'admin', role: 'ADMIN' })
    clearAuth()
    expect(currentUser.value).toBeNull()
  })

  it('smartops_user 损坏 JSON 兜底 null', async () => {
    localStorage.setItem('smartops_user', '{bad json')
    const { currentUser } = await import('./auth')
    expect(currentUser.value).toBeNull()
  })
})
