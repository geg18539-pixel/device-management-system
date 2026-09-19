<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  AI_STATUS,
  LOG_TYPE,
  REPAIR_STATUS,
  REPAIR_STATUS_OPTIONS,
  addRepairLog,
  deleteRepair,
  finishRepair,
  getRepairById,
  getRepairLogs,
  getRepairPage,
  reanalyzeRepair,
  splitLines,
  type DeviceRepair,
  type DeviceRepairLog,
} from '../api/deviceRepair'

// ---------------- 列表 ----------------
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

function aiStatusTagType(status?: string): 'info' | 'warning' | 'success' | 'danger' {
  if (status === AI_STATUS.RUNNING) return 'warning'
  if (status === AI_STATUS.DONE) return 'success'
  if (status === AI_STATUS.FAILED) return 'danger'
  return 'info'
}

function severityTagType(severity?: string): 'danger' | 'warning' | 'info' {
  if (severity === '高') return 'danger'
  if (severity === '中') return 'warning'
  return 'info'
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function canFinish(row: DeviceRepair): boolean {
  return row.repairStatus !== REPAIR_STATUS.FINISHED
}

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

// ---------------- 详情抽屉（AI 分析 + 维修日志）----------------
const drawerVisible = ref(false)
const detail = ref<DeviceRepair | null>(null)
const logs = ref<DeviceRepairLog[]>([])
const logLoading = ref(false)
const newLog = ref('')
const addingLog = ref(false)
const reanalyzing = ref(false)

/** AI 还在分析时轮询刷新的定时器 */
let pollTimer: number | null = null

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function refreshDetail() {
  if (!detail.value?.id) return
  try {
    // 抽屉里那份是列表数据的副本，AI 分析结果是后台异步更新的，
    // 必须重新按 id 拉一次才能看到最新状态
    detail.value = await getRepairById(detail.value.id)
  } catch {
    // 拉取失败就保持现状，用户还可以手动关掉重开
  }
}

/** AI 分析中时每 3 秒拉一次，出结果就停 */
function startPollingIfNeeded() {
  stopPolling()
  if (detail.value?.aiStatus !== AI_STATUS.RUNNING) return

  pollTimer = window.setInterval(async () => {
    await refreshDetail()
    await loadList()
    if (detail.value?.aiStatus !== AI_STATUS.RUNNING) {
      stopPolling()
      if (detail.value?.aiStatus === AI_STATUS.DONE) {
        ElMessage.success('AI 分析已完成')
      }
    }
  }, 3000)
}

async function loadLogs(repairId: number) {
  logLoading.value = true
  try {
    logs.value = await getRepairLogs(repairId)
  } catch {
    // 同上
  } finally {
    logLoading.value = false
  }
}

async function openDetail(row: DeviceRepair) {
  if (!row.id) return
  detail.value = row
  logs.value = []
  newLog.value = ''
  drawerVisible.value = true

  await loadLogs(row.id)
  startPollingIfNeeded()
}

function closeDrawer() {
  stopPolling()
  drawerVisible.value = false
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
    await loadLogs(detail.value.id)
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
    // 后端立即返回，前端先切成"分析中"，再靠轮询拿最终结果
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
const detailLogs = computed(() => logs.value)

onMounted(loadList)
onBeforeUnmount(stopPolling)
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
      <span class="hint">工单由设备页的「报修」操作产生，创建后会自动触发 AI 分析</span>
    </div>

    <el-table v-loading="loading" :data="repairList" border stripe>
      <el-table-column prop="id" label="工单号" width="80" />
      <el-table-column prop="deviceName" label="设备" min-width="130" show-overflow-tooltip />
      <el-table-column prop="faultDesc" label="故障描述" min-width="180" show-overflow-tooltip />

      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.repairStatus)" disable-transitions>
            {{ row.repairStatus }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- AI 分析结果 -->
      <el-table-column label="AI 分析" width="95">
        <template #default="{ row }">
          <el-tag :type="aiStatusTagType(row.aiStatus)" size="small" disable-transitions>
            {{ row.aiStatus || '—' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="严重程度" width="90">
        <template #default="{ row }">
          <el-tag
            v-if="row.aiSeverity"
            :type="severityTagType(row.aiSeverity)"
            size="small"
            disable-transitions
          >
            {{ row.aiSeverity }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <el-table-column prop="reporter" label="报修人" width="95">
        <template #default="{ row }">
          <span :class="{ muted: !row.reporter }">{{ row.reporter || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="repairer" label="维修人" width="95">
        <template #default="{ row }">
          <span :class="{ muted: !row.repairer }">{{ row.repairer || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="报修时间" width="160">
        <template #default="{ row }">{{ formatTime(row.reportTime) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

    <!-- 完工弹窗 -->
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
      <p class="tip">完工后设备状态会自动从「维修中」改回「在线」，并写入一条状态变更日志。</p>

      <template #footer>
        <el-button @click="finishVisible = false">取消</el-button>
        <el-button type="primary" :loading="finishSubmitting" @click="submitFinish">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`工单 #${detail?.id ?? ''} —— ${detail?.deviceName ?? ''}`"
      size="620px"
      @close="closeDrawer"
    >
      <div v-if="detail" class="detail">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单状态">
            <el-tag :type="statusTagType(detail.repairStatus)" size="small" disable-transitions>
              {{ detail.repairStatus }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="报修人">{{ detail.reporter || '—' }}</el-descriptions-item>
          <el-descriptions-item label="维修人">{{ detail.repairer || '—' }}</el-descriptions-item>
          <el-descriptions-item label="维修费用">
            {{ detail.cost != null ? '¥ ' + detail.cost : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="报修时间">
            {{ formatTime(detail.reportTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="完工时间">
            {{ formatTime(detail.finishTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="故障描述" :span="2">
            {{ detail.faultDesc }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- AI 分析 -->
        <div class="section">
          <div class="section-head">
            <h3>AI 智能分析</h3>
            <div class="head-right">
              <el-tag :type="aiStatusTagType(detail.aiStatus)" size="small" disable-transitions>
                {{ detail.aiStatus || '未分析' }}
              </el-tag>
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

          <!-- 分析中 -->
          <el-alert
            v-if="detail.aiStatus === AI_STATUS.RUNNING"
            type="warning"
            :closable="false"
            show-icon
          >
            正在调用本地模型分析故障描述，通常需要十几秒到一分钟。页面会自动刷新结果。
          </el-alert>

          <!-- 失败 -->
          <el-alert
            v-else-if="detail.aiStatus === AI_STATUS.FAILED"
            type="error"
            :closable="false"
            show-icon
          >
            <template #title>分析失败</template>
            <div class="fail-body">
              {{ detail.aiError || '模型未返回可用结果' }}
              <p class="fail-tip">
                工单本身不受影响，可以正常维修和完工。确认 Ollama 已启动后点「开始分析」重试。
              </p>
            </div>
          </el-alert>

          <!-- 未分析（旧工单） -->
          <el-alert
            v-else-if="!detail.aiStatus || detail.aiStatus === AI_STATUS.PENDING"
            type="info"
            :closable="false"
            show-icon
          >
            这张工单还没有分析结果（可能是引入 AI 之前创建的）。点「开始分析」生成维修建议。
          </el-alert>

          <!-- 结果 -->
          <template v-else-if="detail.aiStatus === AI_STATUS.DONE">
            <div class="ai-meta">
              <span class="meta-item">
                严重程度
                <el-tag :type="severityTagType(detail.aiSeverity)" size="small" disable-transitions>
                  {{ detail.aiSeverity || '—' }}
                </el-tag>
              </span>
              <span class="meta-item">
                预计工时
                <strong>{{ detail.aiEstimatedHours ?? '—' }}</strong> 小时
              </span>
              <span class="meta-item muted">
                {{ detail.aiModel }} · {{ formatTime(detail.aiAnalyzedAt) }}
              </span>
            </div>

            <div v-if="aiCauses.length" class="block">
              <h4>可能原因</h4>
              <ul class="list">
                <li v-for="(item, i) in aiCauses" :key="i">{{ item }}</li>
              </ul>
            </div>

            <div v-if="aiSteps.length" class="block">
              <h4>建议维修步骤</h4>
              <ol class="list">
                <li v-for="(item, i) in aiSteps" :key="i">{{ item }}</li>
              </ol>
            </div>

            <p class="disclaimer">
              以上为本地模型的自动建议，仅供参考，请结合实际现场情况判断。
            </p>
          </template>
        </div>

        <!-- 维修日志 -->
        <div class="section">
          <div class="section-head">
            <h3>维修日志</h3>
            <span class="muted">{{ detailLogs.length }} 条</span>
          </div>

          <div v-loading="logLoading">
            <el-timeline v-if="detailLogs.length">
              <el-timeline-item
                v-for="log in detailLogs"
                :key="log.id"
                :timestamp="formatTime(log.logTime)"
                placement="top"
              >
                <div class="log-item">
                  <el-tag
                    size="small"
                    :type="log.logType === LOG_TYPE.CREATED ? 'info'
                      : log.logType === LOG_TYPE.STATUS ? 'success' : 'primary'"
                    disable-transitions
                  >
                    {{ log.logType }}
                  </el-tag>
                  <span class="log-operator">{{ log.operator || '系统' }}</span>
                  <p class="log-content">{{ log.content }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无日志" :image-size="60" />
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
            <el-button
              type="primary"
              size="small"
              class="log-submit"
              :loading="addingLog"
              @click="submitLog"
            >
              添加记录
            </el-button>
          </div>
          <p class="tip">日志只增不改：维修记录属于追溯性资料，记录人不填会自动取当前登录用户。</p>
        </div>
      </div>
    </el-drawer>
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

.detail {
  text-align: left;
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
  color: var(--text-h);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 10px 14px;
  margin-bottom: 14px;
  border-radius: 6px;
  background: var(--code-bg);
  font-size: 13px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text);
}

.block {
  margin-bottom: 16px;
}

.block h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-h);
}

.list {
  margin: 0;
  padding-left: 22px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text);
}

.disclaimer {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: #94a3b8;
}

.fail-body {
  font-size: 13px;
  line-height: 1.7;
}

.fail-tip {
  margin: 6px 0 0;
  color: #94a3b8;
}

.log-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.log-operator {
  font-size: 12px;
  color: #94a3b8;
}

.log-content {
  flex-basis: 100%;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text);
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
  color: #94a3b8;
}
</style>
