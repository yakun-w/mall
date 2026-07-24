package com.macro.mall.portal.demo.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.OmsCartItem1;
import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.dto.SeckillMessage;
import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import com.macro.mall.portal.demo.mapper.OmsCartItem1Mapper;
import com.macro.mall.portal.demo.mapper.OrderItemMapper;
import com.macro.mall.portal.demo.mapper.OrderMapper;
import com.macro.mall.portal.demo.mapper.ProductMapper;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.demo.service.StockService;
import com.macro.mall.portal.domain.OrderParam;
import cn.hutool.json.JSONUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ProtalOrderService implements IProtalOrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OmsCartItem1Mapper cartItem1Mapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private StockService stockService;

    // 🚀 定义 Lua 脚本零件（放在类级别，作为常量）
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setScriptText(
                "local stock = tonumber(redis.call('get', KEYS[1]));" +
                        "if stock <= 0 then return -1 end;" +
                        "stock = stock - 1;" +
                        "redis.call('set', KEYS[1], stock);" +
                        "return stock;"
        );
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    @Transactional
    public OrderResult getOrder(OrderParam orderParam) {
        // 1. 校验与查出购物车
        List<OmsCartItem1> cartItemList = cartItem1Mapper.selectByCartIds(orderParam.getCartIds());

        // 2. 内存组装商品明细零件包
        List<OmsOrderItem> orderItemList = this.buildOrderItems(cartItemList);
        // ==================== 【第一阶段：内存组装与试算】 ====================

        // ==================== 【第二阶段：原子统一扣减 Redis 库存】 ====================
        stockService.lockStock(orderItemList);

        // ==================== 【第三阶段：持久化与数据库事务】 ====================
        // 💡 走到这里，说明 Redis 已经全部成功扣完了！此时开启 DB 事务，去动 MySQL。
        OmsOrder order = this.buildOrderEntity(orderItemList, orderParam);

        // 🔌 物理改造：将原有的一条龙 saveOrderToDb，拆解为清晰的流水线步骤
        this.saveOrder(order);
        this.saveOrderItems(order, orderItemList);

        // 4. 发射延迟消息，打完收工
        this.sendDelayMessage(order);
        return this.packageOrderResult(order, orderItemList);
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
    public void cancelOrder(String orderSn) {
        // 1. 尝试修改数据库状态（原子锁：只有待支付状态能取消成功）
        int updated = orderMapper.cancelIfUnpaid(orderSn);

        // 2. 如果更新行数为 0，说明订单已经被处理过（已支付或已取消），直接退出
        if (updated == 0) {
            return;
        }

        // 3. 状态修改成功，开始回滚物理零件
        OmsOrder order = orderMapper.getBySn(orderSn);
        if (order == null) return;

        // 获取订单内的商品明细
        List<OmsOrderItem> items = orderItemMapper.listByOrderId(order.getId());

        for (OmsOrderItem item : items) {
            // --- 物理回滚动作 A：回滚数据库库存 ---
            // SQL: UPDATE pms_product SET stock = stock + #{quantity} WHERE id = #{id}
            productMapper.addStock(item.getProductId(), item.getProductQuantity());

            // --- 物理回滚动作 B：回滚 Redis 库存 (新增内容) ---
            // 既然下单时减了数字，取消时必须加回去，保证“黑板”上的数是准的
            String redisKey = "stock:" + item.getProductId();
            redisTemplate.opsForValue().increment(redisKey, item.getProductQuantity());
        }

        System.out.println("✅ 订单 " + orderSn + " 已取消，数据库与 Redis 库存已同步回滚");
    }

    @Override
    public OrderResult getOrderBySn(String orderSn){

        OmsOrder order = orderMapper.getBySn(orderSn);

        if(order == null){
            throw new RuntimeException("订单不存在");
        }

        List<OmsOrderItem> items = orderItemMapper.listByOrderId(order.getId());

        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(items);

        return result;
    }

    @Override
    public CommonResult executeSeckill(Long productId, Long memberId) {

        // 零件 1：防重检查（一人一单）
        String userKey = "seckill:user:" + productId + ":" + memberId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(userKey, "1", 24, TimeUnit.HOURS);
        if (success == null || !success) {
            return CommonResult.failed("您已经参加过秒杀，请勿重复下单");
        }

        // 零件 2：Lua 原子扣减库存
        Long result = redisTemplate.execute(SECKILL_SCRIPT,
                Collections.singletonList("seckill:stock:" + productId), "1");

        if (result == null || result == -1) {
            // 记得清理防重标记，给用户下次抢购的机会
            redisTemplate.delete(userKey);
            return CommonResult.failed("商品已售罄");
        }

        // 零件 3：发送 MQ 消息（削峰）
        // 封装一个简单的 DTO 对象发送，包含用户 ID 和商品 ID
        SeckillMessage message = new SeckillMessage(productId, memberId);
        rabbitTemplate.convertAndSend("seckill.exchange", "seckill.order", JSONUtil.toJsonStr(message));

        return CommonResult.success(null, "抢购成功，正在排队中...");
    }

    /**
     * 减库存
     */
    public void reduceStockRedis(Long productId, int quantity){

        String key = "stock:" + productId;

        Long stock = redisTemplate.opsForValue().decrement(key, quantity);

        if(stock < 0){
            // 回滚
            redisTemplate.opsForValue().increment(key, quantity);
            throw new RuntimeException("库存不足");
        }
    }


    private List<OmsOrderItem> buildOrderItems(List<OmsCartItem1> cartItemList) {
        if (CollectionUtils.isEmpty(cartItemList)) {
            throw new RuntimeException("购物车为空");
        }
        List<OmsOrderItem> orderItemList = new ArrayList<>();
        for (OmsCartItem1 cartItem : cartItemList) {
            OmsOrderItem orderItem = new OmsOrderItem();
            orderItem.setProductId(cartItem.getProductId());
            orderItem.setProductName(cartItem.getProductName());
            orderItem.setProductPrice(cartItem.getPrice());
            orderItem.setProductQuantity(cartItem.getQuantity());
            orderItem.setRealAmount(cartItem.getPrice());
            orderItem.setGiftIntegration(0);
            orderItem.setGiftGrowth(0);
            orderItemList.add(orderItem);
        }
        return orderItemList;
    }

    private OmsOrder buildOrderEntity(List<OmsOrderItem> orderItemList, OrderParam orderParam) {
        OmsOrder order = new OmsOrder();

        order.setOrderSn(generateOrderSn());
        order.setCreateTime(new Date());
        order.setStatus(0);

        // ==================== 【职责回归：核心费用与权益自算】 ====================

        // ① 核心商品总价计算 (通过 Stream 统一计算明细总和，防错漏)
        BigDecimal totalAmount = orderItemList.stream()
                .map(item -> item.getRealAmount().multiply(new BigDecimal(item.getProductQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        // ② 优惠券/促销活动抵扣（未来扩展：根据商品明细计算哪些能用券）
        BigDecimal promotionAmount = calculatePromotionAmount(orderItemList, orderParam);
        order.setPromotionAmount(promotionAmount);

        // ③ 运费计算（未来扩展：根据商品重量、体积或店铺规则自算）
        BigDecimal freightAmount = calculateFreightAmount(orderItemList, orderParam);
        order.setFreightAmount(freightAmount);

        // 最终应付金额 = 总价 - 优惠 + 运费
        BigDecimal payAmount = totalAmount.subtract(promotionAmount).add(freightAmount);
        order.setPayAmount(payAmount);

        // ④ 权益计算：积分与成长值（根据商品明细中各自的赠送比例自算）
        int giftIntegration = orderItemList.stream().mapToInt(OmsOrderItem::getGiftIntegration).sum();
        int giftGrowth = orderItemList.stream().mapToInt(OmsOrderItem::getGiftGrowth).sum();
        order.setIntegration(giftIntegration);
        order.setGrowth(giftGrowth);

        // =====================================================================

        return order;
    }

    // 以下为内部私有辅助方法，体现了职责的完整性
    private BigDecimal calculatePromotionAmount(List<OmsOrderItem> items, OrderParam param) {
        // 实际业务中，这里会去查优惠券系统，匹配这批 items 是否满足满减
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateFreightAmount(List<OmsOrderItem> items, OrderParam param) {
        // 实际业务中，根据 items 的物流属性计算策略运费
        return BigDecimal.ZERO;
    }



    /**
     * 职责：负责为当前订单发射一枚延迟关单消息导弹
     */
    private void sendDelayMessage(OmsOrder order) {
        // 1. 物理提取当前需要的订单号（兼容老业务）
        String orderSn = order.getOrderSn();

        // 2. 🛡️ 扩展性体现：未来如果 MQ 升级，需要多维度参数，直接在这里 get，完全不用改方法定义！
        // Long orderId = order.getId();
        // Long memberId = order.getMemberId();
        // Date createTime = order.getCreateTime();

        // 3. 模拟大厂发送 MQ 消息的逻辑
        // 我们可以把整个 order 对象序列化成 JSON 字符串，作为高含金量的消息体发射出去
        // String messageBody = JSON.toJSONString(order);
        // amqpTemplate.convertAndSend("order.delay.exchange", "order.delay.routingKey", messageBody, ...);

        System.out.println("成功为订单 [" + orderSn + "] 发射延迟关单消息，未来可无缝提取其他字段");
    }

    private String generateOrderSn() {
        return System.currentTimeMillis() + String.valueOf(new Random().nextInt(10000));
    }

    //结果包装
    private OrderResult packageOrderResult(OmsOrder order, List<OmsOrderItem> orderItemList) {
        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(orderItemList);
        return result;
    }

    /**
     * 职责 1：纯粹负责订单主表（OmsOrder）的落库
     */
    private void saveOrder(OmsOrder order) {
        // 物理插入主表。注意：MyBatis 对应的 XML 必须配置 useGeneratedKeys="true" keyProperty="id"
        orderMapper.insert(order);
    }

    /**
     * 职责 2：纯粹负责订单商品详情集合（OmsOrderItem）的绑定与落库
     */
    private void saveOrderItems(OmsOrder order, List<OmsOrderItem> orderItemList) {
        // 循环遍历零件包，人肉绑定刚刚由 saveOrder() 生成的自增主键 ID 和订单号
        for (OmsOrderItem item : orderItemList) {
            item.setOrderId(order.getId());    // 🔗 灵魂绑定：把主表的自增 ID 赋给子表的外键
            item.setOrderSn(order.getOrderSn());

            // 单条插入（未来可以在这里优化为 mapper.insertList(orderItemList) 批量插入）
            orderItemMapper.insert(item);
        }
    }
}
