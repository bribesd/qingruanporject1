package com.enterprise.kb.service;

import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.AuthUser;
import com.enterprise.kb.util.AuditLogger;
import com.enterprise.kb.util.Jdbc;
import com.enterprise.kb.util.Str;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 分类管理业务，等价 routes/categoryRoutes.js。
 */
@Service
public class CategoryService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogger auditLogger;

    public CategoryService(JdbcTemplate jdbcTemplate, AuditLogger auditLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogger = auditLogger;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.queryForList(
                "SELECT c.id, c.name, c.parent_id, COUNT(k.id) AS knowledge_count " +
                        "FROM categories c LEFT JOIN knowledge_items k ON k.category_id = c.id " +
                        "GROUP BY c.id, c.name, c.parent_id " +
                        "ORDER BY c.id DESC");
    }

    public long create(Map<String, Object> body, AuthUser currentUser) {
        String name = Str.orEmpty(body.get("name"));
        if (name.isEmpty()) {
            throw new ApiException(400, "分类名称不能为空");
        }
        Long parentId = Str.jsLong(body.get("parentId"));

        long id = Jdbc.insertReturningKey(jdbcTemplate,
                "INSERT INTO categories (name, parent_id) VALUES (?, ?)", name, parentId);

        auditLogger.log(currentUser.id(), "create_category", "创建分类 " + name);
        return id;
    }

    public void update(long id, Map<String, Object> body, AuthUser currentUser) {
        String name = Str.orEmpty(body.get("name"));
        if (name.isEmpty()) {
            throw new ApiException(400, "分类名称不能为空");
        }
        List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                "SELECT id FROM categories WHERE id = ?", id);
        if (targets.isEmpty()) {
            throw new ApiException(404, "分类不存在");
        }
        jdbcTemplate.update("UPDATE categories SET name = ? WHERE id = ?", name, id);
        auditLogger.log(currentUser.id(), "update_category", "更新分类 ID " + id);
    }

    @Transactional
    public void delete(long id, AuthUser currentUser) {
        List<Map<String, Object>> targets = jdbcTemplate.queryForList(
                "SELECT id FROM categories WHERE id = ?", id);
        if (targets.isEmpty()) {
            throw new ApiException(404, "分类不存在");
        }
        try {
            // 事务级联：解除子分类与知识条目的引用后再删除分类
            jdbcTemplate.update("UPDATE categories SET parent_id = NULL WHERE parent_id = ?", id);
            jdbcTemplate.update("UPDATE knowledge_items SET category_id = NULL WHERE category_id = ?", id);
            jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
        } catch (Exception e) {
            throw new ApiException(500, "删除分类失败，请稍后重试");
        }
        auditLogger.log(currentUser.id(), "delete_category", "删除分类 ID " + id);
    }
}
