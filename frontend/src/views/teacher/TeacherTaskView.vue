<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherTasks, createTask, deleteTask, getTaskSubmissions, gradeSubmission, returnSubmission } from '@/api/teacher'

const loading = ref(false)

const tasks = ref([])
const searchForm = reactive({ taskType: '', auditStatus: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const auditStatusConfig = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

async function fetchTasks() {
  loading.value = true
  try {
    const res = await getTeacherTasks({
      taskType: searchForm.taskType || undefined,
      auditStatus: searchForm.auditStatus || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    tasks.value = res.data.records || res.data
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// 创建任务弹窗
const dialogVisible = ref(false)
const saving = ref(false)
const taskFormRef = ref(null)
const taskForm = reactive({
  taskType: 'EXPERIMENT', title: '', courseName: '', className: '',
  description: '', startTime: '', endTime: '', maxRetryCount: 2,
})

const rules = {
  title: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  courseName: [{ required: true, message: '请选择课程', trigger: 'change' }],
  className: [{ required: true, message: '请选择班级', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
}

function openCreate() {
  Object.assign(taskForm, { taskType: 'EXPERIMENT', title: '', courseName: '', className: '', description: '', startTime: '', endTime: '', maxRetryCount: 2 })
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await taskFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await createTask(taskForm)
    ElMessage.success('创建成功，请等待管理员审核')
    dialogVisible.value = false
    await fetchTasks()
  } catch {} finally { saving.value = false }
}

// 批阅弹窗
const reviewVisible = ref(false)
const reviewTask = ref(null)
const submissions = ref([])
const grading = ref(false)

async function openReview(row) {
  reviewTask.value = row
  reviewVisible.value = true
  try {
    const res = await getTaskSubmissions(row.taskId, { page: 1, pageSize: 100 })
    submissions.value = res.data.records || res.data || []
  } catch {
    submissions.value = []
  }
}

async function handleGrade(sub, action) {
  if (action === 'PASS') {
    try {
      const { value: score } = await ElMessageBox.prompt('请输入分数（0-100）', '批阅评分', {
        inputPattern: /^(?:100|[1-9]?\d)$/,
        inputErrorMessage: '请输入0-100的整数',
      })
      grading.value = true
      await gradeSubmission(sub.submissionId, { score: Number(score), comment: '', action: 'PASS' })
      ElMessage.success('批阅完成')
    } catch { return }
    finally { grading.value = false }
  } else {
    try {
      const { value: reason } = await ElMessageBox.prompt('请输入退回原因', '退回提交', {
        inputErrorMessage: '请填写退回原因',
      })
      if (!reason) return
      grading.value = true
      await returnSubmission(sub.submissionId, { returnReason: reason })
      ElMessage.success('已退回')
    } catch { return }
    finally { grading.value = false }
  }
  await openReview(reviewTask.value)
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除该任务吗？', '确认删除', { type: 'warning' })
  await deleteTask(row.taskId)
  ElMessage.success('已删除')
  await fetchTasks()
}

onMounted(fetchTasks)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">实训实验计划管理</h1>
      <el-button type="primary" @click="openCreate">创建任务</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-select v-model="searchForm.taskType" placeholder="任务类型" clearable style="width:110px">
          <el-option label="实验" value="EXPERIMENT" />
          <el-option label="实训" value="TRAINING" />
        </el-select>
        <el-select v-model="searchForm.auditStatus" placeholder="审核状态" clearable style="width:110px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
        </el-select>
        <el-button type="primary" @click="fetchTasks">查询</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="title" label="任务名称" min-width="160" />
        <el-table-column prop="taskType" label="类型" width="70">
          <template #default="{ row }">{{ row.taskType === 'EXPERIMENT' ? '实验' : '实训' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" width="130" />
        <el-table-column prop="className" label="班级" width="140" />
        <el-table-column label="提交进度" width="140">
          <template #default="{ row }">
            <span>{{ row.gradedCount || 0 }}/{{ row.submittedCount || 0 }}/{{ row.studentCount || 0 }}</span>
            <span style="font-size:12px;color:#909399"> （批/交/总）</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="200">
          <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column label="审核" width="90">
          <template #default="{ row }">
            <el-tag :type="auditStatusConfig[row.auditStatus]?.type" size="small">
              {{ auditStatusConfig[row.auditStatus]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openReview(row)" :disabled="(row.submittedCount || 0) === 0">批阅</el-button>
            <el-button size="small" @click="openCreate">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
        @change="fetchTasks"
      />
    </div>

    <!-- 创建任务弹窗 -->
    <el-dialog v-model="dialogVisible" title="创建实验/实训任务" width="640px" destroy-on-close>
      <el-form ref="taskFormRef" :model="taskForm" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="任务类型">
              <el-radio-group v-model="taskForm.taskType">
                <el-radio-button value="EXPERIMENT">实验</el-radio-button>
                <el-radio-button value="TRAINING">实训</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="任务名称" prop="title">
              <el-input v-model="taskForm.title" placeholder="如：实验二：排序算法" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属课程" prop="courseName">
              <el-select v-model="taskForm.courseName" style="width:100%" placeholder="选择课程">
                <el-option label="Java程序设计" value="Java程序设计" />
                <el-option label="数据结构" value="数据结构" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标班级" prop="className">
              <el-select v-model="taskForm.className" style="width:100%" placeholder="选择班级">
                <el-option label="软件工程2024-1班" value="软件工程2024-1班" />
                <el-option label="软件工程2024-2班" value="软件工程2024-2班" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker v-model="taskForm.startTime" type="datetime" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="截止时间" prop="endTime">
              <el-date-picker v-model="taskForm.endTime" type="datetime" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="任务要求">
          <el-input v-model="taskForm.description" type="textarea" :rows="4" placeholder="请输入任务要求、实验步骤等..." />
        </el-form-item>
        <el-form-item label="重交次数">
          <el-input-number v-model="taskForm.maxRetryCount" :min="0" :max="5" />
          <span style="margin-left:8px;font-size:12px;color:#909399">学生可重新提交的最大次数</span>
        </el-form-item>
        <el-form-item label="指导文档">
          <el-upload :auto-upload="false" drag>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em>指导文档</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 批阅弹窗 -->
    <el-dialog v-model="reviewVisible" :title="reviewTask?.title + ' — 批阅'" width="780px">
      <el-table :data="submissions" stripe v-loading="grading">
        <el-table-column prop="userName" label="学生" width="80" />
        <el-table-column prop="submitTime" label="提交时间" width="150" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'GRADED' ? 'success' : row.status === 'RETURNED' ? 'danger' : 'warning'" size="small">
              {{ row.status === 'GRADED' ? '已批改' : row.status === 'RETURNED' ? '已退回' : '待批阅' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分数" width="70">
          <template #default="{ row }">{{ row.score ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="comment" label="评语" min-width="120">
          <template #default="{ row }">{{ row.comment || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <template v-if="row.status !== 'GRADED'">
              <el-button size="small" type="success" @click="handleGrade(row, 'PASS')">通过</el-button>
              <el-button size="small" type="danger" @click="handleGrade(row, 'RETURN')">退回</el-button>
            </template>
            <span v-else style="color:#67c23a">已批阅</span>
          </template>
        </el-table-column>
      </el-table>
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
