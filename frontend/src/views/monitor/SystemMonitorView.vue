<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent,
} from 'echarts/components'
import { getSystemMonitor } from '@/api/admin'

use([
  CanvasRenderer,
  BarChart, PieChart, LineChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent,
])

// ========== 时间 ==========
const now = ref(new Date())
let timer = null

// ========== 后端数据 ==========
const loading = ref(true)
const monitorData = ref({
  systemMetrics: {},
  userStats: {},
  auditSummary: {},
  recentLogs: [],
  onlineUsers: [],
})

// ========== 统计卡片（从后端数据计算） ==========
const statsCards = computed(() => {
  const m = monitorData.value.systemMetrics
  const u = monitorData.value.userStats
  return [
    { label: '用户总数', value: u.totalUsers || 0, unit: '人', icon: 'User', color: '#8ec2f7', bg: '#ecf5ff' },
    { label: '今日活跃', value: u.todayActive || 0, unit: '人', icon: 'Connection', color: '#bce7a7', bg: '#f0f9eb' },
    { label: '内存使用', value: m.memoryUsagePercent != null ? m.memoryUsagePercent + '%' : '-', unit: '', icon: 'Memo', color: '#f8c982', bg: '#fdf6ec' },
    { label: '磁盘使用', value: m.diskUsagePercent != null ? m.diskUsagePercent + '%' : '-', unit: '', icon: 'FolderOpened', color: '#909399', bg: '#f5f7fa' },
  ]
})

// ========== 待审核统计 ==========
const auditStats = computed(() => ({
  pendingCourses: monitorData.value.auditSummary.pendingCourses || 0,
  pendingTasks: monitorData.value.auditSummary.pendingTasks || 0,
  pendingResources: monitorData.value.auditSummary.pendingResources || 0,
  todayApproved: monitorData.value.auditSummary.todayApproved || 0,
  todayRejected: monitorData.value.auditSummary.todayRejected || 0,
}))

// ========== 系统资源使用趋势（由于无历史数据，展示当前快照） ==========
const resourceTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['JVM内存使用率'], bottom: 0 },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: ['总内存', '已使用', '可用', '最大'] },
  yAxis: { type: 'value', axisLabel: { formatter: '{value} MB' } },
  series: [
    {
      name: 'JVM内存',
      type: 'bar',
      data: [
        monitorData.value.systemMetrics.memoryTotal || 0,
        monitorData.value.systemMetrics.memoryUsed || 0,
        monitorData.value.systemMetrics.memoryTotal - (monitorData.value.systemMetrics.memoryUsed || 0),
        monitorData.value.systemMetrics.memoryMax || 0,
      ],
      itemStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#409eff' }, { offset: 1, color: '#a0cfff' }] },
        borderRadius: [6, 6, 0, 0],
      },
    },
  ],
}))

// ========== API 请求量（暂无数据，展示用户角色分布） ==========
const requestBarOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: ['学生', '教师', '管理员', '活跃用户', '总用户'] },
  yAxis: { type: 'value' },
  series: [
    {
      name: '人数',
      type: 'bar',
      data: [
        monitorData.value.userStats.students || 0,
        monitorData.value.userStats.teachers || 0,
        monitorData.value.userStats.admins || 0,
        monitorData.value.userStats.activeUsers || 0,
        monitorData.value.userStats.totalUsers || 0,
      ],
      itemStyle: {
        color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#67c23a' }, { offset: 1, color: '#b3e19d' }] },
        borderRadius: [6, 6, 0, 0],
      },
    },
  ],
}))

// ========== 审核状态饼图（真实数据） ==========
const auditPieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    name: '审核状态',
    type: 'pie',
    radius: ['50%', '75%'],
    center: ['50%', '45%'],
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
    data: [
      { value: monitorData.value.auditSummary.totalApproved || 0, name: '已通过', itemStyle: { color: '#67c23a' } },
      { value: monitorData.value.auditSummary.totalPending || 0, name: '待审核', itemStyle: { color: '#e6a23c' } },
      { value: monitorData.value.auditSummary.totalRejected || 0, name: '已驳回', itemStyle: { color: '#f56c6c' } },
    ],
  }],
}))

// ========== 操作日志 ==========
const logLevelConfig = {
  INFO: { type: 'info', label: '信息' },
  WARN: { type: 'warning', label: '警告' },
  ERROR: { type: 'danger', label: '错误' },
}

function formatDuration(min) {
  if (min == null || min < 0) return '-'
  if (min < 60) return min + '分钟'
  return Math.floor(min / 60) + '小时' + (min % 60) + '分钟'
}

