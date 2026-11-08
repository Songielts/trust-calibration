import { createRouter, createWebHistory } from 'vue-router'
import { getSession } from './session'

const routes = [
  { path: '/', component: () => import('./views/WelcomeView.vue') },
  { path: '/about-you', component: () => import('./views/DemographicsView.vue') },
  { path: '/instructions', component: () => import('./views/InstructionsView.vue'), meta: { participant: true } },
  { path: '/task/:index', component: () => import('./views/TaskView.vue'), meta: { participant: true } },
  { path: '/survey', component: () => import('./views/SurveyView.vue'), meta: { participant: true } },
  { path: '/debrief', component: () => import('./views/DebriefView.vue'), meta: { participant: true } },
  { path: '/admin/login', component: () => import('./views/admin/AdminLogin.vue') },
  { path: '/admin', component: () => import('./views/admin/AdminDashboard.vue'), meta: { admin: true } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  if (to.meta.participant && !getSession()) return '/'
  if (to.meta.admin && !sessionStorage.getItem('adminToken')) return '/admin/login'
  return true
})

export default router
