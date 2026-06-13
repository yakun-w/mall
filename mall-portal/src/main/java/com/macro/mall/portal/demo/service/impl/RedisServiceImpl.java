package com.macro.mall.portal.demo.service.impl;

import com.macro.mall.common.service.RedisService;
import com.macro.mall.portal.demo.service.IRedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RedisServiceImpl implements IRedisService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 1. 核心零件：Lua 脚本对象（静态常量，一次加载，到处运行）
    private static final DefaultRedisScript<Long> STOCK_SCRIPT;

    static {
        STOCK_SCRIPT = new DefaultRedisScript<>();
        STOCK_SCRIPT.setScriptText(
                "local stock = tonumber(redis.call('get', KEYS[1]));" +
                        "if stock >= tonumber(ARGV[1]) then " +
                        "  return redis.call('decrby', KEYS[1], ARGV[1]);" +
                        "else return -1 end;"
        );
        STOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean decrStock(Long productId, int quantity) {
        // 2. 拼接黑板上的标签：stock:1
        String key = "stock:" + productId;

        // 3. 物理执行：一次性发送脚本、Key、参数
        // execute 的逻辑是：如果脚本返回 -1，表示库存不够
        Long result = redisTemplate.execute(
                STOCK_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(quantity)
        );

        // 4. 判定结果：只要不是 -1 且不为空，就说明扣成功了
        return result != null && result != -1;
    }
}
