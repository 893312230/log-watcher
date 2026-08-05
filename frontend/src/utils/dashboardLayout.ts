/** 仪表盘卡片布局：显隐 + 顺序，持久化到 localStorage。 */

export interface DashboardLayoutItem { id: string; visible: boolean }

const STORAGE_KEY = 'smartops_dashboard_layout'

/**
 * 合并持久化布局与卡片注册表：
 * 已删除卡片忽略，新增卡片追加到末尾且默认可见；存储损坏时回退默认。
 *
 * @param defaultIds 注册表中的全部卡片 id（默认顺序）
 * @param stored     localStorage 中的 JSON 串（可为 null）
 * @returns 布局项数组（顺序即渲染顺序）
 */
export function loadLayout(defaultIds: string[], stored: string | null): DashboardLayoutItem[] {
  const fallback = () => defaultIds.map(id => ({ id, visible: true }))
  if (!stored) return fallback()
  try {
    const parsed = JSON.parse(stored)
    if (!Array.isArray(parsed)) return fallback()
    const known: DashboardLayoutItem[] = parsed
      .filter(i => i && typeof i.id === 'string' && defaultIds.includes(i.id))
      .map(i => ({ id: i.id, visible: i.visible !== false }))
    const missing = defaultIds
      .filter(id => !known.some(i => i.id === id))
      .map(id => ({ id, visible: true }))
    return [...known, ...missing]
  } catch {
    return fallback()
  }
}

/** 从 localStorage 读取布局（浏览器环境入口）。 */
export function readLayout(defaultIds: string[]): DashboardLayoutItem[] {
  return loadLayout(defaultIds, localStorage.getItem(STORAGE_KEY))
}

/** 持久化布局到 localStorage。 */
export function persistLayout(items: DashboardLayoutItem[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
}
