<template>
  <main class="page">
    <div class="progress-head">
      <div class="small muted">Question {{ index + 1 }} of {{ total }}</div>
      <el-progress :percentage="progress" :show-text="false" :stroke-width="6" />
    </div>

    <div v-if="loading" class="card"><el-skeleton :rows="5" animated /></div>

    <template v-else-if="trial">
      <div class="card">
        <el-tag effect="plain" round class="domain-tag">{{ trial.domainName }}</el-tag>
        <h2 class="prompt">{{ trial.prompt }}</h2>

        <!-- ============ Stage 1: own answer ============ -->
        <section v-if="stage === 'INITIAL'">
          <div class="stage-title">
            <div class="stage-num">1</div>
            <strong>Your own answer</strong>
          </div>
          <OptionList v-model="initialAnswer" :options="trial.options" />

          <div class="form-item slider-block">
            <label class="form-label">How confident are you that your answer is correct?</label>
            <el-slider v-model="initialConfidence" :min="0" :max="100" :step="5" :marks="confidenceMarks" :format-tooltip="(v) => v + '%'" />
          </div>

          <div class="actions">
            <el-button type="primary" size="large" :disabled="initialAnswer === null" :loading="submitting" @click="submitInitial">
              Submit and see the AI agent's advice
            </el-button>
          </div>

          <!-- System state is shown while the agent works (the reply can take a few seconds with a live model) -->
          <div v-if="submitting" class="thinking" role="status" aria-live="polite">
            <div class="avatar" aria-hidden="true">AI</div>
            <span>Your answer is locked. The AI agent is working on this question</span>
            <span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>
          </div>
        </section>

        <!-- ============ Stage 2: advice + final decision ============ -->
        <section v-else>
          <div class="locked small muted">
            Your first answer: <strong>{{ letters[trial.initialAnswer] }}. {{ trial.options[trial.initialAnswer] }}</strong>
            ({{ trial.initialConfidence }}% confident)
          </div>

          <AdviceCard :advice="trial.advice" />

          <div class="stage-title">
            <div class="stage-num">2</div>
            <strong>Your final answer</strong>
          </div>
          <OptionList v-model="finalAnswer" :options="trial.options" :tags="finalTags" />

          <div class="form-item slider-block">
            <label class="form-label">How confident are you that your final answer is correct?</label>
            <el-slider v-model="finalConfidence" :min="0" :max="100" :step="5" :marks="confidenceMarks" :format-tooltip="(v) => v + '%'" />
          </div>

          <div class="form-item">
            <label class="form-label">How much do you trust the AI agent's advice on this question?</label>
            <LikertScale v-model="trustRating" left-label="Not at all" right-label="Completely" aria-label="Trust in this advice" />
          </div>

          <div class="actions">
            <el-button type="primary" size="large" :disabled="finalAnswer === null || trustRating === null" :loading="submitting" @click="submitFinal">
              {{ index + 1 >= total ? 'Confirm and finish' : 'Confirm final answer' }}
            </el-button>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import OptionList from '../components/OptionList.vue'
import AdviceCard from '../components/AdviceCard.vue'
import LikertScale from '../components/LikertScale.vue'
import { studyApi } from '../api'
import { getSession, nextRoute, saveSession } from '../session'

const route = useRoute()
const router = useRouter()
const letters = ['A', 'B', 'C', 'D', 'E', 'F']
const confidenceMarks = { 0: '0%', 50: '50%', 100: '100%' }

const session = ref(getSession())
const index = computed(() => Number(route.params.index))
const total = computed(() => session.value?.totalTrials ?? 0)
const progress = computed(() => (total.value ? Math.round((index.value / total.value) * 100) : 0))

const loading = ref(true)
const submitting = ref(false)
const trial = ref(null)
const stage = ref('INITIAL')

const initialAnswer = ref(null)
const initialConfidence = ref(50)
const finalAnswer = ref(null)
const finalConfidence = ref(50)
const trustRating = ref(null)
let stageStartedAt = performance.now()

