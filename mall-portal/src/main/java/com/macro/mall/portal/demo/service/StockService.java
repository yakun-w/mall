package com.macro.mall.portal.demo.service;

import com.macro.mall.portal.demo.entity.OmsOrderItem;

import java.util.List;

public interface StockService {
    /**
     * 锁定/扣减指定商品集合的库存
     * @param orderItemList 订单商品明细零件包
     */
    void lockStock(List<OmsOrderItem> orderItemList);
}