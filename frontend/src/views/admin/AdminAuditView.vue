<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAuditCourses, auditCourse, getAuditCourseLogs, getCourseAuditStatistics, getAuditTasks, auditTask, getTaskAuditStatistics, getAuditResources, auditResource, getResourceAuditStatistics } from '@/api/admin'

const activeTab = ref('courses')

// ========== 课程审核 ==========
const courseLoading = ref(false)
const courseAuditList = ref([])
const courseLogs = ref([])
const courseStats = reactive({ pending: 0, approved: 0, rejected: 0 })
const coursePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const courseStatusFilter = ref('')

async function fetchCourseAudit() {
  courseLoading.value = true
  try {
    const [listRes, statsRes, logsRes] = await Promise.all([
      getAuditCourses({ status: courseStatusFilter.value || undefined, page: coursePagination.page, pageSize: coursePagination.pageSize }),
      getCourseAuditStatistics(),
      getAuditCourseLogs({ page: 1, pageSize: 10 }),
    ])
    courseAuditList.value = listRes.data.records || listRes.data
    coursePagination.total = listRes.data.total || 0
    if (statsRes.data) {
      courseStats.pending = statsRes.data.pending || 0
      courseStats.approved = statsRes.data.approved || 0
      courseStats.rejected = statsRes.data.rejected || 0
    }
    courseLogs.value = logsRes.data.records || logsRes.data || []
  } finally {
    courseLoading.value = false
  }
}

// ========== 任务审核 ==========
const taskLoading = ref(false)
const taskAuditList = ref([])
const taskStats = reactive({ pending: 0, total: 0, submissionRate: 0 })
const taskPagination = reactive({ page: 1, pageSize: 10, total: 0 })
const taskStatusFilter = ref('')

async function fetchTaskAudit() {
  taskLoading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      getAuditTasks({ status: taskStatusFilter.value || undefined, page: taskPagination.page, pageSize: taskPagination.pageSize }),
      getTaskAuditStatistics(),
    ])
    taskAuditList.value = listRes.data.records || listRes.data
    taskPagination.total = listRes.data.total || 0
    if (statsRes.data) {
      taskStats.pending = statsRes.data.pending || 0
      taskStats.total = statsRes.data.total || 0
      taskStats.submissionRate = statsRes.data.submissionRate || 0
    }
  } finally {
    taskLoading.value = false
  }
}

// ========== 资源审核 ==========
const resourceLoading = ref(false)
const resourceAuditList = ref([])
const resourceStats = reactive({ pending: 0, total: 0, downloads: 0 })
const resourcePagination = reactive({ page: 1, pageSize: 10, total: 0 })
const resourceStatusFilter = ref('')

async function fetchResourceAudit() {
  resourceLoading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      getAuditResources({ status: resourceStatusFilter.value || undefined, page: resourcePagination.page, pageSize: resourcePagination.pageSize }),
      getResourceAuditStatistics(),
    ])
    resourceAuditList.value = listRes.data.records || listRes.data
    resourcePagination.total = listRes.data.total || 0
    if (statsRes.data) {
      resourceStats.pending = statsRes.data.pending || 0
      resourceStats.total = statsRes.data.total || 0
      resourceStats.downloads = statsRes.data.downloads || statsRes.data.totalDownloads || 0
    }
  } finally {
    resourceLoading.value = false
  }
}

// 审核操作
async function handleAudit(type, row, action) {
  let comment = ''
  if (action === 'REJECTED') {
    try {
      const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回审核', { inputErrorMessage: '驳回必须填写原因' })
      comment = value
    } catch {
      return
    }
  } else {
    try {
      const { value } = await ElMessageBox.prompt('审核意见（可选）', '通过审核', { inputErrorMessage: '' })
      comment = value || ''
    } catch {
      comment = ''
    }
  }

  try {
    const data = { action, comment }
    if (type === 'course') {
      await auditCourse(row.planId, data)
      await fetchCourseAudit()
    } else if (type === 'task') {
      await auditTask(row.taskId, data)
      await fetchTaskAudit()
    } else if (type === 'resource') {
      await auditResource(row.resourceId, data)
      await fetchResourceAudit()
    }
    ElMessage.success(action === 'APPROVED' ? '已通过' : '已驳回')
  } catch {}
}

// Tab 切换时加载对应数据
watch(activeTab, (tab) => {
  if (tab === 'courses') fetchCourseAudit()
  else if (tab === 'tasks') fetchTaskAudit()
  else if (tab === 'resources') fetchResourceAudit()
})

