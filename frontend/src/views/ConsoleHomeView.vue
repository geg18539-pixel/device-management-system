<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getConsoleOverview, type ConsoleOverview } from '../api/console'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import type { PlateTone } from '../utils/plateTone'

const router = useRouter()

const loading = ref(false)
const overview = ref<ConsoleOverview | null>(null)

async function load() {
  loading.value = true
  try {
    overview.value = await getConsoleOverview()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

onMounted(load)

/**
 * 需要关注的四件事。
 *
 * <p><b>顺序是固定的，不按数量排</b>：按数量排的话，顺序会随数据跳动，
 * 管理员每次进来都得重新找一遍。固定顺序下"第三行是什么"是可以记住的。
 * （设备看板的待办也是同一个理由。）
 *
 * <p>为 0 的项照常列出来，只是用二级墨色、不抢注意力 ——
 * 「今天是 0 次」本身就是个好消息，藏起来反而丢信息。
 */
const attention = computed(() => {
  const o = overview.value
  if (!o) return []
  const totalLogins = o.loginSuccessToday + o.loginFailToday
  return [
    {
      label: '今日登录失败',
      hint: totalLogins > 0 ? `今日共登录 ${totalLogins} 次` : '今日还没有登录记录',
      value: o.loginFailToday,
      unit: '次',
      tone: 'crit' as PlateTone,
      go: () => router.push('/system/login-logs'),
    },
    {
      label: '今日越权被拒',
      hint: '已登录但权限不足的请求，逐条都记在操作日志里',
      value: o.deniedToday,
      unit: '次',
      tone: 'warn' as PlateTone,
      go: () => router.push('/system/oper-logs'),
    },
    {
      label: '知识库处理失败',
      hint: '这类不会自愈，要逐份点「重新处理」',
      value: o.knowledgeFailed,
      unit: '份',
      tone: 'crit' as PlateTone,
      go: () => router.push('/system/knowledge'),
    },
    {
      label: '停用账号',
      hint: '已停用，无法登录',
      value: o.disabledUsers,
      unit: '个',
      tone: 'idle' as PlateTone,
      go: () => router.push('/system/users'),
    },
  ]
})

/** 规模带。这四个是"看一眼知道系统有多大"，不需要处理 */
const scale = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { label: '用户', value: o.userTotal, unit: '个' },
    { label: '角色', value: o.roleTotal, unit: '个' },
    { label: '部门', value: o.deptTotal, unit: '个' },
    { label: '知识库文档', value: o.knowledgeTotal, unit: '份' },
  ]
})

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader
      title="系统概览"
      desc="账号、登录、权限与知识库的运行状况。业务数据请看员工工作台的首页看板"
    />

    <!-- ---------- 需要关注 ----------
         放在最前面：纯规模数字（用户 12 个、角色 2 个）看一次就没价值了，
         它不会变也不需要处理。真正值得每天扫一眼的是异常的那几个 -->
    <DataPanel title="需要关注" hint="为 0 时说明一切正常" flush>
      <ul class="attention-list">
        <li
          v-for="item in attention"
          :key="item.label"
          class="attention-row"
          @click="item.go()"
        >
          <span class="attention-name">
            {{ item.label }}
            <span class="attention-sub">{{ item.hint }}</span>
          </span>

          <span class="attention-fig">
            <!-- 为 0 的项用三级墨色，不抢注意力 -->
            <b class="num" :class="item.value > 0 ? item.tone : 'is-zero'">{{ item.value }}</b>
            <span class="attention-unit">{{ item.unit }}</span>
          </span>
        </li>
      </ul>
    </DataPanel>

    <!-- ---------- 规模 ---------- -->
    <DataPanel flush class="scale-panel">
      <div class="strip">
        <div v-for="item in scale" :key="item.label" class="strip-cell">
          <div class="strip-label">{{ item.label }}</div>
          <div class="strip-value num">
            {{ item.value }}<span class="unit">{{ item.unit }}</span>
          </div>
        </div>
      </div>
    </DataPanel>

    <!-- ---------- 最近登录 ---------- -->
    <DataPanel title="最近登录" hint="最近 5 条，最新的在最前" flush>
      <el-table :data="overview?.recentLogins ?? []">
        <el-table-column prop="username" label="账号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="ip" label="来源 IP" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.ip || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="device" label="设备" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.device || '—' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <StatusPlate :tone="row.success ? 'ok' : 'crit'">
              {{ row.success ? '成功' : '失败' }}
            </StatusPlate>
          </template>
        </el-table-column>
        <el-table-column prop="failReason" label="失败原因" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.failReason">{{ row.failReason }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
        </el-table-column>

        <template #empty>
          <EmptyState title="还没有登录记录" desc="等有账号登录之后，这里会显示最近的登录情况" />
        </template>
      </el-table>
    </DataPanel>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* ---------- 需要关注 ---------- */
.attention-list {
  display: flex;
  flex-direction: column;
}

.attention-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: 11px var(--sp-4);
  cursor: pointer;
  border-bottom: 1px solid var(--line-soft);
}

.attention-row:last-child {
  border-bottom: none;
}

.attention-row:hover {
  background: var(--hover);
}

.attention-name {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  font-size: 13px;
  color: var(--ink-1);
}

.attention-sub {
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attention-fig {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.attention-fig b {
  font-size: 20px;
  font-weight: 600;
  line-height: 1;
}

/* 为 0 的项用三级墨色（不是 --ink-4）—— --ink-4 压不住行悬停的 --hover，
   只有 2.95:1，和设备看板的待办是同一个取舍 */
.attention-fig b.is-zero {
  color: var(--ink-3);
}

.attention-fig b.warn {
  color: var(--warn);
}

.attention-fig b.crit {
  color: var(--crit);
}

.attention-fig b.idle {
  color: var(--idle);
}

.attention-unit {
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- 规模带 ---------- */
.scale-panel {
  margin-top: var(--sp-3);
}

.strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.strip-cell {
  padding: var(--sp-4) var(--sp-5);
}

.strip-cell + .strip-cell {
  border-left: 1px solid var(--line-soft);
}

.strip-label {
  font-size: 12px;
  color: var(--ink-3);
}

.strip-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 2px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--ink-1);
}

.strip-value .unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--ink-3);
}

.muted {
  color: var(--ink-3);
}
</style>
