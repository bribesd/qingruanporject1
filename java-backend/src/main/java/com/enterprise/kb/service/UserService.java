package com.enterprise.kb.service;

import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.AuthUser;
import com.enterprise.kb.util.AuditLogger;
import com.enterprise.kb.util.Jdbc;
import com.enterprise.kb.util.Str;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 用户管理业务，等价 routes/userRoutes.js。
 */
@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogger auditLogger;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(JdbcTemplate jdbcTemplate, AuditLogger auditLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogger = auditLogger;
    }

    private void requireAdmin(AuthUser currentUser) {
        if (currentUser == null || !currentUser.isAdmin()) {
            throw new ApiException(403, "只有管理员可以管理用户账号");
        }
    }

    private long roleId(String roleName) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ?", Long.class, roleName);
        if (id == null) {
            throw new ApiException(500, "系统角色配置异常");
        }
        return id;
    }

    private void requireValidStatus(Object value) {
        if (value != null && !List.of("active", "disabled").contains(String.valueOf(value))) {
            throw new ApiException(400, "用户状态无效");
        }
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        return c == null ? 0 : c;
    }

    public List<Map<String, Object>> list(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT u.id, u.username, u.real_name, u.email, u.status, u.role_id, r.name AS role " +
                        "FROM users u LEFT JOIN roles r ON u.role_id = r.id " +
                        "ORDER BY u.id DESC LIMIT ? OFFSET ?", limit, offset);
    }

    public long create(Map<String, Object> body, AuthUser currentUser) {
        requireAdmin(currentUser);
        String username = Str.orEmpty(body.get("username"));
        String password = Str.orEmpty(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) {
            throw new ApiException(400, "用户名和密码不能为空");
        }

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        if (exists != null && exists > 0) {
            throw new ApiException(409, "用户名已存在");
        }

        String hash = passwordEncoder.encode(password);
        String realName = Str.orEmpty(body.get("realName"));
        String email = Str.orEmpty(body.get("email"));
        Long roleId = Str.jsLong(body.get("roleId"));
        long userRoleId = roleId("user");
        if (!currentUser.isSuperAdmin()) {
            roleId = userRoleId;
        } else if (roleId == null) {
            roleId = userRoleId;
        }
        Integer roleExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM roles WHERE id = ?", Integer.class, roleId);
        if (roleExists == null || roleExists == 0) {
            throw new ApiException(400, "用户角色无效");
        }
        String status = body.get("status") == null ? "active" : String.valueOf(body.get("status"));
        requireValidStatus(status);

        long id = Jdbc.insertReturningKey(jdbcTemplate,
                "INSERT INTO users (username, password, real_name, email, role_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                username, hash, realName, email, roleId, status);

        auditLogger.log(currentUser.id(), "create_user", "创建用户 " + username);
        return id;
    }

    public void update(long id, Map<String, Object> body, AuthUser currentUser) {
        requireAdmin(currentUser);
        Long roleId = Str.jsLong(body.get("roleId"));
        requireValidStatus(body.get("status"));

        if (!currentUser.isSuperAdmin() && roleId != null) {
            throw new ApiException(403, "普通管理员不能修改用户角色");
        }

        List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                "SELECT id, role_id, status FROM users WHERE id = ?", id);
        if (targets.isEmpty()) {
            throw new ApiException(404, "用户不存在");
        }

        long superAdminRoleId = roleId("super_admin");
        long adminRoleId = roleId("admin");
        long targetRoleId = ((Number) targets.get(0).get("role_id")).longValue();
        boolean targetIsSuperAdmin = targetRoleId == superAdminRoleId;
        boolean targetIsAdmin = targetRoleId == adminRoleId;
        boolean selfUpdate = id == currentUser.id();

        if (!currentUser.isSuperAdmin() && !selfUpdate && targetIsAdmin && (roleId != null || body.get("status") != null)) {
            throw new ApiException(403, "普通管理员不能修改管理员账号的角色或状态");
        }
        if (targetIsSuperAdmin && !selfUpdate && (roleId != null || body.get("status") != null)) {
            throw new ApiException(403, "不能修改其他超级管理员的角色或状态");
        }
        if (roleId != null) {
            Integer roleExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM roles WHERE id = ?", Integer.class, roleId);
            if (roleExists == null || roleExists == 0) {
                throw new ApiException(400, "用户角色无效");
            }
        }
        if (selfUpdate && currentUser.isSuperAdmin() && "disabled".equals(body.get("status"))) {
            throw new ApiException(400, "不能禁用当前登录的超级管理员");
        }

        if (roleId != null) {
            if (targetIsSuperAdmin && roleId != superAdminRoleId) {
                Long superCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE role_id = ?", Long.class, superAdminRoleId);
                if (superCount != null && superCount <= 1) {
                    throw new ApiException(400, "至少需要保留一个超级管理员，无法降级");
                }
            }
        }

        jdbcTemplate.update(
                "UPDATE users SET real_name = ?, email = ?, " +
                        "role_id = COALESCE(?, role_id), status = COALESCE(?, status) WHERE id = ?",
                Str.normText(body.get("realName")), Str.normText(body.get("email")),
                roleId, body.get("status"), id);

        auditLogger.log(currentUser.id(), "update_user", "更新用户 ID " + id);
    }

    @Transactional
    public void delete(long id, AuthUser currentUser) {
        requireAdmin(currentUser);
        if (id <= 0 || id == currentUser.id()) {
            throw new ApiException(400, "不能删除当前登录的账号");
        }

        List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                "SELECT id, role_id FROM users WHERE id = ?", id);
        if (targets.isEmpty()) {
            throw new ApiException(404, "用户不存在");
        }

        long superAdminId = roleId("super_admin");
        long adminId = roleId("admin");
        long targetRoleId = ((Number) targets.get(0).get("role_id")).longValue();
        if (targetRoleId == superAdminId) {
            throw new ApiException(403, "不能删除超级管理员账号");
        }
        if (!currentUser.isSuperAdmin() && targetRoleId == adminId) {
            throw new ApiException(403, "普通管理员不能删除管理员账号");
        }

        try {
            // 事务级联：先解除其他表外键引用，再删除用户
            jdbcTemplate.update("UPDATE knowledge_items SET author_id = NULL WHERE author_id = ?", id);
            jdbcTemplate.update("UPDATE questions SET user_id = NULL WHERE user_id = ?", id);
            jdbcTemplate.update("UPDATE answers SET user_id = NULL WHERE user_id = ?", id);
            jdbcTemplate.update("UPDATE audit_logs SET user_id = NULL WHERE user_id = ?", id);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
        } catch (Exception e) {
            throw new ApiException(500, "删除用户失败，请稍后重试");
        }

        auditLogger.log(currentUser.id(), "delete_user", "删除用户 ID " + id);
    }
}