onMounted(fetchCourseAudit)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">教学内容统一审核</h1>
    </div>

    <div class="card-wrapper">
      <el-tabs v-model="activeTab">
        <!-- 课程审核 -->
        <el-tab-pane label="课程开课计划" name="courses">
          <div class="stat-cards" style="margin-bottom:16px">
            <div class="stat-card"><div class="stat-label">待审核</div><div class="stat-value warning">{{ courseStats.pending }}</div></div>
            <div class="stat-card"><div class="stat-label">已通过</div><div class="stat-value success">{{ courseStats.approved }}</div></div>
            <div class="stat-card"><div class="stat-label">已驳回</div><div class="stat-value danger">{{ courseStats.rejected }}</div></div>
          </div>

          <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
            <span style="font-size:13px;color:#606266">审核状态：</span>
            <el-select v-model="courseStatusFilter" size="small" style="width:110px" @change="fetchCourseAudit">
              <el-option label="全部" value="" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </div>

          <el-table :data="courseAuditList" v-loading="courseLoading" stripe>
            <el-table-column prop="courseName" label="课程名称" min-width="130" />
            <el-table-column prop="courseCode" label="代码" width="70" />
            <el-table-column prop="teacherName" label="教师" width="80" />
            <el-table-column prop="className" label="班级" width="140" />
            <el-table-column prop="totalHours" label="课时" width="60" />
            <el-table-column prop="submitTime" label="提交时间" width="150" />
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.auditStatus === 'PENDING'">
                  <el-button size="small" type="success" @click="handleAudit('course', row, 'APPROVED')">通过</el-button>
                  <el-button size="small" type="danger" @click="handleAudit('course', row, 'REJECTED')">驳回</el-button>
                </template>
                <el-tag v-else :type="row.auditStatus === 'APPROVED' ? 'success' : 'danger'" size="small">
                  {{ row.auditStatus === 'APPROVED' ? '已通过' : '已驳回' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="card-title" style="margin-top:20px">审核日志</div>
          <el-table :data="courseLogs" stripe size="small">
            <el-table-column prop="courseName" label="课程" width="130" />
            <el-table-column prop="teacherName" label="教师" width="80" />
            <el-table-column label="操作" width="60">
              <template #default="{ row }">
                <el-tag :type="row.actionShort === 'APPROVE' || row.action === 'APPROVED' ? 'success' : 'danger'" size="small">
                  {{ row.result || (row.actionShort === 'APPROVE' || row.action === 'APPROVED' ? '通过' : '驳回') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="auditor" label="审核人" width="80" />
            <el-table-column prop="comment" label="意见" min-width="150" />
            <el-table-column prop="time" label="时间" width="150" />
          </el-table>
        </el-tab-pane>

        <!-- 实训审核 -->
        <el-tab-pane label="实训实验计划" name="tasks">
          <div class="stat-cards" style="margin-bottom:16px">
            <div class="stat-card"><div class="stat-label">待审核</div><div class="stat-value warning">{{ taskStats.pending }}</div></div>
            <div class="stat-card"><div class="stat-label">提交批阅率</div><div class="stat-value primary">{{ taskStats.submissionRate }}%</div></div>
          </div>
          <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
            <span style="font-size:13px;color:#606266">审核状态：</span>
            <el-select v-model="taskStatusFilter" size="small" style="width:110px" @change="fetchTaskAudit">
              <el-option label="全部" value="" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </div>
          <el-table :data="taskAuditList" v-loading="taskLoading" stripe>
            <el-table-column prop="title" label="任务名称" min-width="150" />
            <el-table-column prop="taskType" label="类型" width="60"><template #default="{ row }">{{ row.taskType==='EXPERIMENT'?'实验':'实训' }}</template></el-table-column>
            <el-table-column prop="teacherName" label="教师" width="80" />
            <el-table-column prop="className" label="班级" width="140" />
            <el-table-column label="时间" width="200"><template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template></el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.auditStatus === 'PENDING'">
                  <el-button size="small" type="success" @click="handleAudit('task', row, 'APPROVED')">通过</el-button>
                  <el-button size="small" type="danger" @click="handleAudit('task', row, 'REJECTED')">驳回</el-button>
                </template>
                <el-tag v-else :type="row.auditStatus==='APPROVED'?'success':'danger'" size="small">
                  {{ row.auditStatus==='APPROVED'?'已通过':'已驳回' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 资源审核 -->
        <el-tab-pane label="教学资源" name="resources">
          <div class="stat-cards" style="margin-bottom:16px">
            <div class="stat-card"><div class="stat-label">待审核</div><div class="stat-value warning">{{ resourceStats.pending }}</div></div>
            <div class="stat-card"><div class="stat-label">总资源数</div><div class="stat-value primary">{{ resourceStats.total }}</div></div>
            <div class="stat-card"><div class="stat-label">总下载量</div><div class="stat-value success">{{ resourceStats.downloads }}</div></div>
          </div>
          <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
            <span style="font-size:13px;color:#606266">审核状态：</span>
            <el-select v-model="resourceStatusFilter" size="small" style="width:110px" @change="fetchResourceAudit">
              <el-option label="全部" value="" />
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
            </el-select>
          </div>
          <el-table :data="resourceAuditList" v-loading="resourceLoading" stripe>
            <el-table-column prop="name" label="资源名称" min-width="160" />
            <el-table-column label="类型" width="70">
              <template #default="{ row }">
                {{ { COURSEWARE: '课件', EXERCISE: '习题', VIDEO: '视频', DOCUMENT: '文档', OTHER: '其他' }[row.type] || row.type || row.categoryName }}
              </template>
            </el-table-column>
            <el-table-column prop="teacherName" label="教师" width="80" />
            <el-table-column prop="courseName" label="关联课程" width="120" />
            <el-table-column label="可见范围" width="80">
              <template #default="{ row }">
                {{ { CLASS_ONLY: '本班', DEPARTMENT_WIDE: '院系', SCHOOL_WIDE: '全校' }[row.scope] || row.scope || row.visibility }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.auditStatus === 'PENDING'">
                  <el-button size="small" type="success" @click="handleAudit('resource', row, 'APPROVED')">通过</el-button>
                  <el-button size="small" type="danger" @click="handleAudit('resource', row, 'REJECTED')">驳回</el-button>
                </template>
                <el-tag v-else :type="row.auditStatus==='APPROVED'?'success':'danger'" size="small">
                  {{ row.auditStatus==='APPROVED'?'已通过':'已驳回' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.stat-card { border: none; }
</style>
