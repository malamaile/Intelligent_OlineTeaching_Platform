<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { ElMessage } from 'element-plus'
import { getClassOverview, getAtRiskStudents, getStudentAnalytics, exportAnalytics } from '@/api/teacher'
import { getTeacherCourses } from '@/api/teacher'
import http from '@/api'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

// ========== 筛选条件 ==========
const filters = reactive({ classId: null, courseId: null, semester: '' })
const classOptions = ref([])
const courseOptions = ref([])
const semesterOptions = ref([])

async function loadFilterOptions() {
  try {
    const [classRes, courseRes, semRes] = await Promise.all([
      http.get('/common/classes'),
      getTeacherCourses({ auditStatus: 'APPROVED', page: 1, pageSize: 200 }),
      http.get('/common/semesters'),
    ])
    classOptions.value = (classRes.data || []).map(c => ({ label: c.className, value: c.classId }))
    const courseList = courseRes.data.records || courseRes.data || []
    // 按班级去重课程名
    courseOptions.value = [...new Map(courseList.map(c => [c.courseName, { label: c.courseName + '（' + c.className + '）', value: c.courseId || c.planId }])).values()]
    semesterOptions.value = (semRes.data || []).map(s => ({ label: s.semesterName, value: s.semesterId }))
  } catch { /* ignore */ }
}

// ========== 班级总览 ==========
const classData = ref({
  className: '', studentCount: 0,
  avgCompletionRate: 0, avgCorrectRate: 0,
  avgExperimentRate: 0, avgTrainingRate: 0,
  levelDistribution: { excellent: 0, good: 0, needImprove: 0 },
  courseRanking: [],
})

const levelPieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie', radius: ['40%', '72%'], center: ['50%', '45%'],
    label: { formatter: '{b}\n{c}人 ({d}%)' },
    emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
    data: [
      { value: classData.value.levelDistribution?.excellent || 0, name: '优秀 (≥90)', itemStyle: { color: '#9cee73' } },
      { value: classData.value.levelDistribution?.good || 0, name: '良好 (80-89)', itemStyle: { color: '#7eb9f4' } },
      { value: classData.value.levelDistribution?.needImprove || 0, name: '待提升 (<80)', itemStyle: { color: '#f9c578' } },
    ],
  }],
}))

async function fetchClassOverview() {
  try {
    const params = {}
    if (filters.classId) params.classId = filters.classId
    if (filters.semester) params.semester = filters.semester
    const res = await getClassOverview(params)
    classData.value = res.data || classData.value
  } catch {}
}

// ========== 学生列表 ==========
const filterType = ref('ALL')
const students = ref([])
const studentPagination = reactive({ page: 1, pageSize: 10, total: 0 })

async function fetchStudents() {
  try {
    const params = {
      filterType: filterType.value,
      page: studentPagination.page,
      pageSize: studentPagination.pageSize,
    }
    if (filters.classId) params.classId = filters.classId
    const res = await getAtRiskStudents(params)
    students.value = res.data.records || res.data || []
    studentPagination.total = res.data.total || 0
  } catch {}
}

watch(filterType, () => {
  studentPagination.page = 1
  fetchStudents()
})

watch(() => filters.classId, () => {
  fetchClassOverview()
  fetchStudents()
})

// ========== 学生详情弹窗 ==========
const detailVisible = ref(false)
const detailStudent = ref(null)
const detailLoading = ref(false)

async function openStudentDetail(row) {
  detailStudent.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    const params = {}
    if (filters.semester) params.semester = filters.semester
    const res = await getStudentAnalytics(row.userId, params)
    const data = res.data || {}
    detailStudent.value = { ...row, ...data, userName: data.studentName || row.userName }
  } catch {
    ElMessage.error('加载学生详情失败')
  } finally {
    detailLoading.value = false
  }
}

// ========== 提醒学生 ==========
function remindStudent(row) {
  ElMessage.info('提醒功能：可向 ' + row.userName + ' 发送学业预警通知（待对接消息接口）')
}

