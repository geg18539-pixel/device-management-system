<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import {
  DEVICE_LIFECYCLE,
  DEVICE_LIFECYCLE_TONE,
  deleteAttachment,
  downloadAttachment,
  fetchAttachmentBlob,
  getDeviceHealth,
  getDeviceProfile,
  healthGradeTone,
  type DeviceAttachment,
  type DeviceHealth,
  type DeviceProfile,
  uploadDeviceAttachment,
} from '../api/device'
import { auditToneOf, listDeviceAuditLogs, type AuditLog } from '../api/audit'
import { REPAIR_STATUS, normalizeRepairStatus } from '../api/deviceRepair'
import { MAINTENANCE_RESULT, daysUntilDue, isOverdue } from '../api/maintenance'
import { buildCategoryNameMap, getCategoryTree, type DeviceCategoryTree } from '../api/deviceCategory'
import { buildDeptNameMap, getDeptTree, type DeptTree } from '../api/dept'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import QrcodeVue from 'qrcode.vue'
import { usePerm } from '../composables/usePerm'
import { useAppStore } from '../stores/app'
import { isLoopbackUrl } from '../utils/baseUrl'
import type { PlateTone } from '../utils/plateTone'

const { hasPerm } = usePerm()
const appStore = useAppStore()

const route = useRoute()
const router = useRouter()

const deviceId = Number(route.params.id)
const loading = ref(false)
const profile = ref<DeviceProfile | null>(null)
const activeTab = ref('basic')

// ---------------- 资产标签（二维码） ----------------

const labelVisible = ref(false)

/**
 * 二维码里放的是**这台设备档案页的完整地址**，不是资产编号。
 *
 * <p>放地址的话，用手机相机直接扫就能打开对应页面，不需要先打开本系统、
 * 再手工输入编号。而资产编号另外用大字印在码的下方 —— 两者的用途不同：
 * 码是给"手上正好有手机"的场合，编号是给人读、给没网/没装系统时兜底的。
 *
 * <p>⚠️ <b>地址优先取管理员配的「对外访问地址」，而不是当前页面地址。</b>
 * 这一点第一版做反了：当时想的是"看这个页面的人正好在他能访问的地址上"，
 * 所以直接用 {@code window.location.origin}。但二维码是给**别的设备**扫的 ——
 * 在开发机上浏览器里是 {@code localhost:5173}，手机扫了必然打不开。
 * 真实可用的地址只有管理员知道，所以它是个系统参数。
 * 具体拼法见 {@code stores/app.ts} 的 {@code deviceUrl}。
 */
const labelUrl = computed(() => appStore.deviceUrl(deviceId))

/**
 * 二维码里的地址是不是"本机地址"。
 *
 * <p>是的话，**这台机器之外的任何设备都扫不开** —— 而这只有扫的人才会发现。
 * 与其让人印一堆标签贴上去才发现扫不动，不如在弹窗里直接说清楚怎么改。
 */
const labelUrlIsLocal = computed(() => isLoopbackUrl(labelUrl.value))

async function copyLabelUrl() {
  try {
    // ⚠️ clipboard API 只在安全上下文（HTTPS 或 localhost）可用。
    // 内网用 IP + HTTP 部署时会直接抛，所以必须有退路
    await navigator.clipboard.writeText(labelUrl.value)
    ElMessage.success('链接已复制')
  } catch {
    // 退化成"让用户自己选中复制"。链接那一行的 CSS 开了 user-select: all，
    // 点一下就会整段选中，再按 Ctrl+C 即可
    ElMessage.warning('当前环境不允许自动复制，请点一下链接再按 Ctrl+C')
  }
}

/** 健康分。所有登录角色都能看（和看板上那张卡是同一套算法、同一个值） */
const health = ref<DeviceHealth | null>(null)
/**
 * 变更审计。**只有拿到 sys:audit:list 的角色才加载** ——
 * 没权限还去请求的话会拿到 403，控制台里多一条无意义的报错，
 * 页签本身也会用 hasPerm 藏起来
 */
const auditLogs = ref<AuditLog[]>([])
const auditLoaded = ref(false)

