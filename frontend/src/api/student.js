import http from './index'

// ==================== 门户首页 ====================
export function getDashboard() {
  return http.get('/student/dashboard')
}

// ==================== 课程学习 ====================
export function getMyCourses(params) {
  return http.get('/student/courses', { params })
}

export function getCourseDetail(courseId) {
  return http.get(`/student/courses/${courseId}`)
}

export function joinByInviteCode(data) {
  return http.post('/student/courses/join-by-invite', data)
}

export function reportProgress(courseId, data) {
  return http.post(`/student/courses/${courseId}/progress`, data)
}

// ==================== 实验实训 ====================
export function getTasks(params) {
  return http.get('/student/tasks', { params })
}

export function getTaskDetail(taskId) {
  return http.get(`/student/tasks/${taskId}`)
}

export function submitTask(taskId, formData) {
  return http.post(`/student/tasks/${taskId}/submit`, formData)
}

export function resubmitTask(taskId, formData) {
  return http.post(`/student/tasks/${taskId}/resubmit`, formData)
}

export function getTaskResult(taskId) {
  return http.get(`/student/tasks/${taskId}/result`)
}

// ==================== 教学资源库 ====================
export function getResources(params) {
  return http.get('/student/resources', { params })
}

export function toggleFavorite(resourceId) {
  return http.post(`/student/resources/${resourceId}/favorite`)
}

export function removeFavorite(resourceId) {
  return http.delete(`/student/resources/${resourceId}/favorite`)
}

// ==================== 学情诊断 ====================
export function getAnalyticsOverview(params) {
  return http.get('/student/analytics/overview', { params })
}


export function getDiagnosisReport(params) {
  return http.get('/student/analytics/diagnosis', { params })
}

// ==================== 个人中心 ====================
export function updateProfile(data) {
  return http.put('/student/profile', data)
}

export function uploadAvatar(formData) {
  return http.post('/student/profile/avatar', formData)
}

export function getMyGrades(params) {
  return http.get('/student/grades', { params })
}

export function getMessages(params) {
  return http.get('/student/messages', { params })
}

export function markMessageRead(messageId) {
  return http.put(`/student/messages/${messageId}/read`)
}

export function markAllMessagesRead() {
  return http.put('/student/messages/read-all')
}
