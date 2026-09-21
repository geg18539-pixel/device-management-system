import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

/** 后端统一响应格式，对应后端的 common/Result.java */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 业务成功码 */
const SUCCESS_CODE = 200
const UNAUTHORIZED = 401

/**
 * 后端自定义状态码：必须先修改密码。
 *
 * <p>故意和 403（真的没有权限）区分开 —— 两者对前端意味着完全不同的动作：
 * 428 跳改密页，403 提示"没有权限"。
 */
const PASSWORD_CHANGE_REQUIRED = 428

/** 改密页路径。已经在改密页时不要再跳，否则会死循环 */
const CHANGE_PASSWORD_PATH = '/change-password'

/**
 * 登录接口本身在密码错误时也会返回 401。
 * 必须把它排除在"401 就跳登录页"的逻辑之外，否则用户输错密码时
 * 会被反复重定向，什么提示都看不到。
 */
const LOGIN_PATH = '/auth/login'

const service: AxiosInstance = axios.create({
  // 走 Vite 代理，见 vite.config.ts 的 server.proxy
  baseURL: '/api',
  timeout: 10000,
})

// ============================================================
// 请求拦截器：自动携带 token
// ============================================================
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 在回调内部（运行时）才调用 useUserStore()，不在模块顶层调用。
    // Pinia 是在 main.ts 的 app.use(createPinia()) 之后才可用，
    // 模块顶层取会报 "getActivePinia() was called but there was no active Pinia"。
    const token = useUserStore().token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// ============================================================
// 响应拦截器
// ============================================================
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data

    // 后端返回的不是统一格式时（比如以后的文件下载接口），原样返回，不做解包
    if (res === null || typeof res !== 'object' || typeof res.code !== 'number') {
      return response.data as unknown as AxiosResponse
    }

    if (res.code === SUCCESS_CODE) {
      // 成功：把 { code, message, data } 剥掉，只把 data 交给调用方
      return res.data as unknown as AxiosResponse
    }

    // 业务失败：提示后中断，让调用方的 catch 能接到
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error?.response?.status
    const url: string = String(error?.config?.url ?? '')

    // 401 且不是登录接口 → token 失效或未登录，清理并送回登录页
    if (status === UNAUTHORIZED && !url.includes(LOGIN_PATH)) {
      handleUnauthorized()
      return Promise.reject(error)
    }

    // 428 → 必须先改密码。这是流程状态，不是错误，
    // 所以**不弹错误提示**，直接把人送到改密页
    if (status === PASSWORD_CHANGE_REQUIRED) {
      redirectToChangePassword()
      return Promise.reject(error)
    }

    // 其余错误：网络层问题、5xx、以及登录接口的 401（密码错误）。
    // 后端抛异常时 GlobalExceptionHandler 返回的响应体仍是统一格式，
    // 所以优先用后端给的 message。
    let message = '网络异常，请稍后重试'

    const backendMessage = error?.response?.data?.message
    if (typeof backendMessage === 'string' && backendMessage) {
      message = backendMessage
    } else if (error?.code === 'ECONNABORTED' || String(error?.message).includes('timeout')) {
      message = '请求超时，请稍后重试'
    } else if (status) {
      message = `请求失败（HTTP ${status}）`
    }

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

/**
 * 把用户送到改密页。
 *
 * <p>和 handleUnauthorized 一样用 window.location 而不是 router.push：
 * 既避免 request.ts 反向 import router 造成循环依赖，也顺带清空内存状态。
 */
function redirectToChangePassword() {
  const current = window.location.pathname + window.location.search
  if (current.startsWith(CHANGE_PASSWORD_PATH)) {
    // 已经在这个页面上了。再跳会无限刷新，而且改密接口本身返回 428
    // 也说明后端那边有问题（它本来是放行的）
    return
  }
  window.location.href = `${CHANGE_PASSWORD_PATH}?redirect=${encodeURIComponent(current)}`
}

/** token 失效时的统一处理：清状态 + 回登录页，并记住原地址便于登录后跳回 */
function handleUnauthorized() {
  const userStore = useUserStore()
  const wasLoggedIn = userStore.isLoggedIn

  userStore.logout()

  // 只有"本来是登录状态、突然失效"才提示。
  // 否则刷新页面时若有请求先于路由守卫发出，会反复弹提示很烦人。
  if (wasLoggedIn) {
    ElMessage.error('登录已过期，请重新登录')
  }

  const current = window.location.pathname + window.location.search
  if (current.startsWith('/login')) {
    return
  }

  // 这里用 window.location 而不是 router.push，有两个原因：
  // 1) 避免 request.ts 反向 import router。router → api/auth → request → router
  //    会构成循环依赖；
  // 2) 整页刷新能顺带清空所有内存状态，对"认证失效"来说这是更干净的处理。
  window.location.href = `/login?redirect=${encodeURIComponent(current)}`
}

/**
 * 因为响应拦截器已经把 { code, message, data } 解包成了 data，
 * 运行时的返回值和 axios 自己声明的 AxiosResponse 类型对不上。
 * 这里用一个显式接口把类型收口，让调用方写 request.get<Device[]>('/devices')
 * 时拿到的就是 Promise<Device[]>，而不是 Promise<AxiosResponse<...>>。
 */
export interface HttpClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const request = service as unknown as HttpClient

export default request
