<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  DICT_STATUS,
  DICT_STATUS_OPTIONS,
  clearDictCache,
  createDictItem,
  createDictType,
  deleteDictItem,
  deleteDictType,
  getDictItems,
  getDictTypes,
  updateDictItem,
  updateDictType,
  type SysDictItem,
  type SysDictType,
} from '../api/dict'
import { usePerm } from '../composables/usePerm'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'

const { hasPerm } = usePerm()

// ============================================================
// 左侧：字典类型
// ============================================================
const loadingTypes = ref(false)
const types = ref<SysDictType[]>([])
const activeType = ref<SysDictType | null>(null)

async function loadTypes() {
  loadingTypes.value = true
  try {
    types.value = await getDictTypes()
    // 保持当前选中；没有选中的话默认选第一个（页面一打开就能看到内容）
    const keep = types.value.find((t) => t.id === activeType.value?.id)
    activeType.value = keep ?? types.value[0] ?? null
    await loadItems()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loadingTypes.value = false
  }
}

function selectType(row: SysDictType) {
  activeType.value = row
  void loadItems()
}

// ---------------- 类型的新增 / 编辑 ----------------
const typeDialogVisible = ref(false)
const typeDialogTitle = ref('新增字典类型')
const typeSubmitting = ref(false)
const editingTypeId = ref<number | null>(null)
const typeFormRef = ref<FormInstance>()

function emptyTypeForm() {
  return { dictName: '', dictType: '', status: DICT_STATUS.NORMAL, remark: '' }
}

const typeForm = reactive(emptyTypeForm())

const typeRules: FormRules<typeof typeForm> = {
  dictName: [
    { required: true, message: '请输入字典名称', trigger: 'blur' },
    { max: 100, message: '不能超过 100 个字符', trigger: 'blur' },
  ],
  dictType: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: '只能用小写字母、数字和下划线，且以字母开头',
      trigger: 'blur',
    },
  ],
  remark: [{ max: 200, message: '不能超过 200 个字符', trigger: 'blur' }],
}

async function openCreateType() {
  editingTypeId.value = null
  typeDialogTitle.value = '新增字典类型'
  Object.assign(typeForm, emptyTypeForm())
  typeDialogVisible.value = true
  await nextTick()
  typeFormRef.value?.clearValidate()
}

async function openEditType(row: SysDictType) {
  editingTypeId.value = row.id
  typeDialogTitle.value = '编辑字典类型'
  Object.assign(typeForm, {
    dictName: row.dictName,
    dictType: row.dictType,
    status: row.status,
    remark: row.remark ?? '',
  })
  typeDialogVisible.value = true
  await nextTick()
  typeFormRef.value?.clearValidate()
}

async function submitType() {
  if (!typeFormRef.value) return
  const valid = await typeFormRef.value.validate().catch(() => false)
  if (!valid) return

  typeSubmitting.value = true
  try {
    if (editingTypeId.value === null) {
      await createDictType({ ...typeForm })
      ElMessage.success('新增成功')
    } else {
      await updateDictType(editingTypeId.value, { ...typeForm })
      ElMessage.success('修改成功')
    }
    typeDialogVisible.value = false
    clearDictCache()
    await loadTypes()
  } catch {
    // 编码重复等业务错误
  } finally {
    typeSubmitting.value = false
  }
}

