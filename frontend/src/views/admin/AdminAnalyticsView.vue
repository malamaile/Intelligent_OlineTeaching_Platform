<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getAdminOverview, getWarnings, exportReport } from '@/api/admin'

use([CanvasRenderer, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const router = useRouter()
const loading = ref(true)

// 全校总览
const overview = ref({
  totalStudents: 0, totalCourses: 0,
  overallCompletionRate: 0, overallPassRate: 0,
  levelDistribution: { excellent: 0, good: 0, needImprove: 0 },
  trendData: [],   // 趋势图数据
  deptStats: [],   // 院系统计数据
})

// 院系统计表（直接从 overview 读取，避免 ref 同步问题）
const deptStats = computed(() => {
  return overview.value.deptStats || overview.value.byDepartment || []
})

// 院系成绩柱状图
const deptBarOption = computed(() => {
  const list = deptStats.value
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['完成率', '通过率'], bottom: 10 },
    grid: { left: 50, right: 20, top: 20, bottom: 60 },
    xAxis: { type: 'category', data: list.map(d => d.department || d.departmentName || ''), axisLabel: { rotate: 0, fontSize: 11 } },
    yAxis: { type: 'value', max: 100 },
    series: [
      { name: '完成率', type: 'bar', data: list.map(d => Number(d.completionRate) || 0), itemStyle: { color: '#409eff', borderRadius: [6, 6, 0, 0] }, barWidth: 28 },
      { name: '通过率', type: 'bar', data: list.map(d => Number(d.passRate) || 0), itemStyle: { color: '#67c23a', borderRadius: [6, 6, 0, 0] }, barWidth: 28 },
    ],
  }
})

// 趋势图（基于后端返回的真实数据）
const trendOption = computed(() => {
  const months = (overview.value.trendData || []).map(d => d.month || '')
  const scores = (overview.value.trendData || []).map(d => typeof d.avgScore === 'number' ? d.avgScore : (d.avgScore || 0))
  const rates = (overview.value.trendData || []).map(d => typeof d.completionRate === 'number' ? d.completionRate : (d.completionRate || 0))
  // 如果后端没有趋势数据，使用空数组
  const hasData = months.length > 0
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: hasData ? months : [] },
    yAxis: { type: 'value', max: 100 },
    series: [
      { name: '平均分', type: 'line', smooth: true, data: hasData ? scores : [], areaStyle: { opacity: 0.15 } },
      { name: '完成率', type: 'line', smooth: true, data: hasData ? rates : [], areaStyle: { opacity: 0.15 }, lineStyle: { color: '#67c23a' }, itemStyle: { color: '#67c23a' } },
    ],
  }
})

// 预警名单
const warnings = ref([])

function goCourseAnalytics() {
  router.push('/admin/analytics/courses')
}

const warningTypeConfig = {
  LONG_ABSENCE: '长期缺课', MISSING_HOMEWORK: '作业缺交', LOW_SCORE: '成绩偏低',
}

