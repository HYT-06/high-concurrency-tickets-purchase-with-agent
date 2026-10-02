package com.hyt.test.demo.config;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.hyt.test.demo.common.Result;
import com.hyt.test.demo.common.User;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtConfig {

    /**
     * JWT 签名密钥。原实现硬编码在源码里并随仓库公开——
     * 一旦泄露，任何人都能伪造 token 冒充任意用户通过网关鉴权。
     * 现改为从配置读取：本地值放 application-local.yml（gitignore），生产用环境变量注入。
     */
    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUserName());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUserName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
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
                .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}