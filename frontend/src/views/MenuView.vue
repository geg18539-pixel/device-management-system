<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  MENU_TYPE,
  MENU_TYPE_META,
  MENU_TYPE_OPTIONS,
  createMenu,
  deleteMenu,
  getMenuTree,
  updateMenu,
  type SysMenuForm,
  type SysMenuTree,
  type MenuType,
} from '../api/menu'
import { usePerm } from '../composables/usePerm'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'

const { hasPerm } = usePerm()

const loading = ref(false)
const menuTree = ref<SysMenuTree[]>([])

async function loadTree() {
  loading.value = true
  try {
    menuTree.value = await getMenuTree()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 菜单类型对应的标签颜色：目录蓝、菜单绿、按钮灰 */
/**
 * 菜单类型的文案与色调。走 api/menu.ts 里的 MENU_TYPE_META，
 * 和角色授权树共用一份，避免两处各写一套之后对不上
 */
function menuTypeMeta(type: MenuType) {
  return MENU_TYPE_META[type] ?? { label: type as string, tone: 'idle' as const }
}

/**
 * 把菜单树压平成下拉选项。
 *
 * 用缩进（全角空格）表现层级，而不是用 el-tree-select ——
 * 少一个组件的版本兼容风险，而且这里层级一般不深，缩进足够看清。
 */
const parentOptions = computed(() => {
  const out: { id: number; label: string }[] = [{ id: 0, label: '顶级菜单' }]

  const walk = (nodes: SysMenuTree[], depth: number) => {
    for (const node of nodes) {
      // 按钮类型不能当父节点，跳过
      if (node.menuType !== MENU_TYPE.BUTTON) {
        out.push({ id: node.id, label: '　'.repeat(depth + 1) + node.menuName })
      }
      if (node.children?.length) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(menuTree.value, 0)
  return out
})

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增菜单')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): SysMenuForm {
  return {
    parentId: 0,
    menuName: '',
    path: '',
    component: '',
    menuType: MENU_TYPE.MENU,
    perms: '',
    icon: '',
    sortOrder: 0,
    visible: 1,
  }
}

const form = reactive<SysMenuForm>(emptyForm())

const rules = computed<FormRules<SysMenuForm>>(() => ({
  menuName: [
    { required: true, message: '请输入菜单名称', trigger: 'blur' },
    { max: 50, message: '菜单名称不能超过 50 个字符', trigger: 'blur' },
  ],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  // 按钮只做权限点，不需要路由和组件，所以这两项只对目录/菜单校验
  path:
    form.menuType === MENU_TYPE.BUTTON
      ? []
      : [{ max: 200, message: '路由路径不能超过 200 个字符', trigger: 'blur' }],
  component: [{ max: 200, message: '组件路径不能超过 200 个字符', trigger: 'blur' }],
  perms: [{ max: 100, message: '权限标识不能超过 100 个字符', trigger: 'blur' }],
  icon: [{ max: 50, message: '图标不能超过 50 个字符', trigger: 'blur' }],
}))

async function openCreate(parentId = 0) {
  editingId.value = null
  dialogTitle.value = parentId === 0 ? '新增菜单' : '新增子菜单'
  Object.assign(form, emptyForm())
  form.parentId = parentId
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: SysMenuTree) {
  editingId.value = row.id
  dialogTitle.value = '编辑菜单'
  Object.assign(form, {
    parentId: row.parentId,
    menuName: row.menuName,
    path: row.path ?? '',
    component: row.component ?? '',
    menuType: row.menuType,
    perms: row.perms ?? '',
    icon: row.icon ?? '',
    sortOrder: row.sortOrder ?? 0,
    visible: row.visible ?? 1,
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
      await createMenu({ ...form })
      ElMessage.success('新增成功')
    } else {
      await updateMenu(editingId.value, { ...form })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadTree()
  } catch {
    // 失败提示已由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysMenuTree) {
  try {
    await ElMessageBox.confirm(`确定要删除菜单「${row.menuName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    // 后端有"菜单下还有子菜单不能删"的保护，会返回 400，提示由拦截器弹出
  }
}

onMounted(loadTree)
</script>

<template>
  <div class="page">
    <PageHeader
      title="菜单管理"
      desc="菜单以树形展示，展开可以看到下级；「按钮」类型的节点只用来挂权限标识"
    >
      <template #actions>
        <el-button v-if="hasPerm('sys:menu:add')" type="primary" @click="openCreate(0)">
          新增菜单
        </el-button>
      </template>
    </PageHeader>

    <DataPanel flush>
      <el-table
        v-loading="loading"
        :data="menuTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <StatusPlate :tone="menuTypeMeta(row.menuType).tone" :dot="false">
              {{ menuTypeMeta(row.menuType).label }}
            </StatusPlate>
          </template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="90" />
        <el-table-column prop="path" label="路由路径" min-width="150" show-overflow-tooltip />
        <el-table-column prop="component" label="组件路径" min-width="150" show-overflow-tooltip />
        <el-table-column prop="perms" label="权限标识" min-width="150" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="显示" width="70">
          <template #default="{ row }">
            <span>{{ row.visible === 1 ? '是' : '否' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="(row.menuType !== 'B') && hasPerm('sys:menu:add')"
              link
              type="primary"
              @click="openCreate(row.id)"
            >
              新增下级
            </el-button>
            <el-button v-if="hasPerm('sys:menu:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="hasPerm('sys:menu:remove')" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="还没有菜单" desc="先建目录和菜单，再去角色管理里分配权限" />
        </template>
      </el-table>
    </DataPanel>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option
              v-for="opt in parentOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio
              v-for="opt in MENU_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="侧边栏上显示的文字" maxlength="50" />
        </el-form-item>

        <el-form-item v-if="form.menuType !== 'B'" label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="如：/system/users，目录可留空" maxlength="200" />
        </el-form-item>

        <el-form-item label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="选填" maxlength="200" />
        </el-form-item>

        <el-form-item label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如：sys:user:list，选填" maxlength="100" />
        </el-form-item>

        <el-form-item label="图标" prop="icon">
          <el-input v-model="form.icon" placeholder="选填，如 Setting" maxlength="50" />
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>

        <el-form-item label="是否显示">
          <el-radio-group v-model="form.visible">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

</style>
