/**
 * LEGACY MOCK DATA — 仅作为 API 响应格式参考文档保留
 *
 * 此文件已不再被任何 API 模块导入或使用。
 * 所有前端数据流已切换为调用真实后端 /api/v1 接口。
 *
 * 数据字段对照数据库实体表结构，可用于：
 * - 了解各实体的字段名称和含义
 * - 参考 API 响应的预期数据结构
 * - 新功能开发时快速理解数据模型
 *
 * 对应数据库表：
 *   sys_user, sys_role, sys_department, sys_class, sys_semester,
 *   course, course_chapter, course_plan, student_course_enrollment,
 *   experiment_project, experiment_task, student_experiment_submission,
 *   student_grade, teaching_resource, resource_category,
 *   learning_analytics_daily, academic_diagnosis_rule, academic_diagnosis,
 *   sys_announcement, sys_message, sys_config, sys_login_log, sys_audit_log
 */

// ==================== Mock 用户（sys_user + JOIN sys_department + sys_class） ====================
export const mockUsers = {
  STUDENT: {
    userId: 1,              // sys_user.id
    username: '2024001',    // sys_user.username
    realName: '张三（测试学生）', // sys_user.real_name
    userName: '张三（测试学生）', // 兼容旧字段
    roleCode: 'STUDENT',    // sys_role.role_code
    roleName: '学生',       // sys_role.role_name
    role: 'STUDENT',        // 兼容旧字段
    avatar: '',
    email: 'student@test.com',
    phone: '13800138000',
    departmentId: 1,        // sys_user.department_id
    departmentName: '计算机科学与技术学院', // sys_department.dept_name (JOIN)
    department: '计算机科学与技术学院',    // 兼容旧字段
    classId: 1,             // sys_user.class_id
    className: '软件工程2024-1班',  // sys_class.class_name (JOIN)
    status: 1,              // sys_user.status: 1-启用, 0-禁用
  },
  TEACHER: {
    userId: 100,
    username: 't001',
    realName: '王老师（测试教师）',
    userName: '王老师（测试教师）',
    roleCode: 'TEACHER',
    roleName: '教师',
    role: 'TEACHER',
    avatar: '',
    email: 'teacher@test.com',
    phone: '13900139000',
    departmentId: 1,
    departmentName: '计算机科学与技术学院',
    department: '计算机科学与技术学院',
    classId: null,
    className: null,
    status: 1,
  },
  ADMIN: {
    userId: 999,
    username: 'admin',
    realName: '管理员（测试）',
    userName: '管理员（测试）',
    roleCode: 'ADMIN',
    roleName: '管理员',
    role: 'ADMIN',
    avatar: '',
    email: 'admin@test.com',
    phone: '13700137000',
    departmentId: null,
    departmentName: null,
    department: null,
    classId: null,
    className: null,
    status: 1,
  },
}

// ==================== Mock Dashboard（门户首页） ====================
export const mockDashboard = {
  todoList: [
    { type: 'COURSE', title: 'Java程序设计 第三章 — 面向对象编程', courseName: 'Java程序设计', deadline: '2026-06-25', linkUrl: '/courses/1' },
    { type: 'EXPERIMENT', title: '实验二：排序算法实现与性能对比', courseName: '数据结构', deadline: '2026-06-20', linkUrl: '/tasks' },
    { type: 'TRAINING', title: 'Web项目实战 — 前后端分离开发', courseName: 'Web开发技术', deadline: '2026-06-30', linkUrl: '/tasks' },
    { type: 'COURSE', title: '计算机网络 第五章 — 传输层协议', courseName: '计算机网络', deadline: '2026-06-22', linkUrl: '/courses/3' },
  ],
  notifications: [
    { id: 1, type: 'ANNOUNCEMENT', title: '关于期末考试安排的通知', content: '本学期期末考试将于2026年7月5日（周一）至7月12日（周一）进行，具体安排如下：\n\n1. 公共课考试：7月5日-7月7日\n2. 专业课考试：7月8日-7月12日\n\n请各位同学提前做好复习准备，具体考场安排请登录教务系统查看。如有疑问请联系各院系教务办公室。', publisher: '教务处', publishTime: '2026-06-18 10:00:00' },
    { id: 2, type: 'CLASS_NOTICE', title: '本周五实验课调至周六上午', content: '各位同学：\n\n因实验室周五下午有考试安排，本周五（6月20日）下午的数据结构实验课调整至周六（6月21日）上午9:00-11:30，地点不变（实验楼A301）。\n\n请相互转告，按时参加。如有问题请提前联系老师。', publisher: '李老师', publishTime: '2026-06-17 14:30:00' },
    { id: 3, type: 'ANNOUNCEMENT', title: '校园网络维护通知', content: '为提升校园网络稳定性和安全性，信息化中心计划于6月20日（周六）凌晨2:00至6:00进行网络核心设备升级维护。\n\n维护期间校园网（含教学楼、宿舍区、图书馆）将暂停服务，校外访问校内系统也将受到影响。请提前安排好相关学习和工作。\n\n如有特殊情况请联系信息化中心：010-12345678。', publisher: '信息化中心', publishTime: '2026-06-16 09:00:00' },
  ],
  todayStats: { studyDuration: 125, completedCourses: 2, pendingTasks: 3 },
}

