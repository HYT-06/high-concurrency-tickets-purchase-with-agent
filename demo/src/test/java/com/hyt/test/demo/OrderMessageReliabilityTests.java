package com.hyt.test.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.hyt.test.demo.common.CacheKeys;
import com.hyt.test.demo.common.Order;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.config.RabbitConfig;
import com.hyt.test.demo.mapper.OrderMapper;
import com.hyt.test.demo.mapper.SeatMapper;
import com.hyt.test.demo.service.OrderService;

/**
 * 下单链路的消息可靠性验证：HTTP → Redis 扣减 → RabbitMQ → 消费者落库，
 * 以及失败消息进入死信队列并触发补偿。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderMessageReliabilityTests {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @LocalServerPort
    private int port;

    /** 正常下单：库存扣减后消息应被消费并成功落库 */
    @Test
    void createOrderFlowsThroughRabbitMqToDatabase() throws Exception {
        Seat seat = firstAvailableSeat();
        Long userId = 1L;
        String stockKey = CacheKeys.seatStock(seat.getId());

        String requestBody = String.format(
                "{\"userId\":%d,\"trainId\":%d,\"seatId\":%d}", userId, seat.getTrainId(), seat.getId());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/order/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"), "下单接口返回失败: " + response.body());

        // 库存应在请求返回前就已经原子扣减
        assertEquals("0", stringRedisTemplate.opsForValue().get(stockKey), "Redis 库存未扣减");

        // 订单由 MQ 消费者异步写入
        Order created = awaitOrder(userId, seat.getId());

        // 清理：复用 deleteOrder 恢复座位与库存，避免污染后续测试
        orderService.deleteOrder(created.getId());
        assertEquals("1", stringRedisTemplate.opsForValue().get(stockKey), "清理后库存未恢复");
    }

    /** 毒消息：落库持续失败时，重试耗尽后应进入死信队列并触发库存补偿 */
    @Test
    void poisonMessageIsDeadLetteredThenCompensated() {
        Seat seat = firstAvailableSeat();
        String stockKey = CacheKeys.seatStock(seat.getId());
        assertEquals("1", stringRedisTemplate.opsForValue().get(stockKey));

        // userId 为空 → user_id 非空约束导致插入失败，重试 3 次后进入死信队列
        Order poison = new Order();
        poison.setTrainId(seat.getTrainId());
        poison.setSeatId(seat.getId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, poison);

        // 死信消费者的补偿会把库存加回来，以此证明消息确实走完重试并进入了死信队列
        Awaitility.await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> assertEquals("2",
                        stringRedisTemplate.opsForValue().get(stockKey),
                        "消息未进入死信队列或未触发补偿"));

        // 清理
        stringRedisTemplate.opsForValue().set(stockKey, "1");
    }

    private Order awaitOrder(Long userId, Long seatId) {
        Awaitility.await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertNotNull(findOrder(userId, seatId), "订单未被消费者写入数据库"));
        return findOrder(userId, seatId);
    }

    private Order findOrder(Long userId, Long seatId) {
        List<Order> orders = orderMapper.selectByUserId(userId);
        if (orders == null) {
            return null;
        }
        for (Order order : orders) {
            if (seatId.equals(order.getSeatId())) {
                return order;
            }
        }
        return null;
    }

    private Seat firstAvailableSeat() {
        return seatMapper.selectList(null).stream()
                .filter(s -> "available".equalsIgnoreCase(s.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库没有 available 状态的座位"));
    }
}