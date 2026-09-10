package com.enterprise.kb.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具，等价 utils/auth.js：签发与校验，并在启动时强校验密钥。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-days:7}") long expirationDays) {
        if (secret == null || secret.isBlank() || "default-secret".equals(secret)) {
            throw new IllegalStateException(
                    "[安全] 请在 application.yml 中配置强随机 app.jwt.secret，禁止使用默认值");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "[安全] app.jwt.secret 长度不足 32 字符（HS256 要求 256 位密钥），请加长密钥");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationDays * 24L * 60 * 60 * 1000;
    }

    public String generateToken(AuthUser user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claim("id", user.id())
                .claim("username", user.username())
                .claim("role", user.role())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMillis))
                .signWith(key)
                .compact();
    }

    public Claims verifyToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
