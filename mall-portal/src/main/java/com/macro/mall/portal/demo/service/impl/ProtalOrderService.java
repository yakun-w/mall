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
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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

    private DefaultRedisScript<Long> stockScript;

    // 项目启动时，自动把 Lua 脚本加载到内存中，避免每次下单都去读文件
    @PostConstruct
    public void init() {
        stockScript = new DefaultRedisScript<>();
        stockScript.setLocation(new ClassPathResource("producible_stock.lua"));
        stockScript.setResultType(Long.class);
    }

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
        // 1. 捞出购物车记录
        List<Long> cartIds = orderParam.getCartIds();
        OmsCartItem1Example example = new OmsCartItem1Example();
        example.createCriteria().andIdIn(cartIds);
        List<OmsCartItem1> cartItemList = cartItem1Mapper.selectByExample(example);

        // 2. 内存初始化
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OmsOrderItem> orderItemList = new ArrayList<>();

        // ==================== 【第一阶段：内存组装与试算】 ====================
        // 💡 可以在循环里 add 订单详情，算总价。因为这只是在 Java 内存里堆积木，失败了没副作用。
        for (OmsCartItem1 cartItem : cartItemList) {
            Long productId = cartItem.getProductId();
            int quantity = cartItem.getQuantity();
            BigDecimal price = cartItem.getPrice();

            // 📈 累加价格
            totalAmount = totalAmount.add(price.multiply(new BigDecimal(quantity)));

            // 🛠️ 纯内存组装零件，放心安全地 add
            OmsOrderItem item = new OmsOrderItem();
            item.setProductId(productId);
            item.setProductQuantity(quantity);
            item.setProductPrice(price);
            item.setProductName(cartItem.getProductName());
            orderItemList.add(item);
        }

        // ==================== 【第二阶段：原子统一扣减 Redis 库存】 ====================
        // 💡 循环结束了，所有要扣的商品都整整齐齐在 orderItemList 里面了。
        // 这时候我们去统一扣减 Redis！
        try {
            // 核心改动：把循环扣减，变成【统一交表扣减】
            this.batchReduceRedisStock(orderItemList);
        } catch (Exception e) {
            // 如果批处理扣减失败（比如商品C在抢购中突然没货了），
            // 此时 Redis 内部通过分布式锁或下文的特殊手段已经保证了“要不全成功，要不全没有”
            throw new RuntimeException("手慢了，部分商品库存不足！");
        }

        // ==================== 【第三阶段：持久化与数据库事务】 ====================
        // 💡 走到这里，说明 Redis 已经全部成功扣完了！此时开启 DB 事务，去动 MySQL。
        BigDecimal payAmount = this.calculatePayAmount(orderItemList, orderParam);
        OmsOrder order = this.buildOrderEntity(totalAmount, payAmount, orderParam);

        // 🔌 物理改造：将原有的一条龙 saveOrderToDb，拆解为清晰的流水线步骤
        // 1. 先保存主订单（保存完后，order.getId() 会被 MyBatis 自动回填自增ID）
        this.saveOrder(order);

        // 2. 拿着主订单零件，去批量绑定并保存商品详情零件
        this.saveOrderItems(order, orderItemList);

        // 3. 【未来扩展位】：在这里可以顺手加上订单日志的保存
        // this.saveOrderHistory(order.getId(), "创建订单");


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
    private BigDecimal calculatePayAmount(List<OmsOrderItem> orderItemList, OrderParam orderParam) {
        // 1. 先通过原始商品明细，物理算出一个基础总价
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OmsOrderItem item : orderItemList) {
            // 每一件商品单价 * 数量
            BigDecimal itemPrice = item.getProductPrice().multiply(new BigDecimal(item.getProductQuantity()));
            totalAmount = totalAmount.add(itemPrice);
        }

        BigDecimal payAmount = totalAmount;

        // 2. 🛡️ 扩展性体现一：核算优惠券 (假如传了优惠券ID)
        if (orderParam.getCouponId() != null) {
            // 【未来的大厂伪代码逻辑】：
            // SmsCoupon coupon = couponMapper.selectById(orderParam.getCouponId());
            // List<OmsOrderItem> validItems = filterValidItems(orderItemList, coupon); // 过滤出哪些商品能用这张券
            // BigDecimal validAmount = calculateTotal(validItems); // 算出能用券的商品总额
            // if (validAmount >= coupon.getMinPoint()) { payAmount = payAmount.subtract(coupon.getAmount()); }

            // 目前为了跑通，我们先象征性减个 10 块钱测试
            payAmount = payAmount.subtract(new BigDecimal("10.00"));
        }

        // 3. 🛡️ 扩展性体现二：积分抵扣 (如果使用了积分)
        if (orderParam.getUseIntegration() != null && orderParam.getUseIntegration() > 0) {
            // 假设 100 积分抵扣 1 元
            BigDecimal integrationAmount = new BigDecimal(orderParam.getUseIntegration()).divide(new BigDecimal("100.00"));
            payAmount = payAmount.subtract(integrationAmount);
        }

        // 4. 🚨 安全防线：防止优惠扣成负数（商品太便宜，券太大）
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            payAmount = BigDecimal.ZERO;
        }

        return payAmount;
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

    //结果包装
    private OrderResult packageOrderResult(OmsOrder order, OmsOrderItem item) {
        OrderResult result = new OrderResult();
        result.setOrder(order);
        result.setOrderItems(Arrays.asList(item));
        return result;
    }


    private void batchReduceRedisStock(List<OmsOrderItem> orderItemList) {
        // 1. 把 List 里的零件扁平化组装成 Lua 脚本需要的 ARGV 数组
        // 格式如: ["101", "2", "102", "1"]
        List<String> args = new ArrayList<>();
        for (OmsOrderItem item : orderItemList) {
            args.add(item.getProductId().toString());
            args.add(item.getProductQuantity().toString());
        }

        // 2. 物理发射 Lua 脚本
        // 因为我们的 Key 是动态拼接的，所以第一个参数 KEYS 列表传个空集合集合即可
        Long result = redisTemplate.execute(stockScript, Collections.emptyList(), args.toArray());

        // 3. 物理人肉研判执行结果
        if (result == null || result == 0) {
            // Lua 脚本内部判断库存不足，触发了 return 0，此时 Redis 内部没有发生任何数据改变！
            throw new RuntimeException("手慢了！部分商品库存不足，请重新调整购物车");
        }

        if (result == -1) {
            throw new RuntimeException("系统内部错误：参数格式异常");
        }

        // 如果 result == 1，代表全部扣减成功，顺畅进入下一阶段的 MySQL 事务落库！
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
