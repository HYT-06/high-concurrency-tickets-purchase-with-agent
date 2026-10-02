package com.example.hyt.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.hyt.agent.dto.OrderInfo;
import com.example.hyt.agent.dto.TrainInfo;
import com.example.hyt.agent.tools.OrderQueryTool;
import com.example.hyt.agent.tools.PriceQueryTool;
import com.example.hyt.agent.tools.StationInfoTool;
import com.example.hyt.agent.tools.TrainQueryTool;

/**
 * 直接调用 5 个工具的公开方法，验证「工具 → 真实数据库」这一段。
 * <p>
 * 之所以不走 HTTP 端到端：{@code /chat} 需要真实 LLM 调用，
 * 而当前 DashScope 账号欠费会返回 Arrearage 错误。
 * 工具层是本次改造的主体，直接调用可以完整覆盖它。
 */
@SpringBootTest
class TicketToolTests {

    @Autowired
    private TrainQueryTool trainQueryTool;
    @Autowired
    private OrderQueryTool orderQueryTool;
    @Autowired
    private StationInfoTool stationInfoTool;
    @Autowired
    private PriceQueryTool priceQueryTool;

    @AfterEach
    void clearUser() {
        OrderQueryTool.clearUserId();
    }

    /** 车次查询工具返回真实数据，而不是旧的 G1/G3 mock */
    @Test
    void trainToolQueriesRealData() {
        List<TrainInfo> trains = trainQueryTool.queryTrains("北京", "上海", "");
        assertFalse(trains.isEmpty());
        assertTrue(trains.stream().anyMatch(t -> "G101".equals(t.trainNo())),
                "应查到真实车次 G101，实际: " + trains);
        assertTrue(trains.stream().noneMatch(t -> "G1".equals(t.trainNo())),
                "不应再出现 mock 车次 G1");
    }

    /** 车次详情：查得到返回 1 条，查不到返回空列表（不返回 null） */
    @Test
    void trainDetailNeverReturnsNull() {
        assertEquals(1, trainQueryTool.queryTrainDetail("G101").size());
        assertTrue(trainQueryTool.queryTrainDetail("不存在").isEmpty());
    }

    /** 订单工具依赖 ThreadLocal 注入的身份；未设置时必须快速失败，不能静默返回空 */
    @Test
    void orderToolWithoutUserFailsFast() {
        assertThrows(IllegalStateException.class, () -> orderQueryTool.queryOrder("ALL"),
                "未注入 userId 时应抛异常，避免把「身份丢失」误判成「没有订单」");
    }

    /** 注入身份后返回该用户的真实订单 */
    @Test
    void orderToolReturnsRealOrdersForInjectedUser() {
        OrderQueryTool.setUserId("2");
        List<OrderInfo> orders = orderQueryTool.queryOrder("ALL");
        assertFalse(orders.isEmpty(), "用户 2 应有真实订单");
        assertEquals("G101", orders.get(0).trainNo());
    }

    /** 车站工具从车次表派生车站名，不再返回带地址/地铁线路的编造字段 */
    @Test
    void stationToolDerivesFromTrainTable() {
        List<String> all = stationInfoTool.queryAllStations();
        assertTrue(all.contains("北京南"), "实际: " + all);
        assertEquals(List.of("北京南"), stationInfoTool.queryStationsByKeyword("北京南"));
        assertEquals(List.of(), stationInfoTool.queryStationsByKeyword("不存在的站"));
    }

    /** 票价工具返回真实票价，查不到时返回空映射而非 -1 魔法值 */
    @Test
    void priceToolReturnsRealPrices() {
        Map<String, Double> prices = priceQueryTool.queryPrice("北京", "上海");
        assertEquals(553.0, prices.get("G101"));
        assertTrue(priceQueryTool.queryPrice("不存在", "也不存在").isEmpty(),
                "查不到时应返回空映射，而不是 {\"提示\": -1.0}");
    }
}
