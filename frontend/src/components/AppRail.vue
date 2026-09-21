<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { visibleGroups, type MenuItem } from '../config/menu'
import { useAppStore } from '../stores/app'
import { useMessageStore } from '../stores/message'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const msgStore = useMessageStore()

/** 按当前用户的角色过滤后的导航结构 */
const groups = computed(() => visibleGroups(userStore.roles))

/**
 * 展开的目录（按 title 记）。
 *
 * <p>用数组而不是 Set：要放进 ref 做响应式，数组在模板里的 includes 判断
 * 更直观，目录数量也就一两个，不值得为查找性能换数据结构。
 */
const opened = ref<string[]>([])

/** 路径是否处于激活态。子路由（如设备详情 /devices/1042）也算 */
function isActive(path?: string): boolean {
  if (!path) return false
  return route.path === path || route.path.startsWith(`${path}/`)
}

/** 目录项是否该高亮：有任一子项处于激活态 */
function isGroupActive(item: MenuItem): boolean {
  return item.children?.some((c) => isActive(c.path)) ?? false
}

/** 该目录是否展开 */
function isOpen(title: string): boolean {
  return opened.value.includes(title)
}

/**
 * 进页面时自动展开包含当前路由的目录。
 *
 * <p>不这么做的话，从外部链接直接进 /system/users 会看到"系统管理"是收起的，
 * 用户不知道自己在哪一层。
 */
watch(
  () => route.path,
  () => {
    for (const group of groups.value) {
      for (const item of group.items) {
        if (item.children?.some((c) => isActive(c.path)) && !isOpen(item.title)) {
          opened.value.push(item.title)
        }
      }
    }
  },
  { immediate: true },
)

/**
 * 展开/收起一个目录。
 *
 * <p>收起的图标条里点目录必须先**把导航展开**再打开它 ——
 * 收起态下子项是不渲染的，只切 `opened` 的话用户点了完全没反应。
 * 这里刻意不用"悬浮弹出子菜单"那种做法：它依赖浮层的定位和
 * 悬停判定，属于"只在浏览器里才成立"的交互，这个项目已经吃过
 * 一次自定义指令挂不到多根节点组件上的亏。展开整条导航是
 * 纯状态切换，行为可预期。
 */
function toggle(title: string) {
  if (appStore.railCollapsed) {
    appStore.setRail(false)
    if (!isOpen(title)) {
      opened.value.push(title)
    }
    return
  }

  const i = opened.value.indexOf(title)
  if (i >= 0) {
    opened.value.splice(i, 1)
  } else {
    opened.value.push(title)
  }
}

/** 消息中心上的未读角标 */
const unreadText = computed(() =>
  msgStore.unreadCount > 99 ? '99+' : String(msgStore.unreadCount),
)

/** 底部展开/收起按钮的提示文案 */
const railToggleTitle = computed(() => (appStore.railCollapsed ? '展开导航' : '收起导航'))

/**
 * 收起态下给每个图标补一个原生 title / aria-label。
 *
 * <p>展开态返回 undefined —— 那里文字就写在图标旁边，再叠一层原生提示
 * 纯属噪音；Vue 遇到 undefined 会直接把属性整个去掉。
 *
 * <p>用原生 title 而不是自绘气泡：自绘浮层需要处理定位、悬停判定、
 * 边界翻转，全是"只在浏览器里才成立"的逻辑，而这个项目没有浏览器
 * 可以验证它们。
 */
function collapsedHint(title: string): string | undefined {
  return appStore.railCollapsed ? title : undefined
}

function go(path?: string) {
  if (path) {
    void router.push(path)
  }
}
</script>

