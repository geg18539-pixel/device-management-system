<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  DEPT_ROOT_PARENT_ID,
  DEPT_STATUS,
  DEPT_STATUS_OPTIONS,
  createDept,
  deleteDept,
  flattenDepts,
  getDeptTree,
  updateDept,
  type DeptForm,
  type DeptTree,
} from '../api/dept'
import { usePerm } from '../composables/usePerm'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import type { PlateTone } from '../utils/plateTone'

const { hasPerm } = usePerm()

const loading = ref(false)
const deptTree = ref<DeptTree[]>([])

async function loadTree() {
  loading.value = true
  try {
    deptTree.value = await getDeptTree()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 压平成下拉选项，用缩进表现层级 */
const parentOptions = computed(() => flattenDepts(deptTree.value, '顶级部门'))

/** 部门状态的铭牌色调 */
function statusTone(status: string): PlateTone {
  return status === DEPT_STATUS.NORMAL ? 'ok' : 'idle'
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogTitle = ref('新增部门')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): DeptForm {
  return {
    parentId: DEPT_ROOT_PARENT_ID,
    deptName: '',
    sortOrder: 0,
    leader: '',
    phone: '',
    status: DEPT_STATUS.NORMAL,
    remark: '',
  }
}

const form = reactive<DeptForm>(emptyForm())

const rules: FormRules<DeptForm> = {
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 50, message: '部门名称不能超过 50 个字符', trigger: 'blur' },
  ],
  leader: [{ max: 50, message: '负责人不能超过 50 个字符', trigger: 'blur' }],
  phone: [{ max: 20, message: '联系电话不能超过 20 个字符', trigger: 'blur' }],
  remark: [{ max: 200, message: '备注不能超过 200 个字符', trigger: 'blur' }],
}

/**
 * 编辑某个部门时，它自己以及它的所有后代都不能再被选为「上级部门」。
 *
 * <p>后端会拦（会返回 400），但让下拉里直接不出现这些项更友好 ——
 * 用户根本选不到，就不会撞到错误。
 */
const forbiddenParentIds = computed<Set<number>>(() => {
  const set = new Set<number>()
  if (editingId.value === null) return set

  const collect = (nodes: DeptTree[], inside: boolean) => {
    for (const node of nodes) {
      const nowInside = inside || node.id === editingId.value
      if (nowInside) set.add(node.id)
      if (node.children?.length) collect(node.children, nowInside)
    }
  }
  collect(deptTree.value, false)
  return set
})

const selectableParentOptions = computed(() =>
  parentOptions.value.filter((opt) => !forbiddenParentIds.value.has(opt.id)),
)

async function openCreate(parentId: number = DEPT_ROOT_PARENT_ID) {
  editingId.value = null
  dialogTitle.value = parentId === DEPT_ROOT_PARENT_ID ? '新增部门' : '新增下级部门'
  Object.assign(form, emptyForm())
  form.parentId = parentId
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: DeptTree) {
  editingId.value = row.id
  dialogTitle.value = '编辑部门'
  Object.assign(form, {
    parentId: row.parentId,
    deptName: row.deptName,
    sortOrder: row.sortOrder ?? 0,
    leader: row.leader ?? '',
    phone: row.phone ?? '',
    status: row.status,
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
      await createDept({ ...form })
      ElMessage.success('新增成功')
    } else {
      await updateDept(editingId.value, { ...form })
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
    await loadTree()
  } catch {
    // 同名部门、移动到自己的下级等业务错误后端返回 400，提示由拦截器弹出
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: DeptTree) {
  try {
    await ElMessageBox.confirm(`确定要删除部门「${row.deptName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  try {
    await deleteDept(row.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    // 后端有"有子部门 / 有设备 / 有用户不能删"的保护，会返回 400
  }
}

onMounted(loadTree)
</script>

<template>
  <div class="page">
    <PageHeader
      title="部门管理"
      desc="部门支持多级；设备归属部门后，设备列表和台账都能按部门筛选"
    >
      <template #actions>
        <el-button v-if="hasPerm('sys:dept:add')" type="primary" @click="openCreate()">
          新增部门
        </el-button>
      </template>
    </PageHeader>

    <DataPanel flush>
      <el-table
        v-loading="loading"
        :data="deptTree"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column prop="leader" label="负责人" width="110" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <StatusPlate :tone="statusTone(row.status)">{{ row.status }}</StatusPlate>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('sys:dept:add')" link type="primary" @click="openCreate(row.id)">新增下级</el-button>
            <el-button v-if="hasPerm('sys:dept:edit')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="hasPerm('sys:dept:remove')" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState title="还没有部门" desc="建好部门之后，设备建档时就能选归属部门了" />
        </template>
      </el-table>
    </DataPanel>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级部门">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option
              v-for="opt in selectableParentOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" placeholder="如：运维部" maxlength="50" />
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="负责人" prop="leader">
              <el-input v-model="form.leader" placeholder="选填" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="选填" maxlength="20" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio v-for="opt in DEPT_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" />
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
