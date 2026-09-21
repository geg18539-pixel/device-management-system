/**
 * 侧边栏导航的结构定义。
 *
 * <p>放在这里而不是写死在 AppRail.vue 里，是因为**面包屑也要用它** ——
 * 顶栏那行"资产 / 设备管理"需要知道当前页面属于哪个分组，两边共用一份
 * 定义才不会出现"侧边栏改了名字、面包屑还是旧的"。
 *
 * <p>权限仍然是**静态声明 + 前端过滤**：每条声明允许看到它的角色，
 * 渲染前按当前用户的角色过滤。路由本身能不能进由 router/index.ts 的
 * 守卫把关，两者配合才完整 —— 隐藏菜单只是界面效果，不是安全边界。
 */
export interface MenuItem {
  /** 路由路径。有 children 的目录项不需要 */
  path?: string
  title: string
  /** SVG symbol 的 id（见 App.vue 顶部的图标集）。子项通常不配 */
  icon?: string
  /** 允许看到这条菜单的角色 */
  roles: string[]
  children?: MenuItem[]
}

export interface MenuGroup {
  /** 分组标题。刻意做得很轻 —— 它是路标，不是内容 */
  title: string
  items: MenuItem[]
}

const ALL_ROLES = ['admin', 'operator']

/**
 * 导航分组。
 *
 * <p>把原来平铺的 9 个顶级项收成 5 组。分组本身就是信息：用户找"工单"
 * 会先想到"运维"，比在一长串里逐个往下扫快得多。
 */
export const MENU_GROUPS: MenuGroup[] = [
  {
    title: '概览',
    items: [{ path: '/dashboard', title: '首页看板', icon: 'i-dash', roles: ALL_ROLES }],
  },
  {
    title: '资产',
    items: [
      { path: '/devices', title: '设备管理', icon: 'i-device', roles: ALL_ROLES },
      { path: '/devices/ledger', title: '设备台账', icon: 'i-ledger', roles: ALL_ROLES },
      { path: '/spare-parts', title: '配件耗材', icon: 'i-part', roles: ALL_ROLES },
      // 关系图放在「资产」而不是「AI 能力」：它虽然是在 AI 那批计划里提的，
      // 但实现上完全是确定性的关系查询、没有模型参与 ——
      // 放进 AI 组会让人以为"这图是模型画的"，那是对它的误解
      { path: '/graph', title: '设备关系图', icon: 'i-graph', roles: ALL_ROLES },
    ],
  },
  {
    title: '运维',
    items: [
      { path: '/device-repairs', title: '维修工单', icon: 'i-wrench', roles: ALL_ROLES },
      { path: '/maintenance', title: '维保管理', icon: 'i-shield', roles: ALL_ROLES },
    ],
  },
  {
    title: '协同',
    items: [{ path: '/messages', title: '消息中心', icon: 'i-bell', roles: ALL_ROLES }],
  },
  {
    // AI 那几块单独成组：它们是"系统会自己下判断"的能力，
    // 和消息中心这种纯协作工具不是一类。
    //
    // 诊断对**所有角色**开放（和 AI 助手同口径）：报修和维修本来就是
    // 操作员在做，诊断是给他们用的工具。它检索的是知识库，
    // 但暴露的只是命中的片段和文档标题 —— 知识库的增删改仍然限 admin
    title: 'AI 能力',
    items: [
      { path: '/ai', title: 'AI 助手', icon: 'i-spark', roles: ALL_ROLES },
      { path: '/diagnosis', title: '智能故障诊断', icon: 'i-diagnosis', roles: ALL_ROLES },
      { path: '/query', title: '数据问答', icon: 'i-query', roles: ALL_ROLES },
      { path: '/knowledge', title: '设备知识库', icon: 'i-knowledge', roles: ['admin'] },
    ],
  },
  {
    title: '系统',
    items: [
      {
        title: '系统管理',
        icon: 'i-cog',
        roles: ['admin'],
        children: [
          { path: '/system/users', title: '用户管理', roles: ['admin'] },
          { path: '/system/roles', title: '角色管理', roles: ['admin'] },
          { path: '/system/menus', title: '菜单管理', roles: ['admin'] },
          { path: '/system/depts', title: '部门管理', roles: ['admin'] },
          { path: '/system/configs', title: '系统设置', roles: ['admin'] },
          { path: '/system/dicts', title: '字典管理', roles: ['admin'] },
          { path: '/system/login-logs', title: '登录日志', roles: ['admin'] },
          { path: '/system/oper-logs', title: '操作日志', roles: ['admin'] },
          { path: '/system/audit-logs', title: '资产审计中心', icon: 'i-audit', roles: ['admin'] },
        ],
      },
    ],
  },
]

/** 按角色过滤。子项先过滤，全被过滤掉时整个目录也不显示 ——
 *  否则 operator 会看到一个点开是空的"系统管理"，看着像 bug */
export function visibleGroups(roles: string[]): MenuGroup[] {
  const ok = (item: MenuItem) => item.roles.some((r) => roles.includes(r))

  return MENU_GROUPS.map((group) => {
    const items = group.items
      .map((item) => {
        if (!item.children) return ok(item) ? item : null
        const children = item.children.filter(ok)
        return children.length ? { ...item, children } : null
      })
      .filter((item): item is MenuItem => item !== null)
    return { title: group.title, items }
  }).filter((group) => group.items.length > 0)
}

/**
 * 找到某个路由路径对应的「分组名 + 菜单名」，给面包屑用。
 *
 * <p>子路由（如 /devices/1042 这样的详情页）匹配不到菜单项时会逐级
 * 往前找父路径，最终落到"设备管理"这一类上，而不是显示空白。
 */
export function locateMenu(path: string): { group: string; title: string } | null {
  const normalized = path.replace(/\/+$/, '') || '/'

  for (const group of MENU_GROUPS) {
    for (const item of group.items) {
      if (item.children) {
        const child = item.children.find((c) => c.path === normalized)
        if (child) return { group: group.title, title: child.title }
        // 子路由落到父目录上
        if (item.children.some((c) => c.path && normalized.startsWith(`${c.path}/`))) {
          return { group: group.title, title: item.title }
        }
        continue
      }
      if (item.path === normalized) return { group: group.title, title: item.title }
      if (item.path && normalized.startsWith(`${item.path}/`)) {
        return { group: group.title, title: item.title }
      }
    }
  }
  return null
}