<template>
  <nav class="rail" :class="{ 'is-collapsed': appStore.railCollapsed }" aria-label="主导航">
    <!-- 品牌区。深色轨上唯一用主色的地方。
         收起时只留标记，名称让位给图标 -->
    <div class="rail-brand">
      <span class="rail-mark">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none"
             stroke="currentColor" stroke-width="1.6" aria-hidden="true">
          <rect x="3.5" y="4" width="17" height="6.5" rx="1.4" />
          <rect x="3.5" y="13.5" width="17" height="6.5" rx="1.4" />
          <circle cx="7" cy="7.25" r="1.05" fill="currentColor" stroke="none" />
          <circle cx="7" cy="16.75" r="1.05" fill="currentColor" stroke="none" />
        </svg>
      </span>
      <span class="rail-name">
        <b>{{ appStore.systemName }}</b>
        <span>{{ appStore.companyName || '资产管理平台' }}</span>
      </span>
    </div>

    <div class="rail-nav">
      <div v-for="group in groups" :key="group.title" class="rail-group">
        <div class="rail-group-title">
          <span>{{ group.title }}</span>
        </div>

        <template v-for="item in group.items" :key="item.title">
          <!-- 有子项：点标题展开/收起，不跳转 -->
          <template v-if="item.children?.length">
            <button
              type="button"
              class="rail-item"
              :class="{ 'is-open': isOpen(item.title), 'is-active': isGroupActive(item) }"
              :aria-expanded="isOpen(item.title)"
              :aria-label="collapsedHint(item.title)"
              :title="collapsedHint(item.title)"
              @click="toggle(item.title)"
            >
              <svg class="rail-icon" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="1.6"
                   stroke-linejoin="round" aria-hidden="true">
                <use :href="`#${item.icon}`" />
              </svg>
              <span class="rail-label">{{ item.title }}</span>
              <svg class="rail-chev" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" stroke-width="1.9"
                   stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <use href="#i-chevron" />
              </svg>
            </button>

            <!-- 收起态下子项整个不渲染，而不是 display:none ——
                 收起时本来就没有地方摆它们，用 v-show 会多渲染一层空壳 -->
            <div
              v-show="isOpen(item.title) && !appStore.railCollapsed"
              class="rail-sub"
            >
              <button
                v-for="child in item.children"
                :key="child.path"
                type="button"
                class="rail-item is-sub"
                :class="{ 'is-active': isActive(child.path) }"
                @click="go(child.path)"
              >
                <span class="rail-label">{{ child.title }}</span>
              </button>
            </div>
          </template>

          <!-- 单项：用 RouterLink，保留右键新标签打开的能力 -->
          <RouterLink
            v-else
            class="rail-item"
            :class="{ 'is-active': isActive(item.path) }"
            :to="item.path as string"
            :aria-label="collapsedHint(item.title)"
            :title="collapsedHint(item.title)"
          >
            <svg class="rail-icon" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="1.6"
                 stroke-linejoin="round" aria-hidden="true">
              <use :href="`#${item.icon}`" />
            </svg>
            <span class="rail-label">{{ item.title }}</span>
            <span
              v-if="item.path === '/messages' && msgStore.hasUnread"
              class="rail-badge num"
            >
              {{ unreadText }}
            </span>
          </RouterLink>
        </template>
      </div>
    </div>

    <!-- 收起/展开。放在轨道最底部而不是顶栏：它管的是导航自己，
         跟着导航走比挂到顶栏更好找 -->
    <button
      type="button"
      class="rail-toggle"
      :aria-expanded="!appStore.railCollapsed"
      :aria-label="railToggleTitle"
      :title="railToggleTitle"
      @click="appStore.toggleRail()"
    >
      <svg class="rail-chev" :class="{ 'is-expanded': !appStore.railCollapsed }"
           viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="1.9"
           stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <use href="#i-chevron" />
      </svg>
      <span class="rail-label">{{ railToggleTitle }}</span>
    </button>
  </nav>
</template>

<style scoped>
.rail {
  display: flex;
  flex-direction: column;
  width: var(--rail-w);
  flex: none;
  background: var(--rail);
  border-right: 1px solid var(--rail-line);
  overflow: hidden;
}

/* ---------- 品牌 ---------- */
.rail-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--topbar-h);
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--rail-line);
  flex: none;
}

.rail-mark {
  flex: none;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  color: var(--on-signal);
  background: var(--signal);
  border-radius: var(--r-control);
}

.rail-name {
  min-width: 0;
}

.rail-name b {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--rail-hi);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rail-name span {
  display: block;
  font-size: 11px;
  color: var(--rail-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------- 导航 ---------- */
.rail-nav {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-3) 0 var(--sp-4);
}

.rail-group + .rail-group {
  margin-top: var(--sp-4);
}

/* 分组标题刻意做得很轻：它是路标，不是内容。
   也没有用"拉开字距的全大写"那种模板写法 */
.rail-group-title {
  padding: 0 var(--sp-4) 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--rail-dim);
}

.rail-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: calc(100% - var(--sp-2) * 2);
  height: 34px;
  margin: 1px var(--sp-2);
  padding: 0 10px;
  font: inherit;
  font-size: 13px;
  color: var(--rail-text);
  text-align: left;
  background: none;
  border: none;
  border-radius: var(--r-control);
  cursor: pointer;
  text-decoration: none;
}

.rail-item:hover {
  background: var(--rail-2);
  color: var(--rail-hi);
}

.rail-icon {
  flex: none;
  width: 16px;
  height: 16px;
  opacity: .72;
}

.rail-item:hover .rail-icon {
  opacity: 1;
}

.rail-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 选中态：左侧一道竖条 + 提亮。
   刻意不用整块高饱和填色 —— 那会让侧栏变吵，一眼分不清主次 */
.rail-item.is-active {
  background: var(--rail-active);
  color: var(--rail-hi);
  font-weight: 500;
}

.rail-item.is-active .rail-icon {
  opacity: 1;
  color: var(--rail-accent);
}

