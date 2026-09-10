package com.enterprise.kb.service;

import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.AuthUser;
import com.enterprise.kb.security.JwtUtil;
import com.enterprise.kb.util.AuditLogger;
import com.enterprise.kb.util.Str;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录业务，等价 routes/authRoutes.js。
 */
@Service
public class AuthService {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final JdbcTemplate jdbcTemplate;
    private final JwtUtil jwtUtil;
    private final AuditLogger auditLogger;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 内存登录限流：IP + 用户名，1 分钟窗口内最多 10 次
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public AuthService(JdbcTemplate jdbcTemplate, JwtUtil jwtUtil, AuditLogger auditLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtUtil = jwtUtil;
        this.auditLogger = auditLogger;
    }

    public Map<String, Object> login(String username, String password, String ip) {
        if (Str.orEmpty(username).isEmpty() || Str.orEmpty(password).isEmpty()) {
            throw new ApiException(400, "用户名和密码不能为空");
        }

        String rateKey = ip + "|" + username;
        if (!checkLoginRate(rateKey)) {
            throw new ApiException(429, "登录尝试过于频繁，请稍后再试");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT u.*, r.name AS role_name FROM users u " +
                        "LEFT JOIN roles r ON u.role_id = r.id WHERE u.username = ?", username);
        if (rows.isEmpty()) {
            throw new ApiException(401, "用户名或密码错误");
        }
        Map<String, Object> user = rows.get(0);

        String status = String.valueOf(user.get("status"));
        if (!"active".equals(status)) {
            throw new ApiException(403, "账号已被禁用，请联系管理员");
        }

        boolean valid = passwordEncoder.matches(password, String.valueOf(user.get("password")));
        if (!valid) {
            throw new ApiException(401, "用户名或密码错误");
        }

        AuthUser authUser = new AuthUser(
                ((Number) user.get("id")).longValue(),
                String.valueOf(user.get("username")),
                String.valueOf(user.get("role_name")),
                status);
        String token = jwtUtil.generateToken(authUser);

        auditLogger.log(authUser.id(), "login", "用户 " + authUser.username() + " 登录成功");

        Map<String, Object> userOut = new LinkedHashMap<>();
        userOut.put("id", authUser.id());
        userOut.put("username", authUser.username());
        userOut.put("realName", user.get("real_name"));
        userOut.put("email", user.get("email"));
        userOut.put("role", authUser.role());
        userOut.put("status", authUser.status());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", userOut);
        return result;
    }

    private boolean checkLoginRate(String key) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().time > WINDOW_MS);
        if (!attempts.containsKey(key) && attempts.size() >= MAX_TRACKED_KEYS) {
            return false;
        }
        Attempt attempt = attempts.compute(key, (k, entry) -> {
            if (entry == null || now - entry.time > WINDOW_MS) {
                return new Attempt(1, now);
            }
            return new Attempt(entry.count + 1, entry.time);
        });
        return attempt.count <= MAX_ATTEMPTS;
    }

    private static final class Attempt {
        final int count;
        final long time;

        Attempt(int count, long time) {
            this.count = count;
            this.time = time;
        }
    }
}
