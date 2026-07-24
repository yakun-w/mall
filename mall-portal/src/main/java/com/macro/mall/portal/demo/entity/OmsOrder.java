package com.macro.mall.portal.demo.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(title = "订单主表")
public class OmsOrder {
    @Schema(title = "订单ID")
    private Long id;

    @Schema(title = "订单编号")
    private String orderSn;

    @Schema(title = "会员ID")
    private Long memberId;

    @Schema(title = "原价金额")
    private BigDecimal totalAmount;

    @Schema(title = "应付金额")
    private BigDecimal payAmount;

    @Schema(title = "促销优惠金额")
    private BigDecimal promotionAmount;

    @Schema(title = "运费")
    private BigDecimal freightAmount;

    @Schema(title = "赠送积分")
    private Integer integration;

    @Schema(title = "赠送成长值")
    private Integer growth;

    @Schema(title = "订单状态：0->待付款；1->待发货；2->已发货；3->已完成")
    private Integer status;

    @Schema(title = "购买数量")
    private Integer note;

    private Date createTime;
}
