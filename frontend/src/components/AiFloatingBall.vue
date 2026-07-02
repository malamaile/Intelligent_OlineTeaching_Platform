<script setup>
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { chatStream as chatStreamAPI, uploadFile, getHistory, clearHistory } from '@/api/ai'

const route = useRoute()
const userStore = useUserStore()

// ---- 悬浮球拖拽位置 ----
const savedPos = (() => {
  try { return JSON.parse(localStorage.getItem('ai-ball-pos')) } catch { return null }
})()
const ballX = ref(savedPos?.x ?? 0)
const ballY = ref(savedPos?.y ?? 0)
const dragging = ref(false)
let dragStartX = 0, dragStartY = 0, dragOrigX = 0, dragOrigY = 0, hasMoved = false

function onBallMouseDown(e) {
  if (e.button !== 0) return // 只响应左键
  dragStartX = e.clientX
  dragStartY = e.clientY
  dragOrigX = ballX.value
  dragOrigY = ballY.value
  hasMoved = false
  document.addEventListener('mousemove', onBallMouseMove)
  document.addEventListener('mouseup', onBallMouseUp)
}

function onBallMouseMove(e) {
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (!hasMoved && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
    hasMoved = true
    dragging.value = true
  }
  if (hasMoved) {
    ballX.value = dragOrigX + dx
    ballY.value = dragOrigY + dy
  }
}

function onBallMouseUp() {
  document.removeEventListener('mousemove', onBallMouseMove)
  document.removeEventListener('mouseup', onBallMouseUp)
  if (hasMoved) {
    setTimeout(() => { dragging.value = false }, 0)
    localStorage.setItem('ai-ball-pos', JSON.stringify({ x: ballX.value, y: ballY.value }))
  } else {
    toggle()
  }
}

// 触摸事件
function onBallTouchStart(e) {
  const t = e.touches[0]
  dragStartX = t.clientX
  dragStartY = t.clientY
  dragOrigX = ballX.value
  dragOrigY = ballY.value
  hasMoved = false
  document.addEventListener('touchmove', onBallTouchMove, { passive: false })
  document.addEventListener('touchend', onBallTouchEnd)
}

function onBallTouchMove(e) {
  const t = e.touches[0]
  const dx = t.clientX - dragStartX
  const dy = t.clientY - dragStartY
  if (!hasMoved && (Math.abs(dx) > 3 || Math.abs(dy) > 3)) {
    hasMoved = true
    dragging.value = true
  }
  if (hasMoved) {
    e.preventDefault()
    ballX.value = dragOrigX + dx
    ballY.value = dragOrigY + dy
  }
}

function onBallTouchEnd() {
  document.removeEventListener('touchmove', onBallTouchMove)
  document.removeEventListener('touchend', onBallTouchEnd)
  if (hasMoved) {
    setTimeout(() => { dragging.value = false }, 0)
    localStorage.setItem('ai-ball-pos', JSON.stringify({ x: ballX.value, y: ballY.value }))
  } else {
    toggle()
  }
}

// ---- 状态 ----
const visible = ref(false)
const input = ref('')
const sending = ref(false)
const messages = ref([])
const chatBodyRef = ref(null)
const fileInputRef = ref(null)
const uploading = ref(false)
let abortController = null

// ---- 快捷指令 ----
const slashCommands = [
  { cmd: '/总结', label: '总结', prompt: '帮我总结一下', icon: '📝' },
  { cmd: '/解释', label: '解释', prompt: '请用通俗易懂的方式解释：', icon: '💡' },
  { cmd: '/出题', label: '出题', prompt: '请根据以下内容出几道练习题：', icon: '✏' },
  { cmd: '/步骤', label: '步骤', prompt: '请分步骤详细说明：', icon: '📋' },
  { cmd: '/对比', label: '对比', prompt: '请帮我对比分析：', icon: '⚖' },
]

// ---- 页面上下文感知 ----
const pageContext = computed(() => {
  const path = route.path
  const name = route.meta?.title || ''
  const ctx = { roleName: roleLabel.value }

  if (path.startsWith('/courses/') && path.split('/').length > 2) {
    ctx.contextType = 'COURSE'
    ctx.contextId = path.split('/')[2]
    ctx.contextName = name || '课程详情'
  } else if (path === '/courses') {
    ctx.contextType = 'COURSE'
    ctx.contextName = '课程列表'
  } else if (path.startsWith('/tasks')) {
    ctx.contextType = 'TASK'
    ctx.contextName = name || '实验实训'
  } else if (path === '/resources') {
    ctx.contextType = 'RESOURCE'
    ctx.contextName = name || '教学资源库'
  } else if (path === '/analytics') {
    ctx.contextType = 'ANALYTICS'
    ctx.contextName = name || '学情诊断'
  } else if (path === '/dashboard') {
    ctx.contextType = 'DASHBOARD'
    ctx.contextName = '首页'
  }
  return ctx
})

