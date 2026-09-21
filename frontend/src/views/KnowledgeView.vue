<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import {
  deleteKnowledge,
  knowledgeTone,
  listKnowledge,
  listKnowledgeChunks,
  reprocessKnowledge,
  searchKnowledge,
  uploadKnowledge,
  type KnowledgeDoc,
  type KnowledgeSearchResult,
} from '../api/knowledge'
import DataPanel from '../components/DataPanel.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import StatusPlate from '../components/StatusPlate.vue'
import { usePerm } from '../composables/usePerm'
import type { PlateTone } from '../utils/plateTone'

const { hasPerm } = usePerm()

const loading = ref(false)
const uploading = ref(false)
const docs = ref<KnowledgeDoc[]>([])
const keyword = ref('')

/** 已在库里的文档用哪个模型/维度。用来提醒"和当前配置不一致" */
const indexedModel = computed(() => {
  const done = docs.value.filter((d) => d.chunkCount > 0 && d.embedModel)
  return done.length ? done[0].embedModel : ''
})

async function loadDocs() {
  loading.value = true
  try {
    docs.value = await listKnowledge(keyword.value || undefined)
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

// ============================================================
// 上传
// ============================================================

/**
 * 上传前的本地校验。
 *
 * <p>和格式白名单对齐但**不替代它** —— 后端仍会再校验一遍。
 * 这里挡一道的意义是：等几十秒再说"格式不对"太浪费，
 * 而且后端那几十秒已经花了（文件落盘 + 尝试解析）。
 */
function beforeUpload(file: File): boolean {
  const name = file.name.toLowerCase()
  if (!/\.(pdf|txt|md)$/.test(name)) {
    ElMessage.error('知识库只收 pdf / txt / md 格式的文件')
    return false
  }
  return true
}

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const doc = await uploadKnowledge(options.file as File)
    ElMessage.success(`「${doc.title}」已入库，切出 ${doc.chunkCount} 段`)
    await loadDocs()
  } catch {
    // 失败原因由后端给出（可能是"嵌入模型没拉下来"），拦截器已经弹过提示。
    // 这里刷新一下列表，让用户看到那条「失败」记录和它的原因
    await loadDocs()
  } finally {
    uploading.value = false
  }
}

// ============================================================
// 行操作
// ============================================================

async function handleReprocess(row: KnowledgeDoc) {
  try {
    const doc = await reprocessKnowledge(row.id)
    ElMessage.success(`「${doc.title}」重新处理完成，切出 ${doc.chunkCount} 段`)
  } catch {
    // 同上
  } finally {
    await loadDocs()
  }
}

