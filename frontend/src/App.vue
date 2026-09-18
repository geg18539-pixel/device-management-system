<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from './stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/**
 * 登录页这类"裸页面"不套后台外壳。
 * 判断依据是路由的 meta.public，而不是硬编码 path === '/login'，
 * 以后加注册页、找回密码页只要标一下 meta 就行。
 */
const isBarePage = computed(() => route.meta.public === true)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string | undefined) ?? '设备管理系统')

interface MenuItem {
  title: string
  /** 允许看到这条菜单的角色 */
  roles: string[]
  path?: string
  children?: MenuItem[]
}

/**
 * 侧边栏菜单。
 *
 * 每条菜单声明允许的角色，渲染前按当前用户的角色过滤 ——
 * 这就是"静态路由 + 权限过滤"方案里菜单侧的实现。
 * 路由本身能不能进由 router/index.ts 的守卫把关，两者配合才完整。
 *
 * 注意：图标没有做。菜单表里虽然有 icon 字段，但渲染图标需要
 * @element-plus/icons-vue 这个包，项目里还没装。
 */
const allMenus: MenuItem[] = [
  { path: '/devices', title: '设备管理', roles: ['admin', 'operator'] },
  { path: '/device-repairs', title: '维修工单', roles: ['admin', 'operator'] },
  { path: '/hello', title: '联调测试', roles: ['admin', 'operator'] },
  {
    title: '系统管理',
    roles: ['admin'],
    children: [
      { path: '/system/users', title: '用户管理', roles: ['admin'] },
      { path: '/system/roles', title: '角色管理', roles: ['admin'] },
      { path: '/system/menus', title: '菜单管理', roles: ['admin'] },
    ],
  },
]

const menus = computed<MenuItem[]>(() => {
  const visible = (item: MenuItem) => item.roles.some((r) => userStore.roles.includes(r))

  return allMenus
    .map((item) => {
      if (!item.children) {
        return visible(item) ? item : null
      }
      // 父级先按子项过滤。子项全被过滤掉时整个目录也不显示 ——
      // 否则会出现一个点开是空的"系统管理"，看着像 bug
      const children = item.children.filter(visible)
      return children.length ? { ...item, children } : null
    })
    .filter((item): item is MenuItem => item !== null)
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
  } catch {
    // 点了取消
    return
  }

  userStore.logout()
  ElMessage.success('已退出登录')
  await router.replace('/login')
}

function handleCommand(command: string) {
  if (command === 'logout') {
    void handleLogout()
  }
}
</script>

<template>
  <!-- 裸页面（登录页）直接渲染，不套侧边栏 -->
  <router-view v-if="isBarePage" />

  <el-container v-else class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">设备管理系统</div>

      <el-menu :default-active="activeMenu" router class="menu">
        <template v-for="item in menus" :key="item.title">
          <!-- 有子菜单的渲染成可展开的目录 -->
          <el-sub-menu v-if="item.children?.length" :index="item.title">
            <template #title>{{ item.title }}</template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path as string"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>

          <el-menu-item v-else :index="item.path as string">
            {{ item.title }}
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ pageTitle }}</span>

        <el-dropdown @command="handleCommand">
          <span class="user">
            {{ userStore.nickname }}
            <span class="caret">▾</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                角色：{{ userStore.roles.join('、') || '无' }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid var(--border);
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-h);
  border-bottom: 1px solid var(--border);
}

.menu {
  border-right: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border);
  color: var(--text-h);
}

.page-title {
  font-size: 16px;
  font-weight: 500;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-h);
  outline: none;
}

.caret {
  font-size: 12px;
  color: #94a3b8;
}

.main {
  background: #f5f7fa;
}
</style>
