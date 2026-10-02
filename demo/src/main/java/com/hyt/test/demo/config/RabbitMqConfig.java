package com.hyt.test.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConfig.class);

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());

        // 消息无法路由到任何队列时（如绑定丢失、routingKey 写错）回调，避免消息被静默丢弃
        template.setMandatory(true);

        // 交换机确认：nack 说明消息根本没到 broker
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData != null ? correlationData.getId() : "unknown";
            if (ack) {
                log.debug("消息已到达交换机: id={}", id);
            } else {
                log.error("消息未到达交换机，需人工介入: id={}, cause={}", id, cause);
            }
        });

        // 路由失败退回：消息到了交换机但没有匹配队列
        template.setReturnsCallback(returned -> log.error(
                "消息无法路由到队列: exchange={}, routingKey={}, replyCode={}, replyText={}, body={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText(),
                new String(returned.getMessage().getBody())));

        return template;
    }
}