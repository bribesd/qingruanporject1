package com.enterprise.kb.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据初始化：合并原 initDb.js 与 seedUsers.js 的逻辑，幂等写入种子数据。
 * 运行在 schema.sql 建表之后。
 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final String adminUsername;
    private final String adminPassword;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataInitializer(JdbcTemplate jdbcTemplate,
                           @Value("${app.bootstrap.admin-username}") String adminUsername,
                           @Value("${app.bootstrap.admin-password}") String adminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("APP_INITIAL_ADMIN_PASSWORD must be set when bootstrap is enabled");
        }
        seedRoles();
        seedAdmin();
        seedCategories();
    }

    private void seedRoles() {
        if (count("roles") == 0) {
            jdbcTemplate.update("INSERT INTO roles (name, description) VALUES (?, ?)", "super_admin", "超级管理员");
            jdbcTemplate.update("INSERT INTO roles (name, description) VALUES (?, ?)", "admin", "管理员");
            jdbcTemplate.update("INSERT INTO roles (name, description) VALUES (?, ?)", "user", "普通用户");
        }
    }

    private void seedAdmin() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, adminUsername);
        if (count == null || count == 0) {
            Long adminRoleId = roleId("super_admin");
            String hash = passwordEncoder.encode(adminPassword);
            jdbcTemplate.update(
                    "INSERT INTO users (username, password, real_name, email, role_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                    adminUsername, hash, "System Administrator", adminUsername + "@localhost", adminRoleId, "active");
        }
    }

    @Deprecated(forRemoval = true)
    private void seedLegacyAdmin() {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, "admin");
        if (c == null || c == 0) {
            Long adminRoleId = roleId("super_admin");
            String hash = passwordEncoder.encode(adminPassword);
            jdbcTemplate.update(
                    "INSERT INTO users (username, password, real_name, email, role_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                    "admin", hash, "系统管理员", "admin@company.com", adminRoleId, "active");
        }
    }

    private void seedCategories() {
        String[] names = {"公司制度", "产品说明", "技术文档", "FAQ", "流程规范"};
        for (String name : names) {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM categories WHERE name = ?", Integer.class, name);
            if (c == null || c == 0) {
                jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", name);
            }
        }
    }

    private void seedTestUsers() {
        Object[][] seed = {
                {"testuser", "test123", "测试用户", "user"},
                {"zhangsan", "test123", "张三", "user"},
                {"lisi", "test123", "李四", "admin"},
                {"wangwu", "test123", "王五", "user"},
                {"zhaoliu", "test123", "赵六", "admin"},
        };
        for (Object[] u : seed) {
            String username = (String) u[0];
            String password = (String) u[1];
            String realName = (String) u[2];
            String roleName = (String) u[3];

            Long roleId = roleId(roleName);
            if (roleId == null) {
                continue;
            }
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
            if (c == null || c == 0) {
                String hash = passwordEncoder.encode(password);
                jdbcTemplate.update(
                        "INSERT INTO users (username, password, real_name, email, role_id, status) VALUES (?, ?, ?, ?, ?, 'active')",
                        username, hash, realName, username + "@company.com", roleId);
            }
        }
    }

    private Long roleId(String name) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM roles WHERE name = ?", Long.class, name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long count(String table) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return c == null ? 0 : c;
    }
}
