<script setup lang="ts">
import DiagnosisPanel from '../components/DiagnosisPanel.vue'
import PageHeader from '../components/PageHeader.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="智能故障诊断"
      desc="输入故障现象，系统会检索设备手册和相似历史工单，再让模型据此给出维修建议 —— 建议的依据会一并列出来，可以自己核对"
    />

    <!--
      这一页刻意只放一个面板，不加别的装饰。
      左上方那块"检索到了什么"才是这个功能最该被看见的部分：
      AI 给的结论对不对，取决于材料对不对，先看材料。
    -->
    <DiagnosisPanel show-device-picker />
  </div>
</template>

<style scoped>
.page {
  text-align: left;
}
</style>
