<script setup lang="ts">
import type { PlateTone } from '../utils/plateTone'

/**
 * 状态铭牌。
 *
 * <p>替代各页面手写的 `el-tag :type="..."`。表达方式是
 * **一个指示灯 + 一小块淡底**，不是整块高饱和填色 ——
 * 一屏几十个状态标签并排时，这个差别非常明显。
 *
 * <p>列表里的状态比详情页更需要"扫一眼就能分类"，
 * 所以默认带指示灯圆点。
 */
withDefaults(
  defineProps<{
    tone?: PlateTone
    /** 左侧的小圆点。详情页里单个状态不需要时关掉 */
    dot?: boolean
  }>(),
  { tone: 'idle', dot: true },
)
</script>

<template>
  <span class="plate" :class="tone">
    <span v-if="dot" class="led" />
    <slot />
  </span>
</template>

<style scoped>
.plate {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 22px;
  padding: 0 8px;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  /* 铭牌用最小一级圆角（2px），和面板（6px）、控件（4px）区分开 */
  border-radius: var(--r-plate);
  color: var(--ink-2);
  background: var(--sunken);
  border: 1px solid transparent;
}

/* 指示灯。6px 的圆点在 12px 文字旁边刚好，再大就抢视线了 */
.led {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.plate.ok {
  color: var(--ok);
  background: var(--ok-weak);
}

.plate.warn {
  color: var(--warn);
  background: var(--warn-weak);
}

.plate.crit {
  color: var(--crit);
  background: var(--crit-weak);
}

.plate.info {
  color: var(--signal);
  background: var(--signal-weak);
}

/* idle 保持默认的灰底灰字，不用单独写 */
</style>
