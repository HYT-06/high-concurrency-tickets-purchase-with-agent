package com.example.hyt.agent.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.hyt.agent.repository.TicketRepository;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 票价查询工具。
 * <p>
 * demo 库没有独立的票价表，票价存在 {@code train.price} 上，
 * 因此这里返回"车次号 -> 票价"的映射，用于两站之间的比价。
 * <p>
 * 原实现查不到时会返回 {@code {"提示": -1.0}} 这种魔法值，
 * 容易被模型误读成"票价 -1 元"；现在改为返回空映射。
 */
@Component
public class PriceQueryTool {

    private final TicketRepository ticketRepository;

    public PriceQueryTool(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Tool("查询两站之间各车次的票价，返回 车次号->票价 的映射并按票价升序；返回空映射表示该区间无车次")
    public Map<String, Double> queryPrice(
            @P("出发站") String fromStation,
            @P("到达站") String toStation
    ) {
        return ticketRepository.findPrices(fromStation, toStation);
    }
}
