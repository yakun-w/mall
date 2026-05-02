package com.macro.mall.portal.demo.entity;

public class OmsOrderStatus {
    public static final Integer UNPAID = 0;      // 待支付
    public static final Integer PAID = 1;        // 已支付
    public static final Integer SHIPPED = 2;     // 已发货
    public static final Integer COMPLETED = 3;   // 已完成
    public static final Integer CLOSED = 4;      // 已关闭（取消）
}