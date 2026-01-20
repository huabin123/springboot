package com.example.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 服务消费者演示应用
 * 
 * @author Demo
 */
@SpringBootApplication
@EnableEurekaClient
@EnableCircuitBreaker
public class DemoConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoConsumerApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Consumer 启动成功！");
        System.out.println("测试接口: http://localhost:9090/consumer/hello");
        System.out.println("========================================\n");
    }

    /**
     * 配置RestTemplate，启用Ribbon负载均衡
     */
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
