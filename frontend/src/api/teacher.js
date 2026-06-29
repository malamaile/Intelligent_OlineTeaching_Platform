import http from './index'

// ==================== Dashboard ====================
export function getTeacherDashboard() {
  return http.get('/teacher/dashboard')
}

// ==================== Courses ====================
export function getTeacherCourses(params) {
  return http.get('/teacher/courses', { params })
}

export function createCourse(data) {
  return http.post('/teacher/courses', data)
}

export function updateCourse(courseId, data) {
  return http.put(`/teacher/courses/${courseId}`, data)
}

export function deleteCourse(courseId) {
  return http.delete(`/teacher/courses/${courseId}`)
}

export function getCourseProgress(courseId) {
  return http.get(`/teacher/courses/${courseId}/progress`)
}

export function getGrades(courseId) {
  return http.get(`/teacher/courses/${courseId}/grades`)
}

export function saveGrades(courseId, data) {
  return http.post(`/teacher/courses/${courseId}/grades`, data)
}

// ==================== Tasks ====================
export function getTeacherTasks(params) {
  return http.get('/teacher/tasks', { params })
}

export function createTask(data) {
  return http.post('/teacher/tasks', data)
}

export function updateTask(taskId, data) {
  return http.put(`/teacher/tasks/${taskId}`, data)
}

export function deleteTask(taskId) {
  return http.delete(`/teacher/tasks/${taskId}`)
}

export function getTaskSubmissions(taskId, params) {
  return http.get(`/teacher/tasks/${taskId}/submissions`, { params })
}

export function gradeSubmission(submissionId, data) {
  return http.post(`/teacher/submissions/${submissionId}/grade`, data)
}

export function returnSubmission(submissionId, data) {
  return http.post(`/teacher/submissions/${submissionId}/return`, data)
}

// ==================== Resources ====================
export function getTeacherResources(params) {
  return http.get('/teacher/resources', { params })
}

export function uploadResource(data) {
  return http.post('/teacher/resources', data)
}

export function updateResource(resourceId, data) {
  return http.put(`/teacher/resources/${resourceId}`, data)
}

export function deleteResource(resourceId) {
  return http.delete(`/teacher/resources/${resourceId}`)
}

// ==================== Analytics ====================
export function getClassOverview(params) {
  return http.get('/teacher/analytics/class-overview', { params })
}

export function getStudentAnalytics(userId, params) {
  return http.get(`/teacher/analytics/student/${userId}`, { params })
}

export function getAtRiskStudents(params) {
  return http.get('/teacher/analytics/at-risk-students', { params })
}

// ==================== Notices ====================
export function getTeacherNotices(params) {
  return http.get('/teacher/notices', { params })
}

export function createNotice(data) {
  return http.post('/teacher/notices', data)
}

export function deleteNotice(noticeId) {
  return http.delete(`/teacher/notices/${noticeId}`)
}

// ==================== Messages ====================
export function getTeacherMessages(params) {
  return http.get('/teacher/messages', { params })
}

export function markMessageRead(messageId) {
  return http.put(`/teacher/messages/${messageId}/read`)
}

export function markAllMessagesRead() {
  return http.put('/teacher/messages/read-all')
}

// ==================== Guide Files ====================
export function uploadGuideFile(taskId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post(`/teacher/tasks/${taskId}/guide-files`, formData)
}

// ==================== Audit Feedback ====================
export function getAuditFeedback(resourceId) {
  return http.get(`/teacher/resources/${resourceId}/audit-feedback`)
}

export function resubmitResource(resourceId, data) {
  return http.post(`/teacher/resources/${resourceId}/resubmit`, data)
}

// ==================== Export ====================
export function exportGrades(courseId) {
  return http.get(`/teacher/courses/${courseId}/grades/export`, { responseType: 'blob' })
}

export function exportAnalytics(params) {
  return http.get('/teacher/analytics/export', { params, responseType: 'blob' })
}
