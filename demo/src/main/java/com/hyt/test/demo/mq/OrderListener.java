package com.hyt.test.demo.mq;

import java.util.Collections;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import com.hyt.test.demo.common.CacheKeys;
import com.hyt.test.demo.common.Order;
import com.hyt.test.demo.common.Seat;
import com.hyt.test.demo.config.RabbitConfig;
import com.hyt.test.demo.mapper.OrderMapper;
import com.hyt.test.demo.mapper.SeatMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OrderListener {

    /** 恢复库存 Lua 脚本，与下单时的扣减脚本成对使用 */
    private static final DefaultRedisScript<Long> RESTORE_SCRIPT = createRestoreScript();

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private SeatMapper seatMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static DefaultRedisScript<Long> createRestoreScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/restore_seat.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void receive(Order order) {
        log.info("收到订单消息: userId={}, trainId={}, seatId={}",
            order.getUserId(), order.getTrainId(), order.getSeatId());

        // 写入数据库。失败要抛出，交给重试与死信队列处理：
        // 这里若像以前那样 catch 掉异常，消息会被正常 ack，订单就永久丢了。
        orderMapper.insert(order);
        log.info("订单写入成功, orderId={}", order.getId());
    }

    /**
     * 死信队列消费者：消息重试耗尽仍未成功时才会走到这里。
     * <p>
     * 补偿放在死信这里而不是上面 catch，是因为重试期间补偿会被执行多次，
     * 而 restore_seat.lua 是 INCRBY，重复执行会把库存加多、造成超卖。
     */
    @RabbitListener(queues = RabbitConfig.DEAD_LETTER_QUEUE)
    public void receiveDeadLetter(Order order) {
        log.error("订单消息进入死信队列，开始补偿: userId={}, trainId={}, seatId={}",
                order.getUserId(), order.getTrainId(), order.getSeatId());

        stringRedisTemplate.execute(
                RESTORE_SCRIPT,
                Collections.singletonList(CacheKeys.seatStock(order.getSeatId())),
                "1"
        );

        Seat seat = seatMapper.selectById(order.getSeatId());
        if (seat != null) {
            seat.setStatus("available");
            seatMapper.updateById(seat);
        }

        log.error("死信补偿完成: seatId={}（该订单已退款/释放座位，需人工核对）", order.getSeatId());
    }
}