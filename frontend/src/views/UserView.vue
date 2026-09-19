<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  PASSWORD_RULE_TEXT,
  USER_STATUS,
  assignUserRoles,
  batchDeleteUsers,
  batchResetUserPassword,
  batchUpdateUserStatus,
  checkPasswordStrength,
  createUser,
  deleteUser,
  exportUsers,
  getUserPage,
  getUserRoleIds,
  resetUserPassword,
  updateUser,
  type BatchResult,
  type SysUser,
  type SysUserForm,
} from '../api/user'
import { getAllRoles, type SysRole } from '../api/role'

// ============================================================
// 查询条件
// ============================================================
const loading = ref(false)
const userList = ref<SysUser[]>([])
const total = ref(0)

/** 高级搜索面板是否展开 */
const advancedVisible = ref(false)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: '',
  roleId: undefined as number | undefined,
  status: '',
  createTimeBegin: '',
  createTimeEnd: '',
  sortField: 'id',
  sortOrder: 'desc',
})

/** 创建时间范围绑定用（el-date-picker 的 range 模式需要一个数组） */
const createTimeRange = ref<[string, string] | null>(null)

function syncCreateTimeRange() {
  query.createTimeBegin = createTimeRange.value?.[0] ?? ''
  query.createTimeEnd = createTimeRange.value?.[1] ?? ''
}

/** 有没有用到高级条件 —— 折叠着的时候也要让用户知道"现在筛着呢" */
const advancedActive = computed(
  () => !!(query.roleId || query.status || query.createTimeBegin || query.createTimeEnd),
)

async function loadList() {
  loading.value = true
  try {
    const page = await getUserPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
      roleId: query.roleId,
      status: query.status || undefined,
      createTimeBegin: query.createTimeBegin || undefined,
      createTimeEnd: query.createTimeEnd || undefined,
      sortField: query.sortField,
      sortOrder: query.sortOrder,
    })
    userList.value = page.list
    total.value = page.total
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  syncCreateTimeRange()
  void loadList()
}

function handleReset() {
  query.username = ''
  query.roleId = undefined
  query.status = ''
  createTimeRange.value = null
  query.createTimeBegin = ''
  query.createTimeEnd = ''
  query.sortField = 'id'
  query.sortOrder = 'desc'
  handleSearch()
}

/** 表格列头排序（sortable="custom"）：把排序交给后端做，前端只传参数 */
function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  if (!order) {
    query.sortField = 'id'
    query.sortOrder = 'desc'
  } else {
    query.sortField = prop
    query.sortOrder = order === 'ascending' ? 'asc' : 'desc'
  }
  handleSearch()
}

// ============================================================
// 列显示/隐藏
// ============================================================
const columns = reactive({
  nickname: true,
  email: true,
  phone: false,
  roleNames: true,
  status: true,
  lastLoginTime: true,
  createTime: true,
})

// ============================================================
// 行选择 + 批量操作
// ============================================================
const selectedRows = ref<SysUser[]>([])
const selectedIds = computed(() => selectedRows.value.map((r) => r.id))

function handleSelectionChange(rows: SysUser[]) {
  selectedRows.value = rows
}

