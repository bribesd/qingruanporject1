package com.enterprise.kb.controller;

import com.enterprise.kb.security.RequireAdmin;
import com.enterprise.kb.service.RoleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequireAdmin
    public List<Map<String, Object>> list() {
        return roleService.list();
    }
}
