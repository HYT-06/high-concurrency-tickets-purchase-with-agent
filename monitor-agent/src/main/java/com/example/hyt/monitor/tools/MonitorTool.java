package com.example.hyt.monitor.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 监控数据查询工具：HTTP 调用 gateway 的 /monitor/** 接口
 * 供子 Agent（TrafficAgent / SecurityAgent / HealthAgent）调用
 */
@Component
public class MonitorTool {

    private static final Logger log = LoggerFactory.getLogger(MonitorTool.class);

    private final RestClient restClient;

    public MonitorTool(@Value("${monitor.gateway-url}") String gatewayUrl) {
        this.restClient = RestClient.builder().baseUrl(gatewayUrl).build();
    }

    @Tool("查询最近 N 秒的流量统计，包括总请求数、平均QPS、限流命中数、错误数、各接口请求数")
    public String queryTrafficStats(@P("查询最近多少秒的数据") int seconds) {
        log.debug("[MonitorTool] queryTrafficStats seconds={}", seconds);
        try {
            String result = restClient.get()
                    .uri("/monitor/traffic?seconds={seconds}", seconds)
                    .retrieve()
                    .body(String.class);
            log.debug("[MonitorTool] traffic response length={}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.error("[MonitorTool] queryTrafficStats failed", e);
            return "{\"error\":\"查询流量统计失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool("查询异常用户列表，包括触发限流的用户及其命中次数")
    public String queryAbnormalUsers(@P("异常类型：rate_limited-限流命中, high_traffic-高频请求") String type,
                                      @P("查询最近多少秒的数据") int seconds) {
        log.debug("[MonitorTool] queryAbnormalUsers type={} seconds={}", type, seconds);
        try {
            String result = restClient.get()
                    .uri("/monitor/users?type={type}&seconds={seconds}", type, seconds)
                    .retrieve()
                    .body(String.class);
            return result;
        } catch (Exception e) {
            log.error("[MonitorTool] queryAbnormalUsers failed", e);
            return "{\"error\":\"查询异常用户失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool("查询单个用户的行为详情，包括请求路径、状态码、耗时")
    public String queryUserDetail(@P("用户ID") String userId,
                                   @P("查询最近多少秒的数据") int seconds) {
        log.debug("[MonitorTool] queryUserDetail userId={} seconds={}", userId, seconds);
        try {
            String result = restClient.get()
                    .uri("/monitor/users/{userId}/detail?seconds={seconds}", userId, seconds)
                    .retrieve()
                    .body(String.class);
            return result;
        } catch (Exception e) {
            log.error("[MonitorTool] queryUserDetail failed", e);
            return "{\"error\":\"查询用户详情失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool("检查所有下游服务健康状态，包括 gateway、demo、agent 三个服务")
    public String checkServiceHealth() {
        log.debug("[MonitorTool] checkServiceHealth");
        try {
            String result = restClient.get()
                    .uri("/monitor/health")
                    .retrieve()
                    .body(String.class);
            return result;
        } catch (Exception e) {
            log.error("[MonitorTool] checkServiceHealth failed", e);
            return "{\"error\":\"健康检查失败: " + e.getMessage() + "\"}";
        }
    }
}