// ==================== Mock 课程（course + course_chapter JOIN teacher） ====================
export const mockCourses = {
  records: [
    { courseId: 1, courseName: 'Java程序设计', courseCode: 'CS101', teacherId: 100, teacherName: '王老师', semester: '2025-2026-2', progress: 65, totalHours: 48, completedHours: 31, startDate: '2026-03-01', coverImage: '' },
    { courseId: 2, courseName: '数据结构', courseCode: 'CS201', teacherId: 101, teacherName: '李老师', semester: '2025-2026-2', progress: 42, totalHours: 64, completedHours: 27, startDate: '2026-03-01', coverImage: '' },
    { courseId: 3, courseName: '计算机网络', courseCode: 'CS301', teacherId: 102, teacherName: '赵老师', semester: '2025-2026-2', progress: 78, totalHours: 48, completedHours: 37, startDate: '2026-03-01', coverImage: '' },
    { courseId: 4, courseName: '操作系统', courseCode: 'CS401', teacherId: 103, teacherName: '陈老师', semester: '2025-2026-2', progress: 30, totalHours: 56, completedHours: 17, startDate: '2026-03-01', coverImage: '' },
    { courseId: 5, courseName: '软件工程', courseCode: 'SE101', teacherId: 104, teacherName: '刘老师', semester: '2025-2026-2', progress: 55, totalHours: 48, completedHours: 26, startDate: '2026-03-01', coverImage: '' },
  ],
  total: 5, page: 1, pageSize: 10, totalPages: 1,
}

// 课程详情（course_chapter 展开）
export function mockCourseDetail(courseId) {
  const courseNames = { 1: 'Java程序设计', 2: '数据结构', 3: '计算机网络', 4: '操作系统', 5: '软件工程' }
  const teachers = { 1: '王老师', 2: '李老师', 3: '赵老师', 4: '陈老师', 5: '刘老师' }
  const teacherIds = { 1: 100, 2: 101, 3: 102, 4: 103, 5: 104 }
  return {
    courseId,                                        // course.id
    courseName: courseNames[courseId] || '未知课程', // course.course_name
    courseCode: `CS${courseId}01`,                   // course.course_code
    teacherId: teacherIds[courseId] || 0,            // course.teacher_id
    teacherName: teachers[courseId] || '未知教师',   // sys_user.real_name (JOIN)
    teacherAvatar: '',
    semester: '2025-2026-2',
    description: '本课程介绍相关基础理论、核心概念与实践应用，通过系统学习掌握领域基本技能。',
    progress: [65, 42, 78, 30, 55][courseId - 1] || 50,
    totalChapters: 6,
    completedChapters: [4, 3, 5, 2, 3][courseId - 1] || 3,
    chapters: [
      { chapterId: 1, chapterName: '第一章 基础知识', chapterOrder: 1, videoUrl: '', videoDuration: 7200, status: 'COMPLETED', materials: [{ materialId: 1, name: '第一章课件.pdf', type: 'PDF', fileSize: 2048000, downloadUrl: '' }] },
      { chapterId: 2, chapterName: '第二章 核心概念', chapterOrder: 2, videoUrl: '', videoDuration: 9000, status: 'COMPLETED', materials: [{ materialId: 2, name: '第二章课件.pdf', type: 'PDF', fileSize: 1800000, downloadUrl: '' }] },
      { chapterId: 3, chapterName: '第三章 深入理解', chapterOrder: 3, videoUrl: '', videoDuration: 10800, status: 'COMPLETED', materials: [] },
      { chapterId: 4, chapterName: '第四章 实践应用', chapterOrder: 4, videoUrl: '', videoDuration: 12000, status: 'IN_PROGRESS', lastPosition: 3600, materials: [{ materialId: 4, name: '第四章实验指导.docx', type: 'DOCX', fileSize: 512000, downloadUrl: '' }] },
      { chapterId: 5, chapterName: '第五章 进阶专题', chapterOrder: 5, videoUrl: '', videoDuration: 9600, status: 'NOT_STARTED', materials: [] },
      { chapterId: 6, chapterName: '第六章 总结与展望', chapterOrder: 6, videoUrl: '', videoDuration: 6000, status: 'NOT_STARTED', materials: [] },
    ],
  }
}

