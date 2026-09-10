package com.enterprise.kb.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 审计日志写入，等价 utils/logAction.js。
 */
@Component
public class AuditLogger {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(Long userId, String action, String detail) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (user_id, action, detail) VALUES (?, ?, ?)",
                userId, action, detail == null ? "" : detail);
    }
}
