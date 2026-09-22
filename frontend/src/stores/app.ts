import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getPublicConfig } from '../api/config'
import { joinUrl, resolveBaseUrl } from '../utils/baseUrl'

/** 兜底名称。后端读不到时用它，保证界面上永远不会是空白标题 */
const FALLBACK_SYSTEM_NAME = '设备管理系统'

/**
 * 主题在 localStorage 里的键名。
 *
 * <p>⚠️ 改这里必须同步改 index.html 里那段内联脚本 —— 它在样式生效前
 * 就把 <html> 的 dark 类定好了，用来避免刷新时闪一帧亮色。
 */
const THEME_KEY = 'dms-theme'

/**
 * 侧栏收起偏好在 localStorage 里的键名。
 *
 * <p>和主题不同，它**不需要**在 index.html 里提前处理：store 是在
 * `app.mount()` 之前创建的，首帧渲染时 `railCollapsed` 已经是正确的值，
 * 不会出现"先展开再收起"的闪动。
 */
const RAIL_KEY = 'dms-rail-collapsed'

/**
 * 窄屏断点。
 *
 * <p>⚠️ 这个值必须和 AppRail.vue / App.vue 的 `@media` 保持一致 ——
 * CSS 里读不到 JS 常量，改这里就得同步改那两处。
 *
 * <p>断点的语义是"侧栏该收成图标条了"，不只是"屏幕变窄"：
 * 侧栏占 232px 时，低于这个宽度工作区会被挤得放不下表格。
 */
export const NARROW_QUERY = '(max-width: 900px)'

/**
 * 应用级信息（系统名称、企业名称、主题）。
 *
 * <p>名称这两项由后端的**系统参数**维护，管理员在「系统设置」里改完即时生效。
 * 它们必须在**未登录**时也能读到（登录页要显示），所以走的是免登录的
 * {@code /api/config/public}。
 *
 * <p>这个 store 可以放心 import api 模块 —— 不像 stores/user.ts 会被
 * request.ts 反向引用（那会成环），它处在依赖链的下游。
 */
export const useAppStore = defineStore('app', () => {
  const systemName = ref(FALLBACK_SYSTEM_NAME)
  const companyName = ref('')
  /** 管理员在「系统设置 → 系统信息」里填的对外访问地址。空串表示没配 */
  const baseUrl = ref('')
  const loaded = ref(false)

  const pageTitleSuffix = computed(() => systemName.value || FALLBACK_SYSTEM_NAME)

  /**
   * 系统**对外**的访问地址，用来拼二维码里的链接。
   *
   * <p>⚠️ 为什么不能直接用 `window.location.origin`：二维码是印出来给
   * **别的设备（手机）**扫的。在开发机上浏览器里是 `localhost:5173`，
   * 内网用 IP 打开是 `192.168.x.x`，两者对手机来说都不是"同一个地址" ——
   * 前者扫了必然打不开。
   *
   * <p>所以优先用管理员配的地址（那才是他自己确认过、别的设备能访问的），
   * 没配的时候才退回当前页面地址。**用当前地址时会打不通**，
   * 界面上检测到是本机地址会明确提示（见 DeviceDetailView 的资产标签）。
   */
  const publicBaseUrl = computed(() => resolveBaseUrl(baseUrl.value, window.location.origin))

  /** 拼好的那台设备档案页地址（印在资产标签的二维码里） */
  function deviceUrl(deviceId: number | string): string {
    return joinUrl(publicBaseUrl.value, `devices/${deviceId}`)
  }

  // ============================================================
  // 主题
  // ============================================================

  /**
   * 初始值从 <html> 上已有的 dark 类读，而不是重新读 localStorage。
   *
   * <p>因为 index.html 里的内联脚本已经在样式生效前做过一次判断
   * （localStorage → 系统偏好），这里读 DOM 能保证两处判断结果完全一致，
   * 不会出现"类是这个值、store 认为是那个值"的错位。
   */
  const isDark = ref(document.documentElement.classList.contains('dark'))

  /**
   * 切主题。
   *
   * <p>只需要操作 <html> 上的一个类：tokens.css 和 Element Plus 的暗色变量
   * 都以 html.dark 为选择器，类一加，两边同时生效。
   */
  function setTheme(dark: boolean) {
    isDark.value = dark
    document.documentElement.classList.toggle('dark', dark)
    try {
      localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
    } catch {
      // 隐私模式下 localStorage 可能不可用。存不上不影响本次切换，
      // 只是下次打开会退回系统偏好
    }
  }

  function toggleTheme() {
    setTheme(!isDark.value)
  }

  // ============================================================
  // 侧栏收起
  // ============================================================

  /** 读用户上次的选择。隐私模式下 localStorage 可能不可用，读不到就当没收起 */
  function readRailPref(): boolean {
    try {
      return localStorage.getItem(RAIL_KEY) === '1'
    } catch {
      return false
    }
  }

  /**
   * 初始值 = 用户的偏好 **或** 当前就是窄屏。
   *
   * <p>在这里就把窄屏判断做掉（而不是等 mounted 里再调一次 setRail），
   * 是为了让**首帧**画出来就是收起的图标条，不闪一下 232px 的展开态。
   */
  const railCollapsed = ref(readRailPref() || window.matchMedia(NARROW_QUERY).matches)

  /**
   * 设置收起状态。
   *
   * <p>`persist = false` 给"窄屏自动收起"用：那是一次**环境导致**的收起，
   * 不是用户的选择，不该写进偏好。否则用户在窄屏下待过一次，
   * 回到大屏后侧栏会莫名其妙地保持收起，而且他自己没点过任何按钮。
   */
  function setRail(collapsed: boolean, persist = true) {
    railCollapsed.value = collapsed
    if (!persist) return
    try {
      localStorage.setItem(RAIL_KEY, collapsed ? '1' : '0')
    } catch {
      // 存不上只是下次打开回到默认展开，不影响本次
    }
  }

  function toggleRail() {
    setRail(!railCollapsed.value)
  }

  /** 拉取展示类参数。失败不抛错，退回默认名称 */
  async function loadPublicConfig() {
    try {
      const config = await getPublicConfig()
      systemName.value = config?.['system.name'] || FALLBACK_SYSTEM_NAME
      companyName.value = config?.['system.company'] || ''
      baseUrl.value = config?.['system.base-url'] || ''
      loaded.value = true
    } catch {
      // 拿不到就用默认名称 —— 一个标题不该让页面出不来
    }
  }

  return {
    systemName,
    companyName,
    baseUrl,
    publicBaseUrl,
    deviceUrl,
    loaded,
    pageTitleSuffix,
    loadPublicConfig,
    isDark,
    setTheme,
    toggleTheme,
    railCollapsed,
    setRail,
    toggleRail,
  }
})
