package com.enterprise.kb.security;

/**
 * 当前登录用户信息，等价 Node 中间件注入到 req.user 的对象。
 */
public record AuthUser(Long id, String username, String role, String status) {

    public boolean isAdmin() {
        return "super_admin".equals(role) || "admin".equals(role);
    }

    public boolean isSuperAdmin() {
        return "super_admin".equals(role);
    }
}
