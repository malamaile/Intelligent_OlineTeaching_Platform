<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Document, WarningFilled, TopRight, Download, Close } from '@element-plus/icons-vue'
import { getTeacherTasks, getTeacherCourses, createTask, updateTask, deleteTask, getTaskSubmissions, gradeSubmission, returnSubmission } from '@/api/teacher'
import { uploadFile } from '@/api/common'

const route = useRoute()
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
      courseName: c.courseName, className: c.className, courseId: c.courseId || c.coursePlanId,
    }))
  } catch { courseOptions.value = [] }
}

async function openCreate() {
  isEditing.value = false; editingTaskId.value = null
  Object.assign(taskForm, { taskType: 'EXPERIMENT', title: '', courseName: '', className: '', courseId: null, guideFileUrl: '', description: '', startTime: '', endTime: '', maxRetryCount: 2 })
  initGuideFiles(); await loadCourseOptions(); dialogVisible.value = true
}

async function openEdit(row) {
  isEditing.value = true; editingTaskId.value = row.taskId
  Object.assign(taskForm, { taskType: row.taskType || 'EXPERIMENT', title: row.title || '', courseName: row.courseName || '', className: row.className || '', courseId: row.courseId || null, guideFileUrl: row.guideFileUrl || '', description: row.description || '', startTime: row.startTime || '', endTime: row.endTime || '', maxRetryCount: row.maxRetryCount ?? 2 })
  initGuideFiles(); await loadCourseOptions(); dialogVisible.value = true
}

function onCourseSelect(course) {
  taskForm.className = course.className || ''
  taskForm.courseId = course.courseId
}

// 指导文档上传
const guideFileList = ref([])
const uploadingFile = ref(false)

function initGuideFiles() {
  if (taskForm.guideFileUrl) {
    try { const arr = JSON.parse(taskForm.guideFileUrl); guideFileList.value = arr.map((item, i) => typeof item === 'object' && item.url ? { uid: i, name: item.name || item.url.split('/').pop(), url: item.url } : { uid: i, name: item.split('/').pop() || '文档', url: item }) }
    catch { guideFileList.value = [{ uid: 0, name: taskForm.guideFileUrl.split('/').pop() || '文档', url: taskForm.guideFileUrl }] }
  } else { guideFileList.value = [] }
}

function parseGuideFiles(guideFileUrl) {
  if (!guideFileUrl) return []
  try { const arr = JSON.parse(guideFileUrl); return arr.map(item => typeof item === 'object' && item.url ? { name: item.name || item.url.split('/').pop(), url: item.url } : { name: item.split('/').pop() || '文档', url: item }) }
  catch { return [{ name: guideFileUrl.split('/').pop() || '文档', url: guideFileUrl }] }
}

function syncGuideFiles() { taskForm.guideFileUrl = JSON.stringify(guideFileList.value.map(f => ({ name: f.name, url: f.url }))) }

async function handleGuideUpload(options) {
  uploadingFile.value = true
  try {
    const originalName = options.file.name
    const { value: customName } = await ElMessageBox.prompt('可修改文件名', '上传文件', { inputValue: originalName, confirmButtonText: '确认上传', cancelButtonText: '取消' })
    if (!customName) { uploadingFile.value = false; return }
    const formData = new FormData(); formData.append('file', options.file); formData.append('module', 'guide'); formData.append('fileName', customName)
    const res = await uploadFile(formData)
    guideFileList.value.push({ uid: Date.now(), name: customName, url: res.data.fileUrl })
    syncGuideFiles()
  } catch {} finally { uploadingFile.value = false }
}

function handleGuideRemove(file) { guideFileList.value = guideFileList.value.filter(f => f.uid !== file.uid); syncGuideFiles() }

// 文件预览
const fileBaseUrl = import.meta.env.DEV ? 'http://localhost:8080' : ''

