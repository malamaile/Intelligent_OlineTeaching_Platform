<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, getUserDetail, createUser, updateUser, updateUserStatus, resetUserPassword, deleteUser, createClass, importUsers } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import { getDepartments, getClasses } from '@/api/common'

const userStore = useUserStore()
const loading = ref(false)

const users = ref([])
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })

const searchForm = reactive({ keyword: '', role: '', status: '', department: '' })

const statusConfig = {
  ACTIVE: { label: '正常', type: 'success' },
  FROZEN: { label: '已冻结', type: 'info' },
  LOCKED: { label: '已锁定', type: 'danger' },
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await getUsers({
      keyword: searchForm.keyword || undefined,
      role: searchForm.role || undefined,
      status: searchForm.status === 'ACTIVE' ? 1 : searchForm.status === 'FROZEN' ? 0 : undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    users.value = res.data.records || res.data
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

// 院系和班级选项（从后端动态获取）
const departmentOptions = ref([])
const classOptions = ref([])

async function fetchDepartments() {
  try {
    const res = await getDepartments()
    departmentOptions.value = res.data || []
  } catch {}
}

async function fetchClasses(departmentId) {
  try {
    const res = await getClasses({ departmentId: departmentId || undefined })
    classOptions.value = res.data || []
  } catch {}
}

// 新增/编辑弹窗
const dialogVisible = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const userFormRef = ref(null)
const userForm = reactive({
  userId: null, account: '', userName: '', role: 'STUDENT', password: '',
  department: '', className: '', email: '', phone: '',
})

// 院系变更时重新加载班级列表（必须在 userForm 声明之后）
watch(() => userForm.department, (newDept) => {
  if (!newDept) {
    classOptions.value = []
    userForm.className = ''
    return
  }
  // 按院系名称查找 departmentId
  const dept = departmentOptions.value.find(d => d.departmentName === newDept)
  fetchClasses(dept ? dept.departmentId : undefined)
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

function openCreate() {
  isEditing.value = false
  Object.assign(userForm, { userId: null, account: '', userName: '', role: 'STUDENT', password: '', department: '', className: '', email: '', phone: '' })
  classOptions.value = []
  dialogVisible.value = true
}

function openEdit(row) {
  isEditing.value = true
  // 后端返回字段名为 id 和 departmentName，需正确映射到 userForm
  Object.assign(userForm, {
    userId: row.id || row.userId,           // 后端返回 id
    account: row.account || row.username,
    userName: row.userName || row.realName,
    role: row.role,
    department: row.department || row.departmentName || '',
    className: row.className || '',
    email: row.email || '',
    phone: row.phone || '',
    password: '',
  })
  // 预加载当前院系对应的班级列表
  const dept = departmentOptions.value.find(d => d.departmentName === userForm.department)
  if (dept) fetchClasses(dept.departmentId)
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await userFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEditing.value) {
      await updateUser(userForm.userId, {
        userName: userForm.userName,
        department: userForm.department,
        className: userForm.className,
        email: userForm.email,
        phone: userForm.phone,
      })
      ElMessage.success('修改成功')
    } else {
      await createUser(userForm)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败，请重试')
  } finally { saving.value = false }
}

// 批量导入
const importVisible = ref(false)
const importFile = ref(null)

async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请选择文件'); return }
  const formData = new FormData()
  formData.append('file', importFile.value)
  try {
    const res = await importUsers(formData)
    const data = res.data || {}
    const ok = data.successCount || 0
    const fail = data.failCount || 0
    if (fail > 0) {
      ElMessage.warning(`导入完成：成功 ${ok} 条，失败 ${fail} 条`)
    } else {
      ElMessage.success(`成功导入 ${ok} 条`)
    }
    importVisible.value = false
    importFile.value = null
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e?.message || '导入失败')
  }
}

function handleFileChange(file) {
  importFile.value = file.raw
}

// 状态操作
async function handleToggleStatus(row) {
  const action = row.statusType === 'ACTIVE' ? '冻结' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}账号「${row.userName}」吗？`, `确认${action}`, { type: 'warning' })
  } catch { return }
  try {
    const newStatus = row.statusType === 'ACTIVE' ? 'FROZEN' : 'ACTIVE'
    await updateUserStatus(row.id || row.userId, { status: newStatus })
    ElMessage.success(`已${action}`)
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleResetPassword(row) {
  try {
    await ElMessageBox.confirm(`确定重置「${row.userName}」的密码为默认密码吗？`, '重置密码', { type: 'warning' })
  } catch { return }
  try {
    await resetUserPassword(row.id || row.userId, {})
    ElMessage.success('密码已重置为默认密码')
  } catch (e) {
    ElMessage.error(e?.message || '重置失败')
  }
}

async function handleDelete(row) {
  if ((row.id || row.userId) === userStore.userId) {
    ElMessage.warning('不能删除自己的账号')
    return
  }
  try {
    await ElMessageBox.confirm(`确定永久删除用户「${row.userName}」吗？此操作不可恢复。`, '删除用户', { type: 'error', confirmButtonText: '确定删除' })
  } catch { return }
  try {
    await deleteUser(row.id || row.userId)
    ElMessage.success('用户已删除')
    await fetchUsers()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// 新建班级
const classDialogVisible = ref(false)
const classFormRef = ref(null)
const classForm = reactive({ className: '', classCode: '', departmentId: '', grade: '' })
const classSaving = ref(false)

const classRules = {
  className: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  departmentId: [{ required: true, message: '请选择所属学院', trigger: 'change' }],
}

function openCreateClass() {
  Object.assign(classForm, { className: '', classCode: '', departmentId: '', grade: '' })
  classDialogVisible.value = true
}

async function handleCreateClass() {
  const valid = await classFormRef.value.validate().catch(() => false)
  if (!valid) return
  classSaving.value = true
  try {
    await createClass({
      className: classForm.className,
      classCode: classForm.classCode || classForm.className,
      departmentId: Number(classForm.departmentId),
      grade: classForm.grade || undefined,
    })
    ElMessage.success('班级创建成功')
    classDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  } finally { classSaving.value = false }
}

// 用户详情弹窗
const detailVisible = ref(false)
const detailLoading = ref(false)
const userDetail = ref({})

async function handleViewDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getUserDetail(row.id || row.userId)
    if (res.data) {
      userDetail.value = res.data
    }
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  fetchUsers()
  fetchDepartments()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">用户管理</h1>
      <div>
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button @click="openCreateClass">新建班级</el-button>
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </div>

    <div class="card-wrapper">
      <div class="search-bar">
        <el-input v-model="searchForm.keyword" placeholder="姓名/学号/工号" clearable class="search-input" />
        <el-select v-model="searchForm.role" placeholder="角色" clearable class="search-select">
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-select v-model="searchForm.status" placeholder="状态" clearable class="search-select">
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已冻结" value="FROZEN" />
          <el-option label="已锁定" value="LOCKED" />
        </el-select>
        <el-button type="primary" @click="fetchUsers">查询</el-button>
      </div>

      <el-table :data="users" v-loading="loading" stripe style="width:100%" @row-click="handleViewDetail">
        <el-table-column prop="account" label="账号" min-width="90" />
        <el-table-column prop="userName" label="姓名" min-width="70" />
        <el-table-column label="角色" min-width="60">
          <template #default="{ row }">
            <el-tag size="small">{{ row.role === 'STUDENT' ? '学生' : row.role === 'TEACHER' ? '教师' : '管理员' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="院系" min-width="100" />
        <el-table-column prop="className" label="班级" min-width="110" />
        <el-table-column prop="email" label="邮箱" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" min-width="70">
          <template #default="{ row }">
            <el-tag :type="statusConfig[row.statusType]?.type" size="small">{{ statusConfig[row.statusType]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="{ row }" @click.stop>
            <div class="action-btns">
              <el-button size="small" @click.stop="openEdit(row)">编辑</el-button>
              <el-button size="small" :type="row.statusType === 'ACTIVE' ? 'warning' : 'success'" @click.stop="handleToggleStatus(row)">
                {{ row.statusType === 'ACTIVE' ? '冻结' : '启用' }}
              </el-button>
              <el-button size="small" type="info" @click.stop="handleResetPassword(row)">重置密码</el-button>
              <el-button size="small" type="danger" @click.stop="handleDelete(row)" :disabled="(row.id || row.userId) === userStore.userId">删除</el-button>
            </div>
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
        @change="fetchUsers"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="userFormRef" :model="userForm" :rules="rules" label-width="80px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="账号" prop="account">
              <el-input v-model="userForm.account" placeholder="学号/工号" :disabled="isEditing" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="userName">
              <el-input v-model="userForm.userName" placeholder="真实姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色" prop="role">
              <el-select v-model="userForm.role" style="width:100%" :disabled="isEditing">
                <el-option label="学生" value="STUDENT" />
                <el-option label="教师" value="TEACHER" />
                <el-option label="管理员" value="ADMIN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" v-if="!isEditing">
              <el-input v-model="userForm.password" type="password" placeholder="默认密码" show-password />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="院系" v-if="userForm.role !== 'ADMIN'">
          <el-select v-model="userForm.department" style="width:100%" placeholder="选择院系" clearable>
            <el-option v-for="d in departmentOptions" :key="d.departmentId" :label="d.departmentName" :value="d.departmentName" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" v-if="userForm.role === 'STUDENT'">
          <el-select v-model="userForm.className" style="width:100%" placeholder="选择班级" clearable :disabled="!userForm.department">
            <el-option v-for="c in classOptions" :key="c.classId" :label="c.className" :value="c.className" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="userForm.email" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="userForm.phone" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="520px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-descriptions v-if="userDetail.id" :column="2" border size="small">
          <el-descriptions-item label="账号">{{ userDetail.account || userDetail.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ userDetail.userName || userDetail.realName }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag size="small">{{ userDetail.role === 'STUDENT' ? '学生' : userDetail.role === 'TEACHER' ? '教师' : '管理员' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="userDetail.statusType === 'ACTIVE' ? 'success' : 'danger'" size="small">{{ userDetail.statusType === 'ACTIVE' ? '正常' : userDetail.statusType === 'FROZEN' ? '已冻结' : '已锁定' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="院系">{{ userDetail.departmentName || userDetail.department || '-' }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ userDetail.className || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ userDetail.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ userDetail.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="登录失败次数">{{ userDetail.loginFailCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ userDetail.lastLoginTime || '从未登录' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ userDetail.createTime }}</el-descriptions-item>
        </el-descriptions>
        <div v-else-if="!detailLoading" style="text-align:center;color:#909399;padding:20px">无法加载用户详情</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新建班级弹窗 -->
    <el-dialog v-model="classDialogVisible" title="新建班级" width="500px" destroy-on-close>
      <el-form ref="classFormRef" :model="classForm" :rules="classRules" label-width="80px">
        <el-form-item label="班级名称" prop="className">
          <el-input v-model="classForm.className" placeholder="如：软件工程2201班" />
        </el-form-item>
        <el-form-item label="班级编码" prop="classCode">
          <el-input v-model="classForm.classCode" placeholder="如：SE2201（不填默认同名称）" />
        </el-form-item>
        <el-form-item label="所属学院" prop="departmentId">
          <el-select v-model="classForm.departmentId" style="width:100%" placeholder="选择学院">
            <el-option v-for="d in departmentOptions" :key="d.departmentId" :label="d.departmentName" :value="d.departmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级" prop="grade">
          <el-input v-model="classForm.grade" placeholder="如：2022（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="classSaving" @click="handleCreateClass">创建</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importVisible" title="批量导入学生" width="500px">
      <el-alert title="请按模板格式上传Excel文件" type="info" :closable="false" show-icon style="margin-bottom:16px">
        <template #default>
          <p style="font-size:12px">模板列：账号 | 姓名 | 角色（STUDENT/TEACHER/ADMIN） | 密码 | 部门 | 班级 | 邮箱 | 手机号</p>
        </template>
      </el-alert>
      <el-upload drag :auto-upload="false" accept=".xlsx,.xls" :on-change="handleFileChange">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽或<em>点击上传</em>Excel文件</div>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; }

/* 表格行可点击 */
:deep(.el-table__row) { cursor: pointer; }

/* 操作按钮同一排 */
.action-btns { display: flex; gap: 4px; flex-wrap: nowrap; }

/* 搜索栏响应式 */
.search-input { width: 180px; }
.search-select { width: 100px; }

@media (max-width: 1400px) {
  .search-input { width: 140px; }
  .search-select { width: 90px; }
}

@media (max-width: 1200px) {
  .search-bar { flex-wrap: wrap; }
  .search-input { width: 130px; }
  .search-select { width: 85px; }
}

@media (max-width: 768px) {
  .search-input, .search-select { width: 100%; min-width: 0; }
}
</style>
