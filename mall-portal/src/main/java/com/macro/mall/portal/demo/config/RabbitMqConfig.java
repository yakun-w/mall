package com.macro.mall.portal.demo.config;


import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // 1. 定义队列零件
    @Bean
    public Queue seckillQueue() {
        return new Queue("seckill.order.queue");
    }

    // 2. 定义交换机零件（直连模式）
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange("seckill.exchange");
    }

    // 3. 绑定：把队列和交换机焊在一起，并贴上路由标签 "seckill.order"
    @Bean
    public Binding seckillBinding(Queue seckillQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillQueue)
                .to(seckillExchange)
                .with("seckill.order");
    }
}
