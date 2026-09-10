package com.enterprise.kb.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审计日志查询，等价 routes/auditLogRoutes.js。
 */
@Service
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);
        return c == null ? 0 : c;
    }

    public List<Map<String, Object>> list(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT l.id, l.action, l.detail, l.created_at, u.real_name AS user_name " +
                        "FROM audit_logs l LEFT JOIN users u ON l.user_id = u.id " +
                        "ORDER BY l.id DESC LIMIT ? OFFSET ?", limit, offset);
    }
}
