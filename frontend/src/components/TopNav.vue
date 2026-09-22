<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { visibleGroups, type MenuGroup, type MenuItem } from '../config/menu'
import { useUserStore } from '../stores/user'

/**
 * 员工工作台的**横向顶部导航**。
 *
 * <h3>一级是"分组"，不是"页面"</h3>
 *
 * <p>这五个（概览 / 资产 / 运维 / 协同 / AI 能力）本来就是给侧边栏分组用的，
 * 数量少、语义清晰，正好当一级导航 —— 分组本身就是信息：找"工单"会先想到"运维"。
 *
 * <h3>⚠️ 为什么不用 el-dropdown 做二级</h3>
 *
 * <p>{@code el-dropdown} 的面板是**teleport 到 body** 的，样式得靠
 * {@code popper-class} 绕一层，而且面板和触发器之间是"看着连着、DOM 上隔开"的 ——
 * 那种结构下鼠标从触发器移向面板时会穿过一段不属于任何一方的区域，
 * 悬停会提前断掉。
 *
 * <p>这里自己实现：**面板放在悬停区域内侧**（同一个 div 的子元素），
 * 所以只要鼠标还在组里（包括面板上），{@code mouseleave} 就不会触发。
 * 代价是要自己写定位，但只是个 {@code position: absolute; top: 100%}。
 *
 * <h3>触屏的退路</h3>
 *
 * <p>触屏没有"悬停"，所以组标题本身是个按钮，**点它就进这组的第一个页面**。
 * 这不是完美的移动端方案（理想是抽屉），但至少不会出现"点不动"。
 */
const route = useRoute()
const userStore = useUserStore()

const groups = computed(() => visibleGroups('workbench', userStore.roles))

/** 当前展开的组。鼠标进来设上、离开清掉 */
const openGroup = ref<string | null>(null)

/** 路径是否处于激活态。子路由（如设备详情 /devices/1042）也算 */
function isActive(path?: string): boolean {
  if (!path) return false
  return route.path === path || route.path.startsWith(`${path}/`)
}

/** 这一组里有没有正在看的页面。有就把组标题也高亮 */
function isGroupActive(group: MenuGroup): boolean {
  return group.items.some((item) => isActive(item.path))
}

/**
 * 只有一个页面的组 —— 直接当链接用，不做下拉。
 * 为一个项目弹一个只有一行的面板，不如省掉那一步。
 */
function soloItem(group: MenuGroup): MenuItem | null {
  return group.items.length === 1 ? group.items[0] : null
}
</script>

<template>
  <nav class="top-nav" aria-label="主导航">
    <template v-for="group in groups" :key="group.title">
      <!-- 单页面的组：直接是链接 -->
      <RouterLink
        v-if="soloItem(group)?.path"
        class="nav-item"
        :class="{ 'is-active': isGroupActive(group) }"
        :to="soloItem(group)!.path!"
      >
        {{ group.title }}
      </RouterLink>

      <!-- 多页面的组：悬停下拉 -->
      <div
        v-else
        class="nav-group"
        :class="{ 'is-active': isGroupActive(group), 'is-open': openGroup === group.title }"
        @mouseenter="openGroup = group.title"
        @mouseleave="openGroup = null"
      >
        <RouterLink class="nav-item" :to="group.items[0].path!">
          {{ group.title }}
          <svg class="nav-caret" viewBox="0 0 24 24" width="12" height="12" fill="none"
               stroke="currentColor" stroke-width="2"
               stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M6.5 9.5l5.5 5.5 5.5-5.5" />
          </svg>
        </RouterLink>

        <!-- 面板在这个 div 里面（不是 teleport 出去），
             所以鼠标从标题移到面板上时不会触发 mouseleave -->
        <div v-show="openGroup === group.title" class="nav-panel">
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            class="nav-panel-item"
            :class="{ 'is-active': isActive(item.path) }"
            :to="item.path!"
          >
            {{ item.title }}
          </RouterLink>
        </div>
      </div>
    </template>
  </nav>
</template>

<style scoped>
.top-nav {
  display: flex;
  align-items: stretch;
  gap: 2px;
  height: 100%;
  min-width: 0;
}

/* 组和单项共用同一个外观 —— 在用户眼里它们都是"一级导航项"，
   只是因为下面有没有内容而行为不同 */
.nav-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 100%;
  padding: 0 12px;
  font-size: 13px;
  color: var(--ink-2);
  text-decoration: none;
  white-space: nowrap;
  /* 底部那道 2px 是激活指示。未激活时也留出空间（transparent），
     否则激活的一瞬间文字会往上跳一下 */
  border-bottom: 2px solid transparent;
}

.nav-item:hover {
  color: var(--ink-1);
  background: var(--hover);
}

.nav-item.is-active {
  color: var(--ink-1);
  font-weight: 600;
}

/* 激活/展开时把底部那道线点亮。用 --signal 而不是别的：
   它压在 --surface 上是 5.31:1，本来就用在主按钮和选中态上 */
.nav-item.is-active,
.nav-group.is-open .nav-item {
  border-bottom-color: var(--signal);
}

.nav-caret {
  opacity: .7;
}

.nav-group {
  position: relative;
  display: flex;
  align-items: stretch;
}

/* 面板。top:100% 正好贴着导航条底边 —— 中间不留缝，
   鼠标从标题往下移不会"掉出去" */
.nav-panel {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 20;
  min-width: 150px;
  padding: 5px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  /* 全站的投影只留给浮层（下拉、弹窗、抽屉），这是其中之一 */
  box-shadow: var(--shadow-pop);
}

.nav-panel-item {
  display: block;
  padding: 7px 10px;
  font-size: 13px;
  color: var(--ink-2);
  text-decoration: none;
  border-radius: var(--r-control);
  white-space: nowrap;
}

.nav-panel-item:hover {
  color: var(--ink-1);
  background: var(--hover);
}

/* 当前所在的那一项：底色 + 左侧一道主色细条。
   只靠底色的话，在浅色面板里不够显眼 */
.nav-panel-item.is-active {
  color: var(--ink-1);
  font-weight: 600;
  background: var(--hover);
  box-shadow: inset 2px 0 0 var(--signal);
}
</style>
