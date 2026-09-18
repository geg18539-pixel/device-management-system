import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/devices',
  },
  {
    path: '/devices',
    name: 'DeviceList',
    component: () => import('../views/DeviceList.vue'),
    meta: { title: '设备管理' },
  },
  {
    path: '/hello',
    name: 'Hello',
    component: () => import('../views/HelloView.vue'),
    meta: { title: '联调测试' },
  },
  {
    // 兜底：访问不存在的路径时回到设备列表
    path: '/:pathMatch(.*)*',
    redirect: '/devices',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
