package com.huabin.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 服务消费者启动类
 */
@SpringBootApplication
@EnableFeignClients
public class ConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Consumer Service 启动成功！");
        System.out.println("访问地址: http://localhost:8082");
        System.out.println("========================================\n");
    }
}
