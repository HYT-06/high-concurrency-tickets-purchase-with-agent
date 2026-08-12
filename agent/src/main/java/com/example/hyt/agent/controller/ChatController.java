package com.example.hyt.agent.controller;

import com.example.hyt.agent.service.UserChatService;
import com.example.hyt.agent.tools.OrderQueryTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private UserChatService userChatService;

    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        log.info("=== 收到 POST /chat ===");
        log.info("userId: {}", request.getOrDefault("userId", "N/A"));
        log.info("message: {}", request.getOrDefault("message", "N/A"));

        String userId = request.getOrDefault("userId", "default");
        String message = request.get("message");

        // 设置用户上下文（用于工具调用）
        OrderQueryTool.setUserId(userId);

        try {
            log.info("调用 AI 服务...");
            String reply = userChatService.chat(userId, message);
            log.info("AI 回复长度: {}", reply != null ? reply.length() : 0);
            return Map.of("success", true, "reply", reply);
        } catch (Exception e) {
            log.error("处理请求异常", e);
            return Map.of("success", false, "error", e.getMessage());
        } finally {
            // 清理用户上下文
            OrderQueryTool.clearUserId();
        }
    }
}