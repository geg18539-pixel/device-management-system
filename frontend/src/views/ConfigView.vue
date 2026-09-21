<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getAiModels, getAiStatus, type AiStatus } from '../api/ai'
import {
  VALUE_TYPE,
  createConfig,
  deleteConfig,
  getConfigs,
  updateConfig,
  type SysConfig,
} from '../api/config'
import { usePerm } from '../composables/usePerm'
import DataPanel from '../components/DataPanel.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'

const { hasPerm } = usePerm()

const loading = ref(false)
const configs = ref<SysConfig[]>([])

/** 编辑中的值。按 key 索引，避免直接改 configs 里的对象（取消时不好还原） */
const edits = reactive<Record<string, string>>({})

/** 保存中的 key，用于按钮 loading */
const savingKey = ref<string | null>(null)

// ============================================================
// AI 模型的特殊处理
//
// 这几个键用普通文本框很难用对：
//   - provider 是闭集，手打错一个字（opnai）会**静默退回默认提供方**，
//     界面上还显示着你打的那个错值 —— 所以做成下拉；
//   - base-url 允许留空表示"跟随配置文件/环境变量"，所以要把它**实际生效**
//     的值显示出来，否则管理员看着空框不知道到底在连哪儿。
// ============================================================

const AI_PROVIDER_KEYS = ['ai.chat.provider', 'ai.embedding.provider']
// base-url 和 model 都允许留空表示"跟随配置文件/环境变量"，都要显示生效值
const AI_FALLBACK_KEYS = [
  'ai.chat.base-url', 'ai.chat.model',
  'ai.embedding.base-url', 'ai.embedding.model',
]

const aiStatus = ref<AiStatus | null>(null)
const testingPurpose = ref<'chat' | 'embedding' | null>(null)
const testResult = reactive<Record<string, string>>({})

async function loadAiStatus() {
  try {
    aiStatus.value = await getAiStatus()
  } catch {
    // 拿不到就不显示状态面板，不影响下面的参数表
    aiStatus.value = null
  }
}

/**
 * 这一项留空时**实际生效**的是什么，用作输入框的占位提示。
 *
 * <p>库里的值为空 = 跟随配置文件/环境变量。不把生效值显示出来的话，
 * 管理员看着一个空框不知道系统到底在连哪儿、用哪个模型。
 */
function effectiveValue(configKey: string): string {
  if (!aiStatus.value) return '留空表示用配置文件里的值'
  const chat = configKey.startsWith('ai.chat.')
  const status = chat ? aiStatus.value.chat : aiStatus.value.embedding
  return configKey.endsWith('.base-url') ? status.baseUrl : status.model
}

/** 提供方留空时，下拉框的占位文案要写清"当前跟随的是谁" */
function providerPlaceholder(configKey: string): string {
  if (!aiStatus.value) return '跟随配置文件'
  const status = configKey.startsWith('ai.chat.')
    ? aiStatus.value.chat
    : aiStatus.value.embedding
  return `跟随配置文件（当前：${status.providerLabel}）`
}

async function testConnection(purpose: 'chat' | 'embedding') {
  testingPurpose.value = purpose
  testResult[purpose] = ''
  try {
    const data = await getAiModels(purpose)
    testResult[purpose] = data.models.length
      ? `连接正常 · 可用模型 ${data.models.length} 个：${data.models.slice(0, 6).join('、')}`
        + (data.models.length > 6 ? ' …' : '')
      : '连接正常，但这个服务没有返回任何模型'
  } catch {
    // 失败原因由拦截器弹出。后端已经写明了是哪一家、哪个地址、要不要配密钥
    testResult[purpose] = ''
  } finally {
    testingPurpose.value = null
  }
}

async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await getConfigs()
    // 载入时把当前值填进编辑区
    for (const c of configs.value) {
      edits[c.configKey] = c.configValue ?? ''
    }
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 按分组归拢。
 *
 * <p>用 LinkedHashMap 的思路（普通对象按插入顺序）保持后端给的顺序 ——
 * 后端已经按 configGroup + sortOrder 排好了，前端不该重新排序。
 */
const groups = computed(() => {
  const map: Record<string, SysConfig[]> = {}
  for (const c of configs.value) {
    const group = c.configGroup || '未分组'
    ;(map[group] ??= []).push(c)
  }
  return Object.entries(map).map(([name, items]) => ({ name, items }))
})

/** 某个参数的值有没有被改过 —— 只给改过的项亮保存按钮 */
function isDirty(c: SysConfig): boolean {
  return (edits[c.configKey] ?? '') !== (c.configValue ?? '')
}

