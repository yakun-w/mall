package com.macro.mall.portal.demo.service;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.domain.OrderParam;

import java.util.Map;

public interface IProtalOrderService {

    //make order
    OrderResult getOrder(OrderParam orderParam);

    //支付订单
    public void payOrder(String orderSn);

    //取消订单
    public void cancelOrder(String orderSn);

    //查询订单
    public OrderResult getOrderBySn(String orderSn);

    //秒杀
    public CommonResult executeSeckill(Long productId, Long memberId);
}
