<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loggingIn = ref(false)

const loginForm = reactive({
  account: '',
  password: '',
  role: 'STUDENT',
})

const roleOptions = [
  { label: '学生', value: 'STUDENT' },
  { label: '教师', value: 'TEACHER' },
  { label: '管理员', value: 'ADMIN' },
]

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

async function handleLogin() {
  // 防止重复点击
  if (loggingIn.value) return

  // 手动校验（避免 Element Plus validate 在部分版本的行为差异）
  if (!loginForm.account) { ElMessage.warning('请输入账号'); return }
  if (!loginForm.password) { ElMessage.warning('请输入密码'); return }
  if (loginForm.password.length < 6) { ElMessage.warning('密码至少6位'); return }

  loggingIn.value = true
  try {
    const user = await userStore.login({ ...loginForm })
    ElMessage.success(`欢迎回来，${user.userName}！`)

    const roleCode = user.roleCode || user.role
    const roleRedirect = {
      STUDENT: '/dashboard',
      TEACHER: '/teacher/dashboard',
      ADMIN: '/admin/dashboard',
    }
    const redirect = route.query.redirect || roleRedirect[roleCode] || '/dashboard'
    router.push(redirect)
  } catch (e) {
    ElMessage.error(e?.message || '登录失败，请检查账号密码')
  } finally {
    loggingIn.value = false
  }
}
</script>

<template>
  <!-- 根容器 class 统一为 login-page，样式完全对应 -->
  <div class="login-page">
    <!-- 背景蒙版层 -->
    <div class="bg-mask"></div>
    <!-- 极光背景层 -->
    <div class="aurora-wrapper">
      <div class="aurora aurora-1" />
      <div class="aurora aurora-2" />
      <div class="aurora aurora-3" />
      <div class="aurora aurora-4" />
    </div>
    <div class="login-card">
      <div class="login-header">
        <img src="/favicon.ico" alt="logo" class="login-logo" />
        <h2>智慧在线教学平台</h2>
        <p>Intelligent Online Teaching Platform</p>
      </div>

      <el-form ref="loginFormRef" :model="loginForm" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="role">
          <el-radio-group v-model="loginForm.role" class="role-selector">
            <el-radio-button v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item prop="account">
          <el-input v-model="loginForm.account" placeholder="请输入学号/工号/管理员账号" prefix-icon="User" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loggingIn" class="login-btn" @click="handleLogin">
            {{ loggingIn ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <router-link to="/forgot-password">忘记密码？</router-link>
        <span class="footer-divider">|</span>
        <router-link to="/register">注册账号</router-link>
      </div>
    </div>

    <div class="login-copyright">
      Copyright &copy; 2026 成都理工大学软工五组. All Rights Reserved.
    </div>
  </div>
</template>

<style scoped>
/* ========== 自定义自适应背景【修改区域】 ========== */
.login-page {
  min-height: 100vh;
  width: 100vw;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  /* 自定义背景图 自适应核心配置 */
  background-image: url("@/assets/images/login-bg.jpg");
  background-repeat: no-repeat;
  background-size: cover;
  background-position: center center;
}
/* 半透明黑色蒙版，弱化背景，提升文字可读性 */
.bg-mask {
  position: absolute;
  inset: 0;
  z-index: 0;
  background-color: rgba(0, 0, 0, 0.32);
}

/* ========== 极光（绿色柔光版） ========== */
.aurora-wrapper {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
}

.aurora {
  position: absolute;
  border-radius: 50%;
  filter: blur(110px);
  opacity: 0.55;
  mix-blend-mode: screen;
}

/* 主墨绿光 */
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

/* 浅绿光 */
.aurora-2 {
  width: 520px;
  height: 440px;
  background: radial-gradient(
    ellipse at center,
    rgba(143, 211, 168, 0.35) 0%,
    rgba(255, 255, 255, 0) 70%
  );
  top: -20%;
  right: -8%;
}

/* 底部柔光 */
.aurora-3 {
  width: 480px;
  height: 380px;
  background: radial-gradient(
    ellipse at center,
    rgba(31, 111, 74, 0.25) 0%,
    rgba(143, 211, 168, 0.2) 40%,
    rgba(255, 255, 255, 0) 70%
  );
  bottom: -18%;
  left: 25%;
}

/* 中心光 */
.aurora-4 {
  width: 420px;
  height: 360px;
  background: radial-gradient(
    ellipse at center,
    rgba(31, 111, 74, 0.3) 0%,
    rgba(255, 255, 255, 0) 70%
  );
  top: 30%;
  left: 40%;
}

/* ========== 登录卡片（白色毛玻璃） ========== */
.login-card {
  width: 420px;
  position: relative;
  z-index: 2;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: 20px;
  padding: 44px 40px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-logo {
  width: 56px;
  height: 56px;
  margin-bottom: 14px;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.3));
}
.login-header h2 {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 6px;
  letter-spacing: 1px;
}
.login-header p {
  font-size: 12px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
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
  width:100%;
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(31, 111, 74, 0.2);
  color: #4f6b5b;
}

.role-selector :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: linear-gradient(135deg, #1f6f4a, #8fd3a8);
  border-color: #1f6f4a;
  color: #fff;
}

/* ========== 输入框（统一注册页风格） ========== */
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

/* ========== 登录按钮（统一注册页） ========== */
.login-btn {
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

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 25px rgba(31, 111, 74, 0.35);
  background: linear-gradient(135deg, #165a3b, #7fcf9d);
}

/* ========== 底部链接 ========== */
.login-footer {
  text-align: center;
  font-size: 13px;
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 6px;
}

.login-footer a {
  color: #ffffff;
}

.login-footer a:hover {
  color: #d0f3de;
}

.footer-divider {
  color: #ffffff;
}

/* ========== 版权 ========== */
.login-copyright {
  margin-top: 32px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
  position: relative;
  z-index: 2;
}

/* ========== 移动端自适应 ========== */
@media (max-width: 768px) {
  .login-card {
    width: 100%;
    margin: 0 20px;
    padding: 32px 24px;
  }
  .aurora {
    display: none;
  }
}
</style>
