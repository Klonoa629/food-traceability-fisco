import test from 'node:test'
import assert from 'node:assert/strict'
import { WriteKeys } from '../src/api/writeKeys.ts'

function storage() {
  const rows = new Map()
  return {
    getItem: key => rows.get(key) ?? null,
    setItem: (key, value) => rows.set(key, value),
    removeItem: key => rows.delete(key)
  }
}

test('页面刷新后，相同操作继续使用原请求标识', () => {
  const store = storage()
  const id = new WriteKeys(store).begin('1', '/products/1/records', { description: '入库' })
  assert.equal(new WriteKeys(store).begin('1', '/products/1/records', { description: '入库' }), id)
})

test('结果未确认时不能悄悄更改内容并创建新交易', () => {
  const keys = new WriteKeys(storage())
  keys.begin('1', '/products/1/records', { description: '入库' })
  assert.throws(() => keys.begin('1', '/products/1/records', { description: '已修改' }), /尚未确认/)
})

test('成功后允许发起新的相同业务操作，不会永久按内容去重', () => {
  const keys = new WriteKeys(storage())
  const id = keys.begin('1', '/products/1/records', { stage: 4 })
  keys.finish('1', '/products/1/records', id)
  assert.notEqual(keys.begin('1', '/products/1/records', { stage: 4 }), id)
})

test('不同账户的待确认操作相互隔离', () => {
  const keys = new WriteKeys(storage())
  assert.notEqual(keys.begin('1', '/products', {}), keys.begin('2', '/products', {}))
})
