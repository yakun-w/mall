package com.macro.mall.portal.demo.component;


import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = "seckill.order.queue") // 监听刚才定义的管道
public class SeckillOrderReceiver {

    // @Autowired
    // private OmsPortalOrderService portalOrderService;

    @RabbitHandler
    public void handle(String msg) {
        // 1. 接收到消息（这里的 msg 通常是下单需要的参数，如 productId:memberId）
        System.out.println("🚀 MQ 消费者收到信号，准备异步创建订单，数据为：" + msg);

        // 2. 执行真实的数据库操作
        // 这里最终会调用你的 Service 逻辑去写 oms_order 表和 oms_order_item 表
        // portalOrderService.generateOrderInternal(msg);
    }
}
