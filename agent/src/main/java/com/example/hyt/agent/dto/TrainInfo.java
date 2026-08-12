package com.example.hyt.agent.dto;

/**
 * 车次信息
 */
public record TrainInfo(
        String trainNo,      // 车次号，如 G1
        String fromStation,  // 出发站
        String toStation,    // 到达站
        String departureTime, // 出发时间
        String arriveTime,   // 到达时间
        int durationMinutes, // 时长（分钟）
        boolean hasTicket,   // 是否有余票
        String seatType,     // 座位类型
        double price         // 票价
) {
}