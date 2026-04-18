package com.macro.mall.demo.service.impl;

import com.macro.mall.demo.dto.LoginDTO;
import com.macro.mall.demo.service.IAdminService;
import com.macro.mall.demo.utils.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AdminServiceImpl implements IAdminService {

    @Override
    public String login(LoginDTO loginDTO) {
        if(loginDTO.getUsername().equals("admin") && loginDTO.getPassword().equals("123456")){
            return JwtUtil.generateToken(loginDTO.getUsername());
        };
        return "error";
    }
}
