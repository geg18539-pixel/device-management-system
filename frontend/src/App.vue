<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MSG_LEVEL,
  markAllMessagesRead,
  markMessageRead,
  routeForMessage,
  type SysMessage,
} from './api/message'
import AppRail from './components/AppRail.vue'
import { locateMenu } from './config/menu'
import { NARROW_QUERY, useAppStore } from './stores/app'
import { useMessageStore } from './stores/message'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const msgStore = useMessageStore()

/**
 * 登录页、改密页这类"裸页面"不套后台外壳。
 * 判断依据是路由的 meta，而不是硬编码 path，
 * 以后加注册页、找回密码页只要标一下 meta 就行。
 *
 * <p>public（不需要登录）和 bare（需要登录但不套外壳）是两回事：
 * 登录页是 public，改密页是 bare。
 */
const isBarePage = computed(() => route.meta.public === true || route.meta.bare === true)

/**
 * 面包屑。用 config/menu.ts 里那份导航定义反查当前路径属于哪个分组，
 * 和侧边栏共用一份来源，不会出现"侧边栏改了名字、面包屑还是旧的"。
 *
 * <p>找不到时返回 null，顶栏左侧留空 —— 比显示一段错误的路径好。
 *
 * <p>页面级的大标题不在这里渲染：各页用 components/PageHeader.vue 自己写，
 * 因为每页的说明文字和主操作都不一样，外壳统一渲染反而要额外配一份 meta。
 */
const crumb = computed(() => locateMenu(route.path))

// ============================================================
// 主题
// ============================================================

const themeTitle = computed(() => (appStore.isDark ? '切换到亮色' : '切换到暗色'))

// ============================================================
// 窄屏自动收起侧栏
// ============================================================

const narrowQuery = window.matchMedia(NARROW_QUERY)

/**
 * 跨过断点时把侧栏收起来。
 *
 * <p>只在 `matches` 为真时动作 —— 变宽时**不自动展开**。因为
 * "收起的侧栏"可能是用户自己的选择（他手动点过收起），回到大屏还给他
 * 展开等于覆盖他的偏好。反过来，窄屏下不收会让表格被挤到看不全，
 * 所以这个方向必须强制。
 *
 * <p>`persist = false`：这是环境导致的收起，不是用户的选择，
 * 不写进 localStorage。
 *
 * <p>初始值不在这里处理 —— stores/app.ts 建 store 时就已经判断过
 * matchMedia 了，首帧画出来就是对的。这里只管"开着窗口拖宽窄"的情况。
 */
function handleNarrowChange(event: MediaQueryListEvent) {
  if (event.matches) {
    appStore.setRail(true, false)
  }
}

onMounted(() => narrowQuery.addEventListener('change', handleNarrowChange))
onUnmounted(() => narrowQuery.removeEventListener('change', handleNarrowChange))

// ============================================================
// 退出登录
// ============================================================

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    // 点了取消
    return
  }

  userStore.logout()
  ElMessage.success('已退出登录')
  await router.replace('/login')
}

function handleCommand(command: string) {
  if (command === 'logout') {
    void handleLogout()
  }
}

// ============================================================
// 站内消息
// ============================================================

/**
 * 登录状态变化时启停消息轮询。
 *
 * <p>⚠️ 退出登录后**必须停掉轮询**：否则它会继续发请求、每次都 401、
 * 每次都触发跳登录页，用户会看到登录页在反复刷新。
 */
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      void msgStore.refresh()
      msgStore.startPolling()
    } else {
      msgStore.reset()
    }
  },
  { immediate: true },
)

function formatMsgTime(value?: string): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(5, 16)
}

/** 点一条消息：先标记已读，再跳到对应的业务页 */
async function openMessage(msg: SysMessage) {
  if (!msg.readFlag) {
    try {
      await markMessageRead(msg.id)
    } catch {
      // 标记失败不影响跳转，下次刷新还会是未读
    }
  }
  const target = routeForMessage(msg)
  if (target) {
    await router.push(target)
  }
  void msgStore.refresh()
}

async function handleReadAll() {
  try {
    await markAllMessagesRead()
    ElMessage.success('已全部标记为已读')
    await msgStore.refresh()
  } catch {
    // 提示已由拦截器处理
  }
}

