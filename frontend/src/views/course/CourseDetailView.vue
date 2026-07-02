<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourseDetail, reportProgress } from '@/api/student'
import { ElMessage } from 'element-plus'
import http from '@/api'

const route = useRoute()
const router = useRouter()
const courseId = Number(route.params.id)

const loading = ref(false)
const course = ref(null)
const activeChapter = ref(null)

// 视频播放相关
const videoRef = ref(null)
const playing = ref(false)
const currentPosition = ref(0)
let progressTimer = null
let pageStaySeconds = 0
let pageStayTimer = null

// 章节状态映射
const statusConfig = {
  NOT_STARTED: { label: '未开始', color: '#909399' },
  IN_PROGRESS: { label: '学习中', color: '#409eff' },
  COMPLETED: { label: '已完成', color: '#67c23a' },
}

async function fetchCourseDetail() {
  loading.value = true
  try {
    const res = await getCourseDetail(courseId)
    course.value = res.data
    // 默认选中第一个"学习中"或"未开始"的章节
    const first = res.data.chapters.find((c) => c.status !== 'COMPLETED') || res.data.chapters[0]
    if (first) activeChapter.value = first
  } finally {
    loading.value = false
  }
}

function selectChapter(chapter) {
  activeChapter.value = chapter
  playing.value = false
  currentPosition.value = 0
}

function onVideoLoaded() {
  // 续播：跳转到上次播放位置
  if (activeChapter.value?.lastPosition && videoRef.value) {
    videoRef.value.currentTime = activeChapter.value.lastPosition
    currentPosition.value = activeChapter.value.lastPosition
  }
}

function onVideoTimeUpdate() {
  if (videoRef.value) {
    currentPosition.value = Math.floor(videoRef.value.currentTime)
  }
}

function startProgressReport() {
  stopProgressReport()
  progressTimer = setInterval(() => {
    if (activeChapter.value && playing.value && videoRef.value) {
      reportProgress(courseId, {
        chapterId: activeChapter.value.chapterId,
        position: Math.floor(videoRef.value.currentTime),
        duration: 30,
        pageDuration: pageStaySeconds,
      }).catch(() => {})
    }
  }, 30000) // 每 30 秒上报一次
}

function stopProgressReport() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

// 页面停留计时
function startPageStayTimer() {
  pageStaySeconds = 0
  pageStayTimer = setInterval(() => {
    pageStaySeconds++
  }, 1000)
}

function stopPageStayTimer() {
  if (pageStayTimer) {
    clearInterval(pageStayTimer)
    pageStayTimer = null
  }
}

// 页面可见性变化时暂停/恢复
function handleVisibilityChange() {
  if (document.hidden) {
    stopPageStayTimer()
  } else {
    startPageStayTimer()
  }
}

function onVideoPlay() {
  playing.value = true
  startProgressReport()
}

function onVideoPause() {
  playing.value = false
  // 暂停时也上报一次进度
  if (activeChapter.value && videoRef.value) {
    reportProgress(courseId, {
      chapterId: activeChapter.value.chapterId,
      position: Math.floor(videoRef.value.currentTime),
      duration: 0,
    }).catch(() => {})
  }
}

