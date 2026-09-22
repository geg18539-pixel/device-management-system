<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import AppRail from './components/AppRail.vue'
import TopActions from './components/TopActions.vue'
import TopNav from './components/TopNav.vue'
import { areaOfRoute, locateMenu } from './config/menu'
import { NARROW_QUERY, useAppStore } from './stores/app'

const route = useRoute()
const appStore = useAppStore()

/**
 * 登录页、改密页这类"裸页面"不套任何外壳。
 * 判断依据是路由的 meta，而不是硬编码 path，
 * 以后加注册页、找回密码页只要标一下 meta 就行。
 *
 * <p>public（不需要登录）和 bare（需要登录但不套外壳）是两回事：
 * 登录页是 public，改密页是 bare。
 */
const isBarePage = computed(() => route.meta.public === true || route.meta.bare === true)

/**
 * 当前所在区域（员工工作台 / 管理后台）。**它决定套哪一套外壳**。
 *
 * <p>由路径推出来，不是由角色 —— 管理员同时也是员工，
 * 他此刻在哪个区域取决于他在哪个页面。判定规则只有一条，
 * 收在 config/menu.ts 的 areaOfRoute。
 */
const area = computed(() => areaOfRoute(route.path))

/**
 * 面包屑。只有**管理后台**的顶栏用得上。
 *
 * <p>工作台不用它：那边顶栏是横向导航，导航本身的高亮就说明了位置，
 * 而且每个页面都有自己的 PageHeader 标题 —— 三者叠在一起是重复。
 */
const crumb = computed(() => locateMenu(route.path))

/**
 * 工作台顶栏的品牌区。
 *
 * <p>用**系统参数里的系统名称**（管理员改了跟着变）。后台那个写死的
 * 「管理后台」正好相反：后台要固定（区域要能一眼区分），工作台要跟着企业走。
 */
const brandName = computed(() => appStore.systemName)
const brandTagline = computed(() => appStore.companyName || '资产管理平台')

// ============================================================
// 窄屏自动收起侧栏
// ============================================================
//
// ⚠️ 这一条只对**管理后台**那套外壳有意义 —— 工作台没有侧栏。
// 留着不动是因为它本身没错：窄屏下把侧栏收成图标条仍然是后台该有的行为。

const narrowQuery = window.matchMedia(NARROW_QUERY)

/**
 * 跨过断点时把侧栏收起来。
 *
 * <p>只在 `matches` 为真时动作 —— 变宽时**不自动展开**。因为
 * "收起的侧栏"可能是用户自己的选择（他手动点过收起），回到大屏还给他
 * 展开等于覆盖他的偏好。反过来，窄屏下不收会让表格被挤到看不全，
 * 所以这个方向必须强制。
 *
 * <p>`persist = false`：这是环境导致的收起，不是用户的选择，不写进 localStorage。
 */
function handleNarrowChange(event: MediaQueryListEvent) {
  if (event.matches) {
    appStore.setRail(true, false)
  }
}

onMounted(() => narrowQuery.addEventListener('change', handleNarrowChange))
onUnmounted(() => narrowQuery.removeEventListener('change', handleNarrowChange))
</script>