// ========== 导出报表 ==========
const exporting = ref(false)
async function handleExport() {
  exporting.value = true
  try {
    const params = {}
    if (filters.classId) params.classId = filters.classId
    if (filters.semester) params.semester = filters.semester
    const res = await exportAnalytics(params)
    const blob = res.data
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = (classData.value.className || '班级') + '_学情报表.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

function handleSearch() {
  studentPagination.page = 1
  fetchClassOverview()
  fetchStudents()
}

onMounted(() => {
  loadFilterOptions()
  fetchClassOverview()
  fetchStudents()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">班级学情查看</h1>
      <el-button type="success" :loading="exporting" @click="handleExport">导出报表</el-button>
    </div>

    <!-- 筛选栏 + 班级人数 -->
    <div class="filter-bar">
      <span class="filter-label">班级：</span>
      <el-select v-model="filters.classId" placeholder="全部班级" clearable style="width:180px" @change="handleSearch">
        <el-option v-for="c in classOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
      <span class="filter-label">学期：</span>
      <el-select v-model="filters.semester" placeholder="全部学期" clearable style="width:150px" @change="handleSearch">
        <el-option v-for="s in semesterOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-divider direction="vertical" />
      <span class="class-count">
        <span class="class-count-label">{{ classData.className || '班级' }}</span>
        <span class="class-count-value">{{ classData.studentCount }}</span>
        <span class="class-count-unit">人</span>
      </span>
    </div>

    <!-- 班级总览卡片 -->
    <div class="stat-cards">
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
      <div class="stat-card">
        <div class="stat-label">实训完成率</div>
        <div class="stat-value danger">{{ classData.avgTrainingRate }}<small>%</small></div>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 等级分布 -->
      <el-col :span="12">
        <div class="card-wrapper">
          <div class="card-title">学业等级分布</div>
          <VChart :option="levelPieOption" style="height:240px" autoresize />
        </div>
      </el-col>

      <!-- 个体查看 -->
      <el-col :span="12">
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
          <el-table :data="students" stripe size="small" :header-cell-style="{ textAlign: 'center' }">
            <el-table-column prop="userName" label="姓名" min-width="55" align="center" />
            <el-table-column label="完成率" width="70" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.completionRate >= 70 ? '#67c23a' : row.completionRate >= 50 ? '#409eff' : '#f56c6c', fontWeight: 600 }">{{ row.completionRate }}%</span>
              </template>
            </el-table-column>
            <el-table-column label="平均分" width="60" align="center">
              <template #default="{ row }">
                <span :style="{ color: row.avgScore >= 85 ? '#67c23a' : row.avgScore >= 60 ? '#409eff' : '#f56c6c', fontWeight: 600 }">{{ row.avgScore }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="missedTasks" label="缺交" width="50" align="center" />
            <el-table-column label="风险等级" min-width="75" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.riskLevel === 'HIGH'" type="danger" size="small">高风险</el-tag>
                <el-tag v-else-if="row.riskLevel === 'MEDIUM'" type="warning" size="small">中风险</el-tag>
                <span v-else style="color:#67c23a;font-size:12px">正常</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="90" align="center">
              <template #default="{ row }">
                <div class="action-btns">
                  <el-button size="small" text type="primary" @click="openStudentDetail(row)">详情</el-button>
                  <el-button size="small" text type="warning" @click="remindStudent(row)">提醒</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="studentPagination.page"
            v-model:page-size="studentPagination.pageSize"
            :total="studentPagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end"
            @current-change="fetchStudents"
            @size-change="fetchStudents"
          />
        </div>
      </el-col>
    </el-row>

    <!-- 学生详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="detailStudent?.userName + ' — 学情明细'"
      width="720px"
      destroy-on-close
    >
      <div v-loading="detailLoading" v-if="detailStudent">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ detailStudent.userName }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ detailStudent.className || '-' }}</el-descriptions-item>
          <el-descriptions-item label="平均分">
            <span :style="{ color: detailStudent.avgScore >= 85 ? '#67c23a' : detailStudent.avgScore >= 60 ? '#409eff' : '#f56c6c', fontWeight: 600 }">{{ detailStudent.avgScore }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="平均进度">{{ detailStudent.avgProgress }}%</el-descriptions-item>
          <el-descriptions-item label="任务完成率">{{ detailStudent.taskCompletionRate }}%</el-descriptions-item>
          <el-descriptions-item label="已完成/总任务">{{ detailStudent.completedTasks }}/{{ detailStudent.totalTasks }}</el-descriptions-item>
          <el-descriptions-item label="学业等级">
            <el-tag :type="detailStudent.level === '优秀' ? 'success' : detailStudent.level === '良好' ? 'primary' : detailStudent.level === '及格' ? 'warning' : 'danger'" size="small">{{ detailStudent.level }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="诊断" :span="2">{{ detailStudent.diagnosis || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 各科成绩 -->
        <div v-if="detailStudent.gradeDetails && detailStudent.gradeDetails.length" style="margin-top:16px">
          <div class="section-title">各科成绩</div>
          <el-table :data="detailStudent.gradeDetails" stripe size="small">
            <el-table-column prop="courseName" label="课程" min-width="120" />
            <el-table-column prop="usualGrade" label="平时" width="60" />
            <el-table-column prop="examGrade" label="考试" width="60" />
            <el-table-column prop="experimentGrade" label="实验" width="60" />
            <el-table-column prop="finalGrade" label="总评" width="70">
              <template #default="{ row }">
                <span :style="{ color: row.finalGrade >= 90 ? '#67c23a' : row.finalGrade >= 60 ? '#303133' : '#f56c6c', fontWeight: 600 }">{{ row.finalGrade }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="gradeComment" label="评语" min-width="100" show-overflow-tooltip />
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}

.filter-label {
  font-size: 13px;
  color: #606266;
  margin-left: 8px;
}

.filter-label:first-child { margin-left: 0; }

.class-count {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-left: auto;
}

.class-count-label {
  font-size: 13px;
  color: #909399;
}

.class-count-value {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
}

.class-count-unit {
  font-size: 12px;
  color: #909399;
}

.action-btns {
  display: flex;
  align-items: center;
  gap: 4px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.progress-text {
  font-size: 11px;
  color: #909399;
  text-align: center;
  margin-top: 2px;
}
</style>
