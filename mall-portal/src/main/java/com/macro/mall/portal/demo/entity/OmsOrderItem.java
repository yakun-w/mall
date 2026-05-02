package com.macro.mall.portal.demo.entity;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(title = "订单商品详情")
public class OmsOrderItem {
    @Schema(title = "详情ID")
    private Long id;

    @Schema(title = "订单ID") // 👈 关键：通过这个 ID 关联回上面的 Order
    private Long orderId;

    @Schema(title = "订单编号")
    private String orderSn;

    @Schema(title = "商品ID")
    private Long productId;

    @Schema(title = "商品名称")
    private String productName;

    @Schema(title = "商品价格")
    private BigDecimal productPrice;

    @Schema(title = "购买数量")
    private Integer productQuantity;
}
