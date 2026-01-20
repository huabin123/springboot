package com.huabin.redisson.project;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务 - 实际项目演示
 * 
 * 场景：商品秒杀，防止超卖
 * 
 * @author huabin
 */
@Service
public class SecKillService {
    
    private static final Logger log = LoggerFactory.getLogger(SecKillService.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 秒杀商品（无锁版本 - 会超卖）
     */
    public boolean secKillWithoutLock(Long productId, Long userId) {
        String stockKey = "product:stock:" + productId;
        
        // 1. 查询库存
        String stockStr = redisTemplate.opsForValue().get(stockKey);
        if (stockStr == null) {
            log.warn("商品不存在, productId={}", productId);
            return false;
        }
        
        int stock = Integer.parseInt(stockStr);
        if (stock <= 0) {
            log.warn("库存不足, productId={}, stock={}", productId, stock);
            return false;
        }
        
        // 2. 扣减库存（存在并发问题）
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock - 1));
        
        // 3. 创建订单
        createOrder(productId, userId);
        
        log.info("秒杀成功, productId={}, userId={}, 剩余库存={}", productId, userId, stock - 1);
        return true;
    }
    
    /**
     * 秒杀商品（分布式锁版本 - 防止超卖）
     */
    public boolean secKillWithLock(Long productId, Long userId) {
        String lockKey = "secKill:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁：等待3秒，锁10秒后自动释放
            boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取锁失败, productId={}, userId={}", productId, userId);
                return false;
            }
            
            try {
                String stockKey = "product:stock:" + productId;
                
                // 1. 查询库存
                String stockStr = redisTemplate.opsForValue().get(stockKey);
                if (stockStr == null) {
                    log.warn("商品不存在, productId={}", productId);
                    return false;
                }
                
                int stock = Integer.parseInt(stockStr);
                if (stock <= 0) {
                    log.warn("库存不足, productId={}, stock={}", productId, stock);
                    return false;
                }
                
                // 2. 扣减库存
                redisTemplate.opsForValue().set(stockKey, String.valueOf(stock - 1));
                
                // 3. 创建订单
                createOrder(productId, userId);
                
                log.info("秒杀成功, productId={}, userId={}, 剩余库存={}", productId, userId, stock - 1);
                return true;
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("秒杀被中断, productId={}, userId={}", productId, userId, e);
            return false;
        }
    }
    
    /**
     * 秒杀商品（优化版本 - 使用 Lua 脚本）
     * 
     * 优点：
     * 1. 减少锁的持有时间
     * 2. 利用 Redis 的原子操作
     */
    public boolean secKillOptimized(Long productId, Long userId) {
        String stockKey = "product:stock:" + productId;
        
        // 1. 使用 Lua 脚本原子性扣减库存
        String luaScript = 
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then " +
            "    return -1 " +  // 商品不存在
            "end " +
            "if tonumber(stock) <= 0 then " +
            "    return 0 " +   // 库存不足
            "end " +
            "redis.call('decr', KEYS[1]) " +
            "return 1";         // 扣减成功
        
        Long result = redisTemplate.execute(
            (org.springframework.data.redis.core.script.RedisScript<Long>) 
                org.springframework.data.redis.core.script.RedisScript.of(luaScript, Long.class),
            java.util.Collections.singletonList(stockKey)
        );
        
        if (result == null || result == -1) {
            log.warn("商品不存在, productId={}", productId);
            return false;
        }
        
        if (result == 0) {
            log.warn("库存不足, productId={}", productId);
            return false;
        }
        
        // 2. 创建订单（异步处理，提高性能）
        createOrderAsync(productId, userId);
        
        log.info("秒杀成功, productId={}, userId={}", productId, userId);
        return true;
    }
    
    /**
     * 秒杀商品（防止重复购买）
     */
    public boolean secKillWithUserLock(Long productId, Long userId) {
        // 1. 先检查用户是否已经购买过
        String userOrderKey = "user:order:" + userId + ":" + productId;
        Boolean hasOrdered = redisTemplate.hasKey(userOrderKey);
        if (Boolean.TRUE.equals(hasOrdered)) {
            log.warn("用户已购买过该商品, productId={}, userId={}", productId, userId);
            return false;
        }
        
        // 2. 使用用户级别的锁（防止同一用户并发请求）
        String userLockKey = "secKill:user:lock:" + userId + ":" + productId;
        RLock userLock = redissonClient.getLock(userLockKey);
        
        try {
            boolean locked = userLock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取用户锁失败, productId={}, userId={}", productId, userId);
                return false;
            }
            
            try {
                // 再次检查是否已购买
                if (Boolean.TRUE.equals(redisTemplate.hasKey(userOrderKey))) {
                    log.warn("用户已购买过该商品, productId={}, userId={}", productId, userId);
                    return false;
                }
                
                // 3. 扣减库存
                boolean success = secKillOptimized(productId, userId);
                
                if (success) {
                    // 4. 记录用户购买记录
                    redisTemplate.opsForValue().set(userOrderKey, "1", 24, TimeUnit.HOURS);
                }
                
                return success;
                
            } finally {
                userLock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("秒杀被中断, productId={}, userId={}", productId, userId, e);
            return false;
        }
    }
    
    /**
     * 初始化商品库存
     */
    public void initStock(Long productId, int stock) {
        String stockKey = "product:stock:" + productId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        log.info("初始化库存成功, productId={}, stock={}", productId, stock);
    }
    
    /**
     * 查询剩余库存
     */
    public int getStock(Long productId) {
        String stockKey = "product:stock:" + productId;
        String stockStr = redisTemplate.opsForValue().get(stockKey);
        return stockStr == null ? 0 : Integer.parseInt(stockStr);
    }
    
    /**
     * 创建订单（同步）
     */
    private void createOrder(Long productId, Long userId) {
        // 模拟创建订单
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.debug("创建订单成功, productId={}, userId={}", productId, userId);
    }
    
    /**
     * 创建订单（异步）
     */
    private void createOrderAsync(Long productId, Long userId) {
        // 实际项目中可以使用消息队列异步处理
        new Thread(() -> createOrder(productId, userId)).start();
    }
}
