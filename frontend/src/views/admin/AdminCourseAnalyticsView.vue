<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCourseAnalytics, exportReport } from '@/api/admin'

const router = useRouter()
const loading = ref(true)

// 课程列表
const courseList = ref([])
const summary = reactive({ totalStudents: 0, totalGraded: 0, overallCompletionRate: 0, overallPassRate: 0 })

// 选中的课程详情
const selectedCourse = ref(null)
const showDetail = ref(false)

async function fetchData() {
  loading.value = true
  try {
    const res = await getCourseAnalytics()
    if (res.data) {
      courseList.value = res.data.courses || []
      summary.totalStudents = res.data.totalStudents || 0
      summary.totalGraded = res.data.totalGraded || 0
      summary.overallCompletionRate = res.data.overallCompletionRate || 0
      summary.overallPassRate = res.data.overallPassRate || 0
    }
  } finally {
    loading.value = false
  }
}

function viewCourseDetail(course) {
  selectedCourse.value = course
  showDetail.value = true
}

function backToList() {
  selectedCourse.value = null
  showDetail.value = false
}

function goBackToAnalytics() {
  router.push('/admin/analytics')
}

async function handleExport() {
  try {
    const res = await exportReport({ format: 'CSV' })
    const blob = new Blob([res.data], { type: 'text/csv;charset=UTF-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `课程学情报表_${new Date().toISOString().slice(0, 10)}.csv`
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
      <div style="display:flex;align-items:center;gap:12px">
        <h1 class="page-title" style="margin:0">{{ showDetail ? selectedCourse?.courseName : '课程学情分析' }}</h1>
      </div>
      <div style="display:flex;gap:8px">
        <el-button v-if="showDetail" type="success" @click="handleExport">导出报表</el-button>
        <el-button v-if="showDetail" type="primary" plain @click="backToList">返回课程列表</el-button>
        <el-button type="primary" plain @click="goBackToAnalytics">
          <el-icon><ArrowLeft /></el-icon>返回学情监控
        </el-button>
      </div>
    </div>

    <!-- 课程列表视图 -->
    <template v-if="!showDetail">
      <!-- 汇总卡片 -->
      <div class="stat-cards" style="margin-bottom:20px">
        <div class="stat-card"><div class="stat-label">课程总数</div><div class="stat-value primary">{{ courseList.length }}</div></div>
        <div class="stat-card"><div class="stat-label">总选课人数</div><div class="stat-value success">{{ summary.totalStudents }}</div></div>
        <div class="stat-card"><div class="stat-label">综合完成率</div><div class="stat-value primary">{{ summary.overallCompletionRate }}<small>%</small></div></div>
        <div class="stat-card"><div class="stat-label">综合通过率</div><div class="stat-value success">{{ summary.overallPassRate }}<small>%</small></div></div>
      </div>

      <!-- 课程表格 -->
      <div class="card-wrapper">
        <div class="card-title">课程明细（点击行查看详情）</div>
        <el-table :data="courseList" stripe highlight-current-row
          @row-click="viewCourseDetail" max-height="600" style="cursor:pointer">
          <el-table-column prop="courseName" label="课程名称" min-width="160">
            <template #default="{ row }">
              <div>
                <div style="font-weight:500;color:#1f6f4a">{{ row.courseName }}</div>
                <div style="font-size:12px;color:#909399">{{ row.courseCode }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="planCount" label="班级数" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.planCount }} 个班</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="studentCount" label="选课人数" width="100" align="center" />
          <el-table-column label="完成率" width="200" align="center">
            <template #default="{ row }">
              <div style="display:flex;align-items:center;gap:8px">
                <el-progress :percentage="Math.round(row.completionRate || 0)" :stroke-width="8"
                  :color="(row.completionRate || 0) >= 80 ? '#67c23a' : (row.completionRate || 0) >= 60 ? '#409eff' : '#e6a23c'"
                  style="flex:1" />
                <span style="font-size:13px;font-weight:600;width:48px;text-align:right">{{ row.completionRate }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="通过率" width="200" align="center">
            <template #default="{ row }">
              <div style="display:flex;align-items:center;gap:8px">
                <el-progress :percentage="Math.round(row.passRate || 0)" :stroke-width="8"
                  :color="(row.passRate || 0) >= 85 ? '#67c23a' : (row.passRate || 0) >= 60 ? '#409eff' : '#e6a23c'"
                  style="flex:1" />
                <span style="font-size:13px;font-weight:600;width:48px;text-align:right">{{ row.passRate }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" link>查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 课程详情视图 -->
    <template v-else>
      <div class="stat-cards" style="margin-bottom:20px">
        <div class="stat-card"><div class="stat-label">课程代码</div><div class="stat-value primary" style="font-size:18px">{{ selectedCourse?.courseCode }}</div></div>
        <div class="stat-card"><div class="stat-label">开课班级</div><div class="stat-value success">{{ selectedCourse?.planCount }}<small> 个</small></div></div>
        <div class="stat-card"><div class="stat-label">选课人数</div><div class="stat-value primary">{{ selectedCourse?.studentCount }}<small> 人</small></div></div>
        <div class="stat-card"><div class="stat-label">完成率</div><div class="stat-value primary">{{ selectedCourse?.completionRate }}<small>%</small></div></div>
        <div class="stat-card"><div class="stat-label">通过率</div><div class="stat-value success">{{ selectedCourse?.passRate }}<small>%</small></div></div>
      </div>

      <!-- 各班级明细 -->
      <div class="card-wrapper" v-if="selectedCourse?.plans">
        <div class="card-title">各班级明细</div>
        <el-table :data="selectedCourse.plans" stripe>
          <el-table-column prop="className" label="班级" min-width="150" />
          <el-table-column prop="teacherName" label="授课教师" width="100" />
          <el-table-column prop="studentCount" label="选课人数" width="100" align="center" />
          <el-table-column prop="gradedCount" label="已出成绩" width="100" align="center" />
          <el-table-column label="完成率" width="200" align="center">
            <template #default="{ row }">
              <div style="display:flex;align-items:center;gap:8px">
                <el-progress :percentage="Math.round(row.completionRate || 0)" :stroke-width="8"
                  :color="(row.completionRate || 0) >= 80 ? '#67c23a' : '#409eff'" style="flex:1" />
                <span style="font-size:13px;font-weight:600;width:48px">{{ row.completionRate }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="通过率" width="200" align="center">
            <template #default="{ row }">
              <div style="display:flex;align-items:center;gap:8px">
                <el-progress :percentage="Math.round(row.passRate || 0)" :stroke-width="8"
                  :color="(row.passRate || 0) >= 85 ? '#67c23a' : '#e6a23c'" style="flex:1" />
                <span style="font-size:13px;font-weight:600;width:48px">{{ row.passRate }}%</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
