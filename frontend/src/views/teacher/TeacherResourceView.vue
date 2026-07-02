<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import {
  getTeacherResources, uploadResource, deleteResource,
  updateResource, getAuditFeedback, resubmitResource,
  getTeacherCourses,
} from '@/api/teacher'
import { uploadFile } from '@/api/common'

const loading = ref(false)

const resources = ref([])
const searchForm = reactive({ type: '', auditStatus: '', keyword: '' })
const pagination = reactive({ page: 1, pageSize: Number(localStorage.getItem('resourcePageSize')) || 10, total: 0 })
watch(() => pagination.pageSize, v => localStorage.setItem('resourcePageSize', v))

const typeTagMap = {
  COURSEWARE: 'primary',
  EXERCISE: 'success',
  VIDEO: 'warning',
  DOCUMENT: 'info',
  OTHER: '',
}

const typeLabel = {
  COURSEWARE: '课件',
  EXERCISE: '习题',
  VIDEO: '视频',
  DOCUMENT: '文档',
  OTHER: '其他',
}

const auditConfig = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

const scopeConfig = {
  CLASS_ONLY: '仅本班',
  DEPARTMENT_WIDE: '本院系',
  SCHOOL_WIDE: '全校',
}

const scopeOptions = [
  { label: '仅本班', value: 'CLASS_ONLY' },
  { label: '本院系', value: 'DEPARTMENT_WIDE' },
  { label: '全校', value: 'SCHOOL_WIDE' },
]

