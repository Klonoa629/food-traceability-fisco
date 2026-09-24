<!-- 登录/注册页 -->
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { register } from '../api'
import { ROLE_NAMES } from '../constants/maps'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const tab = ref<'login' | 'register'>('login')

// 登录
const loginFormRef = ref<FormInstance>()
const loginForm = reactive({ username: '', password: '' })
const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const loginLoading = ref(false)

async function submitLogin() {
  await loginFormRef.value?.validate()
  loginLoading.value = true
  try {
    await auth.login(loginForm.username.trim(), loginForm.password)
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/')
  } finally {
    loginLoading.value = false
  }
}

// 注册
const regFormRef = ref<FormInstance>()
const regForm = reactive({ username: '', password: '', orgName: '', role: 1 })
const regRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^\w{3,32}$/, message: '3-32 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '6-64 位', trigger: 'blur' }
  ],
  orgName: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择机构类型', trigger: 'change' }]
}
const regLoading = ref(false)

async function submitRegister() {
  await regFormRef.value?.validate()
  regLoading.value = true
  try {
    await register({ ...regForm, username: regForm.username.trim() })
    ElMessage.success('注册成功,账户待监管机构审批后即可登录')
    tab.value = 'login'
    loginForm.username = regForm.username
  } finally {
    regLoading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="page-brand">
      <h1 class="brand-name">溯禾</h1>
      <p class="brand-sub">基于 FISCO BCOS 联盟链的食品溯源平台</p>
    </div>
    <div class="login-card ft-card">
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" size="large" @submit.prevent="submitLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" autocomplete="username" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" show-password autocomplete="current-password" @keyup.enter="submitLogin" />
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="loginLoading" @click="submitLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册机构账户" name="register">
          <el-form ref="regFormRef" :model="regForm" :rules="regRules" size="large" label-position="top">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="regForm.username" placeholder="3-32 位字母、数字或下划线" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="regForm.password" type="password" placeholder="6-64 位" show-password />
            </el-form-item>
            <el-form-item label="机构名称" prop="orgName">
              <el-input v-model="regForm.orgName" placeholder="如:示范农场" />
            </el-form-item>
            <el-form-item label="机构类型" prop="role">
              <el-select v-model="regForm.role" style="width: 100%">
                <el-option v-for="(name, role) in ROLE_NAMES" :key="role" :label="name" :value="Number(role)" />
              </el-select>
            </el-form-item>
            <el-button type="primary" size="large" style="width: 100%" :loading="regLoading" @click="submitRegister">
              提交注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <p class="login-hint muted">注册后需等待监管机构审批通过才可登录</p>
      <p class="trace-entry">
        <router-link to="/trace">消费者免登录溯源查询 →</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 34px;
  background:
    linear-gradient(rgba(20, 40, 26, 0.30), rgba(20, 40, 26, 0.18)),
    url('../assets/login-bg.jpg') center / cover no-repeat fixed,
    var(--ft-bg);
  padding: 24px;
}

.page-brand { text-align: center; }
.brand-name {
  margin: 0;
  font-size: 56px;
  font-weight: 800;
  letter-spacing: 14px;
  text-indent: 14px; /* 抵消末字间距,视觉居中 */
  color: #ffffff;
  text-shadow: 0 4px 24px rgba(10, 30, 18, 0.45);
}
.brand-sub {
  margin: 10px 0 0;
  font-size: 14px;
  letter-spacing: 3px;
  color: rgba(255, 255, 255, 0.88);
  text-shadow: 0 2px 10px rgba(10, 30, 18, 0.4);
}

.login-card {
  width: 420px;
  max-width: 100%;
  padding: 8px 36px 24px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(10px);
  box-shadow: 0 18px 50px rgba(10, 30, 18, 0.28);
}
.login-hint { text-align: center; font-size: 12px; margin: 18px 0 0; }
.trace-entry { text-align: center; margin: 10px 0 0; font-size: 13px; }
.trace-entry a { color: var(--ft-primary-deep); text-decoration: none; }
.trace-entry a:hover { text-decoration: underline; }
</style>