async function save(c: SysConfig) {
  savingKey.value = c.configKey
  try {
    await updateConfig(c.configKey, edits[c.configKey] ?? '')
    ElMessage.success(`「${c.configName}」已保存，即时生效`)
    await loadConfigs()
  } catch {
    // 类型不合法等错误，后端返回 400，提示由拦截器弹出
  } finally {
    savingKey.value = null
  }
}

/** 恢复成数据库里的当前值（放弃本次编辑） */
function revert(c: SysConfig) {
  edits[c.configKey] = c.configValue ?? ''
}

// ---------------- 新增自定义参数 ----------------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

function emptyForm() {
  return {
    configKey: '',
    configValue: '',
    configName: '',
    configGroup: '自定义',
    valueType: VALUE_TYPE.STRING,
    remark: '',
  }
}

const form = reactive(emptyForm())

const rules: FormRules<typeof form> = {
  configKey: [
    { required: true, message: '请输入参数键', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9._-]*$/, message: '只能用小写字母、数字、点和横线', trigger: 'blur' },
  ],
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
}

async function openCreate() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  await nextTickClear()
}

async function nextTickClear() {
  await Promise.resolve()
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createConfig({ ...form })
    ElMessage.success('新增成功')
    dialogVisible.value = false
    await loadConfigs()
  } catch {
    // 键重复等业务错误
  } finally {
    submitting.value = false
  }
}

