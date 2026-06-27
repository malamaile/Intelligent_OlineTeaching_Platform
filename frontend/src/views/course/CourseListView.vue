<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyCourses } from '@/api/student'
import { getSemesters } from '@/api/common'

const router = useRouter()

const loading = ref(false)
const courses = ref([])
const semesters = ref([])

const searchForm = reactive({
  keyword: '',
  semester: '',
})

const pagination = reactive({
  page: 1,
  pageSize: 9,
  total: 0,
})

async function fetchCourses() {
  loading.value = true
  try {
    const res = await getMyCourses({
      keyword: searchForm.keyword || undefined,
      semester: searchForm.semester || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    courses.value = res.data.records
    pagination.total = res.data.total
  } finally {
    loading.value = false
  }
}

async function fetchSemesters() {
  const res = await getSemesters()
  semesters.value = res.data
}

function handleSearch() {
  pagination.page = 1
  fetchCourses()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.semester = ''
  pagination.page = 1
  fetchCourses()
}

function handlePageChange(page) {
  pagination.page = page
  fetchCourses()
}

function goToDetail(courseId) {
  router.push(`/courses/${courseId}`)
}

function getProgressColor(progress) {
  if (progress >= 80) return '#67c23a'
  if (progress >= 50) return '#409eff'
  return '#e6a23c'
}

onMounted(() => {
  fetchSemesters()
  fetchCourses()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">课程学习</h1>
    </div>

    <!-- 搜索栏 -->
    <div class="card-wrapper">
      <div class="search-bar">
        <el-input
          v-model="searchForm.keyword"
          placeholder="搜索课程名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="searchForm.semester" placeholder="全部学期" clearable style="width: 160px">
          <el-option v-for="sem in semesters" :key="sem" :label="sem" :value="sem" />
        </el-select>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <!-- 课程卡片网格 -->
      <div v-loading="loading">
        <div v-if="courses.length === 0 && !loading" class="empty-state">
          <el-empty description="暂无课程数据" :image-size="120" />
        </div>
        <div v-else class="course-grid">
          <div
            v-for="course in courses"
            :key="course.courseId"
            class="course-card"
            @click="goToDetail(course.courseId)"
          >
            <div class="course-cover">
              <img v-if="course.coverImage" :src="course.coverImage" alt="封面" />
              <div v-else class="cover-placeholder">
                <el-icon :size="40"><Reading /></el-icon>
              </div>
              <div class="course-badge">{{ course.semester }}</div>
            </div>
            <div class="course-info">
              <h3 class="course-name">{{ course.courseName }}</h3>
              <p class="course-code">{{ course.courseCode }}</p>
              <div class="course-teacher">
                <el-icon><User /></el-icon>
                <span>{{ course.teacherName }}</span>
              </div>
              <div class="course-progress-bar">
                <el-progress
                  :percentage="course.progress"
                  :color="getProgressColor(course.progress)"
                  :stroke-width="8"
                />
              </div>
              <div class="course-footer">
                <span>{{ course.completedHours }}/{{ course.totalHours }} 课时</span>
                <span class="course-date">开课：{{ course.startDate }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
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
  </div>
</template>

<style scoped>
.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.course-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s;
  background: #fff;
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.course-cover {
  position: relative;
  height: 140px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  overflow: hidden;
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.7);
}

.course-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
}

.course-info {
  padding: 16px;
}

.course-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-code {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.course-teacher {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 12px;
}

.course-progress-bar {
  margin-bottom: 8px;
}

.course-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.course-date {
  color: #c0c4cc;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.empty-state {
  padding: 60px 0;
}
</style>
