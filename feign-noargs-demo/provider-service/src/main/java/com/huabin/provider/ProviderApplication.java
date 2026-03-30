package com.huabin.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 服务提供者启动类
 */
@SpringBootApplication
public class ProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Provider Service 启动成功！");
        System.out.println("访问地址: http://localhost:8081");
        System.out.println("========================================\n");
    }
}
