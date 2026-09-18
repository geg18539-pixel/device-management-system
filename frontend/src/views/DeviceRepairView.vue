<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  REPAIR_STATUS,
  REPAIR_STATUS_OPTIONS,
  deleteRepair,
  finishRepair,
  getRepairPage,
  type DeviceRepair,
} from '../api/deviceRepair'

const loading = ref(false)
const repairList = ref<DeviceRepair[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  repairStatus: '',
})

async function loadList() {
  loading.value = true
  try {
    const page = await getRepairPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      repairStatus: query.repairStatus || undefined,
    })
    repairList.value = page.list
    total.value = page.total
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  void loadList()
}

function handleReset() {
  query.repairStatus = ''
  handleSearch()
}

function statusTagType(status: string): 'warning' | 'primary' | 'success' {
  if (status === REPAIR_STATUS.PENDING) return 'warning'
  if (status === REPAIR_STATUS.REPAIRING) return 'primary'
  return 'success'
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

/** 只有未完工的工单才能操作完工 */
function canFinish(row: DeviceRepair): boolean {
  return row.repairStatus !== REPAIR_STATUS.FINISHED
}

/** 已完成的工单才能删除（后端也是这个规则：未完工的删了设备会永远停在"维修中"） */
function canDelete(row: DeviceRepair): boolean {
  return row.repairStatus === REPAIR_STATUS.FINISHED
}

// ---------------- 完工 ----------------
const finishVisible = ref(false)
const finishSubmitting = ref(false)
const finishTarget = ref<DeviceRepair | null>(null)
const finishFormRef = ref<FormInstance>()

const finishForm = reactive({
  repairer: '',
  cost: undefined as number | undefined,
  remark: '',
})

const finishRules: FormRules<{ repairer: string; cost?: number; remark: string }> = {
  repairer: [
    { required: true, message: '请输入维修人', trigger: 'blur' },
    { max: 50, message: '维修人不能超过 50 个字符', trigger: 'blur' },
  ],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }],
}

async function openFinish(row: DeviceRepair) {
  finishTarget.value = row
  finishForm.repairer = ''
  finishForm.cost = undefined
  finishForm.remark = ''
  finishVisible.value = true
  await nextTick()
  finishFormRef.value?.clearValidate()
}

async function submitFinish() {
  if (!finishTarget.value?.id || !finishFormRef.value) return

  const valid = await finishFormRef.value.validate().catch(() => false)
  if (!valid) return

  finishSubmitting.value = true
  try {
    await finishRepair(finishTarget.value.id, {
      repairer: finishForm.repairer,
      cost: finishForm.cost,
      remark: finishForm.remark || undefined,
    })
    ElMessage.success('工单已完成，设备状态已改回「在线」')
    finishVisible.value = false
    await loadList()
  } catch {
    // 同上
  } finally {
    finishSubmitting.value = false
  }
}

async function handleDelete(row: DeviceRepair) {
  if (!row.id) return

  try {
    await ElMessageBox.confirm(`确定要删除工单「${row.deviceName}」吗？`, '删除确认', {
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
    await loadList()
  } catch {
    // 同上
  }
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <div class="filters">
        <el-select
          v-model="query.repairStatus"
          placeholder="全部状态"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option
            v-for="opt in REPAIR_STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <span class="hint">工单由设备页的「报修」操作产生</span>
    </div>

    <el-table v-loading="loading" :data="repairList" border stripe>
      <el-table-column prop="id" label="工单号" width="80" />
      <el-table-column prop="deviceName" label="设备" min-width="140" show-overflow-tooltip />
      <el-table-column prop="faultDesc" label="故障描述" min-width="200" show-overflow-tooltip />

      <el-table-column label="状态" width="95">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.repairStatus)" disable-transitions>
            {{ row.repairStatus }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="reporter" label="报修人" width="100">
        <template #default="{ row }">
          <span :class="{ muted: !row.reporter }">{{ row.reporter || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="repairer" label="维修人" width="100">
        <template #default="{ row }">
          <span :class="{ muted: !row.repairer }">{{ row.repairer || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="维修费用" width="100">
        <template #default="{ row }">
          <span v-if="row.cost === null || row.cost === undefined" class="muted">—</span>
          <span v-else>¥ {{ row.cost }}</span>
        </template>
      </el-table-column>

      <el-table-column label="报修时间" width="160">
        <template #default="{ row }">{{ formatTime(row.reportTime) }}</template>
      </el-table-column>

      <el-table-column label="完工时间" width="160">
        <template #default="{ row }">
          <span :class="{ muted: !row.finishTime }">{{ formatTime(row.finishTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canFinish(row)" link type="primary" @click="openFinish(row)">
            完工
          </el-button>
          <el-button v-if="canDelete(row)" link type="danger" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无维修工单" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @size-change="handleSearch"
      @current-change="loadList"
    />

    <el-dialog
      v-model="finishVisible"
      :title="`工单完工 —— ${finishTarget?.deviceName ?? ''}`"
      width="460px"
    >
      <el-form ref="finishFormRef" :model="finishForm" :rules="finishRules" label-width="80px">
        <el-form-item label="维修人" prop="repairer">
          <el-input v-model="finishForm.repairer" placeholder="谁修的" maxlength="50" />
        </el-form-item>
        <el-form-item label="维修费用">
          <el-input-number
            v-model="finishForm.cost"
            :min="0"
            :precision="2"
            :step="10"
            placeholder="选填"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="finishForm.remark"
            type="textarea"
            :rows="2"
            placeholder="选填，比如换了什么零件"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <p class="tip">完工后设备状态会自动从「维修中」改回「在线」。</p>

      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" :loading="finishSubmitting" @click="submitFinish">确定</el-button>
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
  margin-bottom: 16px;
}

.filters {
  display: flex;
  gap: 10px;
}

.hint {
  font-size: 13px;
  color: #94a3b8;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.muted {
  color: #cbd5e1;
}

.tip {
  margin-top: 4px;
  font-size: 12px;
  color: #94a3b8;
}
</style>
