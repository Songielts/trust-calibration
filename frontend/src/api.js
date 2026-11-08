import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

// Attach the admin token (if logged in) to researcher requests
http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('adminToken')
  if (token && config.url && config.url.startsWith('/admin')) {
    config.headers['X-Admin-Token'] = token
  }
  return config
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err.response?.data?.error || err.message || 'Request failed'
    if (!err.config?.silent) ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export const studyApi = {
  info: () => http.get('/study/info'),
  domains: () => http.get('/domains'),
  register: (body) => http.post('/participants', body),
  session: (pid) => http.get(`/participants/${pid}`, { silent: true }),
  trial: (pid, index) => http.get(`/participants/${pid}/trials/${index}`),
  // In live LLM mode this request waits for the model, so it gets a longer timeout
  submitInitial: (pid, index, body) => http.post(`/participants/${pid}/trials/${index}/initial`, body, { timeout: 90000 }),
  submitFinal: (pid, index, body) => http.post(`/participants/${pid}/trials/${index}/final`, body),
  submitSurvey: (pid, body) => http.post(`/participants/${pid}/survey`, body),
  debrief: (pid) => http.get(`/participants/${pid}/debrief`)
}

export const adminApi = {
  login: (token) => http.post('/admin/login', { token }),
  report: (includeSimulated, agentMode) => http.get('/admin/report', { params: { includeSimulated, agentMode } }),
  participants: (includeSimulated) => http.get('/admin/participants', { params: { includeSimulated } }),
  tasks: () => http.get('/admin/tasks'),
  domains: () => http.get('/admin/domains'),
  simulate: (count) => http.post('/admin/simulate', null, { params: { count } }),
  deleteSimulated: () => http.delete('/admin/simulated'),
  deleteDemo: () => http.delete('/admin/demo'),
  // ---- LLM agent
  llmStatus: () => http.get('/admin/llm/status'),
  setAgentMode: (mode) => http.put('/admin/llm/mode', { mode }),
  testCall: (taskId) => http.post('/admin/llm/test', null, { params: { taskId }, timeout: 90000 }),
  startRun: (repetitions) => http.post('/admin/llm/runs', null, { params: { repetitions } }),
  runs: () => http.get('/admin/llm/runs'),
  run: (id) => http.get(`/admin/llm/runs/${id}`),
  freeze: (id) => http.post(`/admin/llm/runs/${id}/freeze`),
  frozen: () => http.get('/admin/llm/frozen'),
  chooseFrozen: (taskId, adviceId) => http.put(`/admin/llm/frozen/${taskId}`, { adviceId }),
  async download(kind, includeSimulated) {
    const res = await axios.get(`/api/admin/export/${kind}.csv`, {
      params: { includeSimulated },
      headers: { 'X-Admin-Token': sessionStorage.getItem('adminToken') || '' },
      responseType: 'blob'
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `${kind}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }
}
