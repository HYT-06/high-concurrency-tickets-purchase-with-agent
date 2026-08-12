package com.example.hyt.agent.tracing;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * LangChain4j 原生 ChatModelListener 实现，记录 LLM 调用全生命周期。
 * 能拿到 token 用量、provider、model 等 AOP 拿不到的细节。
 */
@Component
public class LoggingChatModelListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingChatModelListener.class);

    private static final String ATTR_START_TIME = "trace_start_time";

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        requestContext.attributes().put(ATTR_START_TIME, Instant.now());

        StringBuilder sb = new StringBuilder();
        sb.append("[LLM] ➡ 发送请求\n");
        sb.append("      model    : ").append(requestContext.request().model()).append("\n");
        sb.append("      messages : ");
        int msgCount = requestContext.request().messages().size();
        for (int i = 0; i < msgCount; i++) {
            var m = requestContext.request().messages().get(i);
            sb.append("\n        [").append(i).append("] ").append(m.type()).append(": ")
                    .append(truncate(m.text(), 120));
        }
        log.info(sb.toString());
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        Instant start = (Instant) responseContext.attributes().get(ATTR_START_TIME);
        long costMs = start != null ? Duration.between(start, Instant.now()).toMillis() : -1;

        var resp = responseContext.response();
        TokenUsage usage = resp.tokenUsage();

        StringBuilder sb = new StringBuilder();
        sb.append("[LLM] ✅ 收到响应 (").append(costMs).append("ms)\n");
        sb.append("      content  : ").append(truncate(resp.aiMessage().text(), 200)).append("\n");
        if (usage != null) {
            sb.append("      tokens   : prompt=").append(usage.inputTokenCount())
                    .append(", completion=").append(usage.outputTokenCount())
                    .append(", total=").append(usage.totalTokenCount()).append("\n");
        }
        sb.append("      finish   : ").append(resp.finishReason());
        log.info(sb.toString());
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        Instant start = (Instant) errorContext.attributes().get(ATTR_START_TIME);
        long costMs = start != null ? Duration.between(start, Instant.now()).toMillis() : -1;

        log.error("[LLM] ❌ 调用失败 ({}ms) {}", costMs,
                errorContext.error().getMessage(), errorContext.error());
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > maxLen ? oneLine.substring(0, maxLen) + "..." : oneLine;
    }
}