import { useEffect, useRef, useState } from 'react'
import { api } from '../api'

const SUGGESTIONS = [
  '公司的考勤制度是什么？',
  '如何申请账号权限？',
  '客户问题的处理流程',
  '密码安全有哪些要求？',
]

let msgSeq = 0

// AI 智能问答：全新布局 —— 顶部渐变横幅 + 建议问题 + 居中对话流 + 常驻输入区
export default function AiChatPage() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const bottomRef = useRef(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  function appendMessage(msg) {
    setMessages((prev) => [...prev, { id: ++msgSeq, time: new Date(), ...msg }])
  }

  function patchLastMessage(patch) {
    setMessages((prev) => {
      const next = [...prev]
      Object.assign(next[next.length - 1], patch)
      return next
    })
  }

  async function ask(question) {
    if (!question || sending) return
    setInput('')
    setSending(true)
    appendMessage({ role: 'user', content: question })
    appendMessage({ role: 'assistant', content: '思考中…', sources: [], loading: true })
    try {
      const data = await api.post('/api/qa/ask', { question })
      patchLastMessage({
        content: data.answer,
        sources: data.sources || [],
        loading: false,
      })
    } catch (err) {
      patchLastMessage({
        content: err.status === 401 ? '登录已过期，请重新登录' : err.message || '请求失败',
        sources: [],
        loading: false,
      })
    } finally {
      setSending(false)
    }
  }

  const isEmpty = messages.length === 0

  return (
    <div className="ai-chat-page">
      <div className="ai-chat-hero">
        <div className="ai-chat-hero-text">
          <h2>有什么可以帮你？</h2>
          <p>回答基于企业知识库内容生成，并附引用来源</p>
        </div>
      </div>

      {isEmpty && (
        <div className="ai-suggestions">
          {SUGGESTIONS.map((s) => (
            <button key={s} type="button" className="ai-suggestion" onClick={() => ask(s)}>
              {s}
            </button>
          ))}
        </div>
      )}

      <div className="ai-chat-scroll">
        <div className="ai-chat-thread">
          {messages.map((msg) => (
            <div key={msg.id} className={`ai-msg ${msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-bot'}`}>
              {msg.role === 'assistant' && (
                <div className="ai-avatar" aria-hidden="true">
                  AI
                </div>
              )}
              <div className="ai-bubble">
                <p className="ai-bubble-text">{msg.content}</p>
                {msg.sources?.length > 0 && (
                  <div className="ai-cites">
                    {msg.sources.map((s) => (
                      <span key={s.knowledgeId} className="ai-cite">
                        {s.title}
                        <i>{(s.score * 100).toFixed(0)}%</i>
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
      </div>

      <div className="ai-composer">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && ask(input.trim())}
          placeholder="输入你的问题，回车发送"
          disabled={sending}
        />
        <button type="button" onClick={() => ask(input.trim())} disabled={sending || !input.trim()}>
          发送
        </button>
      </div>
    </div>
  )
}