function getFileExt(url) { if (!url) return ''; try { const p = url.split('?')[0]; const n = p.split('/').pop() || ''; const e = n.split('.').pop(); return e ? e.toLowerCase() : '' } catch { return '' } }
function isOfficeFile(ext) { return ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ext) }
function isPreviewableInBrowser(ext) { return ['pdf', 'jpg', 'jpeg', 'png', 'gif', 'bmp', 'svg', 'webp', 'txt', 'md', 'csv', 'json', 'xml', 'html', 'log'].includes(ext) }

const previewVisible = ref(false)
const previewUrl = ref('')
const previewFileName = ref('')
const previewFileExt = ref('')

function openFilePreview(url, fileName) {
  if (!url) return
  previewUrl.value = url.startsWith('http') ? url : (fileBaseUrl + url)
  previewFileName.value = fileName || url.split('/').pop() || '文件'
  previewFileExt.value = getFileExt(url)
  previewVisible.value = true
}

function handleGuidePreview(file) { openFilePreview(file.url, file.name) }

function openInNewWindow(url) {
  if (!url) return
  const w = window.open(url.startsWith('http') ? url : (fileBaseUrl + url), '_blank')
  if (!w) ElMessage.warning('浏览器已拦截弹窗，请允许本站弹出窗口')
}

async function downloadFile(url, fileName) {
  if (!url) return
  const fullUrl = url.startsWith('http') ? url : (fileBaseUrl + url)
  try {
    const res = await fetch(fullUrl); if (!res.ok) throw new Error('fail')
    const blob = await res.blob(); const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a'); a.href = blobUrl; a.download = fileName || url.split('/').pop() || 'download'
    document.body.appendChild(a); a.click(); document.body.removeChild(a); setTimeout(() => URL.revokeObjectURL(blobUrl), 100)
    ElMessage.success('下载完成')
  } catch { window.open(fullUrl, '_blank') }
}

function formatDate(str) {
  if (!str) return '-'
  const d = new Date(str); const y = d.getFullYear(); const m = d.getMonth() + 1; const day = d.getDate()
  const h = String(d.getHours()).padStart(2, '0'); const min = String(d.getMinutes()).padStart(2, '0')
  return `${y}年${m}月${day}日 ${h}:${min}`
}

async function handleSave() {
  const valid = await taskFormRef.value.validate().catch(() => false); if (!valid) return
  saving.value = true
  try {
    if (isEditing.value) { await updateTask(editingTaskId.value, taskForm); ElMessage.success('修改成功') }
    else { await createTask(taskForm); ElMessage.success('创建成功，请等待管理员审核') }
    dialogVisible.value = false; await fetchTasks()
  } catch {} finally { saving.value = false }
}

// 批阅弹窗
const reviewVisible = ref(false)
const reviewTask = ref(null)
const submissions = ref([])
const grading = ref(false)

async function openReview(row) {
  reviewTask.value = row; reviewVisible.value = true
  try { const res = await getTaskSubmissions(row.taskId, { page: 1, pageSize: 100 }); submissions.value = res.data.records || res.data || [] }
  catch { submissions.value = [] }
}

// 提交详情
const submissionDetailVisible = ref(false)
const currentSubmission = ref(null)

function openSubmissionDetail(row) { currentSubmission.value = row; submissionDetailVisible.value = true }

// 批阅打分弹窗
const gradeDialogVisible = ref(false)
const gradeAction = ref('')
const currentGradingSub = ref(null)
const gradeForm = reactive({ score: null, comment: '' })

function openGradeDialog(sub, action) {
  currentGradingSub.value = sub; gradeAction.value = action; gradeForm.score = null; gradeForm.comment = ''
  gradeDialogVisible.value = true
}

