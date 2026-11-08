<template>
  <main class="page">
    <div class="card">
      <div class="eyebrow">Study complete</div>
      <h1>Thank you for taking part!</h1>
      <p v-if="!debrief || debrief.agentMode === 'SCRIPTED'">
        <strong>About the AI agent:</strong> the AI agent in this study was <em>simulated</em>. Its answers were prepared
        in advance by the researchers, and it was deliberately designed to be right on some questions and wrong on
        others, with its accuracy differing between topics. Any accuracy figures you may have seen were part of the
        study materials. The study compares different ways of presenting an AI agent's advice to find out which ones
        help people judge when to rely on it.
      </p>
      <p v-else>
        <strong>About the AI agent:</strong> the advice you saw was written by a large language model
        ({{ debrief.modelName }}).
        <template v-if="debrief.agentMode === 'LLM_LIVE'">It answered each question at the moment you submitted your own answer.</template>
        <template v-else>Its answers were generated before the study and checked by the researchers, so every participant saw the same answers.</template>
        Like any language model, it can produce fluent and convincing answers that are wrong. Any accuracy figures you
        may have seen were measured by this study system by testing the model on these questions. The study compares
        different ways of presenting an AI agent's advice to find out which ones help people judge when to rely on it.
      </p>
      <p class="muted">Please do not share details of the questions with other potential participants.</p>
    </div>

    <div v-if="debrief" class="stat-grid">
      <div class="stat">
        <div class="label">Your first answers</div>
        <div class="value">{{ debrief.initialCorrect }} / {{ debrief.total }}</div>
        <div class="sub">correct before seeing advice</div>
      </div>
      <div class="stat">
        <div class="label">Your final answers</div>
        <div class="value">{{ debrief.finalCorrect }} / {{ debrief.total }}</div>
        <div class="sub">correct after seeing advice</div>
      </div>
      <div class="stat">
        <div class="label">The AI agent</div>
        <div class="value">{{ debrief.aiCorrect }} / {{ debrief.total }}</div>
        <div class="sub">questions it got right</div>
      </div>
      <div class="stat">
        <div class="label">Wrong advice you followed</div>
        <div class="value">{{ debrief.followedAiWhenWrong }} / {{ debrief.aiWrong }}</div>
        <div class="sub">times the agent was wrong</div>
      </div>
    </div>

    <div v-if="debrief" class="card">
      <h2>Review the answers</h2>
      <el-collapse>
        <el-collapse-item v-for="item in debrief.items" :key="item.index" :name="item.index">
          <template #title>
            <span class="row-title">
              <el-icon :class="item.youCorrect ? 'good' : 'bad'">
                <CircleCheckFilled v-if="item.youCorrect" />
                <CircleCloseFilled v-else />
              </el-icon>
              <span class="q-num">Q{{ item.index }}</span>
              <span class="q-text">{{ item.prompt }}</span>
            </span>
          </template>
          <div class="detail">
            <div><span class="muted">Topic:</span> {{ item.domainName }}</div>
            <div><span class="muted">Correct answer:</span> <strong>{{ item.correctAnswer }}</strong></div>
            <div><span class="muted">Your final answer:</span> {{ item.yourAnswer }}</div>
            <div>
              <span class="muted">AI agent's advice:</span> {{ item.aiAnswer }}
              <el-tag :type="item.aiCorrect ? 'success' : 'danger'" size="small" effect="plain">
                {{ item.aiCorrect ? 'correct' : 'wrong' }}
              </el-tag>
            </div>
            <p class="explanation">{{ item.explanation }}</p>
          </div>
        </el-collapse-item>
      </el-collapse>

      <div class="actions">
        <el-button type="primary" @click="finish">Finish</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { studyApi } from '../api'
import { clearSession, getSession, setDemoCondition } from '../session'

const router = useRouter()
const debrief = ref(null)

onMounted(async () => {
  const s = getSession()
  if (!s) return router.replace('/')
  debrief.value = await studyApi.debrief(s.participantId)
})

function finish() {
  clearSession()
  setDemoCondition(null)
  sessionStorage.removeItem('consentGiven')
  router.push('/')
}
</script>

<style scoped>
.row-title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.q-num { font-weight: 600; color: var(--ink-soft); }
.q-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail { display: grid; gap: 4px; padding: 4px 0 8px; }
.explanation { margin: 8px 0 0; background: var(--paper); border-radius: 8px; padding: 10px 12px; }
</style>
