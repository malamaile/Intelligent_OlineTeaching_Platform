<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherResources, uploadResource, deleteResource } from '@/api/teacher'

const loading = ref(false)

const resources = ref([])
const searchForm = reactive({ type: '', auditStatus: '', keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const typeConfig = {
  COURSEWARE: { label: '课件', color: '#79b8f7' },
  EXERCISE: { label: '习题', color: '#a3ef7c' },
  VIDEO: { label: '视频', color: '#f4c072' },
  DOCUMENT: { label: '文档', color: '#909399' },
  OTHER: { label: '其他', color: '#c0c4cc' },
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

// 上传弹窗
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadForm = reactive({
  resourceName: '', fileType: 'COURSEWARE', courseName: '', visibility: 'CLASS_ONLY', description: '',
})

function openUpload() {
  Object.assign(uploadForm, { resourceName: '', fileType: 'COURSEWARE', courseName: '', visibility: 'CLASS_ONLY', description: '' })
  uploadVisible.value = true
}

async function handleUpload() {
  if (!uploadForm.resourceName) { ElMessage.warning('请输入资源名称'); return }
  uploading.value = true
  try {
    await uploadResource({
      resourceName: uploadForm.resourceName,
      description: uploadForm.description,
      fileType: uploadForm.fileType,
      courseName: uploadForm.courseName || undefined,
      visibility: uploadForm.visibility,
    })
    ElMessage.success('上传成功，请等待管理员审核')
    uploadVisible.value = false
    await fetchResources()
  } catch {} finally { uploading.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除资源「${row.resourceName || row.name}」吗？`, '确认删除', { type: 'warning' })
  await deleteResource(row.resourceId)
  ElMessage.success('已删除')
  await fetchResources()
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(fetchResources)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">教学资源上传</h1>
      <el-button type="primary" @click="openUpload">上传资源</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-select v-model="searchForm.type" placeholder="资源类型" clearable style="width:110px">
          <el-option v-for="(v, k) in typeConfig" :key="k" :label="v.label" :value="k" />
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
        <el-table-column prop="resourceName" label="资源名称" min-width="160">
          <template #default="{ row }">{{ row.resourceName || row.name }}</template>
        </el-table-column>
        <el-table-column label="类型" width="70">
          <template #default="{ row }">
            <el-tag :color="typeConfig[row.fileType || row.type]?.color" size="small" effect="dark">
              {{ typeConfig[row.fileType || row.type]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="关联课程" width="120" />
        <el-table-column label="可见范围" width="90">
          <template #default="{ row }">{{ scopeConfig[row.visibility || row.scope] }}</template>
        </el-table-column>
        <el-table-column label="大小" width="80">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="downloadCount" label="下载" width="60" />
        <el-table-column prop="uploadTime" label="上传时间" width="100" />
        <el-table-column label="审核" width="130">
          <template #default="{ row }">
            <div>
              <el-tag :type="auditConfig[row.auditStatus]?.type" size="small">
                {{ auditConfig[row.auditStatus]?.label }}
              </el-tag>
              <el-tooltip v-if="row.auditComment" :content="row.auditComment" placement="top">
                <el-icon style="margin-left:4px;color:#f56c6c;cursor:pointer"><WarningFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openUpload" :disabled="row.auditStatus === 'APPROVED'">编辑</el-button>
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
        @change="fetchResources"
      />
    </div>

    <!-- 上传弹窗 -->
    <el-dialog v-model="uploadVisible" title="上传教学资源" width="560px" destroy-on-close>
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="资源名称" required>
          <el-input v-model="uploadForm.resourceName" placeholder="请输入资源名称" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资源类型">
              <el-select v-model="uploadForm.fileType" style="width:100%">
                <el-option v-for="(v, k) in typeConfig" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联课程">
              <el-select v-model="uploadForm.courseName" style="width:100%" placeholder="可选">
                <el-option label="Java程序设计" value="Java程序设计" />
                <el-option label="数据结构" value="数据结构" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="可见范围">
          <el-radio-group v-model="uploadForm.visibility">
            <el-radio value="CLASS_ONLY">仅本班</el-radio>
            <el-radio value="DEPARTMENT_WIDE">本院系</el-radio>
            <el-radio value="SCHOOL_WIDE">全校</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资源描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload drag :auto-upload="false" multiple>
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持批量上传，单文件最大 100MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传并提交审核</el-button>
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
