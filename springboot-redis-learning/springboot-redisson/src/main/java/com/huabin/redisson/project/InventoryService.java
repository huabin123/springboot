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
 * 库存服务 - 实际项目演示
 * 
 * 场景：电商库存扣减，防止超卖
 * 
 * @author huabin
 */
@Service
public class InventoryService {
    
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    
    @Autowired
    private RedissonClient redissonClient;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 扣减库存（分布式锁版本）
     * 
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 是否成功
     */
    public boolean deductStock(Long productId, int quantity) {
        String lockKey = "inventory:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁：等待5秒，锁10秒后自动释放
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取库存锁失败, productId={}", productId);
                return false;
            }
            
            try {
                String stockKey = "product:stock:" + productId;
                
                // 1. 查询当前库存
                String stockStr = redisTemplate.opsForValue().get(stockKey);
                if (stockStr == null) {
                    log.warn("商品不存在, productId={}", productId);
                    return false;
                }
                
                int currentStock = Integer.parseInt(stockStr);
                
                // 2. 判断库存是否充足
                if (currentStock < quantity) {
                    log.warn("库存不足, productId={}, 当前库存={}, 需要扣减={}", 
                            productId, currentStock, quantity);
                    return false;
                }
                
                // 3. 扣减库存
                int newStock = currentStock - quantity;
                redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
                
                log.info("库存扣减成功, productId={}, 扣减数量={}, 剩余库存={}", 
                        productId, quantity, newStock);
                
                // 4. 记录库存变动日志（实际项目中应该持久化到数据库）
                logStockChange(productId, -quantity, currentStock, newStock);
                
                return true;
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("库存扣减被中断, productId={}", productId, e);
            return false;
        }
    }
    
    /**
     * 批量扣减库存（使用 MultiLock）
     * 
     * 场景：组合商品，需要同时扣减多个商品的库存
     */
    public boolean batchDeductStock(Long[] productIds, int[] quantities) {
        if (productIds.length != quantities.length) {
            throw new IllegalArgumentException("商品ID和数量数组长度不一致");
        }
        
        // 1. 创建多个锁
        RLock[] locks = new RLock[productIds.length];
        for (int i = 0; i < productIds.length; i++) {
            String lockKey = "inventory:lock:" + productIds[i];
            locks[i] = redissonClient.getLock(lockKey);
        }
        
        // 2. 使用 MultiLock 同时获取所有锁
        RLock multiLock = redissonClient.getMultiLock(locks);
        
        try {
            boolean locked = multiLock.tryLock(5, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取批量库存锁失败");
                return false;
            }
            
            try {
                // 3. 先检查所有商品库存是否充足
                for (int i = 0; i < productIds.length; i++) {
                    String stockKey = "product:stock:" + productIds[i];
                    String stockStr = redisTemplate.opsForValue().get(stockKey);
                    
                    if (stockStr == null) {
                        log.warn("商品不存在, productId={}", productIds[i]);
                        return false;
                    }
                    
                    int currentStock = Integer.parseInt(stockStr);
                    if (currentStock < quantities[i]) {
                        log.warn("库存不足, productId={}, 当前库存={}, 需要扣减={}", 
                                productIds[i], currentStock, quantities[i]);
                        return false;
                    }
                }
                
                // 4. 所有商品库存充足，开始扣减
                for (int i = 0; i < productIds.length; i++) {
                    String stockKey = "product:stock:" + productIds[i];
                    String stockStr = redisTemplate.opsForValue().get(stockKey);
                    int currentStock = Integer.parseInt(stockStr);
                    int newStock = currentStock - quantities[i];
                    
                    redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
                    
                    log.info("批量库存扣减成功, productId={}, 扣减数量={}, 剩余库存={}", 
                            productIds[i], quantities[i], newStock);
                }
                
                return true;
                
            } finally {
                multiLock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("批量库存扣减被中断", e);
            return false;
        }
    }
    
    /**
     * 回滚库存（订单取消时）
     */
    public boolean rollbackStock(Long productId, int quantity) {
        String lockKey = "inventory:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取库存锁失败, productId={}", productId);
                return false;
            }
            
            try {
                String stockKey = "product:stock:" + productId;
                
                // 1. 查询当前库存
                String stockStr = redisTemplate.opsForValue().get(stockKey);
                if (stockStr == null) {
                    log.warn("商品不存在, productId={}", productId);
                    return false;
                }
                
                int currentStock = Integer.parseInt(stockStr);
                
                // 2. 增加库存
                int newStock = currentStock + quantity;
                redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
                
                log.info("库存回滚成功, productId={}, 回滚数量={}, 当前库存={}", 
                        productId, quantity, newStock);
                
                // 3. 记录库存变动日志
                logStockChange(productId, quantity, currentStock, newStock);
                
                return true;
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("库存回滚被中断, productId={}", productId, e);
            return false;
        }
    }
    
    /**
     * 预占库存（下单时）
     * 
     * 场景：用户下单后，库存先预占，支付成功后再真正扣减
     */
    public boolean reserveStock(Long productId, Long orderId, int quantity) {
        String lockKey = "inventory:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取库存锁失败, productId={}", productId);
                return false;
            }
            
            try {
                String stockKey = "product:stock:" + productId;
                String reservedKey = "product:reserved:" + productId + ":" + orderId;
                
                // 1. 查询可用库存
                String stockStr = redisTemplate.opsForValue().get(stockKey);
                if (stockStr == null) {
                    log.warn("商品不存在, productId={}", productId);
                    return false;
                }
                
                int currentStock = Integer.parseInt(stockStr);
                
                // 2. 判断库存是否充足
                if (currentStock < quantity) {
                    log.warn("库存不足, productId={}, 当前库存={}, 需要预占={}", 
                            productId, currentStock, quantity);
                    return false;
                }
                
                // 3. 扣减可用库存
                int newStock = currentStock - quantity;
                redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
                
                // 4. 记录预占信息（30分钟后过期）
                redisTemplate.opsForValue().set(reservedKey, String.valueOf(quantity), 
                        30, TimeUnit.MINUTES);
                
                log.info("库存预占成功, productId={}, orderId={}, 预占数量={}, 剩余库存={}", 
                        productId, orderId, quantity, newStock);
                
                return true;
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("库存预占被中断, productId={}, orderId={}", productId, orderId, e);
            return false;
        }
    }
    
    /**
     * 释放预占库存（订单超时未支付）
     */
    public boolean releaseReservedStock(Long productId, Long orderId) {
        String lockKey = "inventory:lock:" + productId;
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
            
            if (!locked) {
                log.warn("获取库存锁失败, productId={}", productId);
                return false;
            }
            
            try {
                String stockKey = "product:stock:" + productId;
                String reservedKey = "product:reserved:" + productId + ":" + orderId;
                
                // 1. 查询预占数量
                String reservedStr = redisTemplate.opsForValue().get(reservedKey);
                if (reservedStr == null) {
                    log.warn("预占记录不存在, productId={}, orderId={}", productId, orderId);
                    return false;
                }
                
                int reservedQuantity = Integer.parseInt(reservedStr);
                
                // 2. 恢复库存
                String stockStr = redisTemplate.opsForValue().get(stockKey);
                int currentStock = stockStr == null ? 0 : Integer.parseInt(stockStr);
                int newStock = currentStock + reservedQuantity;
                
                redisTemplate.opsForValue().set(stockKey, String.valueOf(newStock));
                
                // 3. 删除预占记录
                redisTemplate.delete(reservedKey);
                
                log.info("释放预占库存成功, productId={}, orderId={}, 释放数量={}, 当前库存={}", 
                        productId, orderId, reservedQuantity, newStock);
                
                return true;
                
            } finally {
                lock.unlock();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("释放预占库存被中断, productId={}, orderId={}", productId, orderId, e);
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
     * 查询当前库存
     */
    public int getCurrentStock(Long productId) {
        String stockKey = "product:stock:" + productId;
        String stockStr = redisTemplate.opsForValue().get(stockKey);
        return stockStr == null ? 0 : Integer.parseInt(stockStr);
    }
    
    /**
     * 记录库存变动日志
     */
    private void logStockChange(Long productId, int changeQuantity, int oldStock, int newStock) {
        // 实际项目中应该持久化到数据库
        log.debug("库存变动记录, productId={}, 变动数量={}, 原库存={}, 新库存={}", 
                productId, changeQuantity, oldStock, newStock);
    }
}
