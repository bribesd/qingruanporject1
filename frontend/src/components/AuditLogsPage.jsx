import { useEffect, useState } from 'react'
import { api } from '../api'

const ACTION_LABEL = {
  login: '登录',
  create_user: '创建用户',
  update_user: '更新用户',
  delete_user: '删除用户',
  create_category: '创建分类',
  update_category: '更新分类',
  delete_category: '删除分类',
  create_knowledge: '创建知识',
  update_knowledge: '更新知识',
  delete_knowledge: '删除知识',
  create_question: '提交问题',
  answer_question: '回答问题',
  update_question_status: '更新问题状态',
}

export default function AuditLogsPage() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    ;(async () => {
      try {
        setLogs((await api.get('/api/audit-logs')) || [])
      } catch (err) {
        if (err.status !== 401) alert(err.message)
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  if (loading) {
    return <p style={{ color: '#64748b' }}>加载中…</p>
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <h3>操作记录（{logs.length}）</h3>
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th style={{ width: 180 }}>时间</th>
            <th style={{ width: 140 }}>操作人</th>
            <th style={{ width: 160 }}>操作</th>
            <th>详情</th>
          </tr>
        </thead>
        <tbody>
          {logs.length === 0 && (
            <tr>
              <td colSpan={4} className="empty">
                暂无操作记录
              </td>
            </tr>
          )}
          {logs.map((log) => (
            <tr key={log.id}>
              <td>{(log.created_at || '').slice(0, 16)}</td>
              <td>{log.user_name || '—'}</td>
              <td>{ACTION_LABEL[log.action] || log.action}</td>
              <td>{log.detail || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