const roleLabel = computed(() => {
  const m = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员' }
  return m[userStore.role] || '用户'
})

// ---- 动态快捷提问 ----
const quickPrompts = computed(() => {
  const ctx = pageContext.value.contextType
  if (ctx === 'COURSE') return [
    '帮我总结这节课的主要内容',
    '这个知识点我不太理解，能解释一下吗？',
    '给我出几道相关的练习题',
    '这个课程的重点是什么？',
  ]
  if (ctx === 'TASK') return [
    '这个实验的步骤是什么？',
    '我在实验中遇到了问题，帮我看看',
    '实验的原理能讲解一下吗？',
    '怎么写实验报告？',
  ]
  if (ctx === 'RESOURCE') return [
    '帮我推荐相关的学习资源',
    '这篇文章的要点是什么？',
    '有没有更进阶的资料推荐？',
  ]
  if (ctx === 'ANALYTICS') return [
    '根据我的学习数据，给我一些建议',
    '我应该在哪些方面加强学习？',
    '如何提高学习效率？',
  ]
  // default
  return [
    '今天我应该学什么？',
    '帮我制定一个学习计划',
    '如何提高编程能力？',
    '有什么好的学习方法推荐？',
  ]
})

// ---- 加载历史 ----
onMounted(async () => {
  try {
    const res = await getHistory(50)
    if (res.data && res.data.length > 0) {
      messages.value = res.data.map((m) => ({
        role: m.role,
        content: m.content,
        contextType: m.contextType,
        contextId: m.contextId,
      }))
    }
  } catch (e) { /* ignore */ }
})

// ---- 逐字输出 ----
let charTimer = null

function typewriterOutput(aiMsg, callback) {
  // 缓冲队列
  const queue = []
  let typing = false

  const pump = () => {
    if (typing || queue.length === 0) return
    typing = true
    const char = queue.shift()
    aiMsg.content += char
    scrollToBottom()
    charTimer = setTimeout(() => {
      typing = false
      pump()
    }, 30) // 每字 30ms
  }

  return {
    push(chunk) {
      // 将每个字符加入队列
      for (const ch of chunk) {
        queue.push(ch)
      }
      pump()
    },
    flush(callback) {
      // 等队列清空后回调
      const check = () => {
        if (queue.length > 0) {
          setTimeout(check, 50)
        } else {
          callback()
        }
      }
      check()
    },
    stop() {
      clearTimeout(charTimer)
      queue.length = 0
      typing = false
    },
  }
}

// ---- 停止生成 ----
function stopGeneration() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  // 清理打字机
  if (charTimer) clearTimeout(charTimer)
  sending.value = false
}

// ---- 发送消息 ----
async function sendMessage(text, extraContext) {
  const content = (text || input.value).trim()
  if (!content || sending.value) return

  input.value = ''
  sending.value = true

  messages.value.push({ role: 'user', content })
  await scrollToBottom()

  const aiMsg = { role: 'assistant', content: '' }
  messages.value.push(aiMsg)

  // 合并页面上下文 + 文件上下文
  const ctx = {
    contextType: pageContext.value.contextType,
    contextName: pageContext.value.contextName,
    contextId: pageContext.value.contextId,
    contextDetail: '',
    roleName: roleLabel.value,
  }
  if (extraContext) Object.assign(ctx, extraContext)

  // 创建 AbortController
  abortController = new AbortController()
  const tw = typewriterOutput(aiMsg)

  chatStreamAPI(content, ctx, {
    signal: abortController.signal,
    onChunk(chunk) {
      tw.push(chunk)
    },
    onDone() {
      tw.flush(() => {
        sending.value = false
        abortController = null
        if (!aiMsg.content) aiMsg.content = '抱歉，我没有理解你的问题。'
      })
    },
    onError(err) {
      tw.stop()
      sending.value = false
      abortController = null
      aiMsg.content = err || 'AI 服务暂不可用'
    },
  })
}

