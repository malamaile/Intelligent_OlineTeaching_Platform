<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getAdminOverview, getWarnings } from '@/api/admin'

use([CanvasRenderer, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const loading = ref(true)

// 全校总览
const overview = ref({
  totalStudents: 0, totalCourses: 0,
  overallCompletionRate: 0, overallPassRate: 0,
  levelDistribution: { excellent: 0, good: 0, needImprove: 0 },
})

// 院系统计表
const deptStats = ref([])

// 院系成绩柱状图
const deptBarOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['完成率', '通过率'], bottom: 0 },
  grid: { left: 50, right: 20, top: 10, bottom: 35 },
  xAxis: { type: 'category', data: deptStats.value.map(d => d.department), axisLabel: { rotate: 15, fontSize: 10 } },
  yAxis: { type: 'value', max: 100 },
  series: [
    { name: '完成率', type: 'bar', data: deptStats.value.map(d => d.completionRate), itemStyle: { color: '#409eff', borderRadius: [6, 6, 0, 0] }, barWidth: 28 },
    { name: '通过率', type: 'bar', data: deptStats.value.map(d => d.passRate), itemStyle: { color: '#67c23a', borderRadius: [6, 6, 0, 0] }, barWidth: 28 },
  ],
}))

// 趋势图
const trendOption = ref({
  tooltip: { trigger: 'axis' },
  grid: { left: 50, right: 20, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: ['3月', '4月', '5月', '6月'] },
  yAxis: { type: 'value', max: 100 },
  series: [
    { name: '平均分', type: 'line', smooth: true, data: [76, 78, 80, 82], areaStyle: { opacity: 0.15 } },
    { name: '完成率', type: 'line', smooth: true, data: [75, 77, 80, 78.5], areaStyle: { opacity: 0.15 }, lineStyle: { color: '#67c23a' }, itemStyle: { color: '#67c23a' } },
  ],
})

// 预警名单
const warnings = ref([])

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
      if (overviewRes.data.deptStats) {
        deptStats.value = overviewRes.data.deptStats
      }
    }
    if (warningsRes.data) {
      warnings.value = warningsRes.data.records || warningsRes.data || []
    }
  } finally {
    loading.value = false
  }
}

function handleExport() {
  // TODO: 调用导出 API
}

onMounted(fetchData)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">全局学情监控</h1>
      <el-button type="success" @click="handleExport">导出报表</el-button>
    </div>

    <!-- 总览卡片 -->
    <div class="stat-cards">
      <div class="stat-card"><div class="stat-label">全校学生</div><div class="stat-value primary">{{ overview.totalStudents }}</div></div>
      <div class="stat-card"><div class="stat-label">开设课程</div><div class="stat-value success">{{ overview.totalCourses }}</div></div>
      <div class="stat-card"><div class="stat-label">完成率</div><div class="stat-value primary">{{ overview.overallCompletionRate }}<small>%</small></div></div>
      <div class="stat-card"><div class="stat-label">通过率</div><div class="stat-value success">{{ overview.overallPassRate }}<small>%</small></div></div>
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
      <el-table :data="deptStats" stripe>
        <el-table-column prop="department" label="院系" min-width="160" />
        <el-table-column prop="studentCount" label="学生数" width="90" />
        <el-table-column label="完成率" width="100">
          <template #default="{ row }">
            <el-progress :percentage="row.completionRate" :stroke-width="8" :color="row.completionRate >= 80 ? '#67c23a' : '#409eff'" />
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="100">
          <template #default="{ row }">
            <el-progress :percentage="row.passRate" :stroke-width="8" :color="row.passRate >= 85 ? '#67c23a' : '#409eff'" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 预警名单 -->
    <div class="card-wrapper">
      <div class="card-title">
        预警名单
        <span style="font-size:12px;color:#909399;margin-left:8px">
          高风险 {{ warnings.filter(w => w.riskLevel === 'HIGH').length }} 人 |
          中风险 {{ warnings.filter(w => w.riskLevel === 'MEDIUM').length }} 人
        </span>
      </div>
      <el-table :data="warnings" stripe>
        <el-table-column prop="userName" label="姓名" width="70" />
        <el-table-column prop="account" label="账号" width="90" />
        <el-table-column prop="department" label="院系" width="140" />
        <el-table-column prop="className" label="班级" width="150" />
        <el-table-column label="预警类型" width="100">
          <template #default="{ row }">{{ warningTypeConfig[row.warningType] || row.warningType }}</template>
        </el-table-column>
        <el-table-column prop="missedTasks" label="缺交" width="60" />
        <el-table-column prop="avgScore" label="均分" width="60">
          <template #default="{ row }">
            <span :style="{ color: row.avgScore >= 60 ? '#409eff' : '#f56c6c', fontWeight: 600 }">{{ row.avgScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="80">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === 'HIGH' ? 'danger' : 'warning'" size="small">
              {{ row.riskLevel === 'HIGH' ? '高风险' : '中风险' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
