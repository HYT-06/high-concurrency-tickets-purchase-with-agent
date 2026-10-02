package com.example.hyt.agent.tools;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.hyt.agent.repository.TicketRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 车站查询工具。
 * <p>
 * demo 库没有独立的车站表，因此车站列表由 {@code train} 表的
 * 出发站/到达站去重得到——只返回系统真实覆盖的车站，
 * 不再像原实现那样返回带地址、地铁线路等编造字段的 mock 数据。
 */
@Component
public class StationInfoTool {

    private final TicketRepository ticketRepository;

    public StationInfoTool(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Tool("查询系统当前覆盖的所有车站名称，可用于确认某个站名的准确写法")
    public List<String> queryAllStations() {
        return ticketRepository.findStationNames();
    }

    @Tool("按关键字模糊查询车站名称，例如传\"北京\"可查到\"北京南\"")
    public List<String> queryStationsByKeyword(
            @P("车站名称关键字") String keyword
    ) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return ticketRepository.findStationNames().stream()
                .filter(name -> name.contains(keyword))
                .toList();
    }
}
