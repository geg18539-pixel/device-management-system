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
  /** 细粒度权限点。老版本存下来的数据里没有这个字段，读取时要兜底成空数组 */
  perms: string[]
}

function readUserFromStorage(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<UserInfo>
    return {
      userId: parsed.userId ?? 0,
      username: parsed.username ?? '',
      nickname: parsed.nickname ?? '',
      roles: parsed.roles ?? [],
      // 兜底：升级前存进去的 JSON 里没有 perms
      perms: parsed.perms ?? [],
    }
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

  /** 是否处于"必须先改密码"的状态。由登录响应和 /auth/me 更新，不持久化 */
  const mustChangePassword = ref(false)

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
      perms: data.perms ?? [],
    }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    mustChangePassword.value = data.mustChangePassword === true
  }

  /**
   * 用 /api/auth/me 的结果刷新用户信息（页面刷新后调用）。
   *
   * <p>nickname 优先保留已有的：早期版本的 /auth/me 不返回昵称，
   * 现在返回了，但保留分支不影响正确性，也避免将来接口再变时出问题。
   */
  function refreshUserInfo(info: {
    userId: number
    username: string
    nickname?: string
    roles?: string[]
    perms?: string[]
    mustChangePassword?: boolean
  }) {
    userInfo.value = {
      userId: info.userId,
      username: info.username,
      nickname: info.nickname || userInfo.value?.nickname || '',
      roles: info.roles ?? [],
      perms: info.perms ?? [],
    }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
    mustChangePassword.value = info.mustChangePassword === true
  }

  function hasRole(role: string) {
    return roles.value.includes(role)
  }

  /**
   * 是否拥有某个权限点（数组表示"其中任意一个即可"）。
   *
   * <p><b>超管整体放行</b>，和后端 JwtInterceptor 的语义保持一致。
   * 不加这条的话，管理员会因为"新增权限点后忘了给自己勾上"而看不到新按钮 ——
   * 这种"把自己锁在门外"的情况在真实项目里非常常见。
   *
   * <p>⚠️ 这**不是安全边界**，只是界面效果。真正拦住越权的是后端的 @RequirePerm。
   */
  function hasPerm(perm: string | string[]) {
    if (roles.value.includes('admin')) {
      return true
    }
    const owned = userInfo.value?.perms ?? []
    const needed = Array.isArray(perm) ? perm : [perm]
    return needed.some((p) => owned.includes(p))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    mustChangePassword.value = false
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    userInfo,
    mustChangePassword,
    isLoggedIn,
    roles,
    nickname,
    setLoginData,
    refreshUserInfo,
    hasRole,
    hasPerm,
    logout,
  }
})
