package com.huabin.redis.solution.cache;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 缓存雪崩解决方案
 * 
 * 方案1：过期时间加随机值
 * 方案2：多级缓存
 * 方案3：服务降级
 */
@Service
public class CacheAvalancheSolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private int dbQueryCount = 0;
    
    /**
     * 解决方案1：过期时间加随机值
     * 
     * 优点：
     * 1. 实现简单
     * 2. 有效避免大量key同时过期
     * 
     * 原理：
     * 在基础过期时间上，加上随机值（如0-300秒）
     * 这样即使批量设置，过期时间也会分散
     */
    public void cacheProducts_RandomExpire(List<Product> products) {
        System.out.println("=== 解决方案：过期时间加随机值 ===");
        
        int baseExpireSeconds = 3600; // 基础过期时间：1小时
        
        for (Product product : products) {
            String cacheKey = "product:random:" + product.getId();
            
            // 加上随机值：0-300秒（5分钟）
            int randomSeconds = ThreadLocalRandom.current().nextInt(300);
            int expireSeconds = baseExpireSeconds + randomSeconds;
            
            redisTemplate.opsForValue().set(cacheKey, product, expireSeconds, TimeUnit.SECONDS);
        }
        
        System.out.println("已缓存 " + products.size() + " 个商品");
        System.out.println("过期时间：3600 + (0~300)秒，分散过期");
    }
    
    /**
     * 解决方案2：多级缓存
     * 
     * 架构：
     * 本地缓存（Caffeine/Guava） → Redis → 数据库
     * 
     * 优点：
     * 1. 即使Redis宕机，本地缓存仍可用
     * 2. 减少网络IO
     * 
     * 缺点：
     * 1. 数据一致性问题
     * 2. 内存占用增加
     */
    public Product getProduct_MultiLevel(Long productId) {
        String cacheKey = "product:multi:" + productId;
        
        // 1. 查本地缓存（这里简化，实际应使用 Caffeine）
        // Product product = localCache.get(productId);
        // if (product != null) {
        //     return product;
        // }
        
        // 2. 查 Redis
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            // 写入本地缓存
            // localCache.put(productId, product);
            return product;
        }
        
        // 3. 查数据库
        product = queryFromDatabase(productId);
        if (product != null) {
            // 写入 Redis
            int randomSeconds = ThreadLocalRandom.current().nextInt(300);
            redisTemplate.opsForValue().set(
                cacheKey, 
                product, 
                3600 + randomSeconds, 
                TimeUnit.SECONDS
            );
            
            // 写入本地缓存
            // localCache.put(productId, product);
        }
        
        return product;
    }
    
    /**
     * 解决方案3：服务降级
     * 
     * 当缓存雪崩发生时，启用降级策略：
     * 1. 返回默认值
     * 2. 返回静态数据
     * 3. 限流保护数据库
     */
    public Product getProduct_Degradation(Long productId) {
        String cacheKey = "product:degrade:" + productId;
        
        try {
            // 1. 查缓存
            Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
            if (product != null) {
                return product;
            }
            
            // 2. 查数据库（带限流）
            if (canQueryDatabase()) {
                product = queryFromDatabase(productId);
                if (product != null) {
                    int randomSeconds = ThreadLocalRandom.current().nextInt(300);
                    redisTemplate.opsForValue().set(
                        cacheKey, 
                        product, 
                        3600 + randomSeconds, 
                        TimeUnit.SECONDS
                    );
                }
                return product;
            } else {
                // 3. 降级：返回默认商品
                System.out.println("触发降级，返回默认商品");
                return getDefaultProduct(productId);
            }
        } catch (Exception e) {
            // 4. 异常降级
            System.out.println("发生异常，返回默认商品");
            return getDefaultProduct(productId);
        }
    }
    
    /**
     * 解决方案4：Redis 集群 + 持久化
     * 
     * 架构：
     * 1. 使用 Redis Cluster 或 哨兵模式
     * 2. 开启 RDB + AOF 持久化
     * 3. 即使 Redis 宕机，也能快速恢复
     * 
     * 配置示例：
     * - 主从复制：1主2从
     * - 哨兵：3个哨兵节点
     * - 持久化：RDB + AOF
     */
    public void configureHighAvailability() {
        System.out.println("=== Redis 高可用配置 ===");
        System.out.println("1. 主从复制：1主2从");
        System.out.println("2. 哨兵模式：3个哨兵节点");
        System.out.println("3. 持久化：RDB + AOF");
        System.out.println("4. 即使主节点宕机，哨兵会自动故障转移");
    }
    
    /**
     * 解决方案5：预热缓存
     * 
     * 在系统启动时，提前加载热点数据到缓存
     * 避免冷启动时的缓存雪崩
     */
    public void warmUpCache() {
        System.out.println("=== 预热缓存 ===");
        
        // 加载热门商品
        List<Long> hotProductIds = getHotProductIds();
        
        for (Long productId : hotProductIds) {
            Product product = queryFromDatabase(productId);
            if (product != null) {
                String cacheKey = "product:warmup:" + productId;
                int randomSeconds = ThreadLocalRandom.current().nextInt(300);
                redisTemplate.opsForValue().set(
                    cacheKey, 
                    product, 
                    3600 + randomSeconds, 
                    TimeUnit.SECONDS
                );
            }
        }
        
        System.out.println("预热完成，已加载 " + hotProductIds.size() + " 个热门商品");
    }
    
    /**
     * 限流判断：是否允许查询数据库
     */
    private boolean canQueryDatabase() {
        // 简化实现：使用计数器限流
        // 实际应使用令牌桶或漏桶算法
        String limitKey = "db:query:limit";
        Long count = redisTemplate.opsForValue().increment(limitKey);
        
        if (count == 1) {
            redisTemplate.expire(limitKey, 1, TimeUnit.SECONDS);
        }
        
        // 每秒最多100个请求
        return count <= 100;
    }
    
    /**
     * 获取默认商品（降级数据）
     */
    private Product getDefaultProduct(Long productId) {
        Product product = new Product();
        product.setId(productId);
        product.setName("商品暂时无法查看");
        product.setPrice(new BigDecimal("0.00"));
        product.setStock(0);
        return product;
    }
    
    /**
     * 获取热门商品ID列表
     */
    private List<Long> getHotProductIds() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            ids.add(i);
        }
        return ids;
    }
    
    /**
     * 模拟数据库查询
     */
    private Product queryFromDatabase(Long productId) {
        synchronized (this) {
            dbQueryCount++;
        }
        
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
     * 对比测试：问题代码 vs 解决方案
     */
    public void comparePerformance() throws InterruptedException {
        System.out.println("\n=== 缓存雪崩解决方案对比测试 ===\n");
        
        // 准备100个商品
        List<Product> products = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            Product product = new Product();
            product.setId(i);
            product.setName("商品" + i);
            product.setPrice(new BigDecimal("99.99"));
            product.setStock(100);
            products.add(product);
        }
        
        // 测试：过期时间加随机值
        System.out.println("【解决方案：过期时间加随机值】");
        cacheProducts_RandomExpire(products);
        
        // 检查过期时间分布
        System.out.println("\n检查过期时间分布：");
        for (int i = 1; i <= 5; i++) {
            String key = "product:random:" + i;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            System.out.println("商品" + i + " 的剩余过期时间: " + ttl + "秒");
        }
        
        System.out.println("\n=== 结论 ===");
        System.out.println("1. 过期时间加随机值：简单有效，推荐使用");
        System.out.println("2. 多级缓存：提高可用性，减少Redis压力");
        System.out.println("3. 服务降级：保护数据库，提供基本服务");
        System.out.println("4. 高可用架构：从根本上避免Redis宕机");
        System.out.println("5. 预热缓存：避免冷启动时的雪崩");
    }
}
