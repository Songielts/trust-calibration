<template>
  <section class="advice" aria-label="AI agent advice">
    <div class="agent-head">
      <div class="avatar" aria-hidden="true">AI</div>
      <div>
        <div class="agent-name">AI Agent</div>
        <div class="small muted">Decision assistant</div>
      </div>
    </div>

    <div class="recommendation">
      <div class="eyebrow">Recommended answer</div>
      <div class="answer">
        <span class="letter">{{ letters[advice.aiAnswerIndex] }}</span>
        {{ advice.aiAnswerText }}
      </div>
    </div>

    <!-- Capability signal (shown only in the CAPABILITY conditions) -->
    <div v-if="advice.capability" class="capability">
      <div class="eyebrow">Track record in {{ advice.capability.domainName }}</div>
      <div class="cap-row">
        <div class="cap-metric">
          <div class="cap-value">{{ fmt(advice.capability.historicalAccuracy) }}%</div>
          <div class="small muted">{{ measured ? 'measured accuracy' : 'historical accuracy' }}</div>
        </div>
        <div class="meter" aria-hidden="true">
          <div class="meter-fill" :style="{ width: advice.capability.historicalAccuracy + '%' }"></div>
        </div>
      </div>
      <!-- Provenance: where the figure comes from and how old it is -->
      <p v-if="measured" class="small muted cap-note">
        Correct on about {{ Math.round(advice.capability.historicalAccuracy) }} of every 100
        {{ advice.capability.domainName }} questions when this study system tested the agent
        ({{ advice.capability.sampleSize }} test answers, checked against the answer key on
        {{ formatDate(advice.capability.measuredOn) }}).
      </p>
      <p v-else class="small muted cap-note">
        Correct on about {{ Math.round(advice.capability.historicalAccuracy) }} of every 100 past
        {{ advice.capability.domainName }} questions (based on
        {{ advice.capability.sampleSize.toLocaleString('en-GB') }} questions).
        <template v-if="advice.capability.benchmarkName">
          Benchmark score on {{ advice.capability.benchmarkName }}:
          <strong>{{ fmt(advice.capability.benchmarkScore) }} / 100</strong>.
        </template>
      </p>
    </div>

    <!-- Step-by-step reasoning (shown only in the REASONING conditions) -->
    <div v-if="advice.reasoningSteps && advice.reasoningSteps.length" class="reasoning">
      <div class="eyebrow">How I reached this answer</div>
      <ol>
        <li v-for="(step, i) in advice.reasoningSteps" :key="i">{{ step }}</li>
      </ol>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ advice: { type: Object, required: true } })
const measured = computed(() => props.advice.capability?.source === 'MEASURED')
const formatDate = (iso) =>
  iso ? new Date(iso + 'T00:00:00').toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }) : 'an earlier date'

const letters = ['A', 'B', 'C', 'D', 'E', 'F']
const fmt = (v) => (Number.isInteger(v) ? v : Number(v).toFixed(1))
</script>

<style scoped>
.advice {
  border: 1.5px solid #b9dcd7;
  background: var(--agent-soft);
  border-radius: var(--radius);
  padding: 18px 20px;
  margin: 18px 0;
}
.agent-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.avatar {
  width: 38px; height: 38px; border-radius: 10px;
  background: var(--agent); color: #fff;
  display: grid; place-items: center; font-weight: 700; font-size: 0.85rem;
}
.agent-name { font-weight: 600; }
.recommendation { background: #fff; border-radius: 10px; padding: 12px 14px; }
.answer { display: flex; align-items: flex-start; gap: 10px; font-weight: 600; font-size: 1.02rem; }
.letter {
  flex: 0 0 26px; height: 26px; border-radius: 50%;
  background: var(--agent); color: #fff; display: grid; place-items: center; font-size: 0.8rem;
}
.capability, .reasoning { margin-top: 14px; background: #fff; border-radius: 10px; padding: 12px 14px; }
.cap-row { display: flex; align-items: center; gap: 16px; }
.cap-metric { flex: 0 0 auto; }
.cap-value { font-size: 1.6rem; font-weight: 700; color: var(--agent); font-variant-numeric: tabular-nums; line-height: 1.1; }
.meter { flex: 1; height: 10px; background: #dfe9e7; border-radius: 99px; overflow: hidden; }
.meter-fill { height: 100%; background: var(--agent); border-radius: 99px; }
.cap-note { margin: 10px 0 0; }
.reasoning ol { margin: 4px 0 0; padding-left: 20px; }
.reasoning li { margin-bottom: 6px; }
</style>
