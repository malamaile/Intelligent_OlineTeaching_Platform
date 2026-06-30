<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { forgotPasswordVerify, forgotPasswordReset } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()

const step = ref(0)
const verifying = ref(false)
const resetting = ref(false)

const verifyForm = reactive({
  account: '',
  role: 'STUDENT',
  reservedInfo: '',
})

const resetForm = reactive({
  resetToken: '',
  newPassword: '',
  confirmPassword: '',
})

const roleOptions = [
  { label: '学生', value: 'STUDENT' },
  { label: '教师', value: 'TEACHER' },
  { label: '管理员', value: 'ADMIN' },
]

async function handleVerify() {
  if (!verifyForm.account || !verifyForm.reservedInfo) {
    ElMessage.warning('请填写完整信息')
    return
  }
  verifying.value = true
  try {
    const res = await forgotPasswordVerify({ ...verifyForm })
    resetForm.resetToken = res.data.resetToken
    step.value = 1
    ElMessage.success('验证通过，请设置新密码')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    verifying.value = false
  }
}

async function handleReset() {
  if (resetForm.newPassword !== resetForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  if (resetForm.newPassword.length < 6 || resetForm.newPassword.length > 20) {
    ElMessage.warning('密码长度为 6-20 位')
    return
  }
  resetting.value = true
  try {
    await forgotPasswordReset({
      resetToken: resetForm.resetToken,
      newPassword: resetForm.newPassword,
    })
    step.value = 2
    ElMessage.success('密码重置成功')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <div class="forgot-page">
    <!-- 极光背景层（与登录/注册统一：绿色柔光） -->
    <div class="aurora-wrapper">
      <div class="aurora aurora-1" />
      <div class="aurora aurora-2" />
      <div class="aurora aurora-3" />
      <div class="aurora aurora-4" />
    </div>

    <div class="forgot-card">
      <div class="forgot-header">
        <img src="/favicon.ico" alt="logo" class="forgot-logo" />
        <h2>找回密码</h2>
        <p>验证身份，安全重置您的登录密码</p>
      </div>

      <!-- 步骤指示器 -->
      <el-steps :active="step" align-center class="steps">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
        <el-step title="完成" />
      </el-steps>

      <!-- Step 0: 验证身份 -->
      <el-form v-if="step === 0" :model="verifyForm" size="large" @submit.prevent="handleVerify">
        <el-form-item prop="role">
          <el-radio-group v-model="verifyForm.role" class="role-selector">
            <el-radio-button v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-input v-model="verifyForm.account" placeholder="请输入学号/工号/管理员账号" prefix-icon="User" />
        </el-form-item>

        <el-form-item>
          <el-input v-model="verifyForm.reservedInfo" placeholder="请输入姓名/手机号/邮箱" prefix-icon="Key" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="verifying" class="full-btn" @click="handleVerify">
            {{ verifying ? '验证中...' : '验证身份' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Step 1: 重置密码 -->
      <el-form v-if="step === 1" :model="resetForm" size="large" @submit.prevent="handleReset">
        <el-form-item>
          <el-input v-model="resetForm.newPassword" type="password" placeholder="请输入新密码（6-20位）" prefix-icon="Lock" show-password />
        </el-form-item>

        <el-form-item>
          <el-input v-model="resetForm.confirmPassword" type="password" placeholder="请再次输入新密码" prefix-icon="Lock" show-password />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="resetting" class="full-btn" @click="handleReset">
            {{ resetting ? '重置中...' : '重置密码' }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Step 2: 完成 -->
      <div v-if="step === 2" class="success-area">
        <el-icon class="success-icon"><CircleCheckFilled /></el-icon>
        <p class="success-title">密码重置成功！</p>
        <p class="success-desc">请使用新密码重新登录平台</p>
        <el-button type="primary" class="full-btn" @click="router.push('/login')">返回登录</el-button>
      </div>

      <div class="forgot-footer">
        <router-link to="/login">← 返回登录</router-link>
      </div>
    </div>

    <div class="forgot-copyright">
      Copyright &copy; 2026 成都理工大学软工五组. All Rights Reserved.
    </div>
  </div>
</template>

<style scoped>
/* ========== 背景（与登录/注册统一：白 + 浅绿渐变） ========== */
.forgot-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  background: linear-gradient(135deg, #ffffff 0%, #e9f7ef 45%, #d7f0e3 100%);
}

/* ========== 极光（绿色柔光，与登录页一致） ========== */
.aurora-wrapper {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
}

.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(110px);
  opacity: 0.55;
  mix-blend-mode: screen;
}

.aurora-1 {
  width: 600px; height: 500px;
  background: radial-gradient(ellipse at center, rgba(31, 111, 74, 0.35) 0%, rgba(143, 211, 168, 0.25) 40%, rgba(255, 255, 255, 0) 70%);
  top: -10%; left: -10%;
}

.aurora-2 {
  width: 520px; height: 440px;
  background: radial-gradient(ellipse at center, rgba(143, 211, 168, 0.35) 0%, rgba(255, 255, 255, 0) 70%);
  top: -20%; right: -8%;
}

.aurora-3 {
  width: 480px; height: 380px;
  background: radial-gradient(ellipse at center, rgba(31, 111, 74, 0.25) 0%, rgba(143, 211, 168, 0.2) 40%, rgba(255, 255, 255, 0) 70%);
  bottom: -18%; left: 25%;
}

.aurora-4 {
  width: 420px; height: 360px;
  background: radial-gradient(ellipse at center, rgba(31, 111, 74, 0.3) 0%, rgba(255, 255, 255, 0) 70%);
  top: 30%; left: 40%;
}

/* ========== 卡片（白色毛玻璃，与登录页一致） ========== */
.forgot-card {
  width: 440px;
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 20px;
  padding: 44px 40px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

/* ========== 标题 ========== */
.forgot-header {
  text-align: center;
  margin-bottom: 24px;
}

.forgot-logo {
  width: 56px; height: 56px;
  margin-bottom: 14px;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.3));
}

.forgot-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
  margin-bottom: 6px;
  letter-spacing: 1px;
}

.forgot-header p {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  letter-spacing: 0.5px;
}

/* ========== 步骤条 ========== */
.steps {
  margin-bottom: 30px;
}

:deep(.el-step__title) {
  color: rgba(255, 255, 255, 0.55) !important;
}

:deep(.el-step__title.is-process) {
  color: #8fd3a8 !important;
}

:deep(.el-step__title.is-finish) {
  color: rgba(255, 255, 255, 0.7) !important;
}

/* ========== 角色按钮（统一登录/注册页） ========== */
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

/* ========== 输入框（统一登录/注册页风格） ========== */
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(31, 111, 74, 0.2);
  border-radius: 10px;
  box-shadow: none;
  transition: all 0.3s;
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

/* ========== 按钮（统一绿色渐变） ========== */
.full-btn {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #fff;
  background: linear-gradient(135deg, #1f6f4a, #8fd3a8);
  border: none;
  box-shadow: 0 6px 18px rgba(31, 111, 74, 0.25);
  transition: all 0.3s;
}

.full-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(31, 111, 74, 0.35);
  background: linear-gradient(135deg, #165a3b, #7fcf9d);
}

/* ========== 成功区域 ========== */
.success-area {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 64px;
  color: #67c23a;
  margin-bottom: 16px;
}

.success-title {
  font-size: 18px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.95);
  margin-bottom: 8px;
}

.success-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.55);
  margin-bottom: 24px;
}

/* ========== 底部链接 ========== */
.forgot-footer {
  text-align: center;
  font-size: 13px;
  margin-top: 16px;
}

.forgot-footer a {
  color: rgba(255, 255, 255, 0.65);
  transition: color 0.2s;
}

.forgot-footer a:hover {
  color: #8fd3a8;
}

/* ========== 版权 ========== */
.forgot-copyright {
  margin-top: 32px;
  font-size: 12px;
  color: rgba(31, 111, 74, 0.35);
  position: relative;
  z-index: 1;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .forgot-card {
    width: 100%;
    margin: 0 20px;
  }
}
</style>
