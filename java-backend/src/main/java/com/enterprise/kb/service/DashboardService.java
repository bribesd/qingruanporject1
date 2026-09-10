package com.enterprise.kb.service;

import com.enterprise.kb.security.AuthUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计看板业务，等价 routes/dashboardRoutes.js。
 */
@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary(AuthUser currentUser) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userCount", count("users"));
        result.put("knowledgeCount", count("knowledge_items"));
        result.put("questionCount", count("questions"));
        result.put("draftCount", countWhere("knowledge_items", "status = 'draft'"));
        result.put("publishedCount", countWhere("knowledge_items", "status = 'published'"));
        result.put("openQuestionCount", countWhere("questions", "status = 'open'"));

        List<Map<String, Object>> recentKnowledge = jdbcTemplate.queryForList(
                "SELECT k.id, k.title, k.status, k.created_at, c.name AS category " +
                        "FROM knowledge_items k LEFT JOIN categories c ON k.category_id = c.id " +
                        "ORDER BY k.created_at DESC LIMIT 5");
        result.put("recentKnowledge", recentKnowledge);
        result.put("activeRole", currentUser.role());
        return result;
    }

    private long count(String table) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return c == null ? 0 : c;
    }

    private long countWhere(String table, String where) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return c == null ? 0 : c;
    }
}
