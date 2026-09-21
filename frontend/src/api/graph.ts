import request from '../utils/request'

/**
 * 设备关系图谱。对应后端 dto/DeviceGraphVO.java。
 *
 * ★ 后端给的是**语义**（tone），不是色值 —— 颜色分主题，
 *   亮色的"警告"是深琥珀、暗色是浅琥珀，而 ECharts 画在 canvas 上
 *   读不到 CSS 变量。所以色值由前端按当前主题从 chartPalette 里取。
 */

/** 节点语义色。既是后端 GraphNodeVO.tone 的取值，也是 chartPalette 的键名 */
export type GraphTone = 'primary' | 'primarySoft' | 'ok' | 'warn' | 'crit' | 'idle'

/** 节点类型。决定画什么形状 —— 形状管类别，颜色管语义 */
export type GraphNodeType = 'device' | 'dept' | 'category' | 'repair' | 'maintenance' | 'part'

export interface GraphNode {
  /** 全局唯一，形如 device:1 repair:7 */
  id: string
  /** 节点上显示的文字 */
  name: string
  nodeType: GraphNodeType
  /** 类型的中文名，图例和悬停提示用 */
  typeLabel: string
  /** 第二行小字，可能是 undefined */
  subtitle?: string
  tone: GraphTone
  /** 是不是当前正在看的那台设备。前端会画得更大 */
  center: boolean
  /** 点击后跳到哪。undefined 表示不可点 */
  routeType?: string
  routeId?: number
}

export interface GraphEdge {
  source: string
  target: string
  label: string
}

export interface DeviceGraph {
  deviceId: number
  deviceName: string
  nodes: GraphNode[]
  edges: GraphEdge[]
  /** 这台设备一共多少张工单 / 图上画了几张 */
  repairTotal: number
  repairShown: number
  partTotal: number
  partShown: number
  /** 同部门一共多少台 / 图上画了几台 */
  siblingTotal: number
  siblingShown: number
  /** 被截断时的说明，空串表示没有截断 */
  note: string
}

/** 取某台设备的关系图 */
export function getDeviceGraph(deviceId: number) {
  return request.get<DeviceGraph>(`/devices/${deviceId}/graph`)
}

// ---------------- 形状映射 ----------------

/**
 * 节点类型 → ECharts 的 symbol。
 *
 * ⚠️ 用**形状**区分类型、用**颜色**区分语义，是这张图能读懂的关键。
 * 六种节点如果只靠颜色，就得凑六种互不相近的颜色，而且颜色一多
 * 就没有哪一种是"重要"的了；分开之后，颜色只用在真正有信息量的地方
 * （工单是否完结、配件是否缺货），其余节点形状不同就够了。
 */
export const NODE_SYMBOL: Record<GraphNodeType, string> = {
  device: 'circle',
  dept: 'triangle',
  category: 'rect',
  repair: 'roundRect',
  maintenance: 'pin',
  part: 'diamond',
}
