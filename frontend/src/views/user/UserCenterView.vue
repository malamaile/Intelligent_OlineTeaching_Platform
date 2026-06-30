<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { updateProfile, uploadAvatar, getMyGrades, getMessages, markMessageRead, markAllMessagesRead } from '@/api/student'
import { updatePassword } from '@/api/auth'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ---- 活跃标签 ----
const activeTab = ref('profile')

// ---- 个人资料 ----
const profileFormRef = ref(null)
const savingProfile = ref(false)
const profileForm = reactive({
  realName: '',
  phone: '',
  email: '',
})

const profileRules = {
  realName: [
    { min: 2, max: 20, message: '姓名长度为 2-20 位', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
}

// 用户状态映射
const statusMap = { 1: '正常', 0: '冻结' }
const statusTagType = { 1: 'success', 0: 'danger' }

// 角色中文映射
const roleMap = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }

function handleResetProfile() {
  profileForm.realName = userStore.userInfo?.userName || ''
  profileForm.phone = userStore.userInfo?.phone || ''
  profileForm.email = userStore.userInfo?.email || ''
  profileFormRef.value?.clearValidate()
}

async function handleSaveProfile() {
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    await updateProfile({
      nickname: profileForm.realName,
      phone: profileForm.phone,
      email: profileForm.email,
    })
    await userStore.fetchUserInfo()
    ElMessage.success('信息修改成功')
  } finally {
    savingProfile.value = false
  }
}

// ---- 头像上传 ----
const uploadingAvatar = ref(false)
async function handleAvatarChange(options) {
  uploadingAvatar.value = true
  try {
    const file = options.file
    // 立刻显示本地预览（不依赖服务端）
    const localPreview = URL.createObjectURL(file)
    userStore.userInfo.avatar = localPreview
    // 上传到服务端
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadAvatar(formData)
    const avatarUrl = res.data.avatarUrl
    // 用服务端 URL 替换本地预览
    userStore.userInfo.avatar = avatarUrl
    ElMessage.success('头像上传成功')
    // 异步持久化到服务端
    updateProfile({ avatar: avatarUrl }).catch(() => {})
  } catch (err) {
    // 上传失败已在拦截器中提示
  } finally {
    uploadingAvatar.value = false
  }
}

// ---- 修改密码 ----
const passwordFormRef = ref(null)
const changingPassword = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return
  changingPassword.value = true
  try {
    await updatePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    await userStore.logout()
    router.push('/login')
  } finally {
    changingPassword.value = false
  }
}

// ---- 成绩查询 ----
const gradesLoading = ref(false)
const grades = ref([])
const gradesPagination = reactive({ page: 1, pageSize: 10, total: 0 })

async function fetchGrades() {
  gradesLoading.value = true
  try {
    const res = await getMyGrades({ page: gradesPagination.page, pageSize: gradesPagination.pageSize })
    grades.value = res.data.records
    gradesPagination.total = res.data.total
  } finally {
    gradesLoading.value = false
  }
}

function handleGradesPageChange(page) {
  gradesPagination.page = page
  fetchGrades()
}

// ---- 消息中心 ----
const messagesLoading = ref(false)
const messages = ref([])
const unreadCount = ref(0)
const msgPagination = reactive({ page: 1, pageSize: 10, total: 0 })

const messageTypeConfig = {
  NOTICE: { label: '班级通知', tag: 'primary' },
  GRADE: { label: '批改提醒', tag: 'success' },
  ANNOUNCEMENT: { label: '系统公告', tag: 'warning' },
  WARNING: { label: '学情预警', tag: 'danger' },
  SYSTEM: { label: '系统消息', tag: 'info' },
}

async function fetchMessages() {
  messagesLoading.value = true
  try {
    const res = await getMessages({ page: msgPagination.page, pageSize: msgPagination.pageSize })
    messages.value = res.data.records
    msgPagination.total = res.data.total
    unreadCount.value = res.data.unreadCount
  } finally {
    messagesLoading.value = false
  }
}

async function handleMarkRead(msg) {
  if (msg.isRead) return
  await markMessageRead(msg.messageId)
  msg.isRead = true
  unreadCount.value--
}

async function handleMarkAllRead() {
  await markAllMessagesRead()
  messages.value.forEach((m) => (m.isRead = true))
  unreadCount.value = 0
  ElMessage.success('已全部标记为已读')
}

function handleMsgPageChange(page) {
  msgPagination.page = page
  fetchMessages()
}

// ---- 初始化 ----
onMounted(async () => {
  // 先拉取最新的完整用户信息（login 返回的不含 status/email/phone）
  await userStore.fetchUserInfo()

  profileForm.realName = userStore.userInfo?.userName || ''
  profileForm.phone = userStore.userInfo?.phone || ''
  profileForm.email = userStore.userInfo?.email || ''

  // 支持从其他页面通过 query 跳转指定 tab
  const targetTab = route.query.tab
  if (targetTab && ['profile', 'password', 'grades', 'messages'].includes(targetTab)) {
    activeTab.value = targetTab
    if (targetTab === 'grades') fetchGrades()
    if (targetTab === 'messages') fetchMessages()
  }
})

