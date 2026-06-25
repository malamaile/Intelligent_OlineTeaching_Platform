<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherCourses, createCourse, updateCourse, deleteCourse, saveGrades } from '@/api/teacher'

const loading = ref(false)
const courses = ref([])
const searchForm = reactive({ keyword: '', auditStatus: '', semester: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const auditStatusConfig = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

async function fetchCourses() {
  loading.value = true
  try {
    const res = await getTeacherCourses({
      keyword: searchForm.keyword || undefined,
      auditStatus: searchForm.auditStatus || undefined,
      semester: searchForm.semester || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    courses.value = res.data.records || res.data
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// 创建/编辑弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('创建开课计划')
const saving = ref(false)
const courseFormRef = ref(null)
const isEditing = ref(false)
const courseForm = reactive({
  courseId: null,
  courseName: '', courseCode: '', semester: '2025-2026-2',
  className: '', totalHours: 48, weeklyHours: 4,
  startDate: '', endDate: '', description: '',
})

const rules = {
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  courseCode: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
  className: [{ required: true, message: '请输入授课班级', trigger: 'blur' }],
}

function openCreate() {
  isEditing.value = false
  dialogTitle.value = '创建开课计划'
  Object.assign(courseForm, { courseId: null, courseName: '', courseCode: '', className: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row) {
  isEditing.value = true
  dialogTitle.value = '编辑开课计划'
  Object.assign(courseForm, { ...row })
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await courseFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEditing.value) {
      await updateCourse(courseForm.courseId, courseForm)
      ElMessage.success('修改成功')
    } else {
      await createCourse(courseForm)
      ElMessage.success('创建成功，请等待管理员审核')
    }
    dialogVisible.value = false
    await fetchCourses()
  } catch {} finally { saving.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '确认删除', { type: 'warning' })
  await deleteCourse(row.courseId)
  ElMessage.success('已删除')
  await fetchCourses()
}

// 成绩管理弹窗
const gradeVisible = ref(false)
const gradeCourse = ref(null)
const gradeList = ref([])

async function openGrades(row) {
  gradeCourse.value = row
  gradeVisible.value = true
  try {
    const { getCourseProgress } = await import('@/api/teacher')
    const res = await getCourseProgress(row.courseId)
    gradeList.value = (res.data && (res.data.students || res.data.records)) || []
  } catch {
    gradeList.value = []
  }
}

async function handleGradeSave() {
  saving.value = true
  try {
    await saveGrades(gradeCourse.value.courseId, gradeList.value.map(s => ({
      studentId: s.studentId || s.userId,
      usualGrade: s.usualGrade ?? s.dailyScore,
      examGrade: s.examGrade ?? s.examScore,
      experimentGrade: s.experimentGrade ?? s.experimentScore,
    })))
    ElMessage.success('成绩保存成功')
    gradeVisible.value = false
  } catch {} finally { saving.value = false }
}

// 导出成绩
function handleExport(row) {
  ElMessage.info('导出功能开发中')
}

onMounted(fetchCourses)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">课程计划管理</h1>
      <el-button type="primary" @click="openCreate">创建开课计划</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-input v-model="searchForm.keyword" placeholder="搜索课程" clearable style="width: 200px" />
        <el-select v-model="searchForm.auditStatus" placeholder="审核状态" clearable style="width: 120px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="fetchCourses">查询</el-button>
      </div>

      <el-table :data="courses" v-loading="loading" stripe>
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="courseCode" label="课程代码" width="100" />
        <el-table-column prop="semester" label="学期" width="120" />
        <el-table-column prop="className" label="授课班级" width="140" />
        <el-table-column prop="studentCount" label="学生数" width="80" />
        <el-table-column label="课时" width="100">
          <template #default="{ row }">{{ row.completedHours || 0 }}/{{ row.totalHours }} ({{ row.weeklyHours }}课时/周)</template>
        </el-table-column>
        <el-table-column label="进度" width="100">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="6" :show-text="false" />
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditStatusConfig[row.auditStatus]?.type" size="small">
              {{ auditStatusConfig[row.auditStatus]?.label || row.auditStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)" :disabled="row.auditStatus === 'APPROVED'">编辑</el-button>
            <el-button size="small" type="warning" @click="openGrades(row)">成绩</el-button>
            <el-button size="small" type="success" @click="handleExport(row)">导出</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.auditStatus === 'APPROVED'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="pagination.total > pagination.pageSize"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="fetchCourses"
      />
    </div>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="courseFormRef" :model="courseForm" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="课程名称" prop="courseName">
              <el-input v-model="courseForm.courseName" placeholder="如：Java程序设计" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课程代码" prop="courseCode">
              <el-input v-model="courseForm.courseCode" placeholder="如：CS101" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="授课学期">
              <el-select v-model="courseForm.semester" style="width:100%">
                <el-option label="2025-2026-2" value="2025-2026-2" />
                <el-option label="2026-2027-1" value="2026-2027-1" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授课班级" prop="className">
              <el-input v-model="courseForm.className" placeholder="如：软件工程2024-1班" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="总课时">
              <el-input-number v-model="courseForm.totalHours" :min="1" :max="200" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="周课时">
              <el-input-number v-model="courseForm.weeklyHours" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开课日期">
              <el-date-picker v-model="courseForm.startDate" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程描述">
          <el-input v-model="courseForm.description" type="textarea" :rows="3" placeholder="请输入课程描述..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 成绩管理弹窗 -->
    <el-dialog v-model="gradeVisible" :title="gradeCourse?.courseName + ' — 成绩管理'" width="700px">
      <el-table :data="gradeList" stripe>
        <el-table-column prop="userName" label="姓名" width="100" />
        <el-table-column label="平时成绩" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.usualGrade" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="考试成绩" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.examGrade" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="实验成绩" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.experimentGrade" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="总评" width="80">
          <template #default="{ row }">
            {{ Math.round(((row.usualGrade || 0) * 0.3 + (row.examGrade || 0) * 0.5 + (row.experimentGrade || 0) * 0.2) * 10) / 10 || '-' }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleGradeSave">保存成绩</el-button>
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
</style>
