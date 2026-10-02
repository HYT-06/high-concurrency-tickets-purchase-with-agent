package com.example.hyt.agent.dto;

/**
 * 订单信息。
 * <p>
 * {@code order} 表本身只有 user_id / train_id / seat_id 等外键，
 * 车次号、站名、座位号需要 JOIN {@code train}、{@code seat} 取得，
 * 因此这里定义的是"订单视图"，而不是单表映射。
 */
public record OrderInfo(
        String orderId,     // 订单号
        String trainNo,     // 车次号
        String fromStation, // 出发站
        String toStation,   // 到达站
        String travelDate,  // 乘车日期
        String seatNumber,  // 座位号
        double price,       // 订单金额
        String status,      // 订单状态
        String createdAt    // 下单时间
) {
}