async function confirmGrade() {
  if (gradeAction.value === 'PASS') { if (gradeForm.score == null || gradeForm.score < 0 || gradeForm.score > 100) { ElMessage.warning('请输入0-100的分数'); return } }
  else { if (!gradeForm.comment || !gradeForm.comment.trim()) { ElMessage.warning('请输入退回原因'); return } }
  grading.value = true; gradeDialogVisible.value = false
  try {
    if (gradeAction.value === 'PASS') { await gradeSubmission(currentGradingSub.value.submissionId, { score: Number(gradeForm.score), comment: gradeForm.comment || '', action: 'PASS' }); ElMessage.success('批阅完成') }
    else { await returnSubmission(currentGradingSub.value.submissionId, { returnReason: gradeForm.comment }); ElMessage.success('已退回') }
    await openReview(reviewTask.value)
  } finally { grading.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除该任务吗？', '确认删除', { type: 'warning' })
  await deleteTask(row.taskId); ElMessage.success('已删除'); await fetchTasks()
}

const detailVisible = ref(false)
const detailTask = ref(null)
function openDetail(row) { detailTask.value = row; detailVisible.value = true }

onMounted(async () => {
  await fetchTasks()
  const targetTaskId = route.query.taskId
  if (targetTaskId) { const task = tasks.value.find(t => String(t.taskId) === String(targetTaskId)); if (task && (task.submittedCount || 0) > 0) openReview(task) }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">实训实验计划管理</h1>
      <el-button type="primary" @click="openCreate()">创建任务</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-select v-model="searchForm.taskType" placeholder="任务类型" clearable style="width:100px">
          <el-option label="实验" value="EXPERIMENT" /><el-option label="实训" value="TRAINING" />
        </el-select>
        <el-select v-model="searchForm.auditStatus" placeholder="审核状态" clearable style="width:100px">
          <el-option label="待审核" value="PENDING" /><el-option label="已通过" value="APPROVED" />
        </el-select>
        <el-button type="primary" @click="fetchTasks">查询</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe style="width:100%" :header-cell-style="{ textAlign:'center' }">
        <el-table-column label="任务名称" min-width="150" show-overflow-tooltip>
          <template #default="{ row }"><el-link type="primary" @click="openDetail(row)">{{ row.title }}</el-link></template>
        </el-table-column>
        <el-table-column prop="taskType" label="类型" width="65" align="center">
          <template #default="{ row }"><el-tag :type="row.taskType==='EXPERIMENT'?'success':'warning'" size="small">{{ row.taskType==='EXPERIMENT'?'实验':'实训' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="className" label="班级" min-width="140" align="center" />
        <el-table-column label="批/交/总" width="110" align="center">
          <template #default="{ row }"><span class="progress-text">{{ row.gradedCount||0 }}/{{ row.submittedCount||0 }}/{{ row.studentCount||0 }}</span></template>
        </el-table-column>
        <el-table-column label="起止时间" width="210" align="center">
          <template #default="{ row }"><div class="time-cell"><div>{{ formatDate(row.startTime) }}</div><div class="time-sep">至</div><div>{{ formatDate(row.endTime) }}</div></div></template>
        </el-table-column>
        <el-table-column label="审核" width="80" align="center">
          <template #default="{ row }"><el-tag :type="auditStatusConfig[row.auditStatus]?.type" size="small">{{ auditStatusConfig[row.auditStatus]?.label }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <el-button size="small" type="success" @click="openReview(row)" :disabled="(row.submittedCount||0)===0">批阅</el-button>
              <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-if="pagination.total > pagination.pageSize" v-model:current-page="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" layout="total, sizes, prev, pager, next" style="margin-top:16px;justify-content:flex-end" @change="fetchTasks" />
    </div>

    <!-- 创建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing?'编辑实验/实训任务':'创建实验/实训任务'" width="640px" destroy-on-close>
      <el-form ref="taskFormRef" :model="taskForm" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="任务类型"><el-radio-group v-model="taskForm.taskType"><el-radio-button value="EXPERIMENT">实验</el-radio-button><el-radio-button value="TRAINING">实训</el-radio-button></el-radio-group></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="任务名称" prop="title"><el-input v-model="taskForm.title" placeholder="如：实验二：排序算法" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="所属课程" prop="courseName"><el-select v-model="taskForm.courseName" style="width:100%" placeholder="选择课程" @change="(val) => onCourseSelect(courseOptions.find(c => c.courseName === val))"><el-option v-for="c in courseOptions" :key="c.courseId" :label="c.courseName" :value="c.courseName" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标班级" prop="className"><el-input v-model="taskForm.className" placeholder="选择课程后自动填充" readonly /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="开始时间" prop="startTime"><el-date-picker v-model="taskForm.startTime" type="datetime" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="截止时间" prop="endTime"><el-date-picker v-model="taskForm.endTime" type="datetime" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="任务要求"><el-input v-model="taskForm.description" type="textarea" :rows="4" placeholder="请输入任务要求、实验步骤等..." /></el-form-item>
        <el-form-item label="重交次数"><el-input-number v-model="taskForm.maxRetryCount" :min="0" :max="5" /><span style="margin-left:8px;font-size:12px;color:#909399">学生可重新提交的最大次数</span></el-form-item>
        <el-form-item label="指导文档">
          <el-upload :file-list="guideFileList" :http-request="handleGuideUpload" :on-remove="handleGuideRemove" :on-preview="handleGuidePreview" accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx" list-type="text">
            <el-button size="small" type="primary" :loading="uploadingFile">上传文档</el-button>
            <template #tip><span class="upload-tip">支持 PDF/Word/PPT/Excel，可上传多个文件</span></template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="handleSave">提交审核</el-button></template>
    </el-dialog>

    <!-- 批阅弹窗 -->
    <el-dialog v-model="reviewVisible" :title="reviewTask?.title+' — 批阅'" width="900px">
      <el-table :data="submissions" stripe v-loading="grading" :header-cell-style="{ textAlign:'center' }">
        <el-table-column prop="userName" label="学生" width="70" align="center" />
        <el-table-column label="提交时间" width="140" align="center"><template #default="{ row }">{{ formatDate(row.submitTime) }}</template></el-table-column>
        <el-table-column label="提交内容" width="80" align="center">
          <template #default="{ row }"><el-button size="small" type="primary" link @click="openSubmissionDetail(row)"><el-icon><View /></el-icon> 查看</el-button></template>
        </el-table-column>
        <el-table-column label="状态" width="75" align="center">
          <template #default="{ row }"><el-tag :type="row.status==='GRADED'?'success':row.status==='RETURNED'?'danger':'warning'" size="small">{{ row.status==='GRADED'?'已批':row.status==='RETURNED'?'退回':'待批' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="score" label="分数" width="60" align="center" />
        <el-table-column prop="comment" label="评语" min-width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <template v-if="row.status!=='GRADED'"><el-button size="small" type="success" @click="openGradeDialog(row,'PASS')">通过</el-button><el-button size="small" type="danger" @click="openGradeDialog(row,'RETURN')">退回</el-button></template>
              <span v-else style="color:#67c23a;font-size:13px">已批阅</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 提交详情弹窗 -->
    <el-dialog v-model="submissionDetailVisible" :title="currentSubmission?.userName+' 的提交内容'" width="600px" destroy-on-close>
      <div class="submission-detail" v-if="currentSubmission">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="提交时间">{{ formatDate(currentSubmission.submitTime) }}</el-descriptions-item>
          <el-descriptions-item label="批阅状态"><el-tag :type="currentSubmission.status==='GRADED'?'success':currentSubmission.status==='RETURNED'?'danger':'warning'" size="small">{{ currentSubmission.status==='GRADED'?'已批阅':currentSubmission.status==='RETURNED'?'已退回':'待批阅' }}</el-tag></el-descriptions-item>
          <el-descriptions-item v-if="currentSubmission.score!=null" label="分数">{{ currentSubmission.score }}</el-descriptions-item>
          <el-descriptions-item v-if="currentSubmission.comment" label="评语">{{ currentSubmission.comment }}</el-descriptions-item>
        </el-descriptions>
        <div class="submission-section" v-if="currentSubmission.processDescription"><div class="submission-section-title">实验过程描述</div><div class="submission-process-text">{{ currentSubmission.processDescription }}</div></div>
        <div class="submission-section" v-if="currentSubmission.reportFileUrl"><div class="submission-section-title">提交报告</div><div class="submission-file-row"><el-icon :size="20" color="#409eff"><Document /></el-icon><span class="submission-file-name">{{ currentSubmission.reportFileName||'实验报告' }}</span><el-button size="small" type="primary" @click="openFilePreview(currentSubmission.reportFileUrl, currentSubmission.reportFileName||'实验报告')">在线预览</el-button></div></div>
        <el-empty v-if="!currentSubmission.processDescription&&!currentSubmission.reportFileUrl" description="该学生暂未提交报告内容" :image-size="60" />
      </div>
      <template #footer><el-button @click="submissionDetailVisible=false">关闭</el-button><template v-if="currentSubmission&&currentSubmission.status!=='GRADED'"><el-button type="success" @click="submissionDetailVisible=false;openGradeDialog(currentSubmission,'PASS')">通过</el-button><el-button type="danger" @click="submissionDetailVisible=false;openGradeDialog(currentSubmission,'RETURN')">退回</el-button></template></template>
    </el-dialog>

    <!-- 批阅打分弹窗 -->
    <el-dialog v-model="gradeDialogVisible" :title="gradeAction==='PASS'?'批阅评分':'退回提交'" width="480px" destroy-on-close>
      <el-form v-if="gradeAction==='PASS'" label-width="80px">
        <el-form-item label="分数" required><el-input-number v-model="gradeForm.score" :min="0" :max="100" :step="1" style="width:160px" /><span style="margin-left:10px;font-size:12px;color:#909399">0-100 分</span></el-form-item>
        <el-form-item label="评语"><el-input v-model="gradeForm.comment" type="textarea" :rows="4" placeholder="请输入批阅评语（选填）" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <div v-else><div style="margin-bottom:8px;font-size:14px;color:#303133">退回原因 <span style="color:#f56c6c">*</span></div><el-input v-model="gradeForm.comment" type="textarea" :rows="4" placeholder="请输入退回原因，学生将看到此内容" maxlength="500" show-word-limit /></div>
      <template #footer><el-button @click="gradeDialogVisible=false">取消</el-button><el-button type="primary" :loading="grading" @click="confirmGrade">{{ gradeAction==='PASS'?'确认批阅':'确认退回' }}</el-button></template>
    </el-dialog>

    <!-- 文件预览弹窗 -->
    <el-dialog v-model="previewVisible" :title="'预览：'+previewFileName" width="92%" top="1vh" destroy-on-close class="preview-dialog">
      <div v-if="isOfficeFile(previewFileExt)" class="preview-office-hint"><el-alert type="info" :closable="false" show-icon title="Office 文件通过微软在线服务预览，加载失败请使用下方按钮" /></div>
      <template v-if="isOfficeFile(previewFileExt)||isPreviewableInBrowser(previewFileExt)">
        <div class="preview-iframe-wrap"><iframe :src="isOfficeFile(previewFileExt)?'https://view.officeapps.live.com/op/view.aspx?src='+encodeURI(previewUrl.startsWith('http')?previewUrl:(location.origin+previewUrl)):previewUrl" frameborder="0" class="preview-iframe" /></div>
      </template>
      <template v-else><div class="preview-unsupported"><el-icon :size="64" color="#c0c4cc"><WarningFilled /></el-icon><p>此文件类型（.{{ previewFileExt||'未知' }}）不支持内嵌预览</p><p class="preview-hint">请使用下方按钮在新窗口打开或下载查看</p></div></template>
      <template #footer>
        <div class="preview-footer"><span class="preview-file-info"><el-tag v-if="previewFileExt" size="small" type="info" effect="plain">{{ previewFileExt.toUpperCase() }}</el-tag></span><div class="preview-footer-actions"><el-button @click="previewVisible=false">关闭</el-button><el-button type="primary" @click="openInNewWindow(previewUrl)"><el-icon><TopRight /></el-icon> 新窗口打开</el-button><el-button type="success" @click="downloadFile(previewUrl,previewFileName)"><el-icon><Download /></el-icon> 下载文件</el-button></div></div>
      </template>
    </el-dialog>

    <!-- 任务详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailTask?.title" width="680px">
      <el-descriptions v-if="detailTask" :column="2" border size="small">
        <el-descriptions-item label="任务名称" :span="2">{{ detailTask.title }}</el-descriptions-item>
        <el-descriptions-item label="任务类型"><el-tag :type="detailTask.taskType==='EXPERIMENT'?'success':'warning'" size="small">{{ detailTask.taskType==='EXPERIMENT'?'实验':'实训' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="审核状态"><el-tag :type="auditStatusConfig[detailTask.auditStatus]?.type" size="small">{{ auditStatusConfig[detailTask.auditStatus]?.label }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="所属课程">{{ detailTask.courseName||'-' }}</el-descriptions-item>
        <el-descriptions-item label="目标班级">{{ detailTask.className||'-' }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDate(detailTask.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDate(detailTask.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="提交进度" :span="2">已批 {{ detailTask.gradedCount||0 }} / 已交 {{ detailTask.submittedCount||0 }} / 总人数 {{ detailTask.studentCount||0 }}</el-descriptions-item>
        <el-descriptions-item label="任务描述" :span="2">{{ detailTask.description||'暂无描述' }}</el-descriptions-item>
        <el-descriptions-item label="指导文档" :span="2"><template v-if="detailTask.guideFileUrl"><div v-for="(f,i) in parseGuideFiles(detailTask.guideFileUrl)" :key="i"><el-link type="primary" @click="handleGuidePreview(f)">{{ f.name }}</el-link></div></template><span v-else style="color:#909399">暂无</span></el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="detailVisible=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
.search-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; }
.progress-text { font-weight: 600; font-size: 13px; }
.time-cell { font-size: 12px; line-height: 1.6; }
.time-sep { color: #909399; font-size: 11px; }
.action-btns { display: flex; gap: 6px; justify-content: center; }
.upload-tip { font-size: 12px; color: #909399; margin-left: 8px; }
:deep(.el-descriptions__label) { white-space: nowrap; }

.submission-detail { display: flex; flex-direction: column; gap: 16px; }
.submission-section { background: #fafafa; border-radius: 8px; padding: 14px 16px; }
.submission-section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 10px; padding-bottom: 8px; border-bottom: 1px solid #ebeef5; }
.submission-process-text { font-size: 13px; color: #606266; line-height: 1.8; white-space: pre-wrap; word-break: break-word; max-height: 200px; overflow-y: auto; }
.submission-file-row { display: flex; align-items: center; gap: 10px; }
.submission-file-name { flex: 1; font-size: 13px; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.preview-dialog :deep(.el-dialog__header) { padding: 12px 20px; border-bottom: 1px solid #ebeef5; }
.preview-dialog :deep(.el-dialog__body) { padding: 0; overflow: hidden; }
.preview-dialog :deep(.el-dialog__footer) { padding: 10px 20px; border-top: 1px solid #ebeef5; }
.preview-office-hint :deep(.el-alert) { border-radius: 0; border-bottom: 1px solid #ebeef5; }
.preview-iframe-wrap { width: 100%; height: calc(100vh - 200px); min-height: 500px; background: #525659; overflow: hidden; }
.preview-iframe { width: 100%; height: 100%; border: none; }
.preview-unsupported { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 80px 20px; color: #909399; height: calc(100vh - 200px); min-height: 400px; }
.preview-unsupported p { margin: 8px 0; font-size: 14px; }
.preview-hint { font-size: 12px !important; color: #c0c4cc; }
.preview-footer { display: flex; align-items: center; justify-content: space-between; }
.preview-footer-actions { display: flex; gap: 8px; }
.preview-file-info { font-size: 12px; color: #909399; }
</style>
