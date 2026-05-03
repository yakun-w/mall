package com.macro.mall.portal.demo.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OrderRabbitMQConfig {

    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_RELEASE_QUEUE = "order.release.queue";

    @Bean
    public Queue orderDelayQueue(){
        Map<String,Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange","order.exchange");
        args.put("x-dead-letter-routing-key","order.release");
        args.put("x-message-ttl", 5 * 1000); // 30分钟

        return new Queue(ORDER_DELAY_QUEUE,true,false,false,args);
    }

    @Bean
    public Queue orderReleaseQueue(){
        return new Queue(ORDER_RELEASE_QUEUE);
    }

    @Bean
    public DirectExchange orderExchange(){
        return new DirectExchange("order.exchange");
    }

    @Bean
    public Binding delayBinding(){
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange())
                .with("order.create");
    }

    @Bean
    public Binding releaseBinding(){
        return BindingBuilder.bind(orderReleaseQueue())
                .to(orderExchange())
                .with("order.release");
    }
}