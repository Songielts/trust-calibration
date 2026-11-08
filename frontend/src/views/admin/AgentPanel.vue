<template>
  <div class="agent-panel">
    <!-- ================= Status & mode ================= -->
    <section class="block">
      <div class="block-head">
        <h3>AI agent</h3>
        <el-tag v-if="status" :type="status.apiKeyConfigured ? 'success' : 'info'" effect="plain">
          {{ status.apiKeyConfigured ? 'API key configured' : 'No API key — scripted agent only' }}
        </el-tag>
      </div>
      <p v-if="status" class="small muted">
        Model <strong>{{ status.model }}</strong> at <code>{{ status.baseUrl }}</code> · temperature {{ status.temperature }}
        · prompt {{ status.promptVersion }}
        <span v-if="!status.apiKeyConfigured">
          · To enable the LLM, set <code>LLM_API_KEY</code> or create <code>backend/local-secrets.yml</code>, then restart the backend.
        </span>
      </p>

      <div class="modes">
        <label v-for="m in modes" :key="m.value" class="mode" :class="{ selected: selectedMode === m.value, disabled: !modeAvailable(m.value) }">
          <input v-model="selectedMode" type="radio" name="agent-mode" :value="m.value" :disabled="!modeAvailable(m.value)" />
          <span>
            <strong>{{ m.title }}</strong>
            <span class="small muted">{{ m.desc }}</span>
          </span>
        </label>
      </div>
      <div class="row-actions">
        <span class="small muted">
          Current mode: <strong>{{ modeTitle(status?.agentMode) }}</strong>. A change applies to participants who register from now on;
          each participant keeps the mode they started with.
        </span>
        <el-button type="primary" :disabled="!status || selectedMode === status.agentMode" :loading="savingMode" @click="applyMode">
          Apply mode
        </el-button>
      </div>
    </section>

    <!-- ================= Prompt specification ================= -->
    <section v-if="status" class="block">
      <el-collapse>
        <el-collapse-item name="spec">
          <template #title><strong>Prompt specification ({{ status.promptVersion }}) and success criteria</strong></template>
          <pre class="prompt">{{ status.promptText }}</pre>
          <div class="criteria">
            <div>
              <div class="eyebrow">Checked by the program on every reply</div>
              <ul><li v-for="c in status.checkedCriteria" :key="c">{{ c }}</li></ul>
            </div>
            <div>
              <div class="eyebrow">Checked by the researcher when reviewing frozen answers</div>
              <ul><li v-for="c in status.humanCriteria" :key="c">{{ c }}</li></ul>
            </div>
          </div>
          <p class="small muted">Correctness is never taken from the model: every answer is compared with the answer key by the system.</p>
        </el-collapse-item>
      </el-collapse>
    </section>

    <!-- ================= Test call ================= -->
    <section class="block">
      <h3>Test one call</h3>
      <div class="row-actions left">
        <el-select v-model="testTaskId" placeholder="Choose a task" style="width: 320px">
          <el-option v-for="t in tasks" :key="t.id" :label="`${t.code} · ${t.prompt.slice(0, 50)}…`" :value="t.id" />
        </el-select>
        <el-button :disabled="!status?.apiKeyConfigured" :loading="testing" @click="runTest">Send to model</el-button>
      </div>
      <div v-if="test" class="test-result">
        <div class="result-line">
          <el-tag :type="test.result.valid ? 'success' : 'danger'" effect="plain">{{ test.result.valid ? 'valid output' : 'rejected' }}</el-tag>
          <el-tag v-if="test.result.valid" :type="test.result.correct ? 'success' : 'warning'" effect="plain">
            {{ test.result.correct ? 'correct' : 'wrong' }} (answer key: {{ letter(test.correctIndex) }})
          </el-tag>
          <span class="small muted">{{ test.result.latencyMs }} ms</span>
        </div>
        <p v-if="!test.result.valid" class="bad small">{{ test.result.validationError }}</p>
        <p v-else><strong>{{ letter(test.result.answerIndex) }}. {{ test.result.answerText }}</strong>
          <span class="small muted"> · self-reported confidence {{ test.result.selfReportedConfidence }} (logged, not shown to participants)</span></p>
        <ol v-if="test.result.reasoningSteps?.length" class="small"><li v-for="(s, i) in test.result.reasoningSteps" :key="i">{{ s }}</li></ol>
        <el-collapse>
          <el-collapse-item title="Messages sent (context assembly)" name="sent"><pre class="prompt">{{ test.messagesSent }}</pre></el-collapse-item>
          <el-collapse-item title="Raw model output" name="raw"><pre class="prompt">{{ test.result.rawOutput || '(none)' }}</pre></el-collapse-item>
        </el-collapse>
      </div>
    </section>

    <!-- ================= Evaluation runs (prompt log) ================= -->
    <section class="block">
      <div class="block-head">
        <h3>Evaluation runs <span class="muted small">(prompt log)</span></h3>
        <div class="row-actions left">
          <span class="small muted">Repetitions per task</span>
          <el-input-number v-model="repetitions" :min="1" :max="10" size="small" />
          <el-button type="primary" :disabled="!status?.apiKeyConfigured || !!status?.runningRunId" :loading="starting" @click="startRun">
            Run evaluation
          </el-button>
        </div>
      </div>
      <p class="small muted">
        Each run sends every task to the model {{ repetitions }} time(s) ({{ (status?.taskCount || 16) * repetitions }} calls) with the same prompt,
        and scores every reply against the answer key. Its per-topic accuracy becomes the capability signal shown to participants in the LLM modes.
      </p>
      <el-table :data="runs" size="small" border empty-text="No runs yet">
        <el-table-column prop="id" label="#" width="50" />
        <el-table-column label="Started" min-width="130"><template #default="{ row }">{{ date(row.startedAt) }}</template></el-table-column>
        <el-table-column label="Model / prompt" min-width="150">
          <template #default="{ row }">{{ row.model }} · {{ row.promptVersion }} · t={{ row.temperature }}</template>
        </el-table-column>
        <el-table-column label="Status" min-width="150">
          <template #default="{ row }">
            <template v-if="row.status === 'RUNNING'">
              <el-progress :percentage="Math.round((row.completedCalls / row.totalCalls) * 100)" :stroke-width="8" />
            </template>
            <el-tooltip v-else :content="row.note || ''" :disabled="!row.note">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'danger'" size="small" effect="plain">{{ row.status.toLowerCase() }}</el-tag>
            </el-tooltip>
            <el-tag v-if="row.active" size="small" type="primary" class="ml">active</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Valid" width="70" align="right"><template #default="{ row }">{{ pct(row.validRate) }}</template></el-table-column>
        <el-table-column label="Accuracy" width="85" align="right"><template #default="{ row }">{{ pct(row.accuracy) }}</template></el-table-column>
        <el-table-column label="Per topic" min-width="200">
          <template #default="{ row }">
            <span v-for="(v, k) in row.domainAccuracy" :key="k" class="topic">{{ topicShort[k] || k }} {{ v }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="" width="150" align="right">
          <template #default="{ row }">
            <el-button size="small" text :disabled="row.status === 'RUNNING'" @click="openRun(row.id)">View</el-button>
            <el-button size="small" text type="primary" :disabled="row.status !== 'COMPLETED'" @click="freeze(row.id)">Freeze</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ================= Run detail ================= -->
    <section v-if="detail" class="block">
      <div class="block-head">
        <h3>Run #{{ detail.run.id }} — answers per task</h3>
        <el-button size="small" text @click="detail = null">Close</el-button>
      </div>
      <el-table :data="detail.tasks" size="small" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="calls">
              <div v-for="c in row.callRows" :key="c.id" class="call">
                <div class="result-line">
                  <span class="small muted">call {{ c.id }}</span>
                  <el-tag v-if="!c.valid" type="danger" size="small" effect="plain">rejected</el-tag>
                  <el-tag v-else :type="c.correct ? 'success' : 'warning'" size="small" effect="plain">
                    {{ letter(c.answerIndex) }} · {{ c.correct ? 'correct' : 'wrong' }}
                  </el-tag>
                  <span v-if="c.valid" class="small muted">confidence {{ c.selfReportedConfidence }}</span>
                  <span class="small muted">{{ c.latencyMs }} ms</span>
                  <el-tag v-if="c.frozen" size="small" type="primary">frozen</el-tag>
                </div>
                <div v-if="!c.valid" class="small bad">{{ c.validationError }}</div>
                <ol v-else class="small"><li v-for="(s, i) in c.reasoningSteps" :key="i">{{ s }}</li></ol>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="taskCode" label="Task" width="100" />
        <el-table-column label="Question" min-width="260"><template #default="{ row }"><span class="clip">{{ row.prompt }}</span></template></el-table-column>
        <el-table-column label="Key" width="55" align="center"><template #default="{ row }">{{ letter(row.correctIndex) }}</template></el-table-column>
        <el-table-column label="Answers" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="(n, k) in row.answerCounts" :key="k" size="small" effect="plain" class="ml"
                    :type="k === 'invalid' ? 'danger' : k === letter(row.correctIndex) ? 'success' : 'warning'">{{ k }} × {{ n }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Correct" width="80" align="right"><template #default="{ row }">{{ row.correctCalls }}/{{ row.calls }}</template></el-table-column>
      </el-table>
    </section>

    <!-- ================= Frozen answer bank ================= -->
    <section class="block">
      <h3>Frozen answers <span class="muted small">(shown in "LLM, pre-generated" mode)</span></h3>
      <p class="small muted">
        Freezing a run keeps, for each task, the answer the model gave most often (majority over repetitions), so every participant
        sees the same answer. Review each one against the human criteria above; you can replace it with another valid answer from the same run.
        Tasks without any valid answer fall back to the scripted answer.
      </p>
      <el-empty v-if="!status?.activeRunId" description="Nothing frozen yet. Run an evaluation, then click Freeze." :image-size="70" />
      <el-table v-else :data="frozen" size="small" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <ol class="small"><li v-for="(s, i) in row.reasoningSteps" :key="i">{{ s }}</li></ol>
          </template>
        </el-table-column>
        <el-table-column prop="taskCode" label="Task" width="100" />
        <el-table-column label="Question" min-width="230"><template #default="{ row }"><span class="clip">{{ row.prompt }}</span></template></el-table-column>
        <el-table-column label="Frozen answer" min-width="200">
          <template #default="{ row }">
            <template v-if="row.adviceId">
              {{ letter(row.answerIndex) }}. {{ row.answerText }}
              <el-tag :type="row.correct ? 'success' : 'warning'" size="small" effect="plain" class="ml">{{ row.correct ? 'correct' : 'wrong' }}</el-tag>
              <el-tag v-if="!row.majorityAnswer" size="small" type="info" effect="plain" class="ml">researcher choice</el-tag>
            </template>
            <span v-else class="muted">— scripted fallback</span>
          </template>
        </el-table-column>
        <el-table-column label="Replace with" width="190">
          <template #default="{ row }">
            <el-select :model-value="row.adviceId" size="small" placeholder="—" :disabled="!variants[row.taskId]?.length"
                       @change="(id) => choose(row.taskId, id)">
              <el-option v-for="c in variants[row.taskId] || []" :key="c.id" :value="c.id"
                         :label="`call ${c.id}: ${letter(c.answerIndex)} (${c.correct ? 'correct' : 'wrong'})`" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const props = defineProps({ tasks: { type: Array, default: () => [] } })
const emit = defineEmits(['changed'])

const modes = [
  { value: 'SCRIPTED', title: 'Scripted agent', desc: 'Researcher-written answers; reliability fully controlled (12/16 correct). Use for the controlled experiment.' },
  { value: 'LLM_FROZEN', title: 'LLM, pre-generated', desc: 'Answers generated by the model in an evaluation run, reviewed and frozen; everyone sees the same answers.' },
  { value: 'LLM_LIVE', title: 'LLM, live', desc: 'The model answers when the participant commits their own answer; answers can differ between participants.' }
]
const topicShort = { SHOP: 'Shop', SCIENCE: 'Sci', HEALTH: 'Health', LOGIC: 'Logic' }

const status = ref(null)
const selectedMode = ref('SCRIPTED')
const savingMode = ref(false)
const testTaskId = ref(null)
const testing = ref(false)
const test = ref(null)
const repetitions = ref(3)
const starting = ref(false)
const runs = ref([])
const detail = ref(null)
const frozen = ref([])
const activeDetail = ref(null)
let pollTimer = null

const tasks = computed(() => props.tasks)
const letter = (i) => (i == null ? '—' : 'ABCDEF'[i])
const pct = (v) => (v == null ? '—' : `${Math.round(v * 100)}%`)
const date = (s) => (s ? new Date(s).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '—')
const modeTitle = (m) => modes.find((x) => x.value === m)?.title || m

/** Valid answers from the active run, per task: the options a frozen answer can be replaced with. */
const variants = computed(() => {
  const out = {}
  for (const t of activeDetail.value?.tasks || []) out[t.taskId] = t.callRows.filter((c) => c.valid)
  return out
})

function modeAvailable(mode) {
  if (!status.value) return mode === 'SCRIPTED'
  if (mode === 'SCRIPTED') return true
  if (!status.value.activeRunId) return false
  return mode === 'LLM_FROZEN' || status.value.apiKeyConfigured
}

async function loadStatus() {
  status.value = await adminApi.llmStatus()
  selectedMode.value = status.value.agentMode
}

async function loadRuns() {
  runs.value = await adminApi.runs()
  const running = runs.value.some((r) => r.status === 'RUNNING')
  if (running && !pollTimer) pollTimer = setInterval(poll, 2000)
  if (!running && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function poll() {
  const wasRunning = !!status.value?.runningRunId
  await Promise.all([loadRuns(), loadStatus()])
  if (wasRunning && !status.value.runningRunId) ElMessage.success('Evaluation run finished')
}

async function loadFrozen() {
  frozen.value = await adminApi.frozen()
  activeDetail.value = status.value?.activeRunId ? await adminApi.run(status.value.activeRunId) : null
}

async function applyMode() {
  savingMode.value = true
  try {
    status.value = await adminApi.setAgentMode(selectedMode.value)
    ElMessage.success(`New participants will get: ${modeTitle(status.value.agentMode)}`)
    emit('changed')
  } catch {
    selectedMode.value = status.value.agentMode
  } finally {
    savingMode.value = false
  }
}

async function runTest() {
  testing.value = true
  try {
    test.value = await adminApi.testCall(testTaskId.value)
  } finally {
    testing.value = false
  }
}

async function startRun() {
  starting.value = true
  try {
    await adminApi.startRun(repetitions.value)
    await Promise.all([loadStatus(), loadRuns()])
  } finally {
    starting.value = false
  }
}

async function openRun(id) {
  detail.value = await adminApi.run(id)
}

async function freeze(id) {
  try {
    await ElMessageBox.confirm(
      `Freeze the majority answers of run #${id}? Its measured accuracy becomes the capability signal for new LLM-mode participants.`,
      'Freeze run', { confirmButtonText: 'Freeze', cancelButtonText: 'Cancel' })
  } catch {
    return
  }
  await adminApi.freeze(id)
  await loadStatus()
  await Promise.all([loadRuns(), loadFrozen()])
  ElMessage.success(`Run #${id} frozen`)
}

async function choose(taskId, adviceId) {
  frozen.value = await adminApi.chooseFrozen(taskId, adviceId)
}

onMounted(async () => {
  await loadStatus()
  await Promise.all([loadRuns(), loadFrozen()])
  if (props.tasks.length) testTaskId.value = props.tasks[0].id
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.block { padding: 6px 0 18px; border-bottom: 1px solid var(--line); margin-bottom: 18px; }
.block:last-child { border-bottom: none; }
.block-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.block-head h3 { margin: 0; }
.modes { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 10px; margin: 12px 0; }
.mode { display: flex; gap: 10px; align-items: flex-start; border: 1.5px solid var(--line); border-radius: 10px; padding: 10px 12px; cursor: pointer; }
.mode span { display: flex; flex-direction: column; gap: 2px; }
.mode.selected { border-color: var(--accent); background: var(--accent-soft); }
.mode.disabled { opacity: 0.5; cursor: not-allowed; }
.mode input { margin-top: 4px; }
.row-actions { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.row-actions.left { justify-content: flex-start; }
.prompt { white-space: pre-wrap; background: var(--paper); border-radius: 8px; padding: 10px 12px; font-size: 0.8rem; max-height: 360px; overflow: auto; }
.criteria { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 12px; }
.criteria ul { margin: 4px 0 8px; padding-left: 18px; font-size: 0.88rem; }
.test-result { margin-top: 12px; }
.result-line { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 4px; }
.topic { display: inline-block; margin-right: 8px; font-size: 0.8rem; font-variant-numeric: tabular-nums; }
.ml { margin-left: 4px; }
.clip { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.calls { display: grid; gap: 10px; padding: 4px 12px; }
.call ol { margin: 4px 0 0; padding-left: 18px; }
</style>
