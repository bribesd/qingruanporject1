package com.enterprise.kb.service;

import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.AuthUser;
import com.enterprise.kb.util.AuditLogger;
import com.enterprise.kb.util.Jdbc;
import com.enterprise.kb.util.Str;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答业务，等价 routes/questionRoutes.js。
 */
@Service
public class QuestionService {

    private static final List<String> VALID_STATUS = List.of("open", "answered");

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogger auditLogger;

    public QuestionService(JdbcTemplate jdbcTemplate, AuditLogger auditLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogger = auditLogger;
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Long.class);
        return c == null ? 0 : c;
    }

    public List<Map<String, Object>> list(int limit, int offset) {
        return jdbcTemplate.queryForList(
                "SELECT q.id, q.title, q.content, q.status, q.created_at, u.real_name AS user_name, " +
                        "(SELECT COUNT(*) FROM answers a WHERE a.question_id = q.id) AS answer_count " +
                        "FROM questions q LEFT JOIN users u ON q.user_id = u.id " +
                        "ORDER BY q.id DESC LIMIT ? OFFSET ?", limit, offset);
    }

    public Map<String, Object> get(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT q.*, u.real_name AS user_name FROM questions q " +
                        "LEFT JOIN users u ON q.user_id = u.id WHERE q.id = ?", id);
        if (rows.isEmpty()) {
            throw new ApiException(404, "问题不存在");
        }
        List<Map<String, Object>> answers = jdbcTemplate.queryForList(
                "SELECT a.*, u.real_name AS author_name FROM answers a " +
                        "LEFT JOIN users u ON a.user_id = u.id WHERE a.question_id = ? " +
                        "ORDER BY a.created_at DESC", id);

        Map<String, Object> question = new LinkedHashMap<>(rows.get(0));
        question.put("answers", answers);
        return question;
    }

    public long create(Map<String, Object> body, AuthUser currentUser) {
        String title = Str.orEmpty(body.get("title"));
        String content = Str.orEmpty(body.get("content"));
        if (title.isEmpty() || content.isEmpty()) {
            throw new ApiException(400, "问题标题和内容不能为空");
        }
        long id = Jdbc.insertReturningKey(jdbcTemplate,
                "INSERT INTO questions (user_id, title, content) VALUES (?, ?, ?)",
                currentUser.id(), title, content);

        auditLogger.log(currentUser.id(), "create_question", "提交问题 " + title);
        return id;
    }

    public long answer(long questionId, Map<String, Object> body, AuthUser currentUser) {
        String content = Str.orEmpty(body.get("content"));
        if (content.isEmpty()) {
            throw new ApiException(400, "回答内容不能为空");
        }
        Integer questionExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE id = ?", Integer.class, questionId);
        if (questionExists == null || questionExists == 0) {
            throw new ApiException(404, "问题不存在");
        }
        long id = Jdbc.insertReturningKey(jdbcTemplate,
                "INSERT INTO answers (question_id, user_id, content) VALUES (?, ?, ?)",
                questionId, currentUser.id(), content);
        jdbcTemplate.update("UPDATE questions SET status = 'answered' WHERE id = ?", questionId);

        auditLogger.log(currentUser.id(), "answer_question", "回答问题 ID " + questionId);
        return id;
    }

    public void updateStatus(long id, Map<String, Object> body, AuthUser currentUser) {
        String status = Str.orEmpty(body.get("status"));
        if (status.isEmpty()) {
            throw new ApiException(400, "状态不能为空");
        }
        if (!VALID_STATUS.contains(status)) {
            throw new ApiException(400, "无效的问题状态");
        }
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questions WHERE id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            throw new ApiException(404, "问题不存在");
        }
        jdbcTemplate.update("UPDATE questions SET status = ? WHERE id = ?", status, id);
        auditLogger.log(currentUser.id(), "update_question_status", "更新问题状态 ID " + id + " -> " + status);
    }
}
