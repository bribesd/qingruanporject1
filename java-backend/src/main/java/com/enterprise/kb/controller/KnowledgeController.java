package com.enterprise.kb.controller;

import com.enterprise.kb.ai.DocumentParserService;
import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.RequireAdmin;
import com.enterprise.kb.security.UserContext;
import com.enterprise.kb.service.KnowledgeService;
import com.enterprise.kb.util.Pagination;
import com.enterprise.kb.util.Str;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final DocumentParserService documentParserService;

    public KnowledgeController(KnowledgeService knowledgeService, DocumentParserService documentParserService) {
        this.knowledgeService = knowledgeService;
        this.documentParserService = documentParserService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset) {
        Pagination.Page page = Pagination.parse(limit, offset, 200);
        long total = knowledgeService.count();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total))
                .body(knowledgeService.list(page.limit(), page.offset()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return knowledgeService.get(id);
    }

    /** 文档上传：解析 pdf/docx/txt/md 为纯文本后创建知识并建立向量索引 */
    @PostMapping("/upload")
    @RequireAdmin
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "status", required = false) String status) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "请选择要上传的文档");
        }
        String content = documentParserService.parse(file);
        String finalTitle = Str.orEmpty(title);
        if (finalTitle.isEmpty()) {
            String name = file.getOriginalFilename() == null ? "未命名文档" : file.getOriginalFilename();
            int dot = name.lastIndexOf('.');
            finalTitle = dot > 0 ? name.substring(0, dot) : name;
        }
        String finalStatus = "published".equals(status) ? "published" : "draft";
        long id = knowledgeService.createFromDocument(
                finalTitle, categoryId, finalStatus, content, UserContext.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("message", "文档上传并解析成功");
        return ResponseEntity.status(201).body(resp);
    }

    @PostMapping
    @RequireAdmin
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long id = knowledgeService.create(body, UserContext.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("message", "知识新增成功");
        return ResponseEntity.status(201).body(resp);
    }

    @PutMapping("/{id}")
    @RequireAdmin
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        knowledgeService.update(id, body, UserContext.get());
        return Map.of("message", "知识更新成功");
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public Map<String, Object> delete(@PathVariable long id) {
        knowledgeService.delete(id, UserContext.get());
        return Map.of("message", "知识删除成功");
    }
}
