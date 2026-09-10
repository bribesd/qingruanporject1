package com.enterprise.kb.controller;

import com.enterprise.kb.security.RequireAdmin;
import com.enterprise.kb.security.UserContext;
import com.enterprise.kb.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return categoryService.list();
    }

    @PostMapping
    @RequireAdmin
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long id = categoryService.create(body, UserContext.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("message", "分类创建成功");
        return ResponseEntity.status(201).body(resp);
    }

    @PutMapping("/{id}")
    @RequireAdmin
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        categoryService.update(id, body, UserContext.get());
        return Map.of("message", "分类更新成功");
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public Map<String, Object> delete(@PathVariable long id) {
        categoryService.delete(id, UserContext.get());
        return Map.of("message", "分类删除成功");
    }
}
