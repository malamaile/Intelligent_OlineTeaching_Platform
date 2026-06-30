<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherTasks, getTeacherCourses, createTask, updateTask, deleteTask, getTaskSubmissions, gradeSubmission, returnSubmission } from '@/api/teacher'
import { uploadFile } from '@/api/common'

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

// 创建/编辑任务弹窗
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingTaskId = ref(null)
const saving = ref(false)
const taskFormRef = ref(null)
const courseOptions = ref([])
const taskForm = reactive({
  taskType: 'EXPERIMENT', title: '', courseName: '', className: '', courseId: null,
  guideFileUrl: '', description: '', startTime: '', endTime: '', maxRetryCount: 2,
})

const rules = {
  title: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  courseName: [{ required: true, message: '请选择课程', trigger: 'change' }],
  className: [{ required: true, message: '请选择班级', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
}

async function loadCourseOptions() {
  try {
    const res = await getTeacherCourses({ page: 1, pageSize: 100 })
    courseOptions.value = (res.data.records || res.data || []).map(c => ({
      courseName: c.courseName,
      className: c.className,
      courseId: c.courseId || c.coursePlanId,
    }))
  } catch (e) { console.error('加载课程失败:', e); courseOptions.value = [] }
}

async function openCreate() {
  isEditing.value = false
  editingTaskId.value = null
  Object.assign(taskForm, { taskType: 'EXPERIMENT', title: '', courseName: '', className: '', courseId: null, guideFileUrl: '', description: '', startTime: '', endTime: '', maxRetryCount: 2 })
  initGuideFiles()
  await loadCourseOptions()
  dialogVisible.value = true
}

async function openEdit(row) {
  isEditing.value = true
  editingTaskId.value = row.taskId
  Object.assign(taskForm, {
    taskType: row.taskType || 'EXPERIMENT',
    title: row.title || '',
    courseName: row.courseName || '',
    className: row.className || '',
    courseId: row.courseId || null,
    guideFileUrl: row.guideFileUrl || '',
    description: row.description || '',
    startTime: row.startTime || '',
    endTime: row.endTime || '',
    maxRetryCount: row.maxRetryCount ?? 2,
  })
  initGuideFiles()
  await loadCourseOptions()
  dialogVisible.value = true
}

function onCourseSelect(course) {
  taskForm.className = course.className || ''
  taskForm.courseId = course.courseId
}

// 指导文档上传（支持多文件，存储为 JSON 数组字符串）
const guideFileList = ref([])
const uploadingFile = ref(false)

function initGuideFiles() {
  if (taskForm.guideFileUrl) {
    try {
      const arr = JSON.parse(taskForm.guideFileUrl)
      guideFileList.value = arr.map((item, i) => {
        if (typeof item === 'object' && item.url) {
          return { uid: i, name: item.name || item.url.split('/').pop(), url: item.url }
        }
        return { uid: i, name: item.split('/').pop() || '文档', url: item }
      })
    } catch {
      guideFileList.value = [{ uid: 0, name: taskForm.guideFileUrl.split('/').pop() || '文档', url: taskForm.guideFileUrl }]
    }
  } else {
    guideFileList.value = []
  }
}

function parseGuideFiles(guideFileUrl) {
  if (!guideFileUrl) return []
  try {
    const arr = JSON.parse(guideFileUrl)
    return arr.map(item => {
      if (typeof item === 'object' && item.url) {
        return { name: item.name || item.url.split('/').pop(), url: item.url }
      }
      return { name: item.split('/').pop() || '文档', url: item }
    })
  } catch {
    return [{ name: guideFileUrl.split('/').pop() || '文档', url: guideFileUrl }]
  }
}

function syncGuideFiles() {
  taskForm.guideFileUrl = JSON.stringify(guideFileList.value.map(f => ({ name: f.name, url: f.url })))
}

async function handleGuideUpload(options) {
  uploadingFile.value = true
  try {
    // 弹窗确认文件名，默认填充原始文件名
    const originalName = options.file.name
    const { value: customName } = await ElMessageBox.prompt('可修改文件名', '上传文件', {
      inputValue: originalName,
      confirmButtonText: '确认上传',
      cancelButtonText: '取消',
    })
    if (!customName) { uploadingFile.value = false; return }
    const formData = new FormData()
    formData.append('file', options.file)
    formData.append('module', 'guide')
    formData.append('fileName', customName)
    const res = await uploadFile(formData)
    const url = res.data.fileUrl
    guideFileList.value.push({ uid: Date.now(), name: customName, url })
    syncGuideFiles()
  } catch { /* 用户取消 */ } finally { uploadingFile.value = false }
}

function handleGuideRemove(file) {
  guideFileList.value = guideFileList.value.filter(f => f.uid !== file.uid)
  syncGuideFiles()
}

function handleGuidePreview(file) {
  const url = file.url
  const ext = url.split('.').pop().toLowerCase()
  // PDF 直接浏览器打开
  if (ext === 'pdf') {
    window.open(url, '_blank')
    return
  }
  // Office 文件用微软在线预览
  if (['doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx'].includes(ext)) {
    const fullUrl = location.origin + url
    window.open('https://view.officeapps.live.com/op/view.aspx?src=' + encodeURIComponent(fullUrl), '_blank')
    return
  }
  // 其他文件直接打开
  window.open(url, '_blank')
}

function formatDate(str) {
  if (!str) return '-'
  const d = new Date(str)
  const y = d.getFullYear()
  const m = d.getMonth() + 1
  const day = d.getDate()
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}年${m}月${day}日 ${h}:${min}`
}

async function handleSave() {
  const valid = await taskFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEditing.value) {
      await updateTask(editingTaskId.value, taskForm)
      ElMessage.success('修改成功')
    } else {
      await createTask(taskForm)
      ElMessage.success('创建成功，请等待管理员审核')
    }
    dialogVisible.value = false
    await fetchTasks()
  } catch (e) {
    console.error('提交失败:', e)
  } finally { saving.value = false }
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

// 详情弹窗
const detailVisible = ref(false)
const detailTask = ref(null)

function openDetail(row) {
  detailTask.value = row
  detailVisible.value = true
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
        <el-select v-model="searchForm.taskType" placeholder="任务类型" clearable style="width:100px">
          <el-option label="实验" value="EXPERIMENT" />
          <el-option label="实训" value="TRAINING" />
        </el-select>
        <el-select v-model="searchForm.auditStatus" placeholder="审核状态" clearable style="width:100px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
        </el-select>
        <el-button type="primary" @click="fetchTasks">查询</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe style="width:100%" :header-cell-style="{ textAlign:'center' }">
        <el-table-column label="任务名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="taskType" label="类型" width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="row.taskType === 'EXPERIMENT' ? 'success' : 'warning'" size="small">{{ row.taskType === 'EXPERIMENT' ? '实验' : '实训' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="className" label="班级" min-width="140" align="center" />
        <el-table-column label="批/交/总" width="110" align="center">
          <template #default="{ row }">
            <span class="progress-text">{{ row.gradedCount || 0 }}/{{ row.submittedCount || 0 }}/{{ row.studentCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="起止时间" width="210" align="center">
          <template #default="{ row }">
            <div class="time-cell">
              <div>{{ formatDate(row.startTime) }}</div>
              <div class="time-sep">至</div>
              <div>{{ formatDate(row.endTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="审核" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="auditStatusConfig[row.auditStatus]?.type" size="small">
              {{ auditStatusConfig[row.auditStatus]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" type="success" @click="openReview(row)" :disabled="(row.submittedCount || 0) === 0">批阅</el-button>
              <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </div>
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
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑实验/实训任务' : '创建实验/实训任务'" width="640px" destroy-on-close>
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
              <el-select v-model="taskForm.courseName" style="width:100%" placeholder="选择课程" @change="(val) => onCourseSelect(courseOptions.find(c => c.courseName === val))">
                <el-option v-for="c in courseOptions" :key="c.courseId" :label="c.courseName" :value="c.courseName" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标班级" prop="className">
              <el-input v-model="taskForm.className" placeholder="选择课程后自动填充" readonly />
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
          <el-upload
            :file-list="guideFileList"
            :http-request="handleGuideUpload"
            :on-remove="handleGuideRemove"
            :on-preview="handleGuidePreview"
            accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx"
            list-type="text"
          >
            <el-button size="small" type="primary" :loading="uploadingFile">上传文档</el-button>
            <template #tip>
              <span class="upload-tip">支持 PDF/Word/PPT/Excel，可上传多个文件</span>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- 批阅弹窗 -->
    <el-dialog v-model="reviewVisible" :title="reviewTask?.title + ' — 批阅'" width="620px">
      <el-table :data="submissions" stripe v-loading="grading" :header-cell-style="{ textAlign:'center' }">
        <el-table-column prop="userName" label="学生" width="70" align="center" />
        <el-table-column label="提交时间" width="140" align="center">
          <template #default="{ row }">{{ formatDate(row.submitTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="75" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'GRADED' ? 'success' : row.status === 'RETURNED' ? 'danger' : 'warning'" size="small">
              {{ row.status === 'GRADED' ? '已批' : row.status === 'RETURNED' ? '退回' : '待批' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分数" width="60" align="center" />
        <el-table-column prop="comment" label="评语" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <template v-if="row.status !== 'GRADED'">
                <el-button size="small" type="success" @click="handleGrade(row, 'PASS')">通过</el-button>
                <el-button size="small" type="danger" @click="handleGrade(row, 'RETURN')">退回</el-button>
              </template>
              <span v-else style="color:#67c23a;font-size:13px">已批阅</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 任务详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailTask?.title" width="680px">
      <el-descriptions v-if="detailTask" :column="2" border size="small">
        <el-descriptions-item label="任务名称" :span="2">{{ detailTask.title }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">
          <el-tag :type="detailTask.taskType === 'EXPERIMENT' ? 'success' : 'warning'" size="small">
            {{ detailTask.taskType === 'EXPERIMENT' ? '实验' : '实训' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="auditStatusConfig[detailTask.auditStatus]?.type" size="small">
            {{ auditStatusConfig[detailTask.auditStatus]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="所属课程">{{ detailTask.courseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标班级">{{ detailTask.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDate(detailTask.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDate(detailTask.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="提交进度" :span="2">
          已批 {{ detailTask.gradedCount || 0 }} / 已交 {{ detailTask.submittedCount || 0 }} / 总人数 {{ detailTask.studentCount || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="任务描述" :span="2">{{ detailTask.description || '暂无描述' }}</el-descriptions-item>
        <el-descriptions-item label="指导文档" :span="2">
          <template v-if="detailTask.guideFileUrl">
            <div v-for="(f, i) in parseGuideFiles(detailTask.guideFileUrl)" :key="i">
              <el-link type="primary" @click="handleGuidePreview(f)">{{ f.name }}</el-link>
            </div>
          </template>
          <span v-else style="color:#909399">暂无</span>
        </el-descriptions-item>
      </el-descriptions>
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

.search-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}

.progress-text {
  font-weight: 600;
  font-size: 13px;
}

.time-cell {
  font-size: 12px;
  line-height: 1.6;
}

.time-sep {
  color: #909399;
  font-size: 11px;
}

.action-btns {
  display: flex;
  gap: 6px;
  justify-content: center;
}

:deep(.el-descriptions__label) {
  white-space: nowrap;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
</style>
