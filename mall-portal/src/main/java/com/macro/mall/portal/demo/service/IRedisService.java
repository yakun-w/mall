package com.macro.mall.portal.demo.service;

public interface IRedisService {
    /**
     * 使用 Lua 脚本原子扣减库存
     * @param productId 商品ID
     * @param quantity  扣减数量
     * @return 是否扣减成功
     */
    boolean decrStock(Long productId, int quantity);
}