// 同步 query.tab 到当前 activeTab（支持浏览器前进后退）
watch(() => route.query.tab, (newTab) => {
  if (newTab && ['profile', 'password', 'grades', 'messages'].includes(newTab)) {
    activeTab.value = newTab
    if (newTab === 'grades') fetchGrades()
    if (newTab === 'messages') fetchMessages()
  }
})

// 监听 tab 切换，按需加载数据 + 同步到 query
function handleTabChange(tab) {
  if (route.query.tab !== tab) {
    router.replace({ query: { ...route.query, tab } })
  }
  if (tab === 'grades') fetchGrades()
  if (tab === 'messages') fetchMessages()
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">个人中心</h1>
    </div>

    <div class="card-wrapper">
      <!-- 用户信息概览 -->
      <div class="user-banner">
        <el-avatar :size="72" :src="userStore.userInfo?.avatar" />
        <div class="user-banner-info">
          <h3>{{ userStore.userName }}</h3>
          <p>
            <el-tag size="small">{{ userStore.role === 'STUDENT' ? '学生' : userStore.role === 'TEACHER' ? '教师' : '管理员' }}</el-tag>
            <span v-if="userStore.userInfo?.department">{{ userStore.userInfo.department }}</span>
            <span v-if="userStore.userInfo?.className">{{ userStore.userInfo.className }}</span>
          </p>
        </div>
      </div>

      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="profile">
          <div class="profile-layout">
            <!-- 头像区 -->
            <div class="profile-avatar-section">
              <div class="avatar-block">
                <el-avatar :size="80" :src="userStore.userInfo?.avatar" />
                <el-upload
                  class="avatar-upload-btn"
                  :show-file-list="false"
                  :http-request="handleAvatarChange"
                  :before-upload="(f) => { const t = f.type; return t === 'image/jpeg' || t === 'image/png' }"
                  accept="image/jpeg,image/png"
                >
                  <span class="avatar-tip">点击更换头像</span>
                </el-upload>
              </div>
              <div class="avatar-right">
                <h3 class="profile-realname">{{ userStore.userInfo?.userName || '-' }}</h3>
                <el-tag size="small" :type="statusTagType[userStore.userInfo?.status] || 'success'">
                  {{ statusMap[userStore.userInfo?.status] || '正常' }}
                </el-tag>
              </div>
            </div>

            <!-- 分割线 -->
            <el-divider class="section-divider" />

            <!-- 账户信息（只读） -->
            <div class="info-section">
              <h4 class="section-title">账户信息</h4>
              <el-row :gutter="24">
                <el-col :span="12">
                  <div class="info-item">
                    <span class="info-label">学号</span>
                    <span class="info-value">{{ userStore.userInfo?.account || '-' }}</span>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="info-item">
                    <span class="info-label">最后登录</span>
                    <span class="info-value info-value-muted">{{ userStore.userInfo?.lastLoginTime || '-' }}</span>
                  </div>
                </el-col>
              </el-row>
            </div>

            <!-- 分割线 -->
            <el-divider class="section-divider" />

            <!-- 联系方式（可编辑） -->
            <div class="info-section">
              <h4 class="section-title">编辑联系方式</h4>
              <el-form
                ref="profileFormRef"
                :model="profileForm"
                :rules="profileRules"
                label-width="80px"
                class="profile-form"
              >
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="姓名" prop="realName">
                      <el-input v-model="profileForm.realName" placeholder="请输入姓名" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="手机号" prop="phone">
                      <el-input v-model="profileForm.phone" placeholder="请输入手机号" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="邮箱" prop="email">
                      <el-input v-model="profileForm.email" placeholder="请输入邮箱" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row>
                  <el-col :span="24">
                    <el-form-item>
                      <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存修改</el-button>
                      <el-button @click="handleResetProfile">重置</el-button>
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </div>
          </div>
        </el-tab-pane>

        <!-- 修改密码 -->
        <el-tab-pane label="修改密码" name="password">
          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-width="100px"
            style="max-width: 420px"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请确认新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="handleChangePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 成绩查询（仅学生） -->
        <el-tab-pane v-if="userStore.role === 'STUDENT'" label="成绩查询" name="grades">
          <el-table :data="grades" v-loading="gradesLoading" stripe style="width: 100%">
            <el-table-column prop="courseName" label="课程名称" min-width="150" />
            <el-table-column prop="semester" label="学期" width="120" />
            <el-table-column prop="teacherName" label="授课教师" width="100" />
            <el-table-column prop="usualGrade" label="平时成绩" width="90" />
            <el-table-column prop="examGrade" label="考试成绩" width="90" />
            <el-table-column prop="experimentGrade" label="实验成绩" width="90" />
            <el-table-column prop="finalGrade" label="总评成绩" width="90">
              <template #default="{ row }">
                <span :style="{ color: row.finalGrade >= 85 ? '#67c23a' : row.finalGrade >= 70 ? '#409eff' : '#f56c6c', fontWeight: 600 }">
                  {{ row.finalGrade ?? '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="credit" label="学分" width="60" />
            <el-table-column prop="gpa" label="绩点" width="60" />
          </el-table>
          <el-pagination
            v-if="gradesPagination.total > 0"
            v-model:current-page="gradesPagination.page"
            :page-size="gradesPagination.pageSize"
            :total="gradesPagination.total"
            layout="total, prev, pager, next"
            class="pagination"
            @current-change="handleGradesPageChange"
          />
        </el-tab-pane>

        <!-- 消息中心（仅学生） -->
        <el-tab-pane v-if="userStore.role === 'STUDENT'" name="messages">
          <template #label>
            <span>消息中心</span>
            <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="msg-badge" />
          </template>
          <div class="msg-actions">
            <el-button v-if="unreadCount > 0" type="primary" text size="small" @click="handleMarkAllRead">
              全部标记已读
            </el-button>
          </div>
          <div v-loading="messagesLoading">
            <div v-if="messages.length === 0" class="empty-state">
              <el-empty description="暂无消息" :image-size="80" />
            </div>
            <div v-else class="msg-list">
              <div
                v-for="msg in messages"
                :key="msg.messageId"
                class="msg-item"
                :class="{ unread: !msg.isRead }"
                @click="handleMarkRead(msg)"
              >
                <div class="msg-dot" v-if="!msg.isRead" />
                <el-tag size="small" :type="messageTypeConfig[msg.type]?.tag || 'info'" class="msg-type-tag">
                  {{ messageTypeConfig[msg.type]?.label || msg.type }}
                </el-tag>
                <div class="msg-body">
                  <div class="msg-title">{{ msg.title }}</div>
                  <div class="msg-content">{{ msg.content }}</div>
                  <div class="msg-time">{{ msg.sendTime }}</div>
                </div>
              </div>
            </div>
          </div>
          <el-pagination
            v-if="msgPagination.total > 0"
            v-model:current-page="msgPagination.page"
            :page-size="msgPagination.pageSize"
            :total="msgPagination.total"
            layout="total, prev, pager, next"
            class="pagination"
            @current-change="handleMsgPageChange"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.user-banner {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  border-radius: 8px;
  margin-bottom: 20px;
}

.user-banner-info h3 {
  font-size: 20px;
  margin-bottom: 8px;
}

.user-banner-info p {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #909399;
}

/* ========== 基本信息布局 ========== */
.profile-layout {
  max-width: 720px;
}

/* 头像区域 */
.profile-avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 0;
}

.avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.avatar-upload-btn {
  cursor: pointer;
}

.avatar-tip {
  font-size: 13px;
  color: #409eff;
  cursor: pointer;
  user-select: none;
}

.avatar-tip:hover {
  color: #1f6f4a;
  text-decoration: underline;
}

.avatar-right {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-realname {
  font-size: 22px;
  font-weight: 600;
  color: #1b2b22;
  margin: 0;
}

/* 分割线 */
.section-divider {
  margin: 20px 0;
}

/* 信息区域 */
.info-section {
  margin-bottom: 8px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 16px 0;
  padding-left: 10px;
  border-left: 3px solid #1f6f4a;
}

/* 信息项 */
.info-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 8px;
  background: #fafbfc;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  transition: background 0.2s;
}

.info-item:hover {
  background: #f5f7fa;
}

.info-label {
  font-size: 13px;
  color: #909399;
  min-width: 90px;
  flex-shrink: 0;
}

.info-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.info-value-muted {
  font-size: 13px;
  color: #909399;
  font-weight: 400;
}

/* 编辑表单 */
.profile-form {
  margin-top: 4px;
}

.profile-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.msg-badge {
  margin-left: 4px;
}

.msg-actions {
  text-align: right;
  margin-bottom: 8px;
}

.msg-list {
  display: flex;
  flex-direction: column;
}

.msg-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid #f2f2f2;
  cursor: pointer;
  transition: background 0.15s;
}

.msg-item:hover {
  background: #f5f7fa;
}

.msg-item.unread {
  background: #ecf5ff;
}

.msg-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
  margin-top: 6px;
  flex-shrink: 0;
}

.msg-type-tag {
  flex-shrink: 0;
  margin-top: 2px;
}

.msg-body {
  flex: 1;
}

.msg-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.msg-content {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-time {
  font-size: 12px;
  color: #c0c4cc;
}

.empty-state {
  padding: 20px 0;
}
</style>
