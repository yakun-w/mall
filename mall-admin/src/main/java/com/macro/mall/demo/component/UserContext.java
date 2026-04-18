package com.macro.mall.demo.component;

import jakarta.servlet.http.HttpServletRequest;

public class UserContext {

    public static String getUsername(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
}