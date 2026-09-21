<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import { DEVICE_LIFECYCLE, exportLedger, getDeviceLedger, type DeviceLedger } from '../api/device'
import AssetComposition from '../components/AssetComposition.vue'
import DataPanel from '../components/DataPanel.vue'
import EChart from '../components/EChart.vue'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import { chartPalette } from '../utils/chartColors'
import { useAppStore } from '../stores/app'

const appStore = useAppStore()
/** 当前主题下的图表配色。切主题时 computed 重算，图表跟着重绘 */
const palette = computed(() => chartPalette(appStore.isDark))

const loading = ref(false)
const ledger = ref<DeviceLedger | null>(null)
const exporting = ref(false)

async function loadLedger() {
  loading.value = true
  try {
    ledger.value = await getDeviceLedger()
  } catch {
    // 错误提示已由 request.ts 的拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    // 导的是完整台账（明细 + 按部门 + 按分类三个 sheet），不带筛选，
    // 因为台账的用途就是"给财务/运维一份完整的东西"
    await exportLedger({})
    ElMessage.success('台账已导出')
  } catch {
    // 同上
  } finally {
    exporting.value = false
  }
}

onMounted(loadLedger)

/** 按部门的设备数量柱状图，颜色按"正常占比"给，一眼能看出哪个部门问题设备多 */
const deptOption = computed<EChartsOption>(() => {
  const items = ledger.value?.deptItems ?? []
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, data: ['正常', '维修', '报废', '停用'] },
    grid: { left: 44, right: 20, top: 24, bottom: 60 },
    xAxis: {
      type: 'category',
      data: items.map((i) => i.name),
      axisLabel: { interval: 0, rotate: items.length > 4 ? 30 : 0 },
      axisTick: { alignWithLabel: true },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '正常', type: 'bar', stack: 'total', data: items.map((i) => i.normal), itemStyle: { color: palette.value.ok } },
      { name: '维修', type: 'bar', stack: 'total', data: items.map((i) => i.repairing), itemStyle: { color: palette.value.warn } },
      { name: '报废', type: 'bar', stack: 'total', data: items.map((i) => i.scrapped), itemStyle: { color: palette.value.crit } },
      { name: '停用', type: 'bar', stack: 'total', data: items.map((i) => i.disabled), itemStyle: { color: palette.value.idle } },
    ],
  }
})

const hasDeptData = computed(() => (ledger.value?.deptItems?.length ?? 0) > 0)

/**
 * 主卡的四档构成。
 *
 * <p>名字统一用 DEVICE_LIFECYCLE 常量，不手写中文字面量 ——
 * 这四档在「设备管理」「设备档案」里也是同一套值，
 * 手写一旦写错（比如把「维修」写成「维修中」）会出现同一台设备
 * 在不同页面上归到不同档、数量还对不上的情况。
 *
 * <p>颜色取自本页已经有的图表色板，和下面那张堆叠柱状图同一套值。
 */
const ledgerParts = computed(() => {
  const l = ledger.value
  const p = palette.value
  return [
    { name: DEVICE_LIFECYCLE.NORMAL, value: l?.normal ?? 0, color: p.ok },
    { name: DEVICE_LIFECYCLE.REPAIR, value: l?.repairing ?? 0, color: p.warn },
    { name: DEVICE_LIFECYCLE.SCRAPPED, value: l?.scrapped ?? 0, color: p.crit },
    { name: DEVICE_LIFECYCLE.DISABLED, value: l?.disabled ?? 0, color: p.idle },
  ]
})
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader
      title="设备台账"
      desc="按部门和分类两个维度汇总；导出的 Excel 含「明细 + 按部门统计 + 按分类统计」三个工作表"
    >
      <template #actions>
        <el-button :loading="exporting" @click="handleExport">导出台账</el-button>
      </template>
    </PageHeader>

    <!-- 顶部：设备总数 + 四档构成。
         原来这里是 5 个等宽卡片平铺（总数/正常/维修/报废/停用），
         "总数"和"其中某一档"本来不是一个量级的信息，摆成一样大
         等于放弃了排版上的表达，看的人得自己找哪个是总的 -->
    <AssetComposition
      class="overview"
      label="在册设备"
      unit="台"
      :total="ledger?.total ?? 0"
      :parts="ledgerParts"
    />

    <!-- 图表 -->
    <DataPanel title="各部门设备状态构成">
      <EChart v-if="hasDeptData" :option="deptOption" height="300px" />
      <EmptyState v-else title="还没有设备" desc="设备建档并指定部门之后，这里会按部门统计" />
    </DataPanel>

    <!-- 两个汇总表 -->
    <div class="tables">
      <DataPanel title="按部门统计" flush>
        <el-table :data="ledger?.deptItems ?? []">
          <el-table-column prop="name" label="部门" min-width="130" show-overflow-tooltip />
          <el-table-column prop="total" label="合计" width="80" />
          <el-table-column prop="normal" label="正常" width="70" />
          <el-table-column prop="repairing" label="维修" width="70" />
          <el-table-column prop="scrapped" label="报废" width="70" />
          <el-table-column prop="disabled" label="停用" width="70" />
          <template #empty>
            <EmptyState title="暂无数据" desc="还没有可汇总的设备" />
          </template>
        </el-table>
      </DataPanel>

      <DataPanel title="按分类统计" flush>
        <el-table :data="ledger?.categoryItems ?? []">
          <el-table-column prop="name" label="分类" min-width="130" show-overflow-tooltip />
          <el-table-column prop="total" label="合计" width="80" />
          <el-table-column prop="normal" label="正常" width="70" />
          <el-table-column prop="repairing" label="维修" width="70" />
          <el-table-column prop="scrapped" label="报废" width="70" />
          <el-table-column prop="disabled" label="停用" width="70" />
          <template #empty>
            <EmptyState title="暂无数据" desc="还没有可汇总的设备" />
          </template>
        </el-table>
      </DataPanel>
    </div>
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}

/* 主卡和下面图表面板之间的间距。卡片本身的样式在
   components/AssetComposition.vue 里（看板也用同一个组件） */
.overview {
  display: block;
  margin-bottom: var(--sp-3);
}

/* 图表面板和下面的汇总表之间的间距 */
.tables {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

@media (max-width: 1200px) {
  .tables {
    grid-template-columns: 1fr;
  }
}
</style>
