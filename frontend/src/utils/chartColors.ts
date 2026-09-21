/**
 * 图表配色
 * ============================================================
 * ⚠️ 为什么这里是硬编码色值，而不是直接引用 tokens.css 的 CSS 变量：
 * ECharts 把图形画在 <canvas> 上，不经过 CSS 引擎，读不到 var(--xxx)。
 * 所以这里的两套值是 tokens 的**镜像**，改 tokens.css 时要同步改这里。
 *
 * 为什么要分两套：状态色是针对各自底色分别调过的（亮色的 ok 是 #16794a，
 * 暗色是 #45a86e）。只挑一个中间值的话，要么在浅底上发灰、要么在深底上发暗。
 * 好在 EChart.vue 本来就 watch 了 option 并 deep 重绘，所以只要页面的
 * option 是 computed 且读了这个函数，切换主题时图表会自动跟着重画。
 *
 * 用法：
 *   const appStore = useAppStore()
 *   const P = computed(() => chartPalette(appStore.isDark))
 *   // 在 computed 里用 P.value.ok
 */

export interface ChartPalette {
  /** 正常 / 在线 / 已完成 */
  ok: string
  /** 警告 / 待处理 / 维修中 */
  warn: string
  /** 故障 / 报废 */
  crit: string
  /** 停用 / 已关闭 / 分组标题 —— 也是字典里查不到时的兜底色 */
  idle: string
  /** 主色。单系列柱状图用它 */
  primary: string
  /** 主色的另一档。两个柱状图并排时用它拉开层次 */
  primarySoft: string
  /** 饼图扇区之间的描边色。取面板底色，让扇区看起来是断开的 */
  separator: string
  /** 画在 canvas 上的文字（节点标签、坐标轴文字）。对应 tokens 的 --ink-1 */
  ink: string
  /**
   * 关系图的连线。
   *
   * <p>⚠️ 取的是 <b>--ink-4</b> 的值，不是 --line-strong。这是算出来的，不是偏好：
   * --line-strong 压 --surface 只有 **1.72:1**（亮）/ 1.69:1（暗），
   * 而连线是**结构性要素**不是装饰 —— 没有它这张图就读不出关系，
   * 按 WCAG 1.4.11 对图形对象的要求要过 3:1。--ink-4 压 --surface 是 3.11:1，刚好达标。
   */
  edge: string
}

/** 亮色主题。值和 tokens.css 的 :root 一致 */
const LIGHT: ChartPalette = {
  ok: '#16794a',
  warn: '#8f6216',
  crit: '#b32318',
  idle: '#5e6975',
  primary: '#0d758e',
  primarySoft: '#569eb0',
  separator: '#ffffff',
  ink: '#16212a',
  edge: '#8594a1',
}

/** 暗色主题。值和 tokens.css 的 html.dark 一致 */
const DARK: ChartPalette = {
  ok: '#45a86e',
  warn: '#d3a041',
  crit: '#e06756',
  idle: '#818f99',
  primary: '#37a9c5',
  primarySoft: '#73c3d6',
  separator: '#161e24',
  ink: '#e3e9ed',
  edge: '#5d6c76',
}

export function chartPalette(isDark: boolean): ChartPalette {
  return isDark ? DARK : LIGHT
}
