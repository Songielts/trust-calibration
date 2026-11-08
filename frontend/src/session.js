import { ref } from 'vue'

// Keeps the participant's study session in localStorage so a page refresh does not lose progress.
const KEY = 'trustStudySession'

export function getSession() {
  try {
    return JSON.parse(localStorage.getItem(KEY)) || null
  } catch {
    return null
  }
}

export function saveSession(session) {
  localStorage.setItem(KEY, JSON.stringify(session))
}

export function clearSession() {
  localStorage.removeItem(KEY)
}

// ---- Demo mode: a presenter opens /?demo=CAPABILITY to choose the condition shown
export const CONDITIONS = {
  CONTROL: 'Control (answer only)',
  CAPABILITY: 'Capability signal',
  REASONING: 'Step-by-step reasoning',
  CAPABILITY_REASONING: 'Capability + reasoning'
}
const DEMO_KEY = 'trustStudyDemoCondition'

export function getDemoCondition() {
  const v = sessionStorage.getItem(DEMO_KEY)
  return v && CONDITIONS[v] ? v : null
}

/** Reactive copy so the demo banner updates immediately */
export const demoCondition = ref(getDemoCondition())

export function setDemoCondition(value) {
  if (value && CONDITIONS[value]) sessionStorage.setItem(DEMO_KEY, value)
  else sessionStorage.removeItem(DEMO_KEY)
  demoCondition.value = getDemoCondition()
}

/** Where a participant with this server-side session state should be. */
export function nextRoute(s) {
  if (!s) return '/'
  if (s.status === 'COMPLETED') return '/debrief'
  if (s.completedTrials >= s.totalTrials) return '/survey'
  if (s.completedTrials === 0) return '/instructions'
  return `/task/${s.completedTrials}`
}
