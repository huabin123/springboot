package com.example.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server 演示应用
 * 
 * @author Demo
 */
@SpringBootApplication
@EnableEurekaServer
public class DemoEurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoEurekaServerApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Eureka Server 启动成功！");
        System.out.println("访问控制台: http://localhost:8761");
        System.out.println("========================================\n");
    }
}
