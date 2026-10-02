package com.hyt.test.demo.service.serviceimpl;

import com.hyt.test.demo.mq.OrderProducer;
import jakarta.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyt.test.demo.common.CacheKeys;
import com.hyt.test.demo.common.Order;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.common.Train;
import com.hyt.test.demo.mapper.OrderMapper;
import com.hyt.test.demo.mapper.SeatMapper;
import com.hyt.test.demo.mapper.TrainMapper;
import com.hyt.test.demo.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderProducer orderProducer;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private TrainMapper trainMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> deductScript;
    private DefaultRedisScript<Long> restoreScript;

    OrderServiceImpl(OrderProducer orderProducer) {
        this.orderProducer = orderProducer;

        // 加载扣库存 Lua 脚本
        deductScript = new DefaultRedisScript<>();
        deductScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/deduct_seat.lua")));
        deductScript.setResultType(Long.class);

        // 加载恢复库存 Lua 脚本
        restoreScript = new DefaultRedisScript<>();
        restoreScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/restore_seat.lua")));
        restoreScript.setResultType(Long.class);
    }

    /**
     * 启动预热：以数据库座位状态为准重建 Redis 库存。
     * <p>
     * 库存 key 是扣减的事实来源，因此<b>不设置过期时间</b>——一旦它过期消失，
     * 下单会把它当成"未预热"重置为 1，导致已售座位被重新放出（超卖）。
     */
    @PostConstruct
    public void preheatStock() {
        List<Seat> seats = seatMapper.selectList(null);
        int available = 0;
        for (Seat seat : seats) {
            boolean sellable = isSellable(seat.getStatus());
            stringRedisTemplate.opsForValue().set(CacheKeys.seatStock(seat.getId()), sellable ? "1" : "0");
            if (sellable) {
                available++;
            }
        }
        log.info("库存预热完成: 座位总数={}, 可售={}", seats.size(), available);
    }

    @Override
    public Order createOrder(Long userId, Long trainId, Long seatId) {
        Train train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new RuntimeException("车次不存在");
        }

        // Redis + Lua 原子扣库存
        String stockKey = CacheKeys.seatStock(seatId);
        Long result = stringRedisTemplate.execute(
                deductScript,
                Collections.singletonList(stockKey),
                "1"
        );
        if (result != null && result == -1) {
            // 库存 key 缺失（例如运行期新增的座位）：以数据库座位状态补建后再扣减
            result = initializeStockAndDeduct(seatId);
        }
        if (result == null || result == -1) {
            throw new RuntimeException("座位库存初始化失败");
        }
        if (result == 0) {
            throw new RuntimeException("该座位已被预订");
        }

        // 构建订单（尚未入库，ID 为空）
        Order order = new Order();
        order.setUserId(userId);
        order.setTrainId(trainId);
        order.setSeatId(seatId);
        order.setPrice(train.getPrice());
        order.setStatus("pending");
        order.setDate(new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        order.setCreatedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

        // 同步更新数据库座位状态为已预订
        Seat seat = seatMapper.selectById(seatId);
        if (seat != null) {
            seat.setStatus("booked");
            seatMapper.updateById(seat);
        }

        // 异步处理：发送到 MQ，消费者负责写入数据库
        try {
            orderProducer.send(order);
        } catch (Exception e) {
            // 消息发送失败：Redis 库存与数据库座位状态都要回滚，避免二者不一致
            log.error("订单消息发送失败，回滚库存: seatId={}", seatId, e);
            rollbackSeat(seatId);
            throw new RuntimeException("订单提交失败，库存已回滚", e);
        }

        return order;
    }

    @Override
    @Transactional
    public boolean deleteOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        rollbackSeat(order.getSeatId());

        return orderMapper.deleteById(orderId) > 0;
    }

    /**
     * 库存 key 缺失时按数据库座位状态补建，并立即重试扣减。
     * 用 SETNX 而非 SET：并发下只补建一次，不会覆盖其他线程已完成的扣减。
     */
    private Long initializeStockAndDeduct(Long seatId) {
        Seat seat = seatMapper.selectById(seatId);
        if (seat == null) {
            throw new RuntimeException("座位不存在");
        }
        String stockKey = CacheKeys.seatStock(seatId);
        boolean sellable = isSellable(seat.getStatus());
        stringRedisTemplate.opsForValue().setIfAbsent(stockKey, sellable ? "1" : "0");
        return stringRedisTemplate.execute(deductScript, Collections.singletonList(stockKey), "1");
    }

    /**
     * 只有 available 状态才可售。
     * 库中历史上混有 sold / booked 两种已占用取值，因此用白名单判断，避免把已占座位当成可售。
     */
    private static boolean isSellable(String status) {
        return "available".equalsIgnoreCase(status);
    }

    /** 回滚单个座位：Redis 库存 +1，数据库座位状态恢复为可售 */
    private void rollbackSeat(Long seatId) {
        stringRedisTemplate.execute(
                restoreScript,
                Collections.singletonList(CacheKeys.seatStock(seatId)),
                "1"
        );

        Seat seat = seatMapper.selectById(seatId);
        if (seat != null) {
            seat.setStatus("available");
            seatMapper.updateById(seat);
        }
    }

    // 以下两个查询走从库：订单是历史数据，主从秒级延迟不影响用户阅读，
    // 且调用方是"查我的订单"这类允许最终一致的场景。
    // 注意 createOrder / deleteOrder 涉及库存与交易，必须留在主库。

    @Override
    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> listByUserId(Long userId) {
        return orderMapper.selectByUserId(userId);
    }
}