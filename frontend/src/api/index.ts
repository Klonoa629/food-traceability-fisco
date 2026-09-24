// 后端接口:认证、业务、监管三组
import http, { unwrap } from './http'
import type { LoginResponse, OperateLog, ProductVO, Result, UserInfo } from '../types'

// 认证
export const login = (username: string, password: string) =>
  unwrap<LoginResponse>(http.post<Result<LoginResponse>>('/auth/login', { username, password }))

export const register = (data: { username: string; password: string; orgName: string; role: number }) =>
  unwrap<number>(http.post<Result<number>>('/auth/register', data))

// 业务
export const listProducts = () => unwrap<ProductVO[]>(http.get<Result<ProductVO[]>>('/products'))

export const getProduct = (id: number | string) =>
  unwrap<ProductVO>(http.get<Result<ProductVO>>(`/products/${id}`))

export const createProduct = (data: { name: string; batchNo: string; description?: string; location: string; dataHash?: string | null }) =>
  unwrap<ProductVO>(http.post<Result<ProductVO>>('/products', data))

export const addRecord = (id: number, data: { stage: number; description: string; location: string; dataHash?: string | null }) =>
  unwrap<ProductVO>(http.post<Result<ProductVO>>(`/products/${id}/records`, data))

export const handover = (id: number, nextHolder: string) =>
  unwrap<ProductVO>(http.post<Result<ProductVO>>(`/products/${id}/handover`, { nextHolder }))

export const inspect = (id: number, data: { qualified: boolean; reportHash?: string | null }) =>
  unwrap<ProductVO>(http.post<Result<ProductVO>>(`/products/${id}/inspect`, data))

export const listOrgs = () => unwrap<UserInfo[]>(http.get<Result<UserInfo[]>>('/orgs'))

// 免登录公开溯源查询
export const publicTrace = (batchNo: string) =>
  unwrap<ProductVO>(http.get<Result<ProductVO>>('/public/trace', { params: { batchNo } }))

// 该接口返回未走统一包装,直接取 body
export const chainPing = async (): Promise<{ blockNumber?: number }> => {
  const res = await http.get('/chain/ping')
  return res.data
}

// 监管
export const adminListUsers = (status?: number) =>
  unwrap<UserInfo[]>(http.get<Result<UserInfo[]>>('/admin/users', { params: status === undefined ? {} : { status } }))

export const adminApprove = (id: number, role: number) =>
  unwrap<unknown>(http.post<Result<unknown>>(`/admin/users/${id}/approve`, { role }))

export const adminRevoke = (id: number) =>
  unwrap<unknown>(http.post<Result<unknown>>(`/admin/users/${id}/revoke`))

export const adminLogs = (params: { action?: string; userId?: number }) =>
  unwrap<OperateLog[]>(http.get<Result<OperateLog[]>>('/admin/logs', { params }))

export const adminRecall = (id: number, reason: string) =>
  unwrap<unknown>(http.post<Result<unknown>>(`/admin/products/${id}/recall`, { reason }))

// 审计记录链上校验:记录 id -> 是否与链上一致
export const adminVerifyLogs = () =>
  unwrap<Record<string, boolean>>(http.get<Result<Record<string, boolean>>>('/admin/logs/verify'))