// ==================== Mock 实验实训（experiment_project + experiment_task + submission） ====================
// 数据库三表关联：experiment_project ─1:N─ experiment_task ─1:N─ student_experiment_submission
export const mockTasks = {
  records: [
    // taskId = experiment_task.id, projectId = experiment_project.id
    { taskId: 1, projectId: 1, projectType: 'EXPERIMENT', title: '实验一：Java基本语法练习', courseName: 'Java程序设计', teacherName: '王老师', deadline: '2026-05-10 23:59:59', submitTime: '2026-05-09 18:00:00', status: 'GRADED', score: 92, teacherComment: '完成度优秀', resubmitCount: 0 },
    { taskId: 2, projectId: 2, projectType: 'EXPERIMENT', title: '实验二：排序算法实现与性能对比', courseName: '数据结构', teacherName: '李老师', deadline: '2026-06-20 23:59:59', submitTime: null, status: 'IN_PROGRESS', score: null, teacherComment: null, resubmitCount: 0 },
    { taskId: 3, projectId: 3, projectType: 'TRAINING', title: 'Web项目实战 — 前后端分离开发', courseName: 'Web开发技术', teacherName: '赵老师', deadline: '2026-06-15 23:59:59', submitTime: '2026-06-14 20:30:00', status: 'GRADED', score: 85, teacherComment: '整体完成度较高，注意代码注释规范', resubmitCount: 0 },
    { taskId: 4, projectId: 4, projectType: 'EXPERIMENT', title: '实验一：进程调度模拟', courseName: '操作系统', teacherName: '陈老师', deadline: '2026-06-10 23:59:59', submitTime: null, status: 'OVERDUE', score: null, teacherComment: null, resubmitCount: 0 },
    { taskId: 5, projectId: 5, projectType: 'TRAINING', title: '需求分析文档编写', courseName: '软件工程', teacherName: '刘老师', deadline: '2026-06-25 23:59:59', submitTime: '2026-06-16 11:00:00', status: 'RETURNED', score: 55, teacherComment: '需求分析不够详尽，缺少用例图', resubmitCount: 1 },
  ],
  total: 5, page: 1, pageSize: 10, totalPages: 1,
}

// 任务详情（experiment_project 描述 + experiment_task 安排信息）
export function mockTaskDetail(taskId) {
  const tasks = mockTasks.records
  const task = tasks.find(t => t.taskId === taskId) || tasks[0]
  return {
    ...task,
    // experiment_project.description
    description: '## 任务要求\n\n1. 根据课堂所学内容，完成相关代码实现\n2. 编写实验/实训报告，记录过程和结果\n3. 报告需包含：实验目的、实验步骤、实验结果、分析总结\n\n## 提交要求\n\n- 报告格式：PDF 或 Word\n- 代码附件：zip 压缩包\n- 截止时间前提交至本平台',
    // experiment_project.guide_file_url
    guideFiles: [
      { fileId: 1, name: '实验指导书.pdf', fileSize: 1024000, downloadUrl: '' },
      { fileId: 2, name: '评分标准.docx', fileSize: 256000, downloadUrl: '' },
    ],
    // student_experiment_submission 当前用户提交
    mySubmission: ['GRADED', 'RETURNED', 'SUBMITTED'].includes(task.status) ? { submitTime: task.submitTime, status: task.status } : null,
  }
}