async function fetchData() {
  loading.value = true
  try {
    const [overviewRes, warningsRes] = await Promise.all([
      getAdminOverview(),
      getWarnings({ page: 1, pageSize: 20 }),
    ])
    if (overviewRes.data) {
      overview.value = { ...overview.value, ...overviewRes.data }
    }
    if (warningsRes.data) {
      warnings.value = warningsRes.data.records || warningsRes.data || []
    }
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    const res = await exportReport({ format: 'CSV' })
    // blob 响应：res 是 axios response，res.data 是 Blob
    const blob = new Blob([res.data], { type: 'text/csv;charset=UTF-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `学情分析报表_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('报表已导出')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">全局学情监控</h1>
    </div>

    <!-- 总览卡片 -->
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-label">全校学生</div><div class="stat-value primary">{{ overview.totalStudents }}</div></div>
      <div class="stat-card clickable" @click="goCourseAnalytics">
        <div class="stat-label">开设课程 <el-icon style="font-size:13px;vertical-align:-1px"><ArrowRight /></el-icon></div>
        <div class="stat-value success">{{ overview.totalCourses }}</div>
        <div style="font-size:12px;color:#909399;margin-top:2px">点击查看课程学情</div>
      </div>
    </div>

    <!-- 图表 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">各院系完成率 & 通过率</div>
          <VChart :option="deptBarOption" style="height:280px" autoresize />
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">学期趋势</div>
          <VChart :option="trendOption" style="height:280px" autoresize />
        </div>
      </el-col>
    </el-row>

    <!-- 院系明细 -->
<div class="card-wrapper" v-if="deptStats.length">
  <div class="card-title">院系明细</div>

  <el-table
    :data="deptStats"
    stripe
    class="dept-table"
  >
    <el-table-column prop="department" label="院系" min-width="160" />

    <el-table-column prop="studentCount" label="学生数" width="90" />

    <el-table-column label="完成率" width="220">
      <template #default="{ row }">
        <div class="progress-cell">
          <el-progress
            :percentage="Number(row.completionRate) || 0"
            :stroke-width="8"
            :color="(Number(row.completionRate) || 0) >= 80 ? '#67c23a' : '#409eff'"
          />
        </div>
      </template>
    </el-table-column>

    <el-table-column label="通过率" width="220">
      <template #default="{ row }">
        <div class="progress-cell">
          <el-progress
            :percentage="Number(row.passRate) || 0"
            :stroke-width="8"
            :color="(Number(row.passRate) || 0) >= 85 ? '#67c23a' : '#409eff'"
          />
        </div>
      </template>
    </el-table-column>

  </el-table>
</div>

    <!-- 预警名单 -->
    <div class="card-wrapper">
      <div class="card-title">
        预警名单
        <span style="font-size:12px;color:#909399;margin-left:8px">
          高风险 {{ warnings.filter(w => w.riskLevel === 'HIGH' || w.riskLevel === '高危').length }} 人 |
          中风险 {{ warnings.filter(w => w.riskLevel === 'MEDIUM' || w.riskLevel === '中危').length }} 人
        </span>
      </div>
      <el-table :data="warnings" stripe style="width:100%">
        <el-table-column prop="userName" label="姓名" min-width="70" />
        <el-table-column prop="account" label="账号" min-width="90" />
        <el-table-column prop="department" label="院系" min-width="120" />
        <el-table-column prop="className" label="班级" min-width="140" />
        <el-table-column label="风险等级" min-width="100">
          <template #default="{ row }">
            <span style="font-size:12px;color:#909399;margin-right:6px">{{ row.riskLevel === 'HIGH' || row.riskLevel === '高危' ? '🔴' : row.riskLevel === 'MEDIUM' || row.riskLevel === '中危' ? '🟡' : '🟢' }}</span>
            <el-tag :type="(row.riskLevel === 'HIGH' || row.riskLevel === '高危') ? 'danger' : (row.riskLevel === 'LOW' || row.riskLevel === '低危') ? 'success' : 'warning'" size="small">
              {{ (row.riskLevel === 'HIGH' || row.riskLevel === '高危') ? '高风险' : (row.riskLevel === 'LOW' || row.riskLevel === '低危') ? '中风险' : '低风险' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avgScore" label="均分" min-width="70" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.avgScore >= 60 ? '#409eff' : '#f56c6c', fontWeight: 600 }">{{ row.avgScore != null ? row.avgScore : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="taskCompletionRate" label="任务完成率" min-width="100" align="center">
          <template #default="{ row }">
            <el-progress :percentage="Math.round(row.taskCompletionRate || 0)" :stroke-width="6" :color="(row.taskCompletionRate || 0) >= 80 ? '#67c23a' : '#409eff'" />
          </template>
        </el-table-column>
        <el-table-column prop="totalStudyHours" label="学习时长(h)" min-width="100" align="center" />
              </el-table>
    </div>

  </div>
</template>

<style scoped>
.page-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
}

.dept-table .cell {
  padding: 8px 10px;
  text-align: center;
}


.progress-wrap {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-card.clickable {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.stat-card.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(31, 111, 74, 0.15);
}

</style>
