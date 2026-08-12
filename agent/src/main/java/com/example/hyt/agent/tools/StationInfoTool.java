package com.example.hyt.agent.tools;

import com.example.hyt.agent.dto.StationInfo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 车站信息查询工具
 */
@Component
public class StationInfoTool {

    // Mock 车站数据
    private static final List<StationInfo> MOCK_STATIONS = List.of(
            new StationInfo("北京南站", "北京", "北京市丰台区永外大街12号", "地铁4号线、14号线"),
            new StationInfo("北京站", "北京", "北京市东城区毛家湾胡同", "地铁2号线"),
            new StationInfo("上海虹桥站", "上海", "上海市闵行区申虹路", "地铁2号线、10号线"),
            new StationInfo("上海站", "上海", "上海市静安区秣陵路", "地铁1号线、3号线、4号线"),
            new StationInfo("天津站", "天津", "天津市河北区新纬路1号", "地铁2号线、3号线、9号线"),
            new StationInfo("广州南站", "广州", "广州市番禺区石壁镇", "地铁2号线、7号线")
    );

    @Tool("查询车站信息")
    public StationInfo queryStationInfo(
            @P("车站名称") String stationName
    ) {
        for (StationInfo station : MOCK_STATIONS) {
            if (station.stationName().contains(stationName) || stationName.contains(station.stationName())) {
                return station;
            }
        }
        return null;
    }

    @Tool("查询指定城市的所有车站")
    public List<StationInfo> queryStationsByCity(
            @P("城市名称") String city
    ) {
        return MOCK_STATIONS.stream()
                .filter(s -> s.city().contains(city) || city.contains(s.city()))
                .toList();
    }
}