package com.macro.mall.demo.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.demo.dto.LoginDTO;
import com.macro.mall.demo.service.IAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