const categoryNameMap = ref<Record<number, string>>({})
const deptNameMap = ref<Record<number, string>>({})

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await getDeviceProfile(deviceId)
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function loadReferenceData() {
  // 分类树和部门树用来把 id 显示成名称。
  // 两个都失败也不影响页面主体，所以各自吞掉异常
  try {
    const tree: DeviceCategoryTree[] = await getCategoryTree()
    categoryNameMap.value = buildCategoryNameMap(tree)
  } catch {
    /* 忽略 */
  }
  try {
    const tree: DeptTree[] = await getDeptTree()
    deptNameMap.value = buildDeptNameMap(tree)
  } catch {
    /* 忽略 */
  }
}

async function loadHealth() {
  try {
    health.value = await getDeviceHealth(deviceId)
  } catch {
    // 拿不到就不显示这一行 —— 健康分不该有能力让整个档案页打不开
  }
}

async function loadAuditLogs() {
  try {
    auditLogs.value = await listDeviceAuditLogs(deviceId)
    auditLoaded.value = true
  } catch {
    // 同上，失败时页签里显示空状态
  }
}

onMounted(async () => {
  const tasks = [loadProfile(), loadReferenceData(), loadHealth()]
  // 页签标题上要显示条数，所以有权限就一起拉；
  // 没权限的话连请求都不发 —— 否则控制台会多一条必然的 403
  if (hasPerm('sys:audit:list')) {
    tasks.push(loadAuditLogs())
  }
  await Promise.all(tasks)
})

const device = computed(() => profile.value?.device)
const plan = computed(() => profile.value?.maintenancePlan)

function categoryLabel(id?: number): string {
  if (id === undefined || id === null) return '未分类'
  return categoryNameMap.value[id] ?? `分类#${id}`
}

function deptLabel(id?: number): string {
  if (id === undefined || id === null) return '未分配'
  return deptNameMap.value[id] ?? `部门#${id}`
}

function lifecycleText(status?: string): string {
  return status && status.trim() ? status : DEVICE_LIFECYCLE.NORMAL
}

/** 生命周期状态的铭牌色调。和设备列表页共用同一份映射 */
function lifecycleTone(status?: string): PlateTone {
  return DEVICE_LIFECYCLE_TONE[lifecycleText(status)] ?? 'idle'
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function dash(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

function goBack() {
  void router.push('/devices')
}

/** 维修工单的状态色调。判断前先归一，历史「待维修」也按待受理处理 */
function repairTone(status: string): PlateTone {
  const s = normalizeRepairStatus(status)
  if (s === REPAIR_STATUS.FINISHED) return 'ok'
  if (s === REPAIR_STATUS.REPAIRING) return 'warn'
  return 'idle'
}

/** 维保计划到期情况的色调：逾期 / 临期 / 正常。没排期的按中性处理 */
function planTone(nextDate?: string): PlateTone {
  if (!nextDate) return 'idle'
  if (isOverdue(nextDate)) return 'crit'
  const days = daysUntilDue(nextDate)
  return days !== null && days <= 30 ? 'warn' : 'ok'
}

function planText(nextDate?: string): string {
  if (!nextDate) return '未排期'
  const days = daysUntilDue(nextDate)
  if (days === null) return nextDate
  if (days < 0) return `已逾期 ${-days} 天`
  if (days === 0) return '今天到期'
  return `还剩 ${days} 天`
}

// ---------------- 附件 ----------------

/** 后端白名单的前端副本，只用于"不合规的文件连传都不用传"的即时反馈 */
const ALLOWED_EXT = [
  'jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp',
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv',
]
const MAX_SIZE_MB = 10

/** 上传前的本地校验。**这不是安全边界** —— 后端仍然会独立校验一次 */
function beforeUpload(file: File): boolean {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_EXT.includes(ext)) {
    ElMessage.error(`不支持的文件类型：.${ext}`)
    return false
  }
  if (file.size > MAX_SIZE_MB * 1024 * 1024) {
    ElMessage.error(`文件大小超过限制（最大 ${MAX_SIZE_MB} MB）`)
    return false
  }
  return true
}

/**
 * 自定义上传。
 *
 * <p>用 el-upload 的 http-request 钩子而不是它自带的 XHR：
 * 走我们自己的 axios 才能带上 Authorization 头、复用统一的错误处理。
 */
async function handleUpload(options: UploadRequestOptions) {
  try {
    await uploadDeviceAttachment(deviceId, options.file as File)
    ElMessage.success('上传成功')
    await loadProfile()
  } catch {
    // 同上
  }
}

async function handleDownload(att: DeviceAttachment) {
  try {
    await downloadAttachment(att.id, att.fileName)
  } catch {
    // 同上
  }
}

// 图片预览：附件接口要鉴权，`<img src>` 发不出 Authorization 头，
// 所以只能先取回二进制再转成 blob URL
const previewVisible = ref(false)
const previewUrl = ref('')
const previewName = ref('')

async function handlePreview(att: DeviceAttachment) {
  const isImage = att.contentType?.startsWith('image/')
  if (!isImage) {
    // 不是图片没什么可预览的，直接下载
    await handleDownload(att)
    return
  }
  try {
    const blob = await fetchAttachmentBlob(att.id, true)
    // 换新图前先释放旧的，否则每预览一张就泄漏一个 blob
    if (previewUrl.value) {
      window.URL.revokeObjectURL(previewUrl.value)
    }
    previewUrl.value = window.URL.createObjectURL(blob)
    previewName.value = att.fileName
    previewVisible.value = true
  } catch {
    // 同上
  }
}

onBeforeUnmount(() => {
  // 组件卸载时释放 blob，不释放会一直挂到页面关闭
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
  }
})

