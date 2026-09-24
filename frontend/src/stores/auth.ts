// 登录状态:token 与当前用户,持久化到 localStorage
import { defineStore } from 'pinia'
import * as api from '../api'
import type { UserInfo } from '../types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('ft_token') || '',
    user: JSON.parse(localStorage.getItem('ft_user') || 'null') as UserInfo | null
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isRegulator: (s) => !!s.user?.regulator,
    // 判断当前用户是否为某产品的链上责任方
    isHolder: (s) => (chainAddress?: string) =>
      !!s.user && !!chainAddress && s.user.chainAddress?.toLowerCase() === chainAddress.toLowerCase()
  },
  actions: {
    async login(username: string, password: string) {
      const { token, user } = await api.login(username, password)
      this.token = token
      this.user = user
      localStorage.setItem('ft_token', token)
      localStorage.setItem('ft_user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('ft_token')
      localStorage.removeItem('ft_user')
    }
  }
})
