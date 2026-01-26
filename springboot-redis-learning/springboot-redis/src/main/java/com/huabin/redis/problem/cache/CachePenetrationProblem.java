package com.huabin.redis.problem.cache;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

/**
 * 缓存穿透问题演示
 * 
 * 问题：查询不存在的数据，缓存和数据库都没有，导致每次请求都打到数据库
 * 场景：恶意用户不断查询不存在的商品ID
 */
@Service
public class CachePenetrationProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 问题代码：没有防护措施的查询
     * 
     * 问题点：
     * 1. 查询不存在的商品ID（如：-1, -999）
     * 2. 缓存中没有
     * 3. 数据库中也没有
     * 4. 每次请求都穿透到数据库
     * 5. 大量恶意请求会压垮数据库
     */
    public Product getProduct_Problem(Long productId) {
        String cacheKey = "product:" + productId;
        
        // 1. 查缓存
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            System.out.println("缓存命中: " + productId);
            return product;
        }
        
        // 2. 缓存未命中，查数据库
        System.out.println("缓存未命中，查询数据库: " + productId);
        product = queryFromDatabase(productId);
        
        // 3. 如果数据库有数据，写入缓存
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
            return product;
        }
        
        // 问题：如果数据库也没有，直接返回null
        // 下次相同请求还会再次查询数据库
        return null;
    }
    
    /**
     * 模拟数据库查询
     * 只有ID为正数的商品存在
     */
    private Product queryFromDatabase(Long productId) {
        // 模拟数据库查询耗时
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 只有正数ID的商品存在
        if (productId > 0 && productId <= 1000) {
            Product product = new Product();
            product.setId(productId);
            product.setName("商品" + productId);
            product.setPrice(new BigDecimal("99.99"));
            product.setStock(100);
            return product;
        }
        
        // 不存在的商品返回null
        return null;
    }
    
    /**
     * 模拟缓存穿透攻击
     * 
     * 场景：恶意用户不断查询不存在的商品ID
     * 结果：每次都穿透到数据库，数据库压力巨大
     */
    public void simulatePenetrationAttack() {
        System.out.println("=== 模拟缓存穿透攻击 ===");
        
        long startTime = System.currentTimeMillis();
        int attackCount = 100;
        
        for (int i = 0; i < attackCount; i++) {
            // 查询不存在的商品ID（负数）
            Long fakeProductId = -1000L - i;
            getProduct_Problem(fakeProductId);
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("攻击完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("数据库被查询了 " + attackCount + " 次");
        System.out.println("问题：每次请求都穿透到数据库，数据库压力巨大！");
    }
}
