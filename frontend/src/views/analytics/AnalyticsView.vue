<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAnalyticsOverview, getDiagnosisReport } from '@/api/student'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart, RadarChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, ToolboxComponent,
} from 'echarts/components'

use([
  CanvasRenderer,
  BarChart, PieChart, LineChart, RadarChart,
  TitleComponent, TooltipComponent, LegendComponent,
  GridComponent, ToolboxComponent,
])

const loading = ref(false)
const overview = ref(null)
const diagnosis = ref(null)

const levelConfig = {
  EXCELLENT: { label: '优秀', color: '#bcf3a1', bg: '#f0f9eb' },
  GOOD: { label: '良好', color: '#9fc9f4', bg: '#ecf5ff' },
  NEED_IMPROVE: { label: '待提升', color: '#f7d39b', bg: '#fdf6ec' },
}

// ========== 课程成绩柱状图 ==========
const courseScoreOption = ref(null)

function buildScoreChart(courseStats) {
  if (!courseStats?.length) return null
  courseScoreOption.value = {
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 30, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: courseStats.map((c) => c.courseName),
      axisLabel: { rotate: 20, fontSize: 11 },
    },
    yAxis: { type: 'value', min: 0, max: 100 },
    series: [
      {
        name: '成绩',
        type: 'bar',
        data: courseStats.map((c) => c.score),
        itemStyle: {
          color: (params) => {
            const v = params.value
            if (v >= 85) return '#bcf3a1'
            if (v >= 70) return '#9fc9f4'
            return '#f56c6c'
          },
          borderRadius: [6, 6, 0, 0],
        },
        barWidth: 36,
        label: { show: true, position: 'top', fontSize: 12, fontWeight: 600 },
      },
    ],
  }
}

// ========== 学习时长饼图 ==========
const durationPieOption = ref(null)

function buildDurationChart(courseStats) {
  if (!courseStats?.length) return null
  durationPieOption.value = {
    tooltip: { trigger: 'item', formatter: '{b}: {c}分钟 ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['35%', '50%'],
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14 } },
        data: courseStats.map((c, i) => ({
          value: c.studyDuration,
          name: c.courseName,
          itemStyle: {
            color: ['#9fc9f4', '#bcf3a1', '#f7d39b', '#f56c6c', '#909399', '#7b5ce7'][i % 6],
          },
        })),
      },
    ],
  }
}

// ========== 任务完成率雷达图 ==========
const radarOption = ref(null)

function buildRadarChart(courseStats) {
  if (!courseStats?.length) return null
  radarOption.value = {
    tooltip: {},
    legend: { data: ['习题正确率', '任务完成率'], bottom: 0 },
    radar: {
      center: ['50%', '45%'],
      radius: '65%',
      indicator: courseStats.map((c) => ({
        name: c.courseName,
        max: 100,
      })),
    },
    series: [
      {
        type: 'radar',
        name: '习题正确率',
        data: [{ value: courseStats.map((c) => c.exerciseCorrectRate), name: '习题正确率' }],
        lineStyle: { color: '#409eff', width: 2 },
        areaStyle: { color: 'rgba(64,158,255,0.15)' },
        itemStyle: { color: '#409eff' },
      },
      {
        type: 'radar',
        name: '任务完成率',
        data: [{ value: courseStats.map((c) => c.taskCompletionRate), name: '任务完成率' }],
        lineStyle: { color: '#67c23a', width: 2 },
        areaStyle: { color: 'rgba(103,194,58,0.15)' },
        itemStyle: { color: '#67c23a' },
      },
    ],
  }
}

// ========== 周趋势折线图 ==========
const weeklyTrendOption = ref(null)

function buildWeeklyChart(weeklyTrend) {
  if (!weeklyTrend?.length) return null
  weeklyTrendOption.value = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['学习时长(分钟)', '完成任务数'], bottom: 0 },
    grid: { left: 50, right: 50, top: 20, bottom: 35 },
    xAxis: { type: 'category', data: weeklyTrend.map((w) => w.week) },
    yAxis: [
      { type: 'value', name: '分钟' },
      { type: 'value', name: '个' },
    ],
    series: [
      {
        name: '学习时长(分钟)',
        type: 'line',
        smooth: true,
        data: weeklyTrend.map((w) => w.duration),
        areaStyle: { opacity: 0.15 },
      },
      {
        name: '完成任务数',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: weeklyTrend.map((w) => w.taskCount),
        lineStyle: { color: '#b4f196' },
        itemStyle: { color: '#b4f196' },
      },
    ],
  }
}

