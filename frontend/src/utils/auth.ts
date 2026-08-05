import { ref } from 'vue'

/**
 * 认证状态模块（单一数据源）。
 *
 * <p>凭据优先级：登录 JWT → 静态 Token 兜底（机器调用兼容）。
 * 关键约束：用户显式登出（或 JWT 失效被 401 踢出）后写入 logged_out 标记，
 * 此后 currentToken() 不再回退静态 Token，强制重新登录——
 * 否则登出后静态 Token 仍可调通，登出形同虚设。
 * 纯静态 Token 部署从不写 logged_out，行为与旧版一致。</p>
 *
 * <p>currentUser 为响应式 ref，setAuth/clearAuth 同步写，
 * App.vue 等组件直接引用即可获得登录态变更的响应式更新。</p>
 */

const STATIC_TOKEN = import.meta.env.VITE_API_TOKEN ?? ''
const JWT_KEY = 'smartops_jwt'
const USER_KEY = 'smartops_user'
const LOGGED_OUT_KEY = 'smartops_logged_out'

export interface AuthUser { username: string; role: string }

function readUser(): AuthUser | null {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null }
}

/** 当前登录用户（响应式；未登录或纯静态 Token 部署为 null）。 */
export const currentUser = ref<AuthUser | null>(readUser())

export const hasJwt = () => !!localStorage.getItem(JWT_KEY)

/** 是否处于显式登出状态（此时禁止静态 Token 兜底）。 */
export const isLoggedOut = () => localStorage.getItem(LOGGED_OUT_KEY) === 'true'

/** 当前有效凭据：JWT 优先；登出标记下返回空串（不再回退静态 Token）。 */
export const currentToken = () => localStorage.getItem(JWT_KEY) ?? (isLoggedOut() ? '' : STATIC_TOKEN)

/** 登录成功：写 JWT + 用户信息，清除登出标记。 */
export function setAuth(token: string, user: AuthUser) {
  localStorage.setItem(JWT_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
  localStorage.removeItem(LOGGED_OUT_KEY)
  currentUser.value = user
}

/** 登出：清除凭据并写入登出标记（阻断静态 Token 回退）。 */
export function clearAuth() {
  localStorage.removeItem(JWT_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.setItem(LOGGED_OUT_KEY, 'true')
  currentUser.value = null
}