async function handleDeleteType(row: SysDictType) {
  try {
    await ElMessageBox.confirm(
      `确定要删除字典「${row.dictName}」吗？下面还有字典项时会拒绝删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteDictType(row.id)
    ElMessage.success('删除成功')
    activeType.value = null
    clearDictCache()
    await loadTypes()
  } catch {
    // 下面还有字典项，后端返回 400
  }
}

// ============================================================
// 右侧：字典项
// ============================================================
const loadingItems = ref(false)
const items = ref<SysDictItem[]>([])

async function loadItems() {
  if (!activeType.value) {
    items.value = []
    return
  }
  loadingItems.value = true
  try {
    items.value = await getDictItems(activeType.value.dictType)
  } catch {
    // 同上
  } finally {
    loadingItems.value = false
  }
}

const itemDialogVisible = ref(false)
const itemDialogTitle = ref('新增字典项')
const itemSubmitting = ref(false)
const editingItemId = ref<number | null>(null)
const itemFormRef = ref<FormInstance>()

function emptyItemForm() {
  return { itemLabel: '', itemValue: '', sortOrder: 0, status: DICT_STATUS.NORMAL, remark: '' }
}

const itemForm = reactive(emptyItemForm())

const itemRules: FormRules<typeof itemForm> = {
  itemLabel: [
    { required: true, message: '请输入展示文案', trigger: 'blur' },
    { max: 100, message: '不能超过 100 个字符', trigger: 'blur' },
  ],
  itemValue: [
    { required: true, message: '请输入键值', trigger: 'blur' },
    { max: 100, message: '不能超过 100 个字符', trigger: 'blur' },
  ],
  remark: [{ max: 200, message: '不能超过 200 个字符', trigger: 'blur' }],
}

async function openCreateItem() {
  if (!activeType.value) return
  editingItemId.value = null
  itemDialogTitle.value = '新增字典项'
  // 排序号默认接着最后一个，省得每加一条都要手填
  const maxSort = items.value.reduce((max, i) => Math.max(max, i.sortOrder ?? 0), 0)
  Object.assign(itemForm, emptyItemForm())
  itemForm.sortOrder = maxSort + 1
  itemDialogVisible.value = true
  await nextTick()
  itemFormRef.value?.clearValidate()
}

async function openEditItem(row: SysDictItem) {
  editingItemId.value = row.id
  itemDialogTitle.value = '编辑字典项'
  Object.assign(itemForm, {
    itemLabel: row.itemLabel,
    itemValue: row.itemValue,
    sortOrder: row.sortOrder ?? 0,
    status: row.status,
    remark: row.remark ?? '',
  })
  itemDialogVisible.value = true
  await nextTick()
  itemFormRef.value?.clearValidate()
}

async function submitItem() {
  if (!itemFormRef.value || !activeType.value) return
  const valid = await itemFormRef.value.validate().catch(() => false)
  if (!valid) return

  itemSubmitting.value = true
  try {
    if (editingItemId.value === null) {
      await createDictItem({ ...itemForm, dictType: activeType.value.dictType })
      ElMessage.success('新增成功')
    } else {
      await updateDictItem(editingItemId.value, {
        ...itemForm,
        dictType: activeType.value.dictType,
      })
      ElMessage.success('修改成功')
    }
    itemDialogVisible.value = false
    clearDictCache()
    await loadItems()
  } catch {
    // 键值重复等业务错误
  } finally {
    itemSubmitting.value = false
  }
}

async function handleDeleteItem(row: SysDictItem) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${row.itemLabel}」吗？业务表里已经存了这个值的历史数据将显示为编码而不是文案。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteDictItem(row.id)
    ElMessage.success('删除成功')
    clearDictCache()
    await loadItems()
  } catch {
    // 同上
  }
}

const itemCount = computed(() => items.value.length)

/** 右侧面板的标题和补充说明（跟着选中的字典变） */
const itemPanelTitle = computed(() =>
  activeType.value ? `字典项 —— ${activeType.value.dictName}` : '字典项',
)

const itemPanelHint = computed(() =>
  activeType.value ? `${activeType.value.dictType}，共 ${itemCount.value} 项` : '',
)

onMounted(loadTypes)
</script>

<template>
  <div class="page">
    <PageHeader
      title="字典管理"
      desc="维护「纯分类」的枚举值，改完即时生效；参与流程判断的状态值不在这里"
    />

    <el-alert type="info" :closable="false" show-icon class="tip">
      <template #title>字典用来维护“纯分类”的枚举值，改完即时生效</template>
      <div class="tip-body">
        展示文案（label）和键值（value）是分开的：<strong>改文案不影响历史数据</strong>，
        但改键值会让已存了旧值的业务数据对不上。停用的项不再出现在下拉里，但历史数据仍能正常显示。
        <br />
        注意：设备状态、工单状态这类<strong>参与流程判断</strong>的状态值不在这里维护，它们由程序控制。
      </div>
    </el-alert>

    <div class="layout">
      <!-- 左：字典类型 -->
      <DataPanel class="left" title="字典类型">
        <template #actions>
          <el-button v-if="hasPerm('sys:dict:add')" size="small" type="primary" @click="openCreateType">
            新增
          </el-button>
        </template>

        <el-table
          v-loading="loadingTypes"
          :data="types"
          highlight-current-row
          :current-row-key="activeType?.id"
          row-key="id"
          @row-click="selectType"
        >
          <el-table-column prop="dictName" label="名称" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">
              <span :class="{ disabled: row.status !== DICT_STATUS.NORMAL }">{{ row.dictName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="dictType" label="编码" width="110" show-overflow-tooltip />
          <el-table-column label="操作" width="110" align="right">
            <template #default="{ row }">
              <el-button v-if="hasPerm('sys:dict:edit')"
                link
                type="primary"
                @click.stop="openEditType(row)"
              >
                改
              </el-button>
              <el-button v-if="hasPerm('sys:dict:remove')"
                link
                type="danger"
                @click.stop="handleDeleteType(row)"
              >
                删
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState title="还没有字典" desc="先建一个字典类型，比如「故障类型」" />
          </template>
        </el-table>
      </DataPanel>

      <!-- 右：字典项 -->
      <DataPanel class="right" :title="itemPanelTitle" :hint="itemPanelHint">
        <template #actions>
          <el-button v-if="hasPerm('sys:dict:add')"
            size="small"
            type="primary"
            :disabled="!activeType"
            @click="openCreateItem"
          >
            新增字典项
          </el-button>
        </template>

        <el-table v-loading="loadingItems" :data="items">
          <el-table-column prop="itemLabel" label="展示文案" min-width="140" show-overflow-tooltip />
          <el-table-column prop="itemValue" label="键值" width="130" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="70" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <StatusPlate :tone="row.status === DICT_STATUS.NORMAL ? 'ok' : 'idle'">
                {{ row.status }}
              </StatusPlate>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="hasPerm('sys:dict:edit')" link type="primary" @click="openEditItem(row)">
                编辑
              </el-button>
              <el-button v-if="hasPerm('sys:dict:remove')" link type="danger" @click="handleDeleteItem(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState
              :title="activeType ? '这个字典下还没有项' : '还没有选中字典'"
              :desc="activeType ? '点右上角「新增字典项」添加第一个' : '先在左边选一个字典，这里会显示它的项'"
            />
          </template>
        </el-table>
      </DataPanel>
    </div>

    <!-- 字典类型弹窗 -->
    <el-dialog v-model="typeDialogVisible" :title="typeDialogTitle" width="480px">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="如 故障类型" maxlength="100" />
        </el-form-item>
        <el-form-item label="类型编码" prop="dictType">
          <el-input
            v-model="typeForm.dictType"
            placeholder="如 fault_type"
            :disabled="editingTypeId !== null"
            maxlength="50"
          />
          <span v-if="editingTypeId !== null" class="inline-tip">编码是程序引用的标识，不能修改</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio v-for="o in DICT_STATUS_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitting" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 字典项弹窗 -->
    <el-dialog v-model="itemDialogVisible" :title="itemDialogTitle" width="480px">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="90px">
        <el-form-item label="展示文案" prop="itemLabel">
          <el-input v-model="itemForm.itemLabel" placeholder="如 机械故障" maxlength="100" />
        </el-form-item>
        <el-form-item label="键值" prop="itemValue">
          <el-input v-model="itemForm.itemValue" placeholder="如 MECH（存进业务表的就是它）" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio v-for="o in DICT_STATUS_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
          <span class="inline-tip">停用后不再出现在下拉里</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <p class="tip-text">
        改「展示文案」是安全的 —— 历史数据存的是键值，页面会自动显示成新文案。
        改「键值」要谨慎：已经存了旧值的业务数据会显示成编码。
      </p>
      <template #footer>
        <el-button @click="itemDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemSubmitting" @click="submitItem">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

.tip {
  margin-bottom: 12px;
}

.tip-body {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
}

.layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 12px;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

/* 面板标题和操作按钮现在由 DataPanel 的 header 提供，
   原来那套 .card-head / .card-title / .muted 不需要了 */

.disabled {
  color: var(--ink-3);
  text-decoration: line-through;
}

.inline-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ink-3);
}

.tip-text {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* 让左侧表格的行看起来可点 */
.left :deep(.el-table__row) {
  cursor: pointer;
}
</style>
