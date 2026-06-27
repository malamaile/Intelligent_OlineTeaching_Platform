import http from './index'

/** 通用文件上传 */
export function uploadFile(formData) {
  return http.post('/common/upload', formData)
}

/** 获取院系列表 */
export function getDepartments() {
  return http.get('/common/departments')
}

/** 获取班级列表 */
export function getClasses(params) {
  return http.get('/common/classes', { params })
}

/** 获取学期列表 */
export function getSemesters() {
  return http.get('/common/semesters')
}

/** 获取课程基础信息列表 */
export function getCourseOptions() {
  return http.get('/common/courses')
}

/** 获取当前学期 */
export function getCurrentSemester() {
  return http.get('/common/current-semester')
}
