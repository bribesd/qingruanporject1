<script setup>
import { nextTick, ref } from 'vue'
import { api } from '../api'

const messages = ref([])
const input = ref('')
const sending = ref(false)
const listEl = ref(null)

function scrollBottom() {
  nextTick(() => listEl.value?.scrollTo({ top: listEl.value.scrollHeight, behavior: 'smooth' }))
}

async function send() {
  const question = input.value.trim()
  if (!question || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  messages.value.push({ role: 'assistant', content: '', sources: [], loading: true })
  sending.value = true
  scrollBottom()
  try {
    const data = await api.post('/api/qa/ask', { question })
    const reply = messages.value[messages.value.length - 1]
    reply.content = data.answer
    reply.sources = data.sources || []
    reply.loading = false
  } catch (err) {
    const reply = messages.value[messages.value.length - 1]
    reply.content = err.message || '请求失败'
    reply.loading = false
  } finally {
    sending.value = false
    scrollBottom()
  }
}
</script>

<template>
  <div class="panel chat-box">
    <div ref="listEl" class="chat-messages">
      <p v-if="messages.length === 0" class="empty">向 AI 提问，回答基于知识库内容并附引用来源</p>
      <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role">
        <template v-if="msg.loading">思考中…</template>
        <template v-else>
          {{ msg.content }}
          <div v-if="msg.sources?.length" class="sources">
            <span v-for="s in msg.sources" :key="s.knowledgeId">📄 {{ s.title }}（{{ (s.score * 100).toFixed(0) }}%）</span>
          </div>
        </template>
      </div>
    </div>
    <div class="chat-input">
      <input
        v-model="input"
        placeholder="输入你的问题，回车发送"
        @keyup.enter="send"
        :disabled="sending"
      />
      <button class="primary-btn" :disabled="sending" @click="send">发送</button>
    </div>
  </div>
</template>
