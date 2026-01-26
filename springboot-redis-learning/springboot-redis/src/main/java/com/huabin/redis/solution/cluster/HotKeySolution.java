package com.huabin.redis.solution.cluster;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 热点Key解决方案
 * 
 * 方案1：本地缓存
 * 方案2：热点Key复制
 * 方案3：热点Key打散
 */
@Service
public class HotKeySolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 模拟本地缓存（实际应使用Caffeine或Guava Cache）
    private static final java.util.Map<String, Object> localCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * 解决方案1：本地缓存
     * 
     * 优点：
     * 1. 减少Redis访问
     * 2. 响应速度快
     * 3. 降低Redis压力
     * 
     * 缺点：
     * 1. 数据一致性问题
     * 2. 内存占用
     */
    public Product getHotProduct_LocalCache(Long productId) {
        String cacheKey = "hot:product:" + productId;
        
        // 1. 先查本地缓存
        Product product = (Product) localCache.get(cacheKey);
        if (product != null) {
            System.out.println("本地缓存命中: " + productId);
            return product;
        }
        
        // 2. 查Redis
        product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            // 写入本地缓存（设置过期时间）
            localCache.put(cacheKey, product);
            
            // 启动定时任务清理本地缓存（简化示例）
            scheduleLocalCacheEviction(cacheKey, 60);
            
            System.out.println("Redis缓存命中: " + productId);
            return product;
        }
        
        // 3. 查数据库
        product = queryFromDatabase(productId);
        if (product != null) {
            // 写入Redis
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
            // 写入本地缓存
            localCache.put(cacheKey, product);
        }
        
        return product;
    }
    
    /**
     * 解决方案2：热点Key复制
     * 
     * 原理：
     * 将热点Key复制多份，分散到不同的Key上
     * 例如：hot:product:888 → hot:product:888:1, hot:product:888:2, ...
     */
    public Product getHotProduct_Replicate(Long productId) {
        // 复制10份
        int replicaCount = 10;
        
        // 随机选择一个副本
        int replicaId = ThreadLocalRandom.current().nextInt(replicaCount);
        String cacheKey = "hot:product:" + productId + ":replica:" + replicaId;
        
        // 查询副本
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            System.out.println("副本缓存命中: replica-" + replicaId);
            return product;
        }
        
        // 副本不存在，查询主Key
        String mainKey = "hot:product:" + productId;
        product = (Product) redisTemplate.opsForValue().get(mainKey);
        
        if (product == null) {
            // 查数据库
            product = queryFromDatabase(productId);
        }
        
        if (product != null) {
            // 写入所有副本
            for (int i = 0; i < replicaCount; i++) {
                String replicaKey = "hot:product:" + productId + ":replica:" + i;
                redisTemplate.opsForValue().set(replicaKey, product, 1, TimeUnit.HOURS);
            }
            System.out.println("已创建" + replicaCount + "个副本");
        }
        
        return product;
    }
    
    /**
     * 解决方案3：热点Key打散（使用Hash Tag）
     * 
     * 原理：
     * 在Redis Cluster中，使用Hash Tag控制Key的分布
     * 但对于热点Key，我们故意让它们分散到不同节点
     */
    public void scatterHotKey(Long productId, Product product) {
        System.out.println("\n=== 解决方案：热点Key打散 ===");
        
        int shardCount = 10;
        
        // 将热点数据分散到10个不同的Key
        for (int i = 0; i < shardCount; i++) {
            // 使用不同的后缀，让它们分布到不同节点
            String scatteredKey = "hot:product:" + productId + ":shard:" + i;
            redisTemplate.opsForValue().set(scatteredKey, product, 1, TimeUnit.HOURS);
        }
        
        System.out.println("已将热点数据打散到" + shardCount + "个节点");
        
        // 读取时随机选择一个分片
        int randomShard = ThreadLocalRandom.current().nextInt(shardCount);
        String key = "hot:product:" + productId + ":shard:" + randomShard;
        Product result = (Product) redisTemplate.opsForValue().get(key);
        
        System.out.println("随机读取分片: " + randomShard);
    }
    
    /**
     * 解决方案4：限流保护
     * 
     * 对热点Key的访问进行限流
     */
    public Product getHotProduct_RateLimit(Long productId) {
        String cacheKey = "hot:product:" + productId;
        String rateLimitKey = "rate:limit:hot:" + productId;
        
        // 限流：每秒最多1000次访问
        Long count = redisTemplate.opsForValue().increment(rateLimitKey);
        if (count == 1) {
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.SECONDS);
        }
        
        if (count > 1000) {
            System.out.println("触发限流，返回降级数据");
            // 返回降级数据或错误
            return getDefaultProduct(productId);
        }
        
        // 正常查询
        return (Product) redisTemplate.opsForValue().get(cacheKey);
    }
    
    /**
     * 解决方案5：使用Caffeine本地缓存（推荐）
     */
    public void useCaffeineCache() {
        System.out.println("\n=== 推荐：使用Caffeine本地缓存 ===");
        System.out.println("");
        System.out.println("依赖：");
        System.out.println("<dependency>");
        System.out.println("    <groupId>com.github.ben-manes.caffeine</groupId>");
        System.out.println("    <artifactId>caffeine</artifactId>");
        System.out.println("</dependency>");
        System.out.println("");
        System.out.println("配置：");
        System.out.println("@Bean");
        System.out.println("public Cache<String, Object> caffeineCache() {");
        System.out.println("    return Caffeine.newBuilder()");
        System.out.println("        .maximumSize(10000)  // 最大10000个");
        System.out.println("        .expireAfterWrite(60, TimeUnit.SECONDS)  // 60秒过期");
        System.out.println("        .recordStats()  // 记录统计信息");
        System.out.println("        .build();");
        System.out.println("}");
    }
    
    /**
     * 热点Key优化总结
     */
    public void hotKeyOptimizationSummary() {
        System.out.println("\n=== 热点Key优化总结 ===");
        System.out.println("1. 本地缓存（推荐）：");
        System.out.println("   ✅ 使用Caffeine/Guava Cache");
        System.out.println("   ✅ 设置合理的过期时间");
        System.out.println("   ✅ 注意数据一致性");
        System.out.println("");
        System.out.println("2. 热点Key复制：");
        System.out.println("   ✅ 复制多份，随机读取");
        System.out.println("   ✅ 分散Redis压力");
        System.out.println("   ✅ 注意更新同步");
        System.out.println("");
        System.out.println("3. 热点Key打散：");
        System.out.println("   ✅ 分散到多个节点");
        System.out.println("   ✅ 提高集群利用率");
        System.out.println("");
        System.out.println("4. 限流保护：");
        System.out.println("   ✅ 防止热点Key压垮Redis");
        System.out.println("   ✅ 提供降级方案");
        System.out.println("");
        System.out.println("5. 监控告警：");
        System.out.println("   ✅ 监控热点Key");
        System.out.println("   ✅ 及时发现并处理");
    }
    
    private Product queryFromDatabase(Long productId) {
        // 模拟数据库查询
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        Product product = new Product();
        product.setId(productId);
        product.setName("商品" + productId);
        product.setPrice(new java.math.BigDecimal("99.99"));
        product.setStock(100);
        return product;
    }
    
    private Product getDefaultProduct(Long productId) {
        Product product = new Product();
        product.setId(productId);
        product.setName("商品暂时无法查看");
        product.setPrice(new java.math.BigDecimal("0.00"));
        product.setStock(0);
        return product;
    }
    
    private void scheduleLocalCacheEviction(String key, int seconds) {
        // 简化示例：实际应使用定时任务
        new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
                localCache.remove(key);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
