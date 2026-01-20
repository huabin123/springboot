package com.huabin.eureka.consumer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 消费者Controller
 * 调用生产者服务
 * 
 * @author huabin
 * @date 2024-01-19
 */
@Slf4j
@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    /**
     * 调用生产者服务
     * 使用服务名称调用，Ribbon会自动进行负载均衡
     * 
     * @param name 名称
     * @return 响应结果
     */
    @GetMapping("/hello/{name}")
    public String hello(@PathVariable String name) {
        // 使用服务名称调用（会自动负载均衡）
        String url = "http://producer-service/hello/" + name;
        
        log.info("调用生产者服务: {}", url);
        String result = restTemplate.getForObject(url, String.class);
        
        log.info("收到响应: {}", result);
        return "消费者调用结果: " + result;
    }

    /**
     * 获取服务信息
     * 
     * @return 服务信息
     */
    @GetMapping("/info")
    public String info() {
        // 使用服务名称调用
        String url = "http://producer-service/hello/info";
        
        log.info("调用生产者服务: {}", url);
        String result = restTemplate.getForObject(url, String.class);
        
        return result;
    }

    /**
     * 获取所有服务列表
     * 
     * @return 服务列表
     */
    @GetMapping("/services")
    public List<String> getServices() {
        List<String> services = discoveryClient.getServices();
        log.info("获取到的服务列表: {}", services);
        return services;
    }

    /**
     * 获取指定服务的所有实例
     * 
     * @param serviceName 服务名称
     * @return 实例列表
     */
    @GetMapping("/instances/{serviceName}")
    public List<ServiceInstance> getInstances(@PathVariable String serviceName) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        log.info("服务 {} 的实例列表: {}", serviceName, instances);
        return instances;
    }
}
