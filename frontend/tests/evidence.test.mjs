import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { parseEvidence, verifyEvidence } from '../src/api/evidence.ts'

const data = new TextEncoder().encode('检测报告')
const hash = createHash('sha256').update(data).digest('hex')

test('只有明确的文件引用才提供原文入口', () => {
  assert.equal(parseEvidence('file:sha256:' + hash).kind, 'file')
  assert.equal(parseEvidence('sha256:' + hash).kind, 'digest')
  assert.equal(parseEvidence(hash).kind, 'legacy')
  assert.equal(parseEvidence('file:sha256:invalid').kind, 'legacy')
})

test('浏览器重算内容哈希，识别被替换的原文', async () => {
  assert.equal(await verifyEvidence(data.buffer, hash), true)
  assert.equal(await verifyEvidence(new TextEncoder().encode('替换内容').buffer, hash), false)
})
