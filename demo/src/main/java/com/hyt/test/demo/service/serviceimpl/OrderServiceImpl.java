package com.hyt.test.demo.service.serviceimpl;

import com.hyt.test.demo.mq.OrderProducer;
import jakarta.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hyt.test.demo.common.Order;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.common.Train;
import com.hyt.test.demo.mapper.OrderMapper;
import com.hyt.test.demo.mapper.SeatMapper;
import com.hyt.test.demo.mapper.TrainMapper;
import com.hyt.test.demo.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {
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
     * 启动时清理旧库存 key（之前 MQ bug 导致的脏数据），
     * 后续首次订票时会自动重建库存。
     */
    @PostConstruct
    public void initStockKeys() {
        Set<String> keys = stringRedisTemplate.keys("seat:stock:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Override
    public Order createOrder(Long userId, Long trainId, Long seatId) {
        Train train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new RuntimeException("车次不存在");
        }

        // Redis + Lua 原子扣库存（库存不存在则自动初始化）
        String stockKey = "seat:stock:" + seatId;
        Long result = stringRedisTemplate.execute(
                deductScript,
                Collections.singletonList(stockKey),
                "1"
        );
        if (result == null || result == -1) {
            // 库存未预热，自动初始化并重试
            stringRedisTemplate.opsForValue().set(stockKey, "1");
            result = stringRedisTemplate.execute(
                    deductScript,
                    Collections.singletonList(stockKey),
                    "1"
            );
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
        orderProducer.send(order);

        return order;
    }

    @Override
    @Transactional
    public boolean deleteOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }

        // Redis + Lua 恢复库存
        String stockKey = "seat:stock:" + order.getSeatId();
        stringRedisTemplate.execute(
                restoreScript,
                Collections.singletonList(stockKey),
                "1"
        );

        // 同步恢复数据库座位状态为可用
        Seat seat = seatMapper.selectById(order.getSeatId());
        if (seat != null) {
            seat.setStatus("available");
            seatMapper.updateById(seat);
        }

        return orderMapper.deleteById(orderId) > 0;
    }

    @Override
    public Order getById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    public List<Order> listByUserId(Long userId) {
        return orderMapper.selectByUserId(userId);
    }
}
