<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import type { EChartsOption } from 'echarts'
import {
  AI_STATUS,
  LOG_TYPE,
  REPAIR_STATUS,
  REPAIR_STATUS_OPTIONS,
  acceptRepair,
  addRepairLog,
  assignRepair,
  closeRepair,
  deleteRepair,
  downloadRepairAttachment,
  exportRepairs,
  fetchRepairAttachmentBlob,
  finishRepair,
  getRepairAttachments,
  getRepairById,
  getRepairLogs,
  getRepairPage,
  getRepairStats,
  isPendingStatus,
  isTerminalStatus,
  normalizeRepairStatus,
  reanalyzeRepair,
  splitLines,
  uploadRepairAttachment,
  deleteRepairAttachment,
  type DeviceRepair,
  type DeviceRepairLog,
  type RepairAttachment,
  type RepairStats,
} from '../api/deviceRepair'
import { getRecordsByRepair, type SparePartRecord } from '../api/sparePart'
import { DICT_TYPE, useDict } from '../api/dict'
import { buildUserNameMap, getUserOptions, type UserOption } from '../api/userOption'
import DataPanel from '../components/DataPanel.vue'
import EChart from '../components/EChart.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { chartPalette } from '../utils/chartColors'
import { useAppStore } from '../stores/app'
import { usePerm } from '../composables/usePerm'
import type { PlateTone } from '../utils/plateTone'

const { hasPerm } = usePerm()
const appStore = useAppStore()
/** 当前主题下的图表配色。切主题时 computed 重算，图表跟着重绘 */
const palette = computed(() => chartPalette(appStore.isDark))

/** 故障类型下拉与文案转换都走字典，不在前端硬编码 */
const { options: faultTypeOptions, labelOf: faultTypeLabel } = useDict(DICT_TYPE.FAULT_TYPE)

/**
 * 用户选项：既给"指派维修人"的下拉用，也用来把表格里的用户名显示成人名。
 *
 * <p>工单里存的是**用户名**（如 wangqiang），列表里直接显示用户名很难看，
 * 所以拉一次用户列表建立映射。历史数据里手工填的姓名（外部维修工）
 * 映射不到，会退回原值显示 —— 比显示空白好。
 */
const userOptions = ref<UserOption[]>([])
const userNameMap = computed(() => buildUserNameMap(userOptions.value))

/** 列表里显示维修人：优先人名，映射不到就显示原值 */
function displayUser(value?: string): string {
  if (!value) return '—'
  return userNameMap.value[value] ?? value
}

// ============================================================
// 统计看板
// ============================================================
const stats = ref<RepairStats | null>(null)

async function loadStats() {
  try {
    stats.value = await getRepairStats()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  }
}

// 状态分布图。配色和列表里的标签保持一致，扫一眼就对得上。
// 用 computed 而不是常量，这样主题切换时会重算
const STATUS_COLORS = computed<Record<string, string>>(() => {
  const p = palette.value
  return {
    [REPAIR_STATUS.PENDING]: p.warn,
    [REPAIR_STATUS.REPAIRING]: p.primary,
    [REPAIR_STATUS.FINISHED]: p.ok,
    [REPAIR_STATUS.CLOSED]: p.idle,
  }
})

const statusChartOption = computed<EChartsOption>(() => {
  const items = stats.value?.statusItems ?? []
  return {
    tooltip: { trigger: 'item', formatter: '{b}：{c} 单（{d}%）' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: palette.value.separator, borderWidth: 2 },
        label: { formatter: '{b} {c}' },
        data: items.map((i) => ({
          name: i.name,
          value: i.value,
          itemStyle: { color: STATUS_COLORS.value[i.name] ?? palette.value.idle },
        })),
      },
    ],
  }
})

const hasStatusData = computed(() => (stats.value?.statusItems?.length ?? 0) > 0)

// ============================================================
// 列表
// ============================================================
const loading = ref(false)
const repairList = ref<DeviceRepair[]>([])
const total = ref(0)
const exporting = ref(false)

/** 页头那行说明。合并了原来列表上方的 .summary */
const headDesc = computed(() => {
  const month = stats.value?.thisMonthCount ?? 0
  return `共 ${total.value} 张工单，本月新增 ${month} 张`
})

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  repairStatus: '',
  keyword: '',
  repairer: '',
  /** 故障类型筛选，传的是字典项的值 */
  faultType: '',
  pendingOnly: false,
})

/** 整理成接口参数，列表和导出共用（导出必须所见即所得） */
function currentParams() {
  return {
    repairStatus: query.repairStatus || undefined,
    keyword: query.keyword || undefined,
    repairer: query.repairer || undefined,
    faultType: query.faultType || undefined,
    pendingOnly: query.pendingOnly || undefined,
  }
}

async function loadList() {
  loading.value = true
  try {
    const page = await getRepairPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      ...currentParams(),
    })
    repairList.value = page.list
    total.value = page.total
  } catch {
    // 同上
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadList(), loadStats()])
}

function handleSearch() {
  query.pageNum = 1
  void loadList()
}

function handleReset() {
  query.repairStatus = ''
  query.keyword = ''
  query.repairer = ''
  query.faultType = ''
  query.pendingOnly = false
  handleSearch()
}