async function fetchMonitorData() {
  loading.value = true
  try {
    const res = await getSystemMonitor()
    if (res.data) {
      monitorData.value = {
        systemMetrics: res.data.systemMetrics || {},
        userStats: res.data.userStats || {},
        auditSummary: res.data.auditSummary || {},
        recentLogs: res.data.recentLogs || [],
        onlineUsers: res.data.onlineUsers || [],
      }
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchMonitorData()
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">系统监控</h1>
      <p class="page-desc">
        JVM 最大内存：<strong>{{ monitorData.systemMetrics.memoryMax || '-' }} MB</strong> &nbsp;|&nbsp;
        CPU 核心数：<strong>{{ monitorData.systemMetrics.cpuCores || '-' }}</strong> &nbsp;|&nbsp;
        当前时间：{{ now.toLocaleString() }}
      </p>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-cards">
      <div v-for="card in statsCards" :key="card.label" class="stat-card" :style="{ borderTop: `3px solid ${card.color}` }">
        <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
          <el-icon :size="22"><component :is="card.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
        </div>
      </div>
    </div>

    <!-- 图表行 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card-wrapper">
          <div class="card-title">JVM 内存状态</div>
          <VChart :option="resourceTrendOption" style="height: 280px" autoresize />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="card-wrapper">
          <div class="card-title">审核统计</div>
          <VChart :option="auditPieOption" style="height: 280px" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- API 请求量 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card-wrapper">
          <div class="card-title">用户分布</div>
          <VChart :option="requestBarOption" style="height: 240px" autoresize />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="card-wrapper">
          <div class="card-title">待审核内容</div>
          <div class="audit-summary">
            <div class="audit-item">
              <span class="audit-label">课程开课计划</span>
              <el-badge :value="auditStats.pendingCourses" type="warning" />
            </div>
            <div class="audit-item">
              <span class="audit-label">实验实训计划</span>
              <el-badge :value="auditStats.pendingTasks" type="warning" />
            </div>
            <div class="audit-item">
              <span class="audit-label">教学资源</span>
              <el-badge :value="auditStats.pendingResources" type="warning" />
            </div>
            <el-divider />
            <div class="audit-item">
              <span class="audit-label">今日通过</span>
              <span class="audit-value success">{{ auditStats.todayApproved }}</span>
            </div>
            <div class="audit-item">
              <span class="audit-label">今日驳回</span>
              <span class="audit-value danger">{{ auditStats.todayRejected }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 在线用户 & 操作日志 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">当前在线用户</div>
          <el-table :data="monitorData.onlineUsers" size="small" stripe max-height="260">
            <el-table-column prop="account" label="账号" width="90" />
            <el-table-column prop="userName" label="姓名" width="80" />
            <el-table-column label="角色" width="70">
              <template #default="{ row }">
                <el-tag size="small">{{ row.roleName || (row.role === 'STUDENT' ? '学生' : row.role === 'TEACHER' ? '教师' : '管理员') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastLoginTime" label="最后登录" width="150" />
            <el-table-column label="活动时长" width="90">
              <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">操作日志</div>
          <div class="log-list">
            <div v-if="!monitorData.recentLogs.length" style="text-align:center;color:#909399;padding:20px">暂无操作记录</div>
            <div v-for="(log, idx) in monitorData.recentLogs" :key="idx" class="log-item">
              <el-tag :type="logLevelConfig[log.level]?.type" size="small" effect="plain">
                {{ logLevelConfig[log.level]?.label }}
              </el-tag>
              <div class="log-body">
                <div class="log-action">
                  <span class="log-user">{{ log.user }}</span>
                  {{ log.action }}
                </div>
                <div class="log-time">{{ log.time }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-desc {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-body {
  flex: 1;
}

.stat-body .stat-label {
  margin-bottom: 4px;
}

.stat-body .stat-value {
  font-size: 24px;
}

/* 审核 */
.audit-summary {
  padding: 8px 0;
}

.audit-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  font-size: 14px;
}

.audit-label {
  color: #606266;
}

.audit-value {
  font-size: 18px;
  font-weight: 700;
}

.audit-value.success { color: #67c23a; }
.audit-value.danger { color: #f56c6c; }

/* 日志 */
.log-list {
  max-height: 260px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.log-item:last-child {
  border-bottom: none;
}

.log-body {
  flex: 1;
  min-width: 0;
}

.log-action {
  font-size: 13px;
  color: #606266;
}

.log-user {
  font-weight: 500;
  color: #303133;
  margin-right: 6px;
}

.log-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 2px;
}
</style>
