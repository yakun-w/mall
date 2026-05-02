package com.macro.mall.portal.demo.service.impl;

import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import com.macro.mall.portal.demo.entity.OmsOrderStatus;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.domain.OrderParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ProtalOrderService implements IProtalOrderService {
    @Override
    public OrderResult getOrder(OrderParam orderParam) {


        // 1. 假装有商品
        String productName = "iphone";
        BigDecimal price = new BigDecimal("800");
        int quantity = 2;

        // 2. 判断库存
        reduceStock(quantity);

        // 3. 计算价格
        BigDecimal totalAmount =
                price.multiply(new BigDecimal(quantity));
        // 假装优惠100
        BigDecimal discount = new BigDecimal("100");

        //运费
        BigDecimal freight = new BigDecimal("10");
        //优惠券
        BigDecimal couponAmount = new BigDecimal("50");

        BigDecimal payAmount = totalAmount;

        payAmount = payAmount.subtract(discount);
        payAmount = payAmount.subtract(couponAmount);
        payAmount = payAmount.add(freight);

        // 4. 创建订单
        // 1. 创建订单主表对象 (Order)
        OmsOrder order = new OmsOrder();
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setStatus(OmsOrderStatus.UNPAID); // 待付款状态
        order.setOrderSn(UUID.randomUUID().toString());
        order.setNote(quantity);


        // 2. 创建订单详情对象 (OrderItem)
        OmsOrderItem item = new OmsOrderItem();
        item.setProductName(productName);
        item.setProductPrice(price);
        item.setProductQuantity(quantity);
        // ... 其他快照信息 ...

        // 3. 把商品放入 List (因为一个订单可能有多个商品)
        List<OmsOrderItem> itemList = new ArrayList<>();
        itemList.add(item);

        // 4. 封装成最终结果 (OrderResult)
        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(itemList);

        orderDB.put(order.getOrderSn(), order);
        orderItemDB.put(order.getOrderSn(), itemList);

        return result;
    }

    @Override
    public void payOrder(String orderSn){

        OmsOrder order = orderDB.get(orderSn);

        if(order == null){
            throw new RuntimeException("订单不存在");
        }

        if(order.getStatus() != OmsOrderStatus.UNPAID){
            throw new RuntimeException("订单状态异常");
        }

        order.setStatus(OmsOrderStatus.PAID);
    }

    @Override
    public void cancelOrder(String orderSn){

        OmsOrder order = orderDB.get(orderSn);

        if(order == null){
            throw new RuntimeException("订单不存在");
        }

        if(order.getStatus() != OmsOrderStatus.UNPAID){
            throw new RuntimeException("只有待支付才能取消");
        }

        order.setStatus(OmsOrderStatus.CLOSED);

        // 👉 从订单项拿数量
        List<OmsOrderItem> items = orderItemDB.get(orderSn);

        int totalQuantity = 0;
        for (OmsOrderItem item : items) {
            totalQuantity += item.getProductQuantity();
        }

        releaseStock(totalQuantity);
    }

    @Override
    public OrderResult getOrderBySn(String orderSn){

        OmsOrder order = orderDB.get(orderSn);

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

    private Map<String, OmsOrder> orderDB = new HashMap<>();
    private Map<String, List<OmsOrderItem>> orderItemDB = new HashMap<>();
}