async function handleDeleteAttachment(att: DeviceAttachment) {
  try {
    await ElMessageBox.confirm(`确定要删除附件「${att.fileName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteAttachment(att.id)
    ElMessage.success('删除成功')
    await loadProfile()
  } catch {
    // 同上
  }
}

function formatSize(bytes?: number): string {
  if (!bytes && bytes !== 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader
      title="设备档案"
      desc="这台设备的完整档案：基础信息、维保、工单、配件和附件"
    >
      <template #actions>
        <el-button v-if="device" @click="labelVisible = true">资产标签</el-button>
        <el-button @click="goBack">返回列表</el-button>
      </template>
    </PageHeader>

    <!-- ---------- 顶部：设备标识 ---------- -->
    <DataPanel class="head-card">
      <div class="head">
        <div class="head-left">
          <span class="device-name">{{ device?.deviceName ?? '—' }}</span>
          <StatusPlate v-if="device" :tone="lifecycleTone(device.lifecycleStatus)">
            {{ lifecycleText(device.lifecycleStatus) }}
          </StatusPlate>
          <StatusPlate v-if="device" tone="idle">{{ device.status }}</StatusPlate>
        </div>
        <div class="head-right">
          <span class="meta">资产编号：{{ dash(device?.assetCode) }}</span>
          <span class="meta">序列号：{{ dash(device?.serialNumber) }}</span>
        </div>
      </div>

      <!-- 报废信息单独提示一块：报废的设备在业务上是"已淘汰"，
           这个事实比任何其他字段都更该被一眼看到 -->
      <el-alert
        v-if="device?.scrapDate"
        type="error"
        :closable="false"
        show-icon
        class="scrap-alert"
        :title="`该设备已于 ${device.scrapDate} 报废`"
        :description="`原因：${device.scrapReason ?? '—'}　操作人：${device.scrapOperator ?? '—'}`"
      />
    </DataPanel>

    <!-- ---------- 六个区块 ---------- -->
    <el-tabs v-model="activeTab" class="tabs">
      <!-- 1. 基础信息 -->
      <el-tab-pane label="基础信息" name="basic">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="设备名称">{{ dash(device?.deviceName) }}</el-descriptions-item>
          <el-descriptions-item label="资产编号">{{ dash(device?.assetCode) }}</el-descriptions-item>
          <el-descriptions-item label="序列号">{{ dash(device?.serialNumber) }}</el-descriptions-item>
          <el-descriptions-item label="所属分类">{{ categoryLabel(device?.categoryId) }}</el-descriptions-item>
          <el-descriptions-item label="所属部门">{{ deptLabel(device?.deptId) }}</el-descriptions-item>
          <el-descriptions-item label="设备类型">{{ dash(device?.deviceType) }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ dash(device?.model) }}</el-descriptions-item>
          <el-descriptions-item label="生产厂商">{{ dash(device?.manufacturer) }}</el-descriptions-item>
          <el-descriptions-item label="存放位置">{{ dash(device?.location) }}</el-descriptions-item>
          <el-descriptions-item label="连通状态">{{ dash(device?.status) }}</el-descriptions-item>
          <el-descriptions-item label="资产状态">{{ lifecycleText(device?.lifecycleStatus) }}</el-descriptions-item>
          <el-descriptions-item label="当前借用人">{{ dash(device?.borrower) }}</el-descriptions-item>
          <el-descriptions-item label="采购日期">{{ dash(device?.purchaseDate) }}</el-descriptions-item>
          <el-descriptions-item label="保修到期">{{ dash(device?.warrantyDate) }}</el-descriptions-item>
          <el-descriptions-item label="借出时间">{{ formatTime(device?.borrowTime) }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ formatTime(device?.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ formatTime(device?.updateTime) }}</el-descriptions-item>

          <!-- 健康分。算出来的、实时的，和看板那张卡同源。
               除了分数还给出扣分原因 —— 一个没有解释的分数没人敢信 -->
          <el-descriptions-item label="健康分" :span="3">
            <template v-if="health">
              <StatusPlate :tone="healthGradeTone(health.grade)">
                {{ health.score }} 分 · {{ health.grade }}
              </StatusPlate>
              <span class="health-why">
                {{ health.reasons.length ? health.reasons.join('、') : '没有扣分项' }}
              </span>
            </template>
            <span v-else class="muted">—</span>
          </el-descriptions-item>

          <el-descriptions-item label="备注" :span="3">{{ dash(device?.description) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 维保计划 -->
        <DataPanel title="维保计划" class="sub-card">
          <el-descriptions v-if="plan" :column="3" border>
            <el-descriptions-item label="计划名称">{{ plan.planName }}</el-descriptions-item>
            <el-descriptions-item label="保养周期">{{ plan.cycleDays }} 天</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ dash(plan.maintainer) }}</el-descriptions-item>
            <el-descriptions-item label="上次保养">{{ dash(plan.lastMaintenanceDate) }}</el-descriptions-item>
            <el-descriptions-item label="下次到期">{{ dash(plan.nextMaintenanceDate) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <StatusPlate :tone="planTone(plan.nextMaintenanceDate)">
                {{ planText(plan.nextMaintenanceDate) }}
              </StatusPlate>
            </el-descriptions-item>
          </el-descriptions>
          <EmptyState
            v-else
            title="该设备还没有维保计划"
            desc="去「维保管理」建一个计划，到期前会自动提醒负责人"
          />
        </DataPanel>
      </el-tab-pane>

      <!-- 2. 维保记录 -->
      <el-tab-pane :label="`维保记录 (${profile?.maintenanceRecords?.length ?? 0})`" name="maintenance">
        <DataPanel flush>
            <el-table :data="profile?.maintenanceRecords ?? []">
            <el-table-column prop="maintenanceDate" label="保养日期" width="120" />
            <el-table-column prop="maintainer" label="保养人" width="110" />
            <el-table-column prop="content" label="保养内容" min-width="240" show-overflow-tooltip />
            <el-table-column label="结果" width="140">
              <template #default="{ row }">
                <StatusPlate
                  :tone="row.result === MAINTENANCE_RESULT.NORMAL ? 'ok' : 'warn'"
                >
                  {{ row.result || '—' }}
                </StatusPlate>
              </template>
            </el-table-column>
            <el-table-column label="费用" width="100">
              <template #default="{ row }">{{ row.cost ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <template #empty>
              <EmptyState title="还没有维保记录" desc="执行一次保养之后，这里会留下存档" />
            </template>
          </el-table>
        </DataPanel>
      </el-tab-pane>

      <!-- 3. 维修工单历史 -->
      <el-tab-pane :label="`维修工单 (${profile?.repairHistory?.length ?? 0})`" name="repairs">
        <DataPanel flush>
            <el-table :data="profile?.repairHistory ?? []">
            <el-table-column prop="faultDesc" label="故障描述" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <StatusPlate :tone="repairTone(row.repairStatus)">
                  {{ normalizeRepairStatus(row.repairStatus) }}
                </StatusPlate>
              </template>
            </el-table-column>
            <el-table-column prop="reporter" label="报修人" width="100" />
            <el-table-column prop="repairer" label="维修人" width="100" />
            <el-table-column label="报修时间" width="165">
              <template #default="{ row }">{{ formatTime(row.reportTime) }}</template>
            </el-table-column>
            <el-table-column label="完工时间" width="165">
              <template #default="{ row }">{{ formatTime(row.finishTime) }}</template>
            </el-table-column>
            <el-table-column label="费用" width="100">
              <template #default="{ row }">{{ row.cost ?? '—' }}</template>
            </el-table-column>
            <template #empty>
              <EmptyState title="这台设备还没有报修过" desc="在设备列表里发起报修，工单会出现在这里" />
            </template>
          </el-table>
        </DataPanel>
      </el-tab-pane>

      <!-- 4. 配件更换记录 -->
      <el-tab-pane :label="`配件更换 (${profile?.partRecords?.length ?? 0})`" name="parts">
        <DataPanel flush>
            <el-table :data="profile?.partRecords ?? []">
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <StatusPlate :tone="row.recordType === '入库' ? 'ok' : 'warn'">
                  {{ row.recordType }}
                </StatusPlate>
              </template>
            </el-table-column>
            <el-table-column prop="partCode" label="配件编码" width="130" />
            <el-table-column prop="partName" label="配件名称" min-width="150" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="关联工单" width="110">
              <template #default="{ row }">#{{ row.relatedRepairId ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="110" />
            <el-table-column label="时间" width="165">
              <template #default="{ row }">{{ formatTime(row.recordTime) }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
            <template #empty>
              <EmptyState
                title="还没有配件更换记录"
                desc="在配件页出库时关联本设备的工单，这里就会有记录"
              />
            </template>
          </el-table>
        </DataPanel>
      </el-tab-pane>

      <!-- 5. 附件 -->
      <el-tab-pane :label="`附件 (${profile?.attachments?.length ?? 0})`" name="attachments">
        <DataPanel flush>
            <div class="attach-toolbar">
              <el-upload v-if="hasPerm('dev:attachment:upload')"
                :show-file-list="false"
                :http-request="handleUpload"
                :before-upload="beforeUpload"
              >
                <el-button type="primary">上传附件</el-button>
              </el-upload>
              <span class="hint">
                支持图片、PDF、Office 文档，单个不超过 {{ MAX_SIZE_MB }} MB
              </span>
            </div>

            <el-table :data="profile?.attachments ?? []">
            <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="uploader" label="上传人" width="110" />
            <el-table-column label="上传时间" width="165">
              <template #default="{ row }">{{ formatTime(row.uploadTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handlePreview(row)">预览/下载</el-button>
                <el-button v-if="hasPerm('dev:attachment:remove')" link type="danger" @click="handleDeleteAttachment(row)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <EmptyState title="还没有附件" desc="上传设备台账、发票或照片，都留在这台设备下" />
            </template>
          </el-table>
        </DataPanel>
      </el-tab-pane>

      <!-- 6. 调拨记录 -->
      <el-tab-pane :label="`调拨记录 (${profile?.transfers?.length ?? 0})`" name="transfers">
        <DataPanel flush>
            <el-table :data="profile?.transfers ?? []">
            <el-table-column label="原部门" width="150">
              <template #default="{ row }">{{ dash(row.fromDeptName) }}</template>
            </el-table-column>
            <el-table-column label="目标部门" width="150">
              <template #default="{ row }">{{ dash(row.toDeptName) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="调拨原因" min-width="220" show-overflow-tooltip />
            <el-table-column prop="operator" label="操作人" width="110" />
            <el-table-column label="调拨时间" width="165">
              <template #default="{ row }">{{ formatTime(row.transferTime) }}</template>
            </el-table-column>
            <template #empty>
              <EmptyState title="还没有调拨记录" desc="在设备列表里执行调拨之后，这里会留下痕迹" />
            </template>
          </el-table>
        </DataPanel>
      </el-tab-pane>

      <!-- 7. 变更审计。只看不写，覆盖的问题是"这台设备的哪个字段、
           在什么时候、被谁从什么改成了什么" -->
      <el-tab-pane
        v-if="hasPerm('sys:audit:list')"
        :label="`变更审计 (${auditLogs.length})`"
        name="audit"
      >
        <DataPanel flush>
          <el-table v-loading="!auditLoaded" :data="auditLogs" height="460">
            <el-table-column label="时间" width="166">
              <template #default="{ row }">
                <span class="num">{{ formatTime(row.auditTime) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="动作" width="96">
              <template #default="{ row }">
                <StatusPlate :tone="auditToneOf(row.action)" :dot="false">
                  {{ row.actionLabel }}
                </StatusPlate>
              </template>
            </el-table-column>

            <!-- 明细直接摊开成两列，不折叠：这里本来就只为一台设备服务，
                 条数有限，折叠反而多一次点击 -->
            <el-table-column label="变更内容" min-width="320">
              <template #default="{ row }">
                <div v-if="row.changes?.length" class="audit-changes">
                  <div v-for="c in row.changes" :key="c.field" class="audit-change">
                    <span class="ac-field">{{ c.field }}</span>
                    <span class="ac-before">{{ c.before }}</span>
                    <span class="ac-arrow">→</span>
                    <span class="ac-after">{{ c.after }}</span>
                  </div>
                </div>
                <span v-else class="muted">{{ row.remark || '无字段变更' }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作人" width="110">
              <template #default="{ row }">
                {{ row.operatorName || row.operator || '—' }}
              </template>
            </el-table-column>

            <template #empty>
              <EmptyState
                title="还没有变更记录"
                desc="这台设备建档之后，每一次编辑、调拨、借用、报废都会记在这里"
              />
            </template>
          </el-table>
        </DataPanel>
      </el-tab-pane>
    </el-tabs>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" :title="previewName" width="640px">
      <div class="preview-box">
        <img v-if="previewUrl" :src="previewUrl" alt="附件预览" class="preview-img" />
      </div>
    </el-dialog>

    <!-- ---------- 资产标签（二维码） ----------
         这块**刻意不跟随主题**：码是给机器扫的，必须浅底深码。
         详见 styles/tokens.css 里 --label-paper 的说明。 -->
    <el-dialog v-model="labelVisible" title="资产标签" width="360px">
      <div class="label-card">
        <div class="label-name">{{ device?.deviceName ?? '—' }}</div>
        <div class="label-code mono">{{ dash(device?.assetCode) }}</div>

        <div class="label-qr">
          <!-- render-as="svg" 而不是默认的 canvas：标签是要打印的，
               矢量图放大不糊。level 用 M（15% 容错）—— 贴在设备上
               难免蹭脏，L 档容错太低，H 档码太密、小标签印不下 -->
          <QrcodeVue
            :value="labelUrl"
            :size="176"
            level="M"
            render-as="svg"
            background="#ffffff"
            foreground="#000000"
          />
        </div>

        <div class="label-foot">扫码打开设备档案</div>
      </div>

      <!-- ⚠️ 本机地址必须提示出来。二维码是印给**别的设备**扫的，
           localhost / 127.0.0.1 只有这台电脑自己能访问 ——
           不提示的话用户会印一堆标签贴到设备上，才发现一台都扫不动 -->
      <el-alert
        v-if="labelUrlIsLocal"
        type="warning"
        :closable="false"
        show-icon
        class="label-warn"
        title="这个地址别的设备扫不开"
      >
        <div class="label-warn-body">
          二维码里是本机地址，只有这台电脑自己能访问。
          要打印标签的话，请到「系统设置 → 系统信息」把「对外访问地址」
          填成这台机器在局域网里的地址（例如 http://192.168.1.20:8080）或域名。
        </div>
      </el-alert>

      <!-- user-select: all 让链接点一下就整段选中，配合下面的复制按钮 ——
           内网 HTTP 环境下 clipboard API 不可用时，这是唯一的退路 -->
      <div class="label-url-row">
        <code class="label-url">{{ labelUrl }}</code>
        <el-button size="small" @click="copyLabelUrl">复制链接</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* 健康分旁边的扣分原因。跟在铭牌后面，同一行读完 */
.health-why {
  margin-left: var(--sp-3);
  font-size: 12px;
  color: var(--ink-3);
}

/* ---------- 变更审计页签 ---------- */
.audit-changes {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px 0;
}

.audit-change {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  line-height: 1.7;
}

.ac-field {
  flex: none;
  width: 76px;
  color: var(--ink-3);
}

/* 旧值加删除线：一眼看出这是被改掉的那个，不用读文字去判断哪边是新的 */
.ac-before {
  color: var(--ink-3);
  text-decoration: line-through;
  text-decoration-color: var(--line-strong);
}

.ac-arrow {
  flex: none;
  /* 用 --ink-3 而不是 --ink-4：箭头压在表格行悬停的 --hover 上时，
     --ink-4 只有 2.95:1（算过），差一点点不达 3:1 */
  color: var(--ink-3);
}

.ac-after {
  color: var(--ink-1);
  font-weight: 500;
}

.muted {
  color: var(--ink-3);
}

.head-card {
  margin-bottom: 12px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.device-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--ink-1);
}

.head-right {
  display: flex;
  gap: 16px;
}

.meta {
  font-size: 13px;
  color: var(--ink-2);
}

.scrap-alert {
  margin-top: 12px;
}

/* el-tabs 只承担标签头，各页签的内容自己是独立的 DataPanel */
.tabs :deep(.el-tabs__header) {
  margin: 0 0 var(--sp-4);
}

.sub-card {
  margin-top: 16px;
}

.attach-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.hint {
  font-size: 12px;
  color: var(--ink-3);
}

.preview-box {
  display: flex;
  justify-content: center;
}

.preview-img {
  max-width: 100%;
  max-height: 65vh;
}

/* ---------- 资产标签 ---------- */

/* 一块"白纸"：背景**亮暗两个主题下取值相同**，见 tokens.css 的 --label-paper。
   二维码必须浅底深码，暗色下反过来渲染很多扫码器认不出来。
   顺带也让它在暗色界面里像一张贴纸，视觉上是想要的效果。

   ⚠️ 里面三个墨色是**局部**定义、不是全局 token：它们只在这块白纸上成立。
   直接用主题的 --ink-* 的话，暗色主题下那些是浅色的，压在白纸上等于看不见。
   收成局部变量而不是散写在三条规则里，是为了让"这几个色属于这张标签、
   不属于主题"这件事一眼可见 */
.label-card {
  --label-ink-1: #16212a;
  --label-ink-2: #55646f;
  --label-ink-3: #62717c;

  padding: var(--sp-4) var(--sp-4) var(--sp-3);
  background: var(--label-paper);
  color: var(--label-ink-1);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  text-align: center;
}

.label-name {
  font-size: 14px;
  font-weight: 600;
}

.label-code {
  margin-top: 2px;
  font-size: 12px;
  color: var(--label-ink-2);
}

.label-qr {
  display: flex;
  justify-content: center;
  margin: var(--sp-3) 0;
}

.label-foot {
  font-size: 11px;
  color: var(--label-ink-3);
}

/* 本机地址警告。放在链接上方：先看到"扫不开"，再看那个链接 */
.label-warn {
  margin-top: var(--sp-3);
}

.label-warn-body {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.7;
}

.label-url-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.label-url {
  flex: 1;
  min-width: 0;
  padding: 5px var(--sp-2);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-2);
  background: var(--sunken);
  border-radius: var(--r-control);
  /* 点一下就整段选中，供 clipboard API 不可用时手工复制 */
  user-select: all;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
