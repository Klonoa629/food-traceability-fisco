/** 未确认的写操作保留原标识，刷新页面后重试仍指向同一笔交易。 */
export class WriteKeys {
  private storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
  constructor(storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>) {
    this.storage = storage
  }

  begin(user: string, url: string, data: unknown): string {
    const slot = this.slot(user, url)
    const payload = JSON.stringify(data ?? null)
    const saved = this.storage.getItem(slot)
    if (saved) {
      const previous = JSON.parse(saved) as { id: string; payload: string }
      if (previous.payload !== payload) {
        throw new Error('上次操作结果尚未确认，请保持原内容重试并核对结果后再提交新内容')
      }
      return previous.id
    }
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    const id = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
    this.storage.setItem(slot, JSON.stringify({ id, payload }))
    return id
  }

  finish(user: string, url: string, id: string) {
    const slot = this.slot(user, url)
    const saved = this.storage.getItem(slot)
    if (saved && (JSON.parse(saved) as { id: string }).id === id) this.storage.removeItem(slot)
  }

  private slot(user: string, url: string) {
    return 'ft_write:' + JSON.stringify([user, url])
  }
}
