/**
 * 文件下载的小工具。
 *
 * <p>抽出来的理由很实际：这段「Blob URL + 临时 `<a>`」的写法原来在
 * device.ts / deviceRepair.ts / user.ts 里各抄了一份（一共 4 处，
 * 有的写成函数、有的直接内联）。再抄第五份就该有人漏掉
 * `revokeObjectURL` 了。
 */

/**
 * 用 Blob URL + 临时 `<a>` 触发浏览器下载。
 *
 * <p>比 `window.open` 可靠：window.open 在需要带鉴权头的场景下拿不到文件
 * （它发的是一个新的、不带 Authorization 的请求）。
 *
 * <p>request.ts 的拦截器对二进制响应是安全的 —— 它判断"返回体里有没有
 * code 字段"，Blob 没有，于是原样返回，不会被当成业务数据解包。
 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // ⚠️ 必须释放。不释放的话这个 blob 会一直挂在页面内存里，
  // 反复导出几十次就是几十份文件大小的内存
  window.URL.revokeObjectURL(url)
}

/**
 * 文件名里用的当天日期（YYYY-MM-DD），**按本地时区**。
 *
 * <p>⚠️ 原来各处写的是 `new Date().toISOString().slice(0, 10)`，
 * 那个是 **UTC** 日期。中国是东八区，所以在早上 8 点之前导出，
 * 文件名会写成"昨天"—— 而且只有早班的人会遇到，很难被发现。
 * 这里按本地年月日拼，和用户看到的时间一致。
 */
export function today(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
