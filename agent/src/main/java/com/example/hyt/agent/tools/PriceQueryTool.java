package com.example.hyt.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 票价查询工具
 */
@Component
public class PriceQueryTool {

    // Mock 票价数据（简化版，实际应从数据库查询）
    private static final Map<String, Map<String, Double>> PRICE_TABLE = Map.of(
            "北京南-上海虹桥", Map.of("二等座", 553.0, "一等座", 933.0, "商务座", 1748.0),
            "北京-天津", Map.of("二等座", 55.5, "一等座", 88.5),
            "北京-广州", Map.of("硬座", 251.0, "硬卧", 423.0, "软卧", 655.0),
            "北京南-杭州东", Map.of("二等座", 538.5, "一等座", 906.5),
            "上海虹桥-杭州东", Map.of("二等座", 73.0, "一等座", 117.0)
    );

    @Tool("查询两站之间的票价")
    public Map<String, Double> queryPrice(
            @P("出发站") String fromStation,
            @P("到达站") String toStation
    ) {
        String key1 = fromStation + "-" + toStation;
        String key2 = toStation + "-" + fromStation;

        if (PRICE_TABLE.containsKey(key1)) {
            return PRICE_TABLE.get(key1);
        }
        if (PRICE_TABLE.containsKey(key2)) {
            return PRICE_TABLE.get(key2);
        }

        // 未找到时返回默认提示
        return Map.of("提示", -1.0);
    }
}