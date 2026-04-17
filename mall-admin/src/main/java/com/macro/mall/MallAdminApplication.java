package com.macro.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 应用启动入口
 * Created by macro on 2018/4/26.
 */
@SpringBootApplication
public class MallAdminApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(MallAdminApplication.class, args);
        Environment env = applicationContext.getEnvironment();
        String port = env.getProperty("server.port");
        System.out.println("\n----------------------------------------------------------\n" +
                "\t项目启动成功！地址如下:\n" +
                "\tLocal: \t\thttp://localhost:" + port + "/\n" +
                "\tSwagger文档: \thttp://localhost:" + port + "/swagger-ui.html\n" +
                "----------------------------------------------------------");
    }
}
