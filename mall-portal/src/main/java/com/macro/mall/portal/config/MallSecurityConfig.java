package com.macro.mall.portal.config;

import com.macro.mall.portal.service.UmsMemberService;
import com.macro.mall.security.config.IgnoreUrlsConfig;
import com.macro.mall.security.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * mall-security模块相关配置
 * Created by macro on 2019/11/5.
 */
//@Configuration
//@EnableWebSecurity
public class MallSecurityConfig extends SecurityConfig {

//    @Autowired
    private UmsMemberService memberService;

    // 1. 必须注册这个 Bean，否则 yml 里的 secure.ignored.urls 不会被加载
    @Bean
    public IgnoreUrlsConfig ignoreUrlsConfig() {
        return new IgnoreUrlsConfig();
    }

    // 2. 核心逻辑：获取用户信息
    @Bean
    @Lazy
    public UserDetailsService userDetailsService() {
        return username -> memberService.loadUserByUsername(username);
    }

    /**
     * 3. [最关键的一步] 显式放行登录接口
     * 如果父类的 filterChain 报冲突，确保你 yml 里开启了 allow-bean-definition-overriding: true
     */
}