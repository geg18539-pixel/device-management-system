import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

import './style.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// Pinia 必须在 router 之前注册。
// 路由守卫里会调用 useUserStore()，如果那时还没有激活的 Pinia 实例，
// 会报 "getActivePinia() was called but there was no active Pinia"。
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
