<template>
  <main class="page login">
    <div class="card">
      <div class="eyebrow">Researchers only</div>
      <h1>Dashboard sign-in</h1>
      <p class="muted small">Enter the admin token configured in <code>application.yml</code> (<code>study.admin-token</code>).</p>
      <el-input v-model="token" type="password" show-password placeholder="Admin token" size="large" @keyup.enter="login" />
      <div class="actions">
        <el-button type="primary" size="large" :loading="loading" :disabled="!token" @click="login">Sign in</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '../../api'

const router = useRouter()
const token = ref('')
const loading = ref(false)

async function login() {
  if (!token.value) return
  loading.value = true
  try {
    await adminApi.login(token.value)
    sessionStorage.setItem('adminToken', token.value)
    router.push('/admin')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login { max-width: 460px; padding-top: 80px; }
</style>
