import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'

export default function CategoriesPage({ canWrite }) {
  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ name: '', parentId: '' })
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setCategories((await api.get('/api/categories')) || [])
  }, [])

  useEffect(() => {
    load()
      .catch((err) => {
        if (err.status !== 401) alert(err.message)
      })
      .finally(() => setLoading(false))
  }, [load])

  function openCreate() {
    setEditingId(null)
    setForm({ name: '', parentId: '' })
    setShowForm(true)
  }

  function openEdit(c) {
    setEditingId(c.id)
    setForm({ name: c.name, parentId: c.parent_id ?? '' })
    setShowForm(true)
  }

  function handleChange(event) {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSave(event) {
    event.preventDefault()
    if (!form.name) {
      alert('分类名称不能为空')
      return
    }
    setSaving(true)
    try {
      if (editingId) {
        await api.put(`/api/categories/${editingId}`, { name: form.name })
      } else {
        await api.post('/api/categories', {
          name: form.name,
          parentId: form.parentId ? Number(form.parentId) : null,
        })
      }
      setShowForm(false)
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(c) {
    if (!window.confirm(`确定删除分类「${c.name}」吗？`)) return
    try {
      await api.del(`/api/categories/${c.id}`)
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
        <h3>分类列表（{categories.length}）</h3>
        {canWrite && (
          <button type="button" onClick={openCreate}>
            新增分类
          </button>
        )}
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th>分类名称</th>
            <th>父分类</th>
            <th>知识条目数</th>
            <th style={{ width: 120 }}>操作</th>
          </tr>
        </thead>
        <tbody>
          {categories.map((c) => (
            <tr key={c.id}>
              <td>{c.name}</td>
              <td>{c.parent_id ? '（有父级）' : '顶级分类'}</td>
              <td>{c.knowledge_count}</td>
              <td>
                {canWrite && (
                  <>
                    <button type="button" className="link-btn" onClick={() => openEdit(c)}>
                      编辑
                    </button>
                    <button type="button" className="link-btn danger" onClick={() => handleDelete(c)}>
                      删除
                    </button>
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
              <h3>{editingId ? '编辑分类' : '新增分类'}</h3>
              <button type="button" className="modal-close" onClick={() => setShowForm(false)}>
                ×
              </button>
            </div>

            <form className="knowledge-form" onSubmit={handleSave}>
              <label>
                <span>分类名称</span>
                <input
                  name="name"
                  value={form.name}
                  onChange={handleChange}
                  placeholder="请输入分类名称"
                />
              </label>

              <label>
                <span>父分类（可选）</span>
                <select name="parentId" value={form.parentId} onChange={handleChange}>
                  <option value="">顶级分类</option>
                  {categories
                    .filter((c) => c.id !== editingId)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </label>

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
