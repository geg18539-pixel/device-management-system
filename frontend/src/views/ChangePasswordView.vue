<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { changePassword, getCurrentUser } from '../api/auth'
import { PASSWORD_RULE_TEXT, checkPasswordStrength } from '../api/user'
import DataPanel from '../components/DataPanel.vue'
import { useUserStore } from '../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

/**
 * 前端校验规则和后端 PasswordPolicy 保持一致。
 *
 * <p>文案直接用 api/user.ts 里那份，不在这里再抄一遍 ——
 * 抄一遍的结果是两边迟早不一致，而用户会以为是 bug。
 */
const rules: FormRules<typeof form> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    {
      validator: (_rule, value: string, callback) => {
        const error = checkPasswordStrength(value)
        if (error) {
          callback(new Error(error))
          return
        }
        if (value === form.oldPassword) {
          callback(new Error('新密码不能与原密码相同'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 是"首次登录/被管理员重置"还是"密码过期"，提示文案不一样 */
const reason = ref('')

onMounted(async () => {
  // 先拿一次当前状态，好判断提示哪种文案。
  // 失败也不影响改密本身，所以吞掉异常
  try {
    const me = await getCurrentUser()
    reason.value =
      me.passwordExpireDays != null && me.passwordExpireDays <= 0
        ? '您的密码已过期，请设置新密码后继续使用'
        : '为了账号安全，请先修改初始密码'
  } catch {
    reason.value = '请先修改密码后继续使用'
  }
})

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    // 用返回的新 token 重建登录态：旧 token 里还挂着"需要改密"的标记，
    // 不换的话下一个请求立刻又会被拦回来
    userStore.setLoginData(result)
    ElMessage.success('密码修改成功')

    const redirect = (route.query.redirect as string | undefined) || '/dashboard'
    await router.replace(redirect)
  } catch {
    // 原密码错误等提示已由拦截器统一弹出
    await nextTick()
  } finally {
    submitting.value = false
  }
}

async function handleLogout() {
  userStore.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="wrap">
    <DataPanel class="card">
      <div class="head">
        <div class="title">修改密码</div>
        <div class="sub">{{ userStore.nickname }}</div>
      </div>

      <el-alert type="warning" :closable="false" show-icon class="alert">
        {{ reason }}
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="form">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="form.oldPassword"
            type="password"
            show-password
            placeholder="当前使用的密码"
            maxlength="64"
          />
        </el-form-item>

        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            :placeholder="PASSWORD_RULE_TEXT"
            maxlength="64"
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="再输一次新密码"
            maxlength="64"
          />
        </el-form-item>
      </el-form>

      <div class="rule">{{ PASSWORD_RULE_TEXT }}</div>

      <div class="footer">
        <el-button link type="info" @click="handleLogout">退出登录</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
      </div>
    </DataPanel>
  </div>
</template>

<style scoped>
.wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--canvas);
  padding: 24px;
}

.card {
  width: 460px;
  text-align: left;
}

/* 这是个独立的居中卡片，比普通面板需要更宽松的内边距 */
.card :deep(.panel-body) {
  padding: 24px;
}

.head {
  margin-bottom: 16px;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ink-1);
}

.sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ink-3);
}

.alert {
  margin-bottom: 18px;
}

.form {
  margin-top: 4px;
}

.rule {
  margin: -4px 0 18px 90px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
}

.footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
