<script setup lang="ts">
/**
 * 页面页头。
 *
 * <p>由**页面自己渲染**，不是外壳统一渲染。原因是各页的说明文字和主操作
 * 都不一样（"新增设备"、"导出工单"、"重新扫描"），外壳统一渲染的话
 * 就得给 19 条路由各配一份 meta，还要再想办法把按钮塞进去。
 * 让页面自己写更直接。
 *
 * <p>位置约定：放在页面内容的最上面，作为内容区的第一个元素。
 */
defineProps<{
  title: string
  /** 一句话说明这个页面在管什么，或者数据的口径（比如"数据截至 15:20"） */
  desc?: string
}>()
</script>

<template>
  <header class="page-head">
    <div class="head-text">
      <h1>{{ title }}</h1>
      <p v-if="desc" class="head-desc">{{ desc }}</p>
    </div>

    <div v-if="$slots.actions" class="head-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.page-head {
  display: flex;
  /* 底部对齐：说明文字和按钮的基线视觉上更稳 */
  align-items: flex-end;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
}

.head-text {
  min-width: 0;
}

.page-head h1 {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -.2px;
  color: var(--ink-1);
}

.head-desc {
  margin-top: 3px;
  font-size: 12px;
  color: var(--ink-3);
}

.head-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex: none;
}
</style>
