<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, updateSettings } from '@/api/admin'

const loading = ref(true)
const saving = ref(false)

const settings = reactive({
  currentSemester: '',
  currentSchoolYear: '',
  maxFileUploadSize: 100,
  allowedFileTypes: ['pdf', 'doc', 'docx', 'ppt', 'pptx', 'mp4', 'jpg', 'png', 'zip'],
  academicThresholds: {
    excellentMinScore: 85,
    goodMinScore: 70,
  },
  passwordRule: {
    defaultPassword: '123456',
    minLength: 6,
    maxLength: 20,
  },
})

async function fetchSettings() {
  loading.value = true
  try {
    const res = await getSettings()
    if (res.data) {
      // 后端返回结构与 settings 完全对应，直接合并
      // maxFileUploadSize: 后端返回 MB 数字
      // allowedFileTypes: 后端返回数组
      // academicThresholds: { excellentMinScore, goodMinScore }
      // passwordRule: { defaultPassword, minLength, maxLength }
      const d = res.data
      Object.assign(settings, {
        currentSemester: d.currentSemester || '',
        currentSchoolYear: d.currentSchoolYear || '',
        maxFileUploadSize: Number(d.maxFileUploadSize) || 100,
        allowedFileTypes: Array.isArray(d.allowedFileTypes) ? d.allowedFileTypes : [],
        academicThresholds: {
          excellentMinScore: Number(d.academicThresholds?.excellentMinScore) || 85,
          goodMinScore: Number(d.academicThresholds?.goodMinScore) || 70,
        },
        passwordRule: {
          defaultPassword: d.passwordRule?.defaultPassword || '123456',
          minLength: Number(d.passwordRule?.minLength) || 6,
          maxLength: Number(d.passwordRule?.maxLength) || 20,
        },
      })
    }
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    // key 必须和 sys_config 表中的 config_key + 后端 getSettings 读取的 key 完全一致
    await updateSettings({
      current_semester: settings.currentSemester,
      current_school_year: settings.currentSchoolYear,
      max_file_upload_size: String(settings.maxFileUploadSize * 1024 * 1024),
      allowed_file_types: settings.allowedFileTypes.join(','),
      excellent_min_score: String(settings.academicThresholds.excellentMinScore),
      good_min_score: String(settings.academicThresholds.goodMinScore),
      default_password: settings.passwordRule.defaultPassword,
      password_min_length: String(settings.passwordRule.minLength),
      password_max_length: String(settings.passwordRule.maxLength),
    })
    ElMessage.success('系统配置已保存')
  } catch (e) {
    ElMessage.error(e?.message || '保存失败，请重试')
  } finally { saving.value = false }
}

const fileTypeOptions = [
  'pdf', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx',
  'mp4', 'avi', 'mov', 'jpg', 'jpeg', 'png', 'gif', 'zip', 'rar',
]

// 动态生成学期选项（当前年前后各推一年）
const semesterOptions = computed(() => {
  const now = new Date()
  const y = now.getFullYear()
  const list = []
  for (let i = -1; i <= 1; i++) {
    const start = y + i
    const end = start + 1
    list.push({ label: `${start}-${end} 春季学期`, value: `${start}-${end}-2` })
    list.push({ label: `${start}-${end} 秋季学期`, value: `${start}-${end}-1` })
  }
  return list
})

const schoolYearOptions = computed(() => {
  const now = new Date()
  const y = now.getFullYear()
  const list = []
  for (let i = -1; i <= 1; i++) {
    const start = y + i
    list.push({ label: `${start}-${start + 1} 学年`, value: `${start}-${start + 1}` })
  }
  return list
})

onMounted(fetchSettings)
</script>

<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <h1 class="page-title">系统设置</h1>
    </div>

    <el-row :gutter="20">
      <!-- 基础参数 1/3 -->
      <el-col :span="8">
        <div class="card-wrapper">
          <div class="card-title">基础参数配置</div>
          <el-form label-width="120px">
            <el-form-item label="当前学期">
              <el-select v-model="settings.currentSemester">
                <el-option v-for="s in semesterOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="当前学年">
              <el-select v-model="settings.currentSchoolYear">
                <el-option v-for="s in schoolYearOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="最大上传大小">
              <el-input-number v-model="settings.maxFileUploadSize" :min="1" :max="500" /> MB
            </el-form-item>
            <el-form-item label="允许上传格式">
              <el-select v-model="settings.allowedFileTypes" multiple placeholder="选择文件类型">
                <el-option v-for="ft in fileTypeOptions" :key="ft" :label="ft" :value="ft" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>
      </el-col>

      <!-- 学情诊断规则 1/3 -->
      <el-col :span="8">
        <div class="card-wrapper">
          <div class="card-title">学情诊断规则</div>
          <el-form label-width="100px">
            <el-form-item label="优秀最低分">
              <el-input-number v-model="settings.academicThresholds.excellentMinScore" :min="0" :max="100" />
            </el-form-item>
            <el-form-item label="良好最低分">
              <el-input-number v-model="settings.academicThresholds.goodMinScore" :min="0" :max="100" />
            </el-form-item>
          </el-form>
        </div>
      </el-col>

      <!-- 密码规则 1/3 -->
      <el-col :span="8">
        <div class="card-wrapper">
          <div class="card-title">密码规则</div>
          <el-form label-width="100px">
            <el-form-item label="默认密码">
              <el-input v-model="settings.passwordRule.defaultPassword" />
            </el-form-item>
            <el-form-item label="最小长度">
              <el-input-number v-model="settings.passwordRule.minLength" :min="4" :max="32" />
            </el-form-item>
            <el-form-item label="最大长度">
              <el-input-number v-model="settings.passwordRule.maxLength" :min="8" :max="64" />
            </el-form-item>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <div style="text-align:center;margin-top:20px">
      <el-button type="primary" size="large" :loading="saving" @click="handleSave">保存配置</el-button>
    </div>
  </div>
</template>

<style scoped>
.page-header { margin-bottom: 16px; }
</style>
