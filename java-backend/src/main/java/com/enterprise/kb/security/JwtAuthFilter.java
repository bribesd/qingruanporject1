package com.enterprise.kb.security;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器，等价 middleware/authMiddleware.js：
 * 校验 Bearer token，实时查库取最新用户状态与角色，写入 UserContext。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    // 受保护的路由前缀（与 server.js 中挂载的受保护路由一致）
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/dashboard", "/api/roles", "/api/users", "/api/categories",
            "/api/knowledge", "/api/questions", "/api/audit-logs",
            "/api/search", "/api/qa");

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthFilter(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI();
        if ("/api/health".equals(path) || "/api/auth/login".equals(path)) {
            return true;
        }
        // 未匹配到任何受保护路由时跳过过滤器，交由 DispatcherServlet 返回 404（等价 Node 的 404 兜底）
        return PROTECTED_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeError(response, 401, "未授权访问");
            return;
        }
        try {
            String token = header.substring(7);
            Claims claims = jwtUtil.verifyToken(token);
            Long id = ((Number) claims.get("id")).longValue();

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT u.id, u.username, u.status, r.name AS role_name " +
                            "FROM users u LEFT JOIN roles r ON u.role_id = r.id WHERE u.id = ?", id);
            if (rows.isEmpty()) {
                writeError(response, 401, "用户不存在");
                return;
            }
            Map<String, Object> user = rows.get(0);
            String status = String.valueOf(user.get("status"));
            if (!"active".equals(status)) {
                writeError(response, 401, "账号已被禁用");
                return;
            }

            AuthUser authUser = new AuthUser(
                    id,
                    String.valueOf(user.get("username")),
                    String.valueOf(user.get("role_name")),
                    status);
            UserContext.set(authUser);
            try {
                chain.doFilter(request, response);
            } finally {
                UserContext.clear();
            }
        } catch (Exception e) {
            UserContext.clear();
            log.warn("JWT 认证失败: {}", e.getMessage());
            writeError(response, 401, "令牌无效或已过期");
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