// ==================== Mock 教学资源（teaching_resource + resource_category） ====================
// visibility 对应 DB: CLASS-本班, DEPT-本院, SCHOOL-全校
export const mockResources = {
  records: [
    { resourceId: 1, resourceName: 'Java基础语法课件', categoryId: 1, categoryName: '课件', fileSize: 5120000, uploadTime: '2026-05-10', downloadCount: 328, teacherName: '王老师', courseName: 'Java程序设计', visibility: 'SCHOOL', isFavorited: true, previewUrl: '' },
    { resourceId: 2, resourceName: '排序算法详解视频', categoryId: 3, categoryName: '视频', fileSize: 25600000, uploadTime: '2026-05-15', downloadCount: 156, teacherName: '李老师', courseName: '数据结构', visibility: 'SCHOOL', isFavorited: false, previewUrl: '' },
    { resourceId: 3, resourceName: '数据结构课后习题集', categoryId: 2, categoryName: '习题', fileSize: 2048000, uploadTime: '2026-05-20', downloadCount: 89, teacherName: '李老师', courseName: '数据结构', visibility: 'CLASS', isFavorited: false, previewUrl: '' },
    { resourceId: 4, resourceName: '网络协议分析实验手册', categoryId: 4, categoryName: '文档', fileSize: 1024000, uploadTime: '2026-06-01', downloadCount: 67, teacherName: '赵老师', courseName: '计算机网络', visibility: 'SCHOOL', isFavorited: true, previewUrl: '' },
    { resourceId: 5, resourceName: '操作系统原理PPT合集', categoryId: 1, categoryName: '课件', fileSize: 8192000, uploadTime: '2026-04-20', downloadCount: 210, teacherName: '陈老师', courseName: '操作系统', visibility: 'DEPT', isFavorited: false, previewUrl: '' },
    { resourceId: 6, resourceName: '软件工程UML建模指南', categoryId: 4, categoryName: '文档', fileSize: 3584000, uploadTime: '2026-06-05', downloadCount: 45, teacherName: '刘老师', courseName: '软件工程', visibility: 'CLASS', isFavorited: false, previewUrl: '' },
    { resourceId: 7, resourceName: 'Java多线程编程实战视频', categoryId: 3, categoryName: '视频', fileSize: 48000000, uploadTime: '2026-05-25', downloadCount: 412, teacherName: '王老师', courseName: 'Java程序设计', visibility: 'SCHOOL', isFavorited: true, previewUrl: '' },
    { resourceId: 8, resourceName: '树与图专题习题集', categoryId: 2, categoryName: '习题', fileSize: 1536000, uploadTime: '2026-06-10', downloadCount: 32, teacherName: '李老师', courseName: '数据结构', visibility: 'SCHOOL', isFavorited: false, previewUrl: '' },
  ],
  total: 8, page: 1, pageSize: 12, totalPages: 1,
}

// ==================== Mock 学情诊断（academic_diagnosis + learning_analytics_daily） ====================
// diagnosis_level 对应 DB: EXCELLENT-优秀, GOOD-良好, TO_IMPROVE-待提升
export const mockAnalyticsOverview = {
  totalStudyDuration: 4800,      // 总学习时长（分钟）
  averageScore: 82.5,            // 综合平均分
  taskCompletionRate: 88.0,      // 任务完成率（%）
  courseStats: [
    { courseId: 1, courseName: 'Java程序设计', studyDuration: 1800, exerciseCorrectRate: 85.0, taskCompletionRate: 90.0, score: 88 },
    { courseId: 2, courseName: '数据结构', studyDuration: 1500, exerciseCorrectRate: 78.0, taskCompletionRate: 83.0, score: 76 },
    { courseId: 3, courseName: '计算机网络', studyDuration: 900, exerciseCorrectRate: 92.0, taskCompletionRate: 95.0, score: 91 },
    { courseId: 4, courseName: '操作系统', studyDuration: 600, exerciseCorrectRate: 72.0, taskCompletionRate: 78.0, score: 70 },
  ],
  weeklyTrend: [
    { week: '第13周', duration: 350, taskCount: 5 },
    { week: '第14周', duration: 320, taskCount: 4 },
    { week: '第15周', duration: 280, taskCount: 3 },
    { week: '第16周', duration: 400, taskCount: 5 },
  ],
}

