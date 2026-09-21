import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getCurrentUser } from '../api/auth'
import { useAppStore } from '../stores/app'
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
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('../views/ChangePasswordView.vue'),
    // bare: true 表示**需要登录、但不套后台外壳**（没有侧边栏和顶栏）。
    // 和 public 的区别：public 是完全不需要登录。
    // 改密页必须是 bare —— 用户在这时还没通过强制改密，看到完整菜单没有意义
    meta: { title: '修改密码', bare: true },
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { title: '首页看板', roles: ['admin', 'operator'] },
  },
  {
    path: '/devices',
    name: 'DeviceList',
    component: () => import('../views/DeviceList.vue'),
    meta: { title: '设备管理', roles: ['admin', 'operator'] },
  },
  {
    // 台账必须声明在 /devices/:id **之前**。
    // vue-router 内部按"静态段优先于动态段"排序，顺序其实不影响匹配结果，
    // 但写在这里是为了让读代码的人一眼看出两者的优先级关系
    path: '/devices/ledger',
    name: 'DeviceLedger',
    component: () => import('../views/DeviceLedgerView.vue'),
    meta: { title: '设备台账', roles: ['admin', 'operator'] },
  },
  {
    path: '/devices/:id',
    name: 'DeviceDetail',
    component: () => import('../views/DeviceDetailView.vue'),
    meta: { title: '设备档案', roles: ['admin', 'operator'] },
  },
  {
    path: '/maintenance',
    name: 'Maintenance',
    component: () => import('../views/MaintenanceView.vue'),
    meta: { title: '维保管理', roles: ['admin', 'operator'] },
  },
  {
    path: '/spare-parts',
    name: 'SparePart',
    component: () => import('../views/SparePartView.vue'),
    meta: { title: '配件耗材', roles: ['admin', 'operator'] },
  },
  {
    // 设备关系图。所有角色都能看，和后端 /api/devices/{id}/graph 同口径 ——
    // 图上出现的都是能在设备档案、配件页里看到的既有数据
    path: '/graph',
    name: 'DeviceGraph',
    component: () => import('../views/DeviceGraphView.vue'),
    meta: { title: '设备关系图', roles: ['admin', 'operator'] },
  },
  {
    path: '/device-repairs',
    name: 'DeviceRepair',
    component: () => import('../views/DeviceRepairView.vue'),
    meta: { title: '维修工单', roles: ['admin', 'operator'] },
  },
  {
    path: '/messages',
    name: 'Message',
    component: () => import('../views/MessageView.vue'),
    // 消息是个人数据，两种角色都能看
    meta: { title: '消息中心', roles: ['admin', 'operator'] },
  },
  {
    path: '/ai',
    name: 'AiAssistant',
    component: () => import('../views/AiAssistantView.vue'),
    meta: { title: 'AI 助手', roles: ['admin', 'operator'] },
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
    path: '/system/login-logs',
    name: 'LoginLog',
    component: () => import('../views/LoginLogView.vue'),
    meta: { title: '登录日志', roles: ['admin'] },
  },
  {
    path: '/system/depts',
    name: 'SysDept',
    component: () => import('../views/DeptView.vue'),
    meta: { title: '部门管理', roles: ['admin'] },
  },
  {
    path: '/system/configs',
    name: 'SysConfig',
    component: () => import('../views/ConfigView.vue'),
    meta: { title: '系统设置', roles: ['admin'] },
  },
  {
    path: '/system/dicts',
    name: 'SysDict',
    component: () => import('../views/DictView.vue'),
    meta: { title: '字典管理', roles: ['admin'] },
  },
  {
    path: '/system/oper-logs',
    name: 'OperLog',
    component: () => import('../views/OperLogView.vue'),
    meta: { title: '操作日志', roles: ['admin'] },
  },
  {
    // 资产审计中心。和系统管理下其他页一样限 admin —— 后端那几个接口
    // 标的是 sys:audit:* 权限点，而默认不授给 operator（见 SystemDataSeeder
    // 的 OPERATOR_EXCLUDED_PERMS），两边口径一致
    path: '/system/audit-logs',
    name: 'AuditCenter',
    component: () => import('../views/AuditCenterView.vue'),
    meta: { title: '资产审计中心', roles: ['admin'] },
  },
  {
    // 设备知识库（RAG 的源文件管理）。限 admin，理由同上：
    // 往里传的是全局资料，和系统设置是一个性质
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('../views/KnowledgeView.vue'),
    meta: { title: '设备知识库', roles: ['admin'] },
  },
  {
    // 智能故障诊断。**所有角色都能用**（和 AI 助手同口径）——
    // 报修维修本来就是操作员在做。后端 /api/diagnosis/** 也只要求登录，
    // 检索到的知识库片段会返回，但知识库本身的增删改仍然限 admin
    path: '/diagnosis',
    name: 'Diagnosis',
    component: () => import('../views/DiagnosisView.vue'),
    meta: { title: '智能故障诊断', roles: ['admin', 'operator'] },
  },
  {
    // 兜底：访问不存在的路径时回到首页看板
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const LOGIN_PATH = '/login'

router.beforeEach(async (to) => {
  const userStore = useUserStore()

  // 系统名称/企业名称是免登录接口，第一次进任何页面时拉一次。
  // 放在守卫里而不是 main.ts：那时 Pinia 已经装好，且只会真正执行一次
  const appStore = useAppStore()
  if (!appStore.loaded) {
    void appStore.loadPublicConfig()
  }

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
    return { path: '/dashboard' }
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
        nickname: me.nickname,
        roles: me.roles,
        perms: me.perms,
        mustChangePassword: me.mustChangePassword,
      })
    } catch {
      // 拿不到就当作登录已失效
      userStore.logout()
      return { path: LOGIN_PATH, query: { redirect: to.fullPath } }
    }
  }

  // ---------- 强制修改密码 ----------
  // 后端会拦住这类用户的所有业务接口（返回 428），前端配合把人送到改密页。
  // 两边都做是必要的：只有后端的话，用户会看到一堆点开就报错的页面；
  // 只有前端则形同虚设 —— 绕过前端直接调接口就能继续用系统。
  const CHANGE_PWD_PATH = '/change-password'
  if (userStore.mustChangePassword && to.path !== CHANGE_PWD_PATH) {
    return { path: CHANGE_PWD_PATH, query: { redirect: to.fullPath } }
  }

  // ---------- 4. 角色校验 ----------
  const required = to.meta.roles as string[] | undefined
  if (required && required.length > 0) {
    const allowed = required.some((role) => userStore.roles.includes(role))
    if (!allowed) {
      // 没有权限时退回首页看板，避免卡在一个访问不了的空页面上。
      // 看板对所有角色开放，所以拿它当兜底是安全的
      return { path: '/dashboard' }
    }
  }

  return true
})

// 顺手把页面标题跟着路由改掉，浏览器标签页上更直观。
// 前缀用**系统参数里配的系统名称**，管理员改了标题就跟着变
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  const suffix = useAppStore().pageTitleSuffix
  document.title = title ? `${title} - ${suffix}` : suffix
})

export default router
