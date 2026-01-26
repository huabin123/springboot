package com.huabin.redis.scenario;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.huabin.redis.model.Order;
import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 秒杀场景完整演示
 * 
 * 整合所有Redis问题和解决方案：
 * 1. 缓存穿透、击穿、雪崩
 * 2. BigKey、阻塞、性能抖动
 * 3. 内存泄漏、碎片
 * 4. 热点Key、数据丢失
 */
@Service
public class SeckillScenario {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // 布隆过滤器：防止缓存穿透
    private BloomFilter<Long> productBloomFilter = BloomFilter.create(
        Funnels.longFunnel(),
        10000,
        0.01
    );
    
    // 本地锁：防止缓存击穿
    private final Lock lock = new ReentrantLock();
    
    // 统计数据
    private int successCount = 0;
    private int failCount = 0;
    private int dbQueryCount = 0;
    
    /**
     * 初始化：预热缓存
     */
    @PostConstruct
    public void init() {
        System.out.println("=== 秒杀系统初始化 ===");
        
        // 1. 初始化布隆过滤器
        for (long i = 1; i <= 1000; i++) {
            productBloomFilter.put(i);
        }
        System.out.println("✓ 布隆过滤器初始化完成");
        
        // 2. 预热热门商品
        warmUpHotProducts();
        System.out.println("✓ 热门商品预热完成");
        
        System.out.println("=== 初始化完成 ===\n");
    }
    
    /**
     * 预热热门商品（解决：缓存雪崩、热点Key）
     */
    private void warmUpHotProducts() {
        // 预热10个热门商品
        for (long i = 1; i <= 10; i++) {
            Product product = new Product();
            product.setId(i);
            product.setName("秒杀商品" + i);
            product.setPrice(new BigDecimal("999.99"));
            product.setStock(1000);
            
            // 使用热点Key复制策略
            int replicaCount = 5;
            for (int j = 0; j < replicaCount; j++) {
                String cacheKey = "seckill:product:" + i + ":replica:" + j;
                
                // 加随机过期时间，避免雪崩
                int baseExpire = 3600;
                int randomExpire = ThreadLocalRandom.current().nextInt(300);
                
                redisTemplate.opsForValue().set(
                    cacheKey,
                    product,
                    baseExpire + randomExpire,
                    TimeUnit.SECONDS
                );
            }
            
            // 预热库存到Redis
            String stockKey = "seckill:stock:" + i;
            redisTemplate.opsForValue().set(stockKey, "1000");
        }
    }
    
    /**
     * 秒杀主流程（问题版本）
     * 
     * 存在的问题：
     * 1. 缓存穿透：恶意用户查询不存在的商品
     * 2. 缓存击穿：热点商品缓存过期
     * 3. 缓存雪崩：大量商品同时过期
     * 4. 热点Key：所有请求打到同一个节点
     * 5. 超卖问题：库存扣减不是原子操作
     */
    public String seckill_Problem(Long userId, Long productId) {
        String cacheKey = "seckill:product:" + productId;
        
        // 1. 查询商品信息
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product == null) {
            // 问题：缓存未命中，直接查数据库（可能穿透）
            product = queryProductFromDB(productId);
            if (product == null) {
                return "商品不存在";
            }
            // 问题：没有加随机过期时间（可能雪崩）
            redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        }
        
        // 2. 检查库存
        String stockKey = "seckill:stock:" + productId;
        String stockStr = (String) redisTemplate.opsForValue().get(stockKey);
        int stock = Integer.parseInt(stockStr);
        
        if (stock <= 0) {
            return "库存不足";
        }
        
        // 3. 扣减库存（问题：不是原子操作，可能超卖）
        stock--;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        
        // 4. 创建订单
        createOrder(userId, productId);
        
