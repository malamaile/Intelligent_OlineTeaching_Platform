<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getAdminOverview, getCourseAuditStatistics, getTaskAuditStatistics, getResourceAuditStatistics, getAuditCourseLogs } from '@/api/admin'

use([CanvasRenderer, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const loading = ref(true)

// 核心统计
const stats = ref({
  totalUsers: 0, activeStudents: 0, teachers: 0, admins: 0,
  frozenAccounts: 0, totalCourses: 0, pendingAudits: 0, totalResources: 0,
})

// 审核统计
const auditSummary = ref({
  pendingCourses: 0, pendingTasks: 0, pendingResources: 0,
  todayApproved: 0, todayRejected: 0, thisWeekTotal: 0,
})

// 审核趋势图
const auditTrendOption = ref({
  tooltip: { trigger: 'axis' },
  legend: { data: ['提交', '通过', '驳回'], bottom: 0 },
  grid: { left: 45, right: 15, top: 15, bottom: 60 },
  xAxis: { type: 'category', data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
  yAxis: { type: 'value' },
  series: [
    { name: '提交', type: 'line', smooth: true, da哪怕，ta: [0, 0, 0, 0, 0, 0, 0], areaStyle: { opacity: 0.1 } },
    { name: '通过', type: 'line', smooth: true, data: [0, 0, 0, 0, 0, 0, 0], lineStyle: { color: '#67c23a' }, itemStyle: { color: '#67c23a' } },
    { name: '驳回', type: 'line', smooth: true, data: [0, 0, 0, 0, 0, 0, 0], lineStyle: { color: '#f56c6c' }, itemStyle: { color: '#f56c6c' } },
  ],
})

// 用户角色分布饼图
const userPieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie', radius: ['50%', '72%'], center: ['50%', '45%'],
    data: [
      { value: stats.value.activeStudents || 0, name: '学生', itemStyle: { color: '#409eff' } },
      { value: stats.value.teachers || 0, name: '教师', itemStyle: { color: '#67c23a' } },
      { value: stats.value.admins || 0, name: '管理员', itemStyle: { color: '#e6a23c' } },
    ],
  }],
}))

// 最近操作日志
const recentLogs = ref([])

const bizTypeConfig = {
  COURSE_PLAN: { label: '课程', color: '#409eff' },
  EXPERIMENT: { label: '实训', color: '#67c23a' },
  RESOURCE: { label: '资源', color: '#e6a23c' },
  USER: { label: '用户', color: '#f56c6c' },
  SYSTEM: { label: '系统', color: '#909399' },
}