<template>
  <!--
    图标集：内联 SVG symbol，全局只渲染一次。
    项目没装 @element-plus/icons-vue，手写这一小套比引入一个包省事，
    也和原来顶栏铃铛的做法一致。每个 symbol 只画路径，颜色由
    currentColor 控制，所以能跟着文字色走。
  -->
  <svg class="icon-sprite" aria-hidden="true" focusable="false">
    <defs>
      <g id="i-dash">
        <rect x="3" y="3" width="7.5" height="7.5" rx="1.2" />
        <rect x="13.5" y="3" width="7.5" height="4.5" rx="1.2" />
        <rect x="13.5" y="10.5" width="7.5" height="10.5" rx="1.2" />
        <rect x="3" y="13.5" width="7.5" height="7.5" rx="1.2" />
      </g>
      <g id="i-device">
        <rect x="3.5" y="3.5" width="17" height="7" rx="1.5" />
        <rect x="3.5" y="13.5" width="17" height="7" rx="1.5" />
        <path d="M7 7h.01M7 17h.01" />
      </g>
      <g id="i-ledger">
        <path d="M6 3.5h8l4.5 4.5v12.5H6z" />
        <path d="M14 3.5V8h4.5" />
        <path d="M9 12.5h6M9 16h4" />
      </g>
      <g id="i-part">
        <path d="M12 3.2l8 4.4v9.2l-8 4.4-8-4.4V7.6z" />
        <path d="M4 7.6l8 4.4 8-4.4M12 12v9.2" />
      </g>
      <g id="i-wrench">
        <path d="M15.6 3.6a5 5 0 00-6.4 6.3L3.8 15.3a1.9 1.9 0 102.7 2.7l5.4-5.4a5 5 0 006.3-6.4l-3 3-2.4-.6-.6-2.4z" />
      </g>
      <g id="i-shield">
        <path d="M12 3l7.5 3v5.4c0 4.6-3.1 8.2-7.5 9.6-4.4-1.4-7.5-5-7.5-9.6V6z" />
        <path d="M9 11.8l2.2 2.2 4-4" />
      </g>
      <g id="i-bell">
        <path d="M6 9a6 6 0 0112 0c0 4 1.5 5.5 1.5 5.5h-15S6 13 6 9z" />
        <path d="M10 18a2 2 0 004 0" />
      </g>
      <g id="i-spark">
        <path d="M12 3l1.9 5.6L19.5 10l-5.6 1.9L12 17.5l-1.9-5.6L4.5 10l5.6-1.4z" />
        <path d="M18.5 16.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z" />
      </g>
      <g id="i-cog">
        <circle cx="12" cy="12" r="3.2" />
        <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.2 5.2l2.1 2.1M16.7 16.7l2.1 2.1M18.8 5.2l-2.1 2.1M7.3 16.7l-2.1 2.1" />
      </g>
      <g id="i-chevron">
        <path d="M9 6l6 6-6 6" />
      </g>
      <g id="i-sun">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.2 5.2l1.4 1.4M17.4 17.4l1.4 1.4M18.8 5.2l-1.4 1.4M6.6 17.4l-1.4 1.4" />
      </g>
      <g id="i-moon">
        <path d="M20.5 14.8A8.6 8.6 0 019.2 3.5a8.6 8.6 0 1011.3 11.3z" />
      </g>
      <g id="i-inbox">
        <path d="M3.5 13.5l2.6-8h11.8l2.6 8v4a2 2 0 01-2 2h-13a2 2 0 01-2-2z" />
        <path d="M3.5 13.5H9a3 3 0 006 0h5.5" />
      </g>
      <!-- 审计：一叠记录 + 放大镜。和 i-ledger（纯文档）的区别要一眼看得出来，
           所以用的是"翻查记录"这个意象而不是又一张纸 -->
      <g id="i-audit">
        <path d="M3.5 5.5h11M3.5 9.5h7M3.5 13.5h5.5M3.5 17.5h4.5" />
        <circle cx="15.8" cy="13.2" r="3.4" />
        <path d="M18.3 15.7l2.4 2.4" />
      </g>
      <!-- 知识库：一本摊开的书。书脊那道弧是它和普通"文档"图标的分界 -->
      <g id="i-knowledge">
        <path d="M4 5.5a2 2 0 012-2h11.5v15H6a2 2 0 00-2 2z" />
        <path d="M4 18.5a2 2 0 012-2h11.5" />
        <path d="M8.5 7.5h6M8.5 11h4" />
      </g>
      <!-- 诊断：一条心电波形。"看出毛病在哪"这个意象，和书、放大镜都不撞 -->
      <g id="i-diagnosis">
        <path d="M2.5 12.5h4l2.5-6.5 3.5 13 2.5-6.5h6.5" />
      </g>
      <!-- 关系图：一个中心连着三个外围节点。
           刻意画成"辐射状"而不是网格 —— 网格看起来像表格图标，
           而这里表达的是"从一台设备往外看它的关系" -->
      <g id="i-graph">
        <circle cx="12" cy="12" r="2.5" />
        <circle cx="12" cy="4.3" r="1.7" />
        <circle cx="5.1" cy="17.6" r="1.7" />
        <circle cx="18.9" cy="17.6" r="1.7" />
        <path d="M12 6v3.5M10.3 13.6L6.4 16.4M13.7 13.6l3.9 2.8" />
      </g>
      <!-- 数据问答：放大镜里装了三根高低不同的柱子。
           放大镜 = 查询（和"筛选"的语义对得上），柱子 = 数据本身。
           和其它图标的区别靠"镜框里那三根柱"这个细节 ——
           单独一个放大镜会和"搜索"混淆 -->
      <g id="i-query">
        <circle cx="10.4" cy="10.4" r="6.6" />
        <path d="M15.2 15.2L21 21" />
        <path d="M8 12.8v-1.9M10.4 12.8V8.3M12.8 12.8V9.9" />
      </g>

      <!-- ═══════ 管理后台的图标 ═══════
           这一组是后台专有的，和上面工作台那批在**画法**上错开：
           工作台的图标偏"物体"（显示器、扳手、铃铛），
           后台的偏"结构"（树、盾牌、门、纸）—— 进哪个区域一眼能感觉出不一样 -->

      <!-- 用户：一个人 -->
      <g id="i-user">
        <circle cx="12" cy="8" r="3.4" />
        <path d="M5.5 19.6c0-3.6 2.9-6.1 6.5-6.1s6.5 2.5 6.5 6.1" />
      </g>
      <!-- 角色：一个盾牌。角色是"权限的集合"，盾牌能表达这层含义，
           而且和「用户」那个头像不会看混（两个都画人最容易混） -->
      <g id="i-role">
        <path d="M12 3.2l7 2.6v5.4c0 4.2-2.9 7.7-7 9.6-4.1-1.9-7-5.4-7-9.6V5.8z" />
        <path d="M9 11.8l2.2 2.2 4-4.3" />
      </g>
      <!-- 部门：组织树。一个父节点往下连两个子节点 ——
           这正是"部门"在系统里的形态（SysDept 是 parentId 自关联树） -->
      <g id="i-dept">
        <rect x="9" y="3.2" width="6" height="4.4" rx="1" />
        <rect x="3.2" y="16.4" width="6" height="4.4" rx="1" />
        <rect x="14.8" y="16.4" width="6" height="4.4" rx="1" />
        <path d="M12 7.6v4.2M6.2 16.4v-2.3h11.6v2.3M12 11.8v2.3" />
      </g>
      <!-- 菜单：三条不同长度的横线表示层级缩进，右侧两条短横是它可以挂子项。
           刻意不用汉堡菜单的三条等长线 —— 那是"更多"的通用符号 -->
      <g id="i-menu">
        <path d="M4 6.5h11M4 12h16M4 17.5h8" />
        <path d="M17.2 4.6h2.8M17.2 8.4h2.8" />
      </g>
      <!-- 系统设置：齿轮 -->
      <g id="i-config">
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2.8v2.5M12 18.7v2.5M4.4 12H2M22 12h-2.4M6.6 6.6L4.9 4.9M19.1 19.1l-1.8-1.8M17.4 6.6l1.7-1.7M4.9 19.1l1.8-1.8" />
      </g>
      <!-- 字典：一列"键 / 值"条目（左边短、右边长）——
           字典项就是 itemValue 和 itemLabel 两列，这个画法直接对应它的数据形态 -->
      <g id="i-dict">
        <path d="M4 6.5h4M11.5 6.5h8.5M4 12h4M11.5 12h8.5M4 17.5h4M11.5 17.5h8.5" />
      </g>
      <!-- 登录日志：箭头穿过门框 -->
      <g id="i-login">
        <path d="M13.5 3.5h5a1.5 1.5 0 011.5 1.5v14a1.5 1.5 0 01-1.5 1.5h-5" />
        <path d="M3.5 12h10M10.2 8.6l3.5 3.4-3.5 3.4" />
      </g>
      <!-- 操作日志：一叠带行的纸 -->
      <g id="i-log">
        <path d="M6 3.5h9l3.5 3.5v13.5H6z" />
        <path d="M15 3.5V7h3.5" />
        <path d="M8.8 11.5h6.4M8.8 15h4.4" />
      </g>
      <!-- 后台首页：一栋房子（"回到后台的首页"）。
           刻意不用工作台那个四宫格（i-dash）—— 两个区域的首页图标一样的话，
           在侧栏里根本分不出自己在哪一边 -->
      <g id="i-home">
        <path d="M3.5 10.9L12 4.2l8.5 6.7V20H3.5z" />
        <path d="M9.6 20v-5.4h4.8V20" />
      </g>
    </defs>
  </svg>

  <!-- 裸页面（登录页、改密页）直接渲染，不套侧边栏和顶栏 -->

  <router-view v-if="isBarePage" />

  <!-- ============================================================
       管理后台：左侧竖栏 + 顶栏（面包屑）
       ============================================================ -->
  <div v-else-if="area === 'console'" class="layout is-console">
    <AppRail :area="area" />

    <div class="main-col">
      <header class="topbar">
        <div class="crumb">
          <template v-if="crumb">
            <span class="crumb-group">{{ crumb.group }}</span>
            <span class="sep">/</span>
            <b>{{ crumb.title }}</b>
          </template>
        </div>

        <TopActions :area="area" />
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>

  <!-- ============================================================
       员工工作台：横向顶部导航（没有左侧栏）

       一级是菜单分组，二级在悬停下拉里。用横向而不是竖栏，是因为工作台
       只有 5 个分组、11 个页面 —— 数量少到一横排放得下，把纵向空间整个
       让给内容（表格、图表这些最怕横向被挤）。

       管理后台**保持竖栏**：那边 12 项、以配置为主，竖排更合适；
       而且两个区域从结构上就不一样，一眼能看出是换了个系统。
       ============================================================ -->
  <div v-else class="layout is-workbench">
    <header class="topnav">
      <RouterLink class="brand" to="/dashboard">
        <span class="brand-mark">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none"
               stroke="currentColor" stroke-width="1.6" aria-hidden="true">
            <rect x="3.5" y="4" width="17" height="6.5" rx="1.4" />
            <rect x="3.5" y="13.5" width="17" height="6.5" rx="1.4" />
            <circle cx="7" cy="7.25" r="1.05" fill="currentColor" stroke="none" />
            <circle cx="7" cy="16.75" r="1.05" fill="currentColor" stroke="none" />
          </svg>
        </span>
        <span class="brand-text">
          <b>{{ brandName }}</b>
          <span>{{ brandTagline }}</span>
        </span>
      </RouterLink>

      <TopNav />

      <TopActions :area="area" />
    </header>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.icon-sprite {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

/* ============================================================
   两套外壳的公共部分
   ============================================================ */

.content {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-5);
}

