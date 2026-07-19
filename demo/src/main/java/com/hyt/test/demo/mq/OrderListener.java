package com.hyt.test.demo.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hyt.test.demo.common.Order;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.config.RabbitConfig;
import com.hyt.test.demo.mapper.OrderMapper;
import com.hyt.test.demo.mapper.SeatMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderListener {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private SeatMapper seatMapper;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void receive(Order order) {
        log.info("收到订单消息: userId={}, trainId={}, seatId={}",
            order.getUserId(), order.getTrainId(), order.getSeatId());

        try {
            // 写入数据库
            orderMapper.insert(order);
            log.info("订单写入成功, orderId={}", order.getId());
        } catch (Exception e) {
            log.error("订单处理失败: {}", e.getMessage(), e);

            // 回滚座位状态
            Seat seat = seatMapper.selectById(order.getSeatId());
            if (seat != null) {
                seat.setStatus("available");
                seatMapper.updateById(seat);
                log.info("座位状态已回滚, seatId={}", order.getSeatId());
            }
        }
    }
}
