import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getCurrentUser } from '../api/auth'
import { useUserStore } from '../stores/user'

/**
 * 路由表是静态写死的（不是后端下发动态注册），
 * 权限控制通过每条路由的 meta.roles 做校验。
 * 这样路由结构在代码里一目了然，改动和调试都简单。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    // public: true 表示无需登录即可访问，守卫会跳过它
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    redirect: '/devices',
  },
  {
    path: '/devices',
    name: 'DeviceList',
    component: () => import('../views/DeviceList.vue'),
    meta: { title: '设备管理', roles: ['admin', 'operator'] },
  },
  {
    path: '/device-repairs',
    name: 'DeviceRepair',
    component: () => import('../views/DeviceRepairView.vue'),
    meta: { title: '维修工单', roles: ['admin', 'operator'] },
  },
  {
    path: '/hello',
    name: 'Hello',
    component: () => import('../views/HelloView.vue'),
    meta: { title: '联调测试', roles: ['admin', 'operator'] },
  },
  {
    path: '/system',
    // 侧边栏里"系统管理"是个目录，没有自己的页面。
    // 加这条重定向，直接输 /system 也能落到第一个子页而不是 404
    redirect: '/system/users',
  },
  {
    path: '/system/users',
    name: 'SysUser',
    component: () => import('../views/UserView.vue'),
    meta: { title: '用户管理', roles: ['admin'] },
  },
  {
    path: '/system/roles',
    name: 'SysRole',
    component: () => import('../views/RoleView.vue'),
    meta: { title: '角色管理', roles: ['admin'] },
  },
  {
    path: '/system/menus',
    name: 'SysMenu',
    component: () => import('../views/MenuView.vue'),
    meta: { title: '菜单管理', roles: ['admin'] },
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

const LOGIN_PATH = '/login'

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // ---------- 1. 未登录 ----------
  if (!userStore.isLoggedIn) {
    if (to.meta.public === true) {
      return true
    }
    // 把原地址放进 query，登录成功后能跳回去
    return { path: LOGIN_PATH, query: { redirect: to.fullPath } }
  }

  // ---------- 2. 已登录却想访问登录页 ----------
  if (to.path === LOGIN_PATH) {
    return { path: '/devices' }
  }

  // ---------- 3. 有 token 但内存里没有用户信息 ----------
  // 刷新页面时会出现这种情况：token 存在 localStorage 里，
  // 但 store 里的 userInfo 已经随内存清空了。
  // 这里顺便验证 token 是否还有效 —— 失效会返回 401，
  // request.ts 的拦截器会清理状态并跳登录页。
  if (!userStore.userInfo) {
    try {
      const me = await getCurrentUser()
      userStore.refreshUserInfo({
        userId: me.userId,
        username: me.username,
        roles: me.roles,
      })
    } catch {
      // 拿不到就当作登录已失效
      userStore.logout()
      return { path: LOGIN_PATH, query: { redirect: to.fullPath } }
    }
  }

  // ---------- 4. 角色校验 ----------
  const required = to.meta.roles as string[] | undefined
  if (required && required.length > 0) {
    const allowed = required.some((role) => userStore.roles.includes(role))
    if (!allowed) {
      // 没有权限时退回首页，避免卡在一个访问不了的空页面上
      return { path: '/devices' }
    }
  }

  return true
})

// 顺手把页面标题跟着路由改掉，浏览器标签页上更直观
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - 设备管理系统` : '设备管理系统'
})

export default router
