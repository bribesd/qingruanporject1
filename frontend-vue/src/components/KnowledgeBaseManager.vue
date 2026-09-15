<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'

const props = defineProps({ user: Object })
const canWrite = ['super_admin', 'admin'].includes(props.user?.role)

const categories = ref([])
const knowledge = ref([])
const loading = ref(true)
const saving = ref(false)
const showForm = ref(false)
const editingId = ref(null)
const file = ref(null)
const form = ref({ title: '', categoryId: '', status: 'draft', content: '' })

async function load() {
  const [cats, items] = await Promise.all([api.get('/api/categories'), api.get('/api/knowledge')])
  categories.value = cats || []
  knowledge.value = items || []
}

onMounted(async () => {
  try { await load() } catch (err) { if (err.status !== 401) alert(err.message) }
  loading.value = false
})

function openCreate() {
  editingId.value = null
  file.value = null
  form.value = { title: '', categoryId: '', status: 'draft', content: '' }
  showForm.value = true
}

function openEdit(item) {
  editingId.value = item.id
  file.value = null
  form.value = {
    title: item.title,
    categoryId: item.category_id ?? '',
    status: item.status || 'draft',
    content: item.content || '',
  }
  showForm.value = true
}

function onFileChange(e) { file.value = e.target.files[0] || null }

async function handleSave() {
  if (!form.value.title || (!form.value.content && !file.value)) {
    alert('请填写标题，并填写内容或选择文档')
    return
  }
  saving.value = true
  try {
    if (!editingId.value && file.value) {
      const fd = new FormData()
      fd.append('file', file.value)
      fd.append('title', form.value.title)
      fd.append('status', form.value.status)
      if (form.value.categoryId) fd.append('categoryId', form.value.categoryId)
      await api.upload('/api/knowledge/upload', fd)
    } else {
      const payload = {
        title: form.value.title,
        content: form.value.content,
        status: form.value.status,
        categoryId: form.value.categoryId ? Number(form.value.categoryId) : null,
      }
      if (editingId.value) await api.put(`/api/knowledge/${editingId.value}`, payload)
      else await api.post('/api/knowledge', payload)
    }
    showForm.value = false
    await load()
  } catch (err) {
    if (err.status !== 401) alert(err.message)
  } finally { saving.value = false }
}

async function handleDelete(item) {
  if (!window.confirm(`确定删除知识「${item.title}」吗？`)) return
  try { await api.del(`/api/knowledge/${item.id}`); await load() }
  catch (err) { if (err.status !== 401) alert(err.message) }
}
</script>

<template>
  <div class="panel" v-if="canWrite && !loading">
    <div class="panel-header">
      <h3>{{ editingId ? '编辑知识' : '新增知识 / 上传文档' }}</h3>
      <button v-if="showForm" class="link-btn" @click="showForm = false; editingId = null">收起表单</button>
      <button v-else class="primary-btn" @click="openCreate">新增</button>
    </div>

    <div v-if="showForm" style="display: flex; flex-direction: column; gap: 12px">
      <label><span>标题</span><input v-model="form.title" placeholder="知识标题" /></label>
      <div style="display: flex; gap: 12px">
        <label style="flex: 1"><span>分类</span>
          <select v-model="form.categoryId">
            <option value="">未分类</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <label style="flex: 1"><span>状态</span>
          <select v-model="form.status">
            <option value="draft">草稿</option>
            <option value="published">已发布</option>
          </select>
        </label>
      </div>
      <label v-if="!editingId"><span>上传文档（pdf / docx / txt / md，可选）</span>
        <input type="file" accept=".pdf,.docx,.txt,.md" @change="onFileChange" />
      </label>
      <label><span>内容{{ file ? '（已选择文档，可留空）' : '' }}</span>
        <textarea v-model="form.content" rows="6" placeholder="知识内容"></textarea>
      </label>
      <button class="primary-btn" :disabled="saving" @click="handleSave">
        {{ saving ? '保存中…' : '保存（自动建立语义索引）' }}
      </button>
    </div>
  </div>

  <div class="panel">
    <div class="panel-header"><h3>知识列表（{{ knowledge.length }}）</h3></div>
    <p v-if="loading" class="empty">加载中…</p>
    <table v-else class="data-table">
      <thead><tr><th>标题</th><th>分类</th><th>状态</th><th v-if="canWrite">操作</th></tr></thead>
      <tbody>
        <tr v-for="item in knowledge" :key="item.id">
          <td>{{ item.title }}</td>
          <td>{{ item.category || '未分类' }}</td>
          <td>{{ item.status === 'published' ? '已发布' : '草稿' }}</td>
          <td v-if="canWrite">
            <button class="link-btn" @click="openEdit(item)">编辑</button>
            <button class="link-btn danger" @click="handleDelete(item)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
