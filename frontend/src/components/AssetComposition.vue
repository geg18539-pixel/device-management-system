<script setup lang="ts">
import { computed } from 'vue'

/** 构成条里的一档 */
export interface CompositionPart {
  name: string
  value: number
  /**
   * 色值。DOM 里可以直接写 `var(--x)` —— 和 ECharts 不同，
   * 这里过的是 CSS 引擎，读得到变量。
   */
  color: string
}

/**
 * 概览主卡：一个**大数字** + 一条构成占比条 + 一行图例。
 *
 * <p>替代原来那种 5~6 个等宽指标卡平铺的写法。平铺的问题是**主次不分**：
 * 一屏里所有数字一样大，视线没有落点；而"总数"和"其中某一档"本来就不是
 * 同一个量级的信息，摆成一样大等于放弃了排版上的表达。
 *
 * <p>看板和设备台账共用它。抽出组件而不是各写一遍，是因为占比条的
 * 分母口径（含"未分类"差额）和那几处对比度约束都容易改漏一边 ——
 * 和当初把 ECharts 生命周期收进 EChart.vue 是同一个理由。
 */
const props = withDefaults(
  defineProps<{
    /** 大数字上方的小标签，如「在册设备」 */
    label: string
    /** 总数字。各档之和由组件自己算，调用方不用操心差额 */
    total: number
    /** 计数的单位，如「台」「单」 */
    unit?: string
    /** 各档的数量和颜色。**含 0 的档也会列进图例** */
    parts: CompositionPart[]
    /**
     * 总数比各档之和大时，差额那一段的名字。
     * 典型场景是历史库升级后新列还没回填（NULL 不会出现在任何一档里）。
     */
    gapName?: string
    /** 差额那一段的颜色。默认和 chartColors.ts 里两套主题的 idle 同值 */
    gapColor?: string
    /** 整块可点。父组件用 @click 接管，这里只负责光标和悬停描边 */
    clickable?: boolean
  }>(),
  {
    unit: '台',
    gapName: '未分类',
    gapColor: 'var(--idle)',
    clickable: false,
  },
)

/**
 * 各档 + 可能的「未分类」。
 *
 * <p>分母取 total 和各档之和里的**较大值**：
 * - 只取各档之和 → 老库里该字段为 NULL 的记录会让每一档的比例都偏大
 * - 只取 total → 两边口径不一致时会算出超过 100% 的宽度，撑破卡片
 *
 * <p>差额单独占一段，比分母取和更诚实：条上能看出来"有一块没归类"，
 * 而不是把它摊到其它档里假装凑满。
 */
const segments = computed(() => {
  const parts = props.parts
  const sum = parts.reduce((acc, p) => acc + p.value, 0)
  const total = Math.max(props.total, sum)
  const gap = total - sum

  const all =
    gap > 0 ? [...parts, { name: props.gapName, value: gap, color: props.gapColor }] : parts

  return all.map((p) => ({
    name: p.name,
    value: p.value,
    color: p.color,
    pct: total > 0 ? (p.value / total) * 100 : 0,
  }))
})

/** 条上真正画出来的段。宽度为 0 的不参与布局，但图例里仍然列出来 */
const barSegments = computed(() => segments.value.filter((s) => s.pct > 0))

/** 读屏描述。纯图形元素没有文字，得补一条 */
const barAria = computed(() =>
  segments.value.map((s) => `${s.name} ${s.value} ${props.unit}`).join('、'),
)
</script>

<template>
  <div class="composition" :class="{ clickable }">
    <div class="comp-label">{{ label }}</div>

    <div class="comp-figure">
      <span class="comp-value num">{{ total }}</span>
      <span class="comp-unit">{{ unit }}</span>
    </div>

    <div
      class="comp-bar"
      :class="{ 'is-empty': !barSegments.length }"
      role="img"
      :aria-label="barSegments.length ? `构成：${barAria}` : '暂无数据'"
    >
      <span
        v-for="seg in barSegments"
        :key="seg.name"
        class="seg"
        :style="{ width: `${seg.pct}%`, background: seg.color }"
      />
    </div>

    <!-- 各档即使为 0 也列出来 ——「报废 0」本身是好消息，藏起来反而丢信息 -->
    <ul class="comp-legend">
      <li v-for="seg in segments" :key="seg.name">
        <i :style="{ background: seg.color }" />
        <span class="lg-name">{{ seg.name }}</span>
        <b class="num">{{ seg.value }}</b>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.composition {
  display: flex;
  flex-direction: column;
  padding: var(--sp-4) var(--sp-5);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
}

/* 层次靠描边加深，不用投影 —— 全站的投影只留给浮层 */
.composition.clickable {
  cursor: pointer;
}

.composition.clickable:hover {
  border-color: var(--line-strong);
}

.comp-label {
  font-size: 12px;
  color: var(--ink-3);
}

.comp-figure {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 2px;
  /* 给下面那个 auto 间距兜底：万一这张卡比旁边那一列还高，
     auto 会算成 0，那时至少还有这一段间距 */
  margin-bottom: var(--sp-4);
}

/* 全页最大的数字。负字距是让它看起来是「一个读数」而不是几个字符 */
.comp-value {
  font-size: 44px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -1.5px;
  color: var(--ink-1);
}

.comp-unit {
  font-size: 13px;
  color: var(--ink-3);
}

/* margin-top: auto 把「占比条 + 图例」这一组压到卡片底部。
   ⚠️ auto 加在**条**上而不是图例上：图例是给条做注解的，两者必须挨着。
   改成把图例单独推下去的话，中间会裂开一大段空白，
   读者得跳着看才知道图例在解释什么。 */
.comp-bar {
  display: flex;
  height: 8px;
  margin: auto 0 var(--sp-3);
  border-radius: 999px;
  overflow: hidden;
  background: var(--sunken);
}

.comp-bar .seg {
  display: block;
  height: 100%;
}

/* 段与段之间用面板底色留缝（不是白色——暗色下写死白色会出现一道刺眼亮边）。
   全局 box-sizing 是 border-box，这 2px 从段自己的宽度里出，总宽仍是 100% */
.comp-bar .seg + .seg {
  border-left: 2px solid var(--surface);
}

.comp-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px var(--sp-4);
}

.comp-legend li {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ink-2);
}

/* 色块用最小一级圆角，和铭牌（StatusPlate）的 2px 对齐 */
.comp-legend i {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: var(--r-plate);
}

/* —— 对比度：--ink-3 只按 --surface / --canvas / --hover 校验过，
   这里压在 --surface 上，亮 5.03:1 / 暗 4.74:1，达标 —— */
.comp-legend .lg-name {
  color: var(--ink-3);
}

.comp-legend b {
  font-weight: 600;
  color: var(--ink-1);
}
</style>