async function fetchDashboard() {
  loading.value = true
  try {
    const [overviewRes, courseStatsRes, taskStatsRes, resourceStatsRes, logsRes] = await Promise.all([
      getAdminOverview(),
      getCourseAuditStatistics(),
      getTaskAuditStatistics(),
      getResourceAuditStatistics(),
      getAuditCourseLogs({ page: 1, pageSize: 5 }),
    ])
    if (overviewRes.data) stats.value = { ...stats.value, ...overviewRes.data }
    if (courseStatsRes.data) {
      auditSummary.value.pendingCourses = courseStatsRes.data.pending || 0
      auditSummary.value.todayApproved = (auditSummary.value.todayApproved || 0) + (courseStatsRes.data.todayApproved || 0)
    }
    if (taskStatsRes.data) {
      auditSummary.value.pendingTasks = taskStatsRes.data.pending || 0
    }
    if (resourceStatsRes.data) {
      auditSummary.value.pendingResources = resourceStatsRes.data.pending || 0
    }
    auditSummary.value.pendingAudits = auditSummary.value.pendingCourses + auditSummary.value.pendingTasks + auditSummary.value.pendingResources
    if (logsRes.data) {
      recentLogs.value = logsRes.data.records || logsRes.data || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">管理员工作台</h1>
      <p class="page-desc">平台核心数据总览，快速掌握系统运行状态</p>
    </div>

    <!-- ====== L1：KPI 统计卡片（24栅格） ====== -->
    <el-row :gutter="16" class="kpi-row">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-info">
          <div class="stat-label">用户总数</div>
          <div class="stat-value">{{ stats.totalUsers }}<small> 人</small></div>
          <div class="stat-detail">学生 {{ stats.activeStudents }} · 教师 {{ stats.teachers }} · 管理员 {{ stats.admins }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-warning">
          <div class="stat-label">待审核</div>
          <div class="stat-value">{{ stats.pendingAudits || auditSummary.pendingAudits }}<small> 项</small></div>
          <div class="stat-detail">课程 {{ auditSummary.pendingCourses }} · 实训 {{ auditSummary.pendingTasks }} · 资源 {{ auditSummary.pendingResources }}</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-success">
          <div class="stat-label">课程总数</div>
          <div class="stat-value">{{ stats.totalCourses }}<small> 门</small></div>
          <div class="stat-detail">教学资源 {{ stats.totalResources }} 份</div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-danger">
          <div class="stat-label">冻结账号</div>
          <div class="stat-value">{{ stats.frozenAccounts }}<small> 个</small></div>
          <div class="stat-detail">需关注处理</div>
        </div>
      </el-col>
    </el-row>

    <!-- ====== L2：图表区域（24栅格） ====== -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <div class="card-l2">
          <div class="card-title">本周审核趋势</div>
          <VChart :option="auditTrendOption" style="height:260px" autoresize />
        </div>
      </el-col>
      <el-col :xs="24" :lg="9">
        <div class="card-l2">
          <div class="card-title">用户角色分布</div>
          <VChart :option="userPieOption" style="height:180px" autoresize />
        </div>
        <div class="card-l2">
          <div class="card-title">快捷操作</div>
          <div class="quick-actions">
            <el-button type="primary" @click="router.push('/admin/audit')">
              <el-icon><Checked /></el-icon>内容审核
              <el-badge :value="stats.pendingAudits || auditSummary.pendingAudits" type="danger" style="margin-left:4px" />
            </el-button>
            <el-button type="success" @click="router.push('/admin/users')">
              <el-icon><UserFilled /></el-icon>用户管理
            </el-button>
            <el-button type="warning" @click="router.push('/admin/announcements')">
              <el-icon><Notification /></el-icon>发布公告
            </el-button>
            <el-button type="info" @click="router.push('/admin/settings')">
              <el-icon><Setting /></el-icon>系统设置
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ====== L3：审核统计 + 操作日志（24栅格） ====== -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <div class="card-l3">
          <div class="card-title">审核统计</div>
          <el-row :gutter="12">
            <el-col :span="8">
              <div class="audit-stat-item">
                <div class="audit-stat-num primary">{{ auditSummary.todayApproved }}</div>
                <div class="audit-stat-label">今日通过</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="audit-stat-item">
                <div class="audit-stat-num danger">{{ auditSummary.todayRejected }}</div>
                <div class="audit-stat-label">今日驳回</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="audit-stat-item">
                <div class="audit-stat-num success">{{ auditSummary.thisWeekTotal }}</div>
                <div class="audit-stat-label">本周处理</div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
          <div class="audit-breakdown">
            <div class="breakdown-row" @click="router.push('/admin/audit')">
              <span>课程开课计划审核</span>
              <el-tag :type="auditSummary.pendingCourses > 0 ? 'warning' : 'success'" size="small">
                {{ auditSummary.pendingCourses > 0 ? auditSummary.pendingCourses + '条待审' : '无待审' }}
              </el-tag>
              <el-icon><ArrowRight /></el-icon>
            </div>
            <div class="breakdown-row" @click="router.push('/admin/audit')">
              <span>实训实验计划审核</span>
              <el-tag :type="auditSummary.pendingTasks > 0 ? 'warning' : 'success'" size="small">
                {{ auditSummary.pendingTasks > 0 ? auditSummary.pendingTasks + '条待审' : '无待审' }}
              </el-tag>
              <el-icon><ArrowRight /></el-icon>
            </div>
            <div class="breakdown-row" @click="router.push('/admin/audit')">
              <span>教学资源审核</span>
              <el-tag :type="auditSummary.pendingResources > 0 ? 'warning' : 'success'" size="small">
                {{ auditSummary.pendingResources > 0 ? auditSummary.pendingResources + '条待审' : '无待审' }}
              </el-tag>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :lg="12">
        <div class="card-l3">
          <div class="card-title">最近操作记录</div>
          <div class="log-list">
            <div v-if="!recentLogs.length" style="padding:20px;text-align:center;color:#909399">暂无操作记录</div>
            <div v-for="(log, idx) in recentLogs" :key="idx" class="log-item">
              <el-tag :color="bizTypeConfig[log.bizType]?.color" size="small" effect="dark">
                {{ bizTypeConfig[log.bizType]?.label || log.bizType }}
              </el-tag>
              <div class="log-body">
                <span class="log-operator">{{ log.operator || log.auditor }}</span>
                <span class="log-action">{{ log.action || log.comment }}</span>
              </div>
              <div class="log-right">
                <el-tag v-if="log.result === '通过' || log.action === 'APPROVE'" type="success" size="small" effect="plain">通过</el-tag>
                <el-tag v-else-if="log.result === '驳回' || log.action === 'REJECT'" type="danger" size="small" effect="plain">驳回</el-tag>
                <span v-else class="log-result">{{ log.result }}</span>
                <span class="log-time">{{ log.time }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-desc { font-size: 13px; color: #909399; margin-top: 4px; }
.stat-detail { font-size: 12px; color: #909399; margin-top: 4px; }
.quick-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.audit-stat-item { text-align: center; padding: 8px; }
.audit-stat-num { font-size: 28px; font-weight: 700; }
.audit-stat-num.primary { color: #409eff; }
.audit-stat-num.success { color: #67c23a; }
.audit-stat-num.danger { color: #f56c6c; }
.audit-stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
.audit-breakdown { display: flex; flex-direction: column; gap: 4px; }
.breakdown-row { display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 6px; cursor: pointer; font-size: 13px; transition: background 0.15s; }
.breakdown-row:hover { background: #f5f7fa; }
.breakdown-row span:first-child { flex: 1; color: #303133; }
.log-list { display: flex; flex-direction: column; max-height: 260px; overflow-y: auto; }
.log-item { display: flex; align-items: center; gap: 8px; padding: 9px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
.log-body { flex: 1; min-width: 0; }
.log-operator { font-weight: 500; color: #303133; margin-right: 6px; }
.log-action { color: #606266; }
.log-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.log-result { font-size: 12px; color: #909399; }
.log-time { font-size: 12px; color: #c0c4cc; }
</style>
