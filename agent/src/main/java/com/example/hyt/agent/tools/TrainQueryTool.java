package com.example.hyt.agent.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.hyt.agent.dto.TrainInfo;
import com.example.hyt.agent.repository.TicketRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 车次查询工具。
 * <p>
 * 已从硬编码 mock 数据改为查询 demo 库的 {@code train} 表。
 */
@Component
public class TrainQueryTool {

    private final TicketRepository ticketRepository;

    public TrainQueryTool(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Tool("查询指定日期、出发站到到达站的车次及票价。站名支持模糊匹配，例如传\"北京\"可命中\"北京南\"")
    public List<TrainInfo> queryTrains(
            @P("出发站") String fromStation,
            @P("到达站") String toStation,
            @P("出发日期，格式 yyyy-MM-dd；传空字符串表示不限日期") String date
    ) {
        return ticketRepository.findTrains(fromStation, toStation, date);
    }

    @Tool("查询指定车次的详细信息，返回列表；返回空列表表示系统中不存在该车次")
    public List<TrainInfo> queryTrainDetail(
            @P("车次号，如 G101") String trainNo
    ) {
        return ticketRepository.findTrainByNumber(trainNo);
    }
}