/* ============================================================
   管理后台：左栏 + 顶栏
   ============================================================ */

.layout.is-console {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--canvas);
}

.main-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: var(--topbar-h);
  padding: 0 var(--sp-5);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  flex: none;
}

.crumb {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: 13px;
  min-width: 0;
}

.crumb-group {
  color: var(--ink-3);
}

.crumb .sep {
  color: var(--ink-4);
}

.crumb b {
  font-weight: 600;
  color: var(--ink-1);
}

/* ============================================================
   员工工作台：横向顶部导航
   ============================================================ */

.layout.is-workbench {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--canvas);
}

/* 顶栏是浅色的（和后台那条一样），**不是**深色轨道 ——
   深色轨道那套 token 是为 232px 竖条调的，横过来占满整屏宽度之后
   面积差了几十倍，观感是另一回事。
   用 --surface + ink 系列，全部是已验证过的组合，不新增任何配色 */
.topnav {
  display: flex;
  align-items: stretch;
  gap: var(--sp-5);
  height: var(--topbar-h);
  padding: 0 var(--sp-5);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  flex: none;
}

/* 品牌区。整块可点，回工作台首页 */
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex: none;
}

.brand-mark {
  flex: none;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  color: var(--on-signal);
  background: var(--signal);
  border-radius: var(--r-control);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text b {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

.brand-text span {
  font-size: 11px;
  color: var(--ink-3);
  white-space: nowrap;
}

/* 窄屏：品牌只留标记，导航尽量留着 —— 它是唯一的入口，
   藏起来用户就没法换页了（横向排不下时可以横向滚动） */
@media (max-width: 900px) {
  .brand-text {
    display: none;
  }

  .topnav {
    gap: var(--sp-3);
  }
}
</style>
