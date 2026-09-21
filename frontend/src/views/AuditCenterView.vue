<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  AUDIT_BIZ_TYPE,
  auditToneOf,
  exportAuditLogs,
  getAuditActions,
  pageAuditLogs,
  type AuditLog,
  type AuditLogQuery,
} from '../api/audit'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { usePerm } from '../composables/usePerm'

const { hasPerm } = usePerm()

const loading = ref(false)
const exporting = ref(false)
const logList = ref<AuditLog[]>([])
const total = ref(0)

/** 动作字典（代码 → 中文）。由后端提供，前端不再抄一份文案 */
const actionOptions = ref<{ value: string; label: string }[]>([])

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  bizType: '' as '' | 'DEVICE' | 'PART',
  action: '',
  operator: '',
  keyword: '',
  auditTimeBegin: '',
  auditTimeEnd: '',
})

const timeRange = ref<[string, string] | null>(null)

/** 组装请求条件。空串一律转 undefined —— 传空串后端会当成"等于空"来筛 */
function buildQuery(): AuditLogQuery {
  return {
    bizType: query.bizType || undefined,
    action: query.action || undefined,
    operator: query.operator.trim() || undefined,
    keyword: query.keyword.trim() || undefined,
    auditTimeBegin: query.auditTimeBegin || undefined,
    auditTimeEnd: query.auditTimeEnd || undefined,
  }
}

