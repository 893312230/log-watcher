import { describe, it, expect } from 'vitest'
import { escapeHtml } from './escape'

describe('escapeHtml', () => {
  it('转义脚本标签防 XSS', () => {
    expect(escapeHtml('<script>alert(1)</script>'))
      .toBe('&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  it('转义 & 与引号', () => {
    expect(escapeHtml(`a&b"c'd`)).toBe('a&amp;b&quot;c&#39;d')
  })

  it('普通堆栈文本不变', () => {
    expect(escapeHtml('at com.example.Foo.bar(Foo.java:12)'))
      .toBe('at com.example.Foo.bar(Foo.java:12)')
  })

  it('空串返回空串', () => {
    expect(escapeHtml('')).toBe('')
  })
})
