<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherNotices, createNotice, deleteNotice, getTeacherMessages } from '@/api/teacher'

const loading = ref(false)

const notices = ref([])
const messages = ref([])

const msgTypeConfig = {
  AUDIT_RESULT: { label: '审批结果', type: 'success' },
  WARNING: { label: '学情预警', type: 'danger' },
  ANNOUNCEMENT: { label: '系统公告', type: 'info' },
}

async function fetchNotices() {
  try {
    const res = await getTeacherNotices({ page: 1, pageSize: 100 })
    notices.value = res.data.records || res.data
  } catch {}
}

async function fetchMessages() {
  try {
    const res = await getTeacherMessages({ page: 1, pageSize: 20 })
    messages.value = res.data.records || res.data
  } catch {}
}

// 发布公告弹窗
const dialogVisible = ref(false)
const publishing = ref(false)
const noticeForm = reactive({
  title: '', content: '', classId: null, importance: 'NORMAL',
})

function openPublish() {
  Object.assign(noticeForm, { title: '', content: '', classId: null, importance: 'NORMAL' })
  dialogVisible.value = true
}

async function handlePublish() {
  if (!noticeForm.title || !noticeForm.content) {
    ElMessage.warning('请填写公告标题和内容')
    return
  }
  publishing.value = true
  try {
    await createNotice({
      classId: noticeForm.classId || undefined,
      title: noticeForm.title,
      content: noticeForm.content,
      importance: noticeForm.importance,
    })
    ElMessage.success('公告已发布')
    dialogVisible.value = false
    await fetchNotices()
  } catch {} finally { publishing.value = false }
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除该公告吗？', '确认删除', { type: 'warning' })
  await deleteNotice(row.noticeId)
  ElMessage.success('已删除')
  await fetchNotices()
}

onMounted(() => {
  fetchNotices()
  fetchMessages()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">消息公告</h1>
      <el-button type="primary" @click="openPublish">发布班级公告</el-button>
    </div>

    <el-row :gutter="20">
      <!-- 已发布公告 -->
      <el-col :span="14">
        <div class="card-wrapper">
          <div class="card-title">我的班级公告</div>
          <div v-if="!notices.length" class="empty-state"><el-empty description="暂无公告" :image-size="60" /></div>
          <div v-else class="notice-list">
            <div v-for="n in notices" :key="n.noticeId" class="notice-item">
              <div class="notice-header">
                <span class="notice-title">{{ n.title }}</span>
                <el-tag v-if="n.importance === 'IMPORTANT'" type="danger" size="small">重要</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">普通</el-tag>
              </div>
              <div class="notice-content">{{ n.content }}</div>
              <div class="notice-footer">
                <span v-if="n.className">发送至：{{ n.className }}</span>
                <span v-if="n.readCount != null">{{ n.readCount }} 人已读</span>
                <span>{{ n.publishTime }}</span>
                <el-button size="small" type="danger" text @click="handleDelete(n)">删除</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 收到的消息 -->
      <el-col :span="10">
        <div class="card-wrapper">
          <div class="card-title">收到的消息</div>
          <div v-if="!messages.length" class="empty-state"><el-empty description="暂无消息" :image-size="60" /></div>
          <div v-else class="msg-list">
            <div v-for="m in messages" :key="m.messageId" class="msg-item" :class="{ unread: !m.isRead }">
              <div v-if="!m.isRead" class="msg-dot" />
              <el-tag :type="msgTypeConfig[m.type]?.type" size="small" effect="plain">
                {{ msgTypeConfig[m.type]?.label || m.type }}
              </el-tag>
              <div class="msg-body">
                <div class="msg-title">{{ m.title }}</div>
                <div class="msg-text">{{ m.content }}</div>
                <div class="msg-time">{{ m.sendTime }}</div>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 发布公告弹窗 -->
    <el-dialog v-model="dialogVisible" title="发布班级公告" width="560px">
      <el-form :model="noticeForm" label-width="80px">
        <el-form-item label="公告标题" required>
          <el-input v-model="noticeForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告内容" required>
          <el-input v-model="noticeForm.content" type="textarea" :rows="5" placeholder="请输入公告内容..." />
        </el-form-item>
        <el-form-item label="重要程度">
          <el-radio-group v-model="noticeForm.importance">
            <el-radio value="NORMAL">普通</el-radio>
            <el-radio value="IMPORTANT">重要</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="handlePublish">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
.notice-list { display: flex; flex-direction: column; gap: 12px; }
.notice-item { padding: 14px; border: 1px solid #ebeef5; border-radius: 8px; }
.notice-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.notice-title { font-size: 15px; font-weight: 600; color: #303133; }
.notice-content { font-size: 13px; color: #606266; line-height: 1.6; margin-bottom: 10px; }
.notice-footer { display: flex; align-items: center; gap: 16px; font-size: 12px; color: #909399; }
.msg-list { display: flex; flex-direction: column; }
.msg-item { display: flex; gap: 10px; padding: 12px 0; border-bottom: 1px solid #f5f5f5; cursor: pointer; }
.msg-item.unread { background: #ecf5ff; margin: 0 -12px; padding: 12px; border-radius: 4px; border-bottom: none; }
.msg-dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; margin-top: 6px; flex-shrink: 0; }
.msg-body { flex: 1; min-width: 0; }
.msg-title { font-size: 14px; font-weight: 500; color: #303133; }
.msg-text { font-size: 13px; color: #606266; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-time { font-size: 12px; color: #c0c4cc; margin-top: 2px; }
.empty-state { padding: 20px 0; }
</style>