async function fetchData() {
  loading.value = true
  try {
    const [overRes, diagRes] = await Promise.all([
      getAnalyticsOverview(),
      getDiagnosisReport(),
    ])
    overview.value = overRes.data
    diagnosis.value = diagRes.data

    // 生成图表
    const stats = overRes.data?.courseStats || []
    buildScoreChart(stats)
    buildDurationChart(stats)
    buildRadarChart(stats)
    buildWeeklyChart(overRes.data?.weeklyTrend || [])
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">学情诊断</h1>
      <p class="page-desc">个人综合学习数据分析、学业水平诊断、薄弱知识点定位</p>
    </div>

    <!-- 诊断等级卡片 -->
    <div class="stat-cards" v-if="diagnosis">
      <div class="stat-card" :style="{ background: levelConfig[diagnosis.academicLevel]?.bg, border: 'none' }">
        <div class="stat-label">学业等级</div>
        <div class="stat-value" :style="{ color: levelConfig[diagnosis.academicLevel]?.color }">
          {{ levelConfig[diagnosis.academicLevel]?.label }}
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">综合评分</div>
        <div class="stat-value primary">{{ diagnosis.overallScore }}<small> 分</small></div>
      </div>
      <div class="stat-card" v-if="overview">
        <div class="stat-label">总学习时长</div>
        <div class="stat-value success">{{ overview.totalStudyDuration }}<small> 分钟</small></div>
      </div>
      <div class="stat-card" v-if="overview">
        <div class="stat-label">平均任务完成率</div>
        <div class="stat-value warning">{{ overview.taskCompletionRate }}<small>%</small></div>
      </div>
    </div>

    <!-- 图表行1：成绩柱状图 + 学习时长饼图 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card-wrapper">
          <div class="card-title">各课程成绩</div>
          <VChart v-if="courseScoreOption" :option="courseScoreOption" style="height: 280px" autoresize />
          <el-empty v-else description="暂无数据" :image-size="60" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="card-wrapper">
          <div class="card-title">学习时长分布</div>
          <VChart v-if="durationPieOption" :option="durationPieOption" style="height: 280px" autoresize />
          <el-empty v-else description="暂无数据" :image-size="60" />
        </div>
      </el-col>
    </el-row>

    <!-- 图表行2：雷达图 + 周趋势 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">正确率 & 完成率对比</div>
          <VChart v-if="radarOption" :option="radarOption" style="height: 300px" autoresize />
          <el-empty v-else description="暂无数据" :image-size="60" />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">学习趋势（近4周）</div>
          <VChart v-if="weeklyTrendOption" :option="weeklyTrendOption" style="height: 300px" autoresize />
          <el-empty v-else description="暂无数据" :image-size="60" />
        </div>
      </el-col>
    </el-row>

    <!-- 诊断详情 -->
    <el-row :gutter="16" v-if="diagnosis">
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">诊断概要</div>
          <el-alert
            :title="diagnosis.summary"
            :type="diagnosis.academicLevel === 'EXCELLENT' ? 'success' : diagnosis.academicLevel === 'GOOD' ? 'info' : 'warning'"
            :closable="false" show-icon
          />
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card-wrapper" v-if="diagnosis.weakPoints?.length">
          <div class="card-title">薄弱知识点</div>
          <el-tag
            v-for="(wp, idx) in diagnosis.weakPoints" :key="idx"
            type="danger" effect="plain" class="diag-tag"
          >
            {{ wp.knowledge }}
            <span class="diag-tag-sub">{{ wp.courseName }}</span>
          </el-tag>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="card-wrapper">
          <div class="card-title">推荐学习资源</div>
          <div v-if="diagnosis.suggestResources?.length">
            <div v-for="sr in diagnosis.suggestResources" :key="sr.resourceId" class="suggest-item">
              <el-icon><FolderOpened /></el-icon>
              <span>{{ sr.name }}</span>
              <el-tag size="small" effect="plain">{{ sr.type === 'VIDEO' ? '视频' : sr.type === 'EXERCISE' ? '习题' : '课件' }}</el-tag>
            </div>
          </div>
          <el-empty v-else description="暂无推荐" :image-size="60" />
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

.diag-tag {
  margin: 0 6px 6px 0;
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 6px 10px;
  height: auto;
}

.diag-tag-sub {
  font-size: 11px;
  opacity: 0.75;
  margin-top: 2px;
}

.suggest-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
  color: #606266;
}

.suggest-item:last-child {
  border-bottom: none;
}
</style>
