<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  MAINTENANCE_RESULT,
  MAINTENANCE_RESULT_OPTIONS,
  MAINTENANCE_STATUS,
  MAINTENANCE_STATUS_OPTIONS,
  createPlan,
  daysUntilDue,
  deletePlan,
  executeByPlan,
  getDuePlans,
  getMaintenancePlanPage,
  getMaintenanceRecordPage,
  isOverdue,
  updatePlan,
  type MaintenanceExecuteForm,
  type MaintenancePlan,
  type MaintenancePlanForm,
  type MaintenanceRecord,
} from '../api/maintenance'
import { getDeviceList, type Device } from '../api/device'
import { buildUserNameMap, getUserOptions, type UserOption } from '../api/userOption'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { usePerm } from '../composables/usePerm'
import type { PlateTone } from '../utils/plateTone'

const { hasPerm } = usePerm()

const activeTab = ref('plans')

/**
 * 用户选项：给"负责人"下拉用，也用来把表格里的用户名显示成人名。
 *
 * <p>计划里存的是**用户名**（如 wangqiang），直接显示很难看，
 * 所以拉一次用户列表建映射；历史数据里手填的姓名映射不到，退回原值。
 */
const userOptions = ref<UserOption[]>([])
const userNameMap = computed(() => buildUserNameMap(userOptions.value))

function displayUser(value?: string): string {
  if (!value) return '—'
  return userNameMap.value[value] ?? value
}

async function loadUserOptions() {
  try {
    userOptions.value = await getUserOptions()
  } catch {
    // 拿不到用户列表时下拉为空，仍可手填
  }
}

// ============================================================
// 到期告警
// ============================================================
const duePlans = ref<MaintenancePlan[]>([])

const overdueCount = computed(() => duePlans.value.filter((p) => isOverdue(p.nextMaintenanceDate)).length)

/** 页头那行说明 */
const headDesc = computed(() => {
  if (!duePlans.value.length) return `共 ${total.value} 条维保计划`
  return `共 ${total.value} 条维保计划，其中 ${duePlans.value.length} 台需要安排保养`
})

async function loadDue() {
  try {
    duePlans.value = await getDuePlans()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  }
}

/** 到期情况的铭牌色调：已逾期 / 临期 / 正常。没排期的按中性处理 */
function planTone(nextDate?: string): PlateTone {
  if (!nextDate) return 'idle'
  if (isOverdue(nextDate)) return 'crit'
  const days = daysUntilDue(nextDate)
  return days !== null && days <= 30 ? 'warn' : 'ok'
}

/** 计划启用状态的色调 */
function planStatusTone(status?: string): PlateTone {
  return status === '启用' ? 'ok' : 'idle'
}

/** 保养结果的色调。正常之外（有异常/待处理）都按警告处理 */
function resultTone(result?: string): PlateTone {
  return result === MAINTENANCE_RESULT.NORMAL ? 'ok' : 'warn'
}

function planText(nextDate?: string): string {
  if (!nextDate) return '未排期'
  const days = daysUntilDue(nextDate)
  if (days === null) return nextDate
  if (days < 0) return `已逾期 ${-days} 天`
  if (days === 0) return '今天到期'
  return `还剩 ${days} 天`
}

// ============================================================
// 计划列表
// ============================================================
const loading = ref(false)
const plans = ref<MaintenancePlan[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: '', keyword: '' })

async function loadPlans() {
  loading.value = true
  try {
    const page = await getMaintenancePlanPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
    })
    plans.value = page.list
    total.value = page.total
  } catch {
    // 同上
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  void loadPlans()
}

function handleReset() {
  query.status = ''
  query.keyword = ''
  handleSearch()
}

/** 列表和告警都要刷新 —— 执行完维保，下次到期日变了，告警也要跟着变 */
async function refreshAll() {
  await Promise.all([loadPlans(), loadDue(), loadRecords()])
}

// ============================================================
// 设备下拉（新增计划时选设备）
// ============================================================
const devices = ref<Device[]>([])

async function loadDevices() {
  try {
    devices.value = await getDeviceList()
  } catch {
    // 同上
  }
}

