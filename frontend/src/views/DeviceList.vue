<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  DEVICE_STATUS,
  DEVICE_STATUS_OPTIONS,
  createDevice,
  deleteDevice,
  getDeviceList,
  updateDevice,
  type Device,
  type DeviceForm,
} from '../api/device'

const loading = ref(false)
const submitting = ref(false)
const deviceList = ref<Device[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增设备')
/** 有值表示编辑中，null 表示新增 */
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

function emptyForm(): DeviceForm {
  return {
    deviceName: '',
    deviceType: '',
    serialNumber: '',
    status: DEVICE_STATUS.ONLINE,
    location: '',
    description: '',
  }
}

const form = reactive<DeviceForm>(emptyForm())

// 校验规则和后端 Device 实体上的注解保持一致，
// 这样大部分非法输入在浏览器就被拦下了，不用等后端返回 400
const rules: FormRules<DeviceForm> = {
  deviceName: [
    { required: true, message: '请输入设备名称', trigger: 'blur' },
    { max: 100, message: '设备名称不能超过 100 个字符', trigger: 'blur' },
  ],
  deviceType: [
    { required: true, message: '请输入设备类型', trigger: 'blur' },
    { max: 50, message: '设备类型不能超过 50 个字符', trigger: 'blur' },
  ],
  status: [{ required: true, message: '请选择设备状态', trigger: 'change' }],
  serialNumber: [{ max: 100, message: '序列号不能超过 100 个字符', trigger: 'blur' }],
  location: [{ max: 100, message: '位置不能超过 100 个字符', trigger: 'blur' }],
  description: [{ max: 500, message: '描述不能超过 500 个字符', trigger: 'blur' }],
}

/** 状态对应的标签颜色：在线绿色，离线灰色，其余黄色 */
function statusTagType(status: string): 'success' | 'info' | 'warning' {
  if (status === DEVICE_STATUS.ONLINE) return 'success'
  if (status === DEVICE_STATUS.OFFLINE) return 'info'
  return 'warning'
}

/** 后端返回的是 ISO 字符串，截成 "YYYY-MM-DD HH:mm:ss" 更好看 */
function formatTime(value?: string): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

async function loadList() {
  loading.value = true
  try {
    deviceList.value = await getDeviceList()
  } catch {
    // request.ts 的响应拦截器已经统一弹过错误提示了，这里不重复弹
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增设备'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  // 等弹窗内容渲染出来再清校验状态，否则首次打开时 formRef 还是 undefined
  await nextTick()
  formRef.value?.clearValidate()
}

async function openEdit(row: Device) {
  if (row.id === undefined) {
    ElMessage.warning('该设备缺少 id，无法编辑')
    return
  }

  editingId.value = row.id
  dialogTitle.value = '编辑设备'
  Object.assign(form, {
    deviceName: row.deviceName,
    deviceType: row.deviceType,
    serialNumber: row.serialNumber ?? '',
    status: row.status,
    location: row.location ?? '',
    description: row.description ?? '',
  })
  dialogVisible.value = true
  await nextTick()
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return

  // validate() 校验不通过时会 reject，这里转成 false
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editingId.value === null) {
      await createDevice({ ...form })
      ElMessage.success('新增成功')
    } else {
      await updateDevice(editingId.value, { ...form })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await loadList()
  } catch {
    // 失败提示同样由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: Device) {
  if (row.id === undefined) return

  try {
    await ElMessageBox.confirm(`确定要删除设备「${row.deviceName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    // 点了取消，ElMessageBox 会 reject，这里直接结束
    return
  }

  try {
    await deleteDevice(row.id)
    ElMessage.success('删除成功')
    await loadList()
  } catch {
    // 同上
  }
}

onMounted(loadList)
</script>

<template>
  <div class="device-page">
    <div class="toolbar">
      <h2>设备管理</h2>
      <el-button type="primary" @click="openCreate">新增设备</el-button>
    </div>

    <el-table v-loading="loading" :data="deviceList" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="deviceName" label="设备名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="deviceType" label="设备类型" min-width="110" show-overflow-tooltip />
      <el-table-column prop="serialNumber" label="序列号" min-width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" disable-transitions>
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="location" label="位置" min-width="110" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="暂无设备数据" />
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="设备名称" prop="deviceName">
          <el-input
            v-model="form.deviceName"
            placeholder="请输入设备名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="设备类型" prop="deviceType">
          <el-input v-model="form.deviceType" placeholder="如：传感器 / 网关" maxlength="50" />
        </el-form-item>

        <el-form-item label="序列号" prop="serialNumber">
          <el-input v-model="form.serialNumber" placeholder="选填，需全局唯一" maxlength="100" />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="opt in DEVICE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" placeholder="选填" maxlength="100" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
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
  </div>
</template>

<style scoped>
.device-page {
  text-align: left;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text-h, #08060d);
}
</style>
