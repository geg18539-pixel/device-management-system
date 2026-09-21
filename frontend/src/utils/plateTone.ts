/**
 * 状态铭牌的语义色。
 *
 * <p>单独放一个文件而不是写在 StatusPlate.vue 的 `<script>` 块里导出：
 * 页面要拿它标注自己的「业务状态 → tone」映射函数，从 SFC 里导入类型
 * 依赖编译器的处理，不如普通的 .ts 文件直接。
 *
 * <p>刻意不用 Element Plus 的 el-tag type 值（success/warning/danger/info）——
 * 那套是 EP 的语义划分，和这里"四个状态 + 一个信息色"对不上，
 * 中间加一层转换反而绕。
 */
export type PlateTone = 'ok' | 'warn' | 'crit' | 'idle' | 'info'