// ============================================================
// 新增 / 编辑计划
// ============================================================
const dialogVisible = ref(false)
const dialogTitle = ref('新增维保计划')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): MaintenancePlanForm {
  return {
    deviceId: undefined,
    planName: '',
    cycleDays: 90,
    lastMaintenanceDate: '',
    nextMaintenanceDate: '',
    maintainer: '',
    status: MAINTENANCE_STATUS.ENABLED,
    remark: '',
  }
}

const form = reactive<MaintenancePlanForm>(emptyForm())

const rules = computed<FormRules<MaintenancePlanForm>>(() => ({
  // 编辑时不允许换设备（后端也会忽略），所以只在新增时要求选
  deviceId:
    editingId.value === null
      ? [{ required: true, message: '请选择设备', trigger: 'change' }]
      : [],
  planName: [
    { required: true, message: '请输入计划名称', trigger: 'blur' },
    { max: 100, message: '计划名称不能超过 100 个字符', trigger: 'blur' },
  ],
  cycleDays: [{ required: true, message: '请填写保养周期', trigger: 'blur' }],
  maintainer: [{ max: 50, message: '负责人不能超过 50 个字符', trigger: 'blur' }],
  remark: [{ max: 200, message: '备注不能超过 200 个字符', trigger: 'blur' }],
}))

