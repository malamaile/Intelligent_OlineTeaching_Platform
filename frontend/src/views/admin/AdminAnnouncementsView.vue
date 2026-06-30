<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAnnouncements, createAnnouncement, updateAnnouncement, deleteAnnouncement } from '@/api/admin'
import { getDepartments } from '@/api/common'

const loading = ref(false)

const announcements = ref([])
const departmentOptions = ref([])
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const scopeConfig = {
  SCHOOL_WIDE: { label: '全校', type: 'danger' },
  DEPARTMENT_WIDE: { label: '院系', type: 'warning' },
  CLASS_ONLY: { label: '班级', type: 'primary' },
}

const searchForm = reactive({ keyword: '', scope: '' })

async function fetchAnnouncements() {
  loading.value = true
  try {
    const res = await getAnnouncements({
      keyword: searchForm.keyword || undefined,
      scope: searchForm.scope || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    announcements.value = res.data.records || res.data
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// 发布弹窗
const dialogVisible = ref(false)
const isEditing = ref(false)
const publishing = ref(false)
const announceFormRef = ref(null)
const announceForm = reactive({
  announcementId: null,
  title: '', content: '', scope: 'SCHOOL_WIDE', department: '', importance: 'NORMAL',
})

const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  scope: [{ required: true, message: '请选择公告范围', trigger: 'change' }],
}

function openPublish() {
  isEditing.value = false
  Object.assign(announceForm, { announcementId: null, title: '', content: '', scope: 'SCHOOL_WIDE', department: '', importance: 'NORMAL' })
  dialogVisible.value = true
}

function openEdit(row) {
  isEditing.value = true
  Object.assign(announceForm, { ...row })
  dialogVisible.value = true
}

async function handlePublish() {
  const valid = await announceFormRef.value.validate().catch(() => false)
  if (!valid) return
  publishing.value = true
  try {
    // 构建干净的数据：全校公告不携带 department 字段
    const payload = {
      title: announceForm.title,
      content: announceForm.content,
      scope: announceForm.scope,
      importance: announceForm.importance,
    }
    if (announceForm.scope === 'DEPARTMENT_WIDE' && announceForm.department) {
      payload.department = announceForm.department
    }
    if (isEditing.value) {
      await updateAnnouncement(announceForm.announcementId, payload)
      ElMessage.success('公告已更新')
    } else {
      await createAnnouncement(payload)
      ElMessage.success('公告已发布')
    }
    dialogVisible.value = false
    await fetchAnnouncements()
  } catch {} finally { publishing.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？`, '确认删除', { type: 'warning' })
  await deleteAnnouncement(row.announcementId)
  ElMessage.success('已删除')
  await fetchAnnouncements()
}

async function fetchDepartments() {
  try {
    const res = await getDepartments()
    departmentOptions.value = res.data || []
  } catch {}
}

onMounted(() => {
  fetchAnnouncements()
  fetchDepartments()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">公告管理</h1>
      <el-button type="primary" @click="openPublish">发布公告</el-button>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-input v-model="searchForm.keyword" placeholder="搜索公告标题" clearable style="width:220px" />
        <el-select v-model="searchForm.scope" placeholder="范围" clearable style="width:100px">
          <el-option label="全校" value="SCHOOL_WIDE" />
          <el-option label="院系" value="DEPARTMENT_WIDE" />
        </el-select>
        <el-button type="primary" @click="fetchAnnouncements">查询</el-button>
      </div>

      <el-table :data="announcements" v-loading="loading" stripe>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="范围" width="80">
          <template #default="{ row }">
            <el-tag :type="scopeConfig[row.scope]?.type" size="small">
              {{ scopeConfig[row.scope]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="院系" width="140">
          <template #default="{ row }">{{ row.department || '-' }}</template>
        </el-table-column>
        <el-table-column label="重要程度" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.importance === 'IMPORTANT'" type="danger" size="small">重要</el-tag>
            <el-tag v-else type="info" size="small" effect="plain">普通</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisher" label="发布人" width="90" />
        <el-table-column prop="publishTime" label="发布时间" width="150" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
        @change="fetchAnnouncements"
      />
    </div>

    <!-- 发布公告弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑公告' : '发布公告'" width="600px" destroy-on-close>
      <el-form ref="announceFormRef" :model="announceForm" :rules="rules" label-width="80px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="announceForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发布范围" prop="scope">
              <el-radio-group v-model="announceForm.scope">
                <el-radio value="SCHOOL_WIDE">全校</el-radio>
                <el-radio value="DEPARTMENT_WIDE">院系</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="announceForm.scope === 'DEPARTMENT_WIDE'" label="选择院系" prop="department">
              <el-select v-model="announceForm.department" style="width:100%" placeholder="选择院系">
                <el-option v-for="d in departmentOptions" :key="d.departmentId" :label="d.departmentName" :value="d.departmentName" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="公告内容" prop="content">
          <el-input v-model="announceForm.content" type="textarea" :rows="6" placeholder="请输入公告内容..." />
        </el-form-item>
        <el-form-item label="重要程度">
          <el-radio-group v-model="announceForm.importance">
            <el-radio value="NORMAL">普通</el-radio>
            <el-radio value="IMPORTANT">重要</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="附件">
          <el-upload :auto-upload="false">
            <el-button type="primary" plain>上传附件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="handlePublish">{{ isEditing ? '保存修改' : '发布' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
