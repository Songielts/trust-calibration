<template>
  <main class="page">
    <div v-if="existing" class="card resume">
      <div>
        <strong>You have a study session in progress.</strong>
        <div class="small muted">You can continue where you left off.</div>
      </div>
      <div class="actions">
        <el-button @click="startOver">Start over</el-button>
        <el-button type="primary" @click="resume">Continue</el-button>
      </div>
    </div>

    <div class="card">
      <div class="eyebrow">Participant information sheet</div>
      <h1>Making decisions with an AI agent</h1>
      <p>
        AI agents increasingly help people choose what to buy, answer questions and give advice.
        In this study we look at how people decide <em>when to accept an AI agent's advice and when to check it</em>.
      </p>

      <h3>What you will do</h3>
      <ul class="steps">
        <li>Answer a few short questions about yourself (about 1 minute).</li>
        <li>
          Work through <strong>{{ info?.taskCount ?? 16 }} short questions</strong> on everyday topics such as prices,
          science, health and logic. For each one you first give <em>your own</em> answer, then see the AI agent's
          advice, and then make your final decision.
        </li>
        <li>Complete a short questionnaire about your experience.</li>
      </ul>
      <p class="muted">The study takes about {{ info?.estimatedMinutes ?? 20 }} minutes. Please do not use other tools or search engines while answering.</p>

      <h3>Your data</h3>
      <p class="muted">
        We do not collect your name or contact details. Your answers are stored anonymously under a random code and will
        only be used for research and teaching purposes. If the AI agent is powered by an online language model,
        only the question text is sent to it — never your answers or any information about you. Participation is voluntary and you may stop at any time
        by closing this page.
      </p>

      <el-checkbox v-model="consent" size="large">
        I have read the information above, I am 18 or older, and I agree to take part.
      </el-checkbox>

      <div class="actions">
        <el-button type="primary" size="large" :disabled="!consent" @click="begin">Begin</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { studyApi } from '../api'
import { CONDITIONS, clearSession, getSession, nextRoute, saveSession, setDemoCondition } from '../session'

const route = useRoute()
const router = useRouter()
const info = ref(null)
const consent = ref(false)
const existing = ref(null)

onMounted(async () => {
  // Demo links (/?demo=CAPABILITY) let a presenter choose the condition; /?demo=off clears it
  const demo = typeof route.query.demo === 'string' ? route.query.demo.toUpperCase() : null
  if (demo) {
    if (CONDITIONS[demo]) {
      setDemoCondition(demo)
      clearSession() // a demo link always starts a fresh session
    } else {
      setDemoCondition(null)
    }
    router.replace('/')
  }
  studyApi
    .info()
    .then((d) => {
      info.value = d
      if (!d.demoModeEnabled) setDemoCondition(null) // demo links are ignored when the server disables them
    })
    .catch(() => {})
  const s = getSession()
  if (s) {
    try {
      existing.value = await studyApi.session(s.participantId)
      saveSession(existing.value)
    } catch {
      clearSession()
    }
  }
})

function begin() {
  sessionStorage.setItem('consentGiven', 'yes')
  router.push('/about-you')
}

function resume() {
  router.push(nextRoute(existing.value))
}

async function startOver() {
  try {
    await ElMessageBox.confirm('Your previous answers will be kept, but you will start a new session.', 'Start over?', {
      confirmButtonText: 'Start over',
      cancelButtonText: 'Cancel'
    })
    clearSession()
    existing.value = null
  } catch {
    /* cancelled */
  }
}
</script>

<style scoped>
.resume { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; border-color: var(--accent); }
.resume .actions { margin-top: 0; }
.steps { padding-left: 20px; }
.steps li { margin-bottom: 6px; }
</style>
