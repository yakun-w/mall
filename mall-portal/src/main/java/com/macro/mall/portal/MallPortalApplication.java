package com.macro.mall.portal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages = "com.macro.mall")
@MapperScan("com.macro.mall.portal.demo.mapper")
public class MallPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallPortalApplication.class, args);
    }

}
