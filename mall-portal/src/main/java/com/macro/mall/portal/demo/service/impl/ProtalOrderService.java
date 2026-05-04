package com.macro.mall.portal.demo.service.impl;

import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import com.macro.mall.portal.demo.entity.OmsOrderStatus;
import com.macro.mall.portal.demo.mapper.OrderItemMapper;
import com.macro.mall.portal.demo.mapper.OrderMapper;
import com.macro.mall.portal.demo.mapper.ProductMapper;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.domain.OrderParam;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ProtalOrderService implements IProtalOrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public OrderResult getOrder(OrderParam orderParam) {

        // 1. 假装有商品
        String productName = "iphone";
        Long productId = 1L;
        int quantity = 2;
        BigDecimal price = new BigDecimal("800");

        // 2. 判断库存
        int count = productMapper.reduceStock(productId, quantity);

        if(count == 0){
            throw new RuntimeException("库存不足");
        }


        // 3. 计算价格
        BigDecimal totalAmount =
                price.multiply(new BigDecimal(quantity));

        BigDecimal payAmount =
                totalAmount.subtract(new BigDecimal("100"))
                        .subtract(new BigDecimal("50"))
                        .add(new BigDecimal("10"));

        // 4. 创建订单
        // 1. 创建订单主表对象 (Order)
        OmsOrder order = new OmsOrder();
        order.setOrderSn(UUID.randomUUID().toString());
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setStatus(0);

        orderMapper.insert(order);


        // 2. 创建订单详情对象 (OrderItem)
        OmsOrderItem item = new OmsOrderItem();
        item.setOrderId(order.getId());
        item.setProductName("iphone");
        item.setProductPrice(price);
        item.setProductQuantity(quantity);

        orderItemMapper.insert(item);

        // 4. 封装成最终结果 (OrderResult)
        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(Arrays.asList(item));

        // 🔥 发送延迟消息
        rabbitTemplate.convertAndSend(
                "order.exchange",
                "order.create",
                order.getOrderSn()
        );

        return result;
    }

    @Override
    public void payOrder(String orderSn){
        //支付
        int updated = orderMapper.payIfUnpaid(orderSn);

        if(updated == 0){
            throw new RuntimeException("订单状态异常");
        }
    }

    @Override
    @Transactional
    public void cancelOrder(String orderSn){
        //改状态
        int updated = orderMapper.cancelIfUnpaid(orderSn);

        if(updated == 0){
            return;
        }
        //订单号
        OmsOrder order = orderMapper.getBySn(orderSn);
        if (order == null) {
            return; // 理论上由于上面更新成功，这里一定能查到
        }


        // 查订单项
        List<OmsOrderItem> items = orderItemMapper.listByOrderId(order.getId());

        // 🔥 回滚库存
        for (OmsOrderItem item : items) {
            productMapper.addStock(item.getProductId(), item.getProductQuantity());
        }
    }

    @Override
    public OrderResult getOrderBySn(String orderSn){

        OmsOrder order = orderMapper.getBySn(orderSn);

        if(order == null){
            throw new RuntimeException("订单不存在");
        }

        List<OmsOrderItem> items = orderItemDB.get(orderSn);

        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(items);

        return result;
    }

    /**
     * 减库存
     */
    private void reduceStock(int quantity){

        if(stock < quantity){
            throw new RuntimeException("库存不足");
        }

        stock = stock - quantity;
    }


    private void releaseStock(int quantity){
        stock = stock + quantity;
    }

    //库存
    private int stock = 10;


    private Map<String, List<OmsOrderItem>> orderItemDB = new HashMap<>();
}
