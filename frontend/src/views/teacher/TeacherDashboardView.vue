<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getTeacherDashboard } from '@/api/teacher'

const router = useRouter()

const loading = ref(true)
const dashboard = ref({
  pendingReviews: { experiments: 0, trainings: 0, total: 0 },
  pendingAudits: { courses: 0, tasks: 0, resources: 0, total: 0 },
  atRiskStudents: [],
  notifications: [],
  classSummary: { totalStudents: 0, avgCompletionRate: 0, avgScore: 0 },
})

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
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">工作台</h1>
      <p class="page-desc">待办工作、通知预警、班级教学动态统一汇总</p>
    </div>

    <!-- ====== L1：KPI 统计卡片（24栅格） ====== -->
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

    <!-- ====== L2 + L3：待办 / 预警 & 通知（24栅格） ====== -->
    <el-row :gutter="16">
      <!-- L2：待办事项 -->
      <el-col :xs="24" :lg="16">
        <div class="card-l2">
          <div class="card-title">待办事项</div>
          <el-tabs>
            <el-tab-pane label="待批改报告" :name="0">
              <div class="todo-actions">
                <el-button type="primary" size="small" @click="router.push('/teacher/tasks')">实验实训批阅</el-button>
              </div>
              <el-empty v-if="!dashboard.pendingReviews.total" description="暂无待批改报告" :image-size="60" />
              <div v-else class="todo-hint">
                <el-icon><Warning /></el-icon>
                您有 <strong>{{ dashboard.pendingReviews.total }}</strong> 份学生报告待批改，请及时处理
              </div>
            </el-tab-pane>
            <el-tab-pane label="待审核" :name="1">
              <div v-if="dashboard.pendingAudits.total" class="pending-audit-list">
                <div v-if="dashboard.pendingAudits.courses" class="audit-row">
                  <el-tag type="warning">课程计划</el-tag>
                  <span>{{ dashboard.pendingAudits.courses }} 条开课申请审核中</span>
                </div>
                <div v-if="dashboard.pendingAudits.tasks" class="audit-row">
                  <el-tag type="warning">实验实训</el-tag>
                  <span>{{ dashboard.pendingAudits.tasks }} 条任务计划审核中</span>
                </div>
                <div v-if="dashboard.pendingAudits.resources" class="audit-row">
                  <el-tag type="warning">教学资源</el-tag>
                  <span>{{ dashboard.pendingAudits.resources }} 条资源审核中</span>
                </div>
              </div>
              <el-empty v-else description="暂无待审核内容" :image-size="60" />
            </el-tab-pane>
          </el-tabs>
        </div>
      </el-col>

      <!-- L3：预警学生 + 最新通知 -->
      <el-col :xs="24" :lg="8">
        <div class="card-l3">
          <div class="card-title">
            预警学生
            <el-button type="danger" text size="small" style="float:right" @click="router.push('/teacher/class-analytics')">查看全部</el-button>
          </div>
          <div v-if="!dashboard.atRiskStudents.length" class="empty-state">
            <el-empty description="暂无预警" :image-size="60" />
          </div>
          <div v-else class="risk-list">
            <div v-for="stu in dashboard.atRiskStudents" :key="stu.userId" class="risk-item">
              <el-avatar :size="32" icon="User" />
              <div class="risk-body">
                <div class="risk-name">{{ stu.userName }} <span class="risk-class">{{ stu.className }}</span></div>
                <div class="risk-reason">{{ stu.reason }}（缺交 {{ stu.missedTasks }} 次）</div>
              </div>
              <el-tag type="danger" size="small">预警</el-tag>
            </div>
          </div>
        </div>

        <div class="card-l3">
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
.stat-detail { font-size: 12px; color: #909399; margin-top: 4px; }
.todo-actions { margin-bottom: 12px; }
.todo-hint { padding: 16px; background: #fef0f0; border-radius: 8px; color: #f56c6c; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.pending-audit-list { display: flex; flex-direction: column; gap: 10px; padding: 8px 0; }
.audit-row { display: flex; align-items: center; gap: 10px; font-size: 14px; color: #606266; }
.risk-list { display: flex; flex-direction: column; gap: 10px; }
.risk-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; }
.risk-body { flex: 1; }
.risk-name { font-size: 14px; font-weight: 500; }
.risk-class { font-size: 12px; color: #909399; font-weight: normal; }
.risk-reason { font-size: 12px; color: #f56c6c; }
.notify-row { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
.notify-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notify-time { font-size: 12px; color: #c0c4cc; white-space: nowrap; }
.empty-state { padding: 10px 0; }
</style>
