<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/student'

const router = useRouter()

const loading = ref(true)
const dashboardData = ref({
  todoList: [],
  notifications: [],
  todayStats: { studyDuration: 0, completedCourses: 0, pendingTasks: 0 },
})

// 代办类型配置
const todoTypeConfig = {
  COURSE: { label: '课程学习', color: '#409eff', icon: 'Reading' },
  EXPERIMENT: { label: '实验任务', color: '#5EEAD4', icon: 'Document' },
  TRAINING: { label: '实训任务', color: '#F28C28', icon: 'Edit' },
}

// 通知类型配置
const notifyTypeConfig = {
  ANNOUNCEMENT: { label: '全校公告', color: '#409eff' },
  CLASS_NOTICE: { label: '班级通知', color: '#00D048' },
  MESSAGE: { label: '个人消息', color: '#e6a23c' },
}

// 通知详情弹窗
const notifyDetailVisible = ref(false)
const currentNotify = ref(null)

async function fetchDashboard() {
  loading.value = true
  try {
    const res = await getDashboard()
    dashboardData.value = res.data
  } finally {
    loading.value = false
  }
}

function goTo(linkUrl) {
  if (linkUrl) router.push(linkUrl)
}

// 点击通知弹出详情
function openNotifyDetail(item) {
  currentNotify.value = item
  notifyDetailVisible.value = true
}

// 跳转消息中心（通过 query 参数指定初始 tab）
function goToMessages() {
  router.push({ path: '/user-center', query: { tab: 'messages' } })
}

onMounted(fetchDashboard)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">门户首页</h1>
    </div>

    <!-- ====== L1：KPI 统计卡片（24栅格） ====== -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="24" :sm="12" :lg="8">
        <div class="card-l1 accent-primary">
          <div class="stat-label">今日学习时长</div>
          <div class="stat-value">{{ dashboardData.todayStats.studyDuration }}<small> 分钟</small></div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <div class="card-l1 accent-success">
          <div class="stat-label">本周完成课程</div>
          <div class="stat-value">{{ dashboardData.todayStats.completedCourses }}<small> 门</small></div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="8">
        <div class="card-l1 accent-warning kpi-clickable" @click="router.push('/tasks')">
          <div class="stat-label">待完成任务</div>
          <div class="stat-value">{{ dashboardData.todayStats.pendingTasks }}<small> 项</small></div>
        </div>
      </el-col>
    </el-row>

    <!-- ====== L2 + L3：待办提醒 / 通知公告（24栅格） ====== -->
    <el-row :gutter="16">
      <!-- L2：待办提醒 -->
      <el-col :xs="24" :md="15" :lg="16">
        <div class="card-l2">
          <div class="card-title">待办提醒</div>
          <div v-if="dashboardData.todoList.length === 0" class="empty-state">
            <el-empty description="暂无待办任务" :image-size="80" />
          </div>
          <div v-else class="todo-list">
            <div
              v-for="(item, idx) in dashboardData.todoList"
              :key="idx"
              class="todo-item"
              @click="goTo(item.linkUrl)"
            >
              <el-tag
                :color="todoTypeConfig[item.type]?.color"
                size="small"
                effect="dark"
                class="todo-tag"
              >
                {{ todoTypeConfig[item.type]?.label || item.type }}
              </el-tag>
              <div class="todo-body">
                <div class="todo-title">{{ item.title }}</div>
                <div class="todo-meta">
                  <span>{{ item.courseName }}</span>
                  <span class="todo-deadline">截止：{{ item.deadline }}</span>
                </div>
              </div>
              <el-icon class="todo-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </el-col>

      <!-- L3：通知公告 -->
      <el-col :xs="24" :md="9" :lg="8">
        <div class="card-l3">
          <div class="card-title">
            通知公告
            <el-button type="primary" text size="small" style="float:right" @click="goToMessages">
              查看全部 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div v-if="dashboardData.notifications.length === 0" class="empty-state">
            <el-empty description="暂无通知" :image-size="80" />
          </div>
          <div v-else class="notify-list">
            <div
              v-for="(item, idx) in dashboardData.notifications"
              :key="idx"
              class="notify-item"
              @click="openNotifyDetail(item)"
            >
              <el-tag
                :type="item.type === 'ANNOUNCEMENT' ? '' : 'success'"
                size="small"
                effect="plain"
              >
                {{ notifyTypeConfig[item.type]?.label || item.type }}
              </el-tag>
              <div class="notify-body">
                <div class="notify-title">{{ item.title }}</div>
                <div class="notify-meta">
                  <span v-if="item.publisher">{{ item.publisher }}</span>
                  <span>{{ item.publishTime }}</span>
                </div>
              </div>
              <el-icon class="notify-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 通知详情弹窗 -->
    <el-dialog v-model="notifyDetailVisible" title="通知详情" width="560px" destroy-on-close>
      <template v-if="currentNotify">
        <div class="notify-detail-header">
          <el-tag
            :type="currentNotify.type === 'ANNOUNCEMENT' ? '' : 'success'"
            size="small"
            effect="plain"
          >
            {{ notifyTypeConfig[currentNotify.type]?.label || currentNotify.type }}
          </el-tag>
          <span class="notify-detail-time">{{ currentNotify.publishTime }}</span>
        </div>
        <h3 class="notify-detail-title">{{ currentNotify.title }}</h3>
        <div v-if="currentNotify.publisher" class="notify-detail-publisher">
          发布人：{{ currentNotify.publisher }}
        </div>
        <el-divider />
        <div class="notify-detail-content">
          {{ currentNotify.content || '暂无详细内容' }}
        </div>
      </template>
      <template #footer>
        <el-button @click="notifyDetailVisible = false">关闭</el-button>
        <el-button type="primary" @click="notifyDetailVisible = false; goToMessages()">查看全部通知</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
/* KPI row spacing */
.kpi-row {
  margin-bottom: 16px;
}

.kpi-clickable {
  cursor: pointer;
}

.todo-list,
.notify-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.todo-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.todo-tag {
  flex-shrink: 0;
}

.todo-body {
  flex: 1;
  min-width: 0;
}

.todo-title {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
}

.todo-deadline {
  color: #f56c6c;
}

.todo-arrow {
  color: #c0c4cc;
  flex-shrink: 0;
}

.notify-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f2f2;
  cursor: pointer;
  transition: background 0.15s;
}

.notify-item:hover {
  background: #f5f7fa;
  margin: 0 -12px;
  padding: 10px 12px;
  border-radius: 4px;
}

.notify-item:last-child {
  border-bottom: none;
}

.notify-body {
  flex: 1;
}

.notify-title {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.notify-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
}

.notify-arrow {
  color: #c0c4cc;
  flex-shrink: 0;
  margin-top: 4px;
}

/* 通知详情弹窗 */
.notify-detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.notify-detail-time {
  font-size: 13px;
  color: #909399;
}

.notify-detail-title {
  font-size: 18px;
  color: #303133;
  margin: 8px 0;
}

.notify-detail-publisher {
  font-size: 13px;
  color: #606266;
}

.notify-detail-content {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-line;
  max-height: 360px;
  overflow-y: auto;
}

.empty-state {
  padding: 20px 0;
}
</style>
