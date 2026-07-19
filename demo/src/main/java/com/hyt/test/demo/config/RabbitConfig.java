package com.hyt.test.demo.config;
import org.springframework.amqp.core.Binding;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;

@Configuration
public class RabbitConfig {
 public static final String QUEUE = "order.queue";
    public static final String EXCHANGE = "order.exchange";
    public static final String ROUTING_KEY = "order.create";

    @Bean
    public Queue queue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING_KEY);
    }
}
