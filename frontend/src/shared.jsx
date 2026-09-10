// 后端枚举值 → 中文标签，供各页面共用
export const KNOWLEDGE_STATUS = { draft: '草稿', published: '已发布' }
export const QUESTION_STATUS = { open: '待处理', answered: '已回答' }
export const USER_STATUS = { active: '启用', disabled: '禁用' }
export const ROLE_LABEL = {
  super_admin: '超级管理员',
  admin: '管理员',
  user: '普通用户',
}

export const statusLabel = (status) => KNOWLEDGE_STATUS[status] || status || '未知'
export const questionLabel = (status) => QUESTION_STATUS[status] || status || '未知'
export const roleLabel = (role) => ROLE_LABEL[role] || role || '—'
export const userStatusLabel = (status) => USER_STATUS[status] || status || '未知'
