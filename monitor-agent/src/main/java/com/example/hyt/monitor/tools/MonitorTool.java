package com.example.hyt.monitor.tools;

import com.example.hyt.monitor.service.MonitorService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 监控数据查询工具：直连 Redis 查询监控数据
 * 供子 Agent（TrafficAgent / SecurityAgent / HealthAgent）调用
 */
@Component
public class MonitorTool {

    private static final Logger log = LoggerFactory.getLogger(MonitorTool.class);

    private final MonitorService monitorService;

    public MonitorTool(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @Tool("查询最近 N 秒的流量统计，包括总请求数、平均QPS、限流命中数、错误数、各接口请求数")
    public String queryTrafficStats(@P("查询最近多少秒的数据") int seconds) {
        log.info("[MonitorTool] queryTrafficStats seconds={}", seconds);
        return monitorService.queryTrafficStats(seconds);
    }

    @Tool("查询异常用户列表，包括触发限流的用户及其命中次数")
    public String queryAbnormalUsers(@P("异常类型：rate_limited-限流命中, high_traffic-高频请求") String type,
                                      @P("查询最近多少秒的数据") int seconds) {
        log.info("[MonitorTool] queryAbnormalUsers type={} seconds={}", type, seconds);
        return monitorService.queryAbnormalUsers(type, seconds);
    }

    @Tool("查询单个用户的行为详情，包括请求路径、状态码、耗时")
    public String queryUserDetail(@P("用户ID") String userId,
                                   @P("查询最近多少秒的数据") int seconds) {
        log.info("[MonitorTool] queryUserDetail userId={} seconds={}", userId, seconds);
        return monitorService.queryUserDetail(userId, seconds);
    }

    @Tool("检查所有下游服务健康状态，包括 gateway、demo、agent 三个服务")
    public String checkServiceHealth() {
        log.info("[MonitorTool] checkServiceHealth");
        return monitorService.checkServiceHealth();
    }
}
