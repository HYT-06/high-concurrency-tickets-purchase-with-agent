package com.example.hyt.monitor.config;

import com.example.hyt.monitor.service.*;
import com.example.hyt.monitor.tools.MonitorTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class MonitorAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(MonitorAgentConfig.class);

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;
    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;
    @Value("${monitor.orchestrator-model}")
    private String orchestratorModel;
    @Value("${monitor.sub-agent-model}")
    private String subAgentModel;
    @Value("${langchain4j.open-ai.chat-model.temperature:0.3}")
    private double temperature;

    // ==================== ChatModel ====================

    /**
     * 主 Agent 模型（高级，负责路由决策+结果汇总）
     */
    @Bean(name = "orchestratorChatModel")
    @Primary
    public ChatLanguageModel orchestratorChatModel() {
        log.info("[MonitorConfig] 主 Agent 模型: {}", orchestratorModel);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(orchestratorModel)
                .temperature(temperature)
                .maxTokens(1024)
                .build();
    }

    /**
     * 子 Agent 模型（低成本，负责窄域分析）
     */
    @Bean(name = "subAgentChatModel")
    public ChatLanguageModel subAgentChatModel() {
        log.info("[MonitorConfig] 子 Agent 模型: {}", subAgentModel);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(subAgentModel)
                .temperature(temperature)
                .maxTokens(512)
                .build();
    }

    // ==================== 子 Agent ====================

    @Bean
    public TrafficAgent trafficAgent(ChatLanguageModel subAgentChatModel, MonitorTool monitorTool) {
        return AiServices.builder(TrafficAgent.class)
                .chatLanguageModel(subAgentChatModel)
                .tools(monitorTool)
                .build();
    }

    @Bean
    public SecurityAgent securityAgent(ChatLanguageModel subAgentChatModel, MonitorTool monitorTool) {
        return AiServices.builder(SecurityAgent.class)
                .chatLanguageModel(subAgentChatModel)
                .tools(monitorTool)
                .build();
    }

    @Bean
    public HealthAgent healthAgent(ChatLanguageModel subAgentChatModel, MonitorTool monitorTool) {
        return AiServices.builder(HealthAgent.class)
                .chatLanguageModel(subAgentChatModel)
                .tools(monitorTool)
                .build();
    }

    // ==================== 主 Agent ====================

    /**
     * 主 Agent：把 3 个子 Agent 包装成 Tool 供主 Agent 调用
     */
    @Bean
    public MonitorAgent monitorAgent(ChatLanguageModel orchestratorChatModel,
                                     TrafficAgent trafficAgent,
                                     SecurityAgent securityAgent,
                                     HealthAgent healthAgent) {
        Object orchestratorTools = new OrchestratorTools(trafficAgent, securityAgent, healthAgent);
        return AiServices.builder(MonitorAgent.class)
                .chatLanguageModel(orchestratorChatModel)
                .tools(orchestratorTools)
                .build();
    }

    /**
     * 主 Agent 的 Tool 包装类：把子 Agent 调用包装成 @Tool 方法
     */
    public static class OrchestratorTools {

        private final TrafficAgent trafficAgent;
        private final SecurityAgent securityAgent;
        private final HealthAgent healthAgent;

        public OrchestratorTools(TrafficAgent trafficAgent,
                                  SecurityAgent securityAgent,
                                  HealthAgent healthAgent) {
            this.trafficAgent = trafficAgent;
            this.securityAgent = securityAgent;
            this.healthAgent = healthAgent;
        }

        @dev.langchain4j.agent.tool.Tool("调用流量分析Agent，分析最近流量趋势、QPS、突发流量、接口热度")
        public String askTrafficAgent(String question) {
            log.info("[Orchestrator] → TrafficAgent: {}", question);
            String result = trafficAgent.analyze(question);
            log.info("[Orchestrator] ← TrafficAgent: {}", result);
            return result;
        }

        @dev.langchain4j.agent.tool.Tool("调用安全检测Agent，分析异常用户、刷票行为、限流命中情况")
        public String askSecurityAgent(String question) {
            log.info("[Orchestrator] → SecurityAgent: {}", question);
            String result = securityAgent.analyze(question);
            log.info("[Orchestrator] ← SecurityAgent: {}", result);
            return result;
        }

        @dev.langchain4j.agent.tool.Tool("调用健康检查Agent，检查所有下游服务状态和延迟")
        public String askHealthAgent(String question) {
            log.info("[Orchestrator] → HealthAgent: {}", question);
            String result = healthAgent.analyze(question);
            log.info("[Orchestrator] ← HealthAgent: {}", result);
            return result;
        }
    }
}
