package com.example.provider.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Hello控制器
 * 
 * @author Demo
 */
@RestController
public class HelloController {

    private static final Logger logger = LoggerFactory.getLogger(HelloController.class);
    private static final Random random = new Random();

    @Value("${server.port}")
    private int port;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * 基本的Hello接口
     */
    @GetMapping("/hello")
    public Map<String, Object> hello() {
        String instanceInfo = getInstanceInfo();
        logger.info("收到请求 /hello，实例: {}", instanceInfo);
        
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "Hello from " + instanceInfo);
        result.put("service", serviceName);
        result.put("port", port);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }

    /**
     * 模拟慢响应（用于测试响应时间加权策略）
     */
    @GetMapping("/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1000") long delay) {
        String instanceInfo = getInstanceInfo();
        logger.info("收到慢请求 /slow，延迟: {}ms，实例: {}", delay, instanceInfo);
        
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "Slow response from " + instanceInfo);
        result.put("delay", delay);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }

    /**
     * 模拟随机失败（用于测试重试机制）
     */
    @GetMapping("/random-fail")
    public Map<String, Object> randomFail(@RequestParam(defaultValue = "30") int failRate) {
        String instanceInfo = getInstanceInfo();
        
        // 按概率失败
        if (random.nextInt(100) < failRate) {
            logger.error("模拟失败，实例: {}", instanceInfo);
            throw new RuntimeException("Random failure for testing");
        }
        
        logger.info("请求成功，实例: {}", instanceInfo);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "Success from " + instanceInfo);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }

    /**
     * 获取实例信息
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("service", serviceName);
        result.put("port", port);
        result.put("instance", getInstanceInfo());
        
        try {
            InetAddress address = InetAddress.getLocalHost();
            result.put("hostname", address.getHostName());
            result.put("ip", address.getHostAddress());
        } catch (UnknownHostException e) {
            logger.error("获取主机信息失败", e);
        }
        
        return result;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "UP");
        result.put("instance", getInstanceInfo());
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }

    /**
     * 获取实例信息字符串
     */
    private String getInstanceInfo() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + port;
        } catch (UnknownHostException e) {
            return "localhost:" + port;
        }
    }
}
