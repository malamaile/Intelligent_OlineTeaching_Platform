<script setup>
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getAdminOverview, getCourseAuditStatistics, getTaskAuditStatistics, getResourceAuditStatistics, getAuditCourseLogs } from '@/api/admin'
import { getDepartments, getClasses } from '@/api/common'
import { createDepartment } from '@/api/admin'

use([CanvasRenderer, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const loading = ref(true)

// 核心统计
const stats = ref({
  totalUsers: 0, activeStudents: 0, teachers: 0, admins: 0,
  frozenAccounts: 0, totalCourses: 0, pendingAudits: 0, totalResources: 0,
  deptCount: 0, classCount: 0,
})

// 学院/班级列表弹窗
const listDialogVisible = ref(false)
const listDialogTitle = ref('')
const listType = ref('') // 'dept' | 'class'
const listData = ref([])
const listLoading = ref(false)
const classDeptFilter = ref('')

async function showDeptList() {
  listType.value = 'dept'
  listDialogTitle.value = '学院列表'
  listLoading.value = true
  try {
    const res = await getDepartments()
    listData.value = res.data || []
  } finally { listLoading.value = false }
  listDialogVisible.value = true
}

async function showClassList(deptId) {
  listType.value = 'class'
  listDialogTitle.value = '班级列表'
  listLoading.value = true
  classDeptFilter.value = deptId ? String(deptId) : ''
  try {
    const res = await getClasses({ departmentId: deptId || undefined })
    listData.value = res.data || []
  } finally { listLoading.value = false }
  listDialogVisible.value = true
}

async function onClassDeptFilterChange(val) {
  listLoading.value = true
  try {
    const res = await getClasses({ departmentId: val || undefined })
    listData.value = res.data || []
  } finally { listLoading.value = false }
}

// 新建学院
const deptDialogVisible = ref(false)
const deptFormRef = ref(null)
const deptForm = reactive({ deptName: '', deptCode: '' })
const deptSaving = ref(false)
const deptRules = { deptName: [{ required: true, message: '请输入学院名称', trigger: 'blur' }] }

function openCreateDept() {
  Object.assign(deptForm, { deptName: '', deptCode: '' })
  deptDialogVisible.value = true
}

async function handleCreateDept() {
  const valid = await deptFormRef.value.validate().catch(() => false)
  if (!valid) return
  deptSaving.value = true
  try {
    await createDepartment({ deptName: deptForm.deptName, deptCode: deptForm.deptCode || undefined })
    ElMessage.success('学院创建成功')
    deptDialogVisible.value = false
    await fetchDepartments()
    await fetchDashboard()
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  } finally { deptSaving.value = false }
}

// 审核统计
const auditSummary = ref({
  pendingCourses: 0, pendingTasks: 0, pendingResources: 0,
  todayApproved: 0, todayRejected: 0, thisWeekTotal: 0,
})

// 审核趋势图（从后端 weeklyAuditTrend 读取本周每日提交/通过/驳回数据）
const auditTrendOption = computed(() => {
  const trend = stats.value.weeklyAuditTrend || []
  const days = trend.map(d => d.day || '')
  const submitted = trend.map(d => Number(d.submitted) || 0)
  const approved = trend.map(d => Number(d.approved) || 0)
  const rejected = trend.map(d => Number(d.rejected) || 0)
  const hasData = days.length > 0
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['提交', '通过', '驳回'], bottom: 0 },
    grid: { left: 45, right: 15, top: 15, bottom: 60 },
    xAxis: { type: 'category', data: hasData ? days : ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] },
    yAxis: { type: 'value' },
    series: [
      { name: '提交', type: 'line', smooth: true, data: hasData ? submitted : [0, 0, 0, 0, 0, 0, 0], areaStyle: { opacity: 0.1 } },
      { name: '通过', type: 'line', smooth: true, data: hasData ? approved : [0, 0, 0, 0, 0, 0, 0], lineStyle: { color: '#67c23a' }, itemStyle: { color: '#67c23a' } },
      { name: '驳回', type: 'line', smooth: true, data: hasData ? rejected : [0, 0, 0, 0, 0, 0, 0], lineStyle: { color: '#f56c6c' }, itemStyle: { color: '#f56c6c' } },
    ],
  }
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

// 院系列表（弹窗筛选用）
const departmentOptions = ref([])
async function fetchDepartments() {
  try {
    const res = await getDepartments()
    departmentOptions.value = res.data || []
  } catch {}
}

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

    // 核心统计数据（来自 /admin/analytics/overview）
    if (overviewRes.data) {
      stats.value = { ...stats.value, ...overviewRes.data }
    }

    // 审核汇总（聚合三个模块的统计数据）
    let todayApproved = 0
    let todayRejected = 0
    let thisWeekTotal = 0

    if (courseStatsRes.data) {
      auditSummary.value.pendingCourses = courseStatsRes.data.pending || 0
      todayApproved += courseStatsRes.data.todayApproved || 0
      todayRejected += courseStatsRes.data.todayRejected || 0
      thisWeekTotal += courseStatsRes.data.thisWeekTotal || 0
    }
    if (taskStatsRes.data) {
      auditSummary.value.pendingTasks = taskStatsRes.data.pending || 0
      todayApproved += taskStatsRes.data.todayApproved || 0
      todayRejected += taskStatsRes.data.todayRejected || 0
      thisWeekTotal += taskStatsRes.data.thisWeekTotal || 0
    }
    if (resourceStatsRes.data) {
      auditSummary.value.pendingResources = resourceStatsRes.data.pending || 0
      todayApproved += resourceStatsRes.data.todayApproved || 0
      todayRejected += resourceStatsRes.data.todayRejected || 0
      thisWeekTotal += resourceStatsRes.data.thisWeekTotal || 0
    }

    auditSummary.value.todayApproved = todayApproved
    auditSummary.value.todayRejected = todayRejected
    auditSummary.value.thisWeekTotal = thisWeekTotal
    auditSummary.value.pendingAudits = auditSummary.value.pendingCourses + auditSummary.value.pendingTasks + auditSummary.value.pendingResources

    // 操作日志
    if (logsRes.data) {
      recentLogs.value = logsRes.data.records || logsRes.data || []
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => { fetchDashboard(); fetchDepartments() })
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
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-info clickable-card" @click="showDeptList">
          <div class="stat-label">学院总数 <el-icon style="font-size:12px;vertical-align:-1px"><ArrowRight /></el-icon></div>
          <div class="stat-value">{{ stats.deptCount }}<small> 个</small></div>
          <div class="stat-detail">点击查看学院列表</div>
          <el-button class="card-add-btn" size="small" circle @click.stop="openCreateDept">
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="card-l1 accent-success clickable-card" @click="showClassList()">
          <div class="stat-label">班级总数 <el-icon style="font-size:12px;vertical-align:-1px"><ArrowRight /></el-icon></div>
          <div class="stat-value">{{ stats.classCount }}<small> 个</small></div>
          <div class="stat-detail">点击查看班级列表</div>
        </div>
      </el-col>
    </el-row>

    <!-- ====== L2：图表区域（左2/3 + 右1/3，等高度） ====== -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="16">
        <div class="card-l2 chart-card">
          <div class="card-title">本周审核趋势</div>
          <VChart :option="auditTrendOption" style="height:300px" autoresize />
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
        <div class="card-l2 chart-card">
          <div class="card-title">用户角色分布</div>
          <VChart :option="userPieOption" style="height:300px" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- ====== L3：操作日志 + 审核统计（宽度与上图对齐） ====== -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :xs="24" :lg="16">
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
                <span class="log-action">{{ log.comment || log.result || log.action }}</span>
              </div>
              <div class="log-right">
                <el-tag v-if="log.result === '通过' || log.actionShort === 'APPROVE' || log.action === 'APPROVED'" type="success" size="small" effect="plain">通过</el-tag>
                <el-tag v-else-if="log.result === '驳回' || log.actionShort === 'REJECT' || log.action === 'REJECTED'" type="danger" size="small" effect="plain">驳回</el-tag>
                <span v-else class="log-result">{{ log.result || log.action || log.comment }}</span>
                <span class="log-time">{{ log.time || log.createTime }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :lg="8">
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
    </el-row>

    <!-- 新建学院弹窗 -->
    <el-dialog v-model="deptDialogVisible" title="新建学院" width="450px" destroy-on-close>
      <el-form ref="deptFormRef" :model="deptForm" :rules="deptRules" label-width="80px">
        <el-form-item label="学院名称" prop="deptName">
          <el-input v-model="deptForm.deptName" placeholder="如：计算机学院" />
        </el-form-item>
        <el-form-item label="学院编码" prop="deptCode">
          <el-input v-model="deptForm.deptCode" placeholder="如：CS（选填，默认同名称）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="deptSaving" @click="handleCreateDept">创建</el-button>
      </template>
    </el-dialog>

    <!-- 学院/班级列表弹窗 -->
    <el-dialog v-model="listDialogVisible" :title="listDialogTitle" width="700px" destroy-on-close>
      <!-- 班级列表时：学院筛选 -->
      <div v-if="listType === 'class'" style="margin-bottom:12px;display:flex;align-items:center;gap:10px">
        <span style="font-size:13px;color:#606266">学院筛选：</span>
        <el-select v-model="classDeptFilter" placeholder="全部学院" clearable style="width:200px" @change="onClassDeptFilterChange">
          <el-option v-for="d in departmentOptions" :key="d.departmentId" :label="d.departmentName" :value="d.departmentId" />
        </el-select>
      </div>
      <el-table :data="listData" v-loading="listLoading" stripe max-height="450">
        <template v-if="listType === 'dept'">
          <el-table-column prop="departmentName" label="学院名称" min-width="200" />
          <el-table-column prop="departmentCode" label="编码" width="100" />
        </template>
        <template v-else>
          <el-table-column prop="className" label="班级名称" min-width="180" />
          <el-table-column prop="classCode" label="编码" width="100" />
          <el-table-column prop="departmentName" label="所属学院" min-width="150" />
          <el-table-column label="操作" width="100" align="center">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="showClassList(row.departmentId); classDeptFilter = String(row.departmentId)">该院班级</el-button>
            </template>
          </el-table-column>
        </template>
      </el-table>
      <template #footer>
        <el-button @click="listDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-desc { font-size: 13px; color: #909399; margin-top: 4px; }
.stat-detail { font-size: 12px; color: #909399; margin-top: 4px; }
.chart-card { display: flex; flex-direction: column; }
.chart-card :deep(.card-title) { flex-shrink: 0; }
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
.log-list { display: flex; flex-direction: column; max-height: 300px; overflow-y: auto; }
.log-item { display: flex; align-items: center; gap: 8px; padding: 9px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
.log-body { flex: 1; min-width: 0; }
.log-operator { font-weight: 500; color: #303133; margin-right: 6px; }
.log-action { color: #606266; }
.log-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.log-result { font-size: 12px; color: #909399; }
.log-time { font-size: 12px; color: #c0c4cc; }
.clickable-card { cursor: pointer; transition: transform 0.15s, box-shadow 0.15s; position: relative; }
.clickable-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(31, 111, 74, 0.15); }
.card-add-btn { position: absolute; top: 12px; right: 12px; }
.kpi-row { row-gap: 16px; }
</style>
