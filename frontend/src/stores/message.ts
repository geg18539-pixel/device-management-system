import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getRecentMessages,
  getUnreadCount,
  type SysMessage,
} from '../api/message'

/** 轮询间隔。60 秒够用了 —— 站内消息不是即时聊天，没必要每秒去问 */
const POLL_INTERVAL_MS = 60_000

/**
 * 站内消息状态（未读数 + 最近几条）。
 *
 * <p>为什么单独一个 store 而不是塞进 user：未读数要被顶栏（App.vue）和
 * 消息中心页同时用，而且**顶栏需要定时刷新**，不能让每个页面各管各的轮询。
 *
 * <p>⚠️ 轮询只在登录后开始、退出登录时停掉。忘了停的话，
 * 退出后还会一直发请求，每次都 401 并触发跳登录页 —— 用户会看到登录页反复刷新。
 */
export const useMessageStore = defineStore('message', () => {
  const unreadCount = ref(0)
  const recent = ref<SysMessage[]>([])
  const loading = ref(false)

  let timer: number | null = null

  const hasUnread = computed(() => unreadCount.value > 0)

  /** 拉一次未读数 + 最近几条 */
  async function refresh() {
    loading.value = true
    try {
      const [count, list] = await Promise.all([getUnreadCount(), getRecentMessages(5)])
      unreadCount.value = count ?? 0
      recent.value = list ?? []
    } catch {
      // 消息拉不到不该弹错误提示打扰用户 —— 顶栏红点不更新就是了。
      // 401 会由 request.ts 的拦截器统一处理
    } finally {
      loading.value = false
    }
  }

  /** 开始轮询。重复调用是安全的（已经在跑就不重复起） */
  function startPolling() {
    stopPolling()
    timer = window.setInterval(() => {
      void refresh()
    }, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  /** 退出登录时调用：停轮询并清状态 */
  function reset() {
    stopPolling()
    unreadCount.value = 0
    recent.value = []
  }

  return { unreadCount, recent, loading, hasUnread, refresh, startPolling, stopPolling, reset }
})
