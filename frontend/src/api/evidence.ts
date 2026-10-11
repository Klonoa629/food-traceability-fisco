/** 链上旧摘要没有类型标记，不能据此推断文件是否存在。 */
export function parseEvidence(value: string) {
  const file = /^file:sha256:([0-9a-f]{64})$/i.exec(value)
  if (file) return { kind: 'file', hash: file[1]!.toLowerCase(), label: '文件存证' }
  const digest = /^sha256:([0-9a-f]{64})$/i.exec(value)
  if (digest) return { kind: 'digest', hash: digest[1]!.toLowerCase(), label: '业务摘要' }
  return { kind: 'legacy', hash: value, label: '旧版摘要（未标注类型）' }
}

/** 在消费者浏览器重新计算原文摘要，与链上文件引用比对。 */
export async function verifyEvidence(data: ArrayBuffer, expected: string) {
  if (!globalThis.crypto?.subtle) throw new Error('浏览器核验需要 HTTPS 或 localhost 访问')
  const digest = await crypto.subtle.digest('SHA-256', data)
  const actual = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('')
  return actual === expected.toLowerCase()
}
