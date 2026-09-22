<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  MSG_LEVEL,
  markAllMessagesRead,
  markMessageRead,
  routeForMessage,
  type SysMessage,
} from '../api/message'
import { AREA_META, type Area } from '../config/menu'
import { useAppStore } from '../stores/app'
import { useMessageStore } from '../stores/message'
import { useUserStore } from '../stores/user'

/**
 * 顶栏右侧的那组操作：区域切换、主题、站内消息、账号菜单。
 *
 * <h3>为什么抽成组件</h3>
 *
 * <p>工作台和后台**两套外壳都要这一块**（一百多行模板 + 两百多行样式）。
 * 留在 App.vue 里就得复制一份，而复制的那份必然会在某次改样式时被漏掉。
 *
 * <p>它自己从路由算区域，不需要父组件喂 —— 减少一个要同步的输入。
 */
const props = defineProps<{
  /** 当前所在区域。只用来决定切换按钮的文字和箭头方向 */
  area: Area
}>()

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const msgStore = useMessageStore()

/** 能不能在工作台和后台之间切。只有管理员有后台可去 */
const canSwitchArea = computed(() => userStore.roles.includes('admin'))

/** 切区域的入口。去后台落到 /system，回工作台落到看板 */
function switchArea() {
  void router.push(props.area === 'console' ? '/dashboard' : '/system')
}

/** 切换按钮上的文案：在哪儿就往哪儿指 */
const switchLabel = computed(() =>
  props.area === 'console' ? `返回${AREA_META.workbench.label}` : AREA_META.console.label,
)

const themeTitle = computed(() => (appStore.isDark ? '切换到亮色' : '切换到暗色'))

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

/**
 * 登录状态变化时启停消息轮询。
 *
 * <p>⚠️ 退出登录后**必须停掉轮询**：否则它会继续发请求、每次都 401、
 * 每次都触发跳登录页，用户会看到登录页在反复刷新。
 *
 * <p>这个 watch 跟着铃铛一起放在这里 —— 它俩是同一件事：
 * 铃铛是轮询的唯一使用者。
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
  <div class="top-actions">

          <!-- 区域切换。**只有管理员渲染** —— 没有 admin 角色的人
               顶栏里不会有这个按钮，侧栏里也不会出现任何后台入口。
               两边是同一件事的两面：菜单按区域取、入口按角色给。
               箭头在后台时翻转，指向"回去"的方向 -->
          <button
            v-if="canSwitchArea"
            type="button"
            class="area-switch"
            @click="switchArea"
          >
            <svg
              class="area-arrow"
              :class="{ 'is-back': area === 'console' }"
              viewBox="0 0 24 24" width="13" height="13" fill="none"
              stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
            >
              <path d="M10.5 6.5L16 12l-5.5 5.5M6 12h9.5" />
            </svg>
            {{ switchLabel }}
          </button>

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
</template>

<style scoped>
.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}



.top-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}


.area-switch:hover {
  color: var(--ink-1);
  background: var(--sunken);
  border-color: var(--line-strong);
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
</style>
