<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  BUSINESS_TYPE_OPTIONS,
  businessTypeLabel,
  businessTypeTone,
  getOperLogPage,
  type OperLog,
} from '../api/operLog'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'

const loading = ref(false)
const logs = ref<OperLog[]>([])
const total = ref(0)

const query = reactive({
  pageNum: 1,
  pageSize: 20,
  operatorName: '',
  title: '',
  businessType: '',
  status: '',
  keyword: '',
})

const dateRange = ref<[string, string] | null>(null)

function currentParams() {
  return {
    operatorName: query.operatorName || undefined,
    title: query.title || undefined,
    businessType: query.businessType || undefined,
    status: query.status || undefined,
    keyword: query.keyword || undefined,
    beginDate: dateRange.value?.[0] || undefined,
    endDate: dateRange.value?.[1] || undefined,
  }
}

async function loadList() {
  loading.value = true
  try {
    const page = await getOperLogPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      ...currentParams(),
    })
    logs.value = page.list
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
  query.operatorName = ''
  query.title = ''
  query.businessType = ''
  query.status = ''
  query.keyword = ''
  dateRange.value = null
  handleSearch()
}

/** 只看越权被拒的记录 —— 排查安全问题时最常用的一筛 */
function handleOnlyDenied() {
  query.title = '越权访问被拒绝'
  query.status = '失败'
  handleSearch()
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

/** 耗时：超过 1 秒的标出来，方便一眼看到慢接口 */
function costText(ms?: number): string {
  if (ms === undefined || ms === null) return '—'
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`
}

// ---------------- 详情 ----------------
const detailVisible = ref(false)
const detail = ref<OperLog | null>(null)

function openDetail(row: OperLog) {
  detail.value = row
  detailVisible.value = true
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader
      title="操作日志"
      desc="记录谁在什么时候改了什么；越权被拒绝的请求也会留在这里，是等保审计要看的部分"
    />

    <DataPanel flush>
      <div class="filter-bar">
        <div class="search-row">
        <el-input
          v-model="query.operatorName"
          placeholder="操作人"
          clearable
          style="width: 140px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="query.title"
          placeholder="操作模块"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.businessType" placeholder="全部类型" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="opt in BUSINESS_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部结果" clearable style="width: 120px" @change="handleSearch">
          <el-option label="成功" value="成功" />
          <el-option label="失败" value="失败" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 250px"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button type="danger" plain @click="handleOnlyDenied">只看越权被拒</el-button>
      </div>

      <div class="search-row second">
        <el-input
          v-model="query.keyword"
          placeholder="接口地址 / 方法名关键词"
          clearable
          style="width: 320px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <span class="hint">
          操作日志只读、不可修改删除；越权被拒绝的请求也会记录在这里
        </span>
        </div>
      </div>

      <el-table v-loading="loading" :data="logs">
      <el-table-column label="操作模块" width="130">
        <template #default="{ row }">
          <span :class="{ denied: row.title === '越权访问被拒绝' }">{{ row.title || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
            <StatusPlate :tone="businessTypeTone(row.businessType)" :dot="false">
              {{ businessTypeLabel(row.businessType) }}
            </StatusPlate>
        </template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="110">
        <template #default="{ row }">
          <span :class="{ muted: !row.operatorName }">{{ row.operatorName || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" show-overflow-tooltip />
      <el-table-column label="请求" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="method">{{ row.requestMethod }}</span>
          <span class="url">{{ row.requestUrl || row.method }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <StatusPlate :tone="row.status === '成功' ? 'ok' : 'crit'">
            {{ row.status }}
          </StatusPlate>
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">{{ costText(row.costTime) }}</template>
      </el-table-column>
      <el-table-column label="操作时间" width="165">
        <template #default="{ row }">{{ formatTime(row.operTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <EmptyState title="没有符合条件的操作日志" desc="换个筛选条件试试；「只看越权被拒」能筛出被挡下来的请求" />
      </template>
      </el-table>

      <div class="table-pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="loadList"
        />
      </div>
    </DataPanel>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="操作日志详情" width="620px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="操作模块">{{ detail.title || '—' }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ businessTypeLabel(detail.businessType) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ detail.operatorName || '—' }}（ID {{ detail.operatorId ?? '—' }}）
        </el-descriptions-item>
        <el-descriptions-item label="来源 IP">{{ detail.ip || '—' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法与地址">
          <span class="method">{{ detail.requestMethod }}</span> {{ detail.requestUrl || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="被调用的方法">{{ detail.method || '—' }}</el-descriptions-item>
        <el-descriptions-item label="执行结果">
          <StatusPlate :tone="detail.status === '成功' ? 'ok' : 'crit'">
            {{ detail.status }}
          </StatusPlate>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ costText(detail.costTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作时间">{{ formatTime(detail.operTime) }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="失败原因">
          <span class="error">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* 搜索栏：面板顶部的一条 */
.filter-bar {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-row.second {
  margin-top: 10px;
}

.hint {
  font-size: 12px;
  color: var(--ink-3);
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

.denied {
  color: var(--crit);
  font-weight: 600;
}

.method {
  display: inline-block;
  min-width: 52px;
  margin-right: 6px;
  font-size: 12px;
  color: var(--ink-2);
}

.url {
  font-size: 13px;
  color: var(--ink-2);
}

.error {
  color: var(--crit);
}
</style>
