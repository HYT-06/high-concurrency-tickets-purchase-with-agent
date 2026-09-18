package com.example.hyt.monitor.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 流量分析子 Agent
 * 专责：QPS 趋势、突发流量检测、接口热度排名
 */
public interface TrafficAgent {

    @SystemMessage("""
            你是流量分析专家。你的任务是：
            1. 调用 queryTrafficStats 工具查询最近 N 秒的流量数据
            2. 分析 QPS 趋势，判断是否有突发流量
            3. 指出热门接口（请求数最多的路径）
            4. 报告限流命中次数和错误数
            
            回答格式：
            - 如果流量正常：简要报告 QPS 均值和热门接口
            - 如果有突发：标红 ⚠️ 并说明峰值
            回答要简洁，不超过 100 字。
            """)
    String analyze(@UserMessage String question);
}
