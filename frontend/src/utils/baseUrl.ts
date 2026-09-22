/**
 * 拼接"系统对外地址"。
 *
 * <p><b>为什么单独抽出来</b>：这段逻辑要处理几种真实存在但很容易漏的输入 ——
 * 管理员可能填 `192.168.1.20:8080`（忘了协议头）、填成 `http://x.com/`
 * （多了个结尾斜杠）。散在 store 里的话，既不好测也不好复用。
 * 抽成纯函数之后，它不依赖 `window`，可以在没有浏览器的环境里直接验。
 */

/** 去掉结尾的斜杠，避免拼出 `http://x.com//devices/1` 这种地址 */
function stripTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '')
}

/**
 * 解析出真正可用的对外地址。
 *
 * @param configured    管理员在「系统设置 → 系统信息 → 对外访问地址」里填的值。空表示没配
 * @param fallbackOrigin 没配时的退路，一般传 `window.location.origin`
 * @returns 不带结尾斜杠的完整地址
 */
export function resolveBaseUrl(configured: string | null | undefined, fallbackOrigin: string): string {
  const raw = (configured ?? '').trim()
  if (!raw) {
    return stripTrailingSlash(fallbackOrigin)
  }
  // 容错：管理员很可能只填 IP 和端口，不带 http://。
  // 不补的话会拼出 `192.168.1.20:8080/devices/1` —— 浏览器会当成
  // 一个自定义协议，直接打不开，而且报错信息看不出真正原因
  const withScheme = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`
  return stripTrailingSlash(withScheme)
}

/** 把基地址和路径拼起来，两边多余的斜杠都归一化掉 */
export function joinUrl(base: string, path: string): string {
  return `${stripTrailingSlash(base)}/${path.replace(/^\/+/, '')}`
}

/**
 * 这个地址是不是"只有本机能访问"。
 *
 * <p>用来在界面上提示：二维码是印给**别的设备**扫的，
 * `localhost` / `127.0.0.1` 扫了必然打不开。
 */
export function isLoopbackUrl(url: string): boolean {
  try {
    const host = new URL(url).hostname.toLowerCase()
    return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '[::1]'
  } catch {
    // 拼不成 URL 的（比如管理员填了带空格的乱字符串）不当成本机地址 ——
    // 那属于另一种错误，不该混进这条提示里
    return false
  }
}
