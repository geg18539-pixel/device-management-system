<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import {
  DEVICE_LIFECYCLE,
  DEVICE_LIFECYCLE_OPTIONS,
  DEVICE_LIFECYCLE_TONE,
  DEVICE_STATUS,
  DEVICE_STATUS_OPTIONS,
  borrowDevice,
  createDevice,
  deleteDevice,
  downloadImportTemplate,
  exportDevices,
  getDevicePage,
  getDeviceStats,
  importDevices,
  repairDevice,
  restoreDevice,
  returnDevice,
  scrapDevice,
  transferDevice,
  updateDevice,
  type ChartItem,
  type Device,
  type DeviceImportResult,
} from '../api/device'
import {
  buildCategoryNameMap,
  flattenCategories,
  getCategoryTree,
  type DeviceCategoryTree,
} from '../api/deviceCategory'
import { buildDeptNameMap, flattenDepts, getDeptTree, type DeptTree } from '../api/dept'
import { DICT_TYPE, useDict } from '../api/dict'
import DataPanel from '../components/DataPanel.vue'
import DeviceCharts from '../components/DeviceCharts.vue'
import DiagnosisPanel from '../components/DiagnosisPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { usePerm } from '../composables/usePerm'
import type { PlateTone } from '../utils/plateTone'

const { hasPerm } = usePerm()

const router = useRouter()

/** 故障类型下拉的数据来自字典，不在前端硬编码 */
const { options: faultTypeOptions } = useDict(DICT_TYPE.FAULT_TYPE)

/** 保修到期筛选的预设档位。用天数而不是日期区间，因为实际用途就是"看看哪些快过保了" */
const WARRANTY_OPTIONS = [
  { label: '30 天内到期', value: 30 },
  { label: '60 天内到期', value: 60 },
  { label: '90 天内到期', value: 90 },
  { label: '半年内到期', value: 180 },
]

// ---------------- 图表 ----------------
const statusItems = ref<ChartItem[]>([])
const categoryItems = ref<ChartItem[]>([])
const totalDevices = ref(0)

async function loadStats() {
  try {
    const stats = await getDeviceStats()
    statusItems.value = stats.statusItems
    categoryItems.value = stats.categoryItems
    totalDevices.value = stats.total
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  }
}

// ---------------- 分类 ----------------
const categoryTree = ref<DeviceCategoryTree[]>([])

/** 平铺成下拉选项（用缩进表示层级） */
const categoryOptions = computed(() => flattenCategories(categoryTree.value))

/** id -> 名称，用来把表格里的 categoryId 显示成分类名 */
const categoryNameMap = computed(() => buildCategoryNameMap(categoryTree.value))

async function loadCategories() {
  try {
    categoryTree.value = await getCategoryTree()
  } catch {
    // 同上
  }
}

function categoryLabel(categoryId?: number): string {
  if (categoryId === undefined || categoryId === null) return '未分类'
  return categoryNameMap.value[categoryId] ?? `分类#${categoryId}`
}

// ---------------- 部门 ----------------
const deptTree = ref<DeptTree[]>([])

/** 平铺成下拉选项（用缩进表示层级） */
const deptOptions = computed(() => flattenDepts(deptTree.value, null))

/** id -> 部门名，用来把表格里的 deptId 显示成部门名 */
const deptNameMap = computed(() => buildDeptNameMap(deptTree.value))

async function loadDepts() {
  try {
    deptTree.value = await getDeptTree()
  } catch {
    // 同上
  }
}

function deptLabel(deptId?: number): string {
  if (deptId === undefined || deptId === null) return '未分配'
  return deptNameMap.value[deptId] ?? `部门#${deptId}`
}

// ---------------- 列表 ----------------
const loading = ref(false)
const deviceList = ref<Device[]>([])
/**
 * 当前筛选条件下的总条数（分页组件用这个）。
 *
 * <p>不能拿 totalDevices 当分页总数 —— 那是**全库**设备数，
 * 一旦加了筛选条件，分页就会多出翻不到数据的空页。
 */
const filteredTotal = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  categoryId: undefined as number | undefined,
  deptId: undefined as number | undefined,
  status: '',
  lifecycleStatus: '',
  warrantyWithinDays: undefined as number | undefined,
  keyword: '',
})

/** 把当前筛选条件整理成接口参数，列表和导出共用（导出必须所见即所得） */
function currentParams() {
  return {
    categoryId: query.categoryId,
    deptId: query.deptId,
    status: query.status || undefined,
    lifecycleStatus: query.lifecycleStatus || undefined,
    warrantyWithinDays: query.warrantyWithinDays,
    keyword: query.keyword || undefined,
  }
}