async function handleExport() {
  exporting.value = true
  try {
    await exportRepairs(currentParams())
    ElMessage.success('导出成功')
  } catch {
    // 同上
  } finally {
    exporting.value = false
  }
}

/** 工单状态的铭牌色调。用归一后的值判断，这样历史「待维修」也是黄色的待受理样式 */
function statusTone(status?: string): PlateTone {
  const s = normalizeRepairStatus(status)
  if (s === REPAIR_STATUS.PENDING) return 'warn'
  if (s === REPAIR_STATUS.REPAIRING) return 'info'
  if (s === REPAIR_STATUS.FINISHED) return 'ok'
  return 'idle'
}

/** AI 分析状态的色调 */
function aiStatusTone(status?: string): PlateTone {
  if (status === AI_STATUS.RUNNING) return 'warn'
  if (status === AI_STATUS.DONE) return 'ok'
  if (status === AI_STATUS.FAILED) return 'crit'
  return 'idle'
}

/** AI 判断的故障严重程度的色调 */
function severityTone(severity?: string): PlateTone {
  if (severity === '高') return 'crit'
  if (severity === '中') return 'warn'
  return 'idle'
}

/** 维修日志类型的色调 */
function logTone(logType?: string): PlateTone {
  if (logType === LOG_TYPE.STATUS) return 'ok'
  if (logType === LOG_TYPE.CREATED) return 'idle'
  return 'info'
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

// 按行状态决定显示哪些操作，避免点了必然报错
function canAccept(row: DeviceRepair): boolean {
  return isPendingStatus(row.repairStatus)
}

function canAssign(row: DeviceRepair): boolean {
  return !isTerminalStatus(row.repairStatus)
}

function canFinish(row: DeviceRepair): boolean {
  return normalizeRepairStatus(row.repairStatus) === REPAIR_STATUS.REPAIRING
}

/** 维修中不能直接关闭（后端会拒），所以这里的按钮就不给它 */
function canClose(row: DeviceRepair): boolean {
  const s = normalizeRepairStatus(row.repairStatus)
  return s === REPAIR_STATUS.PENDING || s === REPAIR_STATUS.FINISHED
}

function canDelete(row: DeviceRepair): boolean {
  return isTerminalStatus(row.repairStatus)
}

/** 下拉命令分发。用返回闭包的写法，模板里不能写带类型标注的内联箭头 */
function rowCommandHandler(row: DeviceRepair) {
  return (command: string): void => {
    if (command === 'accept') void handleAccept(row)
    else if (command === 'assign') void openAssign(row)
    else if (command === 'finish') void openFinish(row)
    else if (command === 'close') void openClose(row)
    else if (command === 'detail') void openDetail(row)
    else if (command === 'delete') void handleDelete(row)
  }
}

// ============================================================
// 受理
// ============================================================
const busyId = ref<number | null>(null)

async function handleAccept(row: DeviceRepair) {
  busyId.value = row.id
  try {
    await acceptRepair(row.id)
    ElMessage.success('工单已受理')
    await refreshAll()
    if (drawerVisible.value) await reloadDetail()
  } catch {
    // 同上
  } finally {
    busyId.value = null
  }
}

// ============================================================
// 指派
// ============================================================
const assignVisible = ref(false)
const assignSubmitting = ref(false)
const assignTarget = ref<DeviceRepair | null>(null)
const assignFormRef = ref<FormInstance>()
const assignForm = reactive({ repairer: '' })

/**
 * 维修人下拉。
 *
 * <p>**可选系统用户，也允许手填**（`allow-create`）——
 * 企业里经常会临时找外部维修工，逼着只能选系统账号反而不好用。
 *
 * <p>但要注意：手填的名字在系统里没有账号，**站内通知发不出去**。
 * 下拉里选的（value 是用户名）才能收到通知，所以表单上要提示这一点。
 */

async function loadUserOptions() {
  try {
    userOptions.value = await getUserOptions()
  } catch {
    // 拿不到用户列表时下拉为空，仍然可以手填 —— 不阻塞指派
  }
}

const assignRules: FormRules<{ repairer: string }> = {
  repairer: [
    { required: true, message: '请输入维修人员', trigger: 'blur' },
    { max: 50, message: '维修人员不能超过 50 个字符', trigger: 'blur' },
  ],
}

async function openAssign(row: DeviceRepair) {
  assignTarget.value = row
  assignForm.repairer = row.repairer ?? ''
  assignVisible.value = true
  await nextTick()
  assignFormRef.value?.clearValidate()
}

async function submitAssign() {
  if (!assignTarget.value || !assignFormRef.value) return
  const valid = await assignFormRef.value.validate().catch(() => false)
  if (!valid) return

  assignSubmitting.value = true
  try {
    await assignRepair(assignTarget.value.id, assignForm.repairer)
    ElMessage.success('指派成功')
    assignVisible.value = false
    await refreshAll()
    if (drawerVisible.value) await reloadDetail()
  } catch {
    // 同上
  } finally {
    assignSubmitting.value = false
  }
}

// ============================================================
// 完工
// ============================================================
const finishVisible = ref(false)
const finishSubmitting = ref(false)
const finishTarget = ref<DeviceRepair | null>(null)
const finishFormRef = ref<FormInstance>()
const finishForm = reactive({
  repairer: '',
  repairResult: '',
  cost: undefined as number | undefined,
  remark: '',
})

const finishRules: FormRules<typeof finishForm> = {
  repairResult: [
    { required: true, message: '请填写维修结果', trigger: 'blur' },
    { max: 2000, message: '维修结果不能超过 2000 个字符', trigger: 'blur' },
  ],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }],
}

