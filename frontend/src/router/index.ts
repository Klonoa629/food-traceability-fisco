// 路由与守卫:未登录跳登录页,监管页仅 regulator 可进
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { setUnauthorizedHandler } from '../api/http'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    { path: '/trace', name: 'trace', component: () => import('../views/PublicTraceView.vue'), meta: { public: true } },
    { path: '/', name: 'products', component: () => import('../views/ProductListView.vue') },
    { path: '/products/:id', name: 'product-detail', component: () => import('../views/ProductDetailView.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/admin/AdminConsole.vue'), meta: { regulator: true } },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return auth.isLoggedIn ? { name: 'products' } : true
  }
  if (!auth.isLoggedIn) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.regulator && !auth.isRegulator) return { name: 'products' }
  return true
})

// token 过期:清掉并回登录页
setUnauthorizedHandler(() => {
  const auth = useAuthStore()
  auth.logout()
  if (router.currentRoute.value.name !== 'login') {
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

export default router
