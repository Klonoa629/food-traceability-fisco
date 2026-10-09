// 后端接口返回的数据结构
export interface UserInfo {
  id: number
  username: string
  orgName: string
  role: number
  regulator: boolean
  chainAddress: string
  signUserId: string
  status: number
  createdAt: string
}

export interface TraceRecord {
  stage: number
  description: string
  operator: string
  location: string
  dataHash: string | null
  timestamp: number
}

export interface ProductVO {
  id: number
  name: string
  batchNo: string
  description?: string
  originFarm: string
  currentHolder: string
  stage: number
  recalled: boolean
  records: TraceRecord[]
}

export interface OperateLog {
  id: number
  userId: number
  username: string
  action: string
  targetId: number | null
  chainTxHash: string | null
  detail: string
  createdAt: string
}

export interface Result<T> {
  code: number
  message: string
  data: T
}

// 分页结果
export interface PageVO<T> {
  records: T[]
  total: number
  page: number
  size: number
}

// 审计哈希链校验结果
export interface AuditChainVO {
  intact: boolean
  total: number
  firstBrokenId: number | null
  reason: string | null
}

export interface LoginResponse {
  token: string
  user: UserInfo
}
