<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  PART_STATUS,
  PART_STATUS_OPTIONS,
  createSparePart,
  deleteSparePart,
  getLowStockParts,
  getSparePartPage,
  getSparePartRecordPage,
  isLowStock,
  stockIn,
  stockOut,
  updateSparePart,
  type SparePart,
  type SparePartForm,
  type SparePartRecord,
  type StockForm,
} from '../api/sparePart'
import { getRepairPage, type DeviceRepair } from '../api/deviceRepair'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { usePerm } from '../composables/usePerm'

const { hasPerm } = usePerm()

const activeTab = ref('parts')

// ============================================================
// 库存告警
// ============================================================
const lowStockParts = ref<SparePart[]>([])

async function loadLowStock() {
  try {
    lowStockParts.value = await getLowStockParts()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  }
}

// ============================================================
// 配件列表
// ============================================================
const loading = ref(false)
const parts = ref<SparePart[]>([])
const total = ref(0)

/** 页头那行说明 */
const headDesc = computed(() => {
  const low = lowStockParts.value.length
  return low ? `共 ${total.value} 种配件，其中 ${low} 种库存告急` : `共 ${total.value} 种配件`
})

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: '',
  lowStockOnly: false,
})

async function loadParts() {
  loading.value = true
  try {
    const page = await getSparePartPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      lowStockOnly: query.lowStockOnly || undefined,
    })
    parts.value = page.list
    total.value = page.total
  } catch {
    // 同上
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  void loadParts()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.lowStockOnly = false
  handleSearch()
}

async function refreshAll() {
  await Promise.all([loadParts(), loadLowStock(), loadRecords()])
}

// ============================================================
// 新增 / 编辑配件
// ============================================================
const dialogVisible = ref(false)
const dialogTitle = ref('新增配件')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): SparePartForm {
  return {
    partCode: '',
    partName: '',
    model: '',
    unit: '个',
    warnThreshold: 0,
    unitPrice: undefined,
    supplier: '',
    location: '',
    status: PART_STATUS.ENABLED,
    remark: '',
  }
}

const form = reactive<SparePartForm>(emptyForm())

const rules: FormRules<SparePartForm> = {
  partCode: [
    { required: true, message: '请输入配件编码', trigger: 'blur' },
    { max: 50, message: '配件编码不能超过 50 个字符', trigger: 'blur' },
  ],
  partName: [
    { required: true, message: '请输入配件名称', trigger: 'blur' },
    { max: 100, message: '配件名称不能超过 100 个字符', trigger: 'blur' },
  ],
  unit: [{ max: 20, message: '单位不能超过 20 个字符', trigger: 'blur' }],
  supplier: [{ max: 100, message: '供应商不能超过 100 个字符', trigger: 'blur' }],
  location: [{ max: 100, message: '库位不能超过 100 个字符', trigger: 'blur' }],
  remark: [{ max: 200, message: '备注不能超过 200 个字符', trigger: 'blur' }],
}

