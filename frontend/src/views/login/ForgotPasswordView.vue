<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { forgotPasswordVerify, forgotPasswordReset } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()

// 步骤：verify -> reset -> success
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
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <div class="forgot-page">
    <div class="aurora-wrapper">
      <div class="aurora aurora-1" />
      <div class="aurora aurora-2" />
      <div class="aurora aurora-3" />
      <div class="aurora aurora-4" />
    </div>
    <div class="forgot-card">
      <div class="forgot-header">
        <h2>忘记密码</h2>
      </div>

      <!-- 步骤指示器 -->
      <el-steps :active="step" align-center class="steps">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
        <el-step title="完成" />
      </el-steps>

      <!-- Step 0: 验证身份 -->
      <el-form v-if="step === 0" :model="verifyForm" size="large" @submit.prevent="handleVerify">
        <el-form-item label="账号">
          <el-input v-model="verifyForm.account" placeholder="请输入学号/工号/管理员账号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="verifyForm.role">
            <el-radio-button v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="预留信息">
          <el-input v-model="verifyForm.reservedInfo" placeholder="请输入姓名/手机号/邮箱" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="verifying" class="full-btn" @click="handleVerify">
            验证身份
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Step 1: 重置密码 -->
      <el-form v-if="step === 1" :model="resetForm" size="large" @submit.prevent="handleReset">
        <el-form-item label="新密码">
          <el-input v-model="resetForm.newPassword" type="password" placeholder="请输入新密码（6-20位）" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="resetForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="resetting" class="full-btn" @click="handleReset">
            重置密码
          </el-button>
        </el-form-item>
      </el-form>

      <!-- Step 2: 完成 -->
      <div v-if="step === 2" class="success-area">
        <el-icon class="success-icon"><CircleCheckFilled /></el-icon>
        <p>密码重置成功！</p>
        <el-button type="primary" class="full-btn" @click="router.push('/login')">返回登录</el-button>
      </div>

      <div class="forgot-footer">
        <router-link to="/login">返回登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========== 背景层：极光 Aurora Gradient ========== */
.forgot-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: #0a0a1a;
}

.aurora-wrapper {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
}

.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.6;
  mix-blend-mode: screen;
}

.aurora-1 {
  width: 560px; height: 460px;
  background: radial-gradient(ellipse at center, rgba(107, 78, 255, 0.85) 0%, rgba(107, 78, 255, 0.35) 40%, rgba(20, 10, 60, 0) 70%);
  top: -15%; left: -10%;
  animation: auroraFlow1 18s ease-in-out infinite;
}

.aurora-2 {
  width: 480px; height: 400px;
  background: radial-gradient(ellipse at center, rgba(163, 216, 255, 0.65) 0%, rgba(107, 78, 255, 0.3) 40%, rgba(30, 5, 60, 0) 75%);
  top: -20%; right: -8%;
  animation: auroraFlow2 20s ease-in-out infinite;
}

.aurora-3 {
  width: 440px; height: 350px;
  background: radial-gradient(ellipse at center, rgba(163, 216, 255, 0.55) 0%, rgba(107, 78, 255, 0.3) 35%, rgba(10, 30, 60, 0) 70%);
  bottom: -18%; left: 25%;
  animation: auroraFlow3 22s ease-in-out infinite;
}

.aurora-4 {
  width: 360px; height: 320px;
  background: radial-gradient(ellipse at center, rgba(107, 78, 255, 0.5) 0%, rgba(80, 40, 160, 0.25) 40%, rgba(15, 5, 40, 0) 70%);
  top: 30%; left: 35%;
  animation: auroraFlow4 25s ease-in-out infinite;
}

@keyframes auroraFlow1 {
  0%, 100% { transform: translate(0, 0) rotate(0deg) scale(1); }
  25%  { transform: translate(60px, 40px) rotate(3deg) scale(1.08); }
  50%  { transform: translate(20px, 80px) rotate(-2deg) scale(0.95); }
  75%  { transform: translate(-30px, 30px) rotate(5deg) scale(1.05); }
}

@keyframes auroraFlow2 {
  0%, 100% { transform: translate(0, 0) rotate(0deg) scale(1); }
  33%  { transform: translate(-50px, 50px) rotate(-4deg) scale(1.1); }
  66%  { transform: translate(30px, -30px) rotate(2deg) scale(0.92); }
}

@keyframes auroraFlow3 {
  0%, 100% { transform: translate(0, 0) rotate(0deg) scale(1); }
  20%  { transform: translate(-40px, -60px) rotate(3deg) scale(1.06); }
  50%  { transform: translate(20px, -30px) rotate(-3deg) scale(0.96); }
  80%  { transform: translate(50px, -80px) rotate(1deg) scale(1.04); }
}

@keyframes auroraFlow4 {
  0%, 100% { transform: translate(0, 0) rotate(0deg) scale(1); }
  30%  { transform: translate(40px, -20px) rotate(6deg) scale(1.12); }
  60%  { transform: translate(-30px, 30px) rotate(-5deg) scale(0.9); }
}

/* ========== 毛玻璃卡片 ========== */
.forgot-card {
  width: 440px;
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 20px;
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.25),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.forgot-header {
  text-align: center;
  margin-bottom: 24px;
}

.forgot-header h2 {
  font-size: 22px;
  color: rgba(255, 255, 255, 0.95);
  letter-spacing: 1px;
}

/* 步骤条 */
:deep(.el-step__title) {
  color: rgba(255, 255, 255, 0.6) !important;
}

:deep(.el-step__title.is-process) {
  color: rgba(255, 255, 255, 0.9) !important;
}

/* 表单 */
:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: none;
  border-radius: 10px;
  transition: all 0.3s;
}

:deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.12);
}

:deep(.el-input__wrapper.is-focus) {
  border-color: rgba(107, 78, 255, 0.7);
  background: rgba(255, 255, 255, 0.14);
  box-shadow: 0 0 0 3px rgba(107, 78, 255, 0.15);
}

:deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.9);
}

:deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.35);
}

:deep(.el-radio-button__inner) {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.8);
}

:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: rgba(107, 78, 255, 0.7);
  border-color: rgba(255, 255, 255, 0.4);
  color: #fff;
}

:deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.75);
}

.steps {
  margin-bottom: 30px;
}

.full-btn {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  background: rgba(107, 78, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(4px);
  transition: all 0.3s;
}

.full-btn:hover {
  background: rgba(107, 78, 255, 0.95);
  box-shadow: 0 4px 20px rgba(107, 78, 255, 0.5);
  transform: translateY(-1px);
}

.forgot-footer {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
}

.forgot-footer a {
  color: rgba(255, 255, 255, 0.6);
  transition: color 0.2s;
}

.forgot-footer a:hover {
  color: rgba(255, 255, 255, 0.9);
}

.success-area {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 56px;
  color: #67c23a;
  margin-bottom: 12px;
}

.success-area p {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 20px;
}
</style>
