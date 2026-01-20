package com.huabin.redisson.demo;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 可重入锁演示
 * 
 * @author huabin
 */
@Component
public class ReentrantLockDemo {
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 示例1：可重入锁基础演示
     * 同一个线程可以多次获取同一把锁
     */
    public void reentrantExample() {
        RLock lock = redissonClient.getLock("reentrantLock");
        
        try {
            // 第1次加锁
            lock.lock();
            System.out.println(Thread.currentThread().getName() + " 第1次加锁成功，持有次数: " + lock.getHoldCount());
            
            try {
                // 第2次加锁（重入）
                lock.lock();
                System.out.println(Thread.currentThread().getName() + " 第2次加锁成功，持有次数: " + lock.getHoldCount());
                
                try {
                    // 第3次加锁（重入）
                    lock.lock();
                    System.out.println(Thread.currentThread().getName() + " 第3次加锁成功，持有次数: " + lock.getHoldCount());
                    
                    // 业务逻辑
                    System.out.println("执行业务逻辑");
                    
                } finally {
                    // 第3次解锁
                    lock.unlock();
                    System.out.println(Thread.currentThread().getName() + " 第3次解锁，持有次数: " + lock.getHoldCount());
                }
                
            } finally {
                // 第2次解锁
                lock.unlock();
                System.out.println(Thread.currentThread().getName() + " 第2次解锁，持有次数: " + lock.getHoldCount());
            }
            
        } finally {
            // 第1次解锁
            lock.unlock();
            System.out.println(Thread.currentThread().getName() + " 第1次解锁，持有次数: " + lock.getHoldCount());
        }
    }
    
    /**
     * 示例2：方法嵌套调用（可重入的典型场景）
     */
    public void nestedMethodExample() {
        methodA();
    }
    
    private void methodA() {
        RLock lock = redissonClient.getLock("nestedLock");
        
        lock.lock();
        try {
            System.out.println("methodA 获取锁，持有次数: " + lock.getHoldCount());
            
            // 调用 methodB，需要再次获取同一把锁
            methodB();
            
        } finally {
            lock.unlock();
            System.out.println("methodA 释放锁");
        }
    }
    
    private void methodB() {
        RLock lock = redissonClient.getLock("nestedLock");
        
        lock.lock();
        try {
            System.out.println("methodB 获取锁（重入），持有次数: " + lock.getHoldCount());
            
            // 调用 methodC
            methodC();
            
        } finally {
            lock.unlock();
            System.out.println("methodB 释放锁");
        }
    }
    
    private void methodC() {
        RLock lock = redissonClient.getLock("nestedLock");
        
        lock.lock();
        try {
            System.out.println("methodC 获取锁（重入），持有次数: " + lock.getHoldCount());
            
            // 实际业务逻辑
            System.out.println("执行核心业务逻辑");
            
        } finally {
            lock.unlock();
            System.out.println("methodC 释放锁");
        }
    }
    
    /**
     * 示例3：错误示例 - 加锁和解锁次数不匹配
     * 会导致锁无法完全释放
     */
    public void wrongExample() {
        RLock lock = redissonClient.getLock("wrongLock");
        
        try {
            lock.lock();
            System.out.println("第1次加锁");
            
            lock.lock();
            System.out.println("第2次加锁，持有次数: " + lock.getHoldCount());
            
            // 业务逻辑
            
        } finally {
            // ❌ 错误：只解锁1次，但加锁了2次
            lock.unlock();
            System.out.println("只解锁1次，持有次数: " + lock.getHoldCount());
            // 锁仍然被持有，其他线程无法获取
        }
    }
    
    /**
     * 示例4：正确示例 - 使用 try-finally 保证加锁解锁配对
     */
    public void correctExample() {
        RLock lock = redissonClient.getLock("correctLock");
        
        lock.lock();
        try {
            System.out.println("第1次加锁");
            
            lock.lock();
            try {
                System.out.println("第2次加锁，持有次数: " + lock.getHoldCount());
                
                // 业务逻辑
                
            } finally {
                lock.unlock();
                System.out.println("第2次解锁，持有次数: " + lock.getHoldCount());
            }
            
        } finally {
            lock.unlock();
            System.out.println("第1次解锁，持有次数: " + lock.getHoldCount());
        }
    }
    
    /**
     * 示例5：多线程场景 - 不同线程无法重入
     */
    public void multiThreadExample() throws InterruptedException {
        RLock lock = redissonClient.getLock("multiThreadLock");
        
        // 线程1获取锁
        Thread thread1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("线程1 获取锁成功");
                
                // 持有锁10秒
                Thread.sleep(10000);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
                System.out.println("线程1 释放锁");
            }
        }, "Thread-1");
        
        // 线程2尝试获取锁（会失败，因为锁被线程1持有）
        Thread thread2 = new Thread(() -> {
            try {
                boolean locked = lock.tryLock(3, TimeUnit.SECONDS);
                if (locked) {
                    try {
                        System.out.println("线程2 获取锁成功");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("线程2 获取锁失败（锁被线程1持有）");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Thread-2");
        
        thread1.start();
        Thread.sleep(1000); // 确保线程1先获取锁
        thread2.start();
        
        thread1.join();
        thread2.join();
    }
    
    /**
     * 示例6：检测重入深度
     */
    public void detectReentrantDepth() {
        RLock lock = redissonClient.getLock("depthLock");
        
        int depth = 0;
        try {
            // 模拟深度重入
            for (int i = 0; i < 10; i++) {
                lock.lock();
                depth++;
                System.out.println("加锁次数: " + depth + ", 持有次数: " + lock.getHoldCount());
            }
            
            System.out.println("最大重入深度: " + depth);
            
        } finally {
            // 释放所有锁
            for (int i = 0; i < depth; i++) {
                lock.unlock();
                System.out.println("解锁次数: " + (i + 1) + ", 剩余持有次数: " + lock.getHoldCount());
            }
        }
    }
}
