import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig, type Plugin } from 'vite'

/**
 * 启动前确认根目录下有 index.html。
 *
 * <h3>⚠️ 为什么需要这个</h3>
 *
 * <p>**Vite 是以「启动时的当前目录」为根的**，不是以 vite.config.ts 所在目录为根。
 * 所以在没有 index.html 的目录里启动（最容易踩的是**仓库根目录**
 * {@code F:\device-management-system}）会出现一种非常难查的现象：
 *
 * <pre>
 *   ➜  Local:   http://localhost:5173/     ← 和正常时一模一样
 *   浏览器访问 / 或 /login → 404
 * </pre>
 *
 * <p>更坑的是**那个 404 的响应体是空的、Content-Type 也是空的** ——
 * 浏览器里只看到一句"404"，没有任何线索指向"你起错目录了"。
 *
 * <p>（正常工作时，浏览器带 {@code Accept: text/html} 请求任何路径，
 * Vite 都会回退到 index.html 并返回 200；所以"文档本身 404"
 * 只可能是根目录下没有 index.html。）
 *
 * <p>这道守卫用 {@code configResolved} 而不是直接读 {@code process.cwd()}：
 * 拿的是 Vite **解析之后**的 root，所以有人用 {@code vite --root xxx}
 * 指定别处时也不会误报。
 */
function requireRootIndex(): Plugin {
  return {
    name: 'dms:require-root-index',
    configResolved(config) {
      const indexHtml = resolve(config.root, 'index.html')
      if (existsSync(indexHtml)) {
        return
      }
      throw new Error(
        `\n\n[启动目录不对] 在 ${config.root} 下没有找到 index.html。\n\n` +
          'Vite 是以「你执行命令时所在的目录」为根的，不是以 vite.config.ts 的位置为根。\n' +
          '在仓库根目录跑会让所有页面都返回空白的 404（而且日志看起来完全正常）。\n\n' +
          '请先进入正确目录再启动：\n' +
          '    cd frontend\n' +
          '    pnpm dev\n\n',
      )
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [requireRootIndex(), vue()],
  server: {
    proxy: {
      // 前端所有以 /api 开头的请求都转发给后端
      //
      // 这里故意不写 rewrite。后端接口本身就是 /api/hello，
      // 路径要原样透传；如果加了 rewrite 把 /api 去掉，
      // 后端就得提供 /hello 才能匹配上。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
