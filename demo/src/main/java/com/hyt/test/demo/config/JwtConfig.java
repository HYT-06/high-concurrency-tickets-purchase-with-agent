package com.hyt.test.demo.config;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hyt.test.demo.common.Result;
import com.hyt.test.demo.common.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtConfig {
    private final String SECRET = "REDACTED_JWT_SECRET";

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUserName());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUserName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
    }
    /**
     * 校验 token 是否有效（给 Gateway 使用）
     * @param token JWT token
     * @return true 有效，false 无效或过期
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}