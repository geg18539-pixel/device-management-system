<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

/** 后端 /api/hello 返回的结构 */
interface HelloResponse {
  code: number
  message: string
  data: string
}

const loading = ref(true)
const errorMsg = ref('')
const result = ref<HelloResponse | null>(null)

// 格式化成带缩进的 JSON，方便直接看完整结构
const prettyJson = computed(() =>
  result.value ? JSON.stringify(result.value, null, 2) : '',
)

onMounted(async () => {
  try {
    const response = await fetch('/api/hello')
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} ${response.statusText}`)
    }
    result.value = (await response.json()) as HelloResponse
  } catch (err) {
    errorMsg.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="hello-page">
    <h2>前后端联调测试</h2>
    <p class="subtitle">
      用原生 <code>fetch('/api/hello')</code> 请求，不经过 Axios 封装 ——
      用来单独验证 Vite 代理和后端是否连通。
    </p>

    <p v-if="loading" class="status">请求中…</p>

    <div v-else-if="errorMsg" class="status status--error">
      <p>请求失败：{{ errorMsg }}</p>
      <p class="hint">
        请确认后端已启动。在 <code>backend\backend</code> 目录执行
        <code>mvnw spring-boot:run</code>，并确认它监听在 8080 端口。
      </p>
    </div>

    <section v-else-if="result">
      <dl class="fields">
        <dt>code</dt>
        <dd>{{ result?.code }}</dd>
        <dt>message</dt>
        <dd>{{ result?.message }}</dd>
        <dt>data</dt>
        <dd>{{ result?.data }}</dd>
      </dl>

      <h3>完整响应</h3>
      <pre class="raw">{{ prettyJson }}</pre>
    </section>
  </div>
</template>

<style scoped>
.hello-page {
  text-align: left;
}

.subtitle {
  margin-bottom: 24px;
  font-size: 15px;
  color: var(--text, #6b6375);
}

.status {
  padding: 16px 20px;
  border: 1px solid var(--border, #e5e4e7);
  border-radius: 8px;
  background: var(--code-bg, #f4f3ec);
}

.status--error {
  border-color: var(--accent-border, rgba(170, 59, 255, 0.5));
  background: var(--accent-bg, rgba(170, 59, 255, 0.1));
}

.hint {
  margin-top: 12px;
  font-size: 15px;
  line-height: 1.6;
}

.fields {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 14px 20px;
  margin: 0 0 28px;
  padding: 20px 24px;
  border: 1px solid var(--border, #e5e4e7);
  border-radius: 8px;
  background: var(--code-bg, #f4f3ec);
}

.fields dt {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 15px;
  color: var(--text, #6b6375);
}

.fields dd {
  margin: 0;
  word-break: break-word;
}

h3 {
  margin: 0 0 14px;
  font-size: 18px;
}

.raw {
  margin: 0;
  padding: 20px 24px;
  border: 1px solid var(--border, #e5e4e7);
  border-radius: 8px;
  background: var(--code-bg, #f4f3ec);
  font-family: ui-monospace, Consolas, monospace;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre;
  overflow-x: auto;
}
</style>
