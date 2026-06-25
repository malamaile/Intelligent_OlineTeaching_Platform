<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getClassOverview, getAtRiskStudents } from '@/api/teacher'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

// 班级总览
const classData = ref({
  className: '', studentCount: 0,
  avgCompletionRate: 0, avgCorrectRate: 0,
  avgExperimentRate: 0, avgTrainingRate: 0,
  levelDistribution: { excellent: 0, good: 0, needImprove: 0 },
})

// 学业等级饼图 — 使用 computed 确保数据变化时图表更新
const levelPieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie', radius: ['50%', '72%'], center: ['50%', '45%'],
    label: { formatter: '{b}\n{d}%' },
    data: [
      { value: classData.value.levelDistribution?.excellent || 0, name: '优秀', itemStyle: { color: '#9cee73' } },
      { value: classData.value.levelDistribution?.good || 0, name: '良好', itemStyle: { color: '#7eb9f4' } },
      { value: classData.value.levelDistribution?.needImprove || 0, name: '待提升', itemStyle: { color: '#f9c578' } },
    ],
  }],
}))

// 学生列表
const filterType = ref('ALL')
const students = ref([])

const filteredStudents = computed(() => {
  if (filterType.value === 'ALL') return students.value
  if (filterType.value === 'MISSING') return students.value.filter(s => s.missedTasks > 0)
  if (filterType.value === 'LOW_SCORE') return students.value.filter(s => s.avgScore < 60)
  if (filterType.value === 'SLOW') return students.value.filter(s => s.completionRate < 50)
  return students.value
})

async function fetchClassOverview() {
  try {
    const res = await getClassOverview()
    classData.value = res.data || classData.value
  } catch {}
}

async function fetchStudents() {
  try {
    const res = await getAtRiskStudents({ filterType: filterType.value })
    students.value = res.data || []
  } catch {}
}

watch(filterType, () => {
  fetchStudents()
})

function handleExport() {
  // TODO: 调用导出 API
}

onMounted(() => {
  fetchClassOverview()
  fetchStudents()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">班级学情查看</h1>
      <el-button type="success" @click="handleExport">导出报表</el-button>
    </div>

    <!-- 班级总览卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-label">{{ classData.className || '班级总览' }}</div>
        <div class="stat-value primary">{{ classData.studentCount }}<small> 人</small></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">课程完成率</div>
        <div class="stat-value success">{{ classData.avgCompletionRate }}<small>%</small></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">习题正确率</div>
        <div class="stat-value primary">{{ classData.avgCorrectRate }}<small>%</small></div>
      </div>
      <div class="stat-card">
        <div class="stat-label">实验完成率</div>
        <div class="stat-value warning">{{ classData.avgExperimentRate }}<small>%</small></div>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 等级分布 -->
      <el-col :span="8">
        <div class="card-wrapper">
          <div class="card-title">学业等级分布</div>
          <VChart :option="levelPieOption" style="height:240px" autoresize />
        </div>
      </el-col>

      <!-- 个体查看 -->
      <el-col :span="16">
        <div class="card-wrapper">
          <div class="card-title">
            学生个体查看
            <el-select v-model="filterType" style="width:140px;margin-left:12px" size="small">
              <el-option label="全部学生" value="ALL" />
              <el-option label="作业缺交" value="MISSING" />
              <el-option label="成绩偏低" value="LOW_SCORE" />
              <el-option label="进度滞后" value="SLOW" />
            </el-select>
          </div>
          <el-table :data="filteredStudents" stripe size="small" max-height="300">
            <el-table-column prop="userName" label="姓名" width="80" />
            <el-table-column label="完成率" width="90">
              <template #default="{ row }">
                <el-progress :percentage="row.completionRate" :stroke-width="6" :color="row.completionRate >= 70 ? '#a5f87c' : row.completionRate >= 50 ? '#409eff' : '#f56c6c'" />
              </template>
            </el-table-column>
            <el-table-column label="平均分" width="80">
              <template #default="{ row }">
                <span :style="{ color: row.avgScore >= 85 ? '#a4f67b' : row.avgScore >= 60 ? '#7ab7f3' : '#f56c6c', fontWeight: 600 }">{{ row.avgScore }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="missedTasks" label="缺交次数" width="80" />
            <el-table-column label="风险等级" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.riskLevel === 'HIGH'" type="danger" size="small">高风险</el-tag>
                <el-tag v-else-if="row.riskLevel === 'MEDIUM'" type="warning" size="small">中风险</el-tag>
                <span v-else style="color:#67c23a">正常</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text type="primary">详情</el-button>
                  <el-button size="small" text type="warning">提醒</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
