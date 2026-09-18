import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { LoginResult } from '../api/auth'

const TOKEN_KEY = 'dms_token'
const USER_KEY = 'dms_user'

interface UserInfo {
  userId: number
  username: string
  nickname: string
  roles: string[]
}

function readUserFromStorage(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    // 存储内容被手工改坏时不要让整个应用起不来
    localStorage.removeItem(USER_KEY)
    return null
  }
}

/**
 * 登录用户状态。
 *
 * <p>这里**刻意不引入任何 api 模块**。因为 request.ts 需要读这里的 token，
 * 如果这个 store 反过来又 import 了 api/auth.ts（而后者 import request.ts），
 * 就形成了 request → store → api → request 的循环依赖。
 * 所以登录动作放在 LoginView 里做，store 只负责存状态。
 *
 * <p>token 和用户信息同时写 localStorage，刷新页面不会掉登录态。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) ?? '')
  const userInfo = ref<UserInfo | null>(readUserFromStorage())

  const isLoggedIn = computed(() => token.value !== '')
  const roles = computed(() => userInfo.value?.roles ?? [])
  const nickname = computed(
    () => userInfo.value?.nickname || userInfo.value?.username || '未登录',
  )

  /** 登录成功后写入 */
  function setLoginData(data: LoginResult) {
    token.value = data.token
    localStorage.setItem(TOKEN_KEY, data.token)

    userInfo.value = {
      userId: data.userId,
      username: data.username,
      nickname: data.nickname ?? '',
      roles: data.roles ?? [],
    }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
  }

  /**
   * 用 /api/auth/me 的结果刷新用户信息（页面刷新后调用）。
   *
   * 该接口不返回昵称，所以这里保留已有的昵称而不是覆盖成空 ——
   * 昵称在 setLoginData 时已经存进 localStorage 了。
   */
  function refreshUserInfo(info: { userId: number; username: string; roles?: string[] }) {
    userInfo.value = {
      userId: info.userId,
      username: info.username,
      nickname: userInfo.value?.nickname ?? '',
      roles: info.roles ?? [],
    }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
  }

  function hasRole(role: string) {
    return roles.value.includes(role)
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    roles,
    nickname,
    setLoginData,
    refreshUserInfo,
    hasRole,
    logout,
  }
})
