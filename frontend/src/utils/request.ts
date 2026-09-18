import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应格式，对应后端的 common/Result.java */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 业务成功码 */
const SUCCESS_CODE = 200

const service: AxiosInstance = axios.create({
  // 走 Vite 代理，见 vite.config.ts 的 server.proxy
  baseURL: '/api',
  timeout: 10000,
})

// ============================================================
// 请求拦截器
// ============================================================
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // TODO: 登录功能做好之后，改成从 Pinia store 里取。
    // 项目里已经装了 pinia，但还没有 store，所以先直接从 localStorage 读。
    const token = localStorage.getItem('token')
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
    // 网络层错误：超时、连不上、5xx 等。后端抛异常时 GlobalExceptionHandler
    // 返回的响应体仍然是统一格式，所以这里优先用后端给的 message。
    let message = '网络异常，请稍后重试'

    const backendMessage = error?.response?.data?.message
    if (typeof backendMessage === 'string' && backendMessage) {
      message = backendMessage
    } else if (error?.code === 'ECONNABORTED' || String(error?.message).includes('timeout')) {
      message = '请求超时，请稍后重试'
    } else if (error?.response?.status) {
      message = `请求失败（HTTP ${error.response.status}）`
    }

    ElMessage.error(message)
    return Promise.reject(error)
  },
)

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
