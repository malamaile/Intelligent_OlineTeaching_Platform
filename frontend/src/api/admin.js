import http from './index'

// ==================== Users ====================
export function getUsers(params) {
  return http.get('/admin/users', { params })
}

export function getUserDetail(userId) {
  return http.get(`/admin/users/${userId}`)
}

export function createUser(data) {
  return http.post('/admin/users', data)
}

export function importUsers(formData) {
  return http.post('/admin/users/import', formData)
}

export function updateUser(userId, data) {
  return http.put(`/admin/users/${userId}`, data)
}

export function updateUserStatus(userId, data) {
  return http.put(`/admin/users/${userId}/status`, data)
}

export function resetUserPassword(userId, data) {
  return http.put(`/admin/users/${userId}/reset-password`, data)
}

// ==================== Course Audit ====================
export function getAuditCourses(params) {
  return http.get('/admin/audit/courses', { params })
}

export function auditCourse(courseId, data) {
  return http.post(`/admin/audit/courses/${courseId}`, data)
}

export function getAuditCourseLogs(params) {
  return http.get('/admin/audit/courses/logs', { params })
}

export function getCourseAuditStatistics() {
  return http.get('/admin/audit/courses/statistics')
}

// ==================== Task Audit ====================
export function getAuditTasks(params) {
  return http.get('/admin/audit/tasks', { params })
}

export function auditTask(taskId, data) {
  return http.post(`/admin/audit/tasks/${taskId}`, data)
}

export function getTaskAuditStatistics() {
  return http.get('/admin/audit/tasks/statistics')
}

// ==================== Resource Audit ====================
export function getAuditResources(params) {
  return http.get('/admin/audit/resources', { params })
}

export function auditResource(resourceId, data) {
  return http.post(`/admin/audit/resources/${resourceId}`, data)
}

export function getResourceAuditStatistics() {
  return http.get('/admin/audit/resources/statistics')
}

// ==================== Settings ====================
export function getSettings() {
  return http.get('/admin/settings')
}

export function updateSettings(data) {
  return http.put('/admin/settings', data)
}

// ==================== Announcements ====================
export function getAnnouncements(params) {
  return http.get('/admin/announcements', { params })
}

export function createAnnouncement(data) {
  return http.post('/admin/announcements', data)
}

export function updateAnnouncement(announcementId, data) {
  return http.put(`/admin/announcements/${announcementId}`, data)
}

export function deleteAnnouncement(announcementId) {
  return http.delete(`/admin/announcements/${announcementId}`)
}

// ==================== Analytics ====================
export function getAdminOverview(params) {
  return http.get('/admin/analytics/overview', { params })
}

export function getWarnings(params) {
  return http.get('/admin/analytics/warnings', { params })
}

// ==================== Export ====================
export function exportReport(params) {
  return http.get('/admin/analytics/export', { params, responseType: 'blob' })
}

// ==================== System Monitor ====================
export function getSystemMonitor() {
  return http.get('/admin/monitor')
}
