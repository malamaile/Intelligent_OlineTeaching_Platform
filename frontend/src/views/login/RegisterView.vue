<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register as registerAPI } from '@/api/auth'

const router = useRouter()

const step = ref(0)
const registering = ref(false)
const registerFormRef = ref(null)

const registerForm = reactive({
  role: 'STUDENT',
  account: '',
  userName: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  department: '',
  className: '',
})

const roleOptions = [
  { label: '学生', value: 'STUDENT' },
  { label: '教师', value: 'TEACHER' },
]

// 院系选项（与 mock 数据保持一致）
const departmentOptions = [
  '计算机科学学院',
  '数学学院',
  '电子信息学院',
  '物理学院',
  '化学学院',
  '生命科学学院',
  '经济管理学院',
  '外国语学院',
]

// 班级选项
const classOptions = [
  '软件工程2024-1班',
  '软件工程2024-2班',
  '计算机科学2024-1班',
  '计算机科学2024-2班',
  '电子信息2024-1班',
  '数学与应用数学2024-1班',
]

const validateConfirmPassword = (_rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  account: [
    { required: true, message: '请输入账号（学号/工号）', trigger: 'blur' },
    { min: 4, max: 20, message: '账号长度为 4-20 位', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '账号只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  userName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '姓名长度为 2-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  department: [
    { required: true, message: '请选择院系', trigger: 'change' },
  ],
  className: [
    { required: true, message: '请选择班级', trigger: 'change' },
  ],
}

async function handleRegister() {
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  registering.value = true
  try {
    await registerAPI({
      account: registerForm.account,
      password: registerForm.password,
      userName: registerForm.userName,
      role: registerForm.role,
      department: registerForm.department,
      className: registerForm.className,
      email: registerForm.email || undefined,
      phone: registerForm.phone || undefined,
    })
    step.value = 1
  } catch {
    // 错误已在拦截器中处理
  } finally {
    registering.value = false
  }
}

function goToLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="register-page">
    <div class="aurora-wrapper">
      <div class="aurora aurora-1" />
      <div class="aurora aurora-2" />
      <div class="aurora aurora-3" />
      <div class="aurora aurora-4" />
    </div>
    <div class="register-card">
      <div class="register-header">
        <img src="/favicon.ico" alt="logo" class="register-logo" />
        <h2>创建账号</h2>
        <p>注册加入智能在线教学平台</p>
      </div>

      <!-- 步骤条 -->
      <el-steps :active="step" align-center class="steps">
        <el-step title="填写信息" />
        <el-step title="注册完成" />
      </el-steps>

      <!-- Step 0: 注册表单 -->
      <el-form
        v-if="step === 0"
        ref="registerFormRef"
        :model="registerForm"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleRegister"
      >
        <!-- 角色选择 -->
        <el-form-item label="注册身份" prop="role">
          <el-radio-group v-model="registerForm.role" class="role-selector">
            <el-radio-button v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-row :gutter="16">
          <!-- 账号 -->
          <el-col :span="12">
            <el-form-item label="账号" prop="account">
              <el-input
                v-model="registerForm.account"
                placeholder="学号/工号"
                prefix-icon="User"
              />
            </el-form-item>
          </el-col>
          <!-- 姓名 -->
          <el-col :span="12">
            <el-form-item label="姓名" prop="userName">
              <el-input
                v-model="registerForm.userName"
                placeholder="请输入真实姓名"
                prefix-icon="UserFilled"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <!-- 密码 -->
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="6-20位密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-col>
          <!-- 确认密码 -->
          <el-col :span="12">
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <!-- 邮箱 -->
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱地址"
                prefix-icon="Message"
              />
            </el-form-item>
          </el-col>
          <!-- 手机号 -->
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input
                v-model="registerForm.phone"
                placeholder="请输入手机号"
                prefix-icon="Phone"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 学生专属字段 -->
        <template v-if="registerForm.role === 'STUDENT'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="院系" prop="department">
                <el-select v-model="registerForm.department" placeholder="请选择院系" style="width: 100%">
                  <el-option v-for="dept in departmentOptions" :key="dept" :label="dept" :value="dept" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="班级" prop="className">
                <el-select v-model="registerForm.className" placeholder="请选择班级" style="width: 100%">
                  <el-option v-for="cls in classOptions" :key="cls" :label="cls" :value="cls" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- 教师专属字段 -->
        <template v-if="registerForm.role === 'TEACHER'">
          <el-form-item label="所属院系" prop="department">
            <el-select v-model="registerForm.department" placeholder="请选择院系" style="width: 100%">
              <el-option v-for="dept in departmentOptions" :key="dept" :label="dept" :value="dept" />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item>
          <el-button type="primary" :loading="registering" class="register-btn" @click="handleRegister">
            {{ registering ? '注册中...' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Step 1: 注册成功 -->
      <div v-if="step === 1" class="success-area">
        <el-icon class="success-icon"><CircleCheckFilled /></el-icon>
        <p class="success-title">注册成功！</p>
        <p class="success-desc">
          您的账号已创建，请等待管理员审核后即可登录使用。
        </p>
        <el-alert
          title="提示"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 20px"
        >
          <template #default>
            <p>账号：<strong>{{ registerForm.account }}</strong></p>
            <p>角色：<strong>{{ registerForm.role === 'STUDENT' ? '学生' : '教师' }}</strong></p>
          </template>
        </el-alert>
        <el-button type="primary" class="register-btn" @click="goToLogin">返回登录</el-button>
      </div>

      <div class="register-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
    </div>

    <div class="register-copyright">
      Copyright &copy; 2026 成都理工大学软工五组. All Rights Reserved.
    </div>
  </div>
</template>

<style scoped>
/* ========== 背景层：极光 Aurora Gradient ========== */
.register-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 20px;
  background: linear-gradient(135deg, #ffffff 0%, #e9f7ef 45%, #d7f0e3 100%);
}

/* 左侧装饰光效（保留但弱化） */
.aurora-wrapper {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  opacity: 0.6;
}

.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(110px);
  mix-blend-mode: screen;
}

.aurora-1 {
  width: 600px;
  height: 500px;
  background: radial-gradient(
    ellipse at center,
    rgba(31, 111, 74, 0.35) 0%,
    rgba(143, 211, 168, 0.25) 40%,
    rgba(255, 255, 255, 0) 70%
  );
  top: -10%;
  left: -10%;
}

.aurora-2 {
  width: 500px;
  height: 450px;
  background: radial-gradient(
    ellipse at center,
    rgba(143, 211, 168, 0.35) 0%,
    rgba(255, 255, 255, 0) 70%
  );
  bottom: -10%;
  left: 10%;
}

/* ========== 注册卡片（右侧浮动） ========== */
.register-card {
  width: 560px;
  max-width: 100%;
  position: relative;
  z-index: 1;

  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);

  border-radius: 20px;
  padding: 40px;

  border: 1px solid rgba(31, 111, 74, 0.15);
  box-shadow: 0 10px 40px rgba(31, 111, 74, 0.12);
}

/* ========== 标题 ========== */
.register-header {
  text-align: center;
  margin-bottom: 24px;
}

.register-header h2 {
  font-size: 22px;
  color: #1f6f4a; /* 墨绿 */
  margin-bottom: 4px;
  letter-spacing: 1px;
}

.register-header p {
  font-size: 13px;
  color: #5f7f6f;
}

/* ========== 步骤条 ========== */
.steps {
  margin-bottom: 30px;
}

:deep(.el-step__title) {
  color: #8aa095 !important;
}

:deep(.el-step__title.is-process) {
  color: #1f6f4a !important;
}

/* ========== 角色按钮 ========== */
.role-selector {
  width: 100%;
  display: flex;
}

.role-selector :deep(.el-radio-button) {
  flex: 1;
}

.role-selector :deep(.el-radio-button__inner) {
   width: 100%;
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(31, 111, 74, 0.2);
  color: #4f6b5b;
}

.role-selector :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: linear-gradient(135deg, #1f6f4a, #8fd3a8);
  border-color: #1f6f4a;
  color: #fff;
}

/* ========== 输入框（毛玻璃绿主题） ========== */
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(31, 111, 74, 0.2);
  box-shadow: none;
  border-radius: 10px;
}

:deep(.el-input__wrapper:hover) {
  border-color: rgba(31, 111, 74, 0.4);
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #1f6f4a;
  box-shadow: 0 0 0 3px rgba(31, 111, 74, 0.12);
}

:deep(.el-input__inner) {
  color: #1f2a24;
}

:deep(.el-input__inner::placeholder) {
  color: #9bb3a5;
}

/* ========== 下拉框 ========== */
:deep(.el-select .el-input__wrapper) {
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(31, 111, 74, 0.2);
}

/* ========== 按钮（重点修改） ========== */
.register-btn {
  width: 100%;
  margin-top: 8px;
  height: 44px;

  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;

  color: #fff;
  letter-spacing: 2px;

  background: linear-gradient(135deg, #1f6f4a, #8fd3a8);
  border: none;

  box-shadow: 0 6px 18px rgba(31, 111, 74, 0.25);
  transition: all 0.3s;
}

.register-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(31, 111, 74, 0.35);
  background: linear-gradient(135deg, #165a3b, #7fcf9d);
}

/* ========== 底部链接 ========== */
.register-footer {
  text-align: center;
  font-size: 13px;
  margin-top: 16px;
}

.register-footer a {
  color: #1f6f4a;
}

.register-footer a:hover {
  color: #165a3b;
}

/* ========== 成功页面 ========== */
.success-icon {
  font-size: 64px;
  color: #1f6f4a;
}

.success-title {
  color: #1f2a24;
}

.success-desc {
  color: #5f7f6f;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .register-page {
    justify-content: center;
    padding: 20px;
  }

  .register-card {
    width: 100%;
  }
}
</style>