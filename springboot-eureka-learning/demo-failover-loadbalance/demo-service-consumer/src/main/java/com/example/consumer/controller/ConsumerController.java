package com.example.consumer.controller;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消费者控制器
 * 
 * @author Demo
 */
@RestController
@RequestMapping("/consumer")
public class ConsumerController {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerController.class);
    private static final String PROVIDER_SERVICE_NAME = "demo-service-provider";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DiscoveryClient discoveryClient;

    // 请求计数器（用于统计）
    private AtomicInteger requestCount = new AtomicInteger(0);
    private AtomicInteger successCount = new AtomicInteger(0);
    private AtomicInteger failCount = new AtomicInteger(0);

    /**
     * 调用Provider的hello接口（带Hystrix熔断）
     */
    @GetMapping("/hello")
    @HystrixCommand(fallbackMethod = "helloFallback")
    public Map<String, Object> hello() {
        int count = requestCount.incrementAndGet();
        logger.info("发起第 {} 次请求", count);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 使用服务名调用，Ribbon会自动负载均衡
            String url = "http://" + PROVIDER_SERVICE_NAME + "/hello";
            Map result = restTemplate.getForObject(url, Map.class);
            
            long duration = System.currentTimeMillis() - startTime;
            successCount.incrementAndGet();
            
            logger.info("请求成功，耗时: {}ms，响应: {}", duration, result);
            
            // 添加统计信息
            Map<String, Object> response = new HashMap<String, Object>(result);
            response.put("requestCount", count);
            response.put("duration", duration);
            
            return response;
        } catch (Exception e) {
            failCount.incrementAndGet();
            logger.error("请求失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Hystrix降级方法
     */
    public Map<String, Object> helloFallback() {
        logger.warn("触发降级，返回默认响应");
        
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "服务暂时不可用，请稍后重试");
        result.put("fallback", true);
        result.put("timestamp", System.currentTimeMillis());
        
        return result;
    }

    /**
     * 测试慢响应
     */
    @GetMapping("/slow")
    @HystrixCommand(fallbackMethod = "slowFallback")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1000") long delay) {
        logger.info("发起慢请求，延迟: {}ms", delay);
        
        String url = "http://" + PROVIDER_SERVICE_NAME + "/slow?delay=" + delay;
        Map result = restTemplate.getForObject(url, Map.class);
        
        return result;
    }

    public Map<String, Object> slowFallback(long delay) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "请求超时");
        result.put("delay", delay);
        result.put("fallback", true);
        
        return result;
    }

    /**
     * 测试随机失败（验证重试机制）
     */
    @GetMapping("/random-fail")
    @HystrixCommand(fallbackMethod = "randomFailFallback")
    public Map<String, Object> randomFail(@RequestParam(defaultValue = "30") int failRate) {
        logger.info("发起随机失败请求，失败率: {}%", failRate);
        
        String url = "http://" + PROVIDER_SERVICE_NAME + "/random-fail?failRate=" + failRate;
        Map result = restTemplate.getForObject(url, Map.class);
        
        return result;
    }

    public Map<String, Object> randomFailFallback(int failRate) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "请求失败，已降级");
        result.put("failRate", failRate);
        result.put("fallback", true);
        
        return result;
    }

    /**
     * 获取服务实例列表
     */
    @GetMapping("/instances")
    public Map<String, Object> instances() {
        List<ServiceInstance> instances = discoveryClient.getInstances(PROVIDER_SERVICE_NAME);
        
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("service", PROVIDER_SERVICE_NAME);
        result.put("count", instances.size());
        result.put("instances", instances);
        
        return result;
    }

    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        int total = requestCount.get();
        int success = successCount.get();
        int fail = failCount.get();
        
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("totalRequests", total);
        result.put("successRequests", success);
        result.put("failRequests", fail);
        
        if (total > 0) {
            result.put("successRate", String.format("%.2f%%", success * 100.0 / total));
            result.put("failRate", String.format("%.2f%%", fail * 100.0 / total));
        }
        
        return result;
    }

    /**
     * 重置统计
     */
    @GetMapping("/reset-stats")
    public Map<String, Object> resetStats() {
        requestCount.set(0);
        successCount.set(0);
        failCount.set(0);
        
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("message", "统计已重置");
        
        return result;
    }
}
