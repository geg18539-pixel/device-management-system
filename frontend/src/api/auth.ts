import request from '../utils/request'

export interface LoginParams {
  username: string
  password: string
}

/** 对应后端 dto/LoginResponse.java */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  nickname: string
  roles: string[]
  /** 细粒度权限点，前端据此控制按钮显隐 */
  perms: string[]
  /** 是否必须先修改密码才能使用系统 */
  mustChangePassword: boolean
  /** 密码还有多少天到期，负数表示已过期。null 表示不适用 */
  passwordExpireDays?: number | null
  passwordExpired?: boolean
}

/** 对应后端 dto/CurrentUserVO.java（/api/auth/me 的返回） */
export interface CurrentUser {
  userId: number
  username: string
  nickname: string
  roles: string[]
  perms: string[]
  mustChangePassword: boolean
  passwordExpireDays?: number | null
}

/** 登录，换取 JWT —— POST /api/auth/login（该路径在后端是放行的） */
export function login(data: LoginParams) {
  return request.post<LoginResult>('/auth/login', data)
}

/**
 * 获取当前登录用户 —— GET /api/auth/me
 *
 * 这个接口需要带 token。主要用途有三个：
 * 1. 页面刷新后 store 里的用户信息没了，用它重新拉一次；
 * 2. 顺便验证 token 是否还有效（失效会返回 401）；
 * 3. 拿权限点去控制按钮显隐。
 */
export function getCurrentUser() {
  return request.get<CurrentUser>('/auth/me')
}

/**
 * 修改自己的密码 —— PUT /api/auth/change-password
 *
 * <p>返回的是**新的 token**：改完密码后旧 token 里还挂着"需要改密"的标记，
 * 不换 token 的话下一个请求立刻又会被拦到改密页。
 */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request.put<LoginResult>('/auth/change-password', data)
}
