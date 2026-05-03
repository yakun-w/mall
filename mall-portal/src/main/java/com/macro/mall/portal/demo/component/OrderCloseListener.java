package com.macro.mall.portal.demo.component;

import com.macro.mall.portal.demo.service.impl.ProtalOrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderCloseListener {

    @Autowired
    private ProtalOrderService orderService;

    @RabbitListener(queues = "order.release.queue")
    public void handle(String orderSn){

        try {
            orderService.cancelOrder(orderSn);
        } catch (Exception e){
            System.out.println("订单已处理：" + orderSn);
        }
    }
}
