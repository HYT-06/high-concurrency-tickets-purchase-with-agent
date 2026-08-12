package com.example.hyt.agent.dto;

/**
 * 订单信息
 */
public record OrderInfo(
        String orderId,      // 订单号
        String trainNo,      // 车次号
        String fromStation,  // 出发站
        String toStation,    // 到达站
        String departureDate, // 出发日期
        String departureTime, // 出发时间
        String seatInfo,     // 座位信息
        double price,        // 票价
        String status        // 状态：PAID-已支付, USED-已乘车, CANCELLED-已取消
) {
}