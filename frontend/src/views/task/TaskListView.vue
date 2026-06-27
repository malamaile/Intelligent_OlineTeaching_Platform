<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getTasks, getTaskDetail, submitTask, resubmitTask, getTaskResult } from '@/api/student'
import { ElMessage, ElMessageBox } from 'element-plus'

// ========== 列表 ==========
const loading = ref(false)
const tasks = ref([])

const searchForm = reactive({ type: '', status: '' })

const taskTypeOptions = [
  { label: '全部', value: '' },
  { label: '实验', value: 'EXPERIMENT' },
  { label: '实训', value: 'TRAINING' },
]

const statusOptions = [
  { label: '全部', value: '' },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '已提交', value: 'SUBMITTED' },
  { label: '已批改', value: 'GRADED' },
  { label: '已逾期', value: 'OVERDUE' },
  { label: '已退回', value: 'RETURNED' },
]

const statusConfig = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  IN_PROGRESS: { label: '进行中', type: 'primary' },
  SUBMITTED: { label: '已提交', type: 'warning' },
  GRADED: { label: '已批改', type: 'success' },
  OVERDUE: { label: '已逾期', type: 'danger' },
  RETURNED: { label: '已退回', type: 'danger' },
}

const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

async function fetchTasks() {
  loading.value = true
  try {
    const res = await getTasks({
      type: searchForm.type || undefined,
      status: searchForm.status || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    tasks.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchTasks()
}

function handleReset() {
  searchForm.type = ''
  searchForm.status = ''
  pagination.page = 1
  fetchTasks()
}

function handlePageChange(page) {
  pagination.page = page
  fetchTasks()
}

// ========== 任务详情弹窗 ==========
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

async function openDetail(task) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getTaskDetail(task.taskId)
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

// ========== 提交报告 ==========
const submitVisible = ref(false)
const submitting = ref(false)
const submitFormRef = ref(null)
const submitForm = reactive({
  content: '',
  reportFile: null,
  attachmentFiles: [],
})
const fileList = ref([])
const attachList = ref([])

const submitRules = {
  content: [{ required: true, message: '请输入实训过程描述', trigger: 'blur' }],
}

function openSubmit(task) {
  detail.value = { taskId: task.taskId, taskType: task.taskType, title: task.title }
  submitForm.content = ''
  submitForm.reportFile = null
  fileList.value = []
  attachList.value = []
  submitVisible.value = true
}

function handleReportFileChange(file) {
  submitForm.reportFile = file.raw
}

function handleAttachChange(file) {
  submitForm.attachmentFiles = attachList.value.map((f) => f.raw)
}

async function handleSubmit() {
  const valid = await submitFormRef.value.validate().catch(() => false)
  if (!valid) return

  // 检查截止时间
  if (detail.value?.deadline && new Date(detail.value.deadline) < new Date()) {
    await ElMessageBox.confirm('该任务已过截止时间，提交将被标记为逾期。确定提交吗？', '提示', {
      confirmButtonText: '确定提交',
      cancelButtonText: '取消',
      type: 'warning',
    })
  }

  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('content', submitForm.content)
    if (submitForm.reportFile) formData.append('reportFile', submitForm.reportFile)
    if (submitForm.attachmentFiles?.length) {
      submitForm.attachmentFiles.forEach((f) => formData.append('attachmentFiles', f))
    }
    const isResubmit = detail.value?.mySubmission?.status === 'RETURNED'
    if (isResubmit) {
      await resubmitTask(detail.value.taskId, formData)
    } else {
      await submitTask(detail.value.taskId, formData)
    }
    ElMessage.success('提交成功')
    submitVisible.value = false
    fetchTasks()
  } finally {
    submitting.value = false
  }
}

// ========== 查看批改结果 ==========
const resultVisible = ref(false)
const result = ref(null)

async function openResult(task) {
  resultVisible.value = true
  result.value = null
  try {
    const res = await getTaskResult(task.taskId)
    result.value = res.data
  } catch { /* ignore */ }
}

// ========== 格式化 ==========
function getDeadlineClass(deadline) {
  if (!deadline) return ''
  const d = new Date(deadline)
  const now = new Date()
  const diff = d - now
  if (diff < 0) return 'deadline-overdue'
  if (diff < 24 * 60 * 60 * 1000) return 'deadline-urgent'
  return ''
}

onMounted(fetchTasks)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">实验实训</h1>
      <p class="page-desc">查看班级实验、实训任务，在线完成任务并提交报告，查看批改结果</p>
    </div>

    <!-- 筛选 -->
    <div class="card-wrapper">
      <div class="search-bar">
        <el-select v-model="searchForm.type" style="width: 110px" @change="handleSearch">
          <el-option v-for="opt in taskTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="searchForm.status" style="width: 110px" @change="handleSearch">
          <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="title" label="任务名称" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="taskType" label="类型" width="80">
          <template #default="{ row }">{{ row.taskType === 'EXPERIMENT' ? '实验' : '实训' }}</template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" width="140" />
        <el-table-column prop="teacherName" label="教师" width="90" />
        <el-table-column prop="deadline" label="截止时间" width="160">
          <template #default="{ row }">
            <span :class="getDeadlineClass(row.deadline)">{{ row.deadline }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusConfig[row.status]?.type" size="small">
              {{ statusConfig[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="成绩" width="70">
          <template #default="{ row }">
            <span v-if="row.score !== null" :style="{ color: row.score >= 85 ? '#a2f07b' : row.score >= 60 ? '#409eff' : '#f56c6c', fontWeight: 600 }">{{ row.score }}</span>
            <span v-else class="no-score">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'NOT_STARTED' || row.status === 'IN_PROGRESS' || row.status === 'RETURNED'"
              type="primary" size="small"
              @click="openSubmit(row)"
            >
              {{ row.status === 'RETURNED' ? '重新提交' : '提交' }}
            </el-button>
            <el-button
              v-if="row.status === 'GRADED' || row.status === 'RETURNED'"
              type="info" size="small"
              @click="openResult(row)"
            >
              查看结果
            </el-button>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="pagination.total > 0"
        v-model:current-page="pagination.page"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="handlePageChange"
      />
    </div>

    <!-- ========== 任务详情弹窗 ========== -->
    <el-dialog v-model="detailVisible" title="任务详情" width="680px" destroy-on-close>
      <div v-loading="detailLoading" v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务名称" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.taskType === 'EXPERIMENT' ? '实验' : '实训' }}</el-descriptions-item>
          <el-descriptions-item label="课程">{{ detail.courseName }}</el-descriptions-item>
          <el-descriptions-item label="授课教师">{{ detail.teacherName }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">
            <span :class="getDeadlineClass(detail.deadline)">{{ detail.deadline }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h4>任务要求</h4>
          <div class="detail-description" v-html="detail.description || '暂无描述'" />
        </div>

        <div class="detail-section" v-if="detail.guideFiles?.length">
          <h4>指导文档</h4>
          <div v-for="f in detail.guideFiles" :key="f.fileId" class="guide-file">
            <el-icon><Document /></el-icon>
            <span>{{ f.name }}</span>
            <span class="file-size">{{ (f.fileSize / 1024).toFixed(1) }} KB</span>
            <el-button type="primary" text size="small" @click="window.open(f.downloadUrl)">下载</el-button>
          </div>
        </div>

        <div class="detail-section" v-if="detail.mySubmission">
          <h4>我的提交</h4>
          <el-tag :type="statusConfig[detail.mySubmission]?.type">
            {{ statusConfig[detail.status]?.label }}
          </el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- ========== 提交报告弹窗 ========== -->
    <el-dialog v-model="submitVisible" title="提交报告" width="600px" destroy-on-close>
      <div v-if="detail">
        <p class="submit-task-title">{{ detail.title }}</p>
        <el-form ref="submitFormRef" :model="submitForm" :rules="submitRules" label-width="100px">
          <el-form-item label="过程描述" prop="content">
            <el-input
              v-model="submitForm.content"
              type="textarea"
              :rows="6"
              placeholder="请描述实训/实验过程、步骤、遇到的问题及解决方案..."
            />
          </el-form-item>
          <el-form-item label="报告文件">
            <el-upload
              v-model:file-list="fileList"
              :limit="1"
              :on-change="handleReportFileChange"
              :auto-upload="false"
              accept=".pdf,.doc,.docx"
              drag
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽或<em>点击上传</em>报告文件</div>
              <template #tip>
                <div class="el-upload__tip">支持 PDF / Word 格式，最大 50MB</div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="附件">
            <el-upload
              v-model:file-list="attachList"
              :on-change="handleAttachChange"
              :auto-upload="false"
              multiple
              accept=".zip,.rar,.jpg,.png,.pdf,.doc,.docx"
            >
              <el-button type="primary" plain>选择附件</el-button>
              <template #tip>
                <div class="el-upload__tip">可上传代码、截图等辅助材料</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认提交</el-button>
      </template>
    </el-dialog>

    <!-- ========== 批改结果弹窗 ========== -->
    <el-dialog v-model="resultVisible" title="批改结果" width="520px">
      <div v-if="result">
        <div class="result-score">
          <span class="score-label">得分</span>
          <span class="score-num" :style="{ color: result.score >= 85 ? '#97fc64' : result.score >= 60 ? '#409eff' : '#f56c6c' }">{{ result.score }}</span>
          <span class="score-unit">/ 100</span>
        </div>
        <div class="result-meta">
          <span>批改教师：{{ result.teacherName }}</span>
          <span>批改时间：{{ result.gradedTime }}</span>
        </div>
        <div class="result-comment">
          <h4>教师评语</h4>
          <p>{{ result.comment || '暂无评语' }}</p>
        </div>
        <el-alert
          v-if="result.canResubmit"
          title="教师已退回此报告，你可以根据评语修改后重新提交"
          type="warning"
          show-icon
          :closable="false"
          style="margin-top: 12px"
        />
      </div>
      <el-empty v-else description="暂无批改结果" />
    </el-dialog>
  </div>
</template>

<style scoped>
.page-desc {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.deadline-urgent {
  color: #f7c477;
  font-weight: 500;
}

.deadline-overdue {
  color: #f47d7d;
  font-weight: 500;
}

.no-score {
  color: #c0c4cc;
}

/* 详情 */
.detail-section {
  margin-top: 20px;
}

.detail-section h4 {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.detail-description {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
}

.guide-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  font-size: 13px;
}

.file-size {
  color: #909399;
  font-size: 12px;
}

/* 提交 */
.submit-task-title {
  font-size: 14px;
  color: #409eff;
  margin-bottom: 16px;
  font-weight: 500;
}

/* 结果 */
.result-score {
  text-align: center;
  padding: 20px;
}

.score-label {
  font-size: 14px;
  color: #909399;
  margin-right: 8px;
}

.score-num {
  font-size: 48px;
  font-weight: 700;
}

.score-unit {
  font-size: 16px;
  color: #909399;
}

.result-meta {
  display: flex;
  justify-content: center;
  gap: 20px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}

.result-comment {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
}

.result-comment h4 {
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
}

.result-comment p {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.el-upload__tip {
  margin-top: 4px;
}
</style>
