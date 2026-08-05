import { describe, it, expect } from 'vitest'
import { toDisplayPage, toApiPage } from './pagination'

describe('分页映射', () => {
  it('后端 0 起始转显示 1 起始', () => {
    expect(toDisplayPage(0)).toBe(1)
    expect(toDisplayPage(4)).toBe(5)
  })

  it('显示 1 起始转后端 0 起始', () => {
    expect(toApiPage(1)).toBe(0)
    expect(toApiPage(5)).toBe(4)
  })

  it('异常显示页码下限 0', () => {
    expect(toApiPage(0)).toBe(0)
    expect(toApiPage(-3)).toBe(0)
  })
})
