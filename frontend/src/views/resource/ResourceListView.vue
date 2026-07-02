<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getResources, toggleFavorite, removeFavorite } from '@/api/student'
import http from '@/api'
import { ElMessage } from 'element-plus'

// ========== 列表 ==========
const loading = ref(false)
const resources = ref([])

const searchForm = reactive({
  keyword: '',
  category: '',
  courseId: '',
  scope: 'ALL',
})

const categoryOptions = [
  { label: '全部类型', value: '' },
  { label: '课件', value: 1 },
  { label: '习题', value: 2 },
  { label: '视频', value: 3 },
  { label: '文档', value: 4 },
  { label: '其他', value: 5 },
]

const scopeOptions = [
  { label: '全部资源', value: 'ALL' },
  { label: '我的收藏', value: 'FAVORITE' },
]

const categoryConfig = {
  COURSEWARE: { label: '课件', icon: 'Document', color: '#89bff5', bg: '#ecf5ff' },
  EXERCISE: { label: '习题', icon: 'Edit', color: '#abf486', bg: '#f0f9eb' },
  VIDEO: { label: '视频', icon: 'VideoCamera', color: '#f8c87f', bg: '#fdf6ec' },
  DOCUMENT: { label: '文档', icon: 'Document', color: '#909399', bg: '#f5f7fa' },
  OTHER: { label: '其他', icon: 'FolderOpened', color: '#c0c4cc', bg: '#fafafa' },
}

const pagination = reactive({ page: 1, pageSize: 12, total: 0 })
const viewMode = ref('grid') // grid | list

