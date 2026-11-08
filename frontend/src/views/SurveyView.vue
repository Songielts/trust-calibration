<template>
  <main class="page">
    <div class="card">
      <div class="eyebrow">Step 3 of 3 · Questionnaire</div>
      <h1>Your experience with the AI agent</h1>
      <p class="muted">Almost done. Please answer the questions below about the AI agent you just worked with.</p>
    </div>

    <div class="card">
      <h2>1. Your view of the AI agent</h2>
      <p class="muted small">How much do you agree with each statement?</p>
      <div v-for="item in trustItems" :key="item.key" class="form-item">
        <label class="form-label">{{ item.text }}</label>
        <LikertScale v-model="form[item.key]" :aria-label="item.text" />
      </div>
    </div>

    <div class="card">
      <h2>2. How accurate was the AI agent?</h2>
      <p class="muted small">Give your best guess as a number from 0 to 100.</p>

      <div class="form-item">
        <label class="form-label">Out of every 100 questions in this study, how many do you think the AI agent answered correctly?</label>
        <el-input-number v-model="form.estimatedOverallAccuracy" :min="0" :max="100" :controls="false" placeholder="0–100" :value-on-clear="null" />
        <span class="muted small unit">out of 100</span>
      </div>

      <label class="form-label">And for each topic?</label>
      <div class="domain-grid">
        <div v-for="d in domains" :key="d.code" class="domain-row">
          <span>{{ d.name }}</span>
          <span>
            <el-input-number v-model="domainEstimates[d.code]" :min="0" :max="100" :controls="false" placeholder="0–100" :value-on-clear="null" />
            <span class="muted small unit">out of 100</span>
          </span>
        </div>
      </div>
    </div>

    <div v-if="session?.showCapability" class="card">
      <h2>3. The accuracy information</h2>
      <div class="form-item">
        <label class="form-label">
          Next to its answers, the AI agent showed an accuracy figure for each topic, for example "54%" for Health &amp; Nutrition.
          What does this figure mean?
        </label>
        <el-radio-group v-model="form.comprehensionAnswer" class="vertical">
          <el-radio value="A">Out of every 100 Health &amp; Nutrition questions the agent was checked on, it answered about 54 correctly.</el-radio>
          <el-radio value="B">The agent is 54% sure that its current answer is correct.</el-radio>
          <el-radio value="C">54% of people agreed with the agent's answers.</el-radio>
          <el-radio value="D">I am not sure.</el-radio>
        </el-radio-group>
      </div>
    </div>

    <div class="card">
      <h2>{{ session?.showCapability ? '4' : '3' }}. Final questions</h2>
      <div class="form-item">
        <label class="form-label">How useful was the information shown with the AI agent's answers for deciding whether to follow them?</label>
        <LikertScale v-model="form.infoUsefulness" left-label="Not useful at all" right-label="Extremely useful" aria-label="Usefulness of information" />
      </div>
      <div class="form-item">
        <label class="form-label">Any comments? <span class="muted small">(optional)</span></label>
        <el-input v-model="form.comments" type="textarea" :rows="3" maxlength="2000" show-word-limit
                  placeholder="For example: how did you decide whether to follow the AI agent?" />
      </div>

      <div class="actions">
        <span v-if="!complete" class="muted small missing">Please answer all required questions.</span>
        <el-button type="primary" size="large" :disabled="!complete" :loading="saving" @click="submit">Submit questionnaire</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import LikertScale from '../components/LikertScale.vue'
import { studyApi } from '../api'
import { getSession, nextRoute, saveSession } from '../session'

const router = useRouter()
const session = ref(getSession())
const domains = ref([])
const saving = ref(false)

const trustItems = [
  { key: 'trustConfident', text: 'I am confident in the AI agent.' },
  { key: 'trustReliable', text: 'The AI agent is reliable.' },
  { key: 'trustTrustworthy', text: 'I can trust the AI agent.' },
  { key: 'trustDependable', text: 'The AI agent is dependable.' },
  { key: 'trustWary', text: 'I am wary of the AI agent.' }
]

const form = reactive({
  trustConfident: null,
  trustReliable: null,
  trustTrustworthy: null,
  trustDependable: null,
  trustWary: null,
  estimatedOverallAccuracy: null,
  infoUsefulness: null,
  comprehensionAnswer: null,
  comments: ''
})
const domainEstimates = reactive({})

const isNum = (v) => typeof v === 'number' && !Number.isNaN(v)
const complete = computed(() => {
  const trustDone = trustItems.every((i) => form[i.key] !== null)
  const domainsDone = domains.value.length > 0 && domains.value.every((d) => isNum(domainEstimates[d.code]))
  const comprehensionDone = !session.value?.showCapability || !!form.comprehensionAnswer
  return trustDone && isNum(form.estimatedOverallAccuracy) && domainsDone && comprehensionDone && form.infoUsefulness !== null
})

onMounted(async () => {
  const s = session.value
  if (!s) return router.replace('/')
  try {
    const fresh = await studyApi.session(s.participantId)
    saveSession(fresh)
    session.value = fresh
    if (fresh.completedTrials < fresh.totalTrials || fresh.status === 'COMPLETED') {
      return router.replace(nextRoute(fresh))
    }
  } catch {
    /* keep local session */
  }
  domains.value = await studyApi.domains()
  domains.value.forEach((d) => (domainEstimates[d.code] = null))
})

async function submit() {
  saving.value = true
  try {
    await studyApi.submitSurvey(session.value.participantId, {
      ...form,
      comments: form.comments || null,
      estimatedDomainAccuracy: { ...domainEstimates }
    })
    saveSession({ ...session.value, status: 'COMPLETED', surveyCompleted: true })
    router.push('/debrief')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.unit { margin-left: 8px; }
.domain-grid { display: grid; gap: 10px; max-width: 520px; }
.domain-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.vertical { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.vertical :deep(.el-radio) { height: auto; white-space: normal; line-height: 1.4; padding: 4px 0; }
.missing { align-self: center; }
</style>
