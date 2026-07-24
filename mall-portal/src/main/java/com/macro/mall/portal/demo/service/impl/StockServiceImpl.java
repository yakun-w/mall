package com.macro.mall.portal.demo.service.impl;

import com.macro.mall.portal.demo.entity.OmsOrderItem;
import com.macro.mall.portal.demo.service.StockService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class StockServiceImpl implements StockService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> stockScript;

    @PostConstruct
    public void init() {
        stockScript = new DefaultRedisScript<>();
        stockScript.setLocation(new ClassPathResource("producible_stock.lua"));
        stockScript.setResultType(Long.class);
    }

    @Override
    public void lockStock(List<OmsOrderItem> orderItemList) {
        // 1. 组装参数
        List<String> args = new ArrayList<>();
        for (OmsOrderItem item : orderItemList) {
            args.add(item.getProductId().toString());
            args.add(item.getProductQuantity().toString());
        }

        // 2. 发射 Lua 导弹（当前的技术实现是 Redis）
        Long result = stringRedisTemplate.execute(stockScript, Collections.emptyList(), args.toArray());

        // 3. 结果研判
        if (result == null || result == 0) {
            throw new RuntimeException("手慢了！部分商品库存不足");
        }
        if (result == -1) {
            throw new RuntimeException("库存系统内部错误：参数异常");
        }
    }


}
