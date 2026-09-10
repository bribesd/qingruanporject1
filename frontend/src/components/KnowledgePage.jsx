import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import { statusLabel } from '../shared'
import Badge from './Badge'

export default function KnowledgePage({ canWrite }) {
  const [categories, setCategories] = useState([])
  const [knowledge, setKnowledge] = useState([])
  const [filter, setFilter] = useState('全部')
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({ title: '', categoryId: '', status: 'draft', content: '' })
  const [saving, setSaving] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    const [categoryList, knowledgeList] = await Promise.all([
      api.get('/api/categories'),
      api.get('/api/knowledge'),
    ])
    setCategories(categoryList || [])
    setKnowledge(knowledgeList || [])
  }, [])

  useEffect(() => {
    load()
      .catch((err) => {
        if (err.status !== 401) alert(err.message)
      })
      .finally(() => setLoading(false))
  }, [load])

  function resetForm() {
    setEditingId(null)
    setForm({ title: '', categoryId: '', status: 'draft', content: '' })
  }

  function openEdit(item) {
    setEditingId(item.id)
    setForm({
      title: item.title,
      categoryId: item.category_id ?? '',
      status: item.status || 'draft',
      content: item.content || '',
    })
  }

  function handleChange(event) {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!form.title || !form.content) {
      alert('标题和内容不能为空')
      return
    }
    setSaving(true)
    try {
      const payload = {
        title: form.title,
        content: form.content,
        categoryId: form.categoryId ? Number(form.categoryId) : null,
        status: form.status,
      }
      if (editingId) {
        await api.put(`/api/knowledge/${editingId}`, payload)
      } else {
        await api.post('/api/knowledge', payload)
      }
      resetForm()
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(item) {
    if (!window.confirm(`确定删除知识「${item.title}」吗？`)) return
    try {
      await api.del(`/api/knowledge/${item.id}`)
      if (editingId === item.id) resetForm()
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    }
  }

  const filteredKnowledge = useMemo(() => {
    if (filter === '全部') return knowledge
    return knowledge.filter((item) => statusLabel(item.status) === filter)
  }, [filter, knowledge])

  if (loading) {
    return <p style={{ color: '#64748b' }}>加载中…</p>
  }

  return (
    <section className={canWrite ? 'knowledge-layout' : 'knowledge-layout single'}>
      {canWrite && (
        <div className="panel knowledge-form-panel">
          <div className="panel-header">
            <h3>{editingId ? '编辑知识' : '新增知识'}</h3>
            {editingId && (
              <button type="button" className="link-btn" onClick={resetForm}>
                取消编辑
              </button>
            )}
          </div>

          <form className="knowledge-form" onSubmit={handleSubmit}>
            <label>
              <span>标题</span>
              <input
                name="title"
                value={form.title}
                onChange={handleChange}
                placeholder="请输入知识标题"
              />
            </label>

            <div className="two-col">
              <label>
                <span>分类</span>
                <select name="categoryId" value={form.categoryId} onChange={handleChange}>
                  <option value="">未分类</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                <span>状态</span>
                <select name="status" value={form.status} onChange={handleChange}>
                  <option value="draft">草稿</option>
                  <option value="published">已发布</option>
                </select>
              </label>
            </div>

            <label>
              <span>内容</span>
              <textarea
                name="content"
                value={form.content}
                onChange={handleChange}
                rows="7"
                placeholder="请输入知识内容"
              />
            </label>

            <button type="submit" className="primary-btn" disabled={saving}>
              {saving ? '保存中…' : editingId ? '更新知识' : '保存知识'}
            </button>
          </form>
        </div>
      )}

      <div className="panel knowledge-list-panel">
        <div className="panel-header list-header">
          <h3>知识列表</h3>
          <div className="filter-row">
            {['全部', '草稿', '已发布'].map((item) => (
              <button
                key={item}
                type="button"
                className={filter === item ? 'chip active' : 'chip'}
                onClick={() => setFilter(item)}
              >
                {item}
              </button>
            ))}
          </div>
        </div>

        <div className="knowledge-list">
          {filteredKnowledge.length === 0 && <p className="empty">暂无知识条目</p>}
          {filteredKnowledge.map((item) => (
            <article key={item.id} className="knowledge-item">
              <div className="knowledge-row">
                <div>
                  <h4>{item.title}</h4>
                  <p>
                    {item.category || '未分类'} · {item.author || '未知'}
                  </p>
                </div>
                <Badge tone={item.status === 'published' ? 'ok' : 'muted'}>
                  {statusLabel(item.status)}
                </Badge>
              </div>
              <p className="knowledge-summary">{item.content}</p>
              <div className="knowledge-meta">
                <span>更新：{(item.updated_at || item.created_at || '').slice(0, 10)}</span>
                {canWrite && (
                  <div>
                    <button type="button" className="link-btn" onClick={() => openEdit(item)}>
                      编辑
                    </button>
                    <button type="button" className="link-btn danger" onClick={() => handleDelete(item)}>
                      删除
                    </button>
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