async function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增维保计划'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: MaintenancePlan) {
  editingId.value = row.id
  dialogTitle.value = '编辑维保计划'
  Object.assign(form, {
    deviceId: row.deviceId,
    planName: row.planName,
    cycleDays: row.cycleDays,
    lastMaintenanceDate: row.lastMaintenanceDate ?? '',
    nextMaintenanceDate: row.nextMaintenanceDate ?? '',
    maintainer: row.maintainer ?? '',
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

  // 空串要转成 undefined：后端是 LocalDate 强类型，收到空串会反序列化失败
  const payload: MaintenancePlanForm = {
    ...form,
    lastMaintenanceDate: form.lastMaintenanceDate || undefined,
    nextMaintenanceDate: form.nextMaintenanceDate || undefined,
  }

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createPlan(payload)
      ElMessage.success('新增成功')
    } else {
      await updatePlan(editingId.value, payload)
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await refreshAll()
  } catch {
    // 同一设备建第二个计划等业务错误后端返回 400
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: MaintenancePlan) {
  try {
    await ElMessageBox.confirm(`确定要删除计划「${row.planName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deletePlan(row.id)
    ElMessage.success('删除成功')
    await refreshAll()
  } catch {
    // 有维保记录的计划不能删，后端返回 400
  }
}

// ============================================================
// 执行维保
// ============================================================
const execVisible = ref(false)
const execSubmitting = ref(false)
const execTarget = ref<MaintenancePlan | null>(null)
const execFormRef = ref<FormInstance>()
const execForm = reactive<MaintenanceExecuteForm>({
  maintenanceDate: '',
  maintainer: '',
  content: '',
  result: MAINTENANCE_RESULT.NORMAL,
  cost: undefined,
  remark: '',
})

const execRules: FormRules<MaintenanceExecuteForm> = {
  content: [
    { required: true, message: '请填写保养内容', trigger: 'blur' },
  ],
}

async function openExecute(row: MaintenancePlan) {
  execTarget.value = row
  execForm.maintenanceDate = new Date().toISOString().slice(0, 10)
  execForm.maintainer = row.maintainer ?? ''
  execForm.content = ''
  execForm.result = MAINTENANCE_RESULT.NORMAL
  execForm.cost = undefined
  execForm.remark = ''
  execVisible.value = true
  await nextTick()
  execFormRef.value?.clearValidate()
}

async function submitExecute() {
  if (!execTarget.value || !execFormRef.value) return
  const valid = await execFormRef.value.validate().catch(() => false)
  if (!valid) return

  execSubmitting.value = true
  try {
    await executeByPlan(execTarget.value.id, { ...execForm })
    ElMessage.success('维保已记录，下次到期日已自动顺延')
    execVisible.value = false
    await refreshAll()
  } catch {
    // 同上
  } finally {
    execSubmitting.value = false
  }
}

// ============================================================
// 维保记录
// ============================================================
const recordLoading = ref(false)
const records = ref<MaintenanceRecord[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({ pageNum: 1, pageSize: 10 })

async function loadRecords() {
  recordLoading.value = true
  try {
    const page = await getMaintenanceRecordPage({
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

onMounted(async () => {
  await Promise.all([loadPlans(), loadDue(), loadRecords(), loadDevices(), loadUserOptions()])
})
</script>

<template>
  <div class="page">
    <PageHeader title="维保管理" :desc="headDesc">
      <template #actions>
        <el-button v-if="hasPerm('dev:maint:add')" type="primary" @click="openCreate">
          新增计划
        </el-button>
      </template>
    </PageHeader>

    <!-- 到期告警 -->
    <el-alert
      v-if="duePlans.length"
      :type="overdueCount > 0 ? 'error' : 'warning'"
      :closable="false"
      show-icon
      class="due-alert"
    >
      <template #title>
        有 {{ duePlans.length }} 台设备需要安排保养
        <template v-if="overdueCount > 0">（其中 {{ overdueCount }} 台已逾期）</template>
      </template>
      <template #default>
        <div class="due-list">
          <StatusPlate
            v-for="p in duePlans.slice(0, 8)"
            :key="p.id"
            :tone="planTone(p.nextMaintenanceDate)"
            class="due-tag"
          >
            {{ p.deviceName }}｜{{ p.nextMaintenanceDate }}｜{{ planText(p.nextMaintenanceDate) }}
          </StatusPlate>
          <span v-if="duePlans.length > 8" class="more">…等共 {{ duePlans.length }} 台</span>
        </div>
      </template>
    </el-alert>

    <el-tabs v-model="activeTab" class="tabs">
      <!-- ---------- 维保计划 ---------- -->
      <el-tab-pane label="维保计划" name="plans">
        <DataPanel flush>
          <div class="filter-bar">
            <div class="filters">
            <el-input
              v-model="query.keyword"
              placeholder="设备名 / 计划名 / 负责人"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px" @change="handleSearch">
              <el-option
                v-for="opt in MAINTENANCE_STATUS_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
            </div>
          </div>

          <el-table v-loading="loading" :data="plans">
            <el-table-column prop="deviceName" label="设备" min-width="150" show-overflow-tooltip />
            <el-table-column prop="planName" label="计划名称" min-width="130" show-overflow-tooltip />
            <el-table-column label="周期" width="90">
              <template #default="{ row }">{{ row.cycleDays }} 天</template>
            </el-table-column>
            <el-table-column label="上次保养" width="120">
              <template #default="{ row }">{{ row.lastMaintenanceDate ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="下次到期" width="120">
              <template #default="{ row }">{{ row.nextMaintenanceDate ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="到期情况" width="130">
              <template #default="{ row }">
                <StatusPlate :tone="planTone(row.nextMaintenanceDate)">
                  {{ planText(row.nextMaintenanceDate) }}
                </StatusPlate>
              </template>
            </el-table-column>
            <el-table-column prop="maintainer" label="负责人" width="100">
              <template #default="{ row }">{{ displayUser(row.maintainer) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <StatusPlate :tone="planStatusTone(row.status)">{{ row.status }}</StatusPlate>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="hasPerm('dev:maint:execute')" link type="primary" @click="openExecute(row)">执行维保</el-button>
                <el-button v-if="hasPerm('dev:maint:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="hasPerm('dev:maint:remove')" link type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState title="还没有维保计划" desc="建好计划之后，到期前会自动提醒负责人" />
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
              @current-change="loadPlans"
            />
          </div>
        </DataPanel>
      </el-tab-pane>

      <!-- ---------- 维保记录 ---------- -->
      <el-tab-pane label="维保记录" name="records">
        <DataPanel flush>
          <el-table v-loading="recordLoading" :data="records">
            <el-table-column prop="deviceName" label="设备" min-width="150" show-overflow-tooltip />
            <el-table-column prop="maintenanceDate" label="保养日期" width="120" />
            <el-table-column prop="maintainer" label="保养人" width="110" />
            <el-table-column prop="content" label="保养内容" min-width="240" show-overflow-tooltip />
            <el-table-column label="结果" width="140">
              <template #default="{ row }">
                <StatusPlate :tone="resultTone(row.result)">{{ row.result || '—' }}</StatusPlate>
              </template>
            </el-table-column>
            <el-table-column label="费用" width="100">
              <template #default="{ row }">{{ row.cost ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <template #empty>
              <EmptyState title="还没有维保记录" desc="执行一次维保之后，这里会留下存档" />
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

    <!-- 新增 / 编辑计划 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备" prop="deviceId">
          <el-select
            v-model="form.deviceId"
            :disabled="editingId !== null"
            placeholder="选择要保养的设备"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in devices"
              :key="d.id"
              :label="`${d.deviceName}${d.assetCode ? '（' + d.assetCode + '）' : ''}`"
              :value="d.id as number"
            />
          </el-select>
          <span v-if="editingId !== null" class="inline-tip">设备不可更换</span>
        </el-form-item>

        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="form.planName" placeholder="如：季度保养" maxlength="100" />
        </el-form-item>

        <el-form-item label="保养周期(天)" prop="cycleDays">
          <el-input-number v-model="form.cycleDays" :min="1" :max="3650" />
          <span class="inline-tip">执行一次维保后，下次到期日按这个天数顺延</span>
        </el-form-item>

        <el-form-item label="上次保养日">
          <el-date-picker
            v-model="form.lastMaintenanceDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="没有可留空"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="下次到期日">
          <el-date-picker
            v-model="form.nextMaintenanceDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="留空则按「上次保养日 + 周期」自动算"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="负责人">
          <!--
            可选系统用户，也允许手填。
            **从下拉里选的会收到站内通知**（存的是用户名，通知按用户名解析账号）；
            手填的姓名（外部保养人员）没有账号，收不到。
          -->
          <el-select
            v-model="form.maintainer"
            placeholder="选系统用户，或输入外部人员姓名"
            filterable
            allow-create
            default-first-option
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="opt in userOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <span class="inline-tip">选系统用户才会收到维保到期的站内提醒</span>
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="opt in MAINTENANCE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
          <span class="inline-tip">停用的计划不参与到期告警</span>
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 执行维保 -->
    <el-dialog
      v-model="execVisible"
      :title="`执行维保 —— ${execTarget?.deviceName ?? ''}`"
      width="520px"
    >
      <el-form ref="execFormRef" :model="execForm" :rules="execRules" label-width="100px">
        <el-form-item label="保养日期">
          <el-date-picker
            v-model="execForm.maintenanceDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="保养人">
          <el-input v-model="execForm.maintainer" placeholder="选填，默认取当前登录用户" maxlength="50" />
        </el-form-item>
        <el-form-item label="保养内容" prop="content">
          <el-input
            v-model="execForm.content"
            type="textarea"
            :rows="4"
            placeholder="做了什么：清洁、检查、校准、更换…"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="保养结果">
          <el-select v-model="execForm.result" style="width: 100%">
            <el-option
              v-for="opt in MAINTENANCE_RESULT_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="费用">
          <el-input-number v-model="execForm.cost" :min="0" :precision="2" :step="10" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="execForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <p class="tip">
        提交后会写入一条维保记录，并把计划的下次到期日顺延一个周期（
        {{ execTarget?.cycleDays ?? 0 }} 天），不需要手工改日期。
      </p>

      <template #footer>
        <el-button @click="execVisible = false">取消</el-button>
        <el-button type="primary" :loading="execSubmitting" @click="submitExecute">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.due-alert {
  margin-bottom: 12px;
}

.due-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.due-tag {
  cursor: default;
}

.more {
  font-size: 12px;
  line-height: 24px;
}

/* el-tabs 只承担标签头。两个页签的内容各自是独立的 DataPanel ——
   再给 .tabs 一层面板外观的话会变成盒子套盒子。
   代价是标签头直接落在工作区底上，这在本项目里是一致的做法 */
.tabs :deep(.el-tabs__header) {
  margin: 0 0 var(--sp-4);
}

/* 搜索栏：面板顶部的一条 */
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
  gap: 10px;
  flex-wrap: wrap;
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
