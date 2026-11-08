<template>
  <main class="page wide">
    <!-- Toolbar -->
    <div class="toolbar card">
      <div class="toolbar-left">
        <h1 class="title">Trust calibration study</h1>
        <div class="small muted">2 × 2 design · capability signal × step-by-step reasoning</div>
      </div>
      <div class="toolbar-right">
        <el-select v-model="agentModeFilter" style="width: 200px" @change="loadAll">
          <el-option label="All agent modes" value="ALL" />
          <el-option label="Scripted agent" value="SCRIPTED" />
          <el-option label="LLM, pre-generated" value="LLM_FROZEN" />
          <el-option label="LLM, live" value="LLM_LIVE" />
        </el-select>
        <el-switch v-model="includeSimulated" active-text="Include demo & simulated data" @change="loadAll" />
        <el-button :icon="Refresh" @click="loadAll">Refresh</el-button>
        <el-dropdown trigger="click" @command="exportCsv">
          <el-button :icon="Download">Export CSV</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="trials">Trials (one row per decision)</el-dropdown-item>
              <el-dropdown-item command="participants">Participants (one row per person)</el-dropdown-item>
              <el-dropdown-item command="llm_calls">LLM calls (prompt log, every model call)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button text @click="logout">Sign out</el-button>
      </div>
    </div>

    <el-alert v-if="report && includeSimulated && report.simulatedParticipants + report.demoParticipants > 0" type="warning" :closable="false" show-icon class="sim-alert">
      Demo sessions and/or simulated participants are included. Simulated participants are synthetic data generated
      from assumptions in <code>SimulationService</code>, and demo sessions chose their condition — never report
      either as study findings.
    </el-alert>

    <div class="card preview">
      <div>
        <strong>Preview the participant view</strong>
        <div class="small muted">
          Opens the study in a new tab with the chosen condition (demo mode). Demo sessions are flagged and excluded from the analysis.
          <span v-if="demoModeEnabled === false" class="bad">Demo mode is disabled on the server (DEMO_MODE=false).</span>
        </div>
      </div>
      <div class="preview-actions">
        <el-button v-for="c in CONDITIONS" :key="c" :disabled="demoModeEnabled === false" @click="openDemo(c)">
          {{ shortLabel[c] }}
        </el-button>
      </div>
    </div>

    <div v-if="report" class="stat-grid">
      <div class="stat">
        <div class="label">Completed participants</div>
        <div class="value">{{ report.completedParticipants }}</div>
        <div class="sub">{{ report.totalParticipants }} started{{ report.simulatedParticipants ? ` · ${report.simulatedParticipants} simulated` : '' }}{{ report.demoParticipants ? ` · ${report.demoParticipants} demo` : '' }} in DB</div>
      </div>
      <div class="stat">
        <div class="label">Decisions recorded</div>
        <div class="value">{{ report.overall.trials }}</div>
        <div class="sub">completed trials</div>
      </div>
      <div class="stat">
        <div class="label">Agent's actual accuracy</div>
        <div class="value">{{ pct(report.agentActualAccuracy) }}</div>
        <div class="sub">of the advice participants saw</div>
      </div>
      <div class="stat">
        <div class="label">Appropriate reliance</div>
        <div class="value">{{ pct(report.overall.appropriateReliance) }}</div>
        <div class="sub">all conditions</div>
      </div>
      <div class="stat">
        <div class="label">Trust discrimination</div>
        <div class="value">{{ signed(report.overall.trustDiscrimination) }}</div>
        <div class="sub">trust | correct − trust | wrong</div>
      </div>
    </div>

    <el-tabs v-model="tab" class="card tabs">
      <!-- ================= Overview ================= -->
      <el-tab-pane label="By condition" name="overview">
        <el-alert v-if="agentModeFilter === 'ALL' && report && report.overall.trials > 0" type="info" :closable="false" show-icon class="sim-alert">
          Showing all agent modes together. The agent's reliability differs between the scripted agent and the LLM, so compare
          conditions within one mode (filter above).
        </el-alert>
        <el-empty v-if="report && report.overall.trials === 0" description="No completed participants yet. Take part via the study link, or generate simulated data below to preview the dashboard." />
        <template v-if="report && report.overall.trials > 0">
          <div class="chart-grid">
            <BarChart
              title="Reliance on the AI agent"
              :categories="conditionLabels"
              :series="[
                { name: 'Over-reliance (↓)', color: '#c2410c', values: byCond('overReliance') },
                { name: 'Under-reliance (↓)', color: '#d4a017', values: byCond('underReliance') },
                { name: 'Appropriate reliance (↑)', color: '#15803d', values: byCond('appropriateReliance') }
              ]"
            />
            <BarChart
              title="Per-question trust (1–7) when the AI was right vs wrong"
              :categories="conditionLabels"
              :max="7"
              :percent="false"
              :series="[
                { name: 'AI correct', color: '#0f766e', values: byCond('meanTrustAiCorrect') },
                { name: 'AI wrong', color: '#b91c1c', values: byCond('meanTrustAiWrong') }
              ]"
            />
            <BarChart
              title="Estimated vs actual agent accuracy"
              :categories="conditionLabels"
              :series="[{ name: 'Mean estimate', color: '#2f5d8a', values: byCond('estimatedOverall').map((v) => (v == null ? null : v / 100)) }]"
              :reference="report.agentActualAccuracy"
              :reference-label="`actual ${pct(report.agentActualAccuracy)}`"
            />
            <BarChart
              title="Calibration error (percentage points, ↓ better)"
              :categories="conditionLabels"
              :max="errorMax"
              :percent="false"
              :series="[
                { name: 'Overall estimate', color: '#6b7280', values: byCond('overallCalibrationError') },
                { name: 'Per-topic estimates', color: '#7c3aed', values: byCond('domainCalibrationError') }
              ]"
            />
          </div>

          <h3 class="section-title">All measures</h3>
          <el-table :data="metricRows" size="small" border class="metric-table">
            <el-table-column label="Measure" min-width="230" fixed>
              <template #default="{ row }">
                <span>{{ row.label }}</span>
                <el-tooltip v-if="row.help" :content="row.help" placement="right">
                  <el-icon class="help"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column v-for="c in conditionColumns" :key="c.key" :label="c.label" min-width="130" align="right">
              <template #default="{ row }">
                <span class="num">{{ row.values[c.key] }}</span>
              </template>
            </el-table-column>
          </el-table>
          <p class="small muted">
            Rates are pooled over decisions; use the CSV export for inferential statistics (e.g. mixed-effects
            logistic regression with participant as random effect, or two-way ANOVA on participant-level scores).
          </p>
        </template>
      </el-tab-pane>

      <!-- ================= By topic ================= -->
      <el-tab-pane label="By topic" name="domains">
        <template v-if="report">
          <p class="muted small">
            A well-calibrated participant trusts and follows the agent more in topics where it is actually more accurate.
            Capability signals advertise each topic's accuracy; compare how trust, agreement and estimates track the real accuracy.
          </p>
          <div class="chart-grid">
            <BarChart
              title="Estimated accuracy per topic vs the agent's actual accuracy"
              :categories="report.domains.map((d) => d.name)"
              :series="domainEstimateSeries"
            />
            <BarChart
              title="Agreement with the agent per topic"
              :categories="report.domains.map((d) => d.name)"
              :series="domainAgreementSeries"
            />
          </div>
          <el-table :data="report.domains" size="small" border>
            <el-table-column prop="name" label="Topic" min-width="160" fixed />
            <el-table-column label="Advertised" width="100" align="right">
              <template #default="{ row }">{{ row.advertisedAccuracy }}%</template>
            </el-table-column>
            <el-table-column label="Actual" width="90" align="right">
              <template #default="{ row }">{{ pct(row.actualAiAccuracy) }}</template>
            </el-table-column>
            <el-table-column v-for="c in conditionColumns.slice(0, 4)" :key="c.key" :label="c.label" align="center">
              <el-table-column label="Trust" width="70" align="right">
                <template #default="{ row }">{{ dec(row.byCondition[c.key].meanTrust) }}</template>
              </el-table-column>
              <el-table-column label="Agree" width="70" align="right">
                <template #default="{ row }">{{ pct(row.byCondition[c.key].agreementRate) }}</template>
              </el-table-column>
              <el-table-column label="Est." width="64" align="right">
                <template #default="{ row }">{{ dec(row.byCondition[c.key].meanEstimate, 0) }}</template>
              </el-table-column>
            </el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <!-- ================= Over time ================= -->
      <el-tab-pane label="Over time" name="blocks">
        <template v-if="report">
          <p class="muted small">
            Does reliance change as participants gain experience with the agent? Questions are split into the first and
            second half of each participant's (randomised) sequence.
          </p>
          <div class="chart-grid">
            <BarChart
              title="Over-reliance: first vs second half"
              :categories="conditionLabels.slice(0, 4)"
              :series="[
                { name: 'First half', color: '#9aa3b2', values: report.blocks.map((b) => b.firstHalfOverReliance) },
                { name: 'Second half', color: '#c2410c', values: report.blocks.map((b) => b.secondHalfOverReliance) }
              ]"
            />
            <BarChart
              title="Mean trust (1–7): first vs second half"
              :categories="conditionLabels.slice(0, 4)"
              :max="7"
              :percent="false"
              :series="[
                { name: 'First half', color: '#9aa3b2', values: report.blocks.map((b) => b.firstHalfTrust) },
                { name: 'Second half', color: '#2f5d8a', values: report.blocks.map((b) => b.secondHalfTrust) }
              ]"
            />
          </div>
          <el-table :data="report.blocks" size="small" border>
            <el-table-column prop="label" label="Condition" min-width="180" />
            <el-table-column label="Agreement 1st" align="right"><template #default="{ row }">{{ pct(row.firstHalfAgreement) }}</template></el-table-column>
            <el-table-column label="Agreement 2nd" align="right"><template #default="{ row }">{{ pct(row.secondHalfAgreement) }}</template></el-table-column>
            <el-table-column label="Over-reliance 1st" align="right"><template #default="{ row }">{{ pct(row.firstHalfOverReliance) }}</template></el-table-column>
            <el-table-column label="Over-reliance 2nd" align="right"><template #default="{ row }">{{ pct(row.secondHalfOverReliance) }}</template></el-table-column>
            <el-table-column label="Trust 1st" align="right"><template #default="{ row }">{{ dec(row.firstHalfTrust) }}</template></el-table-column>
            <el-table-column label="Trust 2nd" align="right"><template #default="{ row }">{{ dec(row.secondHalfTrust) }}</template></el-table-column>
          </el-table>
        </template>
      </el-tab-pane>

      <!-- ================= Participants ================= -->
      <el-tab-pane :label="`Participants (${participants.length})`" name="participants">
        <el-table :data="participants" size="small" border max-height="560">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="Condition" min-width="170">
            <template #default="{ row }">
              {{ shortLabel[row.condition] }}
              <el-tag v-if="row.simulated" size="small" type="warning" effect="plain">simulated</el-tag>
              <el-tag v-if="row.demo" size="small" type="info" effect="plain">demo</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Agent" width="110">
            <template #default="{ row }">{{ agentShort[row.agentMode] || row.agentMode }}</template>
          </el-table-column>
          <el-table-column label="Status" width="120">
            <template #default="{ row }">
              <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small" effect="plain">
                {{ row.status === 'COMPLETED' ? 'Completed' : 'In progress' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ageGroup" label="Age" width="80" />
          <el-table-column prop="education" label="Education" min-width="150" />
          <el-table-column prop="aiFamiliarity" label="AI famil." width="85" align="right" />
          <el-table-column prop="trialsCompleted" label="Trials" width="70" align="right" />
          <el-table-column label="Final acc." width="90" align="right"><template #default="{ row }">{{ pct(row.finalAccuracy) }}</template></el-table-column>
          <el-table-column label="Over-rel." width="90" align="right"><template #default="{ row }">{{ pct(row.overReliance) }}</template></el-table-column>
          <el-table-column label="Trust scale" width="95" align="right"><template #default="{ row }">{{ dec(row.trustScale) }}</template></el-table-column>
          <el-table-column label="Est. acc." width="85" align="right"><template #default="{ row }">{{ row.estimatedOverall ?? '—' }}</template></el-table-column>
          <el-table-column label="Started" min-width="150"><template #default="{ row }">{{ formatDate(row.createdAt) }}</template></el-table-column>
        </el-table>

        <div class="sim-box">
          <div>
            <strong>Demo data</strong>
            <div class="small muted">Generate synthetic participants to preview the dashboard and test the analysis pipeline. They are flagged as simulated and excluded unless the switch above is on.</div>
          </div>
          <div class="sim-actions">
            <el-input-number v-model="simCount" :min="1" :max="500" size="default" />
            <el-button :loading="simulating" @click="simulate">Generate</el-button>
            <el-button type="danger" plain @click="deleteSimulated">Delete simulated</el-button>
            <el-button type="danger" plain @click="deleteDemo">Delete demo sessions</el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ================= Materials ================= -->
      <!-- ================= AI agent ================= -->
      <el-tab-pane label="AI agent" name="agent" lazy>
        <AgentPanel :tasks="tasks" @changed="loadAll" />
      </el-tab-pane>

      <el-tab-pane label="Study materials" name="materials">
        <h3 class="section-title">Topics and capability signals</h3>
        <el-table :data="domainRows" size="small" border>
          <el-table-column prop="name" label="Topic" min-width="160" />
          <el-table-column label="Advertised accuracy" align="right"><template #default="{ row }">{{ row.historicalAccuracy }}%</template></el-table-column>
          <el-table-column prop="sampleSize" label="Based on (n)" align="right" />
          <el-table-column label="Benchmark" min-width="160"><template #default="{ row }">{{ row.benchmarkName }} · {{ row.benchmarkScore }}</template></el-table-column>
          <el-table-column label="Actual accuracy in study" align="right"><template #default="{ row }">{{ pct(row.actualAiAccuracy) }} ({{ row.taskCount }} tasks)</template></el-table-column>
        </el-table>

        <h3 class="section-title">Decision tasks</h3>
        <el-collapse>
          <el-collapse-item v-for="t in tasks" :key="t.id" :name="t.id">
            <template #title>
              <span class="task-title">
                <el-tag size="small" effect="plain">{{ t.code }}</el-tag>
                <el-tag size="small" :type="t.aiCorrect ? 'success' : 'danger'" effect="plain">AI {{ t.aiCorrect ? 'correct' : 'wrong' }}</el-tag>
                <span class="q-text">{{ t.prompt }}</span>
              </span>
            </template>
            <ol type="A" class="opt-list">
              <li v-for="(o, i) in t.options" :key="i">
                {{ o }}
                <el-tag v-if="i === t.correctIndex" size="small" type="success">correct</el-tag>
                <el-tag v-if="i === t.aiAnswerIndex" size="small" type="primary" effect="plain">AI advice</el-tag>
              </li>
            </ol>
            <div class="small"><strong>Reasoning shown to participants:</strong></div>
            <ol class="small muted">
              <li v-for="(s, i) in t.reasoningSteps" :key="i">{{ s }}</li>
            </ol>
            <div class="small"><strong>Explanation (debrief):</strong> {{ t.explanation }}</div>
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>
    </el-tabs>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, QuestionFilled, Refresh } from '@element-plus/icons-vue'
import BarChart from '../../components/BarChart.vue'
import AgentPanel from './AgentPanel.vue'
import { adminApi, studyApi } from '../../api'

const router = useRouter()
const tab = ref('overview')
const includeSimulated = ref(false)
const agentModeFilter = ref('ALL')
const report = ref(null)
const participants = ref([])
const tasks = ref([])
const domainRows = ref([])
const simCount = ref(40)
const simulating = ref(false)
const demoModeEnabled = ref(null)

const CONDITIONS = ['CONTROL', 'CAPABILITY', 'REASONING', 'CAPABILITY_REASONING']
const shortLabel = {
  CONTROL: 'Control',
  CAPABILITY: 'Capability',
  REASONING: 'Reasoning',
  CAPABILITY_REASONING: 'Capability + Reasoning',
  ALL: 'All'
}
const condColor = {
  CONTROL: '#9aa3b2',
  CAPABILITY: '#2f5d8a',
  REASONING: '#0f766e',
  CAPABILITY_REASONING: '#7c3aed'
}
const agentShort = { SCRIPTED: 'Scripted', LLM_FROZEN: 'LLM (frozen)', LLM_LIVE: 'LLM (live)' }
const conditionLabels = CONDITIONS.map((c) => shortLabel[c])
const conditionColumns = [...CONDITIONS, 'ALL'].map((key) => ({ key, label: shortLabel[key] }))

// ---------- formatting
const pct = (v) => (v == null ? '—' : `${(v * 100).toFixed(1)}%`)
const dec = (v, d = 2) => (v == null ? '—' : Number(v).toFixed(d))
const signed = (v, d = 2) => (v == null ? '—' : `${v > 0 ? '+' : ''}${Number(v).toFixed(d)}`)
const pp = (v) => (v == null ? '—' : `${Number(v).toFixed(1)} pp`)
const formatDate = (s) => (s ? new Date(s).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' }) : '—')

const metricDefs = [
  { key: 'participants', label: 'Participants', f: (v) => v },
  { key: 'trials', label: 'Decisions', f: (v) => v },
  { key: 'initialAccuracy', label: 'Initial accuracy', f: pct, help: 'Share of first answers (before seeing advice) that were correct.' },
  { key: 'finalAccuracy', label: 'Final accuracy', f: pct, help: 'Share of final answers that were correct.' },
  { key: 'agreementRate', label: 'Agreement with AI', f: pct, help: "Share of final answers equal to the AI agent's advice." },
  { key: 'switchToAiRate', label: 'Switch to AI', f: pct, help: 'When the first answer differed from the advice: share of final answers that switched to the advice.' },
  { key: 'overReliance', label: 'Over-reliance (↓)', f: pct, help: 'When the AI was wrong: share of final answers that followed it.' },
  { key: 'underReliance', label: 'Under-reliance (↓)', f: pct, help: 'When the AI was right: share of final answers that did not follow it.' },
  { key: 'rair', label: 'RAIR (↑)', f: pct, help: 'Relative AI reliance: when the first answer was wrong and the AI right, share who switched to the AI.' },
  { key: 'rsr', label: 'RSR (↑)', f: pct, help: 'Relative self-reliance: when the first answer was right and the AI wrong, share who kept their own answer.' },
  { key: 'appropriateReliance', label: 'Appropriate reliance (↑)', f: pct, help: 'Share of decisions that followed the AI when it was right and did not follow it when it was wrong.' },
  { key: 'meanTrust', label: 'Mean trust per question (1–7)', f: (v) => dec(v) },
  { key: 'meanTrustAiCorrect', label: 'Trust when AI correct', f: (v) => dec(v) },
  { key: 'meanTrustAiWrong', label: 'Trust when AI wrong', f: (v) => dec(v) },
  { key: 'trustDiscrimination', label: 'Trust discrimination (↑)', f: (v) => signed(v), help: 'Mean trust when the AI was correct minus when it was wrong. Higher means trust better tracks reliability.' },
  { key: 'trustCorrelation', label: 'Trust–correctness r (↑)', f: (v) => signed(v), help: 'Point-biserial correlation between per-question trust and AI correctness.' },
  { key: 'trustScale', label: 'Trust scale (1–7)', f: (v) => dec(v), help: 'Post-task questionnaire, 5 items adapted from Jian et al. (2000); "wary" item reverse-scored.' },
  { key: 'estimatedOverall', label: 'Estimated AI accuracy', f: (v) => (v == null ? '—' : `${dec(v, 1)}%`), help: "Participants' estimate of the share of questions the agent answered correctly." },
  { key: 'overallCalibrationError', label: 'Calibration error, overall (↓)', f: pp, help: '|estimated − actual| overall accuracy, in percentage points.' },
  { key: 'domainCalibrationError', label: 'Calibration error, per topic (↓)', f: pp, help: 'Mean |estimated − actual| accuracy across the four topics, in percentage points.' },
  { key: 'comprehensionRate', label: 'Understood capability signal', f: pct, help: 'Capability conditions only: share who correctly interpreted "historical accuracy".' },
  { key: 'infoUsefulness', label: 'Perceived usefulness of info (1–7)', f: (v) => dec(v) },
  { key: 'meanDecisionSeconds', label: 'Time per question (s)', f: (v) => dec(v, 1) }
]

const allMetrics = computed(() => {
  if (!report.value) return {}
  const map = { ALL: report.value.overall }
  report.value.conditions.forEach((c) => (map[c.condition] = c))
  return map
})

const metricRows = computed(() =>
  metricDefs.map((m) => {
    const values = {}
    conditionColumns.forEach((c) => {
      const raw = allMetrics.value[c.key]?.[m.key]
      values[c.key] = raw == null ? '—' : m.f(raw)
    })
    return { label: m.label, help: m.help, values }
  })
)

const byCond = (key) => CONDITIONS.map((c) => allMetrics.value[c]?.[key] ?? null)

const errorMax = computed(() => {
  const vals = [...byCond('overallCalibrationError'), ...byCond('domainCalibrationError')].filter((v) => v != null)
  const m = Math.max(10, ...vals)
  return Math.ceil(m / 10) * 10
})

const domainEstimateSeries = computed(() => {
  if (!report.value) return []
  const ds = report.value.domains
  return [
    { name: 'Actual', color: '#1d2433', values: ds.map((d) => d.actualAiAccuracy) },
    ...CONDITIONS.map((c) => ({
      name: `Est. · ${shortLabel[c]}`,
      color: condColor[c],
      values: ds.map((d) => (d.byCondition[c].meanEstimate == null ? null : d.byCondition[c].meanEstimate / 100))
    }))
  ]
})

const domainAgreementSeries = computed(() => {
  if (!report.value) return []
  const ds = report.value.domains
  return [
    { name: 'Actual accuracy', color: '#1d2433', values: ds.map((d) => d.actualAiAccuracy) },
    ...CONDITIONS.map((c) => ({
      name: shortLabel[c],
      color: condColor[c],
      values: ds.map((d) => d.byCondition[c].agreementRate)
    }))
  ]
})

// ---------- data loading & actions
async function loadAll() {
  const [r, p] = await Promise.all([adminApi.report(includeSimulated.value, agentModeFilter.value), adminApi.participants(includeSimulated.value)])
  report.value = r
  participants.value = p
}

async function loadMaterials() {
  const [t, d] = await Promise.all([adminApi.tasks(), adminApi.domains()])
  tasks.value = t
  domainRows.value = d
}

async function exportCsv(kind) {
  try {
    await adminApi.download(kind, includeSimulated.value)
  } catch {
    ElMessage.error('Export failed')
  }
}

async function simulate() {
  simulating.value = true
  try {
    const res = await adminApi.simulate(simCount.value)
    ElMessage.success(`${res.created} simulated participants created`)
    includeSimulated.value = true
    await loadAll()
  } finally {
    simulating.value = false
  }
}

async function deleteSimulated() {
  try {
    await ElMessageBox.confirm('Delete all simulated participants and their data? Real participants are not affected.', 'Delete simulated data', {
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })
  } catch {
    return
  }
  const res = await adminApi.deleteSimulated()
  ElMessage.success(`${res.created} simulated participants deleted`)
  await loadAll()
}

async function deleteDemo() {
  try {
    await ElMessageBox.confirm('Delete all sessions started from demo links? Real participants are not affected.', 'Delete demo sessions', {
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })
  } catch {
    return
  }
  const res = await adminApi.deleteDemo()
  ElMessage.success(`${res.created} demo sessions deleted`)
  await loadAll()
}

function openDemo(condition) {
  window.open(`/?demo=${condition}`, '_blank', 'noopener')
}

function logout() {
  sessionStorage.removeItem('adminToken')
  router.push('/admin/login')
}

onMounted(async () => {
  studyApi.info().then((d) => (demoModeEnabled.value = d.demoModeEnabled)).catch(() => {})
  try {
    await Promise.all([loadAll(), loadMaterials()])
  } catch (e) {
    if (e.response?.status === 401) logout()
  }
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.toolbar .title { font-size: 1.4rem; margin: 0; }
.toolbar-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.sim-alert { margin-bottom: 16px; }
.preview { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.preview-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.preview-actions .el-button + .el-button { margin-left: 0; }
.tabs { padding-top: 12px; }
.chart-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(440px, 1fr)); gap: 28px 36px; margin: 8px 0 24px; }
@media (max-width: 520px) { .chart-grid { grid-template-columns: 1fr; } }
.section-title { margin-top: 18px; }
.help { margin-left: 6px; color: var(--ink-faint); vertical-align: -2px; cursor: help; }
.metric-table { margin-bottom: 10px; }
.sim-box { margin-top: 20px; display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap;
  border: 1px dashed var(--line); border-radius: 10px; padding: 14px 16px; background: var(--paper); }
.sim-actions { display: flex; gap: 10px; align-items: center; }
.task-title { display: flex; gap: 8px; align-items: center; min-width: 0; }
.q-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.opt-list li { margin-bottom: 4px; }
</style>
