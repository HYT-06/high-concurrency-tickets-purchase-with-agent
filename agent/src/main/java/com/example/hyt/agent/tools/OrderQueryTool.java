package com.example.hyt.agent.tools;

import com.example.hyt.agent.dto.OrderInfo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单查询工具
 */
@Component
public class OrderQueryTool {

    // 用户上下文，用于传递当前登录用户ID
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    public static void setUserId(String userId) {
        currentUserId.set(userId);
    }

    public static void clearUserId() {
        currentUserId.remove();
    }

    // Mock 订单数据
    private static final List<OrderInfo> MOCK_ORDERS = List.of(
            new OrderInfo("ORD001", "G1", "北京南", "上海虹桥", "2026-07-20", "06:36", "12车03A", 553.0, "PAID"),
            new OrderInfo("ORD002", "D1", "北京", "天津", "2026-07-21", "07:30", "05车10B", 55.5, "PAID"),
            new OrderInfo("ORD003", "K1", "北京", "广州", "2026-07-15", "18:00", "08车15号", 423.0, "USED"),
            new OrderInfo("ORD004", "G3", "北京南", "上海虹桥", "2026-07-22", "07:00", "10车08F", 553.0, "PAID")
    );

    @Tool("查询当前用户的订单列表")
    public List<OrderInfo> queryOrder(
            @P("订单状态：ALL-全部, PAID-已支付, USED-已乘车, CANCELLED-已取消") String status
    ) {
        String userId = currentUserId.get();
        // 实际场景中应根据 userId 查询，这里返回 Mock 数据
        if (status == null || "ALL".equalsIgnoreCase(status)) {
            return MOCK_ORDERS;
        }
        List<OrderInfo> result = new ArrayList<>();
        for (OrderInfo order : MOCK_ORDERS) {
            if (order.status().equalsIgnoreCase(status)) {
                result.add(order);
            }
        }
        return result;
    }

    @Tool("根据订单号查询订单详情")
    public OrderInfo queryOrderById(
            @P("订单号") String orderId
    ) {
        for (OrderInfo order : MOCK_ORDERS) {
            if (order.orderId().equals(orderId)) {
                return order;
            }
        }
        return null;
    }
}