async function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增配件'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: SparePart) {
  editingId.value = row.id
  dialogTitle.value = '编辑配件'
  Object.assign(form, {
    partCode: row.partCode,
    partName: row.partName,
    model: row.model ?? '',
    unit: row.unit ?? '',
    warnThreshold: row.warnThreshold,
    unitPrice: row.unitPrice,
    supplier: row.supplier ?? '',
    location: row.location ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createSparePart({ ...form })
      ElMessage.success('新增成功。库存从 0 开始，请用「入库」补上期初库存')
    } else {
      await updateSparePart(editingId.value, { ...form })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await refreshAll()
  } catch {
    // 编码重复等业务错误后端返回 400
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SparePart) {
  try {
    await ElMessageBox.confirm(`确定要删除配件「${row.partName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteSparePart(row.id)
    ElMessage.success('删除成功')
    await refreshAll()
  } catch {
    // 有流水的配件不能删，后端返回 400
  }
}

// ============================================================
// 入库 / 出库
// ============================================================
const stockVisible = ref(false)
const stockSubmitting = ref(false)
const stockMode = ref<'in' | 'out'>('in')
const stockTarget = ref<SparePart | null>(null)
const stockFormRef = ref<FormInstance>()
const stockForm = reactive<StockForm>({ quantity: 1, relatedRepairId: undefined, remark: '' })

const stockRules: FormRules<StockForm> = {
  quantity: [{ required: true, message: '请填写数量', trigger: 'blur' }],
}

// 出库时可以关联维修工单，这样设备详情页能反查出"这台设备换过哪些配件"
const repairs = ref<DeviceRepair[]>([])

async function loadRepairOptions() {
  try {
    const page = await getRepairPage({ pageNum: 1, pageSize: 100 })
    repairs.value = page.list
  } catch {
    /* 忽略：工单列表拿不到不影响出入库本身 */
  }
}

function openStock(row: SparePart, mode: 'in' | 'out') {
  stockTarget.value = row
  stockMode.value = mode
  stockForm.quantity = 1
  stockForm.relatedRepairId = undefined
  stockForm.remark = ''
  stockVisible.value = true
  void nextTick(() => stockFormRef.value?.clearValidate())
}

async function submitStock() {
  if (!stockTarget.value || !stockFormRef.value) return
  const valid = await stockFormRef.value.validate().catch(() => false)
  if (!valid) return

  stockSubmitting.value = true
  try {
    // 入库不带关联工单（期初/采购入库跟某张维修单没有关系）
    const payload: StockForm =
      stockMode.value === 'in'
        ? { quantity: stockForm.quantity, remark: stockForm.remark || undefined }
        : { ...stockForm, remark: stockForm.remark || undefined }

    if (stockMode.value === 'in') {
      await stockIn(stockTarget.value.id, payload)
      ElMessage.success('入库成功')
    } else {
      await stockOut(stockTarget.value.id, payload)
      ElMessage.success('出库成功')
    }
    stockVisible.value = false
    await refreshAll()
  } catch {
    // 库存不足 / 工单不存在等，后端返回 400，提示由拦截器弹出
  } finally {
    stockSubmitting.value = false
  }
}

// ============================================================
// 出入库流水
// ============================================================
const recordLoading = ref(false)
const records = ref<SparePartRecord[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({ pageNum: 1, pageSize: 10 })

async function loadRecords() {
  recordLoading.value = true
  try {
    const page = await getSparePartRecordPage({
      pageNum: recordQuery.pageNum,
      pageSize: recordQuery.pageSize,
    })
    records.value = page.list
    recordTotal.value = page.total
  } catch {
    // 同上
  } finally {
    recordLoading.value = false
  }
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

/** 出库对话框里可选的工单（未完工的优先展示） */
const repairOptions = computed(() => repairs.value)

onMounted(async () => {
  await Promise.all([loadParts(), loadLowStock(), loadRecords(), loadRepairOptions()])
})
</script>

<template>
  <div class="page">
    <PageHeader title="配件耗材" :desc="headDesc">
      <template #actions>
        <el-button v-if="hasPerm('dev:part:add')" type="primary" @click="openCreate">
          新增配件
        </el-button>
      </template>
    </PageHeader>

    <!-- 库存告警 -->
    <el-alert
      v-if="lowStockParts.length"
      type="warning"
      :closable="false"
      show-icon
      class="low-alert"
    >
      <template #title>有 {{ lowStockParts.length }} 个配件库存告急</template>
      <template #default>
        <div class="low-list">
          <StatusPlate
            v-for="p in lowStockParts"
            :key="p.id"
            tone="warn"
            class="low-tag"
          >
            {{ p.partName }}｜库存 {{ p.stockQuantity }} ≤ 阈值 {{ p.warnThreshold }}
          </StatusPlate>
        </div>
      </template>
    </el-alert>

    <el-tabs v-model="activeTab" class="tabs">
      <!-- ---------- 配件列表 ---------- -->
      <el-tab-pane label="配件列表" name="parts">
        <DataPanel flush>
          <div class="filter-bar">
            <div class="filters">
            <el-input
              v-model="query.keyword"
              placeholder="编码 / 名称 / 型号 / 供应商"
              clearable
              style="width: 230px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
              <el-option
                v-for="opt in PART_STATUS_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-checkbox v-model="query.lowStockOnly" @change="handleSearch">只看库存告急</el-checkbox>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
            </div>
          </div>

          <el-table v-loading="loading" :data="parts">
            <el-table-column prop="partCode" label="配件编码" width="120" />
            <el-table-column prop="partName" label="配件名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="model" label="规格型号" width="120" show-overflow-tooltip />
            <el-table-column label="库存" width="100">
              <template #default="{ row }">
                <!-- 告急的行标红，不用再去心算阈值 -->
                <span :class="{ 'stock-low': isLowStock(row) }">{{ row.stockQuantity }}</span>
                <span v-if="isLowStock(row)" class="unit"> {{ row.unit || '' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="warnThreshold" label="预警阈值" width="90" />
            <el-table-column label="参考单价" width="110">
              <template #default="{ row }">{{ row.unitPrice ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="supplier" label="供应商" min-width="120" show-overflow-tooltip />
            <el-table-column prop="location" label="库位" width="110" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <StatusPlate :tone="row.status === '启用' ? 'ok' : 'idle'">{{ row.status }}</StatusPlate>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="hasPerm('dev:part:stock')" link type="success" @click="openStock(row, 'in')">入库</el-button>
                <el-button v-if="hasPerm('dev:part:stock')" link type="warning" @click="openStock(row, 'out')">出库</el-button>
                <el-button v-if="hasPerm('dev:part:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="hasPerm('dev:part:remove')" link type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState title="还没有配件" desc="新增配件之后，出入库都从这里走" />
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
              @current-change="loadParts"
            />
          </div>
        </DataPanel>
      </el-tab-pane>

      <!-- ---------- 出入库流水 ---------- -->
      <el-tab-pane label="出入库流水" name="records">
        <DataPanel flush>
          <el-table v-loading="recordLoading" :data="records">
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <StatusPlate :tone="row.recordType === '入库' ? 'ok' : 'warn'">
                  {{ row.recordType }}
                </StatusPlate>
              </template>
            </el-table-column>
            <el-table-column prop="partCode" label="配件编码" width="120" />
            <el-table-column prop="partName" label="配件名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="变动前 → 后" width="130">
              <template #default="{ row }">{{ row.beforeStock }} → {{ row.afterStock }}</template>
            </el-table-column>
            <el-table-column label="关联工单" width="100">
              <template #default="{ row }">{{ row.relatedRepairId ? '#' + row.relatedRepairId : '—' }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="110" />
            <el-table-column label="时间" width="165">
              <template #default="{ row }">{{ formatTime(row.recordTime) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <template #empty>
              <EmptyState title="还没有出入库记录" desc="入库或出库之后，这里会留下流水" />
            </template>
          </el-table>

          <div class="table-pager">
            <el-pagination
              v-model:current-page="recordQuery.pageNum"
              v-model:page-size="recordQuery.pageSize"
              :total="recordTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadRecords"
              @current-change="loadRecords"
            />
          </div>
        </DataPanel>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增 / 编辑配件 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="配件编码" prop="partCode">
              <el-input v-model="form.partCode" placeholder="如 PJ-0001" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="配件名称" prop="partName">
              <el-input v-model="form.partName" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="规格型号">
              <el-input v-model="form.model" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计量单位">
              <el-input v-model="form.unit" placeholder="个 / 米 / 套" maxlength="20" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="预警阈值">
              <el-input-number v-model="form.warnThreshold" :min="0" :max="999999" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="参考单价">
              <el-input-number v-model="form.unitPrice" :min="0" :precision="2" :step="1" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="form.supplier" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位">
              <el-input v-model="form.location" placeholder="如 A 区货架" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="opt in PART_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <p class="tip">
        库存不在这里维护 —— 新增后库存为 0，请用列表里的「入库」按钮补上期初库存。
        这样每一次库存变动都有流水可查。
      </p>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 入库 / 出库 -->
    <el-dialog
      v-model="stockVisible"
      :title="`${stockMode === 'in' ? '入库' : '出库'} —— ${stockTarget?.partName ?? ''}`"
      width="500px"
    >
      <el-descriptions :column="2" border class="stock-desc">
        <el-descriptions-item label="配件编码">{{ stockTarget?.partCode }}</el-descriptions-item>
        <el-descriptions-item label="当前库存">
          {{ stockTarget?.stockQuantity }} {{ stockTarget?.unit || '' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form
        ref="stockFormRef"
        :model="stockForm"
        :rules="stockRules"
        label-width="100px"
        class="stock-form"
      >
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="stockForm.quantity" :min="1" :max="999999" />
          <span class="inline-tip">
            {{ stockMode === 'in' ? '入库后' : '出库后' }}库存：
            {{ (stockTarget?.stockQuantity ?? 0) + (stockMode === 'in' ? 1 : -1) * (stockForm.quantity || 0) }}
          </span>
        </el-form-item>

        <el-form-item v-if="stockMode === 'out'" label="关联工单">
          <el-select
            v-model="stockForm.relatedRepairId"
            placeholder="选填，关联到某张维修工单"
            clearable
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="r in repairOptions"
              :key="r.id"
              :label="`#${r.id} ${r.deviceName ?? ''}｜${r.repairStatus}｜${r.faultDesc?.slice(0, 14) ?? ''}`"
              :value="r.id"
            />
          </el-select>
          <span class="inline-tip">关联后，该设备的详情页能看到这次配件更换</span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="stockForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="stockVisible = false">取消</el-button>
        <el-button
          :type="stockMode === 'in' ? 'success' : 'warning'"
          :loading="stockSubmitting"
          @click="submitStock"
        >
          确认{{ stockMode === 'in' ? '入库' : '出库' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.low-alert {
  margin-bottom: 12px;
}

.low-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.low-tag {
  cursor: default;
}

/* el-tabs 只承担标签头，两个页签的内容各自是独立的 DataPanel */
.tabs :deep(.el-tabs__header) {
  margin: 0 0 var(--sp-4);
}

/* 搜索栏：面板顶部的一条 */
.filter-bar {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

/* 分页在面板底部 */
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

.stock-low {
  color: var(--crit);
  font-weight: 600;
}

.unit {
  font-size: 12px;
  color: var(--ink-3);
}

.stock-desc {
  margin-bottom: 16px;
}

.stock-form {
  margin-top: 4px;
}

.inline-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ink-3);
}

.tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-3);
}
</style>
