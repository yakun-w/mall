package com.macro.mall.portal.demo.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.domain.OrderParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController("/demoOrder")
public class ProtalOrderController {

    @Autowired
    private IProtalOrderService protalOrderService;
    // make order
    public CommonResult getProtalOrder(OrderParam orderParam) {
        OrderResult result = protalOrderService.getOrder(orderParam);
        return CommonResult.success(result,"下单成功");
    }
}