export const mockDiagnosisReport = {
  diagnosisLevel: 'GOOD',        // academic_diagnosis.diagnosis_level
  levelLabel: '良好',
  overallScore: 82.5,
  summary: '你在本学期各课程中表现良好。Java程序设计和计算机网络掌握扎实，数据结构的排序算法和操作系统部分需要加强练习。',
  weakPoints: [
    { knowledge: '排序算法', courseName: '数据结构', suggestResourceId: 15 },
    { knowledge: '树与图', courseName: '数据结构', suggestResourceId: 18 },
    { knowledge: '进程调度', courseName: '操作系统', suggestResourceId: 20 },
  ],
  strengths: [
    { knowledge: '面向对象编程', courseName: 'Java程序设计' },
    { knowledge: '网络协议', courseName: '计算机网络' },
  ],
  suggestResources: [
    { resourceId: 15, resourceName: '排序算法详解视频', categoryName: '视频', courseName: '数据结构' },
    { resourceId: 18, resourceName: '树与图专题习题集', categoryName: '习题', courseName: '数据结构' },
    { resourceId: 20, resourceName: '进程调度模拟实验指导', categoryName: '文档', courseName: '操作系统' },
  ],
  generatedTime: '2026-06-18 08:00:00',
}

// ==================== Mock 成绩（student_grade） ====================
// 字段对照 DB: usual_grade → usualGrade, exam_grade → examGrade, experiment_grade → experimentGrade
export const mockGrades = {
  records: [
    { gradeId: 1, studentId: 1, coursePlanId: 1, semesterId: 1, usualGrade: 90, examGrade: 86, experimentGrade: 88, trainingGrade: null, finalGrade: 88, courseName: 'Java程序设计', semester: '2025-2026-2', teacherName: '王老师', credit: 3.0, gpa: 3.7 },
    { gradeId: 2, studentId: 1, coursePlanId: 2, semesterId: 1, usualGrade: 80, examGrade: 72, experimentGrade: 78, trainingGrade: null, finalGrade: 76, courseName: '数据结构', semester: '2025-2026-2', teacherName: '李老师', credit: 4.0, gpa: 3.0 },
    { gradeId: 3, studentId: 1, coursePlanId: 3, semesterId: 1, usualGrade: 95, examGrade: 89, experimentGrade: 90, trainingGrade: null, finalGrade: 91, courseName: '计算机网络', semester: '2025-2026-2', teacherName: '赵老师', credit: 3.0, gpa: 4.0 },
  ],
  total: 3, page: 1, pageSize: 10, totalPages: 1,
}

// ==================== Mock 消息（sys_message） ====================
// message_type 对应 DB: NOTIFICATION-通知, REMINDER-提醒, ALERT-预警, APPROVAL-审批通知
export const mockMessages = {
  records: [
    { messageId: 1, senderId: null, receiverId: 1, messageType: 'NOTIFICATION', title: '实验报告已批改', content: '您的《数据结构》实验二报告已批改，得分85分，请查看详情。', isRead: false, relatedBizType: 'SUBMISSION', relatedBizId: 5, sendTime: '2026-06-19 10:05:00' },
    { messageId: 2, senderId: 100, receiverId: 1, messageType: 'REMINDER', title: '调课通知', content: '本周五实验课因教室临时占用调整至周六上午9:00，地点不变。', isRead: false, relatedBizType: null, relatedBizId: null, sendTime: '2026-06-17 14:30:00' },
    { messageId: 3, senderId: null, receiverId: 1, messageType: 'NOTIFICATION', title: '期末考试安排', content: '本学期期末考试将于7月5日-7月12日进行。', isRead: true, relatedBizType: 'ANNOUNCEMENT', relatedBizId: 1, sendTime: '2026-06-15 09:00:00' },
    { messageId: 4, senderId: null, receiverId: 1, messageType: 'ALERT', title: '学情预警', content: '您的《操作系统》课程学习进度落后于班级平均水平。', isRead: false, relatedBizType: 'COURSE', relatedBizId: 4, sendTime: '2026-06-14 08:00:00' },
    { messageId: 5, senderId: null, receiverId: 1, messageType: 'NOTIFICATION', title: '系统维护通知', content: '平台将于6月20日（周六）凌晨2:00-6:00进行系统维护。', isRead: true, relatedBizType: null, relatedBizId: null, sendTime: '2026-06-13 12:00:00' },
  ],
  total: 5, unreadCount: 3, page: 1, pageSize: 10, totalPages: 1,
}
