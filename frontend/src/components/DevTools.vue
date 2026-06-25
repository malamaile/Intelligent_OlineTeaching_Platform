<script setup>
import { ref } from 'vue'
import { mockUsers } from '@/api/mock'

const DEV = import.meta.env.VITE_DEV_BYPASS_AUTH === 'true'
const visible = ref(false)

const roles = [
  { key: 'STUDENT', label: '学生', color: '#67a9eb' },
  { key: 'TEACHER', label: '教师', color: '#5EEAD4' },
  { key: 'ADMIN', label: '管理员', color: '#F28C28' },
]

function switchRole(roleKey) {
  const user = mockUsers[roleKey]
  localStorage.setItem('dev_role', roleKey)
  localStorage.setItem('token', 'dev-mock-token')
  localStorage.setItem('userInfo', JSON.stringify(user))
  window.location.reload()
}

function toggleVisible() {
  visible.value = !visible.value
}
</script>

<template>
  <div v-if="DEV" class="dev-tools" :class="{ expanded: visible }">
    <div class="dev-toggle" @click="toggleVisible">
      <el-icon :size="sof18"><Setting /></el-icon>
      <span v-if="visible">DEV</span>
    </div>
    <div v-if="visible" class="dev-panel">
      <div class="dev-title">切换角色预览</div>
      <div
        v-for="role in roles"
        :key="role.key"
        class="dev-role-btn"
        :style="{ borderColor: role.color, color: role.color }"
        @click="switchRole(role.key)"
      >
        {{ role.label }}端
      </div>
      <el-divider style="margin: 8px 0" />
      <div class="dev-hint">切换后自动刷新页面</div>
    </div>
  </div>
</template>

<style scoped>
.dev-tools {
  position: fixed;
  bottom: 20px;
  right: 20px;
  z-index: 9999;
}

.dev-toggle {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(107, 78, 255, 0.7);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(107, 78, 255, 0.4);
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  transition: all 0.2s;
  margin-left: auto;
  backdrop-filter: blur(8px);
}

.dev-toggle:hover {
  background: #6B4EFF;
  box-shadow: 0 6px 24px rgba(107, 78, 255, 0.6);
  transform: scale(1.05);
}

.expanded .dev-toggle {
  width: 56px;
  border-radius: 28px 28px 4px 4px;
}

.dev-panel {
  background: rgba(20, 18, 40, 0.92);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px 0 8px 8px;
  padding: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
  margin-top: 0;
  min-width: 140px;
}

.dev-title {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  margin-bottom: 10px;
}

.dev-role-btn {
  padding: 6px 12px;
  border: 1px solid;
  border-radius: 6px;
  margin-bottom: 6px;
  cursor: pointer;
  text-align: center;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.15s;
}

.dev-role-btn:hover {
  opacity: 0.8;
  transform: scale(1.02);
}

.dev-hint {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.3);
  text-align: center;
}
</style>
