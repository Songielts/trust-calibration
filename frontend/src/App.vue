<template>
  <header class="site-header">
    <div class="inner">
      <router-link :to="isAdmin ? '/admin' : '/'" class="brand">
        Decision Study <small>{{ isAdmin ? 'Researcher dashboard' : 'Working with an AI agent' }}</small>
      </router-link>
      <span v-if="!isAdmin" class="muted small">University research study</span>
    </div>
  </header>
  <div v-if="demoLabel" class="demo-banner">
    Demo mode · <strong>{{ demoLabel }}</strong> condition · this session is excluded from the analysis
  </div>
  <router-view />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { CONDITIONS, demoCondition, getSession } from './session'

const route = useRoute()
const isAdmin = computed(() => route.path.startsWith('/admin'))

// Re-evaluated on every navigation so the banner appears as soon as a demo session starts
const demoLabel = computed(() => {
  if (isAdmin.value) return null
  const path = route.fullPath // dependency on the route
  const s = getSession()
  if (s) return s.demo ? s.conditionLabel : null
  const pending = path && demoCondition.value
  return pending ? CONDITIONS[pending] : null
})
</script>
