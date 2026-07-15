package com.macro.mall.portal.demo.service.impl;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.OmsCartItem1;
import com.macro.mall.model.OmsCartItem1Example;
import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import com.macro.mall.portal.demo.mapper.OmsCartItem1Mapper;
import com.macro.mall.portal.demo.mapper.OrderItemMapper;
import com.macro.mall.portal.demo.mapper.OrderMapper;
import com.macro.mall.portal.demo.mapper.ProductMapper;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.demo.service.IRedisService;
import com.macro.mall.portal.domain.OrderParam;
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
    private IRedisService redisService;

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
    public OrderResult getOrder(OrderParam orderParam){

        // 1. 🔍 从入参里拿到用户勾选的购物车 ID 列表并校验
        List<Long> cartIds = orderParam.getCartIds();
        if (CollectionUtils.isEmpty(cartIds)) {
            throw new RuntimeException("请选择要结算的商品");
        }

        // 2. 🔌 去数据库查出这些真实的购物车记录
        OmsCartItem1Example example = new OmsCartItem1Example();
        example.createCriteria().andIdIn(cartIds);
        List<OmsCartItem1> cartItemList = cartItem1Mapper.selectByExample(example);
        if (CollectionUtils.isEmpty(cartItemList)) {
            throw new RuntimeException("未找到对应的购物车商品数据");
        }

        // 3. 🪙 价格与零件容器初始化
        BigDecimal totalAmount = BigDecimal.ZERO;                  // 订单总金额
        List<OmsOrderItem> orderItemList = new ArrayList<>();     // 订单详情零件包

        // 4. 🌀 核心循环：遍历真实的购物车商品列表，处理库存与详情组装
        for (OmsCartItem1 cartItem : cartItemList) {
            Long productId = cartItem.getProductId();
            int quantity = cartItem.getQuantity();
            BigDecimal price = cartItem.getPrice(); // 以购物车记录的价格为准（后续可以扩展去 PMS 查最新价）

            // 🛡️ 步骤一：多重防线扣减库存（Redis + DB 补救）
            this.reduceStockLogic(productId, quantity);

            // 📈 步骤二：累加总价格
            BigDecimal itemTotalAmount = price.multiply(new BigDecimal(quantity));
            totalAmount = totalAmount.add(itemTotalAmount);

            // 🛠️ 步骤三：组装每一个订单详情零件 (OmsOrderItem)
            OmsOrderItem item = new OmsOrderItem();
            item.setProductId(productId);
            item.setProductName(cartItem.getProductName());
            item.setProductPrice(price);
            item.setProductQuantity(quantity);
            // 注意：此时 order 还没落库，没有主键 ID。我们先把它丢进集合，后面统一绑定。
            orderItemList.add(item);
        }

        // 5. 💰 循环结束后，精细计算最终实际支付金额（扣除优惠券、积分等）
        BigDecimal payAmount = this.calculatePayAmount(totalAmount, orderParam);

        // 6. 🏗️ 组装并持久化订单实体
        // 6.1 创建订单主表对象 (OmsOrder)
        OmsOrder order = this.buildOrderEntity(totalAmount, payAmount, orderParam);

        // 6.2 将主表和子表集合一并存入数据库（saveOrderToDb 内部会先保存 order 拿到自增 id，然后赋给每个 item 并保存）
        this.saveOrderToDb(order, orderItemList);

        // 7. 🚀 发射延迟导弹（向 RabbitMQ 发送延迟关单消息，防止用户占座不付款）
        this.sendDelayMessage(order.getOrderSn());

        // 8. 🎁 打包返回结果给前端
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

        List<OmsOrderItem> items = orderItemDB.get(orderSn);

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


    private void releaseStock(int quantity){
        stock = stock + quantity;
    }

    //库存
    private int stock = 10;


    private Map<String, List<OmsOrderItem>> orderItemDB = new HashMap<>();

    //扣库存
    private void reduceStockLogic(Long productId, int quantity) {
        // 第一道防线：Redis 拦截
        boolean redisSuccess = redisService.decrStock(productId, quantity);
        if (!redisSuccess) {
            throw new RuntimeException("库存不足（Redis拦截）");
        }

        // 第二道防线：数据库最终凭证
        int count = productMapper.reduceStock(productId, quantity);
        if (count == 0) {
            // 物理回滚补救
            redisTemplate.opsForValue().increment("stock:" + productId, quantity);
            throw new RuntimeException("库存不足（数据库同步失败）");
        }
    }

    //价格计算
    private BigDecimal calculatePayAmount(BigDecimal totalAmount,OrderParam orderParam) {
        BigDecimal payAmount = totalAmount;
        // 减去优惠券 100，减去积分 50，加上运费 10
        return totalAmount.subtract(new BigDecimal("100"))
                .subtract(new BigDecimal("50"))
                .add(new BigDecimal("10"));
    }


    private OmsOrder buildOrderEntity(BigDecimal totalAmount, BigDecimal payAmount, OrderParam orderParam) {
        OmsOrder order = new OmsOrder();
        order.setOrderSn(UUID.randomUUID().toString());
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setStatus(0); // 0 代表待支付
        return order;
    }

    private OmsOrderItem buildOrderItemEntity(Long orderId, BigDecimal price, int quantity) {
        OmsOrderItem item = new OmsOrderItem();
        item.setOrderId(orderId);
        item.setProductName("iphone");
        item.setProductPrice(price);
        item.setProductQuantity(quantity);
        return item;
    }

    //数据库持久化
    private void saveOrderToDb(OmsOrder order, List<OmsOrderItem> orderItemList) {
        // 1. 先保存主表（MyBatis 开启 useGeneratedKeys="true" keyProperty="id" 后，会自动回填自增 ID 到 order 对象中）
        orderMapper.insert(order);

        // 2. 拿着热乎的自增订单 ID，物理绑定到每一个商品详情零件上
        for (OmsOrderItem item : orderItemList) {
            item.setOrderId(order.getId());
            item.setOrderSn(order.getOrderSn());
            orderItemMapper.insert(item); // 逐条插入或改写成 MyBatis 批量插入 batchInsert
        }
    }

    //MQ 消息车间
    private void sendDelayMessage(String orderSn) {
        rabbitTemplate.convertAndSend(
                "order.exchange",
                "order.create",
                orderSn
        );
    }

    //结果包装
    private OrderResult packageOrderResult(OmsOrder order, OmsOrderItem item) {
        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(Arrays.asList(item));
        return result;
    }
}
