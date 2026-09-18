<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, type LoginParams } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

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
    ElMessage.success('登录成功')

    // 从哪被拦下来的就回哪去，直接访问登录页则去默认首页
    const redirect = route.query.redirect
    const target = typeof redirect === 'string' && redirect ? redirect : '/devices'
    await router.replace(target)
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
    <div class="login-card">
      <div class="brand">
        <h1>设备管理系统</h1>
        <p>Device Management System</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" clearable>
            <template #prefix>
              <span class="icon">👤</span>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
          >
            <template #prefix>
              <span class="icon">🔒</span>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="submit"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中…' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="demo">
        <p class="demo-title">开发环境测试账号（点一下自动填入）</p>
        <div class="demo-btns">
          <el-button size="small" @click="fillDemo('admin', 'admin123')">
            admin / admin123（超管）
          </el-button>
          <el-button size="small" @click="fillDemo('operator', 'operator123')">
            operator / operator123（普通）
          </el-button>
        </div>
        <p class="demo-hint">
          账号由后端 DataInitializer 在空库时自动创建
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #eef2ff 0%, #f8fafc 45%, #ecfeff 100%);
}

.login-card {
  width: 380px;
  padding: 40px 36px 28px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.1);
}

.brand {
  margin-bottom: 28px;
  text-align: center;
}

.brand h1 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
}

.brand p {
  margin: 0;
  font-size: 13px;
  letter-spacing: 0.5px;
  color: #94a3b8;
}

.icon {
  font-size: 14px;
}

.submit {
  width: 100%;
  letter-spacing: 4px;
}

.demo {
  margin-top: 8px;
  padding-top: 18px;
  border-top: 1px dashed #e2e8f0;
  text-align: center;
}

.demo-title {
  margin: 0 0 10px;
  font-size: 12px;
  color: #94a3b8;
}

.demo-btns {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.demo-btns :deep(.el-button + .el-button) {
  margin-left: 0;
}

.demo-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #cbd5e1;
}
</style>
