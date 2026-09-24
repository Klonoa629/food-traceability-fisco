<!-- 布局骨架:品牌顶栏 + 导航 + 用户信息 -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ROLE_NAMES } from '../constants/maps'
import { ElMessageBox } from 'element-plus'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  await ElMessageBox.confirm('确定退出登录吗?', '退出', { type: 'warning' })
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-brand">
        <span class="leaf">禾</span>
        <span>溯禾 · 食品溯源平台</span>
      </div>
      <router-link class="nav-link" to="/">产品工作台</router-link>
      <router-link v-if="auth.isRegulator" class="nav-link" to="/admin">监管控制台</router-link>
      <div class="spacer" />
      <slot name="header-extra" />
      <el-tag v-if="auth.user" type="success" effect="plain" round>
        {{ auth.user.orgName }} · {{ ROLE_NAMES[auth.user.role] ?? (auth.isRegulator ? '监管机构' : '') }}
      </el-tag>
      <el-button text @click="logout">退出登录</el-button>
    </header>
    <main class="app-main">
      <slot />
    </main>
  </div>
</template>
