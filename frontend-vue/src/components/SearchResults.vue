<script setup>
import { ref } from 'vue'
import { api } from '../api'

const query = ref('')
const results = ref([])
const searched = ref(false)
const loading = ref(false)

function escapeHtml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function highlight(text) {
  const safeText = escapeHtml(text)
  const q = query.value.trim()
  if (!q) return safeText
  const safeQ = escapeHtml(q).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safeText.replace(new RegExp(safeQ, 'gi'), (m) => `<mark>${m}</mark>`)
}

async function doSearch() {
  if (!query.value.trim()) return
  loading.value = true
  try {
    results.value = await api.get(`/api/search?q=${encodeURIComponent(query.value.trim())}`)
    searched.value = true
  } catch (err) {
    if (err.status !== 401) alert(err.message)
  } finally { loading.value = false }
}
</script>

<template>
  <div class="panel">
    <div style="display: flex; gap: 10px">
      <input v-model="query" style="flex: 1" placeholder="输入关键词进行语义搜索" @keyup.enter="doSearch" />
      <button class="primary-btn" :disabled="loading" @click="doSearch">
        {{ loading ? '搜索中…' : '搜索' }}
      </button>
    </div>
  </div>

  <div class="panel">
    <p v-if="!searched" class="empty">输入关键词开始搜索，结果按语义相似度排序并高亮</p>
    <p v-else-if="results.length === 0" class="empty">未找到相关内容</p>
    <article v-for="r in results" :key="r.knowledgeId" class="knowledge-item">
      <div class="panel-header">
        <h4>{{ r.title }}</h4>
        <small>相似度 {{ (r.score * 100).toFixed(0) }}% · {{ r.category || '未分类' }}</small>
      </div>
      <!-- 内容已经过 HTML 转义，仅插入 <mark> 高亮标签，无 XSS 风险 -->
      <p class="knowledge-summary" v-html="highlight(r.snippet)"></p>
    </article>
  </div>
</template>
