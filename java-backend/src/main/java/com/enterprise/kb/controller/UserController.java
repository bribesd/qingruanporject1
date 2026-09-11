package com.enterprise.kb.controller;

import com.enterprise.kb.security.RequireAdmin;
import com.enterprise.kb.security.UserContext;
import com.enterprise.kb.service.UserService;
import com.enterprise.kb.util.Pagination;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @RequireAdmin
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset) {
        Pagination.Page page = Pagination.parse(limit, offset, 200);
        long total = userService.count();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total))
                .body(userService.list(page.limit(), page.offset()));
    }

    @PostMapping
    @RequireAdmin
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long id = userService.create(body, UserContext.get());
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("message", "用户创建成功");
        return ResponseEntity.status(201).body(resp);
    }

    @PutMapping("/{id}")
    @RequireAdmin
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        userService.update(id, body, UserContext.get());
        return Map.of("message", "用户更新成功");
    }

    @DeleteMapping("/{id}")
    @RequireAdmin
    public Map<String, Object> delete(@PathVariable long id) {
        userService.delete(id, UserContext.get());
        return Map.of("message", "用户删除成功");
    }
}