async function handleDelete(c: SysConfig) {
  try {
    await ElMessageBox.confirm(`确定要删除参数「${c.configName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteConfig(c.id)
    ElMessage.success('删除成功')
    await loadConfigs()
  } catch {
    // 内置参数不允许删，后端返回 400
  }
}

onMounted(() => {
  void loadConfigs()
  void loadAiStatus()
})
</script>

<template>
  <div class="page">
    <PageHeader title="系统设置" :desc="`共 ${configs.length} 项参数，按分组归类`">
      <template #actions>
        <el-button v-if="hasPerm('sys:config:edit')" type="primary" @click="openCreate">
          新增参数
        </el-button>
      </template>
    </PageHeader>

    <el-alert type="info" :closable="false" show-icon class="tip">
      <template #title>参数保存后<strong>立即生效</strong>，不需要重启服务</template>
      <div class="tip-body">
        库里的值是对 application.yml 的<strong>覆盖</strong>：把某一项的值清空保存，
        就会回退到 yml 里的默认值。内置参数不允许删除，只能改值。
      </div>
    </el-alert>

    <!-- AI 服务状态。**显示的是实际生效的配置**，不是上面表格里那几个字段的字面值 ——
         provider / base-url / model 都可能"库里没配、跟着配置文件或环境变量走"，
         管理员真正需要知道的是最终在连哪一家、哪个地址 -->
    <DataPanel v-if="aiStatus" title="AI 服务状态" class="group-card">
      <div class="ai-row">
        <span class="ai-purpose">对话模型</span>
        <StatusPlate tone="info" :dot="false">{{ aiStatus.chat.providerLabel }}</StatusPlate>
        <span class="ai-meta mono">{{ aiStatus.chat.baseUrl }}</span>
        <span class="ai-meta">{{ aiStatus.chat.model }}</span>
        <StatusPlate :tone="aiStatus.chat.apiKeyConfigured ? 'ok' : 'idle'" :dot="false">
          {{ aiStatus.chat.apiKeyConfigured ? '已配置密钥' : '未配置密钥' }}
        </StatusPlate>
        <el-button
          link
          type="primary"
          size="small"
          :loading="testingPurpose === 'chat'"
          @click="testConnection('chat')"
        >
          测试连接
        </el-button>
      </div>
      <p v-if="testResult.chat" class="ai-result">{{ testResult.chat }}</p>

      <div class="ai-row">
        <span class="ai-purpose">嵌入模型</span>
        <StatusPlate tone="info" :dot="false">{{ aiStatus.embedding.providerLabel }}</StatusPlate>
        <span class="ai-meta mono">{{ aiStatus.embedding.baseUrl }}</span>
        <span class="ai-meta">{{ aiStatus.embedding.model }}</span>
        <StatusPlate :tone="aiStatus.embedding.apiKeyConfigured ? 'ok' : 'idle'" :dot="false">
          {{ aiStatus.embedding.apiKeyConfigured ? '已配置密钥' : '未配置密钥' }}
        </StatusPlate>
        <el-button
          link
          type="primary"
          size="small"
          :loading="testingPurpose === 'embedding'"
          @click="testConnection('embedding')"
        >
          测试连接
        </el-button>
      </div>
      <p v-if="testResult.embedding" class="ai-result">{{ testResult.embedding }}</p>

      <p class="ai-note">
        API Key 只能通过环境变量或 application.yml 配置，<strong>不进数据库、不会在界面上回显</strong>。
        本机 Ollama 不需要密钥；用云端服务时设置
        <code>AI_CHAT_API_KEY</code>（嵌入没单独配则复用它）。
        改动上面的提供方或地址后，点「测试连接」确认能通再离开本页。
      </p>
    </DataPanel>

    <DataPanel v-for="group in groups" :key="group.name" :title="group.name" class="group-card" flush>
      <el-table :data="group.items">
        <el-table-column prop="configName" label="参数名称" width="180">
          <template #default="{ row }">
            <span class="name">{{ row.configName }}</span>
            <StatusPlate v-if="row.builtIn" tone="idle" :dot="false" class="builtin">
              内置
            </StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="参数值" width="260">
          <template #default="{ row }">
            <!-- 提供方做成下拉：手打错一个字会**静默退回默认提供方**，
                 而界面上还显示着你打的那个错值 —— 这种错最难查 -->
            <el-select
              v-if="AI_PROVIDER_KEYS.includes(row.configKey) && aiStatus"
              v-model="edits[row.configKey]"
              :placeholder="providerPlaceholder(row.configKey)"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="p in aiStatus.providers"
                :key="p.value"
                :label="p.label"
                :value="p.value"
              />
            </el-select>
            <el-switch
              v-else-if="row.valueType === 'BOOLEAN'"
              :model-value="edits[row.configKey] === 'true'"
              @update:model-value="(v: string | number | boolean) => (edits[row.configKey] = String(v))"
            />
            <el-input-number
              v-else-if="row.valueType === 'NUMBER'"
              :model-value="Number(edits[row.configKey])"
              :controls="false"
              style="width: 100%"
              @update:model-value="(v: number | undefined) => (edits[row.configKey] = String(v ?? ''))"
            />
            <!-- 服务地址留空 = 跟随配置文件/环境变量。把**实际生效**的值放在
                 占位提示里，否则管理员看着空框不知道到底在连哪儿 -->
            <el-input
              v-else
              v-model="edits[row.configKey]"
              :placeholder="AI_FALLBACK_KEYS.includes(row.configKey)
                ? `留空则用：${effectiveValue(row.configKey)}`
                : ''"
            />
          </template>
        </el-table-column>

        <el-table-column prop="configKey" label="参数键" width="220" show-overflow-tooltip />

        <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />

        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('sys:config:edit')"
              link
              type="primary"
              :disabled="!isDirty(row)"
              :loading="savingKey === row.configKey"
              @click="save(row)"
            >
              保存
            </el-button>
            <el-button
              v-if="isDirty(row)"
              link
              type="info"
              @click="revert(row)"
            >
              撤销
            </el-button>
            <el-button v-if="(!row.builtIn) && hasPerm('sys:config:edit')"
              link
              type="danger"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataPanel>

    <!-- 新增 -->
    <el-dialog v-model="dialogVisible" title="新增参数" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="参数键" prop="configKey">
          <el-input v-model="form.configKey" placeholder="如 custom.page-size" maxlength="100" />
        </el-form-item>
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" placeholder="给人看的名字" maxlength="100" />
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="form.configValue" maxlength="500" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.configGroup" maxlength="50" />
        </el-form-item>
        <el-form-item label="值类型">
          <el-radio-group v-model="form.valueType">
            <el-radio :value="VALUE_TYPE.STRING">文本</el-radio>
            <el-radio :value="VALUE_TYPE.NUMBER">整数</el-radio>
            <el-radio :value="VALUE_TYPE.BOOLEAN">开关</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
      </el-form>
      <p class="tip-text">
        新增的参数只是"存起来"，代码里读不读它由开发决定 ——
        真正会生效的是那些已经在代码里注册过的键。
      </p>
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

.tip {
  margin-bottom: 12px;
}

.tip-body {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
}

/* 分组标题和"共 N 项"都移到 PageHeader 和 DataPanel 的 title 里了 */

.group-card {
  margin-bottom: 12px;
}

.name {
  color: var(--ink-1);
}

.builtin {
  margin-left: 6px;
}

.tip-text {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* ---------- AI 服务状态 ---------- */
.ai-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-3);
  padding: 6px 0;
}

.ai-purpose {
  flex: none;
  width: 72px;
  font-size: 12px;
  color: var(--ink-3);
}

/* 地址用等宽：「l/1」「O/0」在配置里认错一个字符就要查半天 */
.ai-meta {
  font-size: 12px;
  color: var(--ink-2);
}

.ai-result {
  margin: 2px 0 6px 84px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ok);
}

.ai-note {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  line-height: 1.8;
  color: var(--ink-3);
}

.ai-note code {
  padding: 1px 5px;
}
</style>
