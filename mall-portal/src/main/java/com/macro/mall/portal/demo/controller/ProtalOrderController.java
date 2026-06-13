package com.macro.mall.portal.demo.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.demo.dto.OrderResult;
import com.macro.mall.portal.demo.service.IProtalOrderService;
import com.macro.mall.portal.domain.OrderParam;
import com.macro.mall.portal.service.MemberAttentionService;
import com.macro.mall.portal.service.UmsMemberService;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/demoOrder")
public class ProtalOrderController {

    @Autowired
    private IProtalOrderService protalOrderService;

    @Autowired
    private UmsMemberService memberService;

    // make order
    @PostMapping("/generateOrder")
    public CommonResult getProtalOrder(@RequestBody OrderParam orderParam) {
        OrderResult result = protalOrderService.getOrder(orderParam);
        return CommonResult.success(result,"下单成功");
    }

    /**
     * 秒杀下单接口
     */
    @PostMapping("/seckill")
    @ResponseBody
    public CommonResult seckill(@RequestParam Long productId) {
        // 1. 前台只管接客：你是谁？
        UmsMember member = memberService.getCurrentMember();
        if (member == null) return CommonResult.unauthorized(null);

        // 2. 剩下的重活累活，全丢给 Service 引擎
        // 我们让 Service 直接返回 CommonResult，或者抛异常由全局处理器捕捉
        return protalOrderService.executeSeckill(productId, member.getId());
    }
}
