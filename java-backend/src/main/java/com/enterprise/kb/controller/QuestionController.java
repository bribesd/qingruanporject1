package com.enterprise.kb.controller;

import com.enterprise.kb.security.RequireAdmin;
import com.enterprise.kb.security.UserContext;
import com.enterprise.kb.service.QuestionService;
import com.enterprise.kb.util.Pagination;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset) {
        Pagination.Page page = Pagination.parse(limit, offset, 200);
        long total = questionService.count();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total))
                .body(questionService.list(page.limit(), page.offset()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return questionService.get(id);
    }

    @PostMapping
    @RequireAdmin
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long id = questionService.create(body, UserContext.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("message", "问题提交成功");
        return ResponseEntity.status(201).body(resp);
    }

    @PostMapping("/{id}/answers")
    @RequireAdmin
    public ResponseEntity<Map<String, Object>> answer(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long answerId = questionService.answer(id, body, UserContext.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", answerId);
        resp.put("message", "回答提交成功");
        return ResponseEntity.status(201).body(resp);
    }

    @PatchMapping("/{id}/status")
    @RequireAdmin
    public Map<String, Object> updateStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        questionService.updateStatus(id, body, UserContext.get());
        return Map.of("message", "问题状态更新成功");
    }
}
