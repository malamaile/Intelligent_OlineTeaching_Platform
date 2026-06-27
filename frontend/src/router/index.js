import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    // ==================== 公共路由 ====================
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/login/RegisterView.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/forgot-password',
      name: 'ForgotPassword',
      component: () => import('@/views/login/ForgotPasswordView.vue'),
      meta: { title: '忘记密码' },
    },
    {
      path: '/404',
      name: 'NotFound',
      component: () => import('@/views/error/NotFoundView.vue'),
      meta: { title: '404' },
    },
    // ==================== 学生路由 ====================
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('@/views/dashboard/DashboardView.vue'),
      meta: { title: '门户首页' },
    },
    {
      path: '/courses',
      name: 'CourseList',
      component: () => import('@/views/course/CourseListView.vue'),
      meta: { title: '课程学习' },
    },
    {
      path: '/courses/:id',
      name: 'CourseDetail',
      component: () => import('@/views/course/CourseDetailView.vue'),
      meta: { title: '课程详情' },
    },
    {
      path: '/tasks',
      name: 'TaskList',
      component: () => import('@/views/task/TaskListView.vue'),
      meta: { title: '实验实训' },
    },
    {
      path: '/resources',
      name: 'ResourceList',
      component: () => import('@/views/resource/ResourceListView.vue'),
      meta: { title: '教学资源库' },
    },
    {
      path: '/analytics',
      name: 'Analytics',
      component: () => import('@/views/analytics/AnalyticsView.vue'),
      meta: { title: '学情诊断' },
    },
    // ==================== 教师路由 ====================
    {
      path: '/teacher/dashboard',
      name: 'TeacherDashboard',
      component: () => import('@/views/teacher/TeacherDashboardView.vue'),
      meta: { title: '教师工作台' },
    },
    {
      path: '/teacher/courses',
      name: 'TeacherCourses',
      component: () => import('@/views/teacher/TeacherCourseView.vue'),
      meta: { title: '课程计划管理' },
    },
    {
      path: '/teacher/tasks',
      name: 'TeacherTasks',
      component: () => import('@/views/teacher/TeacherTaskView.vue'),
      meta: { title: '实训实验管理' },
    },
    {
      path: '/teacher/resources',
      name: 'TeacherResources',
      component: () => import('@/views/teacher/TeacherResourceView.vue'),
      meta: { title: '资源上传管理' },
    },
    {
      path: '/teacher/class-analytics',
      name: 'TeacherClassAnalytics',
      component: () => import('@/views/teacher/TeacherClassAnalyticsView.vue'),
      meta: { title: '班级学情查看' },
    },
    {
      path: '/teacher/notices',
      name: 'TeacherNotices',
      component: () => import('@/views/teacher/TeacherNoticesView.vue'),
      meta: { title: '消息公告' },
    },
    // ==================== 管理员路由 ====================
    {
      path: '/admin/dashboard',
      name: 'AdminDashboard',
      component: () => import('@/views/admin/AdminDashboardView.vue'),
      meta: { title: '管理员工作台' },
    },
    {
      path: '/admin/users',
      name: 'AdminUsers',
      component: () => import('@/views/admin/AdminUserView.vue'),
      meta: { title: '用户管理' },
    },
    {
      path: '/admin/audit',
      name: 'AdminAudit',
      component: () => import('@/views/admin/AdminAuditView.vue'),
      meta: { title: '内容审核' },
    },
    {
      path: '/admin/analytics',
      name: 'AdminAnalytics',
      component: () => import('@/views/admin/AdminAnalyticsView.vue'),
      meta: { title: '学情监控' },
    },
    {
      path: '/admin/announcements',
      name: 'AdminAnnouncements',
      component: () => import('@/views/admin/AdminAnnouncementsView.vue'),
      meta: { title: '公告管理' },
    },
    {
      path: '/admin/settings',
      name: 'AdminSettings',
      component: () => import('@/views/admin/AdminSettingsView.vue'),
      meta: { title: '系统设置' },
    },
    // ==================== 共享路由 ====================
    {
      path: '/user-center',
      name: 'UserCenter',
      component: () => import('@/views/user/UserCenterView.vue'),
      meta: { title: '个人中心' },
    },
    {
      path: '/monitor',
      name: 'SystemMonitor',
      component: () => import('@/views/monitor/SystemMonitorView.vue'),
      meta: { title: '系统监控' },
    },
    // ==================== Catch-all ====================
    {
      path: '/:pathMatch(.*)*',
      redirect: '/404',
    },
  ],
})

// 路由守卫：未登录跳转到登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const publicPages = ['/login', '/register', '/forgot-password', '/404']
  if (!token && !publicPages.includes(to.path)) {
    return next('/login')
  }
  next()
})

export default router