/** 批量结果提示：全部成功就一句带过，有跳过的一定要说清楚是哪几个、为什么 */
function reportBatch(action: string, result: BatchResult) {
  if (result.skipped.length === 0) {
    ElMessage.success(`${action}成功 ${result.successCount} 条`)
    return
  }

  // 拼 HTML 前必须转义：用户名是用户自己填的，
  // 直接用 dangerouslyUseHTMLString 渲染未转义内容等于开了个 XSS 口子
  const lines = result.skipped
    .map((s) => `• ${escapeHtml(s.username)}（ID ${s.id}）：${escapeHtml(s.reason)}`)
    .join('<br/>')

  void ElMessageBox.alert(
    `成功 <b>${result.successCount}</b> 条，跳过 <b>${result.skipped.length}</b> 条：<br/><br/>${lines}`,
    `${action}结果`,
    { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' },
  )
}

function escapeHtml(value: string): string {
  return String(value ?? '').replace(
    /[&<>"']/g,
    (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] ?? c,
  )
}

async function handleBatchDelete() {
  const ids = selectedIds.value
  if (ids.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${ids.length} 个用户吗？此操作不可撤销。\n` +
        `内置管理员账号和当前登录账号会被自动跳过。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    const result = await batchDeleteUsers(ids)
    reportBatch('删除', result)
    await loadList()
  } catch {
    // 同上
  }
}

async function handleBatchStatus(status: string) {
  const ids = selectedIds.value
  if (ids.length === 0) return

  const label = status === USER_STATUS.NORMAL ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(
      `确定要${label}选中的 ${ids.length} 个用户吗？` +
        (status === USER_STATUS.DISABLED
          ? '\n停用后这些账号将无法登录，当前登录账号和内置管理员会被跳过。'
          : ''),
      `批量${label}确认`,
      { type: 'warning', confirmButtonText: `确定${label}`, cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    const result = await batchUpdateUserStatus(ids, status)
    reportBatch(label, result)
    await loadList()
  } catch {
    // 同上
  }
}

async function handleBatchResetPassword() {
  const ids = selectedIds.value
  if (ids.length === 0) return

  let password: string
  try {
    const result = await ElMessageBox.prompt(
      `为选中的 ${ids.length} 个用户设置同一个新密码`,
      '批量重置密码',
      {
        inputType: 'password',
        inputPlaceholder: PASSWORD_RULE_TEXT,
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        // 返回 null 会被 Element Plus 当成校验通过
        inputValidator: (value) => checkPasswordStrength(value) ?? true,
      },
    )
    password = result.value
  } catch {
    return
  }

  try {
    const result = await batchResetUserPassword(ids, password)
    reportBatch('密码重置', result)
  } catch {
    // 同上
  }
}

// ============================================================
// 导出
// ============================================================
const exporting = ref(false)

async function handleExport() {
  exporting.value = true
  try {
    syncCreateTimeRange()
    // 带上当前全部筛选条件，做到"页面上筛出什么就导出什么"
    await exportUsers({
      pageNum: 1,
      pageSize: query.pageSize,
      username: query.username || undefined,
      roleId: query.roleId,
      status: query.status || undefined,
      createTimeBegin: query.createTimeBegin || undefined,
      createTimeEnd: query.createTimeEnd || undefined,
      sortField: query.sortField,
      sortOrder: query.sortOrder,
    })
    ElMessage.success('导出成功，请查看浏览器下载')
  } catch {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

// ============================================================
// 列表展示辅助
// ============================================================
function statusTagType(status: string): 'success' | 'info' {
  return status === USER_STATUS.NORMAL ? 'success' : 'info'
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

// ============================================================
// 新增 / 编辑
// ============================================================
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const roleOptions = ref<SysRole[]>([])

function emptyForm(): SysUserForm {
  return {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: USER_STATUS.NORMAL,
    roleIds: [],
  }
}

const form = reactive<SysUserForm>(emptyForm())

// 密码规则：新增时必填且要够强；编辑时留空表示不改密码，留空就不校验强度
const rules = computed<FormRules<SysUserForm>>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名不能超过 50 个字符', trigger: 'blur' },
  ],
  password: [
    {
      validator: (_rule, value: string, callback) => {
        if (editingId.value !== null && !value) {
          callback()   // 编辑时留空 = 不修改
          return
        }
        const error = checkPasswordStrength(value)
        callback(error ? new Error(error) : undefined)
      },
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ max: 20, message: '手机号不能超过 20 个字符', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}))

async function loadRoleOptions() {
  try {
    roleOptions.value = await getAllRoles()
  } catch {
    // 同上
  }
}

async function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增用户'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: SysUser) {
  editingId.value = row.id
  dialogTitle.value = '编辑用户'
  Object.assign(form, {
    username: row.username,
    password: '',
    nickname: row.nickname ?? '',
    email: row.email ?? '',
    phone: row.phone ?? '',
    status: row.status,
    roleIds: row.roleIds ?? [],
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
      await createUser({ ...form })
      ElMessage.success('新增成功')
    } else {
      const payload: SysUserForm = { ...form }
      if (!payload.password) {
        delete payload.password   // 留空表示不改密码，别把空串传给后端
      }
      await updateUser(editingId.value, payload)
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch {
    // 同上
  } finally {
    submitting.value = false
  }
}

// ============================================================
// 分配角色
// ============================================================
const roleDialogVisible = ref(false)
const roleSubmitting = ref(false)
const checkedRoleIds = ref<number[]>([])
const roleTargetUser = ref<SysUser | null>(null)

async function openAssignRoles(row: SysUser) {
  roleTargetUser.value = row
  checkedRoleIds.value = []
  roleDialogVisible.value = true
  try {
    checkedRoleIds.value = await getUserRoleIds(row.id)
  } catch {
    // 同上
  }
}

async function submitAssignRoles() {
  if (!roleTargetUser.value) return

  roleSubmitting.value = true
  try {
    await assignUserRoles(roleTargetUser.value.id, checkedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    await loadList()
  } catch {
    // 同上
  } finally {
    roleSubmitting.value = false
  }
}

// ============================================================
// 详情弹窗
// ============================================================
const detailVisible = ref(false)
const detailUser = ref<SysUser | null>(null)

function openDetail(row: SysUser) {
  detailUser.value = row
  detailVisible.value = true
}

// ============================================================
// 单条：重置密码 / 切换状态 / 删除
// ============================================================
async function handleResetPassword(row: SysUser) {
  let value: string
  try {
    const result = await ElMessageBox.prompt(
      `为用户「${row.username}」设置新密码`,
      '重置密码',
      {
        inputType: 'password',
        inputPlaceholder: PASSWORD_RULE_TEXT,
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValidator: (val) => checkPasswordStrength(val) ?? true,
      },
    )
    value = result.value
  } catch {
    return
  }

  try {
    await resetUserPassword(row.id, value)
    ElMessage.success('密码重置成功')
  } catch {
    // 同上
  }
}

/** 单条切换启用/停用：复用批量接口，传一个 id 即可，保护规则也一并生效 */
async function handleToggleStatus(row: SysUser) {
  const target = row.status === USER_STATUS.NORMAL ? USER_STATUS.DISABLED : USER_STATUS.NORMAL
  const label = target === USER_STATUS.NORMAL ? '启用' : '停用'

  try {
    await ElMessageBox.confirm(
      `确定要${label}用户「${row.username}」吗？` +
        (target === USER_STATUS.DISABLED ? '\n停用后该账号将无法登录。' : ''),
      `${label}确认`,
      { type: 'warning', confirmButtonText: `确定${label}`, cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    const result = await batchUpdateUserStatus([row.id], target)
    reportBatch(label, result)
    await loadList()
  } catch {
    // 同上
  }
}

async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.username}」吗？此操作不可撤销。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    if (userList.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await loadList()
  } catch {
    // 后端的内置账号保护等会返回 400，提示由拦截器弹出
  }
}

/**
 * 生成某一行的下拉命令处理器。
 *
 * 不能在模板里写内联箭头函数（`@command="(cmd) => ..."`）：
 * 参数会被推断成隐式 any，在 strict 模式下直接报错。
 * 这里用一个返回闭包的函数，类型能正确收窄。
 */
function rowCommandHandler(row: SysUser) {
  return (command: string | number | object) => {
    handleRowCommand(String(command), row)
  }
}

/** 操作下拉里点了什么 */
function handleRowCommand(command: string, row: SysUser) {
  switch (command) {
    case 'edit':
      void openEdit(row)
      break
    case 'roles':
      void openAssignRoles(row)
      break
    case 'toggle':
      void handleToggleStatus(row)
      break
    case 'reset':
      void handleResetPassword(row)
      break
    case 'delete':
      void handleDelete(row)
      break
  }
}

onMounted(async () => {
  await Promise.all([loadList(), loadRoleOptions()])
})
</script>

<template>
  <div class="page">
    <!-- 搜索区 -->
    <el-card shadow="never" class="search-card">
      <div class="search-row">
        <el-input
          v-model="query.username"
          placeholder="按用户名搜索"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />

        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>

        <el-button link type="primary" @click="advancedVisible = !advancedVisible">
          {{ advancedVisible ? '收起高级搜索' : '高级搜索' }}
          <el-badge v-if="advancedActive && !advancedVisible" is-dot class="filter-dot" />
        </el-button>

        <div class="search-right">
          <el-button type="success" :loading="exporting" @click="handleExport">
            导出 Excel
          </el-button>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </div>

      <!-- 高级搜索：默认折叠，避免一进页面就被一排输入框糊脸 -->
      <el-collapse-transition>
        <div v-show="advancedVisible" class="advanced">
          <div class="advanced-item">
            <span class="label">角色</span>
            <el-select v-model="query.roleId" placeholder="全部" clearable style="width: 170px">
              <el-option
                v-for="role in roleOptions"
                :key="role.id"
                :label="role.roleName"
                :value="role.id as number"
              />
            </el-select>
          </div>

          <div class="advanced-item">
            <span class="label">状态</span>
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
              <el-option label="正常" :value="USER_STATUS.NORMAL" />
              <el-option label="停用" :value="USER_STATUS.DISABLED" />
            </el-select>
          </div>

          <div class="advanced-item">
            <span class="label">创建时间</span>
            <el-date-picker
              v-model="createTimeRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
            />
          </div>

          <el-button type="primary" @click="handleSearch">应用条件</el-button>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 批量操作条：只在选中了行的时候出现 -->
    <div v-if="selectedRows.length" class="batch-bar">
      <span class="batch-info">
        已选中 <strong>{{ selectedRows.length }}</strong> 项
      </span>
      <el-button size="small" @click="handleBatchStatus(USER_STATUS.NORMAL)">批量启用</el-button>
      <el-button size="small" @click="handleBatchStatus(USER_STATUS.DISABLED)">批量停用</el-button>
      <el-button size="small" @click="handleBatchResetPassword">批量重置密码</el-button>
      <el-button size="small" type="danger" @click="handleBatchDelete">批量删除</el-button>
      <el-button size="small" link @click="selectedRows = []">取消选择</el-button>
    </div>

    <!-- 列显隐 -->
    <div class="table-tools">
      <el-popover placement="bottom-end" :width="180" trigger="click">
        <template #reference>
          <el-button size="small">显示列</el-button>
        </template>
        <el-checkbox v-model="columns.nickname">昵称</el-checkbox>
        <el-checkbox v-model="columns.email">邮箱</el-checkbox>
        <el-checkbox v-model="columns.phone">手机号</el-checkbox>
        <el-checkbox v-model="columns.roleNames">角色</el-checkbox>
        <el-checkbox v-model="columns.status">状态</el-checkbox>
        <el-checkbox v-model="columns.lastLoginTime">最后登录</el-checkbox>
        <el-checkbox v-model="columns.createTime">创建时间</el-checkbox>
      </el-popover>
    </div>

    <!-- 表格：height 让表头固定，超出的部分内部滚动 -->
    <el-table
      v-loading="loading"
      :data="userList"
      border
      stripe
      height="480"
      :default-sort="{ prop: 'id', order: 'descending' }"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <el-table-column type="selection" width="46" />

      <el-table-column prop="id" label="ID" width="80" sortable="custom" />

      <el-table-column label="用户名" min-width="130">
        <template #default="{ row }">
          <!-- 点用户名看详情，比挤一排按钮更符合直觉 -->
          <el-link type="primary" :underline="false" @click="openDetail(row)">
            {{ row.username }}
          </el-link>
        </template>
      </el-table-column>

      <el-table-column v-if="columns.nickname" prop="nickname" label="昵称" min-width="110" />

      <el-table-column
        v-if="columns.email"
        prop="email"
        label="邮箱"
        min-width="170"
        show-overflow-tooltip
      />

      <el-table-column v-if="columns.phone" prop="phone" label="手机号" min-width="130" />

      <el-table-column v-if="columns.roleNames" label="角色" min-width="150">
        <template #default="{ row }">
          <template v-if="row.roleNames && row.roleNames.length">
            <el-tag v-for="name in row.roleNames" :key="name" size="small" class="role-tag">
              {{ name }}
            </el-tag>
          </template>
          <span v-else class="muted">未分配</span>
        </template>
      </el-table-column>

      <el-table-column v-if="columns.status" label="状态" width="85">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" disable-transitions>
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column
        v-if="columns.lastLoginTime"
        label="最后登录"
        width="165"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <span :class="{ muted: !row.lastLoginTime }">
            {{ row.lastLoginTime ? formatTime(row.lastLoginTime) : '从未登录' }}
          </span>
        </template>
      </el-table-column>

      <el-table-column v-if="columns.createTime" prop="createTime" label="创建时间" width="165" sortable="custom">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <!-- 4 个操作平铺太挤，收进下拉 -->
          <el-dropdown trigger="click" @command="rowCommandHandler(row)">
            <el-button link type="primary">
              更多<span class="caret">▾</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="roles">分配角色</el-dropdown-item>
                <el-dropdown-item command="reset">重置密码</el-dropdown-item>
                <el-dropdown-item command="toggle" divided>
                  {{ row.status === USER_STATUS.NORMAL ? '停用账号' : '启用账号' }}
                </el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="没有符合条件的用户" />
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      class="pagination"
      @size-change="handleSearch"
      @current-change="loadList"
    />

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录用的账号" maxlength="50" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId === null ? PASSWORD_RULE_TEXT : '留空表示不修改密码'"
            maxlength="64"
          />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="选填" maxlength="50" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="选填" maxlength="100" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="USER_STATUS.NORMAL">正常</el-radio>
            <el-radio :value="USER_STATUS.DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="可暂不分配" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName"
              :value="role.id as number"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog
      v-model="roleDialogVisible"
      :title="`分配角色 —— ${roleTargetUser?.username ?? ''}`"
      width="460px"
    >
      <el-checkbox-group v-model="checkedRoleIds" class="role-group">
        <el-checkbox v-for="role in roleOptions" :key="role.id" :value="role.id as number">
          {{ role.roleName }}
          <span class="muted">（{{ role.roleKey }}）</span>
        </el-checkbox>
      </el-checkbox-group>

      <el-empty v-if="!roleOptions.length" description="没有可选角色" :image-size="60" />

      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitAssignRoles">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 用户详情 -->
    <el-dialog
      v-model="detailVisible"
      :title="`用户详情 —— ${detailUser?.username ?? ''}`"
      width="560px"
    >
      <el-descriptions v-if="detailUser" :column="2" border>
        <el-descriptions-item label="ID">{{ detailUser.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detailUser.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailUser.nickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailUser.status)" size="small" disable-transitions>
            {{ detailUser.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱" :span="2">
          {{ detailUser.email || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailUser.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          {{ detailUser.roleNames?.join('、') || '未分配' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatTime(detailUser.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="最后登录时间">
          {{ detailUser.lastLoginTime ? formatTime(detailUser.lastLoginTime) : '从未登录' }}
        </el-descriptions-item>
        <el-descriptions-item label="最后登录 IP" :span="2">
          {{ detailUser.lastLoginIp || '—' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="detailUser && !detailUser.lastLoginTime"
        type="info"
        :closable="false"
        show-icon
        class="detail-tip"
      >
        该账号还没有登录记录。登录时间与 IP 会在每次成功登录时更新。
      </el-alert>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.search-card {
  margin-bottom: 12px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-right {
  margin-left: auto;
  display: flex;
  gap: 10px;
}

.advanced {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed var(--border);
}

.advanced-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  font-size: 13px;
  color: #64748b;
}

.filter-dot {
  margin-left: 2px;
  vertical-align: top;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
  border: 1px solid var(--accent-border);
  border-radius: 6px;
  background: var(--accent-bg);
}

.batch-info {
  margin-right: 6px;
  font-size: 13px;
}

.table-tools {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.role-tag {
  margin-right: 6px;
}

/* 下拉箭头的文本字符版。
   项目里没有装 @element-plus/icons-vue，为一个箭头引一个依赖不划算，
   用字符和 App.vue 侧边栏里的写法保持一致。 */
.caret {
  margin-left: 2px;
  font-size: 11px;
  color: #94a3b8;
}

.role-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.muted {
  color: #cbd5e1;
}

.detail-tip {
  margin-top: 14px;
}
</style>
