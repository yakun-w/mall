package com.macro.mall.demo.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.demo.component.UserContext;
import com.macro.mall.demo.dto.LoginDTO;
import com.macro.mall.demo.service.IAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/demo")
public class AdminController {

    @Autowired
    public IAdminService adminService;

    //登录
    @RequestMapping("/login")
    public CommonResult login(@RequestBody LoginDTO loginDTO) {
        String login = adminService.login(loginDTO);
        return CommonResult.success(login);
    }

    @GetMapping("/info")
    public String getUserInfo(HttpServletRequest request) {

        return "当前用户：" + UserContext.getUsername(request);
    }
}