        return "秒杀成功";
    }
    
    /**
     * 秒杀主流程（优化版本）
     * 
     * 解决方案：
     * 1. 布隆过滤器：防止缓存穿透
     * 2. 互斥锁：防止缓存击穿
     * 3. 随机过期时间：防止缓存雪崩
     * 4. 热点Key复制：分散压力
     * 5. Lua脚本：保证原子性
     */
    public String seckill_Optimized(Long userId, Long productId) {
        // 1. 布隆过滤器判断（防止缓存穿透）
        if (!productBloomFilter.mightContain(productId)) {
            return "商品不存在";
        }
        
        // 2. 查询商品信息（使用热点Key复制）
        Product product = getProductWithHotKeyReplica(productId);
        if (product == null) {
            return "商品不存在";
        }
        
        // 3. 使用Lua脚本原子扣减库存
        String stockKey = "seckill:stock:" + productId;
        String userKey = "seckill:user:" + productId + ":" + userId;
        
        String luaScript = 
            "-- 检查用户是否已经秒杀过\n" +
            "if redis.call('exists', KEYS[2]) == 1 then\n" +
            "    return -2\n" +
            "end\n" +
            "-- 获取库存\n" +
            "local stock = tonumber(redis.call('get', KEYS[1]))\n" +
            "if stock == nil or stock <= 0 then\n" +
            "    return -1\n" +
            "end\n" +
            "-- 扣减库存\n" +
            "redis.call('decr', KEYS[1])\n" +
            "-- 标记用户已秒杀\n" +
            "redis.call('setex', KEYS[2], 86400, '1')\n" +
            "return stock - 1";
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(luaScript, Long.class),
            java.util.Arrays.asList(stockKey, userKey)
        );
        
        if (result == null || result == -1) {
            synchronized (this) {
                failCount++;
            }
            return "库存不足";
        } else if (result == -2) {
            return "您已经参与过该商品的秒杀";
        }
        
        // 4. 异步创建订单
        createOrderAsync(userId, productId);
        
        synchronized (this) {
            successCount++;
        }
        
        return "秒杀成功，剩余库存：" + result;
    }
    
    /**
     * 获取商品（使用热点Key复制）
     */
    private Product getProductWithHotKeyReplica(Long productId) {
        // 随机选择一个副本
        int replicaId = ThreadLocalRandom.current().nextInt(5);
        String cacheKey = "seckill:product:" + productId + ":replica:" + replicaId;
        
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 缓存未命中，使用互斥锁防止击穿
        lock.lock();
        try {
            // 双重检查
            product = (Product) redisTemplate.opsForValue().get(cacheKey);
            if (product != null) {
                return product;
            }
            
            // 查询数据库
            product = queryProductFromDB(productId);
            if (product != null) {
                // 写入所有副本，加随机过期时间
                for (int i = 0; i < 5; i++) {
                    String replicaKey = "seckill:product:" + productId + ":replica:" + i;
                    int baseExpire = 3600;
                    int randomExpire = ThreadLocalRandom.current().nextInt(300);
                    redisTemplate.opsForValue().set(
                        replicaKey,
                        product,
                        baseExpire + randomExpire,
                        TimeUnit.SECONDS
                    );
                }
            }
            
            return product;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 查询数据库
     */
    private Product queryProductFromDB(Long productId) {
        synchronized (this) {
            dbQueryCount++;
        }
        
        // 模拟数据库查询
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        if (productId > 0 && productId <= 1000) {
            Product product = new Product();
            product.setId(productId);
            product.setName("秒杀商品" + productId);
            product.setPrice(new BigDecimal("999.99"));
            product.setStock(1000);
            return product;
        }
        
        return null;
    }
    
    /**
     * 创建订单（同步）
     */
    private void createOrder(Long userId, Long productId) {
        Order order = new Order();
        order.setId(System.currentTimeMillis());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setQuantity(1);
        order.setTotalAmount(new BigDecimal("999.99"));
        order.setStatus("PENDING");
        
        // 模拟订单创建耗时
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 创建订单（异步）
     */
    private void createOrderAsync(Long userId, Long productId) {
        // 实际应使用消息队列（RabbitMQ、Kafka）
        new Thread(() -> {
            createOrder(userId, productId);
        }).start();
    }
    
    /**
     * 压测：问题版本
     */
    public void stressTest_Problem() throws InterruptedException {
        System.out.println("\n=== 压测：问题版本 ===");
        
        Long productId = 888L;
        int userCount = 1000;
        
        // 准备商品
        Product product = new Product();
        product.setId(productId);
        product.setName("iPhone 15 Pro Max");
        product.setPrice(new BigDecimal("9999.00"));
        product.setStock(100);
        
        String cacheKey = "seckill:product:" + productId;
        String stockKey = "seckill:stock:" + productId;
        
        redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(stockKey, "100");
        
        // 重置计数器
        successCount = 0;
        failCount = 0;
        dbQueryCount = 0;
        
        CountDownLatch latch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        long startTime = System.currentTimeMillis();
        
        // 1000个用户同时秒杀
        for (int i = 0; i < userCount; i++) {
            final long userId = i + 1;
            new Thread(() -> {
                try {
                    startLatch.await();
                    seckill_Problem(userId, productId);
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
        
        // 检查最终库存
        String finalStock = (String) redisTemplate.opsForValue().get(stockKey);
        
        System.out.println("\n=== 压测结果（问题版本）===");
        System.out.println("并发用户数: " + userCount);
        System.out.println("初始库存: 100");
        System.out.println("最终库存: " + finalStock);
        System.out.println("耗时: " + (endTime - startTime) + "ms");
        System.out.println("数据库查询次数: " + dbQueryCount);
        System.out.println("");
        System.out.println("问题：");
        System.out.println("1. 可能出现超卖（库存为负）");
        System.out.println("2. 缓存击穿导致大量数据库查询");
        System.out.println("3. 性能较差");
    }
    
    /**
     * 压测：优化版本
     */
    public void stressTest_Optimized() throws InterruptedException {
        System.out.println("\n=== 压测：优化版本 ===");
        
        Long productId = 1L;
        int userCount = 1000;
        
        // 重置库存
        String stockKey = "seckill:stock:" + productId;
        redisTemplate.opsForValue().set(stockKey, "100");
        
        // 重置计数器
        successCount = 0;
        failCount = 0;
        dbQueryCount = 0;
        
        CountDownLatch latch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        long startTime = System.currentTimeMillis();
        
        // 1000个用户同时秒杀
        for (int i = 0; i < userCount; i++) {
            final long userId = i + 1;
            new Thread(() -> {
                try {
                    startLatch.await();
                    seckill_Optimized(userId, productId);
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
        
        // 检查最终库存
        String finalStock = (String) redisTemplate.opsForValue().get(stockKey);
        
        System.out.println("\n=== 压测结果（优化版本）===");
        System.out.println("并发用户数: " + userCount);
        System.out.println("初始库存: 100");
        System.out.println("最终库存: " + finalStock);
        System.out.println("成功秒杀: " + successCount);
        System.out.println("失败次数: " + failCount);
        System.out.println("耗时: " + (endTime - startTime) + "ms");
        System.out.println("数据库查询次数: " + dbQueryCount);
        System.out.println("");
        System.out.println("优化效果：");
        System.out.println("✓ 无超卖问题");
        System.out.println("✓ 数据库查询次数大幅减少");
        System.out.println("✓ 性能提升明显");
    }
    
    /**
     * 完整场景演示
     */
    public void fullScenarioDemo() throws InterruptedException {
        System.out.println("\n╔════════════════════════════════════════════════╗");
        System.out.println("║     Redis 生产问题全景图 - 秒杀场景演示       ║");
        System.out.println("╚════════════════════════════════════════════════╝\n");
        
        // 1. 压测问题版本
        stressTest_Problem();
        
        Thread.sleep(2000);
        
        // 2. 压测优化版本
        stressTest_Optimized();
        
        // 3. 总结
        System.out.println("\n╔════════════════════════════════════════════════╗");
        System.out.println("║              优化方案总结                      ║");
        System.out.println("╚════════════════════════════════════════════════╝");
        System.out.println("");
        System.out.println("1. 缓存穿透 → 布隆过滤器");
        System.out.println("2. 缓存击穿 → 互斥锁 + 双重检查");
        System.out.println("3. 缓存雪崩 → 随机过期时间");
        System.out.println("4. 热点Key → Key复制 + 随机读取");
        System.out.println("5. 超卖问题 → Lua脚本原子操作");
        System.out.println("6. 性能优化 → 异步创建订单");
        System.out.println("");
        System.out.println("═══════════════════════════════════════════════");
    }
}
