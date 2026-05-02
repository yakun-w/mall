package com.macro.mall.portal.demo.dto;

import com.macro.mall.portal.demo.entity.OmsOrder;
import com.macro.mall.portal.demo.entity.OmsOrderItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OrderResult {

    private OmsOrder order;

    private List<OmsOrderItem> orderItems;

}