async function fetchResources() {
  loading.value = true
  try {
    const res = await getResources({
      keyword: searchForm.keyword || undefined,
      category: searchForm.category || undefined,
      courseId: searchForm.courseId || undefined,
      scope: searchForm.scope === 'FAVORITE' ? 'FAVORITE' : undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    resources.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

async function handleToggleFavorite(resource) {
  if (resource.isFavorited) {
    await removeFavorite(resource.resourceId)
    resource.isFavorited = false
    ElMessage.success('已取消收藏')
  } else {
    await toggleFavorite(resource.resourceId)
    resource.isFavorited = true
    ElMessage.success('已加入收藏')
  }
}

async function handleDownload(resource) {
  try {
    const response = await http.get(`/student/resources/${resource.resourceId}/download`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', resource.fileName || resource.name || 'download')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('开始下载')
  } catch {
    ElMessage.error('下载失败')
  }
}

// ========== 预览 ==========
const previewVisible = ref(false)
const previewResource = ref(null)

function openPreview(resource) {
  previewResource.value = resource
  previewVisible.value = true
}

/**
 * 根据文件类型判断预览渲染方式
 * @param {Object} resource - 资源对象（含 type 和 fileType 字段）
 * @param {String} mode - 'video' | 'image' | 'pdf' | 'office'
 * @returns {Boolean}
 */
function isPreviewType(resource, mode) {
  const ft = (resource.fileType || '').toLowerCase()
  const type = (resource.type || '').toUpperCase()
  const IMG_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']
  const VIDEO_EXTS = ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'flv']
  const OFFICE_EXTS = ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx']

  switch (mode) {
    case 'video':  return type === 'VIDEO' || VIDEO_EXTS.includes(ft)
    case 'image':  return IMG_EXTS.includes(ft)
    case 'pdf':    return ft === 'pdf'
    case 'office': return OFFICE_EXTS.includes(ft)
    default:       return false
  }
}

function handleSearch() {
  pagination.page = 1
  fetchResources()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.category = ''
  searchForm.courseId = ''
  searchForm.scope = 'ALL'
  pagination.page = 1
  fetchResources()
}

function handlePageChange(page) {
  pagination.page = page
  fetchResources()
}

function formatFileSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(fetchResources)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">教学资源库</h1>
      <p class="page-desc">浏览、检索、下载全校公开及班级专属教学资源，支持在线预览与收藏</p>
    </div>

    <div class="card-wrapper">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索资源名称"
          clearable
          style="width: 240px"
          prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.category" style="width: 130px" @change="handleSearch">
          <el-option v-for="opt in categoryOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="searchForm.scope" style="width: 130px" @change="handleSearch">
          <el-option v-for="opt in scopeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <div class="view-toggle">
          <el-tooltip content="网格视图">
            <el-button :type="viewMode === 'grid' ? 'primary' : ''" text circle @click="viewMode = 'grid'">
              <el-icon><Grid /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="列表视图">
            <el-button :type="viewMode === 'list' ? 'primary' : ''" text circle @click="viewMode = 'list'">
              <el-icon><List /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <div v-loading="loading">
        <div v-if="resources.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无资源" :image-size="120" />
        </div>

        <!-- 网格视图 -->
        <div v-else-if="viewMode === 'grid'" class="resource-grid">
          <div v-for="res in resources" :key="res.resourceId" class="resource-card">
            <div class="rc-top">
              <div class="rc-icon" :style="{ background: categoryConfig[res.type]?.bg }">
                <el-icon :size="28" :color="categoryConfig[res.type]?.color">
                  <component :is="categoryConfig[res.type]?.icon || 'FolderOpened'" />
                </el-icon>
              </div>
              <el-tag
                :color="categoryConfig[res.type]?.color"
                size="small" effect="dark"
                class="rc-type-badge"
              >
                {{ categoryConfig[res.type]?.label }}
              </el-tag>
            </div>
            <div class="rc-body">
              <div class="rc-name" :title="res.name">{{ res.name }}</div>
              <div class="rc-meta">
                <span>{{ res.courseName || '公共资源' }}</span>
                <span>{{ res.teacherName }}</span>
              </div>
              <div class="rc-stats">
                <span><el-icon><Download /></el-icon> {{ res.downloadCount }}</span>
                <span>{{ formatFileSize(res.fileSize) }}</span>
                <span>{{ res.uploadTime }}</span>
              </div>
            </div>
            <div class="rc-actions">
              <el-button size="small" :type="res.isFavorited ? 'warning' : 'default'" @click="handleToggleFavorite(res)">
                <el-icon><StarFilled v-if="res.isFavorited" /><Star v-else /></el-icon>
                {{ res.isFavorited ? '已收藏' : '收藏' }}
              </el-button>
              <el-button size="small" type="primary" @click="openPreview(res)">
                <el-icon><View /></el-icon>预览
              </el-button>
              <el-button size="small" type="primary" @click="handleDownload(res)">
                <el-icon><Download /></el-icon>下载
              </el-button>
            </div>
          </div>
        </div>

        <!-- 列表视图 -->
        <div v-else class="resource-list">
          <div
            v-for="res in resources"
            :key="res.resourceId"
            class="resource-row"
          >
            <el-icon :size="20" :color="categoryConfig[res.type]?.color">
              <component :is="categoryConfig[res.type]?.icon || 'FolderOpened'" />
            </el-icon>
            <span class="rr-name">{{ res.name }}</span>
            <span class="rr-type">
              <el-tag :color="categoryConfig[res.type]?.color" size="small" effect="dark">
                {{ categoryConfig[res.type]?.label }}
              </el-tag>
            </span>
            <span class="rr-course">{{ res.courseName || '公共资源' }}</span>
            <span class="rr-size">{{ formatFileSize(res.fileSize) }}</span>
            <span class="rr-downloads">{{ res.downloadCount }} 次下载</span>
            <span class="rr-date">{{ res.uploadTime }}</span>
            <span class="rr-actions">
              <el-button size="small" :type="res.isFavorited ? 'warning' : 'default'" @click="handleToggleFavorite(res)">
                <el-icon><StarFilled v-if="res.isFavorited" /><Star v-else /></el-icon>
                {{ res.isFavorited ? '已收藏' : '收藏' }}
              </el-button>
              <el-button size="small" type="primary" @click="openPreview(res)">
                <el-icon><View /></el-icon>预览
              </el-button>
              <el-button size="small" type="primary" @click="handleDownload(res)">
                <el-icon><Download /></el-icon>下载
              </el-button>
            </span>
          </div>
        </div>
      </div>

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

    <!-- ========== 预览弹窗 ========== -->
    <el-dialog
      v-model="previewVisible"
      :title="previewResource?.name || '资源预览'"
      width="780px"
      destroy-on-close
    >
      <div v-if="previewResource" class="preview-area">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="资源名称">{{ previewResource.name }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :color="categoryConfig[previewResource.type]?.color" size="small" effect="dark">
              {{ categoryConfig[previewResource.type]?.label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatFileSize(previewResource.fileSize) }}</el-descriptions-item>
          <el-descriptions-item label="下载次数">{{ previewResource.downloadCount }} 次</el-descriptions-item>
          <el-descriptions-item label="上传教师">{{ previewResource.teacherName }}</el-descriptions-item>
          <el-descriptions-item label="关联课程">{{ previewResource.courseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上传时间" :span="2">{{ previewResource.uploadTime }}</el-descriptions-item>
        </el-descriptions>

        <!-- 预览区 -->
        <div class="preview-content">
          <!-- 视频：原生播放器 -->
          <template v-if="isPreviewType(previewResource, 'video')">
            <video
              v-if="previewResource.previewUrl"
              :src="previewResource.previewUrl"
              controls
              style="width: 100%; border-radius: 8px; max-height: 460px"
            >
              您的浏览器不支持视频播放
            </video>
            <el-empty v-else description="视频资源暂无预览地址" :image-size="80" />
          </template>
          <!-- 图片：直接渲染 -->
          <template v-else-if="isPreviewType(previewResource, 'image')">
            <div class="image-preview">
              <img :src="previewResource.previewUrl" :alt="previewResource.name" />
            </div>
          </template>
          <!-- PDF：iframe嵌入 -->
          <template v-else-if="isPreviewType(previewResource, 'pdf')">
            <iframe
              v-if="previewResource.previewUrl"
              :src="previewResource.previewUrl"
              class="preview-iframe"
              frameborder="0"
            />
            <el-empty v-else description="暂无预览" :image-size="80" />
          </template>
          <!-- Office文档：不支持在线预览 -->
          <template v-else-if="isPreviewType(previewResource, 'office')">
            <div class="no-preview">
              <el-icon :size="48"><Document /></el-icon>
              <p>Office 文档（{{ previewResource.fileType }}）不支持在线预览</p>
              <p class="hint">请下载后在本地使用 Microsoft Office 或 WPS 打开</p>
              <el-button type="primary" @click="handleDownload(previewResource)">下载查看</el-button>
            </div>
          </template>
          <!-- 其他有URL的文件：iframe兜底 -->
          <template v-else-if="previewResource.previewUrl">
            <iframe
              :src="previewResource.previewUrl"
              class="preview-iframe"
              frameborder="0"
            />
          </template>
          <!-- 无预览地址 -->
          <template v-else>
            <div class="no-preview">
              <el-icon :size="48"><WarningFilled /></el-icon>
              <p>该资源不支持在线预览</p>
              <el-button type="primary" @click="handleDownload(previewResource)">下载查看</el-button>
            </div>
          </template>
        </div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleDownload(previewResource)" v-if="previewResource">下载资源</el-button>
      </template>
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
  margin-top: 20px;
  justify-content: flex-end;
}

.view-toggle {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

/* 网格视图 */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.resource-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 20px;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}

.resource-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border-color: #d3d9e6;
}

.rc-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.rc-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rc-body {
  flex: 1;
}

.rc-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rc-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
  margin-bottom: 10px;
}

.rc-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #c0c4cc;
}

.rc-stats span {
  display: flex;
  align-items: center;
  gap: 2px;
}

.rc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f2f2f2;
}

.rc-actions .el-button,
.rr-actions .el-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 列表视图 */
.resource-list {
  display: flex;
  flex-direction: column;
}

.resource-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid #f2f2f2;
  transition: background 0.15s;
  font-size: 13px;
}

.resource-row:hover {
  background: #f5f7fa;
}

.rr-name {
  flex: 1;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rr-type {
  width: 60px;
}

.rr-course {
  width: 120px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rr-size {
  width: 80px;
  color: #909399;
}

.rr-downloads {
  width: 80px;
  color: #909399;
}

.rr-date {
  width: 100px;
  color: #c0c4cc;
}

.rr-actions {
  display: flex;
  gap: 4px;
}

/* 预览 */
.preview-area {
  min-height: 300px;
}

.preview-content {
  margin-top: 16px;
}

.preview-iframe {
  width: 100%;
  height: 420px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.no-preview {
  text-align: center;
  padding: 60px 0;
  color: #909399;
}

.no-preview p {
  margin: 12px 0 16px;
  font-size: 14px;
}

.no-preview .hint {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: -8px;
  margin-bottom: 16px;
}

/* 图片预览 */
.image-preview {
  text-align: center;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.image-preview img {
  max-width: 100%;
  max-height: 500px;
  border-radius: 4px;
  object-fit: contain;
}

.empty-state {
  padding: 60px 0;
}
</style>
