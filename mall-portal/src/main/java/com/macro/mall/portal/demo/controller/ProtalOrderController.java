package com.macro.mall.portal.demo.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.domain.OrderParam;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/demoOrder")
public class ProtalOrderController {

    @Autowired
    private IProtalOrderService protalOrderService;
    // make order
    @PostMapping("/generateOrder")
    public CommonResult getProtalOrder(@RequestBody OrderParam orderParam) {
        OrderResult result = protalOrderService.getOrder(orderParam);
        return CommonResult.success(result,"下单成功");
    }
}
