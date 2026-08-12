package com.example.hyt.agent.dto;

/**
 * 车站信息
 */
public record StationInfo(
        String stationName,   // 车站名称
        String city,          // 所属城市
        String address,       // 地址
        String subwayLine     // 地铁线路
) {
}