package com.hyt.test.gateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.google.common.util.concurrent.RateLimiter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtFilter implements Filter {

    private static final String SECRET = "REDACTED_JWT_SECRET";

    @Value("${rate-limit.requests-per-second:10.0}")
    private double requestsPerSecond;

    @Value("${https.redirect-enabled:false}")
    private boolean httpsRedirectEnabled;

    private final ConcurrentHashMap<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // HTTPS 重定向
        if (httpsRedirectEnabled && !isHttpsRequest(httpRequest)) {
            String httpsUrl = buildHttpsUrl(httpRequest);
            httpResponse.sendRedirect(httpsUrl);
            return;
        }

        // 白名单路径（不需要 token，不限流）
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        if (isWhitelisted(path)) {
            if (path.startsWith("/chat")) {
                System.out.println("[Gateway] /chat 请求通过白名单: " + method + " " + path);
            }
            chain.doFilter(request, response);
            return;
        }

        // 从请求头获取 token
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);

        // 校验 token 并提取用户 ID
        Claims claims = parseToken(token);
        if (claims == null) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        // 从 token 中获取用户 ID（作为限流的 key）
        Object userIdObj = claims.get("id");
        String userId = userIdObj != null ? String.valueOf(userIdObj) : claims.getSubject();

        // 基于用户的令牌桶限流
        RateLimiter rateLimiter = userRateLimiters.computeIfAbsent(userId,
            k -> RateLimiter.create(requestsPerSecond));

        if (!rateLimiter.tryAcquire()) {
            httpResponse.setStatus(HttpServletResponse.SC_REQUEST_TIMEOUT);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 检查路径是否在白名单中（无需鉴权）
     */
    private boolean isWhitelisted(String path) {
        // 登录/注册 API
        if (path.startsWith("/user/login") || path.startsWith("/user/register")) {
            return true;
        }
        // 静态资源 — HTML 页面
        if (path.endsWith(".html")) {
            return true;
        }
        // 静态资源 — CSS / JS / 图片 / 字体
        if (path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/img/") || path.startsWith("/fonts/")
                || path.startsWith("/favicon.ico")) {
            return true;
        }
        // 智能客服 API（由前端 widget 携带 token 鉴权）
        if (path.startsWith("/chat")) {
            return true;
        }
        // 根路径
        if (path.equals("/") || path.isEmpty()) {
            return true;
        }
        return false;
    }

    private boolean isHttpsRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String xForwardedProto = request.getHeader("X-Forwarded-Proto");
        return xForwardedProto != null && "https".equalsIgnoreCase(xForwardedProto);
    }

    private String buildHttpsUrl(HttpServletRequest request) {
        String serverName = request.getServerName();
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();

        StringBuilder httpsUrl = new StringBuilder("https://");
        httpsUrl.append(serverName);
        httpsUrl.append(requestUri);

        if (queryString != null) {
            httpsUrl.append("?").append(queryString);
        }

        return httpsUrl.toString();
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