async function loadList() {
  loading.value = true
  try {
    const page = await getDevicePage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      ...currentParams(),
    })
    deviceList.value = page.list
    filteredTotal.value = page.total
  } catch {
    // 同上
  } finally {
    loading.value = false
  }
}

/** 列表和图表都要刷新 —— 增删改之后统计数字同样会变 */
async function refreshAll() {
  await Promise.all([loadList(), loadStats()])
}

function handleSearch() {
  query.pageNum = 1
  void loadList()
}

function handleReset() {
  query.categoryId = undefined
  query.deptId = undefined
  query.status = ''
  query.lifecycleStatus = ''
  query.warrantyWithinDays = undefined
  query.keyword = ''
  handleSearch()
}

/** 生命周期状态的铭牌色调。老数据可能为空，按「正常」处理 */
function lifecycleTone(status?: string): PlateTone {
  const key = status && status.trim() ? status : DEVICE_LIFECYCLE.NORMAL
  return DEVICE_LIFECYCLE_TONE[key] ?? 'idle'
}

function lifecycleText(status?: string): string {
  return status && status.trim() ? status : DEVICE_LIFECYCLE.NORMAL
}

/** 连通状态的铭牌色调 */
function statusTone(status: string): PlateTone {
  if (status === DEVICE_STATUS.ONLINE) return 'ok'
  if (status === DEVICE_STATUS.OFFLINE) return 'idle'
  if (status === DEVICE_STATUS.REPAIRING) return 'warn'
  return 'info'
}

/** 页头那行说明。合并了原来表格上方的 .summary */
const headDesc = computed(() => {
  if (!totalDevices.value) return '还没有设备数据'
  return `共 ${totalDevices.value} 台设备，当前筛选出 ${filteredTotal.value} 台`
})

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

/** 保修是否已过期，用来在表格里标红 */
function isWarrantyExpired(value?: string): boolean {
  if (!value) return false
  return value < new Date().toISOString().slice(0, 10)
}

// 按行状态决定显示哪些操作按钮，避免点了必然报错的按钮
function canBorrow(row: Device): boolean {
  return !row.borrower && row.status !== DEVICE_STATUS.REPAIRING
}

function canReturn(row: Device): boolean {
  return !!row.borrower
}

function canRepair(row: Device): boolean {
  return row.status !== DEVICE_STATUS.REPAIRING && !isScrapped(row)
}

/** 已报废的设备不该再调拨/借用/报修 */
function isScrapped(row: Device): boolean {
  return row.lifecycleStatus === DEVICE_LIFECYCLE.SCRAPPED
}

function canTransfer(row: Device): boolean {
  return !isScrapped(row)
}

function canScrap(row: Device): boolean {
  return !isScrapped(row)
}

function canRestore(row: Device): boolean {
  return isScrapped(row)
}

/**
 * 下拉菜单的命令分发。
 *
 * <p>用"返回闭包"的写法而不是在模板里写内联箭头函数：
 * 模板里带类型标注的内联箭头在 strict 模式下会报隐式 any。
 */
