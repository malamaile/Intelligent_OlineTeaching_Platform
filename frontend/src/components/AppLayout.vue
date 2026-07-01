<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import AiFloatingBall from '@/components/AiFloatingBall.vue'
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const isCollapse = computed(() => appStore.sidebarCollapsed)

const menuItems = computed(() => {
  const studentMenus = [
    { path: '/dashboard', title: '首页', icon: 'HomeFilled' },
    { path: '/courses', title: '课程学习', icon: 'Reading' },
    { path: '/tasks', title: '实验实训', icon: 'Document' },
    { path: '/resources', title: '教学资源库', icon: 'FolderOpened' },
    { path: '/analytics', title: '学情诊断', icon: 'DataAnalysis' },
  ]
  const teacherMenus = [
    { path: '/teacher/dashboard', title: '工作台', icon: 'HomeFilled' },
    { path: '/teacher/courses', title: '课程计划管理', icon: 'Reading' },
    { path: '/teacher/tasks', title: '实训实验管理', icon: 'Document' },
    { path: '/teacher/resources', title: '资源上传管理', icon: 'Upload' },
    { path: '/teacher/class-analytics', title: '班级学情查看', icon: 'DataAnalysis' },
    { path: '/teacher/notices', title: '消息公告', icon: 'ChatLineSquare' },
  ]
  const adminMenus = [
    { path: '/admin/dashboard', title: '工作台', icon: 'HomeFilled' },
    { path: '/admin/users', title: '用户管理', icon: 'UserFilled' },
    { path: '/admin/audit', title: '内容审核', icon: 'Checked' },
    { path: '/admin/analytics', title: '学情监控', icon: 'DataLine' },
    { path: '/admin/announcements', title: '公告管理', icon: 'Notification' },
    { path: '/admin/settings', title: '系统设置', icon: 'Setting' },
    { path: '/monitor', title: '系统监控', icon: 'Monitor' },
  ]

  const role = userStore.role
  if (role === 'TEACHER') return [...teacherMenus, { path: '/user-center', title: '个人中心', icon: 'User' }]
  if (role === 'ADMIN') return [...adminMenus, { path: '/user-center', title: '个人中心', icon: 'User' }]
  return [...studentMenus, { path: '/user-center', title: '个人中心', icon: 'User' }]
})

const activeMenu = computed(() => {
  const p = route.path
  // 课程详情高亮课程列表
  if (p.startsWith('/courses/')) return '/courses'
  // 管理员子页面高亮对应菜单
  if (p === '/admin/analytics/courses') return '/admin/analytics'
  if (p.startsWith('/admin/')) return p
  if (p === '/monitor') return '/monitor'
  // 教师子页面高亮对应菜单
  if (p.startsWith('/teacher/')) return p
  return p
})

const homePath = computed(() => {
  const role = userStore.role
  if (role === 'ADMIN') return '/admin/dashboard'
  if (role === 'TEACHER') return '/teacher/dashboard'
  return '/dashboard'
})

function handleMenuSelect(path) {
  if (!path) return
  // 避免重复导航到当前页
  if (route.path === path) return
  router.push(path).catch((err) => {
    if (err.name !== 'NavigationDuplicated') console.error(err)
  })
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
}

// keep-alive 缓存名单（需要保留滚动/状态的页面组件名）
const cachedViews = ['CourseListView', 'TaskListView', 'ResourceListView', 'AnalyticsView', 'DashboardView']
</script>

<template>
  <el-container class="app-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="app-aside">
      <div class="logo-area" @click="router.push(userStore.role === 'ADMIN' ? '/admin/dashboard' : userStore.role === 'TEACHER' ? '/teacher/dashboard' : '/dashboard')">
        <img src="/favicon.ico" alt="logo" class="logo-img" />
        <span v-show="!isCollapse" class="logo-text">智能教学平台</span>
      </div>

      <!-- 侧边栏菜单（el-menu + @select 控制导航，兼容动态路由） -->
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        class="app-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧主体 -->
    <el-container class="main-container">
      <!-- 顶栏 -->
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: homePath }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title && route.path !== homePath">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-avatar :size="32" :src="userStore.userInfo?.avatar" />
              <span class="user-name">{{ userStore.userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/user-center')">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区（keep-alive 缓存 + key 强制刷新非同路由） -->
      <el-main class="app-main">
        <router-view v-slot="{ Component, route: r }">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="r.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>

    <!-- AI 学习助手悬浮球 -->
    <AiFloatingBall />
  </el-container>

</template>

<style scoped>
/* ========== 主框架 ========== */
.app-layout {
  height: 100vh;
}

/* ========== 侧边栏：毛玻璃 ========== */
.app-aside {
  background: rgba(255, 255, 255, 0.5) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(31, 111, 74, 0.12);
  box-shadow: 4px 0 30px rgba(31, 111, 74, 0.08);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.3s;
}

.logo-area {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-bottom: 1px solid rgba(31, 111, 74, 0.12);
  background: linear-gradient(135deg, rgba(31, 111, 74, 0.06), rgba(143, 211, 168, 0.1));
}

.logo-img {
  width: 32px;
  height: 32px;
  margin-right: 8px;
  filter: drop-shadow(0 0 6px rgba(31, 111, 74, 0.3));
}

.logo-text {
  color: #1f6f4a;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  letter-spacing: 1px;
}

/* el-menu router 侧边栏菜单 */
.app-menu {
  background: transparent !important;
  border-right: none !important;
  padding: 8px;
}

.app-menu :deep(.el-menu-item) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px !important;
  border-radius: 10px;
  margin: 2px 0;
  cursor: pointer;
  color: #4f6b5b;
  font-size: 14px;
  line-height: 1.4;
  transition: all 0.2s;
  user-select: none;
  white-space: nowrap;
  height: auto;
}

.app-menu :deep(.el-menu-item:hover) {
  background: rgba(31, 111, 74, 0.08) !important;
  color: #1f6f4a !important;
  transform: translateX(3px);
}

.app-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(31, 111, 74, 0.15), rgba(143, 211, 168, 0.2)) !important;
  color: #1f6f4a !important;
  font-weight: 600;
  box-shadow: 0 2px 12px rgba(31, 111, 74, 0.12);
}

/* 折叠时菜单项居中适配 */
.app-menu:deep(.el-menu-item) .el-icon {
  flex-shrink: 0;
}

/* ========== 顶栏：毛玻璃 ========== */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(31, 111, 74, 0.1);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #8aa095;
  transition: color 0.2s;
}

.collapse-btn:hover {
  color: #1f6f4a;
}

:deep(.el-breadcrumb__inner) {
  color: #8aa095 !important;
}

:deep(.el-breadcrumb__inner.is-link:hover) {
  color: #1f6f4a !important;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #1b2b22 !important;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-name {
  font-size: 14px;
  color: #1b2b22;
}

/* ========== 内容区 ========== */
.main-container {
  background: transparent;
}

.app-main {
  background: transparent;
  min-height: calc(100vh - 60px);
  padding: 20px;
}
</style>
