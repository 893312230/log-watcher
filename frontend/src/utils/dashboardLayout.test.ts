import { describe, it, expect } from 'vitest'
import { loadLayout } from './dashboardLayout'

const IDS = ['a', 'b', 'c']

describe('loadLayout', () => {
  it('无存储时默认全部可见、注册表顺序', () => {
    expect(loadLayout(IDS, null)).toEqual([
      { id: 'a', visible: true }, { id: 'b', visible: true }, { id: 'c', visible: true }])
  })

  it('保留存储的顺序与显隐', () => {
    const stored = JSON.stringify([{ id: 'c', visible: false }, { id: 'a', visible: true }, { id: 'b', visible: true }])
    expect(loadLayout(IDS, stored)).toEqual([
      { id: 'c', visible: false }, { id: 'a', visible: true }, { id: 'b', visible: true }])
  })

  it('新增卡片追加到末尾且默认可见，已删除卡片忽略', () => {
    const stored = JSON.stringify([{ id: 'gone', visible: true }, { id: 'b', visible: false }])
    expect(loadLayout(IDS, stored)).toEqual([
      { id: 'b', visible: false }, { id: 'a', visible: true }, { id: 'c', visible: true }])
  })

  it('存储损坏时回退默认', () => {
    expect(loadLayout(IDS, '{bad json')).toEqual(loadLayout(IDS, null))
    expect(loadLayout(IDS, '"just a string"')).toEqual(loadLayout(IDS, null))
  })

  it('visible 缺失或非 false 视为可见', () => {
    const stored = JSON.stringify([{ id: 'a' }, { id: 'b', visible: 0 }, { id: 'c', visible: false }])
    expect(loadLayout(IDS, stored)).toEqual([
      { id: 'a', visible: true }, { id: 'b', visible: true }, { id: 'c', visible: false }])
  })
})
