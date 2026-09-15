import { useState } from 'react'
import { api } from '../api'

function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

// 语义搜索：全新布局 —— 居中大搜索框 + 相似度徽章结果卡片
export default function SearchPage() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [searched, setSearched] = useState(false)
  const [loading, setLoading] = useState(false)

  function highlight(text) {
    const safeText = escapeHtml(text)
    const q = query.trim()
    if (!q) return safeText
    const safeQ = escapeHtml(q).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return safeText.replace(new RegExp(safeQ, 'gi'), (m) => `<mark class="ai-hl">${m}</mark>`)
  }

  async function doSearch() {
    if (!query.trim()) return
    setLoading(true)
    try {
      setResults((await api.get(`/api/search?q=${encodeURIComponent(query.trim())}`)) || [])
      setSearched(true)
    } catch (err) {
      if (err.status !== 401) alert(err.message)
    } finally {
      setLoading(false)
    }
  }

  const hot = Math.max(...results.map((r) => r.score), 0)

  return (
    <div className="ai-search-page">
      <div className="ai-search-hero">
        <h2>语义搜索</h2>
        <p>不只是关键词匹配，而是按语义相似度找到最相关的知识</p>
        <div className="ai-search-bar">
          <span className="ai-search-icon">🔍</span>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="输入关键词，例如：考勤、权限申请…"
            disabled={loading}
            onKeyDown={(e) => e.key === 'Enter' && doSearch()}
          />
          <button type="button" onClick={doSearch} disabled={loading || !query.trim()}>
            {loading ? '搜索中…' : '搜索'}
          </button>
        </div>
      </div>

      <div className="ai-results">
        {searched && results.length === 0 && (
          <div className="ai-results-empty">没有找到与「{query}」相关的内容，换个说法试试</div>
        )}

        {results.map((r) => (
          <article key={r.knowledgeId} className="ai-result-card">
            <div className="ai-result-score" data-level={r.score >= hot * 0.8 ? 'high' : 'normal'}>
              <b>{(r.score * 100).toFixed(0)}%</b>
              <span>相似度</span>
            </div>
            <div className="ai-result-body">
              <div className="ai-result-title">
                {/* 内容已经过 HTML 转义，仅插入高亮标签 */}
                <h4 dangerouslySetInnerHTML={{ __html: highlight(r.title) }} />
                {r.category && <span className="ai-result-tag">{r.category}</span>}
              </div>
              <p dangerouslySetInnerHTML={{ __html: highlight(r.snippet) }} />
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}
