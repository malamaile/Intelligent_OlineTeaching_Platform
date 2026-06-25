<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCourseDetail, reportProgress } from '@/api/student'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const courseId = Number(route.params.courseId)

const loading = ref(false)
const course = ref(null)
const activeChapter = ref(null)

// 视频播放相关
const videoRef = ref(null)
const playing = ref(false)
const currentPosition = ref(0)
let progressTimer = null

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

function downloadMaterial(material) {
  window.open(`/api/v1/student/courses/${courseId}/materials/${material.materialId}/download`)
}

onMounted(fetchCourseDetail)

onBeforeUnmount(() => {
  stopProgressReport()
  // 离开页面时上报最终进度
  if (activeChapter.value && videoRef.value) {
    reportProgress(courseId, {
      chapterId: activeChapter.value.chapterId,
      position: Math.floor(videoRef.value.currentTime || 0),
      duration: 0,
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
                <div class="sub-title">配套资料</div>
                <div
                  v-for="mat in activeChapter.materials"
                  :key="mat.materialId"
                  class="material-item"
                  @click="downloadMaterial(mat)"
                >
                  <el-icon><Document /></el-icon>
                  <span class="material-name">{{ mat.name }}</span>
                  <span class="material-size">{{ (mat.fileSize / 1024 / 1024).toFixed(1) }} MB</span>
                  <el-button type="primary" text size="small">下载</el-button>
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
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.material-item:hover {
  background: #f5f7fa;
}

.material-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
}

.material-size {
  font-size: 12px;
  color: #909399;
}

.empty-state {
  padding: 60px 0;
}
</style>
