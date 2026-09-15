import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { roleLabel, userStatusLabel } from '../shared'
import Badge from './Badge'

const emptyForm = {
  username: '',
  password: '',
  realName: '',
  email: '',
  roleId: '',
  status: 'active',
}

export default function UsersPage({ user: currentUser, canWrite }) {
  const [users, setUsers] = useState([])
  const [roles, setRoles] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)

  if (!canWrite) {
    return <p style={{ color: '#64748b' }}>无权限查看用户列表</p>
  }
  const canManageRoles = currentUser?.role === 'super_admin'
  const editingUser = users.find((u) => u.id === editingId)
  const canEditSensitiveFields =
    canManageRoles && (!editingUser || editingUser.role !== 'super_admin' || editingUser.id === currentUser.id)

  const load = useCallback(async () => {
    const [userList, roleList] = await Promise.all([api.get('/api/users'), api.get('/api/roles')])
    setUsers(userList || [])
    setRoles(roleList || [])
  }, [])

  useEffect(() => {
    load()
      .catch((err) => {
        if (err.status !== 401) alert(err.message)
      })
      .finally(() => setLoading(false))
  }, [load])

  function openCreate() {
    const defaultRoleId = roles.find((r) => r.name === 'user')?.id ?? ''
    setEditingId(null)
    setForm({ ...emptyForm, roleId: defaultRoleId })
    setShowForm(true)
  }

  function openEdit(u) {
    setEditingId(u.id)
    setForm({
      username: u.username,
      password: '',
      realName: u.real_name || '',
      email: u.email || '',
      roleId: u.role_id ?? '',
      status: u.status || 'active',
    })
    setShowForm(true)
  }

  function handleChange(event) {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSave(event) {
    event.preventDefault()
    if (!editingId && (!form.username || !form.password)) {
      alert('用户名和密码不能为空')
      return
    }
    setSaving(true)
    try {
      const payload = {
        realName: form.realName,
        email: form.email,
        status: form.status,
      }
      if (canManageRoles) {
        payload.roleId = form.roleId ? Number(form.roleId) : null
      }
      if (editingId) {
        await api.put(`/api/users/${editingId}`, payload)
      } else {
        await api.post('/api/users', { ...payload, username: form.username, password: form.password })
      }
      setShowForm(false)
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(u) {
    if (currentUser && u.id === currentUser.id) {
      alert('不能删除当前登录的账号')
      return
    }
    if (!window.confirm(`确定删除用户「${u.username}」吗？`)) return
    try {
      await api.del(`/api/users/${u.id}`)
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    }
  }

  if (loading) {
    return <p style={{ color: '#64748b' }}>加载中…</p>
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <h3>用户列表（{users.length}）</h3>
        {canWrite && (
          <button type="button" onClick={openCreate}>
            新增用户
          </button>
        )}
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>真实姓名</th>
            <th>邮箱</th>
            <th>角色</th>
            <th>状态</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.username}</td>
              <td>{u.real_name || '—'}</td>
              <td>{u.email || '—'}</td>
              <td>{roleLabel(u.role)}</td>
              <td>
                <Badge tone={u.status === 'active' ? 'ok' : 'muted'}>
                  {userStatusLabel(u.status)}
                </Badge>
              </td>
              <td>
                {canWrite && (
                  <>
                    <button type="button" className="link-btn" onClick={() => openEdit(u)}>
                      编辑
                    </button>
                    {u.role !== 'super_admin' && (
                      <button type="button" className="link-btn danger" onClick={() => handleDelete(u)}>
                        删除
                      </button>
                    )}
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {showForm && (
        <div className="modal-overlay" onClick={() => setShowForm(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingId ? '编辑用户' : '新增用户'}</h3>
              <button type="button" className="modal-close" onClick={() => setShowForm(false)}>
                ×
              </button>
            </div>

            <form className="knowledge-form" onSubmit={handleSave}>
              <label>
                <span>用户名</span>
                <input
                  name="username"
                  value={form.username}
                  onChange={handleChange}
                  disabled={!!editingId}
                  placeholder="请输入用户名"
                />
              </label>

              {!editingId && (
                <label>
                  <span>密码</span>
                  <input
                    type="password"
                    name="password"
                    value={form.password}
                    onChange={handleChange}
                    placeholder="请输入密码"
                  />
                </label>
              )}

              <div className="two-col">
                <label>
                  <span>真实姓名</span>
                  <input
                    name="realName"
                    value={form.realName}
                    onChange={handleChange}
                    placeholder="真实姓名"
                  />
                </label>
                <label>
                  <span>邮箱</span>
                  <input
                    name="email"
                    value={form.email}
                    onChange={handleChange}
                    placeholder="邮箱"
                  />
                </label>
              </div>

              <div className="two-col">
                <label>
                  <span>角色</span>
                  <select
                    name="roleId"
                    value={form.roleId}
                    onChange={handleChange}
                    disabled={!canEditSensitiveFields}
                  >
                    <option value="">请选择角色</option>
                    {roles.map((r) => (
                      <option key={r.id} value={r.id}>
                        {roleLabel(r.name)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>状态</span>
                  <select
                    name="status"
                    value={form.status}
                    onChange={handleChange}
                    disabled={!canEditSensitiveFields}
                  >
                    <option value="active">启用</option>
                    <option value="disabled">禁用</option>
                  </select>
                </label>
              </div>

              <div className="form-actions">
                <button type="button" className="secondary-btn" onClick={() => setShowForm(false)}>
                  取消
                </button>
                <button type="submit" className="primary-btn" disabled={saving}>
                  {saving ? '保存中…' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </section>
  )
}
