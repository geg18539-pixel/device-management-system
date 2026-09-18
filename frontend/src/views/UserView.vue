<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  getUserPage,
  getUserRoleIds,
  resetUserPassword,
  updateUser,
  type SysUser,
  type SysUserForm,
} from '../api/user'
import { getAllRoles, type SysRole } from '../api/role'

// ---------------- 列表 ----------------
const loading = ref(false)
const userList = ref<SysUser[]>([])
const total = ref(0)

const query = reactive({
  username: '',
  pageNum: 1,
  pageSize: 10,
})

async function loadList() {
  loading.value = true
  try {
    const page = await getUserPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      username: query.username || undefined,
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
  // 换条件搜索时必须回到第 1 页，否则可能停在一个空页上
  query.pageNum = 1
  void loadList()
}

function handleReset() {
  query.username = ''
  handleSearch()
}

function statusTagType(status: string): 'success' | 'info' {
  return status === '正常' ? 'success' : 'info'
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): SysUserForm {
  return {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: '正常',
    roleIds: [],
  }
}

const form = reactive<SysUserForm>(emptyForm())

// 注意 password：新增时必填，编辑时留空表示不改密码。
// 校验规则要跟着变，所以在提交前动态切换（见 rules 的 computed）
const rules = computed<FormRules<SysUserForm>>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名不能超过 50 个字符', trigger: 'blur' },
  ],
  password:
    editingId.value === null
      ? [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 4, max: 64, message: '密码长度需在 4 到 64 个字符之间', trigger: 'blur' },
        ]
      : [{ min: 4, max: 64, message: '密码长度需在 4 到 64 个字符之间', trigger: 'blur' }],
  nickname: [{ max: 50, message: '昵称不能超过 50 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ max: 20, message: '手机号不能超过 20 个字符', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}))

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
    // 编辑时密码框留空，表示"不改密码"
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
      // 编辑时如果密码留空，就不要把这个空串传给后端 ——
      // 后端虽然对空串做了"不修改"处理，但显式去掉更清晰
      const payload: SysUserForm = { ...form }
      if (!payload.password) {
        delete payload.password
      }
      await updateUser(editingId.value, payload)
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch {
    // 失败提示已由拦截器处理
  } finally {
    submitting.value = false
  }
}

// ---------------- 分配角色 ----------------
const roleDialogVisible = ref(false)
const roleSubmitting = ref(false)
const roleOptions = ref<SysRole[]>([])
const checkedRoleIds = ref<number[]>([])
const roleTargetUser = ref<SysUser | null>(null)

async function loadRoleOptions() {
  try {
    roleOptions.value = await getAllRoles()
  } catch {
    // 错误提示已由拦截器处理
  }
}

async function openAssignRoles(row: SysUser) {
  roleTargetUser.value = row
  checkedRoleIds.value = []
  roleDialogVisible.value = true

  try {
    // roleOptions 在 onMounted 已经加载过了，这里只需要取该用户的角色 id。
    // 顺便不再重复请求角色列表，少一次网络往返。
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

// ---------------- 重置密码 ----------------
async function handleResetPassword(row: SysUser) {
  let value: string
  try {
    // 用 prompt 而不是单独做个弹窗，因为只需要一个输入框
    const result = await ElMessageBox.prompt(
      `为用户「${row.username}」设置新密码（4-64 位）`,
      '重置密码',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /^.{4,64}$/,
        inputErrorMessage: '密码长度需在 4 到 64 个字符之间',
        inputType: 'password',
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

// ---------------- 删除 ----------------
async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${row.username}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    // 删掉当前页最后一条时要往前翻一页，否则会停在空页
    if (userList.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await loadList()
  } catch {
    // 后端的内置账号保护等会在这里返回 400，提示由拦截器弹出
  }
}

// 角色选项在新增/编辑弹窗里也要用，所以进页面就加载，
// 不能等到点"分配角色"时才加载
onMounted(async () => {
  await Promise.all([loadList(), loadRoleOptions()])
})
</script>

<template>
  <div class="page">
    <!-- 搜索栏 -->
    <div class="toolbar">
      <div class="filters">
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
      </div>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="userList" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="110" />
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <template v-if="row.roleNames && row.roleNames.length">
            <el-tag
              v-for="name in row.roleNames"
              :key="name"
              size="small"
              class="role-tag"
            >
              {{ name }}
            </el-tag>
          </template>
          <span v-else class="muted">未分配</span>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="85">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" disable-transitions>
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openAssignRoles(row)">分配角色</el-button>
          <el-button link type="warning" @click="handleResetPassword(row)">重置密码</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无用户数据" />
      </template>
    </el-table>

    <!-- 分页 -->
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

    <!-- 新增 / 编辑弹窗 -->
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
            :placeholder="editingId === null ? '请输入密码' : '留空表示不修改密码'"
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
            <el-radio value="正常">正常</el-radio>
            <el-radio value="停用">停用</el-radio>
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

    <!-- 分配角色弹窗 -->
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

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

.role-tag {
  margin-right: 6px;
}

.role-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.muted {
  color: #94a3b8;
  font-size: 13px;
}
</style>
