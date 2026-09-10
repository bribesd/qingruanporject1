package com.enterprise.kb.service;

import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.AuthUser;
import com.enterprise.kb.util.AuditLogger;
import com.enterprise.kb.util.Jdbc;
import com.enterprise.kb.util.Str;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 知识库业务，等价 routes/knowledgeRoutes.js。
 */
@Service
public class KnowledgeService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogger auditLogger;

    public KnowledgeService(JdbcTemplate jdbcTemplate, AuditLogger auditLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogger = auditLogger;
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_items", Long.class);
        return c == null ? 0 : c;
    }

    public List<Map<String, Object>> list(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT k.id, k.title, k.content, k.status, k.category_id, k.view_count, k.created_at, k.updated_at, " +
                        "c.name AS category, u.real_name AS author " +
                        "FROM knowledge_items k " +
                        "LEFT JOIN categories c ON k.category_id = c.id " +
                        "LEFT JOIN users u ON k.author_id = u.id " +
                        "ORDER BY k.id DESC LIMIT ? OFFSET ?", limit, offset);
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT k.*, c.name AS category, u.real_name AS author " +
                        "FROM knowledge_items k " +
                        "LEFT JOIN categories c ON k.category_id = c.id " +
                        "LEFT JOIN users u ON k.author_id = u.id " +
                        "WHERE k.id = ?", id);
        if (rows.isEmpty()) {
            throw new ApiException(404, "知识不存在");
        }
        jdbcTemplate.update("UPDATE knowledge_items SET view_count = view_count + 1 WHERE id = ?", id);
        return rows.get(0);
    }

    public long create(Map<String, Object> body, AuthUser currentUser) {
        String title = Str.orEmpty(body.get("title"));
        String content = Str.orEmpty(body.get("content"));
        if (title.isEmpty() || content.isEmpty()) {
            throw new ApiException(400, "标题和内容不能为空");
        }
        Long categoryId = Str.jsLong(body.get("categoryId"));
        String status = body.get("status") == null ? "draft" : String.valueOf(body.get("status"));

        long id = Jdbc.insertReturningKey(jdbcTemplate,
                "INSERT INTO knowledge_items (title, content, category_id, author_id, status) VALUES (?, ?, ?, ?, ?)",
                title, content, categoryId, currentUser.id(), status);

        auditLogger.log(currentUser.id(), "create_knowledge", "创建知识 " + title);
        return id;
    }

    public void update(long id, Map<String, Object> body, AuthUser currentUser) {
        String title = Str.orEmpty(body.get("title"));
        String content = Str.orEmpty(body.get("content"));
        if (title.isEmpty() || content.isEmpty()) {
            throw new ApiException(400, "标题和内容不能为空");
        }
        Long categoryId = Str.jsLong(body.get("categoryId"));
        String status = body.get("status") == null ? "draft" : String.valueOf(body.get("status"));

        jdbcTemplate.update(
                "UPDATE knowledge_items SET title = ?, content = ?, category_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                title, content, categoryId, status, id);

        auditLogger.log(currentUser.id(), "update_knowledge", "更新知识 ID " + id);
    }

    public void delete(long id, AuthUser currentUser) {
        jdbcTemplate.update("DELETE FROM knowledge_items WHERE id = ?", id);
        auditLogger.log(currentUser.id(), "delete_knowledge", "删除知识 ID " + id);
    }
}
