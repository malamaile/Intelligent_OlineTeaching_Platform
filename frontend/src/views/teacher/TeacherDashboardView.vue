<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { getTeacherDashboard } from '@/api/teacher'

const router = useRouter()

const loading = ref(true)
const dashboard = ref({
  pendingReviews: { experiments: 0, trainings: 0, total: 0 },
  pendingReviewItems: [],
  pendingAudits: { courses: 0, tasks: 0, resources: 0, total: 0 },
  atRiskStudents: [],
  notifications: [],
  classSummary: { totalStudents: 0, avgCompletionRate: 0, avgScore: 0 },
})

// 待办事项默认打开「待批改报告」
const activeTodoTab = ref('reviews')

async function fetchDashboard() {
  loading.value = true
  try {
    const res = await getTeacherDashboard()
    dashboard.value = res.data
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)

// 跳转到批阅界面
function goToReview(taskId) {
  router.push({ path: '/teacher/tasks', query: { taskId } })
}
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">工作台</h1>
      <p class="page-desc">待办工作、通知预警、班级教学动态统一汇总</p>
    </div>

    <!-- ====== L1：KPI 统计卡片 ====== -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-danger">
          <div class="stat-label">待批改报告</div>
          <div class="stat-value">{{ dashboard.pendingReviews.total }}<small> 份</small></div>
          <div class="stat-detail">实验 {{ dashboard.pendingReviews.experiments }} · 实训 {{ dashboard.pendingReviews.trainings }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-warning">
          <div class="stat-label">待审核内容</div>
          <div class="stat-value">{{ dashboard.pendingAudits.total }}<small> 项</small></div>
          <div class="stat-detail">课程 {{ dashboard.pendingAudits.courses }} · 任务 {{ dashboard.pendingAudits.tasks }} · 资源 {{ dashboard.pendingAudits.resources }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-info">
          <div class="stat-label">班级人数</div>
          <div class="stat-value">{{ dashboard.classSummary.totalStudents }}<small> 人</small></div>
          <div class="stat-detail">平均完成率 {{ dashboard.classSummary.avgCompletionRate }}%</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-success">
          <div class="stat-label">班级平均分</div>
          <div class="stat-value">{{ dashboard.classSummary.avgScore }}<small> 分</small></div>
          <div class="stat-detail">当前学期</div>
        </div>
      </el-col>
    </el-row>

    <!-- ====== L2：待办 · 预警 · 通知 三栏并排，顶端对齐 ====== -->
    <el-row :gutter="16" class="dashboard-row">
      <!-- 待办事项 -->
      <el-col :xs="24" :lg="8">
        <div class="card-panel">
          <div class="card-title">待办事项</div>
          <el-tabs v-model="activeTodoTab" class="todo-tabs">
            <el-tab-pane label="待批改报告" name="reviews">
              <el-empty v-if="!dashboard.pendingReviewItems.length" description="暂无待批改报告" :image-size="60" />
              <div v-else class="pending-review-list">
                <div
                  v-for="item in dashboard.pendingReviewItems"
                  :key="item.taskId"
                  class="review-card"
                  @click="goToReview(item.taskId)"
                >
                  <div class="review-card-header">
                    <el-tag :type="item.taskType === 'EXPERIMENT' ? 'success' : 'warning'" size="small" effect="dark">
                      {{ item.taskType === 'EXPERIMENT' ? '实验' : '实训' }}
                    </el-tag>
                    <span class="review-card-title">{{ item.taskTitle }}</span>
                  </div>
                  <div class="review-card-body">
                    <span class="review-card-course">{{ item.courseName }}</span>
                    <span class="review-card-class">{{ item.className }}</span>
                  </div>
                  <div class="review-card-footer">
                    <span class="review-card-count">
                      <el-badge :value="item.pendingCount" type="danger" />
                      <span class="count-label">份待批阅</span>
                    </span>
                    <span class="review-card-student" v-if="item.sampleStudentName">
                      如：{{ item.sampleStudentName }}{{ item.pendingCount > 1 ? ' 等' : '' }}
                    </span>
                    <el-icon class="review-card-arrow"><ArrowRight /></el-icon>
                  </div>
                </div>
                <div class="review-all-action">
                  <el-button type="primary" size="small" @click="router.push('/teacher/tasks')">查看全部任务</el-button>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="待审核" name="audits">
              <div v-if="dashboard.pendingAudits.total" class="pending-audit-list">
                <div v-if="dashboard.pendingAudits.courses" class="audit-row">
                  <el-tag type="warning" size="small">课程</el-tag>
                  <span>{{ dashboard.pendingAudits.courses }} 条开课申请审核中</span>
                </div>
                <div v-if="dashboard.pendingAudits.tasks" class="audit-row">
                  <el-tag type="warning" size="small">任务</el-tag>
                  <span>{{ dashboard.pendingAudits.tasks }} 条任务计划审核中</span>
                </div>
                <div v-if="dashboard.pendingAudits.resources" class="audit-row">
                  <el-tag type="warning" size="small">资源</el-tag>
                  <span>{{ dashboard.pendingAudits.resources }} 条资源审核中</span>
                </div>
              </div>
              <el-empty v-else description="暂无待审核内容" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-col>

      <!-- 预警学生 -->
      <el-col :xs="24" :lg="8">
        <div class="card-panel">
          <div class="card-title">
            预警学生
            <el-button type="danger" text size="small" class="card-title-btn" @click="router.push('/teacher/class-analytics')">查看全部</el-button>
          </div>
          <div class="risk-list">
            <div v-if="!dashboard.atRiskStudents.length" class="empty-state">
              <el-empty description="暂无预警" :image-size="60" />
            </div>
            <div v-for="stu in dashboard.atRiskStudents" :key="stu.userId" class="risk-item">
              <el-avatar :size="32" icon="UserFilled" />
              <div class="risk-body">
                <div class="risk-name">{{ stu.userName }} <span class="risk-class">{{ stu.className }}</span></div>
                <div class="risk-reason">{{ stu.reason }}</div>
              </div>
              <el-tag type="danger" size="small">预警</el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 最新通知 -->
      <el-col :xs="24" :lg="8">
        <div class="card-panel">
          <div class="card-title">最新通知</div>
          <div v-if="!dashboard.notifications.length" class="empty-state">
            <el-empty description="暂无通知" :image-size="60" />
          </div>
          <div v-for="n in dashboard.notifications" :key="n.id" class="notify-row">
            <el-tag :type="n.type === 'AUDIT_RESULT' ? 'success' : 'info'" size="small" effect="plain">
              {{ n.type === 'AUDIT_RESULT' ? '审批' : '系统' }}
            </el-tag>
            <span class="notify-title">{{ n.title }}</span>
            <span class="notify-time">{{ n.publishTime }}</span>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-desc { font-size: 13px; color: #909399; margin-top: 4px; }

/* ====== L1 KPI 卡片 ====== */
.kpi-row { margin-bottom: 20px; }

/* ====== L2 三栏面板 ====== */
.dashboard-row { align-items: stretch; }
.card-panel {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,.06);
  height: 100%;
  display: flex;
  flex-direction: column;
}
.card-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.card-title-btn { float: right; }

/* ====== 待办事项 ====== */
.todo-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }

/* ====== 待批阅列表 ====== */
.pending-review-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 400px;
  overflow-y: auto;
}
.review-card {
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.review-card:hover {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}
.review-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.review-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-card-body {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.review-card-course { color: #606266; }
.review-card-class { color: #909399; }
.review-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}
.review-card-count {
  display: flex;
  align-items: center;
  gap: 4px;
}
.count-label {
  color: #606266;
  font-size: 12px;
}
.review-card-student {
  flex: 1;
  margin-left: 8px;
  color: #c0c4cc;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-card-arrow {
  color: #c0c4cc;
  font-size: 14px;
  transition: color 0.2s;
}
.review-card:hover .review-card-arrow {
  color: #409eff;
}
.review-all-action {
  text-align: center;
  padding-top: 4px;
}

.pending-audit-list { display: flex; flex-direction: column; gap: 10px; padding: 4px 0; }
.audit-row { display: flex; align-items: center; gap: 10px; font-size: 13px; color: #606266; }

/* ====== 预警学生 ====== */
.risk-list { flex: 1; overflow-y: auto; }
.risk-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; }
.risk-item:last-child { border-bottom: none; }
.risk-body { flex: 1; min-width: 0; }
.risk-name { font-size: 14px; font-weight: 500; }
.risk-class { font-size: 12px; color: #909399; font-weight: normal; margin-left: 4px; }
.risk-reason { font-size: 12px; color: #f56c6c; margin-top: 2px; }

/* ====== 最新通知 ====== */
.notify-row { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
.notify-row:last-child { border-bottom: none; }
.notify-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
.notify-time { font-size: 12px; color: #c0c4cc; white-space: nowrap; }

/* ====== 通用 ====== */
.empty-state { padding: 10px 0; }
.stat-detail { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
