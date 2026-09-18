<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  DEVICE_STATUS,
  DEVICE_STATUS_OPTIONS,
  borrowDevice,
  createDevice,
  deleteDevice,
  getDevicePage,
  getDeviceStats,
  repairDevice,
  returnDevice,
  updateDevice,
  type ChartItem,
  type Device,
} from '../api/device'
import {
  buildCategoryNameMap,
  flattenCategories,
  getCategoryTree,
  type DeviceCategoryTree,
} from '../api/deviceCategory'
import DeviceCharts from '../components/DeviceCharts.vue'

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

// ---------------- 列表 ----------------
const loading = ref(false)
const deviceList = ref<Device[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  categoryId: undefined as number | undefined,
  status: '',
  keyword: '',
})

async function loadList() {
  loading.value = true
  try {
    const page = await getDevicePage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      categoryId: query.categoryId,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
    })
    deviceList.value = page.list
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
  query.status = ''
  query.keyword = ''
  handleSearch()
}

function statusTagType(status: string): 'success' | 'info' | 'warning' | 'primary' {
  if (status === DEVICE_STATUS.ONLINE) return 'success'
  if (status === DEVICE_STATUS.OFFLINE) return 'info'
  if (status === DEVICE_STATUS.REPAIRING) return 'warning'
  return 'primary'
}

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
  return row.status !== DEVICE_STATUS.REPAIRING
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
  assetCode: string
  serialNumber: string
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
    assetCode: '',
    serialNumber: '',
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
    assetCode: row.assetCode ?? '',
    serialNumber: row.serialNumber ?? '',
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
    assetCode: form.assetCode || undefined,
    serialNumber: form.serialNumber || undefined,
    status: form.status,
    location: form.location || undefined,
    description: form.description || undefined,
    purchaseDate: form.purchaseDate || undefined,
    warrantyDate: form.warrantyDate || undefined,
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
const repairForm = reactive({ faultDesc: '' })
const repairFormRef = ref<FormInstance>()

const repairRules: FormRules<{ faultDesc: string }> = {
  faultDesc: [
    { required: true, message: '请描述故障现象', trigger: 'blur' },
    { max: 500, message: '故障描述不能超过 500 个字符', trigger: 'blur' },
  ],
}

async function openRepair(row: Device) {
  repairTarget.value = row
  repairForm.faultDesc = ''
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
    await repairDevice(repairTarget.value.id, repairForm.faultDesc)
    ElMessage.success('报修成功，已生成维修工单')
    repairVisible.value = false
    await refreshAll()
  } catch {
    // 同上
  } finally {
    repairSubmitting.value = false
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
  await Promise.all([loadCategories(), refreshAll()])
})
</script>

<template>
  <div class="page">
    <!-- 顶部两个图表 -->
    <DeviceCharts :status-items="statusItems" :category-items="categoryItems" />

    <!-- 搜索栏 -->
    <div class="toolbar">
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

        <el-input
          v-model="query.keyword"
          placeholder="名称 / 资产编号 / 序列号"
          clearable
          style="width: 230px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-button type="primary" @click="openCreate">新增设备</el-button>
    </div>

    <div class="summary">
      共 <strong>{{ totalDevices }}</strong> 台设备，当前筛选出 <strong>{{ deviceList.length }}</strong> 条
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="deviceList" border stripe>
      <el-table-column prop="assetCode" label="资产编号" width="130" />
      <el-table-column prop="deviceName" label="设备名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="分类" width="110">
        <template #default="{ row }">
          <span :class="{ muted: !row.categoryId }">{{ categoryLabel(row.categoryId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="serialNumber" label="序列号" min-width="120" show-overflow-tooltip />

      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" disable-transitions>
            {{ row.status }}
          </el-tag>
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

      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canBorrow(row)" link type="primary" @click="openBorrow(row)">
            借用
          </el-button>
          <el-button v-if="canReturn(row)" link type="warning" @click="handleReturn(row)">
            归还
          </el-button>
          <el-button v-if="canRepair(row)" link type="warning" @click="openRepair(row)">
            报修
          </el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无设备数据" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="totalDevices"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @size-change="handleSearch"
      @current-change="loadList"
    />

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
            <el-form-item label="状态" prop="status">
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
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.filters {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: #64748b;
}

.summary strong {
  color: var(--text-h);
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.muted {
  color: #cbd5e1;
}

.expired {
  color: #ef4444;
}

.tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
}
</style>
