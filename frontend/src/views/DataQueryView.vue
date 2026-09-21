<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { askData, getQueryCatalog, type QueryCatalog, type QueryResult } from '../api/query'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'

const question = ref('')
const asking = ref(false)
const result = ref<QueryResult | null>(null)
const catalog = ref<QueryCatalog | null>(null)

/** 问一句。传 text 表示点的是示例问题，顺手填进输入框 */
async function ask(text?: string) {
  const q = (text ?? question.value).trim()
  if (!q) {
    ElMessage.info('先写一句想问的')
    return
  }
  question.value = q
  asking.value = true
  try {
    result.value = await askData(q)
  } catch {
    // 「没理解你的问题」后端返回 400，提示文案由 request.ts 的拦截器统一弹出。
    // 这里必须把上一次的结果清掉 —— 否则用户会对着旧结果以为这次也问成功了
    result.value = null
  } finally {
    asking.value = false
  }
}

onMounted(async () => {
  try {
    catalog.value = await getQueryCatalog()
  } catch {
    // 拿不到就不显示提示区，不影响主流程
  }
})

/** el-table 要对象数组，把后端的 string[][] 包一层 */
const tableRows = computed(() =>
  (result.value?.rows ?? []).map((cells, i) => ({ key: i, cells })),
)

/**
 * 单元格是不是纯数字，决定要不要用等宽数字。
 *
 * <p>按**内容**判断而不是按列下标：同一个页面里既有"部门 5 台"这种
 * 第一列是文字、第二列是数字的聚合表，也有"库存 4 / 阈值 10"这种
 * 数字分散在中间几列的清单表，按下标写死一定会有一边不对。
 */
function isNumeric(text: string): boolean {
  return /^-?\d+(\.\d+)?$/.test(text)
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="数据问答"
      desc="用一句中文问设备、工单、配件的数据。数字全部来自数据库，同一句话问两次结果一定一样"
    />

    <!-- ---------- 提问 ---------- -->
    <DataPanel>
      <div class="ask-bar">
        <el-input
          v-model="question"
          placeholder="例如：各部门分别有多少台设备"
          clearable
          maxlength="200"
          @keyup.enter="ask()"
        />
        <el-button type="primary" :loading="asking" @click="ask()">提问</el-button>
      </div>

      <!-- 示例问题从后端来（跟着查询目录一起变），点一下直接问 -->
      <div v-if="catalog?.examples?.length" class="examples">
        <span class="examples-label">试试这些：</span>
        <el-button
          v-for="e in catalog.examples"
          :key="e"
          size="small"
          text
          class="example-btn"
          @click="ask(e)"
        >
          {{ e }}
        </el-button>
      </div>
    </DataPanel>

    <!-- ---------- 结果 ---------- -->
    <DataPanel
      v-if="result"
      class="result-panel"
      :title="result.metricLabel"
      :hint="result.groupByLabel ? `按「${result.groupByLabel}」分组` : ''"
    >
      <!-- 「我理解成什么」必须是这里最显眼的一行：小模型会把问题理解错，
           让用户一眼看出来，比让他对着结果猜要强得多 -->
      <p class="understanding">
        <span class="u-tag">我理解你在问</span>
        {{ result.understanding }}
      </p>

      <p class="summary">{{ result.summary }}</p>

      <!-- 单个数字（不分组的聚合）：给一个大号读数，不放表格 -->
      <div v-if="!result.listMode && !result.groupByLabel" class="single">
        <b class="num single-value">{{ result.rows[0]?.[0] ?? '—' }}</b>
      </div>

      <el-table v-else :data="tableRows" class="result-table">
        <el-table-column
          v-for="(col, i) in result.columns"
          :key="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span :class="{ num: isNumeric(row.cells[i]) }">{{ row.cells[i] }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="没有符合条件的数据" desc="换个条件再问一次" />
        </template>
      </el-table>
    </DataPanel>

    <DataPanel v-else-if="!asking" class="result-panel">
      <EmptyState
        title="还没有提问"
        desc="在上面写一句中文，或者点一个示例问题"
      />
    </DataPanel>

    <!-- ---------- 能问什么 ---------- -->
    <DataPanel v-if="catalog?.metrics?.length" title="能问什么" hint="超出这个范围的问题会被明确拒绝，不会瞎猜">
      <ul class="metric-list">
        <li v-for="m in catalog.metrics" :key="m.label" class="metric-row">
          <span class="metric-label">{{ m.label }}</span>
          <span class="metric-mode">{{ m.listMode ? '清单' : '统计' }}</span>
          <span class="metric-hint">{{ m.hint }}</span>
        </li>
      </ul>
    </DataPanel>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* ---------- 提问 ---------- */
.ask-bar {
  display: flex;
  gap: var(--sp-3);
}

.examples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0 var(--sp-1);
  margin-top: var(--sp-3);
}

.examples-label {
  font-size: 12px;
  color: var(--ink-3);
}

/* 示例按钮用 text 样式、缩小内边距，让一排句子读起来像提示而不像一排按钮 */
.example-btn {
  padding: 2px 6px;
  height: auto;
  font-size: 12px;
}

/* ---------- 结果 ---------- */
.result-panel {
  margin-top: var(--sp-3);
}

.understanding {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  background: var(--signal-weak);
  border-radius: var(--r-control);
  font-size: 13px;
  line-height: 1.7;
  /* ⚠️ 淡底上不能用 --ink-3 / --ink-1 的常规搭配里偏浅的那个，
     这里用 --ink-1 正文色压在 signal-weak 上，对比度是够的 */
  color: var(--ink-1);
}

.u-tag {
  flex: none;
  font-size: 11px;
  color: var(--signal);
}

.summary {
  margin-top: var(--sp-3);
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-1);
}

.single {
  margin-top: var(--sp-3);
  padding: var(--sp-4) 0 var(--sp-2);
}

.single-value {
  font-size: 40px;
  font-weight: 600;
  line-height: 1.1;
  color: var(--ink-1);
}

.result-table {
  margin-top: var(--sp-3);
}

/* ---------- 能问什么 ---------- */
.metric-list {
  display: flex;
  flex-direction: column;
}

.metric-row {
  display: flex;
  align-items: baseline;
  gap: var(--sp-3);
  padding: 9px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--line-soft);
}

.metric-row:last-child {
  border-bottom: none;
}

.metric-label {
  flex: none;
  width: 110px;
  font-weight: 600;
  color: var(--ink-1);
}

/* 形态标签用中性色：它只是分类，不是状态 */
.metric-mode {
  flex: none;
  padding: 1px 6px;
  font-size: 11px;
  color: var(--ink-2);
  background: var(--sunken);
  border-radius: var(--r-plate);
}

.metric-hint {
  min-width: 0;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