async function handleDelete(row: KnowledgeDoc) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.title}」吗？它在库里的 ${row.chunkCount} 个文本段会一起清掉，磁盘上的原文件也会删除。`,
      '删除文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteKnowledge(row.id)
    ElMessage.success('已删除')
    await loadDocs()
  } catch {
    // 同上
  }
}

// ---- 文本块预览 ----
const chunkVisible = ref(false)
const chunkLoading = ref(false)
const chunkTitle = ref('')
const chunkList = ref<string[]>([])

async function openChunks(row: KnowledgeDoc) {
  chunkTitle.value = row.title
  chunkList.value = []
  chunkVisible.value = true
  chunkLoading.value = true
  try {
    chunkList.value = await listKnowledgeChunks(row.id)
  } catch {
    // 同上
  } finally {
    chunkLoading.value = false
  }
}

// ============================================================
// 检索试验
// ============================================================

const searchQuery = ref('')
const searching = ref(false)
const result = ref<KnowledgeSearchResult | null>(null)

async function handleSearch() {
  const q = searchQuery.value.trim()
  if (!q) {
    ElMessage.warning('请先输入要检索的内容')
    return
  }
  searching.value = true
  try {
    result.value = await searchKnowledge(q)
  } catch {
    result.value = null
  } finally {
    searching.value = false
  }
}

/**
 * 相似度的显示分档。
 *
 * <p>⚠️ 这只是**给人看的颜色提示**，不是检索门槛 ——
 * 决定"算不算命中"的是后端的 minScore 配置。
 * 这里的档位也不该被当成"多少分算好"的通用标准：
 * 不同嵌入模型的分值分布差别很大。
 */
function scoreTone(score: number): PlateTone {
  if (score >= 0.55) return 'ok'
  if (score >= 0.45) return 'warn'
  return 'idle'
}

function formatSize(bytes?: number): string {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadDocs)
</script>

<template>
  <div class="page">
    <PageHeader
      title="设备知识库"
      desc="上传设备手册、厂商资料、维修案例；系统会解析、切分并转成向量，检索时按语义相似度找相关段落"
    />

    <!-- ---------- 文档管理 ---------- -->
    <DataPanel title="文档管理" :hint="indexedModel ? `当前向量由 ${indexedModel} 生成` : ''" flush>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="按标题或文件名搜索"
          clearable
          style="width: 240px"
          @keyup.enter="loadDocs"
          @clear="loadDocs"
        />
        <el-button @click="loadDocs">刷新</el-button>

        <el-upload
          v-if="hasPerm('sys:knowledge:add')"
          class="uploader"
          :show-file-list="false"
          :http-request="handleUpload"
          :before-upload="beforeUpload"
          accept=".pdf,.txt,.md"
        >
          <el-button type="primary" :loading="uploading">
            {{ uploading ? '正在解析并嵌入…' : '上传文档' }}
          </el-button>
        </el-upload>

        <span class="hint">支持 PDF / TXT / Markdown</span>
      </div>

      <el-table v-loading="loading" :data="docs">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="doc-title">{{ row.title }}</span>
            <!-- 失败原因直接摊在标题下面，不藏进弹窗 ——
                 这是用户最需要看到的一行 -->
            <span v-if="row.status === '失败' && row.errorMsg" class="doc-error">
              {{ row.errorMsg }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="文件" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono">{{ row.fileName || '—' }}</span>
            <span class="doc-meta">{{ formatSize(row.fileSize) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <StatusPlate :tone="knowledgeTone(row.status)">{{ row.status }}</StatusPlate>
          </template>
        </el-table-column>

        <el-table-column label="文本段" width="90" align="right">
          <template #default="{ row }">
            <b class="num">{{ row.chunkCount || 0 }}</b>
          </template>
        </el-table-column>

        <el-table-column label="向量" width="150">
          <template #default="{ row }">
            <span v-if="row.embedModel" class="doc-meta">
              {{ row.embedModel }} · {{ row.dimension }} 维
            </span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column prop="uploader" label="上传人" width="100" />

        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">
            <span class="num">{{ formatTime(row.uploadTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openChunks(row)">
              看分段
            </el-button>
            <el-button
              v-if="hasPerm('sys:knowledge:add')"
              link
              type="primary"
              size="small"
              @click="handleReprocess(row)"
            >
              重新处理
            </el-button>
            <el-button
              v-if="hasPerm('sys:knowledge:remove')"
              link
              type="danger"
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState
            title="知识库还是空的"
            desc="上传一份设备手册或维修案例试试；解析完成后就能在下面按语义检索"
          />
        </template>
      </el-table>
    </DataPanel>

    <!-- ---------- 检索试验 ---------- -->
    <DataPanel title="检索试验" hint="只返回命中的原文段落，不接模型生成" class="search-panel">
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="用一句话描述你要找的内容，比如：温度传感器 A 的额定电压是多少"
          @keydown.enter.exact.prevent="handleSearch"
        />
        <el-button type="primary" :loading="searching" class="search-btn" @click="handleSearch">
          检索
        </el-button>
      </div>

      <p v-if="result?.notice" class="notice">{{ result.notice }}</p>

      <div v-if="result && result.hits.length" class="hits">
        <div class="hits-meta">
          共 {{ result.totalIndexed }} 段可检索，命中 {{ result.hits.length }} 段
          <span v-if="result.skippedMismatch > 0" class="warn-text">
            （有 {{ result.skippedMismatch }} 段因向量模型/维度不一致被跳过）
          </span>
        </div>

        <article v-for="hit in result.hits" :key="hit.chunkId" class="hit">
          <header class="hit-head">
            <span class="hit-doc">{{ hit.docTitle }}</span>
            <span class="hit-pos">第 {{ hit.chunkIndex + 1 }} 段</span>
            <span class="hit-score num" :class="scoreTone(hit.score)">{{ hit.score.toFixed(3) }}</span>
            <span class="hit-bar" :style="{ width: `${Math.max(2, hit.score * 100)}%` }" />
          </header>
          <pre class="hit-text">{{ hit.content }}</pre>
        </article>
      </div>

      <EmptyState
        v-else-if="result"
        title="没有命中任何内容"
        desc="换个说法试试；也可以确认相关资料有没有上传到知识库"
      />
    </DataPanel>

    <!-- 文本块预览 -->
    <el-dialog v-model="chunkVisible" :title="`分段预览 · ${chunkTitle}`" width="720px">
      <div v-loading="chunkLoading" class="chunk-box">
        <div v-for="(chunk, i) in chunkList" :key="i" class="chunk">
          <div class="chunk-idx">第 {{ i + 1 }} 段 · {{ chunk.length }} 字</div>
          <pre class="chunk-text">{{ chunk }}</pre>
        </div>
        <EmptyState v-if="!chunkLoading && !chunkList.length" title="还没有分段" desc="这份文档还没处理成功" />
      </div>
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
  flex-wrap: wrap;
  gap: 10px;
  padding: var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.uploader {
  display: inline-block;
}

.hint {
  font-size: 12px;
  color: var(--ink-3);
}

/* 标题列两行：标题在上，失败原因在下 */
.doc-title {
  display: block;
  color: var(--ink-1);
}

.doc-error {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--crit);
}

.doc-meta {
  display: block;
  font-size: 11px;
  color: var(--ink-3);
}

.muted {
  color: var(--ink-3);
}

/* ---------- 检索试验 ---------- */
.search-panel {
  margin-top: var(--sp-3);
}

.search-bar {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
}

.search-btn {
  flex: none;
  margin-top: 1px;
}

.notice {
  margin-top: var(--sp-3);
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--warn);
  background: var(--warn-weak);
  border-radius: var(--r-control);
}

.hits {
  margin-top: var(--sp-3);
}

.hits-meta {
  font-size: 12px;
  color: var(--ink-3);
}

.warn-text {
  color: var(--warn);
}

.hit {
  margin-top: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  background: var(--hover);
  border: 1px solid var(--line-soft);
  border-radius: var(--r-control);
}

.hit-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: 12px;
}

.hit-doc {
  color: var(--ink-1);
  font-weight: 500;
}

.hit-pos {
  color: var(--ink-3);
}

/* 相似度靠右，和上面的文档名拉开距离 */
.hit-score {
  margin-left: auto;
  font-weight: 600;
}

.hit-score.ok {
  color: var(--ok);
}
.hit-score.warn {
  color: var(--warn);
}
.hit-score.idle {
  color: var(--ink-3);
}

/* 分数下面一条细进度条。宽度按相似度给，
   让"哪条更相关"在扫视时就能看出来 */
.hit-bar {
  display: block;
  height: 2px;
  margin-top: 6px;
  background: var(--signal-line);
  border-radius: 999px;
  max-width: 100%;
}

.hit-text {
  margin-top: var(--sp-2);
  font-family: inherit;
  font-size: 12px;
  line-height: 1.8;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
}

/* ---------- 分段预览 ---------- */
.chunk-box {
  max-height: 60vh;
  overflow-y: auto;
  text-align: left;
}

.chunk {
  padding: var(--sp-3);
  border-bottom: 1px solid var(--line-soft);
}

.chunk:last-child {
  border-bottom: none;
}

.chunk-idx {
  font-size: 11px;
  color: var(--ink-3);
}

.chunk-text {
  margin-top: 4px;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.8;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