async function fetchResources() {
  loading.value = true
  try {
    const res = await getTeacherResources({
      type: searchForm.type || undefined,
      auditStatus: searchForm.auditStatus || undefined,
      keyword: searchForm.keyword || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    resources.value = res.data.records || res.data
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// ========== 上传/编辑弹窗 ==========
const uploadVisible = ref(false)
const isEditing = ref(false)
const editingResourceId = ref(null)
const uploading = ref(false)
const uploadForm = reactive({
  resourceName: '', fileType: 'COURSEWARE', courseName: '', visibility: 'CLASS_ONLY', description: '',
})

// 已上传的文件列表
const uploadedFiles = ref([])
const uploadFileLoading = ref(false)

// 课程下拉选项
const courseOptions = ref([])

async function loadCourseOptions() {
  try {
    const res = await getTeacherCourses({ auditStatus: 'APPROVED', page: 1, pageSize: 200 })
    const list = res.data.records || res.data || []
    courseOptions.value = list.map(c => c.courseName).filter(Boolean)
  } catch { courseOptions.value = [] }
}

function openUpload(row) {
  isEditing.value = false
  editingResourceId.value = null
  uploadedFiles.value = []
  loadCourseOptions()
  if (row) {
    isEditing.value = true
    editingResourceId.value = row.resourceId
    uploadForm.resourceName = row.resourceName || row.name || ''
    uploadForm.fileType = row.fileType || row.type || 'COURSEWARE'
    uploadForm.courseName = row.courseName || ''
    uploadForm.visibility = row.visibility || row.scope || 'CLASS_ONLY'
    uploadForm.description = row.description || ''
    if (row.fileUrl) {
      uploadedFiles.value = [{ name: row.fileName || '已上传文件', url: row.fileUrl }]
    }
  } else {
    uploadForm.resourceName = ''
    uploadForm.fileType = 'COURSEWARE'
    uploadForm.courseName = ''
    uploadForm.visibility = 'CLASS_ONLY'
    uploadForm.description = ''
    uploadedFiles.value = []
  }
  uploadVisible.value = true
}

// 实际文件上传处理器
async function handleFileUpload(options) {
  uploadFileLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', options.file)
    formData.append('module', 'resource')
    const res = await uploadFile(formData)
    const url = res.data.fileUrl
    const name = res.data.fileName || options.file.name
    uploadedFiles.value.push({ name, url })
    ElMessage.success('文件上传成功')
  } catch (e) {
    ElMessage.error('文件上传失败')
  } finally {
    uploadFileLoading.value = false
  }
}

function handleFileRemove(file) {
  uploadedFiles.value = uploadedFiles.value.filter(f => f.url !== file.url && f.name !== file.name)
}

async function handleUpload() {
  if (!uploadForm.resourceName) { ElMessage.warning('请输入资源名称'); return }
  uploading.value = true
  try {
    const payload = {
      resourceName: uploadForm.resourceName,
      description: uploadForm.description,
      fileType: uploadForm.fileType,
      courseName: uploadForm.courseName || undefined,
      visibility: uploadForm.visibility,
    }
    // 携带上传的文件信息
    if (uploadedFiles.value.length > 0) {
      payload.fileUrl = uploadedFiles.value[0].url
      payload.fileName = uploadedFiles.value[0].name
    }

    if (isEditing.value) {
      await updateResource(editingResourceId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await uploadResource(payload)
      ElMessage.success('上传成功，请等待管理员审核')
    }
    uploadVisible.value = false
    await fetchResources()
  } catch {} finally { uploading.value = false }
}

// ========== 删除 ==========
async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除资源「${row.resourceName || row.name}」吗？`, '确认删除', { type: 'warning' })
  await deleteResource(row.resourceId)
  ElMessage.success('已删除')
  await fetchResources()
}

// ========== 审核反馈查看 ==========
const feedbackVisible = ref(false)
const feedbackResource = ref(null)
const feedbackLogs = ref([])
const feedbackLoading = ref(false)

async function openFeedback(row) {
  feedbackResource.value = row
  feedbackVisible.value = true
  feedbackLoading.value = true
  try {
    const res = await getAuditFeedback(row.resourceId)
    feedbackLogs.value = res.data?.auditLogs || []
  } catch {
    feedbackLogs.value = []
  } finally {
    feedbackLoading.value = false
  }
}

// ========== 重新提交 ==========
async function handleResubmit(row) {
  try {
    const { value: confirm } = await ElMessageBox.confirm(
      '重新提交将重置审核状态为待审核，确认继续？',
      '重新提交审核',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
    if (confirm !== 'confirm') return
  } catch { return }

  try {
    await resubmitResource(row.resourceId, {})
    ElMessage.success('已重新提交审核')
    await fetchResources()
  } catch {}
}

// ========== 预览资源文件 ==========
function handlePreview(row) {
  const url = row.fileUrl
  if (!url) {
    ElMessage.warning('该资源暂无文件')
    return
  }
  // 存储的 URL 格式为 /api/v1/common/files/...，相对路径即可，
  // 开发环境走 Vite 代理，生产环境同源直接访问
  window.open(url, '_blank')
}

// ========== 下载资源 ==========
function handleDownload(row) {
  const url = row.fileUrl
  if (!url) {
    ElMessage.warning('该资源暂无文件')
    return
  }
  fetch(url)
    .then(res => res.blob())
    .then(blob => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = row.fileName || row.resourceName || 'download'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      setTimeout(() => URL.revokeObjectURL(url), 100)
      ElMessage.success('下载完成')
    })
    .catch(() => {
      window.open(url, '_blank')
    })
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatDate(str) {
  if (!str) return '-'
  try {
    const d = new Date(str)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    const sec = String(d.getSeconds()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}:${sec}`
  } catch { return str }
}

onMounted(fetchResources)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">教学资源上传</h1>
      <el-button type="primary" @click="openUpload()">上传资源</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-select v-model="searchForm.type" placeholder="资源类型" clearable style="width:110px">
          <el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" />
        </el-select>
        <el-select v-model="searchForm.auditStatus" placeholder="审核状态" clearable style="width:110px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input v-model="searchForm.keyword" placeholder="搜索资源" clearable style="width:180px" />
        <el-button type="primary" @click="fetchResources">查询</el-button>
      </div>

      <el-table :data="resources" v-loading="loading" stripe>
        <el-table-column label="资源名称" min-width="150">
          <template #default="{ row }">
            <el-tooltip :content="row.resourceName || row.name" placement="top-start" :show-after="400" :hide-after="0">
              <span class="cell-text">{{ row.resourceName || row.name }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="65" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagMap[row.fileType || row.type] || 'info'" size="small">
              {{ typeLabel[row.fileType || row.type] || row.fileType || row.type || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联课程" width="110">
          <template #default="{ row }">
            <el-tooltip :content="row.courseName || '-'" placement="top-start" :show-after="400" :hide-after="0" :disabled="!row.courseName">
              <span class="cell-text">{{ row.courseName || '-' }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="可见范围" width="80" align="center">
          <template #default="{ row }">{{ scopeConfig[row.visibility] || scopeConfig[row.scope] || '-' }}</template>
        </el-table-column>
        <el-table-column label="文件" width="70" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.fileName" :content="row.fileName" placement="top-start">
              <el-button size="small" text type="primary" @click="handlePreview(row)">预览</el-button>
            </el-tooltip>
            <span v-else style="color:#c0c4cc;font-size:12px">-</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="85" align="center">
          <template #default="{ row }"><span class="size-cell">{{ formatSize(row.fileSize) }}</span></template>
        </el-table-column>
        <el-table-column label="下载" width="55" align="center">
          <template #default="{ row }">{{ row.downloadCount ?? row.downloads ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="上传时间" width="160" align="center">
          <template #default="{ row }">
            <span class="time-cell">{{ formatDate(row.uploadTime || row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核" width="80" align="center">
          <template #default="{ row }">
            <div class="audit-cell">
              <el-tag :type="auditConfig[row.auditStatus]?.type" size="small">
                {{ auditConfig[row.auditStatus]?.label }}
              </el-tag>
              <el-tooltip
                v-if="row.auditStatus === 'REJECTED' && row.auditComment"
                :content="row.auditComment"
                placement="top"
              >
                <el-icon class="audit-comment-icon"><WarningFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" align="center">
          <template #default="{ row }">
            <div class="action-btns">
              <!-- 驳回：查看审核意见 + 重新提交 -->
              <template v-if="row.auditStatus === 'REJECTED'">
                <el-button size="small" type="warning" @click="openFeedback(row)">审核意见</el-button>
                <el-button size="small" type="primary" @click="handleResubmit(row)">重新提交</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
              <!-- 待审核 -->
              <template v-else-if="row.auditStatus === 'PENDING'">
                <el-button size="small" @click="openUpload(row)">编辑</el-button>
                <el-button size="small" type="primary" @click="openUpload(row)">重传</el-button>
                <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
              <!-- 已通过 -->
              <template v-else>
                <el-button size="small" type="primary" @click="handleDownload(row)">下载</el-button>
                <el-button size="small" @click="openUpload(row)">重传</el-button>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="pagination.total"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[5, 10, 20, 50]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="fetchResources"
      />
    </div>

    <!-- 上传/编辑弹窗 -->
    <el-dialog
      v-model="uploadVisible"
      :title="isEditing ? '编辑教学资源' : '上传教学资源'"
      width="560px"
      destroy-on-close
    >
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="资源名称" required>
          <el-input v-model="uploadForm.resourceName" placeholder="请输入资源名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资源类型">
              <el-select v-model="uploadForm.fileType" style="width:100%">
                <el-option v-for="(label, value) in typeLabel" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联课程">
              <el-select v-model="uploadForm.courseName" style="width:100%" placeholder="选择课程" clearable filterable>
                <el-option v-for="name in courseOptions" :key="name" :label="name" :value="name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="可见范围">
          <el-radio-group v-model="uploadForm.visibility">
            <el-radio v-for="opt in scopeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资源描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            :file-list="uploadedFiles"
            :http-request="handleFileUpload"
            :on-remove="handleFileRemove"
            multiple
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持批量上传，单文件最大 100MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">
          {{ isEditing ? '保存修改' : '上传并提交审核' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 审核反馈弹窗 -->
    <el-dialog
      v-model="feedbackVisible"
      :title="'审核意见 — ' + (feedbackResource?.resourceName || feedbackResource?.name || '')"
      width="560px"
      destroy-on-close
    >
      <div v-loading="feedbackLoading">
        <!-- 当前审核状态 -->
        <el-alert
          :title="feedbackResource?.auditStatus === 'REJECTED' ? '该资源已被驳回' : '审核状态：' + (auditConfig[feedbackResource?.auditStatus]?.label || '')"
          :type="feedbackResource?.auditStatus === 'REJECTED' ? 'error' : 'info'"
          :closable="false"
          show-icon
          style="margin-bottom:16px"
        >
          <template v-if="feedbackResource?.auditComment">
            <div style="margin-top:6px;font-size:13px">
              <strong>审核意见：</strong>{{ feedbackResource.auditComment }}
            </div>
          </template>
        </el-alert>

        <!-- 审核日志 -->
        <div v-if="feedbackLogs.length" class="feedback-logs">
          <div class="feedback-logs-title">审核记录</div>
          <div v-for="log in feedbackLogs" :key="log.logId" class="feedback-log-item">
            <div class="feedback-log-header">
              <el-tag
                :type="log.afterStatus === 'APPROVED' ? 'success' : log.afterStatus === 'REJECTED' ? 'danger' : 'warning'"
                size="small"
              >
                {{ log.action === 'SUBMIT' ? '提交' : log.afterStatus === 'APPROVED' ? '通过' : log.afterStatus === 'REJECTED' ? '驳回' : log.afterStatus }}
              </el-tag>
              <span class="feedback-log-operator">{{ log.operatorName }}</span>
              <span class="feedback-log-time">{{ log.createTime }}</span>
            </div>
            <div v-if="log.comment" class="feedback-log-comment">{{ log.comment }}</div>
          </div>
        </div>
        <el-empty v-else-if="!feedbackLoading" description="暂无审核记录" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="feedbackVisible = false">关闭</el-button>
        <el-button v-if="feedbackResource?.auditStatus === 'REJECTED'" type="primary" @click="feedbackVisible = false; openUpload(feedbackResource)">
          修改并重新提交
        </el-button>
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

.audit-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.cell-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.time-cell {
  white-space: nowrap;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.size-cell {
  white-space: nowrap;
  font-size: 12px;
}

:deep(.el-tag) {
  color: #303133;
}

.audit-comment-icon {
  color: #f56c6c;
  cursor: pointer;
  font-size: 14px;
}

.action-btns {
  display: flex;
  gap: 6px;
  justify-content: center;
  flex-wrap: wrap;
}

/* 审核反馈 */
.feedback-logs {
  margin-top: 8px;
}

.feedback-logs-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}

.feedback-log-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
}

.feedback-log-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.feedback-log-operator {
  font-size: 13px;
  color: #606266;
}

.feedback-log-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}

.feedback-log-comment {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  padding: 8px 10px;
  background: #fafafa;
  border-radius: 4px;
}
</style>
