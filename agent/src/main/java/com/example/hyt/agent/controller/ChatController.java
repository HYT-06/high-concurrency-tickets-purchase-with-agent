package com.example.hyt.agent.controller;

import com.example.hyt.agent.service.UserChatService;
import com.example.hyt.agent.tools.OrderQueryTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /**
     * 与网关 {@code JwtFilter} 约定的身份头。
     * <p>
     * 只能由网关校验 JWT 后注入——agent 直接暴露在 9090 端口上，
     * 但即便有人绕过网关直连，也拿不到合法身份（缺少该头会直接 401）。
     */
    private static final String USER_ID_HEADER = "X-User-Id";

    /** 前端未指定会话时的兜底值，保证只带 userId 的旧调用方仍能工作 */
    private static final String DEFAULT_SESSION = "default";

    @Autowired
    private UserChatService userChatService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
            @RequestBody Map<String, String> request) {

        // 身份一律以网关注入的请求头为准，绝不读取请求体里的 userId。
        // 请求体完全由客户端控制——这正是此前可以被伪造、越权查询他人订单的入口。
        if (userId == null || userId.isBlank()) {
            log.warn("[CHAIN] ⛔ REJECT 缺少 {} 请求头，该请求未经网关鉴权", USER_ID_HEADER);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "未认证：缺少网关注入的身份信息"));
        }

        String message = request.get("message");
        String sessionId = request.getOrDefault("sessionId", DEFAULT_SESSION);

        // 身份用于鉴权，会话用于隔离记忆窗口。
        // 此前两者都用 userId，导致同一用户的多标签页/多端共用一份 20 条消息的记忆，上下文互相污染。
        String memoryId = userId + ":" + sessionId;

        log.info("[CHAIN] 🟢 REQUEST userId={} session={} message=\"{}\"",
                userId, sessionId, truncate(message, 100));

        long start = System.currentTimeMillis();
        OrderQueryTool.setUserId(userId);

        try {
            String reply = userChatService.chat(memoryId, message);
            long totalCost = System.currentTimeMillis() - start;

            log.info("[CHAIN] ✅ RESPONSE userId={} session={} totalCost={}ms reply=\"{}\"",
                    userId, sessionId, totalCost, truncate(reply, 200));
            return ResponseEntity.ok(Map.of("success", true, "reply", reply));
        } catch (Exception e) {
            long totalCost = System.currentTimeMillis() - start;
            log.error("[CHAIN] ❌ FAILED userId={} totalCost={}ms error={}",
                    userId, totalCost, e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        } finally {
            OrderQueryTool.clearUserId();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        String oneLine = s.replaceAll("\\s+", " ").trim();
        return oneLine.length() > max ? oneLine.substring(0, max) + "..." : oneLine;
    }
}
