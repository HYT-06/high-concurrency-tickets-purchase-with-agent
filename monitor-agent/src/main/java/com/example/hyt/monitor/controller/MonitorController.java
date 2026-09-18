package com.example.hyt.monitor.controller;

import com.example.hyt.monitor.service.MonitorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 监控 Agent 入口接口
 */
@RestController
@RequestMapping("/monitor/ask")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final MonitorAgent monitorAgent;

    public MonitorController(MonitorAgent monitorAgent) {
        this.monitorAgent = monitorAgent;
    }

    /**
     * 自然语言查询监控系统
     * POST /monitor/ask
     * Body: { "question": "最近5分钟系统正常吗" }
     */
    @PostMapping
    public Map<String, Object> ask(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", "question 不能为空");
            return result;
        }

        log.info("[MonitorController] 收到查询: {}", question);
        long start = System.currentTimeMillis();

        try {
            String reply = monitorAgent.chat(question);
            long cost = System.currentTimeMillis() - start;
            log.info("[MonitorController] 响应耗时: {}ms", cost);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("reply", reply);
            result.put("cost_ms", cost);
            return result;
        } catch (Exception e) {
            log.error("[MonitorController] 查询失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
