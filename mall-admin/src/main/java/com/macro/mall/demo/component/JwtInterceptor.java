package com.macro.mall.demo.component;

import com.macro.mall.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class JwtInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String token = request.getHeader("Authorization");

        //null token
        if (token == null || token.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        try {
            String username = JwtUtil.getUsername(token);

            request.setAttribute("username", username);
        }catch (Exception e){
            response.setStatus(401);
            return false;
        }

        return true;

    }

}