.rail-item.is-active::before {
  content: "";
  position: absolute;
  left: -8px;
  top: 7px;
  bottom: 7px;
  width: 3px;
  border-radius: 0 2px 2px 0;
  background: var(--rail-accent);
}

/* 目录展开时箭头转 90 度 */
.rail-chev {
  flex: none;
  width: 13px;
  height: 13px;
  opacity: .5;
  transition: transform .15s;
}

.rail-item.is-open .rail-chev {
  transform: rotate(90deg);
}

/* 子项：缩进对齐到父项的图标右侧 */
.rail-sub {
  padding-left: 26px;
}

.rail-item.is-sub {
  height: 30px;
  font-size: 12.5px;
}

.rail-item.is-sub.is-active {
  background: transparent;
  color: var(--rail-hi);
}

.rail-item.is-sub.is-active::before {
  top: 6px;
  bottom: 6px;
}

/* ---------- 未读角标 ---------- */
.rail-badge {
  flex: none;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  font-size: 11px;
  color: var(--on-crit);
  background: var(--crit);
  border-radius: 999px;
}

/* ---------- 收起态（图标条）----------
   窄屏时整条轨道收成 60px，只留图标。宽度收窄是**布局行为**
   （flex 里 flex: none，不会浮到内容上面），所以不需要处理
   z-index、遮罩、点外关闭这些浮层问题。

   收起状态由 stores/app.ts 的 railCollapsed 控制：窄屏进入时自动置位，
   也可以随时点底部的按钮手动切换。 */
.rail.is-collapsed {
  width: var(--rail-w-collapsed);
}

/* 品牌只留标记 */
.rail.is-collapsed .rail-brand {
  justify-content: center;
  padding: 0;
}

.rail.is-collapsed .rail-name {
  display: none;
}

/* 分组标题退化成一条分隔线。文字不是"藏起来"，是这一层
   本来就没地方放它 —— 收起态靠图标分组和分隔线维持结构感 */
.rail.is-collapsed .rail-group-title {
  height: 1px;
  margin: var(--sp-3) var(--sp-4) var(--sp-2);
  padding: 0;
  background: var(--rail-line);
  overflow: hidden;
}

.rail.is-collapsed .rail-group-title span {
  display: none;
}

/* 第一组的分隔线紧挨着品牌区那条下边框，两条横线离得太近会显乱 */
.rail.is-collapsed .rail-group:first-child .rail-group-title {
  display: none;
}

/* 图标居中，标签、箭头、角标数字一律不显示 */
.rail.is-collapsed .rail-item {
  justify-content: center;
  height: 40px;
  padding: 0;
}

.rail.is-collapsed .rail-icon {
  width: 18px;
  height: 18px;
  opacity: .8;
}

/* 收起态下没有文字，图标本身的对比度就得顶上来 */
.rail.is-collapsed .rail-item:hover .rail-icon,
.rail.is-collapsed .rail-item.is-active .rail-icon {
  opacity: 1;
}

/* 未读角标收成一个小圆点：60px 宽放不下 "99+"，
   但"有没有未读"这个信息不能丢 */
.rail.is-collapsed .rail-badge {
  position: absolute;
  top: 5px;
  right: 6px;
  min-width: 7px;
  height: 7px;
  padding: 0;
  font-size: 0;
  overflow: hidden;
  border-radius: 50%;
}

/* ---------- 底部展开/收起按钮 ---------- */
.rail-toggle {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 42px;
  padding: 0 var(--sp-4);
  font: inherit;
  font-size: 12px;
  color: var(--rail-dim);
  text-align: left;
  background: none;
  border: none;
  border-top: 1px solid var(--rail-line);
  cursor: pointer;
}

.rail-toggle:hover {
  color: var(--rail-hi);
  background: var(--rail-2);
}

/* 箭头默认指向右（=展开），展开态转 180 度指向左（=收起） */
.rail-toggle .rail-chev.is-expanded {
  transform: rotate(180deg);
}

.rail.is-collapsed .rail-toggle {
  justify-content: center;
  padding: 0;
}

.rail.is-collapsed .rail-toggle .rail-chev {
  width: 15px;
  height: 15px;
  opacity: .8;
}

/* ---------- 窄屏 ----------
   ⚠️ 这里**故意不写宽度**。

   窄屏收起是靠 stores/app.ts 的 railCollapsed 状态驱动的
   （初始值看 matchMedia，跨断点时由 App.vue 的监听置位）。
   如果这里再写一条 `@media (max-width: 900px) { .rail { width: 60px } }`，
   它就成了一条**CSS 单方面说了算**的规则：用户在这种宽度下点
   "展开导航"时，状态是展开的、CSS 却仍然把宽度按死在 60px，
   结果是文字被裁掉、按钮点不动，而且完全不报错。

   状态和样式各管一半最省事：CSS 只管 .is-collapsed 长什么样，
   "什么时候该收起"由 JS 一处决定。断点值见 NARROW_QUERY。 */
</style>