async function openFinish(row: DeviceRepair) {
  finishTarget.value = row
  // 已指派过维修人的话带出来，没指派就留空（后端会用当前登录用户兜底）
  finishForm.repairer = row.repairer ?? ''
  finishForm.repairResult = ''
  finishForm.cost = undefined
  finishForm.remark = ''
  finishVisible.value = true
  await nextTick()
  finishFormRef.value?.clearValidate()
}

async function submitFinish() {
  if (!finishTarget.value || !finishFormRef.value) return
  const valid = await finishFormRef.value.validate().catch(() => false)
  if (!valid) return

  finishSubmitting.value = true
  try {
    await finishRepair(finishTarget.value.id, {
      repairer: finishForm.repairer || undefined,
      repairResult: finishForm.repairResult,
      cost: finishForm.cost,
      remark: finishForm.remark || undefined,
    })
    ElMessage.success('工单已完成，设备状态已改回「在线」')
    finishVisible.value = false
    await refreshAll()
    if (drawerVisible.value) await reloadDetail()
  } catch {
    // 同上
  } finally {
    finishSubmitting.value = false
  }
}

// ============================================================
// 关闭
// ============================================================
const closeVisible = ref(false)
const closeSubmitting = ref(false)
const closeTarget = ref<DeviceRepair | null>(null)
const closeFormRef = ref<FormInstance>()
const closeForm = reactive({ reason: '' })

const closeRules: FormRules<{ reason: string }> = {
  reason: [
    { required: true, message: '请填写关闭原因', trigger: 'blur' },
    { max: 200, message: '关闭原因不能超过 200 个字符', trigger: 'blur' },
  ],
}

async function openClose(row: DeviceRepair) {
  closeTarget.value = row
  closeForm.reason = ''
  closeVisible.value = true
  await nextTick()
  closeFormRef.value?.clearValidate()
}

async function submitClose() {
  if (!closeTarget.value || !closeFormRef.value) return
  const valid = await closeFormRef.value.validate().catch(() => false)
  if (!valid) return

  closeSubmitting.value = true
  try {
    await closeRepair(closeTarget.value.id, closeForm.reason)
    ElMessage.success('工单已关闭')
    closeVisible.value = false
    await refreshAll()
    if (drawerVisible.value) await reloadDetail()
  } catch {
    // 同上
  } finally {
    closeSubmitting.value = false
  }
}

