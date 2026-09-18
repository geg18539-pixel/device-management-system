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
}

/** 对应后端 common/LoginUser.java（/api/auth/me 的返回） */
export interface CurrentUser {
  userId: number
  username: string
  nickname: string
  roles: string[]
}

/** 登录，换取 JWT —— POST /api/auth/login（该路径在后端是放行的） */
export function login(data: LoginParams) {
  return request.post<LoginResult>('/auth/login', data)
}

/**
 * 获取当前登录用户 —— GET /api/auth/me
 *
 * 这个接口需要带 token。主要用途有两个：
 * 1. 页面刷新后 store 里的用户信息没了，用它重新拉一次；
 * 2. 顺便验证 token 是否还有效（失效会返回 401）。
 */
export function getCurrentUser() {
  return request.get<CurrentUser>('/auth/me')
}
