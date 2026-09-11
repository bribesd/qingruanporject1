import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { api, setToken } from './api'
import Dashboard from './components/Dashboard'
import KnowledgePage from './components/KnowledgePage'
import UsersPage from './components/UsersPage'
import CategoriesPage from './components/CategoriesPage'
import QuestionsPage from './components/QuestionsPage'
import AuditLogsPage from './components/AuditLogsPage'

const PAGES = {
  dashboard: { title: '智能企业知识库管理与问答平台', subtitle: '后台管理中心' },
  knowledge: { title: '知识管理', subtitle: '维护企业知识库内容与状态' },
  users: { title: '用户管理', subtitle: '管理系统账号与权限' },
  categories: { title: '分类管理', subtitle: '维护知识分类结构' },
  questions: { title: '问答管理', subtitle: '处理用户提问与回答' },
  logs: { title: '审计日志', subtitle: '查看系统操作记录' },
}

const NAV_ITEMS = [
  { key: 'dashboard', label: '首页' },
  { key: 'knowledge', label: '知识管理' },
  { key: 'users', label: '用户管理' },
  { key: 'categories', label: '分类管理' },
  { key: 'questions', label: '问答管理' },
  { key: 'logs', label: '审计日志' },
]

function App() {
  const [loggedIn, setLoggedIn] = useState(() => !!localStorage.getItem('token'))
  const [currentUser, setCurrentUser] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem('user') || 'null')
    } catch {
      return null
    }
  })

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loginError, setLoginError] = useState('')
  const [loggingIn, setLoggingIn] = useState(false)
  const [activePage, setActivePage] = useState('dashboard')

  const handleLogout = useCallback(() => {
    setToken('')
    localStorage.removeItem('user')
    setLoggedIn(false)
    setCurrentUser(null)
    setActivePage('dashboard')
  }, [])

  // 任意接口返回 401 时，自动退出登录
  useEffect(() => {
    const onUnauthorized = () => handleLogout()
    window.addEventListener('auth:unauthorized', onUnauthorized)
    return () => window.removeEventListener('auth:unauthorized', onUnauthorized)
  }, [handleLogout])

  const handleLogin = async (event) => {
    event.preventDefault()
    setLoginError('')
    if (!username || !password) {
      setLoginError('请输入用户名和密码')
      return
    }
    setLoggingIn(true)
    try {
      const data = await api.post('/api/auth/login', { username, password })
      setToken(data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
      setCurrentUser(data.user)
      setLoggedIn(true)
    } catch (err) {
      setLoginError(err.message)
    } finally {
      setLoggingIn(false)
    }
  }

  const page = PAGES[activePage]
  const canWrite = ['super_admin', 'admin'].includes(currentUser?.role)
  const canManageUsers = canWrite
  const visibleNavItems = NAV_ITEMS.filter((item) => item.key !== 'users' || canManageUsers)

  useEffect(() => {
    if (activePage === 'users' && !canManageUsers) {
      setActivePage('dashboard')
    }
  }, [activePage, canManageUsers])

  const displayName = currentUser
    ? currentUser.realName || currentUser.username
    : username || '管理员'

  if (!loggedIn) {
    return (
      <div className="login-page">
        <div className="login-box">
          <div className="login-header">
            <div className="brand-mark">AI</div>
            <div>
              <h1>智慧知识库</h1>
              <p>企业管理后台</p>
            </div>
          </div>

          <form onSubmit={handleLogin} className="login-form">
            <label>
              <span>用户名</span>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="请输入用户名"
              />
            </label>

            <label>
              <span>密码</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="请输入密码"
              />
            </label>

            {loginError && (
              <small className="hint" style={{ color: '#dc2626' }}>
                {loginError}
              </small>
            )}

            <button type="submit" className="primary-btn" disabled={loggingIn}>
              {loggingIn ? '登录中…' : '登录系统'}
            </button>
          </form>
        </div>
      </div>
    )
  }

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">AI</div>
          <div>
            <h2>智慧知识库</h2>
            <small>企业管理后台</small>
          </div>
        </div>

        <nav className="nav">
          <p className="nav-title">菜单</p>
          {visibleNavItems.map((item) => (
            <button
              key={item.key}
              className={activePage === item.key ? 'nav-btn active' : 'nav-btn'}
              onClick={() => setActivePage(item.key)}
            >
              {item.label}
            </button>
          ))}
          <button className="nav-btn" onClick={handleLogout}>
            退出登录
          </button>
        </nav>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div>
            <h1>{page.title}</h1>
            <p>{page.subtitle}</p>
          </div>
          <div className="user-pill">管理员 · {displayName}</div>
        </header>

        {activePage === 'dashboard' && <Dashboard onNavigate={setActivePage} />}
        {activePage === 'knowledge' && <KnowledgePage canWrite={canWrite} />}
        {activePage === 'users' && canManageUsers && <UsersPage user={currentUser} canWrite={canWrite} />}
        {activePage === 'categories' && <CategoriesPage canWrite={canWrite} />}
        {activePage === 'questions' && <QuestionsPage canWrite={canWrite} />}
        {activePage === 'logs' && <AuditLogsPage />}
      </main>
    </div>
  )
}

export default App
