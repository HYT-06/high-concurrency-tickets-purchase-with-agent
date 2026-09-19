package com.example.hyt.monitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;

/**
 * 直连 Redis 查询监控数据（不再通过 gateway HTTP 接口）
 * 逻辑与 gateway 的 MonitorController 一致
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${monitor.key-prefix:monitor}")
    private String keyPrefix;

    public MonitorService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询最近 N 秒的流量统计
     */
    public String queryTrafficStats(int seconds) {
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

        return toJson(success(data));
    }

    /**
     * 查询异常用户（限流命中的用户）
     */
    public String queryAbnormalUsers(String type, int seconds) {
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

        abusers.sort((a, b) -> Long.compare(
                (long) b.get("rate_limit_hits"),
                (long) a.get("rate_limit_hits")));

        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("seconds", seconds);
        data.put("abnormal_users", abusers);

        return toJson(success(data));
    }

    /**
     * 查询单个用户的行为详情
     */
    public String queryUserDetail(String userId, int seconds) {
        long now = Instant.now().getEpochSecond();
        long since = now - seconds;

        String userKey = keyPrefix + ":user:" + userId;
        Set<Object> behaviors = redisTemplate.opsForZSet().rangeByScore(userKey, since, now);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("seconds", seconds);
        data.put("request_count", behaviors != null ? behaviors.size() : 0);
        data.put("behaviors", behaviors != null ? new ArrayList<>(behaviors) : Collections.emptyList());

        return toJson(success(data));
    }

    /**
     * 检查下游服务健康状态
     */
    public String checkServiceHealth() {
        Map<String, Object> services = new HashMap<>();

        services.put("gateway", checkHealth("localhost", 10010));
        services.put("demo", checkHealth("localhost", 8080));
        services.put("agent", checkHealth("localhost", 9090));

        boolean allHealthy = services.values().stream()
                .allMatch(s -> "UP".equals(((Map<?, ?>) s).get("status")));

        Map<String, Object> data = new HashMap<>();
        data.put("overall", allHealthy ? "UP" : "DOWN");
        data.put("services", services);

        return toJson(success(data));
    }

    private Map<String, Object> checkHealth(String host, int port) {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
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

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"error\":\"JSON序列化失败\"}";
        }
    }
}
