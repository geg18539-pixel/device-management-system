<script setup lang="ts">
/**
 * 面板容器。
 *
 * <p>替代原来 21 处写法一模一样的 `el-card shadow="never"`。
 *
 * <p>为什么用原生 <section> 而不是继续用 el-card：el-card 自带一整套
 * 内边距、边框、阴影的 CSS 变量，要改成我们的层级（面板 6px 圆角、
 * 1px 分隔线、不用投影）得逐条覆盖它的变量和内部结构，
 * 不如直接用 token 写干净。而且 `shadow="never"` 这个属性本身就说明
 * 原来的用法已经绕开了 el-card 的主要能力。
 *
 * <p>层次靠底色和分隔线表达，不靠投影 —— 全站的投影只留给浮层
 * （下拉、弹窗、抽屉）。
 */
withDefaults(
  defineProps<{
    title?: string
    /** 标题右侧的补充说明，比如"共 8 台，最多显示 10 条" */
    hint?: string
    /** 内容区不加内边距。表格这类自带留白的内容用 */
    flush?: boolean
  }>(),
  { title: '', hint: '', flush: false },
)
</script>

<template>
  <section class="panel">
    <header v-if="title || hint || $slots.actions" class="panel-head">
      <h2 v-if="title" class="panel-title">{{ title }}</h2>
      <span v-if="hint" class="panel-hint">{{ hint }}</span>
      <div v-if="$slots.actions" class="panel-actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="panel-body" :class="{ 'is-flush': flush }">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.panel {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  /* 裁掉子元素的方角，否则 flush 模式下的表格会戳出面板的圆角。
     不影响表格自己的横向滚动和固定列 */
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--line-soft);
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

.panel-hint {
  font-size: 12px;
  color: var(--ink-3);
  min-width: 0;
}

/* 操作按钮推到最右。用 margin-left: auto 而不是给标题加 flex: 1，
   这样没有标题、只有按钮时位置也是对的 */
.panel-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.panel-body {
  padding: var(--sp-4);
}

.panel-body.is-flush {
  padding: 0;
}
</style>
