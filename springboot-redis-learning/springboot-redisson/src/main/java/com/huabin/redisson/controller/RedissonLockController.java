package com.huabin.redisson.controller;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * Redisson分布式锁Controller
 * 
 * @author huabin
 */
@RestController
@RequestMapping("/lock")
public class RedissonLockController {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 可重入锁示例
     */
    @GetMapping("/reentrant")
    public String reentrantLock(@RequestParam String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试加锁，最多等待10秒，锁定后30秒自动解锁
            boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (isLocked) {
                try {
                    // 业务逻辑
                    Thread.sleep(5000);
                    return "执行成功";
                } finally {
                    // 释放锁
                    lock.unlock();
                }
            } else {
                return "获取锁失败";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "获取锁被中断";
        }
    }

    /**
     * 公平锁示例
     */
    @GetMapping("/fair")
    public String fairLock(@RequestParam String lockKey) {
        RLock fairLock = redissonClient.getFairLock(lockKey);
        
        try {
            fairLock.lock(30, TimeUnit.SECONDS);
            // 业务逻辑
            Thread.sleep(2000);
            return "公平锁执行成功";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "执行被中断";
        } finally {
            fairLock.unlock();
        }
    }

    /**
     * 读写锁示例 - 读锁
     */
    @GetMapping("/read")
    public String readLock(@RequestParam String lockKey) {
        RLock readLock = redissonClient.getReadWriteLock(lockKey).readLock();
        
        try {
            readLock.lock(30, TimeUnit.SECONDS);
            // 读取数据
            Thread.sleep(2000);
            return "读取成功";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "读取被中断";
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 读写锁示例 - 写锁
     */
    @PostMapping("/write")
    public String writeLock(@RequestParam String lockKey) {
        RLock writeLock = redissonClient.getReadWriteLock(lockKey).writeLock();
        
        try {
            writeLock.lock(30, TimeUnit.SECONDS);
            // 写入数据
            Thread.sleep(3000);
            return "写入成功";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "写入被中断";
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 联锁示例（MultiLock）
     * 同时锁定多个资源
     */
    @GetMapping("/multi")
    public String multiLock(@RequestParam String key1, @RequestParam String key2) {
        RLock lock1 = redissonClient.getLock(key1);
        RLock lock2 = redissonClient.getLock(key2);
        RLock multiLock = redissonClient.getMultiLock(lock1, lock2);
        
        try {
            boolean isLocked = multiLock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (isLocked) {
                try {
                    // 业务逻辑
                    return "联锁执行成功";
                } finally {
                    multiLock.unlock();
                }
            } else {
                return "获取联锁失败";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "获取联锁被中断";
        }
    }

    /**
     * 红锁示例（RedLock）
     * 适用于Redis集群环境
     */
    @GetMapping("/red")
    public String redLock(@RequestParam String key1, @RequestParam String key2) {
        RLock lock1 = redissonClient.getLock(key1);
        RLock lock2 = redissonClient.getLock(key2);
        RLock redLock = redissonClient.getRedLock(lock1, lock2);
        
        try {
            boolean isLocked = redLock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (isLocked) {
                try {
                    // 业务逻辑
                    return "红锁执行成功";
                } finally {
                    redLock.unlock();
                }
            } else {
                return "获取红锁失败";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "获取红锁被中断";
        }
    }
}
