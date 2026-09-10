package com.enterprise.kb.controller;

import com.enterprise.kb.service.AuthService;
import com.enterprise.kb.util.Str;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return authService.login(
                Str.orEmpty(body.get("username")),
                Str.orEmpty(body.get("password")),
                request.getRemoteAddr());
    }
}
