<template>
  <main class="page">
    <div class="card">
      <div class="eyebrow">Step 2 of 3 · Instructions</div>
      <h1>How each question works</h1>
      <p>You will see {{ session?.totalTrials ?? 16 }} multiple-choice questions. Each question has two steps:</p>

      <div class="how">
        <div class="how-step">
          <div class="stage-num">1</div>
          <div>
            <strong>Your own answer.</strong>
            <p class="muted">Choose the answer you think is correct and say how confident you are.
              Your first answer is locked once you submit it.</p>
          </div>
        </div>
        <div class="how-step">
          <div class="stage-num agent">AI</div>
          <div>
            <strong>The AI agent's advice.</strong>
            <p class="muted">The AI agent then recommends an answer. It may also show some information
              about itself or about how it reached its answer. Like any AI system, it can make mistakes.</p>
          </div>
        </div>
        <div class="how-step">
          <div class="stage-num">2</div>
          <div>
            <strong>Your final decision.</strong>
            <p class="muted">Choose your final answer — you may keep your own answer or change it —
              and rate how much you trust the AI agent's advice on that question.</p>
          </div>
        </div>
      </div>

      <el-alert type="info" :closable="false" show-icon>
        Try to get as many <strong>final</strong> answers right as possible. You will find out which answers were
        correct at the end of the study, not during it.
      </el-alert>

      <div class="actions">
        <el-button type="primary" size="large" @click="router.push('/task/0')">Start the questions</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { getSession } from '../session'

const router = useRouter()
const session = getSession()
</script>

<style scoped>
.how { display: grid; gap: 14px; margin: 18px 0 22px; }
.how-step { display: flex; gap: 14px; align-items: flex-start; }
.how-step p { margin: 2px 0 0; }
.stage-num { flex: 0 0 28px; width: 28px; height: 28px; border-radius: 50%; background: var(--accent); color: #fff;
  display: grid; place-items: center; font-weight: 700; font-size: 0.8rem; }
.stage-num.agent { background: var(--agent); font-size: 0.7rem; }
</style>
