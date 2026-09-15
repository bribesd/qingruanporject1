let token = localStorage.getItem('token') || ''

export function setToken(next) {
  token = next || ''
  if (token) localStorage.setItem('token', token)
  else localStorage.removeItem('token')
}

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, { ...options, headers })
  let data = null
  try { data = await res.json() } catch { /* 非JSON响应 */ }

  if (!res.ok) {
    if (res.status === 401) window.dispatchEvent(new CustomEvent('auth:unauthorized'))
    const err = new Error(data?.message || `请求失败（${res.status}）`)
    err.status = res.status
    throw err
  }
  return data
}

export const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body: JSON.stringify(body) }),
  put: (path, body) => request(path, { method: 'PUT', body: JSON.stringify(body) }),
  del: (path) => request(path, { method: 'DELETE' }),
  upload: (path, formData) => request(path, { method: 'POST', body: formData }),
}
