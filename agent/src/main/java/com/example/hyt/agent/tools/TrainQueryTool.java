package com.example.hyt.agent.tools;

import com.example.hyt.agent.dto.TrainInfo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 车次查询工具
 */
@Component
public class TrainQueryTool {

    // Mock 车次数据
    private static final List<TrainInfo> MOCK_TRAINS = List.of(
            new TrainInfo("G1", "北京南", "上海虹桥", "06:36", "12:36", 360, true, "二等座", 553.0),
            new TrainInfo("G3", "北京南", "上海虹桥", "07:00", "13:00", 360, true, "二等座", 553.0),
            new TrainInfo("G5", "北京南", "上海虹桥", "08:00", "14:00", 360, false, "二等座", 553.0),
            new TrainInfo("D1", "北京", "天津", "07:30", "08:30", 60, true, "二等座", 55.5),
            new TrainInfo("D3", "北京", "天津", "09:00", "10:00", 60, true, "二等座", 55.5),
            new TrainInfo("K1", "北京", "广州", "18:00", "20:00", 1560, true, "硬卧", 423.0)
    );

    @Tool("查询指定日期、出发站到到达站的车次余票信息")
    public List<TrainInfo> queryTrains(
            @P("出发站") String fromStation,
            @P("到达站") String toStation,
            @P("日期，格式yyyy-MM-dd") String date
    ) {
        // 验证日期格式
        try {
            LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return List.of();
        }

        // 过滤车次
        List<TrainInfo> result = new ArrayList<>();
        for (TrainInfo train : MOCK_TRAINS) {
            if (train.fromStation().contains(fromStation) || train.toStation().contains(toStation)) {
                result.add(train);
            }
        }
        return result;
    }

    @Tool("查询指定车次的详细信息")
    public TrainInfo queryTrainDetail(
            @P("车次号") String trainNo
    ) {
        for (TrainInfo train : MOCK_TRAINS) {
            if (train.trainNo().equalsIgnoreCase(trainNo)) {
                return train;
            }
        }
        return null;
    }
}