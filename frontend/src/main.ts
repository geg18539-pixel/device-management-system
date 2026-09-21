import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'

// ── 样式引入顺序不能动 ──────────────────────────────────────────
// 1) EP 基础样式 —— 定义 :root 下的 --el-* 变量
// 2) EP 暗色变量 —— 定义 html.dark 下的 --el-* 变量
// 3) 我们的设计 token
// 4) EP 变量重映射 —— **必须在 1、2 之后**，同优先级下后引入的胜出，
//    顺序反了 EP 的默认蓝会盖回来，而且不报错
// 5) 基础重置与全局工具类
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/tokens.css'
import './styles/element-override.css'
import './styles/base.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

// Pinia 必须在 router 之前注册。
// 路由守卫里会调用 useUserStore()，如果那时还没有激活的 Pinia 实例，
// 会报 "getActivePinia() was called but there was no active Pinia"。
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 注意：这里**没有**注册按钮权限指令。
//
// 原来有个 v-perm 指令，在 mounted 里把无权限的元素 removeChild 掉。
// 但自定义指令挂不到多根节点的组件上 —— Vue 会把指令塞到根 vnode 上，
// 而 Element Plus 的 el-button / el-dropdown-item 根节点都是 Fragment，
// 于是指令拿到的是锚点注释节点，删掉它会把 Vue 的 DOM 结构搞乱
// （表现为"按钮点了没反应"，而且不报错）。
//
// 现在统一用 v-if="hasPerm('xxx')"（见 composables/usePerm.ts），
// 是 Vue 原生的条件渲染，任何组件都能用。

app.mount('#app')