function formatDuration(seconds) {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}时${m}分${s}秒`
  if (m > 0) return `${m}分${s}秒`
  return `${s}秒`
}

function previewMaterial(mat) {
  const url = mat.fileUrl
  if (!url) {
    ElMessage.warning('该资料暂无预览')
    return
  }
  // PDF、图片类可以在线预览
  const ft = (mat.fileType || '').toLowerCase()
  const previewTypes = ['pdf', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'txt', 'md']
  if (previewTypes.includes(ft)) {
    window.open(url, '_blank')
  } else {
    downloadMaterial(mat)
  }
}

async function downloadMaterial(mat) {
  try {
    const response = await http.get(`/student/resources/${mat.materialId}/download`, {
      responseType: 'blob',
    })
    const blobUrl = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = blobUrl
    link.setAttribute('download', mat.fileName || mat.name || 'download')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(blobUrl)
    ElMessage.success('下载完成')
  } catch {
    // 降级：直接打开文件URL
    if (mat.fileUrl) {
      window.open(mat.fileUrl, '_blank')
    } else {
      ElMessage.error('下载失败')
    }
  }
}

function formatMaterialSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(() => {
  fetchCourseDetail()
  startPageStayTimer()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  stopProgressReport()
  stopPageStayTimer()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  // 离开页面时上报最终进度（含页面停留时长）
  if (activeChapter.value && videoRef.value) {
    reportProgress(courseId, {
      chapterId: activeChapter.value.chapterId,
      position: Math.floor(videoRef.value.currentTime || 0),
      duration: 0,
      pageDuration: pageStaySeconds,
    }).catch(() => {})
  } else if (pageStaySeconds > 0) {
    // 即使没播放视频，也上报停留时长
    reportProgress(courseId, {
      chapterId: 0,
      position: 0,
      duration: 0,
      pageDuration: pageStaySeconds,
    }).catch(() => {})
  }
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 返回按钮 -->
    <div class="page-header">
      <el-button text @click="router.push('/courses')">
        <el-icon><ArrowLeft /></el-icon>返回课程列表
      </el-button>
    </div>

    <template v-if="course">
      <!-- 课程信息 -->
      <div class="card-wrapper">
        <div class="course-header">
          <div class="course-header-left">
            <h1>{{ course.courseName }}</h1>
            <div class="course-meta">
              <span>课程代码：{{ course.courseCode }}</span>
              <span>授课教师：{{ course.teacherName }}</span>
              <span>学期：{{ course.semester }}</span>
            </div>
          </div>
          <div class="course-header-right">
            <el-progress type="circle" :percentage="course.progress" :width="80" />
          </div>
        </div>
      </div>

      <el-row :gutter="20">
        <!-- 左侧：章节列表 -->
        <el-col :span="8">
          <div class="card-wrapper">
            <div class="card-title">课程章节（{{ course.completedChapters }}/{{ course.totalChapters }}）</div>
            <div class="chapter-list">
              <div
                v-for="chapter in course.chapters"
                :key="chapter.chapterId"
                class="chapter-item"
                :class="{ active: activeChapter?.chapterId === chapter.chapterId }"
                @click="selectChapter(chapter)"
              >
                <div class="chapter-index">{{ chapter.sortOrder }}</div>
                <div class="chapter-body">
                  <div class="chapter-title">{{ chapter.title }}</div>
                  <div class="chapter-meta">
                    <span :style="{ color: statusConfig[chapter.status]?.color }">
                      {{ statusConfig[chapter.status]?.label }}
                    </span>
                    <span>{{ formatDuration(chapter.duration) }}</span>
                  </div>
                </div>
                <el-icon v-if="chapter.status === 'COMPLETED'" class="chapter-done"><CircleCheckFilled /></el-icon>
              </div>
            </div>
          </div>
        </el-col>

        <!-- 右侧：视频播放 & 资料 -->
        <el-col :span="16">
          <div class="card-wrapper">
            <template v-if="activeChapter">
              <div class="card-title">{{ activeChapter.title }}</div>
              <!-- 视频播放区 -->
              <div v-if="activeChapter.videoUrl" class="video-area">
                <video
                  ref="videoRef"
                  :src="activeChapter.videoUrl"
                  controls
                  class="video-player"
                  @loadedmetadata="onVideoLoaded"
                  @timeupdate="onVideoTimeUpdate"
                  @play="onVideoPlay"
                  @pause="onVideoPause"
                >
                  您的浏览器不支持视频播放
                </video>
              </div>
              <div v-else class="no-video">
                <el-empty description="该章节暂无视频" :image-size="80" />
              </div>

              <!-- 课件资料 -->
              <div v-if="activeChapter.materials && activeChapter.materials.length > 0" class="materials-area">
                <div class="sub-title">配套资料（{{ activeChapter.materials.length }} 个文件）</div>
                <div
                  v-for="mat in activeChapter.materials"
                  :key="mat.materialId"
                  class="material-item"
                >
                  <el-icon :size="20"><Document /></el-icon>
                  <div class="material-info">
                    <span class="material-name">{{ mat.name }}</span>
                    <span class="material-meta">{{ mat.fileType?.toUpperCase() }} · {{ formatMaterialSize(mat.fileSize) }}</span>
                  </div>
                  <el-button type="primary" text size="small" @click="previewMaterial(mat)">预览</el-button>
                  <el-button type="primary" size="small" @click="downloadMaterial(mat)">下载</el-button>
                </div>
              </div>
            </template>
            <div v-else class="empty-state">
              <el-empty description="请选择章节" :image-size="80" />
            </div>
          </div>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.course-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-header-left h1 {
  font-size: 22px;
  color: #303133;
  margin-bottom: 8px;
}

.course-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #909399;
}

.chapter-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chapter-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.chapter-item:hover {
  background: #f5f7fa;
}

.chapter-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.chapter-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  flex-shrink: 0;
}

.chapter-item.active .chapter-index {
  background: #409eff;
  color: #fff;
}

.chapter-body {
  flex: 1;
}

.chapter-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.chapter-meta {
  font-size: 12px;
  display: flex;
  gap: 12px;
  margin-top: 2px;
}

.chapter-done {
  color: #67c23a;
  font-size: 18px;
}

/* 视频 */
.video-area {
  margin-bottom: 20px;
}

.video-player {
  width: 100%;
  border-radius: 8px;
  background: #000;
  outline: none;
}

.no-video {
  padding: 40px 0;
}

/* 资料 */
.materials-area {
  margin-top: 12px;
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.material-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  transition: background 0.15s;
}

.material-item:hover {
  background: #f5f7fa;
}

.material-info {
  flex: 1;
  min-width: 0;
}

.material-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.material-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  display: block;
}

.empty-state {
  padding: 60px 0;
}
</style>
