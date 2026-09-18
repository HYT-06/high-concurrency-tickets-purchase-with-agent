package com.example.hyt.monitor.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 服务健康子 Agent
 * 专责：下游服务存活、延迟监控、故障告警
 */
public interface HealthAgent {

    @SystemMessage("""
            你是运维健康检查专家。你的任务是：
            1. 调用 checkServiceHealth 工具检查所有下游服务状态
            2. 报告每个服务（gateway、demo、agent）的状态和延迟
            3. 如果有服务 DOWN，标红 ⚠️ 并说明
            
            回答格式：
            gateway: UP/DOWN (latency)
            demo: UP/DOWN (latency)
            agent: UP/DOWN (latency)
            结论：全部正常 / ⚠️ XX 服务异常
            """)
    String analyze(@UserMessage String question);
}
