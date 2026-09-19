<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getLoginLogPage, type SysLoginLog } from '../api/loginLog'

const loading = ref(false)
const logList = ref<SysLoginLog[]>([])
const total = ref(0)

/** 成功/失败筛选用字符串，空串表示不限 —— el-select 绑 boolean 容易踩空值的坑 */
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  username: '',
  ip: '',
  result: '' as '' | 'success' | 'fail',
  loginTimeBegin: '',
  loginTimeEnd: '',
})

const timeRange = ref<[string, string] | null>(null)

async function loadList() {
  loading.value = true
  try {
    const page = await getLoginLogPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      ip: query.ip || undefined,
      success: query.result === '' ? undefined : query.result === 'success',
      loginTimeBegin: query.loginTimeBegin || undefined,
      loginTimeEnd: query.loginTimeEnd || undefined,
    })
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
  query.loginTimeBegin = timeRange.value?.[0] ?? ''
  query.loginTimeEnd = timeRange.value?.[1] ?? ''
  void loadList()
}

function handleReset() {
  query.username = ''
  query.ip = ''
  query.result = ''
  timeRange.value = null
  query.loginTimeBegin = ''
  query.loginTimeEnd = ''
  handleSearch()
}

/** 一键切到"只看失败"——排查异常时最常用的筛选 */
function showFailuresOnly() {
  query.result = 'fail'
  handleSearch()
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="query.username"
          placeholder="按用户名搜索"
          clearable
          style="width: 170px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="query.ip"
          placeholder="按 IP 搜索"
          clearable
          style="width: 150px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="query.result" placeholder="全部结果" clearable style="width: 130px">
          <el-option label="登录成功" value="success" />
          <el-option label="登录失败" value="fail" />
        </el-select>
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
        <el-button type="warning" plain @click="showFailuresOnly">只看失败</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="logList" border stripe height="520">
      <el-table-column label="登录时间" width="170">
        <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
      </el-table-column>

      <el-table-column prop="username" label="用户名" min-width="130" show-overflow-tooltip />

      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" disable-transitions>
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="失败原因" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ muted: !row.failReason }">{{ row.failReason || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="ip" label="IP" width="140">
        <template #default="{ row }">
          <span :class="{ muted: !row.ip }">{{ row.ip || '—' }}</span>
        </template>
      </el-table-column>

      <!-- 设备列悬停时显示原始 UA，方便遇到识别不准时对照 -->
      <el-table-column label="设备" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tooltip
            v-if="row.userAgent"
            :content="row.userAgent"
            placement="top"
            :show-after="300"
          >
            <span>{{ row.device || '未知' }}</span>
          </el-tooltip>
          <span v-else class="muted">未知</span>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无登录记录" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      class="pagination"
      @size-change="handleSearch"
      @current-change="loadList"
    />

    <p class="footnote">
      日志只读，不提供修改和删除 —— 审计记录如果可以被人改动，就没有作为依据的价值了。
      需要清理请直接操作数据库。
    </p>
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
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.muted {
  color: #cbd5e1;
}

.footnote {
  margin-top: 14px;
  font-size: 12px;
  line-height: 1.7;
  color: #94a3b8;
}
</style>