// ============================================================
// 删除
// ============================================================
async function handleDelete(row: DeviceRepair) {
  try {
    await ElMessageBox.confirm(`确定要删除工单 #${row.id} 吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteRepair(row.id)
    ElMessage.success('删除成功')
    if (repairList.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await refreshAll()
  } catch {
    // 同上
  }
}

// ============================================================
// 详情抽屉
// ============================================================
const drawerVisible = ref(false)
const detail = ref<DeviceRepair | null>(null)
const logs = ref<DeviceRepairLog[]>([])
const logLoading = ref(false)
const newLog = ref('')
const addingLog = ref(false)
const reanalyzing = ref(false)

/** 该工单消耗的配件 */
const partRecords = ref<SparePartRecord[]>([])
/** 维修照片 */
const photos = ref<RepairAttachment[]>([])

let pollTimer: number | null = null

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function reloadDetail() {
  if (!detail.value?.id) return
  const id = detail.value.id
  try {
    // 抽屉里那份是列表数据的副本，状态流转和 AI 分析都是别处改的，
    // 必须重新按 id 拉一次才能看到最新状态
    detail.value = await getRepairById(id)
    logs.value = await getRepairLogs(id)
    partRecords.value = await getRecordsByRepair(id)
    photos.value = await getRepairAttachments(id)
    startPollingIfNeeded()
  } catch {
    // 拉取失败就保持现状
  }
}

/** AI 分析中时每 3 秒拉一次，出结果就停 */
function startPollingIfNeeded() {
  stopPolling()
  if (detail.value?.aiStatus !== AI_STATUS.RUNNING) return

  pollTimer = window.setInterval(async () => {
    if (!detail.value?.id) return
    try {
      detail.value = await getRepairById(detail.value.id)
    } catch {
      return
    }
    void loadList()
    if (detail.value?.aiStatus !== AI_STATUS.RUNNING) {
      stopPolling()
      if (detail.value?.aiStatus === AI_STATUS.DONE) {
        ElMessage.success('AI 分析已完成')
      }
    }
  }, 3000)
}

async function openDetail(row: DeviceRepair) {
  detail.value = row
  logs.value = []
  partRecords.value = []
  photos.value = []
  newLog.value = ''
  drawerVisible.value = true
  await reloadDetail()
}

function closeDrawer() {
  stopPolling()
  drawerVisible.value = false
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

async function submitLog() {
  if (!detail.value?.id) return
  const content = newLog.value.trim()
  if (!content) {
    ElMessage.warning('请先输入内容')
    return
  }
  addingLog.value = true
  try {
    await addRepairLog(detail.value.id, content)
    newLog.value = ''
    ElMessage.success('已记录')
    logs.value = await getRepairLogs(detail.value.id)
  } catch {
    // 同上
  } finally {
    addingLog.value = false
  }
}

async function handleReanalyze() {
  if (!detail.value?.id) return
  reanalyzing.value = true
  try {
    await reanalyzeRepair(detail.value.id)
    ElMessage.success('已提交分析任务，稍等片刻')
    detail.value = { ...detail.value, aiStatus: AI_STATUS.RUNNING, aiError: undefined }
    startPollingIfNeeded()
  } catch {
    // 同上
  } finally {
    reanalyzing.value = false
  }
}

const aiCauses = computed(() => splitLines(detail.value?.aiPossibleCauses))
const aiSteps = computed(() => splitLines(detail.value?.aiSuggestion))

// ============================================================
// 维修照片
// ============================================================
const ALLOWED_EXT = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'pdf']
const MAX_SIZE_MB = 10

/** 上传前的本地校验。**不是安全边界** —— 后端仍会独立校验 */
function beforeUpload(file: File): boolean {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXT.includes(ext)) {
    ElMessage.error(`不支持的文件类型：.${ext}`)
    return false
  }
  if (file.size > MAX_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`文件大小超过限制（最大 ${MAX_SIZE_MB} MB）`)
    return false
  }
  return true
}

/** 走自定义上传是为了用我们自己的 axios —— 才能带上 Authorization 头 */
async function handleUpload(options: UploadRequestOptions) {
  if (!detail.value?.id) return
  try {
    await uploadRepairAttachment(detail.value.id, options.file as File)
    ElMessage.success('上传成功')
    photos.value = await getRepairAttachments(detail.value.id)
  } catch {
    // 同上
  }
}

const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

async function handlePreview(att: RepairAttachment) {
  if (!att.contentType?.startsWith('image/')) {
    await handleDownload(att)
    return
  }
  try {
    const blob = await fetchRepairAttachmentBlob(att.id, true)
    // 换新图前先释放旧的，否则每预览一张就泄漏一个 blob
    if (previewUrl.value) window.URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = window.URL.createObjectURL(blob)
    previewName.value = att.fileName
    previewVisible.value = true
  } catch {
    // 同上
  }
}

async function handleDownload(att: RepairAttachment) {
  try {
    await downloadRepairAttachment(att.id, att.fileName)
  } catch {
    // 同上
  }
}

async function handleDeletePhoto(att: RepairAttachment) {
  try {
    await ElMessageBox.confirm(`确定要删除「${att.fileName}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteRepairAttachment(att.id)
    ElMessage.success('删除成功')
    if (detail.value?.id) photos.value = await getRepairAttachments(detail.value.id)
  } catch {
    // 同上
  }
}

function formatSize(bytes?: number): string {
  if (bytes === undefined || bytes === null) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

onMounted(async () => {
  // 用户选项和首页数据并行加载：它是给指派下拉和"维修人"列的显示名用的
  await Promise.all([refreshAll(), loadUserOptions()])
})
onBeforeUnmount(() => {
  stopPolling()
  if (previewUrl.value) window.URL.revokeObjectURL(previewUrl.value)
})
</script>

<template>
  <div class="page">
    <PageHeader title="维修工单" :desc="headDesc">
      <template #actions>
        <el-button
          v-if="hasPerm('dev:repair:export')"
          :loading="exporting"
          @click="handleExport"
        >
          导出 Excel
        </el-button>
      </template>
    </PageHeader>
    <!-- ---------- 统计看板 ----------
         原来是 6 个等宽卡片排成 3×2（总数/待处理/已完成/已关闭/完成率/平均时长），
         六个数一样大，等于没有重点。现在收成一张主卡：一个大数字 + 完成率
         + 底部两格，把"要动手的"和"读数"分开。 -->
    <div class="stats-row">
      <div class="hero">
        <div class="hero-label">工单总数</div>
        <div class="hero-figure">
          <span class="hero-value num">{{ stats?.total ?? 0 }}</span>
          <span class="hero-unit">单</span>
        </div>

        <!-- 主卡里唯一的比率。用进度条表达比再放一个数字更直观，
             分母口径直接写在下面，省得看的人猜 -->
        <div class="hero-rate">
          <div class="rate-head">
            <span class="rate-label">完成率</span>
            <b class="num">{{ stats?.completionRate ?? 0 }}%</b>
          </div>
          <el-progress
            :percentage="stats?.completionRate ?? 0"
            :stroke-width="8"
            :show-text="false"
          />
          <div class="rate-hint">
            已完成 {{ stats?.finished ?? 0 }} + 已关闭 {{ stats?.closed ?? 0 }}，共
            {{ stats?.total ?? 0 }} 单
          </div>
        </div>

        <!-- 底部两格：左边是要动手的，右边是质量读数 -->
        <div class="hero-foot">
          <div class="foot-cell">
            <div class="foot-label">待处理</div>
            <div
              class="foot-value num"
              :class="(stats?.pendingCount ?? 0) > 0 ? 'warn' : 'is-zero'"
            >
              {{ stats?.pendingCount ?? 0 }}<span class="unit">单</span>
            </div>
            <div class="foot-note">
              待受理 {{ stats?.pending ?? 0 }}、维修中 {{ stats?.repairing ?? 0 }}
            </div>
          </div>

          <div class="foot-cell">
            <div class="foot-label">平均维修时长</div>
            <div class="foot-value num" :class="{ 'is-zero': !stats?.avgRepairHours }">
              {{ stats?.avgRepairHours ?? '—'
              }}<span v-if="stats?.avgRepairHours" class="unit">h</span>
            </div>
            <div class="foot-note">
              <template v-if="stats?.avgSampleSize">
                近 90 天 {{ stats.avgSampleSize }} 个样本
              </template>
              <template v-else>暂无样本</template>
            </div>
          </div>
        </div>
      </div>

      <DataPanel title="工单状态分布">
        <EChart v-if="hasStatusData" :option="statusChartOption" height="210px" />
        <EmptyState v-else title="还没有工单" desc="报修之后这里会按状态显示分布" />
      </DataPanel>
    </div>

    <!-- ---------- 筛选 + 列表 ---------- -->
    <DataPanel flush>
      <div class="filter-bar">
        <div class="filters">
        <el-select
          v-model="query.repairStatus"
          placeholder="全部状态"
          clearable
          style="width: 130px"
          @change="handleSearch"
        >
          <el-option
            v-for="opt in REPAIR_STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-checkbox v-model="query.pendingOnly" @change="handleSearch">只看待处理</el-checkbox>
        <!-- 故障类型的选项来自字典，不在前端写死 -->
        <el-select
          v-model="query.faultType"
          placeholder="全部故障类型"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option
            v-for="opt in faultTypeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="设备名 / 故障描述 / 报修人"
          clearable
          style="width: 210px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="query.repairer"
          placeholder="维修人"
          clearable
          style="width: 120px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="repairList">
        <el-table-column prop="id" label="工单号" width="80" />
        <el-table-column prop="deviceName" label="设备" min-width="130" show-overflow-tooltip />
        <el-table-column prop="faultDesc" label="故障描述" min-width="170" show-overflow-tooltip />

        <el-table-column label="故障类型" width="110">
          <template #default="{ row }">
            <!-- 用字典把存进库的值转成文案；字典里查不到就退回原值 -->
            <span :class="{ muted: !row.faultType }">{{ faultTypeLabel(row.faultType) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="92">
          <template #default="{ row }">
            <!-- 用归一后的值显示：历史「待维修」也显示成「待受理」 -->
            <StatusPlate :tone="statusTone(row.repairStatus)">
              {{ normalizeRepairStatus(row.repairStatus) }}
            </StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="AI 分析" width="90">
          <template #default="{ row }">
            <StatusPlate :tone="aiStatusTone(row.aiStatus)">
              {{ row.aiStatus || '—' }}
            </StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="严重程度" width="85">
          <template #default="{ row }">
            <StatusPlate v-if="row.aiSeverity" :tone="severityTone(row.aiSeverity)">
              {{ row.aiSeverity }}
            </StatusPlate>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="reporter" label="报修人" width="90">
          <template #default="{ row }">
            <span :class="{ muted: !row.reporter }">{{ row.reporter || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="repairer" label="维修人" width="90">
          <template #default="{ row }">
            <!-- 库里存的是用户名，显示成人名（映射不到就退回原值） -->
            <span :class="{ muted: !row.repairer }">{{ displayUser(row.repairer) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="报修时间" width="160">
          <template #default="{ row }">{{ formatTime(row.reportTime) }}</template>
        </el-table-column>

        <el-table-column label="费用" width="90">
          <template #default="{ row }">{{ row.cost != null ? '¥' + row.cost : '—' }}</template>
        </el-table-column>

        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="(canAccept(row)) && hasPerm('dev:repair:accept')"
              link
              type="warning"
              :loading="busyId === row.id"
              @click="handleAccept(row)"
            >
              受理
            </el-button>
            <el-dropdown trigger="click" @command="rowCommandHandler(row)">
              <el-button link type="primary">更多<span class="caret">▾</span></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="(canAssign(row)) && hasPerm('dev:repair:assign')" command="assign">
                    指派维修人员
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canFinish(row)) && hasPerm('dev:repair:finish')" command="finish">
                    完工
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canClose(row)) && hasPerm('dev:repair:close')" command="close">
                    关闭工单
                  </el-dropdown-item>
                  <el-dropdown-item command="detail" divided>查看详情</el-dropdown-item>
                  <el-dropdown-item v-if="(canDelete(row)) && hasPerm('dev:repair:remove')" command="delete" divided>
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="没有符合条件的工单" desc="换个筛选条件试试，或者去设备页发起报修" />
        </template>
      </el-table>

      <div class="table-pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="loadList"
        />
      </div>
    </DataPanel>

    <!-- ---------- 指派 ---------- -->
    <el-dialog v-model="assignVisible" :title="`指派维修人员 —— 工单 #${assignTarget?.id ?? ''}`" width="440px">
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="90px">
        <el-form-item label="维修人员" prop="repairer">
          <el-select
            v-model="assignForm.repairer"
            placeholder="选系统用户，或直接输入外部人员姓名"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option
              v-for="opt in userOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="tip">
        改派也会留一条日志，能看到工单从谁手上转给了谁。<br />
        <strong>从下拉里选的系统用户会收到站内通知</strong>；
        直接手输的外部人员没有系统账号，收不到通知。
      </p>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- ---------- 完工 ---------- -->
    <el-dialog
      v-model="finishVisible"
      :title="`工单完工 —— ${finishTarget?.deviceName ?? ''}`"
      width="520px"
    >
      <el-form ref="finishFormRef" :model="finishForm" :rules="finishRules" label-width="90px">
        <el-form-item label="维修人">
          <el-input v-model="finishForm.repairer" placeholder="留空则用已指派的或当前登录用户" maxlength="50" />
        </el-form-item>
        <el-form-item label="维修结果" prop="repairResult">
          <el-input
            v-model="finishForm.repairResult"
            type="textarea"
            :rows="4"
            placeholder="具体做了什么、修好没有、更换了哪些件"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="维修费用">
          <el-input-number v-model="finishForm.cost" :min="0" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="finishForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <p class="tip">完工后设备状态会自动从「维修中」改回「在线」，并写入一条状态变更日志。</p>
      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" :loading="finishSubmitting" @click="submitFinish">确定</el-button>
      </template>
    </el-dialog>

    <!-- ---------- 关闭 ---------- -->
    <el-dialog v-model="closeVisible" :title="`关闭工单 #${closeTarget?.id ?? ''}`" width="460px">
      <el-form ref="closeFormRef" :model="closeForm" :rules="closeRules" label-width="90px">
        <el-form-item label="关闭原因" prop="reason">
          <el-input
            v-model="closeForm.reason"
            type="textarea"
            :rows="3"
            placeholder="如：误报，设备实际正常 / 已确认恢复，归档"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <p class="tip">
        从「待受理」关闭 = 误报作废，设备状态会被改回「在线」；
        从「已完成」关闭 = 归档。维修中的工单不能直接关闭，需要先完工。
      </p>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="closeSubmitting" @click="submitClose">确定关闭</el-button>
      </template>
    </el-dialog>

    <!-- ---------- 详情抽屉 ---------- -->
    <el-drawer
      v-model="drawerVisible"
      :title="`工单 #${detail?.id ?? ''} —— ${detail?.deviceName ?? ''}`"
      size="680px"
      @close="closeDrawer"
    >
      <div v-if="detail" class="detail">
        <!-- 顶部操作条 -->
        <div class="action-bar">
          <StatusPlate :tone="statusTone(detail.repairStatus)">
            {{ normalizeRepairStatus(detail.repairStatus) }}
          </StatusPlate>
          <div class="actions">
            <el-button v-if="(canAccept(detail)) && hasPerm('dev:repair:accept')" type="warning" size="small" @click="handleAccept(detail)">
              受理
            </el-button>
            <el-button v-if="(canAssign(detail)) && hasPerm('dev:repair:assign')" size="small" @click="openAssign(detail)">
              指派
            </el-button>
            <el-button v-if="(canFinish(detail)) && hasPerm('dev:repair:finish')" type="primary" size="small" @click="openFinish(detail)">
              完工
            </el-button>
            <el-button v-if="(canClose(detail)) && hasPerm('dev:repair:close')" size="small" @click="openClose(detail)">
              关闭
            </el-button>
          </div>
        </div>

        <!-- 基本信息 -->
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报修人">{{ detail.reporter || '—' }}</el-descriptions-item>
          <el-descriptions-item label="维修人">{{ displayUser(detail.repairer) }}</el-descriptions-item>
          <el-descriptions-item label="报修时间">{{ formatTime(detail.reportTime) }}</el-descriptions-item>
          <el-descriptions-item label="指派时间">{{ formatTime(detail.assignTime) }}</el-descriptions-item>
          <el-descriptions-item label="受理时间">{{ formatTime(detail.acceptTime) }}</el-descriptions-item>
          <el-descriptions-item label="完工时间">{{ formatTime(detail.finishTime) }}</el-descriptions-item>
          <el-descriptions-item label="关闭时间">{{ formatTime(detail.closeTime) }}</el-descriptions-item>
          <el-descriptions-item label="维修费用">
            {{ detail.cost != null ? '¥ ' + detail.cost : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="故障描述" :span="2">{{ detail.faultDesc }}</el-descriptions-item>
          <el-descriptions-item label="故障类型" :span="2">
            {{ faultTypeLabel(detail.faultType) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="2">
            {{ detail.closeReason }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.repairResult" label="维修结果" :span="2">
            <span class="pre-wrap">{{ detail.repairResult }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="2">
            {{ detail.remark }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 消耗配件 -->
        <div class="section">
          <div class="section-head">
            <h3>消耗配件</h3>
            <span class="muted">{{ partRecords.length }} 项</span>
          </div>
          <el-table v-if="partRecords.length" :data="partRecords" border size="small">
            <el-table-column prop="partCode" label="编码" width="110" />
            <el-table-column prop="partName" label="名称" min-width="130" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="70" />
            <el-table-column label="单价" width="90">
              <template #default="{ row }">{{ row.unitPrice ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="领料人" width="90" />
          </el-table>
          <EmptyState v-else title="还没有消耗配件" desc="在配件页出库时关联本工单，这里就会有记录" />
        </div>

        <!-- 维修照片 -->
        <div class="section">
          <div class="section-head">
            <h3>维修照片 / 附件</h3>
            <el-upload v-if="hasPerm('dev:attachment:upload')"
              :show-file-list="false"
              :http-request="handleUpload"
              :before-upload="beforeUpload"
            >
              <el-button size="small" type="primary">上传</el-button>
            </el-upload>
          </div>
          <div v-if="photos.length" class="photo-list">
            <div v-for="p in photos" :key="p.id" class="photo-item">
              <div class="photo-name" :title="p.fileName">{{ p.fileName }}</div>
              <div class="photo-meta">{{ formatSize(p.fileSize) }}｜{{ p.uploader }}</div>
              <div class="photo-actions">
                <el-button link type="primary" size="small" @click="handlePreview(p)">预览/下载</el-button>
                <el-button v-if="hasPerm('dev:attachment:remove')" link type="danger" size="small" @click="handleDeletePhoto(p)">删除</el-button>
              </div>
            </div>
          </div>
          <EmptyState v-else title="还没有上传照片" desc="支持图片和 PDF，用来留现场证据" />
        </div>

        <!-- AI 分析 -->
        <div class="section">
          <div class="section-head">
            <h3>AI 智能分析</h3>
            <div class="head-right">
              <StatusPlate :tone="aiStatusTone(detail.aiStatus)">
                {{ detail.aiStatus || '未分析' }}
              </StatusPlate>
              <el-button
                size="small"
                :loading="reanalyzing"
                :disabled="detail.aiStatus === AI_STATUS.RUNNING"
                @click="handleReanalyze"
              >
                {{ detail.aiStatus === AI_STATUS.DONE ? '重新分析' : '开始分析' }}
              </el-button>
            </div>
          </div>

          <el-alert v-if="detail.aiStatus === AI_STATUS.RUNNING" type="warning" :closable="false" show-icon>
            正在调用本地模型分析故障描述，通常需要十几秒到一分钟。页面会自动刷新结果。
          </el-alert>

          <el-alert v-else-if="detail.aiStatus === AI_STATUS.FAILED" type="error" :closable="false" show-icon>
            <template #title>分析失败</template>
            <div class="fail-body">
              {{ detail.aiError || '模型未返回可用结果' }}
              <p class="fail-tip">
                工单本身不受影响，可以正常维修和完工。确认 Ollama 已启动后点「开始分析」重试。
              </p>
            </div>
          </el-alert>

          <el-alert
            v-else-if="!detail.aiStatus || detail.aiStatus === AI_STATUS.PENDING"
            type="info"
            :closable="false"
            show-icon
          >
            这张工单还没有分析结果（可能是引入 AI 之前创建的）。点「开始分析」生成维修建议。
          </el-alert>

          <template v-else-if="detail.aiStatus === AI_STATUS.DONE">
            <div class="ai-meta">
              <span class="meta-item">
                严重程度
                <StatusPlate :tone="severityTone(detail.aiSeverity)">
                  {{ detail.aiSeverity || '—' }}
                </StatusPlate>
              </span>
              <span class="meta-item">预计工时 <strong>{{ detail.aiEstimatedHours ?? '—' }}</strong> 小时</span>
              <span class="meta-item muted">{{ detail.aiModel }}｜{{ formatTime(detail.aiAnalyzedAt) }}</span>
            </div>
            <div v-if="aiCauses.length" class="block">
              <h4>可能原因</h4>
              <ul class="list"><li v-for="(item, i) in aiCauses" :key="i">{{ item }}</li></ul>
            </div>
            <div v-if="aiSteps.length" class="block">
              <h4>建议维修步骤</h4>
              <ol class="list"><li v-for="(item, i) in aiSteps" :key="i">{{ item }}</li></ol>
            </div>
            <p class="disclaimer">以上为本地模型的自动建议，仅供参考，请结合实际现场情况判断。</p>
          </template>
        </div>

        <!-- 维修日志 -->
        <div class="section">
          <div class="section-head">
            <h3>维修日志</h3>
            <span class="muted">{{ logs.length }} 条</span>
          </div>
          <div v-loading="logLoading">
            <el-timeline v-if="logs.length">
              <el-timeline-item
                v-for="log in logs"
                :key="log.id"
                :timestamp="formatTime(log.logTime)"
                placement="top"
              >
                <div class="log-item">
                  <StatusPlate :tone="logTone(log.logType)">{{ log.logType }}</StatusPlate>
                  <span class="log-operator">{{ log.operator || '系统' }}</span>
                  <p class="log-content">{{ log.content }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
            <EmptyState v-else title="暂无日志" desc="受理、完工这些流转会自动记一条" />
          </div>

          <div class="log-editor">
            <el-input
              v-model="newLog"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="500"
              show-word-limit
              placeholder="记录本次维修的进展，比如：已更换电源模块，待观察"
            />
            <el-button type="primary" size="small" class="log-submit" :loading="addingLog" @click="submitLog">
              添加记录
            </el-button>
          </div>
          <p class="tip">日志只增不改：维修记录属于追溯性资料，记录人不填会自动取当前登录用户。</p>
        </div>
      </div>
    </el-drawer>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" :title="previewName" width="640px">
      <div class="preview-box">
        <img v-if="previewUrl" :src="previewUrl" alt="照片预览" class="preview-img" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* ---------- 统计区 ---------- */
.stats-row {
  display: grid;
  /* minmax(0, ...) 是必须的：默认的 min-width:auto 会让主卡里的
     长数字（或那句 rate-hint）把列撑破 */
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

/* 主卡。和看板、设备台账那两张主卡（components/AssetComposition.vue）
   是同一套视觉语言：一个大数字 + 分层的次要信息。
   区别是这里**没有占比条** —— 右边紧挨着就是「工单状态分布」图，
   再来一条占比条等于同一个信息说两遍。 */
.hero {
  display: flex;
  flex-direction: column;
  padding: var(--sp-4) var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
}

.hero-label {
  font-size: 12px;
  color: var(--ink-3);
}

.hero-figure {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 2px;
  margin-bottom: var(--sp-4);
}

/* 全页最大的数字。负字距让它看起来是「一个读数」而不是几个字符 */
.hero-value {
  font-size: 44px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -1.5px;
  color: var(--ink-1);
}

.hero-unit {
  font-size: 13px;
  color: var(--ink-3);
}

/* 完成率 */
.hero-rate {
  margin-bottom: var(--sp-4);
}

.rate-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: 6px;
}

.rate-label {
  font-size: 12px;
  color: var(--ink-3);
}

.rate-head b {
  font-size: 20px;
  font-weight: 600;
  color: var(--ink-1);
}

.rate-hint {
  margin-top: 6px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 底部两格。margin-top: auto 把它压到卡片底部，
   这样它和右侧图表面板的下边缘基本齐平 */
.hero-foot {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--sp-4);
  margin-top: auto;
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line-soft);
}

.foot-label {
  font-size: 12px;
  color: var(--ink-3);
}

.foot-value {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--ink-1);
}

/* 「待处理」有东西时才是告警色 */
.foot-value.warn {
  color: var(--warn);
}

/* 为 0（或没有样本）时不抢注意力 */
.foot-value.is-zero {
  color: var(--ink-3);
}

.foot-note {
  margin-top: 1px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 单位比数字小一档。用 --ink-3 而不是跟着数字的告警色 ——
   单位是说明，不是读数本身 */
.unit {
  margin-left: 2px;
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-3);
}

/* ---------- 筛选 ---------- */
.filter-bar {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

/* 分页在面板底部，和表格之间用一条淡分隔线断开 */
.table-pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--sp-3) var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.muted {
  color: var(--ink-3);
}

.caret {
  margin-left: 2px;
  font-size: 12px;
  color: var(--ink-3);
}

/* ---------- 详情 ---------- */
.detail {
  text-align: left;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.actions {
  display: flex;
  gap: 8px;
}

.section {
  margin-top: 22px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pre-wrap {
  white-space: pre-wrap;
}

/* ---------- 照片 ---------- */
.photo-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 10px;
}

.photo-item {
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  padding: 10px;
}

.photo-name {
  font-size: 13px;
  color: var(--ink-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.photo-meta {
  margin: 4px 0 6px;
  font-size: 12px;
  color: var(--ink-3);
}

.photo-actions {
  display: flex;
  gap: 8px;
}

.preview-box {
  display: flex;
  justify-content: center;
}

.preview-img {
  max-width: 100%;
  max-height: 65vh;
}

/* ---------- AI ---------- */
.ai-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  margin-bottom: 14px;
  border-radius: var(--r-panel);
  background: var(--sunken);
  font-size: 13px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-2);
}

.block {
  margin-bottom: 16px;
}

.block h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

.list {
  margin: 0;
  padding-left: 22px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--ink-2);
}

.disclaimer {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

.fail-body {
  font-size: 13px;
  line-height: 1.7;
}

.fail-tip {
  margin: 6px 0 0;
  color: var(--ink-3);
}

/* ---------- 日志 ---------- */
.log-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.log-operator {
  font-size: 12px;
  color: var(--ink-3);
}

.log-content {
  flex-basis: 100%;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.log-editor {
  margin-top: 12px;
}

.log-submit {
  margin-top: 8px;
  float: right;
}

.tip {
  clear: both;
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* 主卡和状态图并排需要横向空间：主卡里那句「已完成 N + 已关闭 N，共 N 单」
   在更窄的列里会被迫折成两行。窄了就改成上下排列。
   断点和看板的 .overview 取同一个值 */
@media (max-width: 1100px) {
  .stats-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