function quickAsk(prompt) {
  if (!visible.value) visible.value = true
  nextTick(() => sendMessage(prompt))
}

async function scrollToBottom() {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

function toggle() {
  visible.value = !visible.value
  if (visible.value) nextTick(() => scrollToBottom())
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// ---- 文件上传 ----
function triggerUpload() {
  fileInputRef.value?.click()
}

async function handleFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadFile(file)
    const data = res.data
    // 把文件内容作为上下文追加到消息
    const fileInfo = `[上传了文件: ${data.fileName} (${(data.fileSize / 1024).toFixed(1)}KB)]`
    messages.value.push({ role: 'user', content: fileInfo, isFile: true })
    // 将文件内容传给 AI 分析
    sendMessage(`请帮我分析这个文件的内容：${data.fileName}`, {
      fileContent: data.fileContent,
      fileName: data.fileName,
    })
  } catch {
    messages.value.push({ role: 'assistant', content: '文件上传失败，请重试' })
  } finally {
    uploading.value = false
    // 重置 input 以便重新选择同一文件
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

// ---- 格式化 AI 回复 ----
function formatMessage(text) {
  if (!text) return ''
  // 1. 转义 HTML
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // 2. 代码块（在换行处理之前）
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
  // 3. 行内代码
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  // 4. 粗体
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  // 5. 换行
  html = html.replace(/\n/g, '<br>')
  return html
}

// ---- 清空对话 ----
async function handleClearHistory() {
  messages.value = []
  try { await clearHistory() } catch {}
}
</script>

<template>
  <div
    class="ai-floating-wrapper"
    :style="{ transform: `translate(${ballX}px, ${ballY}px)` }"
  >
    <!-- 悬浮球 -->
    <div
      class="ai-ball"
      :class="{ active: visible, dragging: dragging }"
      @mousedown="onBallMouseDown"
      @touchstart.prevent="onBallTouchStart"
    >
      <model-viewer
        v-if="!visible"
        src="/ai-avatar.glb"
        autoplay
        camera-orbit="90deg 75deg 105%"
        interaction-prompt="none"
        disable-zoom
        class="ai-ball-model"
      />
      <span v-else class="ai-ball-close">✕</span>
    </div>

    <!-- AI 对话面板 -->
    <transition name="slide-up">
      <div v-if="visible" class="ai-panel">
        <!-- 头部 -->
        <div class="ai-panel-header">
          <div class="ai-header-left">
            <model-viewer
              src="/ai-avatar.glb"
              autoplay
              camera-orbit="90deg 75deg 105%"
              interaction-prompt="none"
              disable-zoom
              class="ai-header-model"
            />
            <span class="ai-header-title">AI 学习助手</span>
          </div>
          <div class="ai-header-right">
            <span class="ai-header-badge">{{ pageContext.contextName || '通用' }}</span>
            <span class="ai-header-badge deepseek">DeepSeek</span>
            <span class="ai-clear-btn" title="清空对话" @click="handleClearHistory">🗑</span>
          </div>
        </div>

        <!-- 快捷提问 -->
        <div v-if="messages.length === 0" class="ai-quick-prompts">
          <div class="ai-quick-title">
            💡 {{ pageContext.contextType ? '在当前页面你可以问我：' : '试试问我：' }}
          </div>
          <div
            v-for="(prompt, idx) in quickPrompts"
            :key="idx"
            class="ai-quick-item"
            @click="quickAsk(prompt)"
          >
            {{ prompt }}
          </div>
        </div>

        <!-- 对话列表 -->
        <div ref="chatBodyRef" class="ai-chat-body">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="ai-msg"
            :class="msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-assistant'"
          >
            <div class="ai-msg-avatar">
              <model-viewer
                v-if="msg.role !== 'user'"
                src="/ai-avatar.glb"
                autoplay
                camera-orbit="90deg 75deg 105%"
                interaction-prompt="none"
                disable-zoom
                class="ai-msg-model"
              />
              <img
                v-else
                :src="userStore.userInfo?.avatar || '/favicon.ico'"
                class="ai-msg-user-avatar"
                @error="(e) => { e.target.src = '/favicon.ico' }"
              />
            </div>
            <div class="ai-msg-bubble">
              <span v-if="msg.isFile" class="ai-file-tag">📎 {{ msg.content }}</span>
              <span
                v-else-if="msg.role === 'assistant'"
                v-html="formatMessage(msg.content)"
              />
              <template v-else>{{ msg.content }}</template>
              <span
                v-if="sending && idx === messages.length - 1 && msg.role === 'assistant'"
                class="ai-cursor"
              >|</span>
            </div>
          </div>
          <div v-if="messages.length === 0 && !sending" class="ai-empty">
            <span class="ai-empty-icon">👋</span>
            <p>你好！我是 AI 学习助手</p>
            <p class="ai-empty-sub">我会根据你当前浏览的页面，提供针对性的帮助</p>
          </div>
        </div>

        <!-- 快捷指令按钮行 -->
        <div class="ai-cmd-row">
          <button
            v-for="cmd in slashCommands"
            :key="cmd.cmd"
            class="ai-cmd-btn"
            :disabled="sending"
            @click="quickAsk(cmd.prompt)"
          >
            {{ cmd.icon }} {{ cmd.label }}
          </button>
        </div>

        <!-- 输入区 -->
        <div class="ai-input-area">
          <input
            ref="fileInputRef"
            type="file"
            class="ai-file-input-hidden"
            accept=".txt,.md,.java,.py,.js,.ts,.json,.xml,.csv,.sql,.html,.css,.yaml,.yml,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png,.gif,.bmp,.webp"
            @change="handleFileChange"
          />
          <button class="ai-upload-btn" title="上传文件分析" :disabled="uploading || sending" @click="triggerUpload">
            {{ uploading ? '⏳' : '📎' }}
          </button>
          <input
            v-model="input"
            class="ai-input"
            placeholder="输入你的问题..."
            :disabled="sending"
            @keydown="handleKeydown"
          />
          <!-- 停止按钮 -->
          <button v-if="sending" class="ai-stop-btn" title="停止生成" @click="stopGeneration">
            ⏹
          </button>
          <!-- 发送按钮 -->
          <button v-else class="ai-send-btn" :disabled="!input.trim()" @click="sendMessage()">
            发送
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
/* ========== 悬浮球容器（可自由拖拽） ========== */
.ai-floating-wrapper {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.ai-ball {
  width: 96px;
  height: 96px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  user-select: none;
  flex-shrink: 0;
  transition: transform 0.3s ease;
}

.ai-ball:hover {
  transform: scale(1.08);
}

.ai-ball.dragging {
  cursor: grabbing;
  transform: scale(1.12);
  transition: none;
}

/* 3D 模型 — 悬浮球 */
.ai-ball-model {
  width: 96px;
  height: 96px;
  overflow: hidden;
  pointer-events: none;
}

/* 3D 模型 — 面板头部 */
.ai-header-model {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

/* 3D 模型 — 消息头像 */
.ai-msg-model {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  pointer-events: none;
}

/* 用户头像 — 消息头像 */
.ai-msg-user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.ai-ball-close { font-size: 28px; color: #1b2b22; font-weight: 600; }

/* ========== 对话面板 ========== */
.ai-panel {
  width: 400px;
  height: 560px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(31, 111, 74, 0.15);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(31, 111, 74, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  margin-bottom: 12px;
}

/* 头部 */
.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(31, 111, 74, 0.06), rgba(143, 211, 168, 0.1));
  border-bottom: 1px solid rgba(31, 111, 74, 0.1);
  flex-shrink: 0;
}

.ai-header-left { display: flex; align-items: center; gap: 8px; }
.ai-header-title { font-size: 15px; font-weight: 600; color: #1b2b22; }

.ai-header-right { display: flex; align-items: center; gap: 6px; }

.ai-header-badge {
  font-size: 11px;
  color: #1f6f4a;
  background: rgba(31, 111, 74, 0.1);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.ai-header-badge.deepseek {
  color: #4f6b5b;
  background: rgba(79, 107, 91, 0.08);
}

.ai-clear-btn {
  font-size: 14px;
  cursor: pointer;
  padding: 2px 4px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.ai-clear-btn:hover { opacity: 1; }

/* 快捷提问 */
.ai-quick-prompts {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(31, 111, 74, 0.08);
  flex-shrink: 0;
}

.ai-quick-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.ai-quick-item {
  display: inline-block;
  font-size: 12px;
  color: #1f6f4a;
  background: rgba(31, 111, 74, 0.06);
  border: 1px solid rgba(31, 111, 74, 0.12);
  border-radius: 14px;
  padding: 5px 12px;
  margin: 3px 6px 3px 0;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.ai-quick-item:hover {
  background: rgba(31, 111, 74, 0.12);
  border-color: rgba(31, 111, 74, 0.3);
}

/* 聊天区域 */
.ai-chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-msg { display: flex; gap: 8px; max-width: 100%; align-items: flex-start; }
.ai-msg-user { flex-direction: row-reverse; }
.ai-msg-avatar { flex-shrink: 0; display: flex; align-items: center; justify-content: center; }

.ai-msg-bubble {
  font-size: 13px;
  line-height: 1.6;
  padding: 10px 14px;
  border-radius: 14px;
  max-width: 82%;
  word-break: break-word;
  white-space: pre-wrap;
}

.ai-msg-user .ai-msg-bubble {
  background: linear-gradient(135deg, #1f6f4a, #3a8f65);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-msg-assistant .ai-msg-bubble {
  background: #f5f7fa;
  color: #303133;
  border-bottom-left-radius: 4px;
}

.ai-file-tag {
  color: #409eff;
  font-size: 12px;
}

/* 格式化内容 */
.ai-msg-bubble :deep(code) {
  background: rgba(31, 111, 74, 0.1);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
}

.ai-msg-bubble :deep(pre) {
  background: rgba(31, 111, 74, 0.06);
  padding: 8px 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 6px 0;
}

.ai-msg-bubble :deep(pre code) {
  background: none;
  padding: 0;
}

.ai-msg-bubble :deep(strong) {
  font-weight: 600;
}

.ai-cursor { animation: blink 0.8s infinite; font-weight: 100; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

/* 空状态 */
.ai-empty {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
}
.ai-empty-icon { font-size: 36px; display: block; margin-bottom: 12px; }
.ai-empty p { font-size: 13px; line-height: 1.5; margin: 0; }
.ai-empty-sub { color: #c0c4cc; font-size: 12px !important; margin-top: 4px !important; }

/* 输入区 */
.ai-input-area {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  border-top: 1px solid rgba(31, 111, 74, 0.1);
  flex-shrink: 0;
  background: rgba(31, 111, 74, 0.02);
}

.ai-file-input-hidden { display: none; }

.ai-upload-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid rgba(31, 111, 74, 0.18);
  background: rgba(255, 255, 255, 0.8);
  font-size: 15px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.ai-upload-btn:hover { background: rgba(31, 111, 74, 0.08); border-color: #1f6f4a; }
.ai-upload-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.ai-input {
  flex: 1;
  border: 1px solid rgba(31, 111, 74, 0.18);
  border-radius: 20px;
  padding: 7px 14px;
  font-size: 13px;
  outline: none;
  color: #303133;
  background: rgba(255, 255, 255, 0.8);
  transition: border-color 0.2s;
  min-width: 0;
}

.ai-input:focus {
  border-color: #1f6f4a;
  box-shadow: 0 0 0 2px rgba(31, 111, 74, 0.1);
}

.ai-input:disabled { background: #f5f7fa; cursor: not-allowed; }

.ai-send-btn {
  padding: 7px 16px;
  border-radius: 20px;
  border: none;
  background: linear-gradient(135deg, #1f6f4a, #8fd3a8);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  flex-shrink: 0;
}

.ai-send-btn:hover:not(:disabled) { background: linear-gradient(135deg, #165a3b, #7fcf9d); }
.ai-send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 停止按钮 */
.ai-stop-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid #f56c6c;
  background: rgba(245, 108, 108, 0.1);
  color: #f56c6c;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.ai-stop-btn:hover {
  background: #f56c6c;
  color: #fff;
}

/* 快捷指令按钮行 */
.ai-cmd-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 14px;
  border-top: 1px solid rgba(31, 111, 74, 0.08);
  background: rgba(31, 111, 74, 0.02);
  flex-shrink: 0;
}

.ai-cmd-btn {
  padding: 4px 10px;
  border-radius: 12px;
  border: 1px solid rgba(31, 111, 74, 0.15);
  background: rgba(255, 255, 255, 0.8);
  color: #1f6f4a;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.ai-cmd-btn:hover:not(:disabled) {
  background: rgba(31, 111, 74, 0.1);
  border-color: #1f6f4a;
}

.ai-cmd-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 动画 */
.slide-up-enter-active { transition: all 0.3s ease; }
.slide-up-leave-active { transition: all 0.2s ease; }
.slide-up-enter-from, .slide-up-leave-to { opacity: 0; transform: translateY(16px); }

@media (max-width: 480px) {
  .ai-panel { width: calc(100vw - 48px); height: 460px; }
}
</style>
