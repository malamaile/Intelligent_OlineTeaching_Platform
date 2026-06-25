<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'

const route = useRoute()

// 不需要 AppLayout 的页面（登录、注册、忘记密码、404）
const publicPages = ['Login', 'Register', 'ForgotPassword', 'NotFound']
const isPublicPage = computed(() => publicPages.includes(route.name))
</script>

<template>
  <AppLayout v-if="!isPublicPage" />
  <router-view v-else />
</template>
