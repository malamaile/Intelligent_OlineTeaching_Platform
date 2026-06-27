<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart, GaugeChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, ToolboxComponent,
} from 'echarts/components'

use([
  CanvasRenderer,
  BarChart, PieChart, LineChart, GaugeChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, ToolboxComponent,
])

// ========== 时间 ==========
const now = ref(new Date())
let timer = null

// ========== 统计卡片 ==========
const statsCards = ref([
  { label: '在线用户', value: 128, icon: 'User', color: '#8ec2f7', bg: '#ecf5ff' },
  { label: 'CPU 使用率', value: '42%', icon: 'Cpu', color: '#bce7a7', bg: '#f0f9eb' },
  { label: '内存使用率', value: '68%', icon: 'Memo', color: '#f8c982', bg: '#fdf6ec' },
  { label: '磁盘使用率', value: '55%', icon: 'FolderOpened', color: '#909399', bg: '#f5f7fa' },
])

// ========== 待审核统计 ==========
const auditStats = ref({
  pendingCourses: 5,
  pendingTasks: 12,
  pendingResources: 8,
  todayApproved: 23,
  todayRejected: 3,
})

// ========== CPU / 内存趋势图 ==========
const resourceTrendOption = reactive({
  tooltip: { trigger: 'axis' },
  legend: { data: ['CPU使用率', '内存使用率'], bottom: 0 },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['14:00', '14:10', '14:20', '14:30', '14:40', '14:50', '15:00', '15:10', '15:20', '15:30'],
  },
  yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
  series: [
    {
      name: 'CPU使用率',
      type: 'line',
      smooth: true,
      data: [35, 42, 38, 45, 55, 48, 52, 42, 38, 42],
      areaStyle: { opacity: 0.15 },
    },
    {
      name: '内存使用率',
      type: 'line',
      smooth: true,
      data: [60, 62, 65, 63, 68, 70, 72, 68, 66, 68],
      areaStyle: { opacity: 0.15 },
    },
  ],
})

// ========== 请求量柱状图 ==========
const requestBarOption = reactive({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
  },
  yAxis: { type: 'value' },
  series: [
    {
      name: 'API请求量',
      type: 'bar',
      data: [3200, 4500, 5100, 4800, 3900, 1200, 800],
      itemStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#a0cfff' },
          ],
        },
        borderRadius: [6, 6, 0, 0],
      },
    },
  ],
})

// ========== 审核状态饼图 ==========
const auditPieOption = reactive({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      name: '审核状态',
      type: 'pie',
      radius: ['50%', '75%'],
      center: ['50%', '45%'],
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      data: [
        { value: 120, name: '已通过', itemStyle: { color: '#67c23a' } },
        { value: 25, name: '待审核', itemStyle: { color: '#e6a23c' } },
        { value: 8, name: '已驳回', itemStyle: { color: '#f56c6c' } },
      ],
    },
  ],
})

// ========== 在线用户列表 ==========
const onlineUsers = ref([
  { account: '2024001', userName: '张三', role: 'STUDENT', ip: '192.168.1.101', loginTime: '2026-06-18 15:20:00', duration: 35 },
  { account: 't001', userName: '王老师', role: 'TEACHER', ip: '192.168.1.50', loginTime: '2026-06-18 14:30:00', duration: 85 },
  { account: '2024100', userName: '李四', role: 'STUDENT', ip: '192.168.1.102', loginTime: '2026-06-18 15:10:00', duration: 45 },
  { account: 'admin', userName: '管理员张', role: 'ADMIN', ip: '192.168.1.1', loginTime: '2026-06-18 08:00:00', duration: 475 },
  { account: '2024030', userName: '王五', role: 'STUDENT', ip: '192.168.1.103', loginTime: '2026-06-18 15:25:00', duration: 30 },
])

// ========== 操作日志 ==========
const logs = ref([
  { time: '2026-06-18 15:30:12', user: '管理员张', action: '审核通过课程《数据结构》', type: 'AUDIT', level: 'INFO' },
  { time: '2026-06-18 15:28:05', user: '王老师', action: '上传教学资源《排序算法课件》', type: 'UPLOAD', level: 'INFO' },
  { time: '2026-06-18 15:25:33', user: '张三', action: '提交实验报告《实验二》', type: 'SUBMIT', level: 'INFO' },
  { time: '2026-06-18 15:20:01', user: '系统', action: '自动备份数据库完成', type: 'SYSTEM', level: 'INFO' },
  { time: '2026-06-18 15:15:48', user: '李四', action: '登录失败：密码错误（第3次）', type: 'LOGIN', level: 'WARN' },
  { time: '2026-06-18 15:10:22', user: '管理员张', action: '冻结账号 2024150（违规操作）', type: 'USER', level: 'WARN' },
  { time: '2026-06-18 15:05:00', user: '系统', action: '学情诊断定时任务执行完成', type: 'SYSTEM', level: 'INFO' },
])

const logLevelConfig = {
  INFO: { type: 'info', label: '信息' },
  WARN: { type: 'warning', label: '警告' },
  ERROR: { type: 'danger', label: '错误' },
}

function formatDuration(min) {
  if (min < 60) return min + '分钟'
  return Math.floor(min / 60) + '小时' + (min % 60) + '分钟'
}

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">系统监控</h1>
      <p class="page-desc">
        服务运行时间：<strong>7天12小时35分</strong> &nbsp;|&nbsp;
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
          <div class="card-title">资源使用趋势</div>
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
          <div class="card-title">本周 API 请求量</div>
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
          <el-table :data="onlineUsers" size="small" stripe max-height="260">
            <el-table-column prop="account" label="账号" width="90" />
            <el-table-column prop="userName" label="姓名" width="80" />
            <el-table-column prop="role" label="角色" width="70">
              <template #default="{ row }">
                <el-tag size="small">{{ row.role === 'STUDENT' ? '学生' : row.role === 'TEACHER' ? '教师' : '管理员' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP地址" width="120" />
            <el-table-column prop="loginTime" label="登录时间" width="150" />
            <el-table-column label="在线时长" width="80">
              <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">操作日志</div>
          <div class="log-list">
            <div v-for="(log, idx) in logs" :key="idx" class="log-item">
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
