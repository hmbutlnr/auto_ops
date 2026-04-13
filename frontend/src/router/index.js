import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import HostManagement from '../views/HostManagement.vue'

const routes = [
  {
    path: '/',
    name: 'Chat',
    component: ChatView,
    meta: { title: 'AI 助手' }
  },
  {
    path: '/hosts',
    name: 'HostManagement',
    component: HostManagement,
    meta: { title: '主机管理' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title || 'Auto Ops'
  next()
})

export default router
