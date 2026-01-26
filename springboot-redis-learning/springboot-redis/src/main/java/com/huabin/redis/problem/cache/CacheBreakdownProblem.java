package com.huabin.redis.problem.cache;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 缓存击穿问题演示
 * 
 * 问题：热点key过期，大量并发请求同时打到数据库
 * 场景：秒杀商品缓存过期，瞬间大量用户访问
 */
@Service
public class CacheBreakdownProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private int dbQueryCount = 0; // 统计数据库查询次数
    
    /**
     * 问题代码：没有互斥锁保护
     * 
     * 问题点：
     * 1. 热点商品缓存过期
     * 2. 大量并发请求同时发现缓存不存在
     * 3. 所有请求都去查询数据库
     * 4. 数据库瞬间压力巨大
     */
    public Product getHotProduct_Problem(Long productId) {
        String cacheKey = "hot:product:" + productId;
        
        // 1. 查缓存
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 2. 缓存未命中，查数据库
        // 问题：多个线程同时执行到这里，都会查询数据库
        System.out.println(Thread.currentThread().getName() + " - 缓存未命中，查询数据库");
        product = queryHotProductFromDatabase(productId);
        
        // 3. 写入缓存
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 10, TimeUnit.SECONDS);
        }
        
        return product;
    }
    
    /**
     * 模拟数据库查询热点商品
     */
    private Product queryHotProductFromDatabase(Long productId) {
        // 统计数据库查询次数
        synchronized (this) {
            dbQueryCount++;
        }
        
        // 模拟数据库查询耗时
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        Product product = new Product();
        product.setId(productId);
        product.setName("热门商品" + productId);
        product.setPrice(new BigDecimal("199.99"));
        product.setStock(1000);
        return product;
    }
    
    /**
     * 模拟缓存击穿场景
     * 
     * 场景：秒杀商品缓存过期，1000个用户同时访问
     * 结果：1000个请求都打到数据库
     */
    public void simulateBreakdownAttack() throws InterruptedException {
        System.out.println("=== 模拟缓存击穿场景 ===");
        
        Long hotProductId = 888L;
        int concurrentUsers = 1000;
        
        // 重置计数器
        dbQueryCount = 0;
        
        // 先删除缓存，模拟缓存过期
        redisTemplate.delete("hot:product:" + hotProductId);
        
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        long startTime = System.currentTimeMillis();
        
        // 模拟1000个用户同时访问
        for (int i = 0; i < concurrentUsers; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    getHotProduct_Problem(hotProductId);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "User-" + i).start();
        }
        
        // 统一开始
        startLatch.countDown();
        
        // 等待所有线程完成
        latch.await();
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("并发请求数: " + concurrentUsers);
        System.out.println("数据库查询次数: " + dbQueryCount);
        System.out.println("耗时: " + (endTime - startTime) + "ms");
        System.out.println("问题：大量请求同时打到数据库，数据库压力巨大！");
    }
}
