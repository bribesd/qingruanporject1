import { useCallback, useEffect, useState } from 'react'
import { api } from '../api'
import { questionLabel } from '../shared'
import Badge from './Badge'

export default function QuestionsPage({ canWrite }) {
  const [questions, setQuestions] = useState([])
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState(null)
  const [answer, setAnswer] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(async () => {
    setQuestions((await api.get('/api/questions')) || [])
  }, [])

  useEffect(() => {
    load()
      .catch((err) => {
        if (err.status !== 401) alert(err.message)
      })
      .finally(() => setLoading(false))
  }, [load])

  async function openDetail(q) {
    try {
      setDetail(await api.get(`/api/questions/${q.id}`))
      setAnswer('')
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    }
  }

  async function submitAnswer() {
    if (!answer.trim()) {
      alert('回答内容不能为空')
      return
    }
    setSaving(true)
    try {
      await api.post(`/api/questions/${detail.id}/answers`, { content: answer })
      setAnswer('')
      setDetail(await api.get(`/api/questions/${detail.id}`))
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    } finally {
      setSaving(false)
    }
  }

  async function changeStatus(status) {
    try {
      await api.patch(`/api/questions/${detail.id}/status`, { status })
      setDetail(await api.get(`/api/questions/${detail.id}`))
      await load()
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    }
  }

  if (loading) {
    return <p style={{ color: '#64748b' }}>加载中…</p>
  }

  return (
    <>
      <section className="panel">
        <div className="panel-header">
          <h3>问题列表（{questions.length}）</h3>
        </div>

        <table className="data-table">
          <thead>
            <tr>
              <th>问题</th>
              <th>提问人</th>
              <th>状态</th>
              <th>回答数</th>
              <th style={{ width: 120 }}>操作</th>
            </tr>
          </thead>
          <tbody>
            {questions.map((q) => (
              <tr key={q.id}>
                <td>{q.title}</td>
                <td>{q.user_name || '匿名'}</td>
                <td>
                  <Badge tone={q.status === 'answered' ? 'ok' : 'warning'}>
                    {questionLabel(q.status)}
                  </Badge>
                </td>
                <td>{q.answer_count}</td>
                <td>
                  <button type="button" className="link-btn" onClick={() => openDetail(q)}>
                    查看详情
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {detail && (
        <div className="modal-overlay" onClick={() => setDetail(null)}>
          <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>问题详情</h3>
              <button type="button" className="modal-close" onClick={() => setDetail(null)}>
                ×
              </button>
            </div>

            <div className="modal-body">
              <div className="detail-head">
                <h4>{detail.title}</h4>
                <Badge tone={detail.status === 'answered' ? 'ok' : 'warning'}>
                  {questionLabel(detail.status)}
                </Badge>
              </div>
              <p className="detail-meta">
                提问人：{detail.user_name || '匿名'} · {(detail.created_at || '').slice(0, 16)}
              </p>
              <p className="detail-content">{detail.content}</p>

              <h5>回答（{detail.answers?.length || 0}）</h5>
              {(!detail.answers || detail.answers.length === 0) && (
                <p className="empty">暂无回答</p>
              )}
              {detail.answers?.map((a) => (
                <div key={a.id} className="answer-item">
                  <p>{a.content}</p>
                  <small>
                    {a.author_name || '未知'} · {(a.created_at || '').slice(0, 16)}
                  </small>
                </div>
              ))}

              {canWrite && (
                <>
                  <h5>提交回答</h5>
                  <textarea
                    value={answer}
                    onChange={(e) => setAnswer(e.target.value)}
                    rows="3"
                    placeholder="请输入回答内容"
                  />
                </>
              )}
            </div>

            {canWrite && (
              <div className="form-actions">
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={() =>
                    changeStatus(detail.status === 'answered' ? 'open' : 'answered')
                  }
                >
                  {detail.status === 'answered' ? '标记为待处理' : '标记为已回答'}
                </button>
                <button type="button" className="primary-btn" disabled={saving} onClick={submitAnswer}>
                  {saving ? '提交中…' : '提交回答'}
                </button>
              </div>
            )}
          </div>
        </div>
      )}
    </>
  )
}