function rowCommandHandler(row: Device) {
  return (command: string): void => {
    if (command === 'borrow') void openBorrow(row)
    else if (command === 'return') void handleReturn(row)
    else if (command === 'repair') void openRepair(row)
    else if (command === 'transfer') void openTransfer(row)
    else if (command === 'scrap') void openScrap(row)
    else if (command === 'restore') void handleRestore(row)
    else if (command === 'delete') void handleDelete(row)
  }
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增设备')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

interface DeviceForm {
  deviceName: string
  deviceType: string
  categoryId: number | undefined
  deptId: number | undefined
  lifecycleStatus: string
  assetCode: string
  serialNumber: string
  model: string
  manufacturer: string
  status: string
  location: string
  description: string
  purchaseDate: string
  warrantyDate: string
}

function emptyForm(): DeviceForm {
  return {
    deviceName: '',
    deviceType: '',
    categoryId: undefined,
    deptId: undefined,
    lifecycleStatus: DEVICE_LIFECYCLE.NORMAL,
    assetCode: '',
    serialNumber: '',
    model: '',
    manufacturer: '',
    status: DEVICE_STATUS.ONLINE,
    location: '',
    description: '',
    purchaseDate: '',
    warrantyDate: '',
  }
}

const form = reactive<DeviceForm>(emptyForm())

const rules: FormRules<DeviceForm> = {
  deviceName: [
    { required: true, message: '请输入设备名称', trigger: 'blur' },
    { max: 100, message: '设备名称不能超过 100 个字符', trigger: 'blur' },
  ],
  assetCode: [{ max: 50, message: '资产编号不能超过 50 个字符', trigger: 'blur' }],
  serialNumber: [{ max: 100, message: '序列号不能超过 100 个字符', trigger: 'blur' }],
  model: [{ max: 100, message: '型号不能超过 100 个字符', trigger: 'blur' }],
  manufacturer: [{ max: 100, message: '厂商不能超过 100 个字符', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  location: [{ max: 100, message: '位置不能超过 100 个字符', trigger: 'blur' }],
  description: [{ max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' }],
}

async function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增设备'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: Device) {
  editingId.value = row.id ?? null
  dialogTitle.value = '编辑设备'
  Object.assign(form, {
    deviceName: row.deviceName,
    deviceType: row.deviceType ?? '',
    categoryId: row.categoryId,
    deptId: row.deptId,
    lifecycleStatus: row.lifecycleStatus ?? DEVICE_LIFECYCLE.NORMAL,
    assetCode: row.assetCode ?? '',
    serialNumber: row.serialNumber ?? '',
    model: row.model ?? '',
    manufacturer: row.manufacturer ?? '',
    status: row.status,
    location: row.location ?? '',
    description: row.description ?? '',
    purchaseDate: row.purchaseDate ?? '',
    warrantyDate: row.warrantyDate ?? '',
  })
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 空字符串要转成 null 再提交：后端字段是 LocalDate / BigDecimal 之类的强类型，
  // 收到空串会反序列化失败
  const payload: Device = {
    deviceName: form.deviceName,
    deviceType: form.deviceType || undefined,
    categoryId: form.categoryId ?? undefined,
    lifecycleStatus: form.lifecycleStatus || DEVICE_LIFECYCLE.NORMAL,
    assetCode: form.assetCode || undefined,
    serialNumber: form.serialNumber || undefined,
    model: form.model || undefined,
    manufacturer: form.manufacturer || undefined,
    status: form.status,
    location: form.location || undefined,
    description: form.description || undefined,
    purchaseDate: form.purchaseDate || undefined,
    warrantyDate: form.warrantyDate || undefined,
  }

  // ★ 部门只在**新增**时提交。
  // 编辑时后端本来也会忽略 deptId（归属变更必须走调拨，否则调拨历史会漏记录），
  // 这里干脆不发，避免让读代码的人以为编辑能改部门
  if (editingId.value === null) {
    payload.deptId = form.deptId ?? undefined
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createDevice(payload)
      ElMessage.success('新增成功')
    } else {
      await updateDevice(editingId.value, payload)
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await refreshAll()
  } catch {
    // 失败提示已由拦截器处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 借用 ----------------
const borrowVisible = ref(false)
const borrowSubmitting = ref(false)
const borrowTarget = ref<Device | null>(null)
const borrowForm = reactive({ borrower: '', remark: '' })
const borrowFormRef = ref<FormInstance>()

const borrowRules: FormRules<{ borrower: string; remark: string }> = {
  borrower: [
    { required: true, message: '请输入借用人', trigger: 'blur' },
    { max: 50, message: '借用人不能超过 50 个字符', trigger: 'blur' },
  ],
}

async function openBorrow(row: Device) {
  borrowTarget.value = row
  borrowForm.borrower = ''
  borrowForm.remark = ''
  borrowVisible.value = true
  await nextTick()
  borrowFormRef.value?.clearValidate()
}

async function submitBorrow() {
  if (!borrowTarget.value?.id || !borrowFormRef.value) return

  const valid = await borrowFormRef.value.validate().catch(() => false)
  if (!valid) return

  borrowSubmitting.value = true
  try {
    await borrowDevice(borrowTarget.value.id, borrowForm.borrower, borrowForm.remark || undefined)
    ElMessage.success('借用成功')
    borrowVisible.value = false
    await refreshAll()
  } catch {
    // 同上
  } finally {
    borrowSubmitting.value = false
  }
}

// ---------------- 归还 ----------------
async function handleReturn(row: Device) {
  if (!row.id) return

  try {
    await ElMessageBox.confirm(
      `确认「${row.borrower}」已归还设备「${row.deviceName}」？`,
      '归还确认',
      { type: 'warning', confirmButtonText: '确认归还', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await returnDevice(row.id)
    ElMessage.success('归还成功')
    await refreshAll()
  } catch {
    // 同上
  }
}

// ---------------- 报修 ----------------
const repairVisible = ref(false)
const repairSubmitting = ref(false)
const repairTarget = ref<Device | null>(null)
const repairForm = reactive({ faultDesc: '', faultType: '' })
const repairFormRef = ref<FormInstance>()

/**
 * AI 辅助诊断弹窗。
 *
 * <p>做成独立弹窗而不是嵌在报修表单里：诊断结果有三栏资料加一大段生成的建议，
 * 塞进一个 460px 的报修弹窗会把表单挤得没法用。
 * 诊断完用户自己看着填故障类型和描述，两个弹窗互不干扰。
 */
const diagnosisVisible = ref(false)

const repairRules: FormRules<{ faultDesc: string; faultType: string }> = {
  faultDesc: [
    { required: true, message: '请描述故障现象', trigger: 'blur' },
    { max: 500, message: '故障描述不能超过 500 个字符', trigger: 'blur' },
  ],
}

async function openRepair(row: Device) {
  repairTarget.value = row
  repairForm.faultDesc = ''
  repairForm.faultType = ''
  repairVisible.value = true
  await nextTick()
  repairFormRef.value?.clearValidate()
}

async function submitRepair() {
  if (!repairTarget.value?.id || !repairFormRef.value) return

  const valid = await repairFormRef.value.validate().catch(() => false)
  if (!valid) return

  repairSubmitting.value = true
  try {
    await repairDevice(
      repairTarget.value.id,
      repairForm.faultDesc,
      repairForm.faultType || undefined,
    )
    ElMessage.success('报修成功，已生成维修工单')
    repairVisible.value = false
    await refreshAll()
  } catch {
    // 同上
  } finally {
    repairSubmitting.value = false
  }
}

// ---------------- 导出 ----------------
const exporting = ref(false)

async function handleExport() {
  exporting.value = true
  try {
    // 导出用和列表**完全相同的筛选条件**，所以传的是 currentParams()
    await exportDevices(currentParams())
    ElMessage.success('导出成功')
  } catch {
    // 同上
  } finally {
    exporting.value = false
  }
}

// ---------------- 跳转详情 ----------------
function openDetail(row: Device) {
  if (!row.id) return
  void router.push(`/devices/${row.id}`)
}

function openLedger() {
  void router.push('/devices/ledger')
}

// ---------------- 批量导入 ----------------
const importVisible = ref(false)
const importing = ref(false)
const templateUrlLoading = ref(false)
const importResult = ref<DeviceImportResult | null>(null)

function openImport() {
  importResult.value = null
  importVisible.value = true
}

async function handleDownloadTemplate() {
  templateUrlLoading.value = true
  try {
    await downloadImportTemplate()
    ElMessage.success('模板已下载，按模板填好后回来导入')
  } catch {
    // 同上
  } finally {
    templateUrlLoading.value = false
  }
}

/** 前端先挡一道明显的错误类型，省得白传一次；后端仍然会独立校验 */
function beforeImport(file: File): boolean {
  const name = file.name.toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    ElMessage.error('请上传 Excel 文件（.xlsx 或 .xls）')
    return false
  }
  return true
}

async function handleImport(options: UploadRequestOptions) {
  importing.value = true
  try {
    importResult.value = await importDevices(options.file as File)
    const r = importResult.value
    if (r.failCount === 0) {
      ElMessage.success(`全部导入成功，共 ${r.successCount} 条`)
    } else {
      ElMessage.warning(`成功 ${r.successCount} 条，失败 ${r.failCount} 条`)
    }
    // 导入成功的话刷新列表和图表
    await refreshAll()
  } catch {
    // 失败提示已由拦截器弹出
  } finally {
    importing.value = false
  }
}

// ---------------- 报废 ----------------
const scrapVisible = ref(false)
const scrapSubmitting = ref(false)
const scrapTarget = ref<Device | null>(null)
const scrapFormRef = ref<FormInstance>()
const scrapForm = reactive({ reason: '', scrapDate: '' })

const scrapRules: FormRules<{ reason: string; scrapDate: string }> = {
  reason: [
    { required: true, message: '请填写报废原因', trigger: 'blur' },
    { max: 200, message: '报废原因不能超过 200 个字符', trigger: 'blur' },
  ],
}

async function openScrap(row: Device) {
  scrapTarget.value = row
  scrapForm.reason = ''
  // 默认今天。大部分情况就是今天报的，不该逼用户每次都去点一次日期
  scrapForm.scrapDate = new Date().toISOString().slice(0, 10)
  scrapVisible.value = true
  await nextTick()
  scrapFormRef.value?.clearValidate()
}

async function submitScrap() {
  if (!scrapTarget.value?.id || !scrapFormRef.value) return

  const valid = await scrapFormRef.value.validate().catch(() => false)
  if (!valid) return

  scrapSubmitting.value = true
  try {
    await scrapDevice(scrapTarget.value.id, scrapForm.reason, scrapForm.scrapDate || undefined)
    ElMessage.success('已报废')
    scrapVisible.value = false
    await refreshAll()
  } catch {
    // 后端有"借用中 / 有未完成工单不能报废"的保护，提示由拦截器弹出
  } finally {
    scrapSubmitting.value = false
  }
}

/** 取消报废。误操作的回退口子，必须给，否则点错就不可逆了 */
async function handleRestore(row: Device) {
  if (!row.id) return

  try {
    await ElMessageBox.confirm(
      `确认把设备「${row.deviceName}」从报废恢复为正常吗？`,
      '取消报废',
      { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await restoreDevice(row.id)
    ElMessage.success('已恢复为正常')
    await refreshAll()
  } catch {
    // 同上
  }
}

// ---------------- 调拨 ----------------
const transferVisible = ref(false)
const transferSubmitting = ref(false)
const transferTarget = ref<Device | null>(null)
const transferFormRef = ref<FormInstance>()
const transferForm = reactive<{ toDeptId: number | undefined; reason: string }>({
  toDeptId: undefined,
  reason: '',
})

const transferRules: FormRules<{ toDeptId: number | undefined; reason: string }> = {
  toDeptId: [{ required: true, message: '请选择目标部门', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写调拨原因', trigger: 'blur' },
    { max: 200, message: '调拨原因不能超过 200 个字符', trigger: 'blur' },
  ],
}

async function openTransfer(row: Device) {
  transferTarget.value = row
  transferForm.toDeptId = undefined
  transferForm.reason = ''
  transferVisible.value = true
  await nextTick()
  transferFormRef.value?.clearValidate()
}

async function submitTransfer() {
  if (!transferTarget.value?.id || !transferFormRef.value) return

  const valid = await transferFormRef.value.validate().catch(() => false)
  if (!valid) return

  transferSubmitting.value = true
  try {
    await transferDevice(transferTarget.value.id, transferForm.toDeptId, transferForm.reason)
    ElMessage.success('调拨成功')
    transferVisible.value = false
    await refreshAll()
  } catch {
    // 同上
  } finally {
    transferSubmitting.value = false
  }
}

// ---------------- 删除 ----------------
async function handleDelete(row: Device) {
  if (!row.id) return

  try {
    await ElMessageBox.confirm(`确定要删除设备「${row.deviceName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteDevice(row.id)
    ElMessage.success('删除成功')
    if (deviceList.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await refreshAll()
  } catch {
    // 后端有"设备借出中/有维修记录不能删"的保护，会返回 400，提示由拦截器弹出
  }
}

onMounted(async () => {
  // 分类和部门都要在下拉/表格用，和列表一起并行加载
  await Promise.all([loadCategories(), loadDepts(), refreshAll()])
})
</script>

<template>
  <div class="page">
    <PageHeader title="设备管理" :desc="headDesc">
      <template #actions>
        <el-button @click="openLedger">设备台账</el-button>
        <el-button v-if="hasPerm('dev:device:import')" @click="openImport">批量导入</el-button>
        <el-button
          v-if="hasPerm('dev:device:export')"
          :loading="exporting"
          @click="handleExport"
        >
          导出 Excel
        </el-button>
        <el-button v-if="hasPerm('dev:device:add')" type="primary" @click="openCreate">
          新增设备
        </el-button>
      </template>
    </PageHeader>

    <!-- 顶部两个图表 -->
    <DeviceCharts :status-items="statusItems" :category-items="categoryItems" />

    <!-- 搜索栏和列表放同一个面板：搜索在上、表格在下 -->
    <DataPanel flush>
      <div class="filter-bar">
        <div class="filters">
          <el-select
            v-model="query.categoryId"
            placeholder="全部分类"
            clearable
            style="width: 170px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in categoryOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
            />
          </el-select>

          <el-select
            v-model="query.deptId"
            placeholder="全部部门"
            clearable
            style="width: 170px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in deptOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
            />
          </el-select>

          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in DEVICE_STATUS_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>

          <el-select
            v-model="query.lifecycleStatus"
            placeholder="全部资产状态"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in DEVICE_LIFECYCLE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>

          <el-select
            v-model="query.warrantyWithinDays"
            placeholder="不限保修期"
            clearable
            style="width: 150px"
            @change="handleSearch"
          >
            <el-option
              v-for="opt in WARRANTY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>

          <el-input
            v-model="query.keyword"
            placeholder="名称 / 资产编号 / 序列号"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />

          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="deviceList">
        <el-table-column prop="assetCode" label="资产编号" width="130" />
        <el-table-column prop="deviceName" label="设备名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <span :class="{ muted: !row.categoryId }">{{ categoryLabel(row.categoryId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="部门" width="120">
          <template #default="{ row }">
            <span :class="{ muted: !row.deptId }">{{ deptLabel(row.deptId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" width="110" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂商" width="110" show-overflow-tooltip />
        <el-table-column prop="serialNumber" label="序列号" min-width="120" show-overflow-tooltip />

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusPlate :tone="statusTone(row.status)">{{ row.status }}</StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="资产状态" width="90">
          <template #default="{ row }">
            <StatusPlate :tone="lifecycleTone(row.lifecycleStatus)">
              {{ lifecycleText(row.lifecycleStatus) }}
            </StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="借用人" width="100">
          <template #default="{ row }">
            <span :class="{ muted: !row.borrower }">{{ row.borrower || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="保修到期" width="115">
          <template #default="{ row }">
            <span v-if="!row.warrantyDate" class="muted">—</span>
            <span v-else :class="{ expired: isWarrantyExpired(row.warrantyDate) }">
              {{ row.warrantyDate }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="location" label="位置" min-width="100" show-overflow-tooltip />
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="hasPerm('dev:device:edit')" link type="primary" @click="openEdit(row)">
              编辑
            </el-button>
            <!-- 6 个操作平铺太挤，收进下拉。这里用的是返回闭包的写法，
                 模板里不能写带类型标注的内联箭头（strict 下会报隐式 any） -->
            <el-dropdown trigger="click" @command="rowCommandHandler(row)">
              <el-button link type="primary">
                更多<span class="caret">▾</span>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="(canBorrow(row)) && hasPerm('dev:device:borrow')" command="borrow">
                    借用
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canReturn(row)) && hasPerm('dev:device:return')" command="return">
                    归还
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canRepair(row)) && hasPerm('dev:device:repair')" command="repair">
                    报修
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canTransfer(row)) && hasPerm('dev:device:transfer')" command="transfer">
                    调拨
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canScrap(row)) && hasPerm('dev:device:scrap')" command="scrap" divided>
                    报废
                  </el-dropdown-item>
                  <el-dropdown-item v-if="(canRestore(row)) && hasPerm('dev:device:scrap')" command="restore">
                    取消报废
                  </el-dropdown-item>
                  <el-dropdown-item v-if="hasPerm('dev:device:remove')" command="delete" divided>
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="没有符合条件的设备" desc="换个筛选条件试试，或者直接新增一台。" />
        </template>
      </el-table>

      <div class="table-pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="filteredTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="loadList"
        />
      </div>
    </DataPanel>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input v-model="form.deviceName" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资产编号" prop="assetCode">
              <el-input v-model="form.assetCode" placeholder="如 ZC-2026-0001" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属分类">
              <el-select v-model="form.categoryId" placeholder="可暂不分类" clearable style="width: 100%">
                <el-option
                  v-for="opt in categoryOptions"
                  :key="opt.id"
                  :label="opt.label"
                  :value="opt.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 部门只在新增时可指定。编辑时不提供修改入口 ——
                 归属变更必须走「调拨」，否则调拨历史会漏掉这些改动 -->
            <el-form-item v-if="editingId === null" label="所属部门">
              <el-select v-model="form.deptId" placeholder="可暂不分配" clearable style="width: 100%">
                <el-option
                  v-for="opt in deptOptions"
                  :key="opt.id"
                  :label="opt.label"
                  :value="opt.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item v-else label="当前部门">
              <span class="static-value">{{ deptLabel(form.deptId) }}</span>
              <span class="inline-tip">变更归属请用「调拨」</span>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="型号" prop="model">
              <el-input v-model="form.model" placeholder="如 SHT-2000" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="厂商" prop="manufacturer">
              <el-input v-model="form.manufacturer" placeholder="如 中科传感" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="资产状态">
              <el-select v-model="form.lifecycleStatus" style="width: 100%">
                <el-option
                  v-for="opt in DEVICE_LIFECYCLE_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-input
                v-model="form.deviceType"
                placeholder="遗留字段，建议改用分类"
                maxlength="50"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="序列号" prop="serialNumber">
              <el-input v-model="form.serialNumber" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="连通状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option
                  v-for="opt in DEVICE_STATUS_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="采购日期">
              <el-date-picker
                v-model="form.purchaseDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选填"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="保修到期">
              <el-date-picker
                v-model="form.warrantyDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选填"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" maxlength="100" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 借用 -->
    <el-dialog
      v-model="borrowVisible"
      :title="`借用 —— ${borrowTarget?.deviceName ?? ''}`"
      width="440px"
    >
      <el-form ref="borrowFormRef" :model="borrowForm" :rules="borrowRules" label-width="80px">
        <el-form-item label="借用人" prop="borrower">
          <el-input v-model="borrowForm.borrower" placeholder="谁借走这台设备" maxlength="50" />
        </el-form-item>
        <el-form-item label="借用说明">
          <el-input v-model="borrowForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <p class="tip">借用后设备状态会自动变成「使用中」。</p>

      <template #footer>
        <el-button @click="borrowVisible = false">取消</el-button>
        <el-button type="primary" :loading="borrowSubmitting" @click="submitBorrow">确定</el-button>
      </template>
    </el-dialog>

    <!-- 报修 -->
    <el-dialog
      v-model="repairVisible"
      :title="`报修 —— ${repairTarget?.deviceName ?? ''}`"
      width="460px"
    >
      <el-form ref="repairFormRef" :model="repairForm" :rules="repairRules" label-width="80px">
        <el-form-item label="故障类型">
          <el-select
            v-model="repairForm.faultType"
            placeholder="选填，先判断不出来可以留空"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="opt in faultTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述" prop="faultDesc">
          <el-input
            v-model="repairForm.faultDesc"
            type="textarea"
            :rows="4"
            placeholder="描述一下故障现象"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <!-- AI 辅助入口。放在描述下面而不是按钮栏里：
             它是"帮你把这条描述想清楚"的工具，跟"提交/取消"不是一类操作 -->
        <div class="ai-assist">
          <el-button link type="primary" size="small" @click="diagnosisVisible = true">
            AI 辅助诊断
          </el-button>
          <span class="ai-assist-hint">
            检索设备手册和相似历史工单，给出维修建议；依据的资料会一并列出
          </span>
        </div>
      </el-form>
      <p class="tip">
        提交后会生成一张维修工单，设备状态变成「维修中」。
        工单的完工在「维修工单」页面操作。
      </p>

      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="repairSubmitting" @click="submitRepair">确定</el-button>
      </template>
    </el-dialog>

    <!-- AI 辅助诊断。append-to-body 是必须的：嵌在报修弹窗里面，
         不挂到 body 上会被外层弹窗的层叠上下文压住 -->
    <el-dialog
      v-model="diagnosisVisible"
      :title="`AI 辅助诊断 —— ${repairTarget?.deviceName ?? ''}`"
      width="960px"
      append-to-body
    >
      <DiagnosisPanel
        :initial-fault-desc="repairForm.faultDesc"
        :device-id="repairTarget?.id"
        :device-name="repairTarget?.deviceName ?? ''"
        :fault-type="repairForm.faultType"
      />
    </el-dialog>
    <!-- 报废 -->
    <el-dialog
      v-model="scrapVisible"
      :title="`报废 —— ${scrapTarget?.deviceName ?? ''}`"
      width="460px"
    >
      <el-form ref="scrapFormRef" :model="scrapForm" :rules="scrapRules" label-width="90px">
        <el-form-item label="报废日期">
          <el-date-picker
            v-model="scrapForm.scrapDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="报废原因" prop="reason">
          <el-input
            v-model="scrapForm.reason"
            type="textarea"
            :rows="3"
            placeholder="如：主板烧毁，已无维修价值"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <p class="tip">
        报废后资产状态变为「报废」，设备不能再被调拨或借用。
        误操作可以在操作列的「取消报废」里恢复。
      </p>

      <template #footer>
        <el-button @click="scrapVisible = false">取消</el-button>
        <el-button type="danger" :loading="scrapSubmitting" @click="submitScrap">确认报废</el-button>
      </template>
    </el-dialog>

    <!-- 调拨 -->
    <el-dialog
      v-model="transferVisible"
      :title="`调拨 —— ${transferTarget?.deviceName ?? ''}`"
      width="480px"
    >
      <el-form ref="transferFormRef" :model="transferForm" :rules="transferRules" label-width="90px">
        <el-form-item label="当前部门">
          <span class="static-value">{{ deptLabel(transferTarget?.deptId) }}</span>
        </el-form-item>
        <el-form-item label="目标部门" prop="toDeptId">
          <el-select v-model="transferForm.toDeptId" placeholder="选择调入的部门" style="width: 100%">
            <el-option
              v-for="opt in deptOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调拨原因" prop="reason">
          <el-input
            v-model="transferForm.reason"
            type="textarea"
            :rows="3"
            placeholder="如：一号车间设备更新，旧机调给二号车间"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <p class="tip">
        调拨会留下一条记录（从哪个部门到哪个部门、原因、操作人、时间），
        在设备详情的「调拨记录」里可以看到。
      </p>

      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSubmitting" @click="submitTransfer">
          确认调拨
        </el-button>
      </template>
    </el-dialog>
    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入设备" width="720px">
      <!-- 第一步：下载模板 -->
      <div class="import-step">
        <div class="step-row">
          <span class="step-no">1</span>
          <div class="step-body">
            <div class="step-title">下载模板</div>
            <div class="step-desc">
              模板里有示例行和「填写说明」页（含系统里现有的分类、部门名称），照着填不会出错。
            </div>
          </div>
          <el-button :loading="templateUrlLoading" @click="handleDownloadTemplate">
            下载模板
          </el-button>
        </div>
      </div>

      <!-- 第二步：上传 -->
      <div class="import-step">
        <div class="step-row">
          <span class="step-no">2</span>
          <div class="step-body">
            <div class="step-title">上传填好的文件</div>
            <div class="step-desc">
              单次最多 2000 行。格式不对的行会被跳过并单独列出来，其余行正常导入。
            </div>
          </div>
          <el-upload
            :show-file-list="false"
            :http-request="handleImport"
            :before-upload="beforeImport"
            accept=".xlsx,.xls"
          >
            <el-button type="primary" :loading="importing">
              {{ importing ? '正在导入…' : '选择文件并导入' }}
            </el-button>
          </el-upload>
        </div>
      </div>

      <!-- 第三步：结果 -->
      <div v-if="importResult" class="import-step">
        <div class="step-row">
          <span class="step-no">3</span>
          <div class="step-body">
            <div class="step-title">导入结果</div>
            <div class="step-desc">
              共 {{ importResult.total }} 行：
              <span class="ok-text">成功 {{ importResult.successCount }} 条</span>
              <template v-if="importResult.failCount > 0">
                ，<span class="fail-text">失败 {{ importResult.failCount }} 条</span>
              </template>
            </div>
          </div>
        </div>

        <el-alert
          v-for="(w, i) in importResult.warnings"
          :key="`w${i}`"
          type="warning"
          :closable="false"
          show-icon
          class="import-alert"
        >
          {{ w }}
        </el-alert>

        <template v-if="importResult.errors.length">
          <el-table :data="importResult.errors" border size="small" max-height="260">
            <el-table-column label="Excel 行号" width="100">
              <template #default="{ row }">{{ row.rowNum }}</template>
            </el-table-column>
            <el-table-column prop="deviceName" label="设备名称" width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span :class="{ muted: !row.deviceName }">{{ row.deviceName || '（空）' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="没导入成功的原因" min-width="300" show-overflow-tooltip />
          </el-table>
          <p v-if="importResult.errorsTruncated" class="tip">
            错误太多，只显示前 {{ importResult.errors.length }} 条。
            改完这些可以再导一次（已经成功导入的行不会重复入库）。
          </p>
          <p v-else class="tip">
            把上面这几行改好再导一次即可 —— 已经成功导入的行会因为编号重复被跳过，不会重复入库。
          </p>
        </template>
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          class="import-alert"
          title="全部导入成功"
        />
      </div>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认等其它弹窗在下面 -->
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* 搜索栏：面板顶部的一条，和表格共用同一个面板 */
.filter-bar {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

/* ---------- 批量导入弹窗 ---------- */
/* 步骤卡：原来用 el-card，但它只需要一个边框加内边距，
   直接用面板 token 写更省事，也少一层 :deep 覆盖 */
.import-step {
  margin-bottom: 12px;
  padding: 14px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
}

.step-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 步骤序号。用圆形数字，让"这是一步"这件事一眼看出来 */
.step-no {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: var(--signal);
  color: var(--on-signal);
  font-size: 13px;
}

.step-body {
  flex: 1;
  min-width: 0;
}

.step-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-1);
}

.step-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-3);
}

.ok-text {
  color: var(--ok);
  font-weight: 600;
}

.fail-text {
  color: var(--crit);
  font-weight: 600;
}

.import-alert {
  margin-top: 12px;
}

.caret {
  margin-left: 2px;
  font-size: 12px;
  color: var(--ink-3);
}

/* 只读展示的字段（编辑时不可改的那些） */
.static-value {
  color: var(--ink-1);
}

.inline-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ink-3);
}

/* 分页在面板底部，和表格之间用一条淡分隔线断开 */
.table-pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--sp-3) var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

.muted {
  color: var(--ink-3);
}

.expired {
  color: var(--crit);
}

.tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-3);
}

/* AI 辅助入口。挂在表单外的独立一行，和上面的表单项留一点间距 */
.ai-assist {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin: -6px 0 12px;
  padding-left: 80px;
}

.ai-assist-hint {
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-3);
}
</style>
