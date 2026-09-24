// 领域模型映射表与展示工具(角色/状态/环节名、地址缩写、时间格式化)
export const ROLE_NAMES: Record<number, string> = {
  1: '基地',
  2: '加工厂',
  3: '质检机构',
  4: '物流',
  5: '仓库',
  6: '零售商'
}

export const STATUS_NAMES: Record<number, string> = {
  0: '待审批',
  1: '生效',
  2: '已吊销'
}

// stage=6 召回是终态,链上拒绝一切写操作
export const STAGE_NAMES = ['种植', '加工', '质检', '运输', '仓储', '销售', '召回']

export const STAGE_COLORS = [
  '#43A047', // 种植
  '#EB6325', // 加工
  '#0756A5', // 质检
  '#0288D1', // 运输
  '#7E57C2', // 仓储
  '#F9A825', // 销售
  '#D64550'  // 召回
]

// 机构角色 -> 可写入的记录环节;质检机构不写记录,走质检接口
export const ROLE_STAGE: Record<number, number> = {
  1: 0,
  2: 1,
  4: 3,
  5: 4,
  6: 5
}

// 审计日志动作类型
export const ACTION_NAMES: Record<string, string> = {
  REGISTER: '注册',
  LOGIN: '登录',
  APPROVE_USER: '审批账户',
  REVOKE_USER: '吊销账户',
  REGISTER_PRODUCT: '注册产品',
  ADD_RECORD: '环节记录',
  HANDOVER: '交接',
  INSPECT: '质检',
  RECALL: '召回'
}

export function shortAddr(addr?: string | null): string {
  if (!addr) return '-'
  if (addr.length <= 12) return addr
  return `${addr.slice(0, 6)}…${addr.slice(-4)}`
}

export function shortHash(hash?: string | null): string {
  if (!hash) return '-'
  if (hash.length <= 18) return hash
  return `${hash.slice(0, 10)}…${hash.slice(-6)}`
}

// 链上记录的时间是毫秒时间戳
export function formatTime(ms?: number | null): string {
  if (!ms) return '-'
  return new Date(ms).toLocaleString('zh-CN', { hour12: false })
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString('zh-CN', { hour12: false })
}
