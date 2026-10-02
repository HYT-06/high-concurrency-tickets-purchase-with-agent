package com.hyt.test.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 流量采集过滤器：拦截所有请求，记录指标到 Redis
 * <p>
 * Redis 数据结构：
 * 1. monitor:traffic:{epochSecond} → Hash，每秒一个桶
 *    字段: total, METHOD:/path → count, rate_limited, error_count
 * 2. monitor:user:{userId} → Sorted Set，score=timestamp, value=JSON行为记录
 * 3. monitor:ratelimit:{userId} → Hash，限流命中计数
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)  // 在 JwtFilter 之后执行
public class TrafficMetricsFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TrafficMetricsFilter.class);

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${monitor.traffic-enabled:true}")
    private boolean trafficEnabled;

    @Value("${monitor.key-prefix:monitor}")
    private String keyPrefix;

    @Value("${monitor.retention-seconds:300}")
    private int retentionSeconds;

    /** JWT 签名密钥，用于解析 token 得到 userId 以便按用户记流量。原为硬编码，现从配置读取 */
    @Value("${jwt.secret}")
    private String secret;

    public TrafficMetricsFilter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!trafficEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        long startTime = System.currentTimeMillis();
        long epochSecond = Instant.now().getEpochSecond();

        // 用 wrapper 捕获最终状态码
        StatusCaptureWrapper responseWrapper = new StatusCaptureWrapper((HttpServletResponse) response);

        try {
            chain.doFilter(request, responseWrapper);
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            int status = responseWrapper.getStatus();
            String path = httpRequest.getRequestURI();
            String method = httpRequest.getMethod();
            String userId = extractUserId(httpRequest);

            try {
                recordTraffic(epochSecond, path, method, status, cost, userId);
            } catch (Exception e) {
                log.warn("[Monitor] Redis 记录失败，不影响正常请求: {}", e.getMessage());
            }
        }
    }

    /**
     * 记录流量数据到 Redis
     */
    private void recordTraffic(long epochSecond, String path, String method,
                                int status, long cost, String userId) {
        String trafficKey = keyPrefix + ":traffic:" + epochSecond;

        // 1. 每秒流量桶（Hash）
        redisTemplate.opsForHash().increment(trafficKey, "total", 1);
        redisTemplate.opsForHash().increment(trafficKey, method + ":" + path, 1);
        if (status == 408) {
            redisTemplate.opsForHash().increment(trafficKey, "rate_limited", 1);
        }
        if (status >= 500) {
            redisTemplate.opsForHash().increment(trafficKey, "error_count", 1);
        }
        // 设置过期时间
        redisTemplate.expire(trafficKey, retentionSeconds, TimeUnit.SECONDS);

        // 2. 用户行为追踪（Sorted Set）
        if (!"anonymous".equals(userId)) {
            String userKey = keyPrefix + ":user:" + userId;
            String behavior = String.format("{\"ts\":%d,\"path\":\"%s\",\"method\":\"%s\",\"status\":%d,\"cost\":%d}",
                    epochSecond, path, method, status, cost);
            redisTemplate.opsForZSet().add(userKey, behavior, epochSecond);
            redisTemplate.expire(userKey, retentionSeconds, TimeUnit.SECONDS);

            // 3. 限流命中记录
            if (status == 408) {
                String ratelimitKey = keyPrefix + ":ratelimit:" + userId;
                redisTemplate.opsForHash().increment(ratelimitKey, "hit_count", 1);
                redisTemplate.opsForHash().put(ratelimitKey, "last_hit", String.valueOf(epochSecond));
                redisTemplate.expire(ratelimitKey, retentionSeconds, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * 从 JWT 中提取 userId（如果有的话）
     */
    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "anonymous";
        }
        try {
            String token = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object userIdObj = claims.get("id");
            return userIdObj != null ? String.valueOf(userIdObj) : claims.getSubject();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    /**
     * 响应包装器：捕获最终状态码
     */
    private static class StatusCaptureWrapper extends HttpServletResponseWrapper {
        private int status = 200;

        public StatusCaptureWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            super.sendError(sc, msg);
        }

        @Override
        public int getStatus() {
            return this.status;
        }
    }
}
