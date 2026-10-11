// axios 封装:注入 Bearer、解包 Result、401 自动登出
import axios, { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { WriteKeys } from './writeKeys'
import { ElMessage } from 'element-plus'
import type { Result } from '../types'

const writeKeys = new WriteKeys(sessionStorage)
const pendingRequests = new WeakMap<InternalAxiosRequestConfig, { user: string; url: string; id: string }>()
function finishWrite(config: InternalAxiosRequestConfig | undefined, code: number) {
  if (!config || (code !== 0 && (code < 400 || code >= 500))) return
  const key = pendingRequests.get(config)
  if (key) writeKeys.finish(key.user, key.url, key.id)
}

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('ft_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (config.method === 'post' && ['/products', '/admin/users/', '/admin/products/'].some(prefix => config.url?.startsWith(prefix))) {
    const user = String((JSON.parse(localStorage.getItem('ft_user') || 'null') as { id?: number } | null)?.id ?? '')
    const url = config.url!
    try {
      const id = writeKeys.begin(user, url, config.data)
      config.headers.set('Idempotency-Key', id)
      pendingRequests.set(config, { user, url, id })
    } catch (error) {
      ElMessage.error((error as Error).message)
      throw error
    }
  }
  return config
})

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

http.interceptors.response.use(
  (res: AxiosResponse<Result<unknown>>) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      finishWrite(res.config, body.code)
      if (body.code === 0) return res
      if (body.code === 401) {
        onUnauthorized?.()
        return Promise.reject(new ApiError(401, body.message || '登录已过期'))
      }
      // 502 时 message 带合约 revert 原文,完整展示
      ElMessage.error(body.message || `请求失败(code=${body.code})`)
      return Promise.reject(new ApiError(body.code, body.message || '请求失败'))
    }
    return res
  },
  (err: AxiosError<Result<unknown>>) => {
    const status = err.response?.status ?? -1
    finishWrite(err.config, status)
    const msg = err.response?.data?.message || err.message || '网络异常'
    if (status === 401) {
      onUnauthorized?.()
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(new ApiError(status, msg))
  }
)

export async function unwrap<T>(p: Promise<AxiosResponse<Result<T>>>): Promise<T> {
  const res = await p
  return res.data.data
}

export default http
