<script setup lang="ts">
/**
 * 空状态。
 *
 * <p>替代 `<el-empty>` 的默认插图 —— 那个灰色的插画在四个不同语境的页面里
 * 长得一模一样，而且只说"暂无数据"，不告诉人下一步能做什么。
 *
 * <p>空状态的职责是**指路**：说清楚为什么空、以及可以做什么。
 * 所以 desc 该写"换个筛选条件试试"或"点右上角新增一台"，
 * 而不是"空空如也"这类抒情句。
 */
withDefaults(
  defineProps<{
    title?: string
    desc?: string
  }>(),
  { title: '暂无数据', desc: '' },
)
</script>

<template>
  <div class="empty">
    <span class="empty-mark">
      <svg
        viewBox="0 0 24 24"
        width="20"
        height="20"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <use href="#i-inbox" />
      </svg>
    </span>

    <b class="empty-title">{{ title }}</b>
    <p v-if="desc" class="empty-desc">{{ desc }}</p>

    <!-- 有操作可给时（比如"新增一台"按钮）从这里进来 -->
    <div v-if="$slots.default" class="empty-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  padding: 48px var(--sp-5);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  text-align: center;
}

/* 用一条虚线的方框代替插画。它比插画轻，而且是"这里本该有东西"的
   通用符号，不用为每个语境画一套 */
.empty-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  color: var(--ink-4);
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-panel);
}

.empty-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-2);
}

/* 限制行宽：空状态的说明文字常常被拉成很长的一条 */
.empty-desc {
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
  max-width: 36ch;
}

.empty-actions {
  margin-top: var(--sp-2);
}
</style>
