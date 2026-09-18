<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const menus = [
  { path: '/devices', title: '设备管理' },
  { path: '/hello', title: '联调测试' },
]

/** el-menu 的 default-active，跟随当前路由高亮 */
const activeMenu = computed(() => route.path)

/** 顶栏标题取路由的 meta.title */
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '设备管理系统')
</script>

<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">设备管理系统</div>

      <!-- router 属性让 el-menu 直接用 index 作为路径跳转 -->
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ m.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">{{ pageTitle }}</el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid var(--border);
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-h);
  border-bottom: 1px solid var(--border);
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--border);
  font-size: 16px;
  font-weight: 500;
  color: var(--text-h);
}

.main {
  background: #f5f7fa;
}
</style>
