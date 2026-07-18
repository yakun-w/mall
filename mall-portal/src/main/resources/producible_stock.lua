-- KEYS: 不传（或者传商品ID，但由于多商品Key分散，这里我们通过ARGV传递数据）
-- ARGV: 传入的参数格式为：[productId1, quantity1, productId2, quantity2, ...]

local itemsCount = #ARGV
-- 因为是成对出现的（ID和数量），所以参数个数必须是偶数
if itemsCount % 2 ~= 0 then
    return -1
end

-- 【第一阶段：全局库存校验】
for i = 1, itemsCount, 2 do
    local productId = ARGV[i]
    local quantity = tonumber(ARGV[i+1])
    local stockKey = "stock:" .. productId

    -- 获取当前 Redis 中的库存值
    local currentStock = tonumber(redis.call('get', stockKey) or "0")

    -- 只要有一个商品库存不够，立刻终止，返回 0（代表库存不足，不发生任何扣减）
    if currentStock < quantity then
        return 0
    end
end

-- 【第二阶段：真正的原子扣减】
-- 走到这里，说明上面第一阶段所有商品的库存全部校验通过！
for i = 1, itemsCount, 2 do
    local productId = ARGV[i]
    local quantity = tonumber(ARGV[i+1])
    local stockKey = "stock:" .. productId

    -- 强行扣减库存
    redis.call('decrby', stockKey, quantity)
end

-- 全部扣减成功，返回 1
return 1