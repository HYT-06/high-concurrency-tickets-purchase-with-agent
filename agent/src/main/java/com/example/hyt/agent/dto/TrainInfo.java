package com.example.hyt.agent.dto;

/**
 * 车次信息。
 * <p>
 * 字段与 demo 库 {@code train} 表一一对应。原先的 mock 版本带有
 * departureTime / arriveTime / durationMinutes / hasTicket / seatType 等字段，
 * 但真实表里并不存在这些列，因此改为按真实 schema 定义，
 * 避免工具返回编造出来的数据。
 */
public record TrainInfo(
        String trainNo,      // 车次号，如 G101
        String name,         // 车次名称
        String type,         // 车型：高铁/动车/普速
        String fromStation,  // 出发站
        String toStation,    // 到达站
        String duration,     // 运行时长（原始文本）
        double price,        // 票价
        String travelDate,   // 发车日期
        String status        // 状态：正常/停运
) {
}
