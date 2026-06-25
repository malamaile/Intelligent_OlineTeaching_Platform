import { defineStore } from 'pinia'
import { login as loginAPI, logout as logoutAPI, getCurrentUser } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => {
    let userInfo = null
    try {
      userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null')
    } catch {
      userInfo = null
    }
    return {
      token: localStorage.getItem('token') || '',
      userInfo,
    }
  },

  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.userInfo?.roleCode || state.userInfo?.role || '',
    userName: (state) => state.userInfo?.userName || state.userInfo?.realName || '',
  },

  actions: {
    async login(credentials) {
      const res = await loginAPI(credentials)
      this.token = res.data.token
      this.userInfo = res.data.userInfo
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('userInfo', JSON.stringify(res.data.userInfo))
      return res.data.userInfo
    },

    async fetchUserInfo() {
      const res = await getCurrentUser()
      this.userInfo = res.data
      localStorage.setItem('userInfo', JSON.stringify(res.data))
    },

    async logout() {
      try {
        await logoutAPI()
      } catch {
        // 即使后端接口异常，也要完成本地登出
      }
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    },
  },
})
