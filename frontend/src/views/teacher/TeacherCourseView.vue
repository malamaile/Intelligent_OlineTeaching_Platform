<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getTeacherCourses, createCourse, updateCourse, deleteCourse,
  getGrades, saveGrades, exportGrades, getCourseProgress, updateChapters,
} from '@/api/teacher'
import { uploadFile } from '@/api/common'
import http from '@/api'

const loading = ref(false)
const courses = ref([])
const searchForm = reactive({ keyword: '', auditStatus: '', semester: '' })
const pagination = reactive({ page: 1, pageSize: Number(localStorage.getItem('coursePageSize')) || 10, total: 0 })
watch(() => pagination.pageSize, v => localStorage.setItem('coursePageSize', v))

const auditStatusConfig = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
}

async function fetchCourses() {
  loading.value = true
  try {
    const res = await getTeacherCourses({
      keyword: searchForm.keyword || undefined,
      auditStatus: searchForm.auditStatus || undefined,
      semester: searchForm.semester || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    courses.value = res.data.records || res.data
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// ========== 课程创建/编辑 ==========
const dialogVisible = ref(false)
const dialogTitle = ref('创建开课计划')
const saving = ref(false)
const courseFormRef = ref(null)
const isEditing = ref(false)
const courseForm = reactive({
  courseId: null,
  courseName: '', courseCode: '', semester: '2025-2026-2',
  totalHours: 48, weeklyHours: 4,
  startDate: '', endDate: '', description: '',
})

// 行政班级多选
const allClasses = ref([])
const selectedClassIds = ref([])

async function loadAllClasses() {
  try {
    const res = await http.get('/common/classes')
    allClasses.value = (res.data || []).map(c => ({ classId: c.classId, className: c.className }))
  } catch { allClasses.value = [] }
}

// 邀请码
const inviteForm = reactive({
  inviteCode: '',
  inviteExpireTime: '',
  inviteApproval: false,
  inviteEnabled: true,
})

function generateInviteCode() {
  inviteForm.inviteCode = String(Math.floor(1000 + Math.random() * 9000))
}

const rules = {
  courseName: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  courseCode: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
}

function openCreate() {
  isEditing.value = false
  dialogTitle.value = '创建开课计划'
  Object.assign(courseForm, { courseId: null, courseName: '', courseCode: '', description: '' })
  selectedClassIds.value = []
  generateInviteCode()
  inviteForm.inviteExpireTime = ''
  inviteForm.inviteApproval = false
  inviteForm.inviteEnabled = true
  loadAllClasses()
  dialogVisible.value = true
}

function openEdit(row) {
  isEditing.value = true
  dialogTitle.value = '编辑开课计划'
  Object.assign(courseForm, { ...row })
  selectedClassIds.value = []
  loadAllClasses()
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await courseFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (selectedClassIds.value.length === 0) { ElMessage.warning('请至少选择一个行政班级'); return }
  saving.value = true
  try {
    const payload = {
      ...courseForm,
      classIds: selectedClassIds.value,
      inviteCode: inviteForm.inviteCode,
      inviteExpireTime: inviteForm.inviteExpireTime || undefined,
      inviteApproval: inviteForm.inviteApproval ? 1 : 0,
      inviteEnabled: inviteForm.inviteEnabled ? 1 : 0,
    }
    if (isEditing.value) {
      await updateCourse(courseForm.courseId, payload)
      ElMessage.success('修改成功')
    } else {
      await createCourse(payload)
      ElMessage.success('创建成功，请等待管理员审核')
    }
    dialogVisible.value = false
    await fetchCourses()
  } catch {} finally { saving.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.courseName}」吗？`, '确认删除', { type: 'warning' })
  await deleteCourse(row.courseId)
  ElMessage.success('已删除')
  await fetchCourses()
}

// ========== 章节管理（独立弹窗） ==========
const chapterVisible = ref(false)
const chapterCourse = ref(null)
const chapterSaving = ref(false)
const chapters = ref([])
let chapterIdCounter = 0

function addChapter() {
  chapterIdCounter++
  chapters.value.push({
    _key: chapterIdCounter,
    chapterName: '',
    chapterOrder: chapters.value.length + 1,
    videoUrl: '',
    videoFileName: '',
    attachmentUrl: '',
    attachmentFileName: '',
  })
}

function removeChapter(index) {
  chapters.value.splice(index, 1)
  chapters.value.forEach((ch, i) => { ch.chapterOrder = i + 1 })
}

function moveChapterUp(index) {
  if (index === 0) return
  const tmp = chapters.value[index]
  chapters.value[index] = chapters.value[index - 1]
  chapters.value[index - 1] = tmp
  chapters.value.forEach((ch, i) => { ch.chapterOrder = i + 1 })
}

function moveChapterDown(index) {
  if (index >= chapters.value.length - 1) return
  const tmp = chapters.value[index]
  chapters.value[index] = chapters.value[index + 1]
  chapters.value[index + 1] = tmp
  chapters.value.forEach((ch, i) => { ch.chapterOrder = i + 1 })
}

const chapterVideoUploading = ref({})
async function handleChapterVideoUpload(chapter, options) {
  chapterVideoUploading.value[chapter._key] = true
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    fd.append('module', 'resource')
    const res = await uploadFile(fd)
    chapter.videoUrl = res.data.fileUrl
    chapter.videoFileName = res.data.fileName || options.file.name
    ElMessage.success('视频上传成功')
  } catch {
    ElMessage.error('视频上传失败')
  } finally {
    chapterVideoUploading.value[chapter._key] = false
  }
}

const chapterAttachmentUploading = ref({})
async function handleChapterAttachmentUpload(chapter, options) {
  chapterAttachmentUploading.value[chapter._key] = true
  try {
    const fd = new FormData()
    fd.append('file', options.file)
    fd.append('module', 'resource')
    const res = await uploadFile(fd)
    chapter.attachmentUrl = res.data.fileUrl
    chapter.attachmentFileName = res.data.fileName || options.file.name
    ElMessage.success('课件上传成功')
  } catch {
    ElMessage.error('课件上传失败')
  } finally {
    chapterAttachmentUploading.value[chapter._key] = false
  }
}

async function openChapters(row) {
  chapterCourse.value = row
  chapterIdCounter = 0
  chapters.value = []
  chapterVisible.value = true
  try {
    const res = await getCourseProgress(row.courseId)
    const list = res.data?.chapterProgress || []
    chapters.value = list.map(ch => ({
      _key: ++chapterIdCounter,
      chapterName: ch.chapterName || '',
      chapterOrder: ch.chapterOrder || 1,
      videoUrl: ch.videoUrl || '',
      videoFileName: '',
      attachmentUrl: ch.attachmentUrl || '',
      attachmentFileName: '',
    }))
  } catch {
    chapters.value = []
  }
}

async function handleChapterSave() {
  chapterSaving.value = true
  try {
    await updateChapters(chapterCourse.value.courseId, {
      chapters: chapters.value.map(ch => ({
        chapterName: ch.chapterName,
        chapterOrder: ch.chapterOrder,
        videoUrl: ch.videoUrl,
        contentText: '',
        attachmentUrl: ch.attachmentUrl,
      })),
    })
    ElMessage.success('章节保存成功')
    chapterVisible.value = false
  } catch {} finally { chapterSaving.value = false }
}

// ========== 成绩管理 ==========
const gradeVisible = ref(false)
const gradeCourse = ref(null)
const gradeList = ref([])
const hasExperiment = computed(() => gradeList.value.some(s => s.experimentGrade != null))
const hasTraining = computed(() => gradeList.value.some(s => s.trainingGrade != null))

async function openGrades(row) {
  gradeCourse.value = row
  gradeVisible.value = true
  gradeList.value = []
  try {
    const res = await getGrades(row.courseId)
    gradeList.value = res.data || []
  } catch {
    gradeList.value = []
  }
}

async function handleGradeSave() {
  saving.value = true
  try {
    await saveGrades(gradeCourse.value.courseId, gradeList.value.map(s => ({
      studentId: s.studentId || s.userId,
      usualGrade: s.usualGrade ?? s.dailyScore,
      examGrade: s.examGrade ?? s.examScore,
      experimentGrade: s.experimentGrade ?? s.experimentScore,
      trainingGrade: s.trainingGrade,
    })))
    ElMessage.success('成绩保存成功')
    gradeVisible.value = false
  } catch {} finally { saving.value = false }
}

async function handleExport(row) {
  try {
    const res = await exportGrades(row.courseId)
    const blob = res.data
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.courseName}_成绩表.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

// 课程详情弹窗
const detailVisible = ref(false)
const detailCourse = ref(null)

function openDetail(row) {
  detailCourse.value = row
  detailVisible.value = true
}

onMounted(fetchCourses)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">课程计划管理</h1>
      <el-button type="primary" @click="openCreate">创建开课计划</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-input v-model="searchForm.keyword" placeholder="搜索课程" clearable style="width: 200px" />
        <el-select v-model="searchForm.auditStatus" placeholder="审核状态" clearable style="width: 120px">
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="fetchCourses">查询</el-button>
      </div>

      <el-table :data="courses" v-loading="loading" stripe>
        <el-table-column label="课程名称" min-width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">{{ row.courseName }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="courseCode" label="课程代码" width="100" />
        <el-table-column label="行政班级" min-width="160">
          <template #default="{ row }">
            <span style="font-size:12px">{{ (row.classNames || []).join('、') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="邀请码" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.inviteCode" :type="row.inviteEnabled ? 'success' : 'info'" size="small">
              {{ row.inviteCode }}
            </el-tag>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="课时进度" width="120">
          <template #default="{ row }"><span style="white-space:nowrap">{{ row.completedHours || 0 }}/{{ row.totalHours }} ({{ row.weeklyHours }}课时/周)</span></template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditStatusConfig[row.auditStatus]?.type" size="small">
              {{ auditStatusConfig[row.auditStatus]?.label || row.auditStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)" :disabled="row.auditStatus === 'APPROVED'">编辑</el-button>
            <el-button size="small" type="primary" @click="openChapters(row)">章节</el-button>
            <el-button size="small" type="warning" @click="openGrades(row)">成绩</el-button>
            <el-button size="small" type="success" @click="handleExport(row)">导出</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" :disabled="row.auditStatus === 'APPROVED'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="pagination.total"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="fetchCourses"
      />
    </div>

    <!-- ========== 创建/编辑课程弹窗 ========== -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="650px" destroy-on-close top="3vh">
      <el-form ref="courseFormRef" :model="courseForm" :rules="rules" label-width="80px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="课程名称" prop="courseName">
              <el-input v-model="courseForm.courseName" placeholder="如：Java程序设计" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课程代码" prop="courseCode">
              <el-input v-model="courseForm.courseCode" placeholder="如：CS101" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="授课学期">
              <el-select v-model="courseForm.semester" style="width:100%">
                <el-option label="2025-2026-2" value="2025-2026-2" />
                <el-option label="2026-2027-1" value="2026-2027-1" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总课时">
              <el-input-number v-model="courseForm.totalHours" :min="1" :max="200" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="周课时">
              <el-input-number v-model="courseForm.weeklyHours" :min="1" :max="20" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开课日期">
              <el-date-picker v-model="courseForm.startDate" type="date" placeholder="选择日期" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="课程描述">
          <el-input v-model="courseForm.description" type="textarea" :rows="2" placeholder="请输入课程描述..." />
        </el-form-item>

        <!-- 行政班级多选 -->
        <el-divider content-position="left">行政班级（勾选后学生自动入班）</el-divider>
        <el-checkbox-group v-model="selectedClassIds" class="class-checkbox-group">
          <el-checkbox v-for="c in allClasses" :key="c.classId" :value="c.classId" :label="c.classId">
            {{ c.className }}
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="allClasses.length === 0" style="color:#909399;font-size:13px">
          暂无行政班级数据，请先联系管理员创建
        </div>

        <!-- 邀请码 -->
        <el-divider content-position="left">邀请码</el-divider>
        <el-row :gutter="16">
          <el-col :span="10">
            <el-form-item label="邀请码">
              <el-input v-model="inviteForm.inviteCode" readonly style="width:100%">
                <template #append>
                  <el-button @click="generateInviteCode">🔄</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="有效期">
              <el-date-picker v-model="inviteForm.inviteExpireTime" type="date" placeholder="不限" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="入班审核">
              <el-switch v-model="inviteForm.inviteApproval" />
              <span style="margin-left:4px;font-size:12px;color:#909399">{{ inviteForm.inviteApproval ? '需审核' : '直接入班' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- ========== 章节管理弹窗（审核通过后可用） ========== -->
    <el-dialog v-model="chapterVisible" :title="chapterCourse?.courseName + ' — 章节管理'" width="800px" destroy-on-close top="3vh">
      <div class="chapter-toolbar">
        <el-button type="primary" size="small" @click="addChapter">+ 添加章节</el-button>
      </div>
      <div v-if="chapters.length === 0" style="color:#909399;text-align:center;padding:24px 0">
        暂未添加章节，请点击上方按钮创建
      </div>
      <div v-for="(ch, idx) in chapters" :key="ch._key" class="chapter-editor">
        <div class="chapter-header">
          <span class="chapter-label">第 {{ ch.chapterOrder }} 章</span>
          <div class="chapter-actions">
            <el-button size="small" text :disabled="idx === 0" @click="moveChapterUp(idx)">↑</el-button>
            <el-button size="small" text :disabled="idx >= chapters.length - 1" @click="moveChapterDown(idx)">↓</el-button>
            <el-button size="small" text type="danger" @click="removeChapter(idx)">删除</el-button>
          </div>
        </div>
        <el-form-item label="章节名称" label-width="70px">
          <el-input v-model="ch.chapterName" placeholder="如：第一章 Java概述" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="教学视频" label-width="70px">
              <div class="upload-row">
                <el-upload :show-file-list="false" :http-request="(opts) => handleChapterVideoUpload(ch, opts)" accept="video/*">
                  <el-button size="small" :loading="chapterVideoUploading[ch._key]">上传视频</el-button>
                </el-upload>
                <span v-if="ch.videoFileName" class="upload-name">{{ ch.videoFileName }}</span>
                <span v-else-if="ch.videoUrl" class="upload-name">已有视频</span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课件附件" label-width="70px">
              <div class="upload-row">
                <el-upload :show-file-list="false" :http-request="(opts) => handleChapterAttachmentUpload(ch, opts)" accept=".pdf,.ppt,.pptx,.doc,.docx">
                  <el-button size="small" :loading="chapterAttachmentUploading[ch._key]">上传课件</el-button>
                </el-upload>
                <span v-if="ch.attachmentFileName" class="upload-name">{{ ch.attachmentFileName }}</span>
                <span v-else-if="ch.attachmentUrl" class="upload-name">已有课件</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      <template #footer>
        <el-button @click="chapterVisible = false">取消</el-button>
        <el-button type="primary" :loading="chapterSaving" @click="handleChapterSave">保存章节</el-button>
      </template>
    </el-dialog>

    <!-- ========== 成绩管理弹窗 ========== -->
    <el-dialog v-model="gradeVisible" :title="gradeCourse?.courseName + ' — 成绩管理'" destroy-on-close>
      <el-table :data="gradeList" stripe max-height="500">
        <el-table-column prop="userName" label="姓名" min-width="80" />
        <el-table-column label="平时" min-width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.usualGrade" :min="0" :max="100" size="small" controls-position="right" style="width:90px" />
          </template>
        </el-table-column>
        <el-table-column label="考试" min-width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.examGrade" :min="0" :max="100" size="small" controls-position="right" style="width:90px" />
          </template>
        </el-table-column>
        <el-table-column v-if="hasExperiment" label="实验" min-width="100">
          <template #default="{ row }">
            <span :style="{ color: row.experimentGrade != null ? '#303133' : '#c0c4cc' }">
              {{ row.experimentGrade != null ? row.experimentGrade : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="hasTraining" label="实训" min-width="100">
          <template #default="{ row }">
            <span :style="{ color: row.trainingGrade != null ? '#303133' : '#c0c4cc' }">
              {{ row.trainingGrade != null ? row.trainingGrade : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="总评" min-width="80">
          <template #default="{ row }">
            {{ row.finalGrade != null ? row.finalGrade : (Math.round(((row.usualGrade || 0) * 0.3 + (row.examGrade || 0) * 0.4 + ((row.experimentGrade || row.trainingGrade || 0) * 0.3)) * 10) / 10 || '-') }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleGradeSave">保存成绩</el-button>
      </template>
    </el-dialog>

    <!-- ========== 课程详情弹窗 ========== -->
    <el-dialog v-model="detailVisible" :title="detailCourse?.courseName" width="500px" destroy-on-close>
      <el-descriptions v-if="detailCourse" :column="2" border size="small">
        <el-descriptions-item label="课程名称">{{ detailCourse.courseName }}</el-descriptions-item>
        <el-descriptions-item label="课程代码">{{ detailCourse.courseCode }}</el-descriptions-item>
        <el-descriptions-item label="学期">{{ detailCourse.semester }}</el-descriptions-item>
        <el-descriptions-item label="周课时">{{ detailCourse.weeklyHours }} 课时/周</el-descriptions-item>
        <el-descriptions-item label="总课时">{{ detailCourse.totalHours }}</el-descriptions-item>
        <el-descriptions-item label="已完成课时">{{ detailCourse.completedHours || 0 }}</el-descriptions-item>
        <el-descriptions-item label="行政班级" :span="2">
          {{ (detailCourse.classNames || []).join('、') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="邀请码">
          <el-tag v-if="detailCourse.inviteCode" :type="detailCourse.inviteEnabled ? 'success' : 'info'" size="small">
            {{ detailCourse.inviteCode }}
          </el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="学生数">{{ detailCourse.studentCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="auditStatusConfig[detailCourse.auditStatus]?.type" size="small">
            {{ auditStatusConfig[detailCourse.auditStatus]?.label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="课程进度">
          <el-progress :percentage="detailCourse.progress || 0" :stroke-width="8" />
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

.class-checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.class-checkbox-group .el-checkbox {
  margin-right: 0;
}

.chapter-toolbar {
  margin-bottom: 12px;
}

.chapter-editor {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fafafa;
}

.chapter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.chapter-label {
  font-weight: 600;
  font-size: 14px;
  color: #409eff;
}

.chapter-actions {
  display: flex;
  gap: 4px;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-name {
  font-size: 12px;
  color: #67c23a;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
