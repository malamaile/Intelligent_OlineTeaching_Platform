import http from './index'

/** 用户注册 */
export function register(data) {
  return http.post('/auth/register', data)
}

/** 账号密码登录 */
export function login(data) {
  return http.post('/auth/login', data)
}

/** 忘记密码 — 验证身份 */
export function forgotPasswordVerify(data) {
  return http.post('/auth/forgot-password/verify', data)
}

/** 忘记密码 — 重置密码 */
export function forgotPasswordReset(data) {
  return http.post('/auth/forgot-password/reset', data)
}

/** 退出登录 */
export function logout() {
  return http.post('/auth/logout')
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return http.get('/auth/current-user')
}

/** 修改密码（已登录状态） */
export function updatePassword(data) {
  return http.put('/auth/password', data)
}
