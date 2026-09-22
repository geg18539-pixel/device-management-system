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
 *
 * <h3>两个区域</h3>
 *
 * <p>导航分成**员工前台（workbench）**和**管理后台（console）**两套，
 * 各有各的分组、各套各的外壳。划分的依据是路径而不是角色 —— 见
 * {@link areaOfRoute}。
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

/**
 * 区域。
 *
 * <p>之所以叫 workbench / console 而不是 front / back：
 * 前者是"员工干活的地方"，后者是"管理员配系统的地方" ——
 * 这两个词描述的是**用途**，不会和前端/后端的"前后"混起来。
 */
export type Area = 'workbench' | 'console'

export interface AreaMeta {
  /** 顶栏上显示的当前区域名 */
  label: string
  /** 侧栏品牌区的主标题 */
  brand: string
  /** 品牌区下面那行小字 */
  tagline: string
}

/**
 * 侧栏品牌区显示什么。
 *
 * <p>工作台的品牌用**系统参数里的系统名称**（管理员改了侧栏跟着变），
 * 所以这里留空 —— AppRail 在 brand 为空时回退到 store 里的系统名称。
 * 后台则是固定叫「管理后台」：那两个区域要一眼能区分开，
 * 名字跟着管理员改来改去反而失去了区分作用。
 */
export const AREA_META: Record<Area, AreaMeta> = {
  workbench: { label: '工作台', brand: '', tagline: '' },
  console: { label: '管理后台', brand: '管理后台', tagline: '系统与权限配置' },
}

const ALL_ROLES = ['admin', 'operator']
const ADMIN_ONLY = ['admin']

/**
 * 一个路径属于哪个区域。
 *
 * <p>判据是**路径前缀**，不是角色：`/system/**` 就是后台，其余都是工作台。
 *
 * <p>为什么不按角色判断：管理员**同时也是员工** —— 他要在工作台里管设备、
 * 看工单，也要到后台里配权限。如果按角色分流，"管理员看到的是什么"
 * 就永远只有一个答案，而实际上取决于他此刻在哪个区域。
 *
 * <p>为什么不做成每一条路由上的 `meta.area`：那需要维护 24 个重复字段，
 * 而漏标一个的后果是**那个页面套错外壳**，还不报错。一条规则更难写错。
 */
export function areaOfRoute(path: string): Area {
  return path === '/system' || path.startsWith('/system/') ? 'console' : 'workbench'
}

/**
 * 工作台的导航分组。
 *
 * <p>把原来平铺的 9 个顶级项收成几组。分组本身就是信息：用户找"工单"
 * 会先想到"运维"，比在一长串里逐个往下扫快得多。
 */
const WORKBENCH_GROUPS: MenuGroup[] = [
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
    ],
  },
]

/**
 * 管理后台的导航分组。
 *
 * <p>按"管理员平时怎么找"分成四组，而不是把九个页面平铺成一长条：
 * 「用户/角色/部门/菜单」是一件事（谁是谁、谁能干什么），
 * 「系统设置/字典」是一件事（全局参数），
 * 「登录日志/操作日志/资产审计」是一件事（追溯），
 * 知识库单独一组（它是 AI 能力的资料来源，性质上是配置）。
 *
 * <p>⚠️ 这里的分组顺序就是侧栏从上到下的顺序，**按使用频率排**：
 * 管人比查日志频繁得多。
 */
const CONSOLE_GROUPS: MenuGroup[] = [
  {
    // 概览单独一组、排在最前：它是进后台的落地页，
    // 也是唯一一个"看"而不是"改"的页面
    title: '概览',
    items: [
      { path: '/system/home', title: '系统概览', icon: 'i-home', roles: ADMIN_ONLY },
    ],
  },
  {
    title: '组织与权限',
    items: [
      { path: '/system/users', title: '用户管理', icon: 'i-user', roles: ADMIN_ONLY },
      { path: '/system/roles', title: '角色管理', icon: 'i-role', roles: ADMIN_ONLY },
      { path: '/system/depts', title: '部门管理', icon: 'i-dept', roles: ADMIN_ONLY },
      { path: '/system/menus', title: '菜单管理', icon: 'i-menu', roles: ADMIN_ONLY },
    ],
  },
  {
    title: '系统配置',
    items: [
      { path: '/system/configs', title: '系统设置', icon: 'i-config', roles: ADMIN_ONLY },
      { path: '/system/dicts', title: '字典管理', icon: 'i-dict', roles: ADMIN_ONLY },
    ],
  },
  {
    title: '日志与审计',
    items: [
      { path: '/system/login-logs', title: '登录日志', icon: 'i-login', roles: ADMIN_ONLY },
      { path: '/system/oper-logs', title: '操作日志', icon: 'i-log', roles: ADMIN_ONLY },
      { path: '/system/audit-logs', title: '资产审计中心', icon: 'i-audit', roles: ADMIN_ONLY },
    ],
  },
  {
    title: '知识与资料',
    items: [
      { path: '/system/knowledge', title: '设备知识库', icon: 'i-knowledge', roles: ADMIN_ONLY },
    ],
  },
]

/** 两个区域各自的导航。键就是 {@link Area} */
export const MENU_GROUPS: Record<Area, MenuGroup[]> = {
  workbench: WORKBENCH_GROUPS,
  console: CONSOLE_GROUPS,
}

/** 按角色过滤。子项先过滤，全被过滤掉时整个目录也不显示 ——
 *  否则 operator 会看到一个点开是空的"系统管理"，看着像 bug */
export function visibleGroups(area: Area, roles: string[]): MenuGroup[] {
  const ok = (item: MenuItem) => item.roles.some((r) => roles.includes(r))

  return MENU_GROUPS[area]
    .map((group) => {
      const items = group.items
        .map((item) => {
          if (!item.children) return ok(item) ? item : null
          const children = item.children.filter(ok)
          return children.length ? { ...item, children } : null
        })
        .filter((item): item is MenuItem => item !== null)
      return { title: group.title, items }
    })
    .filter((group) => group.items.length > 0)
}

/**
 * 找到某个路由路径对应的「分组名 + 菜单名」，给面包屑用。
 *
 * <p>**只在该路径所属区域的菜单里找** —— 两个区域的分组名可能重名
 * （比如两边都有叫「配置」的组），全区搜索会串味。
 *
 * <p>子路由（如 /devices/1042 这样的详情页）匹配不到菜单项时会逐级
 * 往前找父路径，最终落到"设备管理"这一类上，而不是显示空白。
 */
export function locateMenu(path: string): { group: string; title: string } | null {
  const normalized = path.replace(/\/+$/, '') || '/'

  for (const group of MENU_GROUPS[areaOfRoute(normalized)]) {
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
