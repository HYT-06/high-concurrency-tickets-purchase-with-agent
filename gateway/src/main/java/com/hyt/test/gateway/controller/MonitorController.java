package com.hyt.test.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 监控查询接口：供 Monitor Agent 的 Tool 调用
 * <p>
 * 所有接口返回统一 JSON 格式：
 * { "success": true/false, "data": {...}, "message": "..." }
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${monitor.key-prefix:monitor}")
    private String keyPrefix;

    @Value("${monitor.retention-seconds:300}")
    private int retentionSeconds;

    public MonitorController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询最近 N 秒的流量统计
     * GET /monitor/traffic?seconds=60
     */
    @GetMapping("/traffic")
    public Map<String, Object> getTrafficStats(@RequestParam(defaultValue = "60") int seconds) {
        long now = Instant.now().getEpochSecond();
        long since = now - seconds;

        long totalRequests = 0;
        long totalRateLimited = 0;
        long totalErrors = 0;
        Map<String, Long> perPath = new HashMap<>();
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (long ts = since; ts <= now; ts++) {
            String key = keyPrefix + ":traffic:" + ts;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

            if (!entries.isEmpty()) {
                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", ts);
                long total = getLong(entries, "total");
                point.put("total", total);
                point.put("rate_limited", getLong(entries, "rate_limited"));
                point.put("errors", getLong(entries, "error_count"));
                timeline.add(point);

                totalRequests += total;
                totalRateLimited += getLong(entries, "rate_limited");
                totalErrors += getLong(entries, "error_count");

                // 按路径聚合
                for (Map.Entry<Object, Object> e : entries.entrySet()) {
                    String field = String.valueOf(e.getKey());
                    if (!field.equals("total") && !field.equals("rate_limited") && !field.equals("error_count")) {
                        perPath.merge(field, getLong(entries, field), Long::sum);
                    }
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("seconds", seconds);
        data.put("total_requests", totalRequests);
        data.put("avg_qps", seconds > 0 ? Math.round(totalRequests * 100.0 / seconds) / 100.0 : 0);
        data.put("rate_limited", totalRateLimited);
        data.put("errors", totalErrors);
        data.put("per_path", perPath);
        data.put("timeline", timeline);

        return success(data);
    }

    /**
     * 查询异常用户（高频请求或频繁触发限流）
     * GET /monitor/users?type=rate_limited&seconds=60
     */
    @GetMapping("/users")
    public Map<String, Object> getUserBehavior(
            @RequestParam(defaultValue = "high_traffic") String type,
            @RequestParam(defaultValue = "60") int seconds) {
        long now = Instant.now().getEpochSecond();
        long since = now - seconds;

        // 扫描所有 monitor:ratelimit:* 的 key
        Set<String> ratelimitKeys = redisTemplate.keys(keyPrefix + ":ratelimit:*");
        List<Map<String, Object>> abusers = new ArrayList<>();

        if (ratelimitKeys != null) {
            for (String key : ratelimitKeys) {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                long hitCount = getLong(entries, "hit_count");
                String lastHit = String.valueOf(entries.get("last_hit"));

                if (hitCount > 0) {
                    String userId = key.substring((keyPrefix + ":ratelimit:").length());
                    Map<String, Object> user = new HashMap<>();
                    user.put("userId", userId);
                    user.put("rate_limit_hits", hitCount);
                    user.put("last_hit", lastHit);
                    abusers.add(user);
                }
            }
        }

        // 按命中次数降序
        abusers.sort((a, b) -> Long.compare(
                (long) b.get("rate_limit_hits"),
                (long) a.get("rate_limit_hits")));

        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("seconds", seconds);
        data.put("abnormal_users", abusers);

        return success(data);
    }

    /**
     * 查询单个用户的详细行为记录
     * GET /monitor/users/{userId}/detail?seconds=60
     */
    @GetMapping("/users/{userId}/detail")
    public Map<String, Object> getUserDetail(
            @PathVariable String userId,
            @RequestParam(defaultValue = "60") int seconds) {
        long now = Instant.now().getEpochSecond();
        long since = now - seconds;

        String userKey = keyPrefix + ":user:" + userId;
        Set<Object> behaviors = redisTemplate.opsForZSet().rangeByScore(userKey, since, now);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("seconds", seconds);
        data.put("request_count", behaviors != null ? behaviors.size() : 0);
        data.put("behaviors", behaviors != null ? new ArrayList<>(behaviors) : Collections.emptyList());

        return success(data);
    }

    /**
     * 健康检查：下游服务状态
     * GET /monitor/health
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> services = new HashMap<>();

        services.put("gateway", checkHealth("localhost", 10010));
        services.put("demo", checkHealth("localhost", 8080));
        services.put("agent", checkHealth("localhost", 9090));

        boolean allHealthy = services.values().stream()
                .allMatch(s -> "UP".equals(((Map<?, ?>) s).get("status")));

        Map<String, Object> data = new HashMap<>();
        data.put("overall", allHealthy ? "UP" : "DOWN");
        data.put("services", services);

        return success(data);
    }

    /**
     * 简单 TCP 连通性检查
     */
    private Map<String, Object> checkHealth(String host, int port) {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 2000);
            result.put("status", "UP");
            result.put("latency_ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }

    private long getLong(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
