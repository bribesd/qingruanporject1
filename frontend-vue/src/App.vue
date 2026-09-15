<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { api, setToken } from './api'
import KnowledgeBaseManager from './components/KnowledgeBaseManager.vue'
import ChatWindow from './components/ChatWindow.vue'
import SearchResults from './components/SearchResults.vue'

const pages = {
  knowledge: { label: '知识库管理', title: '知识库管理', component: KnowledgeBaseManager },
  chat: { label: '智能问答', title: 'AI 智能问答', component: ChatWindow },
  search: { label: '语义搜索', title: '知识语义搜索', component: SearchResults },
}

const loggedIn = ref(!!localStorage.getItem('token'))
const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const username = ref('')
const password = ref('')
const loginError = ref('')
const loggingIn = ref(false)
const activePage = ref('knowledge')

async function handleLogin() {
  loginError.value = ''
  if (!username.value || !password.value) {
    loginError.value = '请输入用户名和密码'
    return
  }
  loggingIn.value = true
  try {
    const data = await api.post('/api/auth/login', { username: username.value, password: password.value })
    setToken(data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    user.value = data.user
    loggedIn.value = true
  } catch (err) {
    loginError.value = err.message
  } finally {
    loggingIn.value = false
  }
}

function handleLogout() {
  setToken('')
  localStorage.removeItem('user')
  user.value = null
  loggedIn.value = false
}

const onUnauthorized = () => handleLogout()
onMounted(() => window.addEventListener('auth:unauthorized', onUnauthorized))
onUnmounted(() => window.removeEventListener('auth:unauthorized', onUnauthorized))
</script>

<template>
  <div v-if="!loggedIn" class="login-page">
    <form class="login-box" @submit.prevent="handleLogin">
      <h1>智慧知识库 · AI 问答平台</h1>
      <label>
        <span>用户名</span>
        <input v-model="username" placeholder="请输入用户名" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" placeholder="请输入密码" />
      </label>
      <small v-if="loginError" style="color: #dc2626">{{ loginError }}</small>
      <button class="primary-btn" type="submit" :disabled="loggingIn">
        {{ loggingIn ? '登录中…' : '登录系统' }}
      </button>
    </form>
  </div>

  <div v-else class="admin-shell">
    <aside class="sidebar">
      <h2>智慧知识库</h2>
      <button
        v-for="(page, key) in pages"
        :key="key"
        class="nav-btn"
        :class="{ active: activePage === key }"
        @click="activePage = key"
      >
        {{ page.label }}
      </button>
      <button class="nav-btn" @click="handleLogout">退出登录</button>
    </aside>
    <main class="main-panel">
      <div class="panel-header">
        <h2>{{ pages[activePage].title }}</h2>
        <small>{{ user?.realName || user?.username }}</small>
      </div>
      <component :is="pages[activePage].component" :user="user" />
    </main>
  </div>
</template>