async function loadList() {
  loading.value = true
  try {
    const page = await pageAuditLogs(buildQuery(), query.pageNum, query.pageSize)
    logList.value = page.list
    total.value = page.total
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  query.auditTimeBegin = timeRange.value?.[0] ?? ''
  query.auditTimeEnd = timeRange.value?.[1] ?? ''
  void loadList()
}

function handleReset() {
  query.bizType = ''
  query.action = ''
  query.operator = ''
  query.keyword = ''
  timeRange.value = null
  query.auditTimeBegin = ''
  query.auditTimeEnd = ''
  handleSearch()
}

async function handleExport() {
  exporting.value = true
  try {
    await exportAuditLogs(buildQuery())
  } catch {
    // 同上
  } finally {
    exporting.value = false
  }
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

/**
 * 列表里的变更摘要。
 *
 * <p>表格列宽有限，把十几个字段全铺开会把行撑得没法看，
 * 所以这里只给"改了哪几个字段、共几项"，具体的新旧值放在展开行里。
 * 没有字段变更的（比如删除）直接说明原因，而不是留空白让人以为数据丢了。
 */
function changesSummary(row: AuditLog): string {
  if (!row.changes?.length) {
    return row.remark || '无字段变更'
  }
  const names = row.changes.map((c) => c.field)
  if (names.length <= 3) {
    return names.join('、')
  }
  return `${names.slice(0, 3).join('、')} 等 ${names.length} 项`
}

onMounted(async () => {
  // 字典拿不到也不影响主列表（动作文案后端已经随每条记录返回了），
  // 只是筛选下拉会空着 —— 所以这里失败不提示错误
  try {
    const dict = await getAuditActions()
    actionOptions.value = Object.entries(dict ?? {}).map(([value, label]) => ({ value, label }))
  } catch {
    actionOptions.value = []
  }
  await loadList()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="资产审计中心"
      desc="设备和配件的全生命周期变更记录：谁、在什么时候、把哪个字段从什么改成了什么"
    >
      <template #actions>
        <el-button
          v-if="hasPerm('sys:audit:export')"
          :loading="exporting"
          @click="handleExport"
        >
          导出审计报表
        </el-button>
      </template>
    </PageHeader>

    <DataPanel flush>
      <div class="filter-bar">
        <div class="filters">
          <el-input
            v-model="query.keyword"
            placeholder="编号 / 名称 / 操作人"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-select v-model="query.bizType" placeholder="全部类型" clearable style="width: 120px">
            <el-option label="设备" :value="AUDIT_BIZ_TYPE.DEVICE" />
            <el-option label="配件" :value="AUDIT_BIZ_TYPE.PART" />
          </el-select>
          <el-select v-model="query.action" placeholder="全部动作" clearable style="width: 130px">
            <el-option
              v-for="opt in actionOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input
            v-model="query.operator"
            placeholder="按操作人"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-date-picker
            v-model="timeRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 250px"
          />
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="logList" height="520">
        <!-- 展开行看字段级的新旧值。审计的重点就是"具体改了什么"，
             所以不放在弹窗里，原地展开能和上下文对照着看 -->
        <el-table-column type="expand" width="42">
          <template #default="{ row }">
            <div class="change-detail">
              <table v-if="row.changes?.length" class="change-table">
                <thead>
                  <tr>
                    <th>字段</th>
                    <th>变更前</th>
                    <th>变更后</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in row.changes" :key="c.field">
                    <td class="col-field">{{ c.field }}</td>
                    <td class="col-before">{{ c.before }}</td>
                    <td class="col-after">{{ c.after }}</td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="change-empty">
                这次操作没有字段级变更{{ row.remark ? `：${row.remark}` : '' }}
              </p>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="时间" width="166">
          <template #default="{ row }">
            <span class="num">{{ formatTime(row.auditTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="对象" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="obj-name">{{ row.bizName || '—' }}</span>
            <span class="obj-meta">{{ row.bizTypeLabel }}<template v-if="row.bizCode"> · {{ row.bizCode }}</template></span>
          </template>
        </el-table-column>

        <el-table-column label="动作" width="96">
          <template #default="{ row }">
            <StatusPlate :tone="auditToneOf(row.action)" :dot="false">
              {{ row.actionLabel }}
            </StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="变更内容" min-width="230" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: !row.changes?.length }">{{ changesSummary(row) }}</span>
            <span v-if="row.changeCount" class="chg-count num">{{ row.changeCount }} 项</span>
          </template>
        </el-table-column>

        <el-table-column label="操作人" width="120">
          <template #default="{ row }">
            {{ row.operatorName || row.operator || '—' }}
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState
            title="没有符合条件的审计记录"
            desc="换个筛选条件试试；这个页面记录的是设备和配件的每一次变更"
          />
        </template>
      </el-table>

      <div class="table-pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSearch"
          @current-change="loadList"
        />
      </div>
    </DataPanel>

    <p class="footnote">
      审计记录只读，不提供修改和删除接口 —— 变更记录一旦可以被人改动，就没有作为依据的价值了。
      需要清理请直接操作数据库。
    </p>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.filter-bar {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

/* 分页在面板底部 */
.table-pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--sp-3) var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

.muted {
  color: var(--ink-3);
}

/* 对象列两行：名称在上、类型和编号在下。
   编号用等宽，0/O 和 1/l 能分清 */
.obj-name {
  display: block;
  color: var(--ink-1);
}

.obj-meta {
  display: block;
  font-size: 11px;
  color: var(--ink-3);
}

.chg-count {
  margin-left: 6px;
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- 展开行：字段级新旧值 ---------- */
.change-detail {
  padding: var(--sp-3) var(--sp-4) var(--sp-3) 54px;
}

.change-table {
  border-collapse: collapse;
  font-size: 12px;
  min-width: 420px;
}

.change-table th,
.change-table td {
  padding: 5px 12px 5px 0;
  text-align: left;
  vertical-align: top;
}

.change-table th {
  font-weight: 500;
  color: var(--ink-3);
  border-bottom: 1px solid var(--line);
  padding-bottom: 4px;
}

.change-table td {
  border-bottom: 1px solid var(--line-soft);
}

.col-field {
  width: 110px;
  color: var(--ink-2);
}

.col-before {
  width: 180px;
  color: var(--ink-3);
  /* 旧值加删除线：一眼看出"这是被改掉的那个"，不用读文字判断哪边是新的 */
  text-decoration: line-through;
  text-decoration-color: var(--line-strong);
}

.col-after {
  color: var(--ink-1);
  font-weight: 500;
}

.change-empty {
  font-size: 12px;
  color: var(--ink-3);
}

.footnote {
  margin-top: var(--sp-3);
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}
</style>
