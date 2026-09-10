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

    private void requireSuperAdmin(AuthUser currentUser) {
        if (currentUser == null || !currentUser.isSuperAdmin()) {
            throw new ApiException(403, "Only super administrators may manage user accounts");
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
        requireSuperAdmin(currentUser);
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
        if (roleId == null) {
            roleId = 2L;
        }
        String status = body.get("status") == null ? "active" : String.valueOf(body.get("status"));

        long id = Jdbc.insertReturningKey(jdbcTemplate,
                "INSERT INTO users (username, password, real_name, email, role_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                username, hash, realName, email, roleId, status);

        auditLogger.log(currentUser.id(), "create_user", "创建用户 " + username);
        return id;
    }

    public void update(long id, Map<String, Object> body, AuthUser currentUser) {
        requireSuperAdmin(currentUser);
        Long roleId = Str.jsLong(body.get("roleId"));

        if (roleId != null) {
            List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                    "SELECT id, role_id FROM users WHERE id = ?", id);
            List<Map<String, Object>> superRoles = jdbcTemplate.queryForList(
                    "SELECT id FROM roles WHERE name = 'super_admin'");
            if (!targets.isEmpty() && !superRoles.isEmpty()) {
                long superAdminId = ((Number) superRoles.get(0).get("id")).longValue();
                long targetRoleId = ((Number) targets.get(0).get("role_id")).longValue();
                if (targetRoleId == superAdminId && roleId != superAdminId) {
                    Long superCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM users WHERE role_id = ?", Long.class, superAdminId);
                    if (superCount != null && superCount <= 1) {
                        throw new ApiException(400, "至少需要保留一个超级管理员，无法降级");
                    }
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
        requireSuperAdmin(currentUser);
        if (id <= 0 || id == currentUser.id()) {
            throw new ApiException(400, "不能删除当前登录的账号");
        }

        List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                "SELECT id, role_id FROM users WHERE id = ?", id);
        if (targets.isEmpty()) {
            throw new ApiException(404, "用户不存在");
        }

        List<Map<String, Object>> superRoles = jdbcTemplate.queryForList(
                "SELECT id FROM roles WHERE name = 'super_admin'");
        if (!superRoles.isEmpty()) {
            long superAdminId = ((Number) superRoles.get(0).get("id")).longValue();
            long targetRoleId = ((Number) targets.get(0).get("role_id")).longValue();
            if (targetRoleId == superAdminId) {
                Long superCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE role_id = ?", Long.class, superAdminId);
                if (superCount != null && superCount <= 1) {
                    throw new ApiException(400, "至少需要保留一个超级管理员，无法删除");
                }
            }
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
