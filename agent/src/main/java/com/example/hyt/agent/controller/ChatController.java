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
        String userId = request.getOrDefault("userId", "default");
        String message = request.get("message");

        log.info("[CHAIN] 🟢 REQUEST userId={} message=\"{}\"", userId, truncate(message, 100));

        long start = System.currentTimeMillis();
        OrderQueryTool.setUserId(userId);

        try {
            String reply = userChatService.chat(userId, message);
            long totalCost = System.currentTimeMillis() - start;

            log.info("[CHAIN] ✅ RESPONSE userId={} totalCost={}ms reply=\"{}\"",
                    userId, totalCost, truncate(reply, 200));
            return Map.of("success", true, "reply", reply);
        } catch (Exception e) {
            long totalCost = System.currentTimeMillis() - start;
            log.error("[CHAIN] ❌ FAILED userId={} totalCost={}ms error={}",
                    userId, totalCost, e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
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