package com.huabin.redis.problem.cluster;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 热点Key问题演示
 * 
 * 问题：某个Key被大量访问，导致单个节点压力过大
 * 场景：秒杀商品、热门新闻、明星微博
 */
@Service
public class HotKeyProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 问题：热点Key导致单节点压力过大
     * 
     * 场景：
     * 1. 秒杀商品被10万用户同时访问
     * 2. 在Redis Cluster中，该Key只在一个节点上
     * 3. 该节点压力巨大，其他节点空闲
     * 4. 可能导致该节点宕机
     */
    public void hotKeyProblem() {
        System.out.println("=== 热点Key问题演示 ===");
        
        // 创建热点商品
        Long hotProductId = 888L;
        String hotKey = "seckill:product:" + hotProductId;
        
        Product hotProduct = new Product();
        hotProduct.setId(hotProductId);
        hotProduct.setName("iPhone 15 Pro Max");
        hotProduct.setPrice(new BigDecimal("9999.00"));
        hotProduct.setStock(100);
        
        redisTemplate.opsForValue().set(hotKey, hotProduct, 1, TimeUnit.HOURS);
        
        System.out.println("热点商品：" + hotProduct.getName());
        System.out.println("Key: " + hotKey);
        System.out.println("");
        
        System.out.println("问题分析：");
        System.out.println("1. 在Redis Cluster中，Key通过CRC16(key) % 16384计算slot");
        System.out.println("2. 该Key只会存储在一个节点上");
        System.out.println("3. 所有请求都打到这一个节点");
        System.out.println("4. 该节点CPU、网络IO压力巨大");
        System.out.println("5. 其他节点空闲，资源浪费");
    }
    
    /**
     * 模拟热点Key访问
     */
    public void simulateHotKeyAccess() throws InterruptedException {
        System.out.println("\n=== 模拟热点Key访问 ===");
        
        Long hotProductId = 888L;
        String hotKey = "seckill:product:" + hotProductId;
        int concurrentUsers = 10000;
        
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        
        long startTime = System.currentTimeMillis();
        
        // 10000个用户同时访问同一个Key
        for (int i = 0; i < concurrentUsers; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    // 访问热点Key
                    redisTemplate.opsForValue().get(hotKey);
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
        
        System.out.println("并发访问数: " + concurrentUsers);
        System.out.println("耗时: " + (endTime - startTime) + "ms");
        System.out.println("");
        System.out.println("问题：");
        System.out.println("1. 所有请求都打到同一个Redis节点");
        System.out.println("2. 该节点网络带宽可能被打满");
        System.out.println("3. CPU使用率飙升");
        System.out.println("4. 可能导致节点宕机");
    }
    
    /**
     * 热点Key的危害
     */
    public void hotKeyDangers() {
        System.out.println("\n=== 热点Key的危害 ===");
        System.out.println("1. 单节点压力过大：");
        System.out.println("   - CPU使用率100%");
        System.out.println("   - 网络带宽打满");
        System.out.println("   - 响应时间变长");
        System.out.println("");
        System.out.println("2. 节点宕机风险：");
        System.out.println("   - 单节点负载过高可能宕机");
        System.out.println("   - 影响该节点上的其他Key");
        System.out.println("");
        System.out.println("3. 资源浪费：");
        System.out.println("   - 其他节点空闲");
        System.out.println("   - 集群资源利用率低");
        System.out.println("");
        System.out.println("4. 缓存击穿：");
        System.out.println("   - 热点Key过期时，大量请求打到数据库");
        System.out.println("   - 数据库压力巨大");
    }
    
    /**
     * 热点Key检测
     */
    public void detectHotKey() {
        System.out.println("\n=== 热点Key检测方法 ===");
        System.out.println("1. Redis 4.0+ 内置检测：");
        System.out.println("   redis-cli --hotkeys");
        System.out.println("");
        System.out.println("2. 客户端统计：");
        System.out.println("   - 在应用层统计Key访问频率");
        System.out.println("   - 使用本地计数器或日志分析");
        System.out.println("");
        System.out.println("3. 代理层统计：");
        System.out.println("   - 使用Codis、Twemproxy等代理");
        System.out.println("   - 代理层统计Key访问频率");
        System.out.println("");
        System.out.println("4. 监控告警：");
        System.out.println("   - 监控单节点QPS");
        System.out.println("   - 监控单节点网络流量");
        System.out.println("   - 设置告警阈值");
    }
}
