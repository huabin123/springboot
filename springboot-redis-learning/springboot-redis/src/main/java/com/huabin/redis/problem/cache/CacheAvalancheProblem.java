package com.huabin.redis.problem.cache;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存雪崩问题演示
 * 
 * 问题：大量key同时过期，导致大量请求同时打到数据库
 * 场景：活动商品缓存统一设置1小时过期，1小时后同时失效
 */
@Service
public class CacheAvalancheProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private int dbQueryCount = 0; // 统计数据库查询次数
    
    /**
     * 问题代码：所有商品设置相同的过期时间
     * 
     * 问题点：
     * 1. 批量缓存商品时，设置相同的过期时间
     * 2. 到期时，所有缓存同时失效
     * 3. 大量请求同时打到数据库
     * 4. 数据库瞬间压力巨大，可能宕机
     */
    public void cacheProducts_Problem(List<Product> products) {
        System.out.println("=== 问题代码：批量缓存商品（相同过期时间） ===");
        
        // 问题：所有商品都设置相同的过期时间（10秒）
        for (Product product : products) {
            String cacheKey = "product:" + product.getId();
            redisTemplate.opsForValue().set(cacheKey, product, 10, TimeUnit.SECONDS);
        }
        
        System.out.println("已缓存 " + products.size() + " 个商品，过期时间都是10秒");
        System.out.println("问题：10秒后所有缓存同时失效，会导致雪崩！");
    }
    
    /**
     * 获取商品
     */
    public Product getProduct_Problem(Long productId) {
        String cacheKey = "product:" + productId;
        
        // 1. 查缓存
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 2. 缓存未命中，查数据库
        System.out.println("缓存未命中，查询数据库: " + productId);
        product = queryFromDatabase(productId);
        
        // 3. 写入缓存
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 10, TimeUnit.SECONDS);
        }
        
        return product;
    }
    
    /**
     * 模拟数据库查询
     */
    private Product queryFromDatabase(Long productId) {
        // 统计数据库查询次数
        synchronized (this) {
            dbQueryCount++;
        }
        
        // 模拟数据库查询耗时
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        Product product = new Product();
        product.setId(productId);
        product.setName("商品" + productId);
        product.setPrice(new BigDecimal("99.99"));
        product.setStock(100);
        return product;
    }
    
    /**
     * 模拟缓存雪崩场景
     * 
     * 场景：
     * 1. 批量缓存100个商品，过期时间都是10秒
     * 2. 10秒后，所有缓存同时失效
     * 3. 大量请求同时访问这些商品
     * 4. 所有请求都打到数据库
     */
    public void simulateAvalanche() throws InterruptedException {
        System.out.println("\n=== 模拟缓存雪崩场景 ===");
        
        // 1. 准备100个商品
        List<Product> products = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            Product product = new Product();
            product.setId(i);
            product.setName("商品" + i);
            product.setPrice(new BigDecimal("99.99"));
            product.setStock(100);
            products.add(product);
        }
        
        // 2. 批量缓存（问题：相同过期时间）
        cacheProducts_Problem(products);
        
        // 3. 等待缓存过期
        System.out.println("\n等待10秒，让缓存过期...");
        Thread.sleep(11000);
        
        // 4. 重置计数器
        dbQueryCount = 0;
        
        // 5. 模拟大量请求同时访问
        System.out.println("\n缓存已过期，开始大量请求...");
        long startTime = System.currentTimeMillis();
        
        for (Product product : products) {
            getProduct_Problem(product.getId());
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("\n=== 雪崩结果 ===");
        System.out.println("商品数量: " + products.size());
        System.out.println("数据库查询次数: " + dbQueryCount);
        System.out.println("耗时: " + (endTime - startTime) + "ms");
        System.out.println("问题：所有缓存同时失效，" + dbQueryCount + " 个请求同时打到数据库！");
    }
}
