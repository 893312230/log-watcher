/**
 * 分页映射工具。
 *
 * <p>后端分页参数 0 起始，el-pagination 显示 1 起始，
 * 列表页统一经本模块互转，避免偏移一页的重复/缺页。</p>
 */

/** 后端页码（0 起始）→ el-pagination 显示页码（1 起始）。 */
export const toDisplayPage = (apiPage: number) => apiPage + 1

/** el-pagination 显示页码（1 起始）→ 后端页码（0 起始，下限 0）。 */
export const toApiPage = (displayPage: number) => Math.max(0, displayPage - 1)
