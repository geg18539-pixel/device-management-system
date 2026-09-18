<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  ElTree,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  getRoleMenuIds,
  getRolePage,
  updateRole,
  type SysRole,
} from '../api/role'
import { getMenuTree, type SysMenuTree } from '../api/menu'

// ---------------- 列表 ----------------
const loading = ref(false)
const roleList = ref<SysRole[]>([])
const total = ref(0)

const query = reactive({
  roleName: '',
  pageNum: 1,
  pageSize: 10,
})

async function loadList() {
  loading.value = true
  try {
    const page = await getRolePage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      roleName: query.roleName || undefined,
    })
    roleList.value = page.list
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
  query.roleName = ''
  handleSearch()
}

function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增角色')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): SysRole {
  return {
    roleName: '',
    roleKey: '',
    sortOrder: 0,
    status: '正常',
    remark: '',
  }
}

const form = reactive<SysRole>(emptyForm())

const rules: FormRules<SysRole> = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 50, message: '角色名称不能超过 50 个字符', trigger: 'blur' },
  ],
  roleKey: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    { max: 50, message: '角色标识不能超过 50 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_]*$/, message: '只能用字母、数字、下划线，且以字母开头', trigger: 'blur' },
  ],
  remark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }],
}

async function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增角色'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: SysRole) {
  editingId.value = row.id ?? null
  dialogTitle.value = '编辑角色'
  Object.assign(form, {
    roleName: row.roleName,
    roleKey: row.roleKey,
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? '正常',
    remark: row.remark ?? '',
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
      await createRole({ ...form })
      ElMessage.success('新增成功')
    } else {
      await updateRole(editingId.value, { ...form })
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

async function handleDelete(row: SysRole) {
  if (row.id === undefined) return

  try {
    await ElMessageBox.confirm(`确定要删除角色「${row.roleName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    if (roleList.value.length === 1 && query.pageNum > 1) {
      query.pageNum -= 1
    }
    await loadList()
  } catch {
    // 后端有"内置 admin 角色不能删""角色下有用户不能删"的保护，
    // 会返回 400，提示由拦截器弹出
  }
}

// ---------------- 分配权限 ----------------
const menuDialogVisible = ref(false)
const menuSubmitting = ref(false)
const menuTree = ref<SysMenuTree[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const roleTarget = ref<SysRole | null>(null)

async function openAssignMenus(row: SysRole) {
  if (row.id === undefined) return

  roleTarget.value = row
  menuDialogVisible.value = true

  try {
    // 菜单树只需加载一次，之后再打开弹窗直接复用
    if (!menuTree.value.length) {
      menuTree.value = await getMenuTree()
    }
    const checkedIds = await getRoleMenuIds(row.id)

    // 等弹窗里的 el-tree 渲染出来才能操作它的实例
    await nextTick()
    treeRef.value?.setCheckedKeys(checkedIds)
  } catch {
    // 同上
  }
}

async function submitAssignMenus() {
  if (!roleTarget.value?.id || !treeRef.value) return

  // ★ 关键：必须把「全选」和「半选」的节点一起提交。
  // el-tree 里勾选子节点时，父节点只是"半选"状态，getCheckedKeys() 拿不到它。
  // 如果只提交全选节点，父级目录就会从结果里丢掉 ——
  // 表现为"分配了子菜单，但重新打开发现父目录没勾上，侧边栏整块消失"。
  const checked = treeRef.value.getCheckedKeys() as number[]
  const halfChecked = treeRef.value.getHalfCheckedKeys() as number[]
  const menuIds = [...checked, ...halfChecked]

  menuSubmitting.value = true
  try {
    await assignRoleMenus(roleTarget.value.id, menuIds)
    ElMessage.success('权限分配成功')
    menuDialogVisible.value = false
  } catch {
    // 同上
  } finally {
    menuSubmitting.value = false
  }
}

const menuDialogTitle = computed(
  () => `分配权限 —— ${roleTarget.value?.roleName ?? ''}`,
)

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <div class="filters">
        <el-input
          v-model="query.roleName"
          placeholder="按角色名称搜索"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="roleList" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="roleName" label="角色名称" min-width="140" />
      <el-table-column prop="roleKey" label="角色标识" min-width="130" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="85">
        <template #default="{ row }">
          <el-tag :type="row.status === '正常' ? 'success' : 'info'" disable-transitions>
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="创建时间" width="165">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openAssignMenus(row)">分配权限</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无角色数据" />
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

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如：超级管理员" maxlength="50" />
        </el-form-item>

        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="如：admin，代码里判断权限用" maxlength="50" />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>

        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="正常">正常</el-radio>
            <el-radio value="停用">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="选填"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="menuDialogVisible" :title="menuDialogTitle" width="520px">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'menuName', children: 'children' }"
        class="menu-tree"
      />
      <p class="tip">
        勾选子菜单时父级目录会自动变成半选状态，提交时会把两者一起保存，
        这样侧边栏才能正确渲染出目录层级。
      </p>

      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="menuSubmitting" @click="submitAssignMenus">
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

.menu-tree {
  max-height: 380px;
  overflow-y: auto;
  padding: 8px 0;
}

.tip {
  margin-top: 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #94a3b8;
}
</style>
