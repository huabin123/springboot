package com.huabin.redisson.demo;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 分布式锁基础使用示例
 * 
 * @author huabin
 */
@Component
public class BasicLockDemo {
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 示例1：基础加锁（不推荐）
     * 问题：会无限等待，直到获取锁
     */
    public void basicLockExample() {
        RLock lock = redissonClient.getLock("basicLock");
        
        // 加锁（阻塞等待）
        lock.lock();
        
        try {
            System.out.println(Thread.currentThread().getName() + " 获取锁成功");
            
            // 模拟业务处理
            Thread.sleep(2000);
            
            System.out.println(Thread.currentThread().getName() + " 业务处理完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // 释放锁
            lock.unlock();
            System.out.println(Thread.currentThread().getName() + " 释放锁");
        }
    }
    
    /**
     * 示例2：指定过期时间的加锁
     * 优点：即使客户端崩溃，锁也会在30秒后自动释放
     * 缺点：如果业务执行超过30秒，锁会提前释放
     */
    public void lockWithLeaseTime() {
        RLock lock = redissonClient.getLock("lockWithLeaseTime");
        
        // 加锁，30秒后自动释放
        lock.lock(30, TimeUnit.SECONDS);
        
        try {
            System.out.println(Thread.currentThread().getName() + " 获取锁成功，30秒后自动释放");
            
            // 模拟业务处理
            Thread.sleep(2000);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 示例3：尝试加锁（推荐）
     * 优点：可以设置等待时间，避免无限等待
     */
    public void tryLockExample() {
        RLock lock = redissonClient.getLock("tryLock");
        
        try {
            // 尝试加锁：等待10秒，锁30秒后自动释放
            boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
            
            if (locked) {
                try {
                    System.out.println(Thread.currentThread().getName() + " 获取锁成功");
                    
                    // 模拟业务处理
                    Thread.sleep(2000);
                    
                } finally {
                    lock.unlock();
                    System.out.println(Thread.currentThread().getName() + " 释放锁");
                }
            } else {
                System.out.println(Thread.currentThread().getName() + " 获取锁失败，等待超时");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println(Thread.currentThread().getName() + " 被中断");
        }
    }
    
    /**
     * 示例4：使用 WatchDog 自动续期（推荐）
     * 优点：不需要预估业务执行时间，自动续期
     * 注意：不指定 leaseTime 参数
     */
    public void lockWithWatchDog() {
        RLock lock = redissonClient.getLock("lockWithWatchDog");
        
        try {
            // 尝试加锁：等待10秒，不指定过期时间（启用WatchDog）
            boolean locked = lock.tryLock(10, TimeUnit.SECONDS);
            
            if (locked) {
                try {
                    System.out.println(Thread.currentThread().getName() + " 获取锁成功，WatchDog自动续期");
                    
                    // 模拟长时间业务处理（超过默认的30秒）
                    for (int i = 0; i < 6; i++) {
                        Thread.sleep(10000);
                        System.out.println(Thread.currentThread().getName() + " 业务执行中... " + (i + 1) * 10 + "秒");
                    }
                    
                    System.out.println(Thread.currentThread().getName() + " 业务处理完成");
                    
                } finally {
                    lock.unlock();
                    System.out.println(Thread.currentThread().getName() + " 释放锁");
                }
            } else {
                System.out.println(Thread.currentThread().getName() + " 获取锁失败");
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 示例5：判断锁是否被持有
     */
    public void checkLockStatus() {
        RLock lock = redissonClient.getLock("statusLock");
        
        // 判断锁是否被任意线程持有
        boolean isLocked = lock.isLocked();
        System.out.println("锁是否被持有: " + isLocked);
        
        // 判断锁是否被当前线程持有
        boolean isHeldByCurrentThread = lock.isHeldByCurrentThread();
        System.out.println("锁是否被当前线程持有: " + isHeldByCurrentThread);
        
        // 获取锁的持有次数（可重入次数）
        int holdCount = lock.getHoldCount();
        System.out.println("锁的持有次数: " + holdCount);
    }
    
    /**
     * 示例6：强制解锁（慎用）
     * 注意：会解锁任何线程持有的锁，可能导致并发问题
     */
    public void forceUnlock() {
        RLock lock = redissonClient.getLock("forceLock");
        
        // 强制解锁（不管是哪个线程持有）
        lock.forceUnlock();
        System.out.println("强制解锁成功");
    }
}
