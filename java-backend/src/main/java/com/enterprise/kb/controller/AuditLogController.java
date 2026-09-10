package com.enterprise.kb.controller;

import com.enterprise.kb.security.RequireSuperAdmin;
import com.enterprise.kb.service.AuditLogService;
import com.enterprise.kb.util.Pagination;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @RequireSuperAdmin
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String offset) {
        Pagination.Page page = Pagination.parse(limit, offset, 100);
        long total = auditLogService.count();
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total))
                .body(auditLogService.list(page.limit(), page.offset()));
    }
}
