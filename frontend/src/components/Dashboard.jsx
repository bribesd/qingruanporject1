import { useEffect, useState } from 'react'
import { api } from '../api'
import { statusLabel, questionLabel } from '../shared'
import Badge from './Badge'

export default function Dashboard({ onNavigate }) {
  const [stats, setStats] = useState([])
  const [categoryHeat, setCategoryHeat] = useState([])
  const [recentKnowledge, setRecentKnowledge] = useState([])
  const [recentQuestions, setRecentQuestions] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const [summary, categories, questions] = await Promise.all([
          api.get('/api/dashboard/summary'),
          api.get('/api/categories'),
          api.get('/api/questions'),
        ])
        if (!alive) return
        setStats([
          { label: '知识条目', value: summary.knowledgeCount },
          { label: '用户总数', value: summary.userCount },
          { label: '问题总数', value: summary.questionCount },
          { label: '待处理问题', value: summary.openQuestionCount },
        ])
        setRecentKnowledge(summary.recentKnowledge || [])
        setRecentQuestions((questions || []).slice(0, 5))
        setCategoryHeat(
          (categories || []).map((c) => ({ name: c.name, count: c.knowledge_count || 0 })),
        )
      } catch (err) {
        if (err.status !== 401) alert(err.message)
      } finally {
        if (alive) setLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [])

  const maxHeat = Math.max(1, ...categoryHeat.map((c) => c.count))

  if (loading) {
    return <p style={{ color: '#64748b' }}>加载中…</p>
  }

  return (
    <>
      <section className="stats-grid">
        {stats.map((item) => (
          <div key={item.label} className="stat-card">
            <span>{item.label}</span>
            <strong>{item.value}</strong>
          </div>
        ))}
      </section>

      <section className="content-grid">
        <div className="panel">
          <div className="panel-header">
            <h3>知识分类热度</h3>
            <button type="button" onClick={() => onNavigate('categories')}>
              查看全部
            </button>
          </div>
          <ul className="category-list">
            {categoryHeat.map((item) => (
              <li key={item.name}>
                <span>{item.name}</span>
                <div className="progress-wrap">
                  <div
                    className="progress"
                    style={{ width: `${(item.count / maxHeat) * 100}%` }}
                  />
                </div>
                <b>{item.count}</b>
              </li>
            ))}
          </ul>
        </div>

        <div className="panel">
          <div className="panel-header">
            <h3>最近知识更新</h3>
            <button type="button" onClick={() => onNavigate('knowledge')}>
              新增知识
            </button>
          </div>
          <ul className="list-block">
            {recentKnowledge.slice(0, 4).map((item) => (
              <li key={item.id}>
                <div>
                  <strong>{item.title}</strong>
                  <small>{item.category || '未分类'}</small>
                </div>
                <Badge tone={item.status === 'published' ? 'ok' : 'muted'}>
                  {statusLabel(item.status)}
                </Badge>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="panel full-panel">
        <div className="panel-header">
          <h3>最新问答问题</h3>
          <button type="button" onClick={() => onNavigate('questions')}>
            处理问答
          </button>
        </div>
        <table>
          <thead>
            <tr>
              <th>问题</th>
              <th>提问人</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            {recentQuestions.map((item) => (
              <tr key={item.id}>
                <td>{item.title}</td>
                <td>{item.user_name || '匿名'}</td>
                <td>
                  <Badge tone={item.status === 'answered' ? 'ok' : 'warning'}>
                    {questionLabel(item.status)}
                  </Badge>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  )
}
