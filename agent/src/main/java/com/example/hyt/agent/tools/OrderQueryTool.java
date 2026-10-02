package com.example.hyt.agent.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.hyt.agent.dto.OrderInfo;
import com.example.hyt.agent.repository.TicketRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 订单查询工具。
 * <p>
 * 已从硬编码 mock 数据改为查询 demo 库的 {@code order} 表，
 * 并 JOIN 车次与座位表补齐站名、座位号。
 * <p>
 * 用户身份通过 {@link ThreadLocal} 由 {@code ChatController} 注入，
 * 刻意不作为工具参数暴露——否则 LLM 可以伪造 userId 去查他人订单。
 */
@Component
public class OrderQueryTool {

    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    public static void setUserId(String userId) {
        currentUserId.set(userId);
    }

    public static void clearUserId() {
        currentUserId.remove();
    }

    /**
     * 取当前线程的用户身份，缺失时快速失败。
     * <p>
     * 之所以抛异常而不是返回空列表：缺失身份会静默表现为"该用户没有订单"，
     * 与真实的无订单无法区分。典型成因是把工具改成异步或流式执行——
     * ThreadLocal 不跨线程传递，那样 {@code get()} 会静默返回 null。
     */
    private static String requireUserId() {
        String userId = currentUserId.get();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException(
                    "当前线程未注入用户身份，无法查询订单。请确认调用入口已执行 "
                            + "OrderQueryTool.setUserId(...)，且工具仍在请求线程上同步执行。");
        }
        return userId;
    }

    private final TicketRepository ticketRepository;

    public OrderQueryTool(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Tool("查询当前登录用户的订单列表；返回空列表表示该用户没有订单")
    public List<OrderInfo> queryOrder(
            @P("订单状态过滤，如 pending-待支付、paid-已支付、cancelled-已取消；传 ALL 表示全部") String status
    ) {
        return ticketRepository.findOrders(requireUserId(), status);
    }

    @Tool("根据订单号查询订单详情，返回列表；返回空列表表示未找到该订单")
    public List<OrderInfo> queryOrderById(
            @P("订单号") String orderId
    ) {
        return ticketRepository.findOrderById(orderId);
    }
}
