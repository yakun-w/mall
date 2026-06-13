package com.macro.mall.portal.demo.component;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


/**
 * Redis 库存初始化器
 * 核心逻辑：项目启动时将数据库库存同步到 Redis
 */
@Component
public class RedisStockInitializer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 假设这是你的商品 Service
    // @Autowired
    // private PmsProductService productService;

    @PostConstruct
    public void initStock() {
        // 1. 定义 Key 的零件：stock:{productId}
        String stockKey = "stock:1";

        // 2. 物理动作：初始化设置 100 个库存
        // 真实项目通常是从数据库查出所有商品 stock，循环 set 到 Redis
        redisTemplate.opsForValue().set(stockKey, "100");

        System.out.println("✅ Redis 库存零件初始化完成: " + stockKey + " = 100");
    }
}