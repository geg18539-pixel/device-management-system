<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, type LoginParams } from '../api/auth'
import { useAppStore } from '../stores/app'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive<LoginParams>({
  username: '',
  password: '',
})

// 校验规则和后端 dto/LoginRequest.java 上的注解保持一致
const rules: FormRules<LoginParams> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { max: 50, message: '用户名不能超过 50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, max: 64, message: '密码长度需在 4 到 64 个字符之间', trigger: 'blur' },
  ],
}

/** 从哪被拦下来的就回哪去，直接访问登录页则去默认首页 */
function targetOf(redirect: unknown): string {
  return typeof redirect === 'string' && redirect ? redirect : '/dashboard'
}

async function handleLogin() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({ ...form })

    // 先落状态再跳转：路由守卫会读 userStore.isLoggedIn，
    // 顺序反了会被守卫拦回登录页
    userStore.setLoginData(data)

    // 还在用初始密码（或密码已过期）→ 直接送去改密页。
    // 后端同样会拦住这类用户的所有业务接口（428），
    // 在这里拦只是为了不让用户先看到一堆报错的页面
    if (data.mustChangePassword) {
      ElMessage.warning('请先修改密码后再使用系统')
      await router.replace({
        path: '/change-password',
        query: { redirect: targetOf(route.query.redirect) },
      })
      return
    }

    ElMessage.success('登录成功')
    await router.replace(targetOf(route.query.redirect))
  } catch {
    // 失败提示已由 request.ts 的响应拦截器统一弹出（含"用户名或密码错误"），
    // 这里不重复弹，只负责不再往下走
  } finally {
    loading.value = false
  }
}

/** 开发时的快捷填充，省得每次手打 */
function fillDemo(username: string, password: string) {
  form.username = username
  form.password = password
}
</script>

<template>
  <div class="login-page">
    <!--
      左半屏：深色品牌区。
      刻意不用渐变背景 + 悬浮白卡那套 —— 那是 AI 生成登录页最典型的组合。
      这里给的是深色面板 + 一层极淡的工程网格（工业图纸的语汇，
      用 1px 线生成，不是装饰性渐变水洗）。
    -->
    <aside class="brand-side">
      <span class="brand-mark">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none"
             stroke="currentColor" stroke-width="1.6" aria-hidden="true">
          <rect x="3.5" y="4" width="17" height="6.5" rx="1.4" />
          <rect x="3.5" y="13.5" width="17" height="6.5" rx="1.4" />
          <circle cx="7" cy="7.25" r="1.05" fill="currentColor" stroke="none" />
          <circle cx="7" cy="16.75" r="1.05" fill="currentColor" stroke="none" />
        </svg>
      </span>

      <h1>{{ appStore.systemName }}</h1>
      <p>台账、调拨、维保、维修工单和配件库存，都在一个地方。</p>

      <div class="brand-foot">
        {{ appStore.companyName || '资产管理平台' }}
      </div>
    </aside>

    <!-- 右半屏：表单 -->
    <main class="form-side">
      <div class="form-box">
        <!-- 窄屏下品牌区会被隐藏，这里补一个精简版，免得登录页完全没有身份 -->
        <div class="narrow-brand">
          <span class="brand-mark">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none"
                 stroke="currentColor" stroke-width="1.6" aria-hidden="true">
              <rect x="3.5" y="4" width="17" height="6.5" rx="1.4" />
              <rect x="3.5" y="13.5" width="17" height="6.5" rx="1.4" />
              <circle cx="7" cy="7.25" r="1.05" fill="currentColor" stroke="none" />
              <circle cx="7" cy="16.75" r="1.05" fill="currentColor" stroke="none" />
            </svg>
          </span>
          <b>{{ appStore.systemName }}</b>
        </div>

        <h2>登录</h2>
        <p class="form-sub">请使用系统分配的账号登录</p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" clearable />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="submit"
              :loading="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中…' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="demo">
          <p class="demo-title">开发环境测试账号</p>
          <div class="demo-btns">
            <el-button size="small" @click="fillDemo('admin', 'admin123')">
              admin / admin123（超管）
            </el-button>
            <el-button size="small" @click="fillDemo('operator', 'operator123')">
              operator / operator123（普通）
            </el-button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  min-height: 100vh;
}

/* ---------- 品牌区 ---------- */
.brand-side {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 48px;
  background: var(--rail);
  color: var(--rail-text);
  overflow: hidden;
}

/* 极淡的工程网格。用 1px 线铺出来，比渐变有依据，也几乎不占视觉重量 */
.brand-side::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(255, 255, 255, .03) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, .03) 1px, transparent 1px);
  background-size: 28px 28px;
  pointer-events: none;
}

.brand-side > * {
  position: relative;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  color: var(--on-signal);
  background: var(--signal);
  border-radius: var(--r-control);
}

.brand-side h1 {
  margin: var(--sp-6) 0 0;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.3;
  letter-spacing: -.3px;
  color: var(--rail-hi);
}

.brand-side p {
  margin: var(--sp-3) 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--rail-dim);
  max-width: 30ch;
}

.brand-foot {
  margin-top: auto;
  padding-top: var(--sp-6);
  font-size: 12px;
  color: var(--rail-dim);
}

/* ---------- 表单区 ---------- */
.form-side {
  display: grid;
  place-items: center;
  padding: 32px;
  background: var(--surface);
}

.form-box {
  width: 100%;
  max-width: 340px;
}

.narrow-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--sp-6);
}

.narrow-brand .brand-mark {
  width: 28px;
  height: 28px;
}

.narrow-brand b {
  font-size: 15px;
  color: var(--ink-1);
}

.form-box h2 {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -.2px;
}

.form-sub {
  margin: var(--sp-1) 0 var(--sp-6);
  font-size: 13px;
  color: var(--ink-3);
}

.submit {
  width: 100%;
}

.demo {
  margin-top: var(--sp-5);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
  text-align: center;
}

.demo-title {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--ink-3);
}

.demo-btns {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-btns :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* ---------- 窄屏：藏掉品牌区，表单占满 ---------- */
@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-side {
    display: none;
  }

  .narrow-brand {
    display: flex;
  }
}
</style>
