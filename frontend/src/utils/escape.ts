/**
 * HTML 转义工具。
 *
 * <p>用于 v-html 渲染不可信内容（如日志堆栈）前的整体转义，
 * 之后再做受控的标记替换（如堆栈帧 → 代码链接），防 XSS 注入。</p>
 */

/** 转义 & < > " ' 为 HTML 实体。 */
export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
