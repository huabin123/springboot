package com.huabin.redis.controller;

import com.huabin.redis.model.User;
import com.huabin.redis.service.PipelineAdvancedService;
import com.huabin.redis.service.PipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Pipeline 示例控制器
 * 
 * @author huabin
 * @description 提供 Pipeline 相关的 HTTP 接口
 */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {
    
    @Autowired
    private PipelineService pipelineService;
    
    @Autowired
    private PipelineAdvancedService pipelineAdvancedService;
    
    /**
     * 性能对比测试
     * 
     * GET /api/pipeline/performance?size=1000
     */
    @GetMapping("/performance")
    public Map<String, Object> performanceTest(@RequestParam(defaultValue = "1000") int size) {
        return pipelineService.performanceComparison(size);
    }
    
    /**
     * 批量插入用户
     * 
     * POST /api/pipeline/users/batch
     */
    @PostMapping("/users/batch")
    public Map<String, Object> batchInsertUsers(@RequestParam(defaultValue = "100") int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            User user = new User();
            user.setId((long) i);
            user.setName("User" + i);
            user.setEmail("user" + i + "@example.com");
            user.setAge(20 + (i % 50));
            users.add(user);
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchInsertUsers(users);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", count);
        result.put("cost", cost + "ms");
        return result;
    }
    
    /**
     * 批量查询用户
     * 
     * GET /api/pipeline/users/batch?ids=1,2,3,4,5
     */
    @GetMapping("/users/batch")
    public Map<String, Object> batchGetUsers(@RequestParam String ids) {
        String[] idArray = ids.split(",");
        List<Long> userIds = new ArrayList<>();
        for (String id : idArray) {
            userIds.add(Long.parseLong(id.trim()));
        }
        
        long start = System.currentTimeMillis();
        List<User> users = pipelineService.batchGetUsers(userIds);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("users", users);
        result.put("cost", cost + "ms");
        return result;
    }
    
    /**
     * 批量增加商品浏览量
     * 
     * POST /api/pipeline/products/views?ids=1,2,3,4,5
     */
    @PostMapping("/products/views")
    public Map<String, Object> batchIncrementViews(@RequestParam String ids) {
        String[] idArray = ids.split(",");
        List<Long> productIds = new ArrayList<>();
        for (String id : idArray) {
            productIds.add(Long.parseLong(id.trim()));
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchIncrementViewCount(productIds);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", productIds.size());
        result.put("cost", cost + "ms");
        return result;
    }
    
    /**
     * 用户登录（混合操作）
     * 
     * POST /api/pipeline/login
     */
    @PostMapping("/login")
    public Map<String, Object> userLogin(@RequestParam String userId) {
        String sessionId = UUID.randomUUID().toString();
        
        long start = System.currentTimeMillis();
        pipelineService.mixedOperations(userId, sessionId);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("cost", cost + "ms");
        return result;
    }
    
    /**
     * 秒杀商品预热
     * 
     * POST /api/pipeline/seckill/preload?count=1000
     */
    @PostMapping("/seckill/preload")
    public Map<String, Object> preloadSeckill(@RequestParam(defaultValue = "1000") int count) {
        List<PipelineAdvancedService.SeckillProduct> products = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            PipelineAdvancedService.SeckillProduct product = 
                new PipelineAdvancedService.SeckillProduct(
                    (long) i,
                    "商品" + i,
                    100 + (i % 500),
                    99.9 + (i % 100)
                );
            products.add(product);
        }
        
        long start = System.currentTimeMillis();
        pipelineAdvancedService.preloadSeckillStock(products);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", count);
        result.put("cost", cost + "ms");
        return result;
    }
    
    /**
     * 批量查询秒杀库存
     * 
     * GET /api/pipeline/seckill/stock?ids=1,2,3,4,5
     */
    @GetMapping("/seckill/stock")
    public Map<String, Object> getSeckillStock(@RequestParam String ids) {
        String[] idArray = ids.split(",");
        List<Long> productIds = new ArrayList<>();
        for (String id : idArray) {
            productIds.add(Long.parseLong(id.trim()));
        }
        
        long start = System.currentTimeMillis();
        Map<Long, Integer> stockMap = pipelineAdvancedService.batchGetSeckillStock(productIds);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("stocks", stockMap);
        result.put("cost", cost + "ms");
        return result;
    }
    
    /**
     * 演示 Pipeline 不是事务
     * 
     * POST /api/pipeline/demo/not-transaction
     */
    @PostMapping("/demo/not-transaction")
    public Map<String, Object> demoNotTransaction() {
        try {
            pipelineAdvancedService.pipelineIsNotTransaction();
            return createResponse(true, "执行完成，请检查 Redis 中的 key1 和 key2");
        } catch (Exception e) {
            return createResponse(false, e.getMessage());
        }
    }
    
    /**
     * 演示使用事务保证原子性
     * 
     * POST /api/pipeline/demo/transaction
     */
    @PostMapping("/demo/transaction")
    public Map<String, Object> demoTransaction() {
        try {
            pipelineAdvancedService.useTransactionForAtomicity();
            return createResponse(true, "事务执行成功");
        } catch (Exception e) {
            return createResponse(false, e.getMessage());
        }
    }
    
    /**
     * 分批执行 Pipeline 示例
     * 
     * POST /api/pipeline/demo/batch?total=10000&batchSize=500
     */
    @PostMapping("/demo/batch")
    public Map<String, Object> demoBatchPipeline(
            @RequestParam(defaultValue = "10000") int total,
            @RequestParam(defaultValue = "500") int batchSize) {
        
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            keys.add("batch:key:" + i);
        }
        
        long start = System.currentTimeMillis();
        pipelineService.batchPipelineWithLimit(keys, batchSize);
        long cost = System.currentTimeMillis() - start;
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("total", total);
        result.put("batchSize", batchSize);
        result.put("batches", (total + batchSize - 1) / batchSize);
        result.put("cost", cost + "ms");
        return result;
    }
    
    private Map<String, Object> createResponse(boolean success, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        return result;
    }
}