const finalTags = computed(() => {
  if (!trial.value || !trial.value.advice) return {}
  const tags = {}
  const add = (i, tag) => (tags[i] = [...(tags[i] || []), tag])
  add(trial.value.initialAnswer, { text: 'Your first answer', cls: 'you' })
  add(trial.value.advice.aiAnswerIndex, { text: "AI agent's advice", cls: 'agent' })
  return tags
})

function resetForm() {
  initialAnswer.value = null
  initialConfidence.value = 50
  finalAnswer.value = null
  finalConfidence.value = 50
  trustRating.value = null
}

async function load() {
  const s = session.value
  if (!s) return router.replace('/')
  if (Number.isNaN(index.value) || index.value > s.completedTrials || index.value >= s.totalTrials) {
    return router.replace(nextRoute(s))
  }
  loading.value = true
  resetForm()
  try {
    const t = await studyApi.trial(s.participantId, index.value)
    if (t.stage === 'DONE') {
      // Already answered (e.g. browser back button): move on
      return router.replace(index.value + 1 >= t.total ? '/survey' : `/task/${index.value + 1}`)
    }
    trial.value = t
    stage.value = t.stage
    stageStartedAt = performance.now()
  } finally {
    loading.value = false
  }
}

async function submitInitial() {
  submitting.value = true
  try {
    const t = await studyApi.submitInitial(session.value.participantId, index.value, {
      answer: initialAnswer.value,
      confidence: initialConfidence.value,
      timeMs: Math.round(performance.now() - stageStartedAt)
    })
    trial.value = t
    stage.value = 'FINAL'
    stageStartedAt = performance.now()
  } finally {
    submitting.value = false
  }
}

async function submitFinal() {
  submitting.value = true
  try {
    const res = await studyApi.submitFinal(session.value.participantId, index.value, {
      answer: finalAnswer.value,
      confidence: finalConfidence.value,
      trustRating: trustRating.value,
      timeMs: Math.round(performance.now() - stageStartedAt)
    })
    const updated = { ...session.value, completedTrials: index.value + 1 }
    saveSession(updated)
    session.value = updated
    router.push(res.finished ? '/survey' : `/task/${res.nextIndex}`)
  } finally {
    submitting.value = false
  }
}

// The same component instance is reused from /task/0 to /task/1 ..., so reload when the index changes
watch(
  () => route.params.index,
  (v) => {
    if (v !== undefined) load()
  },
  { immediate: true }
)
</script>

<style scoped>
.progress-head { margin-bottom: 16px; }
.domain-tag { margin-bottom: 10px; }
.prompt { font-size: 1.25rem; margin-bottom: 22px; }
.slider-block { margin-top: 22px; padding: 0 10px 12px; }
.slider-block .form-label { margin-left: -10px; }
.locked { background: var(--accent-soft); border-radius: 8px; padding: 8px 12px; }
.thinking {
  display: flex; align-items: center; gap: 10px; margin-top: 16px;
  border: 1.5px dashed #b9dcd7; background: var(--agent-soft); border-radius: var(--radius); padding: 12px 16px;
  color: var(--agent); font-weight: 500;
}
.thinking .avatar {
  width: 30px; height: 30px; border-radius: 8px; background: var(--agent); color: #fff;
  display: grid; place-items: center; font-size: 0.75rem; font-weight: 700; flex: 0 0 auto;
}
.dots { display: inline-flex; gap: 4px; }
.dots i { width: 6px; height: 6px; border-radius: 50%; background: var(--agent); animation: blink 1.2s infinite ease-in-out; }
.dots i:nth-child(2) { animation-delay: 0.2s; }
.dots i:nth-child(3) { animation-delay: 0.4s; }
@keyframes blink { 0%, 80%, 100% { opacity: 0.2; } 40% { opacity: 1; } }
@media (prefers-reduced-motion: reduce) { .dots i { animation: none; opacity: 0.6; } }
</style>
