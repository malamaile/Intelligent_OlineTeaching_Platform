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
    role: (state) => {
      const roleCode = state.userInfo?.roleCode
      if (roleCode === 'ADMIN' || roleCode === 'TEACHER' || roleCode === 'STUDENT') return roleCode
      // 兜底：通过角色中文名映射
      const roleName = state.userInfo?.role
      if (roleName === '管理员' || roleName === 'admin') return 'ADMIN'
      if (roleName === '教师' || roleName === 'teacher') return 'TEACHER'
      if (roleName === '学生' || roleName === 'student') return 'STUDENT'
      return roleCode || roleName || ''
    },
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
