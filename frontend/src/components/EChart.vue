<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption, EChartsType } from 'echarts'

/**
 * 一个只会「把 option 画出来」的 ECharts 容器。
 *
 * <p>抽出来的原因：ECharts 的实例生命周期（init / setOption / resize / dispose）
 * 每一步都有容易忘的坑，散在各个页面里就要重复写、也容易漏。
 * 收在这里之后，页面只需要关心"数据长什么样、配成什么图"。
 *
 * <p>三个必须做对的点（都是踩过的）：
 * <ol>
 *   <li>容器必须有**确定的宽高**：ECharts 靠容器尺寸决定画布大小，
 *       高度为 0 或 auto 时 init 得到 0×0，图什么都不显示。所以这里
 *       默认给 260px 高度，也允许调用方覆盖。</li>
 *   <li>窗口缩放 / 侧边栏折叠会改变容器宽度，而 canvas 不会跟着 CSS 变，
 *       必须手动 resize。</li>
 *   <li>卸载必须 dispose：否则 canvas、事件监听和内部定时器都还挂着，
 *       反复进出页面会持续泄漏内存。</li>
 * </ol>
 */
const props = withDefaults(
  defineProps<{
    option: EChartsOption
    /** 容器高度，任何 CSS 长度值。必须给，不能是 auto */
    height?: string
  }>(),
  { height: '260px' },
)

/**
 * 图形被点击时，把**被点中的数据项的 id** 透出去。
 *
 * <p>刻意只透一个 id、不透整个 ECharts 的 params：params 是个跨所有图表类型的
 * 大联合类型，页面拿到它还得自己判断"这是饼图的扇区还是关系图的节点"。
 * 一个 id 足够让页面把它映射回自己的数据，语义的解釋留在页面那边 ——
 * 这个组件坚持"只管把 option 画出来"。
 *
 * <p>数据项没有 {@code id} 时（饼图、柱状图通常只写 name）会是 undefined，
 * 页面自己决定这种情况要不要处理。
 */
const emit = defineEmits<{ 'item-click': [id: string | undefined] }>()

const el = ref<HTMLDivElement>()

// 用普通变量而不是 ref：ECharts 实例不需要触发视图更新，
// 放进 ref 会让 Vue 深度代理整个实例，既浪费又可能出怪问题
let chart: EChartsType | null = null

function render() {
  if (!chart) return
  // 第二个参数 notMerge=true：数据整体替换时不要和旧配置合并，
  // 否则类目变少时旧的数据项会残留在图上
  chart.setOption(props.option, true)
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  if (el.value) {
    chart = echarts.init(el.value)
    // 参数声明成 unknown 再自己收窄：ECharts 的 params 是个跨所有图表类型的
    // 大联合，直接标注具体类型会因为函数参数逆变而编译不过
    chart.on('click', (params: unknown) => {
      const p = params as { data?: { id?: string } }
      emit('item-click', p.data?.id)
    })
  }
  render()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

// deep 是必要的：父组件经常就地修改数组/对象的属性
watch(() => props.option, render, { deep: true })
</script>

<template>
  <div ref="el" class="echart" :style="{ height: props.height }" />
</template>

<style scoped>
.echart {
  width: 100%;
}
</style>
