package com.example.hyt.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.hyt.agent.dto.OrderInfo;
import com.example.hyt.agent.dto.TrainInfo;
import com.example.hyt.agent.repository.TicketRepository;

/**
 * 验证 agent 已从 mock 数据切换为读取 demo 的真实数据库。
 * <p>
 * 依赖 demo 的从库（localhost:3307）已启动且主从同步正常。
 */
@SpringBootTest
class TicketRepositoryTests {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 车次来自真实库：demo 库里是 G101/D301/K101 */
    @Test
    void findsRealTrainsFromDemoDatabase() {
        List<TrainInfo> trains = ticketRepository.findTrains("北京", "上海", "");
        assertFalse(trains.isEmpty(), "北京→上海 应能查到车次");
        assertTrue(trains.stream().anyMatch(t -> "G101".equals(t.trainNo())),
                "应查到真实车次 G101，实际返回: " + trains);
    }

    /** 站名模糊匹配：传"北京"应命中"北京南" */
    @Test
    void stationNameMatchesByFuzzySearch() {
        List<TrainInfo> trains = ticketRepository.findTrains("北京", "杭州", "");
        assertTrue(trains.stream().anyMatch(t -> "K101".equals(t.trainNo())),
                "K101 是 北京→杭州，应被\"北京\"模糊命中，实际: " + trains);
    }

    /** 原先 mock 里的 G1 在真实库中不存在，用来确认 mock 已被彻底替换 */
    @Test
    void mockOnlyTrainNumberIsGone() {
        assertTrue(ticketRepository.findTrainByNumber("G1").isEmpty(),
                "G1 只是旧 mock 数据，真实库中不应存在");
        assertEquals(1, ticketRepository.findTrainByNumber("G101").size());
    }

    /** 订单查询需要 JOIN 车次表与座位表补齐车次号、站名、座位号 */
    @Test
    void ordersJoinTrainAndSeat() {
        List<OrderInfo> orders = ticketRepository.findOrders("2", "ALL");
        assertFalse(orders.isEmpty(), "用户 2 应有订单");
        OrderInfo order = orders.get(0);
        assertEquals("G101", order.trainNo(), "车次号应来自 train 表 JOIN");
        assertEquals("北京南", order.fromStation());
        assertEquals("01B", order.seatNumber(), "座位号应来自 seat 表 JOIN");
    }

    /** 非数字 userId 不能拼进 SQL 导致报错，应安全返回空 */
    @Test
    void nonNumericUserIdReturnsEmptyInsteadOfFailing() {
        assertTrue(ticketRepository.findOrders("not-a-number", "ALL").isEmpty());
        assertTrue(ticketRepository.findOrders(null, "ALL").isEmpty());
        assertTrue(ticketRepository.findOrderById("abc").isEmpty());
    }

    /** 车站列表由车次表的出发站/到达站去重得到 */
    @Test
    void stationNamesDerivedFromTrainTable() {
        List<String> names = ticketRepository.findStationNames();
        assertFalse(names.isEmpty());
        assertTrue(names.contains("北京南"), "应包含北京南，实际: " + names);
        assertTrue(names.contains("上海虹桥"), "应包含上海虹桥，实际: " + names);
    }

    /** 票价映射：车次号 -> 票价，且按票价升序 */
    @Test
    void pricesComeFromTrainTable() {
        Map<String, Double> prices = ticketRepository.findPrices("北京", "上海");
        assertEquals(553.0, prices.get("G101"), "G101 票价应来自 train.price");
    }

    /** 只读账号 + 从库 read_only 的双重保护：agent 侧不可能写到数据库 */
    @Test
    void agentCannotWriteToDatabase() {
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO train(name, number, type, status, departure, arrival, duration, price, date) "
                        + "VALUES ('只读校验', 'ZZAGENT', '高铁', '正常', 'A', 'B', '1h', 1.0, '2026-01-01')"),
                "agent 使用的账号不应有写权限");
    }
}