function goMessages() {
  void router.push('/messages')
}
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
    </defs>
  </svg>

  <!-- 裸页面（登录页、改密页）直接渲染，不套侧边栏和顶栏 -->
  <router-view v-if="isBarePage" />

  <div v-else class="layout">
    <AppRail />

    <div class="main-col">
      <header class="topbar">
        <div class="crumb">
          <template v-if="crumb">
            <span class="crumb-group">{{ crumb.group }}</span>
            <span class="sep">/</span>
            <b>{{ crumb.title }}</b>
          </template>
        </div>

        <div class="topbar-right">
          <!-- 主题切换 -->
          <button
            type="button"
            class="icon-btn"
            :title="themeTitle"
            :aria-label="themeTitle"
            @click="appStore.toggleTheme()"
          >
            <svg viewBox="0 0 24 24" width="17" height="17" fill="none"
                 stroke="currentColor" stroke-width="1.7"
                 stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <use :href="appStore.isDark ? '#i-sun' : '#i-moon'" />
            </svg>
          </button>

          <!-- 站内消息。有未读时右上角显示红点数字 -->
          <el-dropdown trigger="click" placement="bottom-end">
            <el-badge
              :value="msgStore.unreadCount"
              :hidden="!msgStore.hasUnread"
              :max="99"
              class="bell-badge"
            >
              <span class="bell" title="站内消息">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none"
                     stroke="currentColor" stroke-width="1.7"
                     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <use href="#i-bell" />
                </svg>
              </span>
            </el-badge>

            <template #dropdown>
              <div class="msg-panel">
                <div class="msg-panel-head">
                  <span class="msg-panel-title">站内消息</span>
                  <el-button
                    v-if="msgStore.hasUnread"
                    link
                    type="primary"
                    size="small"
                    @click="handleReadAll"
                  >
                    全部已读
                  </el-button>
                </div>

                <div v-if="!msgStore.recent.length" class="msg-empty">暂无消息</div>
                <div v-else class="msg-list">
                  <div
                    v-for="m in msgStore.recent"
                    :key="m.id"
                    class="msg-item"
                    :class="{ unread: !m.readFlag }"
                    @click="openMessage(m)"
                  >
                    <div class="msg-item-main">
                      <span class="msg-dot" :class="{ hidden: m.readFlag }" />
                      <span class="msg-title" :class="{ important: m.level === MSG_LEVEL.IMPORTANT }">
                        {{ m.title }}
                      </span>
                    </div>
                    <div class="msg-time num">{{ formatMsgTime(m.createTime) }}</div>
                  </div>
                </div>

                <div class="msg-panel-foot">
                  <el-button link type="primary" size="small" @click="goMessages">
                    查看全部
                  </el-button>
                </div>
              </div>
            </template>
          </el-dropdown>

          <el-dropdown @command="handleCommand">
            <span class="user">
              <span class="avatar">{{ userStore.nickname.slice(0, 1) }}</span>
              <span class="user-text">
                <span class="uname">{{ userStore.nickname }}</span>
                <span class="urole">{{ userStore.roles.join('、') || '无角色' }}</span>
              </span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  角色：{{ userStore.roles.join('、') || '无' }}
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
/* 图标集本身不占布局，只是把 symbol 放进文档 */
.icon-sprite {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

/* ---------- 外壳 ---------- */
.layout {
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

/* ---------- 顶栏 ---------- */
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
  gap: 6px;
  font-size: 13px;
  min-width: 0;
}

.crumb-group {
  color: var(--ink-3);
}

/* 面包屑用斜杠分隔，不用中点拼接（"A · B · C" 是模板化界面的高频特征） */
.crumb .sep {
  color: var(--ink-4);
}

.crumb b {
  font-weight: 500;
  color: var(--ink-1);
}

.topbar-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.icon-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  color: var(--ink-2);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--r-control);
  cursor: pointer;
}

.icon-btn:hover {
  color: var(--ink-1);
  background: var(--sunken);
}

/* ---------- 内容区 ---------- */
.content {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-5);
  background: var(--canvas);
}

/* ---------- 消息铃铛 ---------- */
.bell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  color: var(--ink-2);
  cursor: pointer;
  outline: none;
}

.bell:hover {
  color: var(--signal);
}

/* 红点压到铃铛右上角，不要挤在默认位置 */
.bell-badge :deep(.el-badge__content) {
  top: 6px;
  right: 10px;
}

/* ---------- 消息下拉面板 ---------- */
.msg-panel {
  width: 320px;
  text-align: left;
}

.msg-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
}

.msg-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

.msg-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--ink-3);
}

.msg-list {
  max-height: 320px;
  overflow-y: auto;
}

.msg-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-soft);
  cursor: pointer;
}

.msg-item:hover {
  background: var(--hover);
}

.msg-item-main {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

/* 未读的小圆点。已读时不显示，但**保留占位**（visibility: hidden 而不是
   display: none），否则已读和未读的标题会左右错开一截，看着很乱 */
.msg-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--crit);
}

.msg-dot.hidden {
  visibility: hidden;
}

.msg-title {
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink-2);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.msg-item.unread .msg-title {
  color: var(--ink-1);
  font-weight: 500;
}

.msg-title.important {
  color: var(--crit);
}

.msg-time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--ink-3);
}

.msg-panel-foot {
  padding: 8px;
  text-align: center;
  border-top: 1px solid var(--line);
}

/* ---------- 用户 ---------- */
.user {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 3px 8px 3px 3px;
  border: 1px solid transparent;
  border-radius: var(--r-control);
  cursor: pointer;
  outline: none;
}

.user:hover {
  border-color: var(--line);
}

.avatar {
  flex: none;
  width: 26px;
  height: 26px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--on-signal);
  background: var(--signal);
  border-radius: var(--r-control);
}

.user-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.uname {
  font-size: 13px;
  color: var(--ink-1);
}

.urole {
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------- 窄屏：顶栏省掉次要信息，把空间让给操作 ---------- */
@media (max-width: 900px) {
  .crumb {
    display: none;
  }

  .topbar {
    padding: 0 var(--sp-4);
  }

  .content {
    padding: var(--sp-4);
  }
}
</style>
