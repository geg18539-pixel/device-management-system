import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
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
