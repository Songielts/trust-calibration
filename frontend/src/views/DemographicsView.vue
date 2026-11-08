<template>
  <main class="page">
    <div class="card">
      <div class="eyebrow">Step 1 of 3 · About you</div>
      <h1>A few questions about you</h1>
      <p class="muted">These help us describe who took part. There are no right or wrong answers.</p>

      <div class="form-item">
        <label class="form-label">Age group</label>
        <el-radio-group v-model="form.ageGroup">
          <el-radio-button v-for="a in ages" :key="a" :value="a">{{ a }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="form-item">
        <label class="form-label">Gender <span class="muted small">(optional)</span></label>
        <el-select v-model="form.gender" placeholder="Select" clearable style="width: 260px">
          <el-option v-for="g in genders" :key="g" :label="g" :value="g" />
        </el-select>
      </div>

      <div class="form-item">
        <label class="form-label">Highest level of education</label>
        <el-select v-model="form.education" placeholder="Select" style="width: 260px">
          <el-option v-for="e in educations" :key="e" :label="e" :value="e" />
        </el-select>
      </div>

      <div class="form-item">
        <label class="form-label">How often do you use AI assistants (e.g. chatbots)?</label>
        <el-radio-group v-model="form.aiUsageFrequency">
          <el-radio-button v-for="u in usages" :key="u" :value="u">{{ u }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="form-item">
        <label class="form-label">How familiar are you with how AI systems work?</label>
        <LikertScale v-model="form.aiFamiliarity" left-label="Not at all familiar" right-label="Very familiar" aria-label="AI familiarity" />
      </div>

      <div class="form-item">
        <label class="form-label">"In general, I tend to trust new technology."</label>
        <LikertScale v-model="form.trustPropensity" aria-label="Trust propensity" />
      </div>

      <div class="actions">
        <el-button @click="router.push('/')">Back</el-button>
        <el-button type="primary" :disabled="!complete" :loading="saving" @click="submit">Continue</el-button>
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import LikertScale from '../components/LikertScale.vue'
import { studyApi } from '../api'
import { getDemoCondition, getSession, nextRoute, saveSession } from '../session'

const router = useRouter()
const ages = ['18-24', '25-34', '35-44', '45-54', '55+']
const genders = ['Female', 'Male', 'Non-binary', 'Prefer to self-describe', 'Prefer not to say']
const educations = ['Secondary school', 'Vocational qualification', "Bachelor's degree", "Master's degree", 'Doctorate', 'Other']
const usages = ['Never', 'Monthly', 'Weekly', 'Daily']

const form = reactive({
  ageGroup: '',
  gender: '',
  education: '',
  aiUsageFrequency: '',
  aiFamiliarity: null,
  trustPropensity: null
})
const saving = ref(false)

const complete = computed(
  () => form.ageGroup && form.education && form.aiUsageFrequency && form.aiFamiliarity && form.trustPropensity
)

onMounted(() => {
  const s = getSession()
  if (s) router.replace(nextRoute(s))
  else if (sessionStorage.getItem('consentGiven') !== 'yes') router.replace('/')
})

async function submit() {
  saving.value = true
  try {
    const session = await studyApi.register({
      ...form,
      gender: form.gender || null,
      consent: true,
      demoCondition: getDemoCondition()
    })
    saveSession(session)
    router.push('/instructions')
  } finally {
    saving.value = false
  }
}
</script>
