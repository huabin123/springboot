package com.huabin.redis.solution.cache;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存击穿解决方案
 * 
 * 方案1：互斥锁（Mutex Lock）
 * 方案2：热点数据永不过期
 * 方案3：逻辑过期
 */
@Service
public class CacheBreakdownSolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 本地锁（单机版）
    private final Lock lock = new ReentrantLock();
    
    private int dbQueryCount = 0; // 统计数据库查询次数
    
    /**
     * 解决方案1：互斥锁（单机版）
     * 
     * 优点：
     * 1. 保证只有一个线程查询数据库
     * 2. 实现简单
     * 
     * 缺点：
     * 1. 其他线程需要等待
     * 2. 单机锁，分布式环境无效
     */
    public Product getHotProduct_Mutex(Long productId) {
        String cacheKey = "hot:product:" + productId;
        
        // 1. 查缓存
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 2. 缓存未命中，获取锁
        lock.lock();
        try {
            // 3. 双重检查（DCL）
            product = (Product) redisTemplate.opsForValue().get(cacheKey);
            if (product != null) {
                System.out.println(Thread.currentThread().getName() + " - 获取锁后发现缓存已存在");
                return product;
            }
            
            // 4. 查询数据库
            System.out.println(Thread.currentThread().getName() + " - 获取锁成功，查询数据库");
            product = queryHotProductFromDatabase(productId);
            
            // 5. 写入缓存
            if (product != null) {
                redisTemplate.opsForValue().set(cacheKey, product, 30, TimeUnit.SECONDS);
            }
            
            return product;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 解决方案2：分布式锁（使用 Redis SETNX）
     * 
     * 优点：
     * 1. 适用于分布式环境
     * 2. 保证只有一个实例查询数据库
     * 
     * 缺点：
     * 1. 实现复杂
     * 2. 需要考虑锁超时、死锁等问题
     */
    public Product getHotProduct_DistributedLock(Long productId) {
        String cacheKey = "hot:product:" + productId;
        String lockKey = "lock:product:" + productId;
        String requestId = Thread.currentThread().getName();
        
        // 1. 查缓存
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 2. 尝试获取分布式锁
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
            lockKey, 
            requestId, 
            10, 
            TimeUnit.SECONDS
        );
        
        if (Boolean.TRUE.equals(lockAcquired)) {
            // 3. 获取锁成功
            try {
                // 双重检查
                product = (Product) redisTemplate.opsForValue().get(cacheKey);
                if (product != null) {
                    System.out.println(Thread.currentThread().getName() + " - 获取锁后发现缓存已存在");
                    return product;
                }
                
                // 查询数据库
                System.out.println(Thread.currentThread().getName() + " - 获取分布式锁成功，查询数据库");
                product = queryHotProductFromDatabase(productId);
                
                // 写入缓存
                if (product != null) {
                    redisTemplate.opsForValue().set(cacheKey, product, 30, TimeUnit.SECONDS);
                }
                
                return product;
            } finally {
                // 释放锁（使用 Lua 脚本保证原子性）
                String script = 
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";
                redisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                    java.util.Collections.singletonList(lockKey),
                    requestId
                );
            }
        } else {
            // 4. 获取锁失败，等待一段时间后重试
            System.out.println(Thread.currentThread().getName() + " - 获取锁失败，等待重试");
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 递归重试
            return getHotProduct_DistributedLock(productId);
        }
    }
    
    /**
     * 解决方案3：热点数据永不过期
     * 
     * 优点：
     * 1. 不会出现缓存击穿
     * 2. 性能最好
     * 
     * 缺点：
     * 1. 占用内存
     * 2. 需要异步更新机制
     */
    public Product getHotProduct_NeverExpire(Long productId) {
        String cacheKey = "hot:product:never:" + productId;
        
        // 1. 查缓存（永不过期）
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 2. 缓存未命中（首次访问），查询数据库
        System.out.println(Thread.currentThread().getName() + " - 首次访问，查询数据库");
        product = queryHotProductFromDatabase(productId);
        
        // 3. 写入缓存（不设置过期时间）
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product);
            
            // 启动异步线程定期更新缓存
            startAsyncRefresh(productId);
        }
        
        return product;
    }
    
    /**
     * 解决方案4：逻辑过期
     * 
     * 优点：
     * 1. 不会出现缓存击穿
     * 2. 返回速度快（返回旧数据）
     * 
     * 缺点：
     * 1. 可能返回过期数据
     * 2. 实现复杂
     */
    public Product getHotProduct_LogicalExpire(Long productId) {
        String cacheKey = "hot:product:logical:" + productId;
        String lockKey = "lock:refresh:" + productId;
        
        // 1. 查缓存
        ProductWithExpire productWithExpire = (ProductWithExpire) redisTemplate.opsForValue().get(cacheKey);
        
        if (productWithExpire == null) {
            // 首次访问，查询数据库
            Product product = queryHotProductFromDatabase(productId);
            if (product != null) {
                productWithExpire = new ProductWithExpire(product, System.currentTimeMillis() + 30000);
                redisTemplate.opsForValue().set(cacheKey, productWithExpire);
            }
            return product;
        }
        
        // 2. 检查逻辑过期时间
        if (productWithExpire.getExpireTime() > System.currentTimeMillis()) {
            // 未过期，直接返回
            return productWithExpire.getProduct();
        }
        
        // 3. 已过期，尝试获取锁刷新缓存
        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(
            lockKey, 
            "1", 
            10, 
            TimeUnit.SECONDS
        );
        
        if (Boolean.TRUE.equals(lockAcquired)) {
            // 获取锁成功，异步刷新缓存
            new Thread(() -> {
                try {
                    System.out.println("异步刷新缓存: " + productId);
                    Product newProduct = queryHotProductFromDatabase(productId);
                    ProductWithExpire newData = new ProductWithExpire(
                        newProduct, 
                        System.currentTimeMillis() + 30000
                    );
                    redisTemplate.opsForValue().set(cacheKey, newData);
                } finally {
                    redisTemplate.delete(lockKey);
                }
            }).start();
        }
        
        // 4. 返回旧数据（不等待刷新完成）
        return productWithExpire.getProduct();
    }
    
    /**
     * 启动异步刷新任务
     */
    private void startAsyncRefresh(Long productId) {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(20000); // 每20秒刷新一次
                    
                    String cacheKey = "hot:product:never:" + productId;
                    Product product = queryHotProductFromDatabase(productId);
                    redisTemplate.opsForValue().set(cacheKey, product);
                    
                    System.out.println("异步刷新缓存: " + productId);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }
    
    /**
     * 模拟数据库查询
     */
    private Product queryHotProductFromDatabase(Long productId) {
        synchronized (this) {
            dbQueryCount++;
        }
        
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
     * 对比测试：各种解决方案
     */
    public void comparePerformance() throws InterruptedException {
        System.out.println("\n=== 缓存击穿解决方案对比测试 ===\n");
        
        Long hotProductId = 888L;
        int concurrentUsers = 1000;
        
        // 测试1：互斥锁方案
        System.out.println("【方案1：互斥锁】");
        testSolution(hotProductId, concurrentUsers, "mutex");
        
        Thread.sleep(1000);
        
        // 测试2：分布式锁方案
        System.out.println("\n【方案2：分布式锁】");
        testSolution(hotProductId, concurrentUsers, "distributed");
        
        System.out.println("\n=== 结论 ===");
        System.out.println("1. 互斥锁：数据库查询次数=1，其他线程等待");
        System.out.println("2. 分布式锁：适用于分布式环境");
        System.out.println("3. 永不过期：性能最好，但需要异步更新");
        System.out.println("4. 逻辑过期：返回速度快，但可能返回旧数据");
    }
    
    private void testSolution(Long productId, int concurrentUsers, String type) throws InterruptedException {
        dbQueryCount = 0;
        
        // 删除缓存
        if ("mutex".equals(type)) {
            redisTemplate.delete("hot:product:" + productId);
        } else {
            redisTemplate.delete("hot:product:" + productId);
        }
        
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentUsers; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    if ("mutex".equals(type)) {
                        getHotProduct_Mutex(productId);
                    } else {
                        getHotProduct_DistributedLock(productId);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }, "User-" + i).start();
        }
        
        startLatch.countDown();
        latch.await();
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("并发请求数: " + concurrentUsers);
        System.out.println("数据库查询次数: " + dbQueryCount);
        System.out.println("耗时: " + (endTime - startTime) + "ms");
    }
    
    /**
     * 带逻辑过期时间的商品包装类
     */
    public static class ProductWithExpire implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private Product product;
        private Long expireTime; // 逻辑过期时间（时间戳）
        
        public ProductWithExpire() {
        }
        
        public ProductWithExpire(Product product, Long expireTime) {
            this.product = product;
            this.expireTime = expireTime;
        }
        
        public Product getProduct() {
            return product;
        }
        
        public void setProduct(Product product) {
            this.product = product;
        }
        
        public Long getExpireTime() {
            return expireTime;
        }
        
        public void setExpireTime(Long expireTime) {
            this.expireTime = expireTime;
        }
    }
}
