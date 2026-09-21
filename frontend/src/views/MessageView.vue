<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MSG_LEVEL,
  MSG_TYPE_META,
  MSG_TYPE_OPTIONS,
  deleteAllReadMessages,
  deleteMessage,
  getMessagePage,
  markAllMessagesRead,
  markMessageRead,
  routeForMessage,
  type SysMessage,
} from '../api/message'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { useMessageStore } from '../stores/message'

const router = useRouter()
const msgStore = useMessageStore()

const loading = ref(false)
const messages = ref<SysMessage[]>([])
const total = ref(0)

const query = reactive<{
  pageNum: number
  pageSize: number
  readFlag: '' | 'true' | 'false'
  msgType: string
}>({
  pageNum: 1,
  pageSize: 10,
  readFlag: '',
  msgType: '',
})

async function loadList() {
  loading.value = true
  try {
    const page = await getMessagePage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      // 空串表示"不限" —— 不能传空字符串给后端，那会被当成一个筛选值
      readFlag: query.readFlag === '' ? undefined : query.readFlag === 'true',
      msgType: query.msgType || undefined,
    })
    messages.value = page.list
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
  query.readFlag = ''
  query.msgType = ''
  handleSearch()
}

/** 列表和顶栏红点都要刷 —— 标记已读之后未读数会变 */
async function refreshAll() {
  await Promise.all([loadList(), msgStore.refresh()])
}

function typeMeta(msgType: string) {
  return MSG_TYPE_META[msgType] ?? { label: msgType, tone: 'idle' as const }
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

// ---------------- 单条操作 ----------------

async function handleOpen(msg: SysMessage) {
  if (!msg.readFlag) {
    await markRead(msg)
  }
  const target = routeForMessage(msg)
  if (target) {
    await router.push(target)
  }
}

async function markRead(msg: SysMessage) {
  try {
    await markMessageRead(msg.id)
    await refreshAll()
  } catch {
    // 同上
  }
}

async function handleDelete(msg: SysMessage) {
  try {
    await ElMessageBox.confirm(`确定要删除消息「${msg.title}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteMessage(msg.id)
    ElMessage.success('删除成功')
    if (messages.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await refreshAll()
  } catch {
    // 同上
  }
}

async function handleReadAll() {
  try {
    const count = await markAllMessagesRead()
    ElMessage.success(count > 0 ? `已标记 ${count} 条为已读` : '没有未读消息')
    await refreshAll()
  } catch {
    // 同上
  }
}

async function handleDeleteAllRead() {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有已读消息吗？未读消息不受影响。',
      '清空确认',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const count = await deleteAllReadMessages()
    ElMessage.success(`已清空 ${count} 条已读消息`)
    query.pageNum = 1
    await refreshAll()
  } catch {
    // 同上
  }
}

const unreadCount = computed(() => msgStore.unreadCount)

/** 页头那行说明 */
const headDesc = computed(() => {
  const unread = unreadCount.value
  return unread ? `共 ${total.value} 条消息，${unread} 条未读` : `共 ${total.value} 条消息`
})

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader title="消息中心" :desc="headDesc">
      <template #actions>
        <el-button :disabled="unreadCount === 0" @click="handleReadAll">
          全部已读<span v-if="unreadCount">（{{ unreadCount }}）</span>
        </el-button>
        <el-button type="danger" plain @click="handleDeleteAllRead">清空已读</el-button>
      </template>
    </PageHeader>

    <DataPanel flush>
      <div class="filter-bar">
        <div class="search-row">
          <el-select
            v-model="query.readFlag"
            placeholder="全部消息"
            style="width: 130px"
            @change="handleSearch"
          >
            <el-option label="全部消息" value="" />
            <el-option label="只看未读" value="false" />
            <el-option label="只看已读" value="true" />
          </el-select>

          <el-select
            v-model="query.msgType"
            placeholder="全部类型"
            clearable
            style="width: 140px"
            @change="handleSearch"
          >
            <el-option v-for="o in MSG_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>

          <el-button @click="handleReset">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="messages">
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <span class="dot" :class="{ hidden: row.readFlag }" />
          </template>
        </el-table-column>

        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <StatusPlate :tone="typeMeta(row.msgType).tone">
              {{ typeMeta(row.msgType).label }}
            </StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="标题" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span
              :class="{
                'unread-title': !row.readFlag,
                important: row.level === MSG_LEVEL.IMPORTANT,
              }"
            >
              {{ row.title }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="content" label="内容" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="content">{{ (row.content || '').replace(/\n/g, ' ') }}</span>
          </template>
        </el-table-column>

        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.readFlag" link type="primary" @click="markRead(row)">
              标记已读
            </el-button>
            <el-button
              v-if="routeForMessage(row)"
              link
              type="primary"
              @click="handleOpen(row)"
            >
              查看
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="没有符合条件的消息" desc="换个筛选条件试试，或者等新的通知进来" />
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
          @current-change="loadList"
        />
      </div>
    </DataPanel>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.search-card {
  margin-bottom: 12px;
}

/* 搜索栏：面板顶部的一条 */
.filter-bar {
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

/* 搜索行：下拉和按钮排一行，窄屏自动换行 */
.search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 分页在面板底部 */
.table-pager {
  display: flex;
  justify-content: flex-end;
  padding: var(--sp-3) var(--sp-4);
  border-top: 1px solid var(--line-soft);
}

/* 未读小圆点。已读时用 visibility 隐藏而不是 display:none，避免标题左右错位 */
.dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--crit);
}

.dot.hidden {
  visibility: hidden;
}

.unread-title {
  font-weight: 600;
  color: var(--ink-1);
}

.important {
  color: var(--crit);
}

.content {
  color: var(--ink-2);
}
</style>
