package com.huabin.redis.problem.performance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 阻塞问题演示
 * 
 * 问题：某些操作会阻塞Redis，影响其他请求
 * 场景：
 * 1. KEYS * 命令
 * 2. FLUSHALL/FLUSHDB
 * 3. SAVE命令（RDB持久化）
 * 4. 慢查询
 */
@Service
public class BlockingProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 问题1：使用KEYS命令
     * 
     * 问题点：
     * 1. KEYS * 会遍历所有key
     * 2. 阻塞Redis，其他请求无法执行
     * 3. 生产环境禁止使用
     */
    public void useKeysCommand_Problem() {
        System.out.println("=== 问题：使用KEYS命令 ===");
        
        // 先创建大量key
        System.out.println("创建10000个key...");
        for (int i = 1; i <= 10000; i++) {
            redisTemplate.opsForValue().set("test:key:" + i, "value" + i);
        }
        
        // 使用KEYS命令查找
        System.out.println("\n执行 KEYS test:* 命令...");
        long startTime = System.currentTimeMillis();
        
        Set<String> keys = redisTemplate.keys("test:*");
        
        long endTime = System.currentTimeMillis();
        System.out.println("KEYS命令耗时: " + (endTime - startTime) + "ms");
        System.out.println("找到 " + (keys != null ? keys.size() : 0) + " 个key");
        
        System.out.println("\n问题：");
        System.out.println("1. KEYS命令会阻塞Redis");
        System.out.println("2. 在此期间，其他请求无法执行");
        System.out.println("3. key越多，阻塞时间越长");
        System.out.println("4. 生产环境禁止使用！");
        
        // 清理
        if (keys != null) {
            redisTemplate.delete(keys);
        }
    }
    
    /**
     * 问题2：慢查询
     * 
     * 问题点：
     * 1. 复杂的Lua脚本
     * 2. 大量的MGET/MSET
     * 3. 大集合的交集、并集运算
     */
    public void slowQuery_Problem() {
        System.out.println("\n=== 问题：慢查询 ===");
        
        // 创建两个大集合
        System.out.println("创建两个大集合...");
        String set1 = "set:1";
        String set2 = "set:2";
        
        for (int i = 1; i <= 10000; i++) {
            redisTemplate.opsForSet().add(set1, "member" + i);
        }
        
        for (int i = 5000; i <= 15000; i++) {
            redisTemplate.opsForSet().add(set2, "member" + i);
        }
        
        // 执行交集运算
        System.out.println("\n执行 SINTER 命令（求交集）...");
        long startTime = System.currentTimeMillis();
        
        Set<Object> intersection = redisTemplate.opsForSet().intersect(set1, set2);
        
        long endTime = System.currentTimeMillis();
        System.out.println("SINTER耗时: " + (endTime - startTime) + "ms");
        System.out.println("交集大小: " + (intersection != null ? intersection.size() : 0));
        
        System.out.println("\n问题：");
        System.out.println("1. 大集合的交集运算耗时长");
        System.out.println("2. 会阻塞Redis");
        System.out.println("3. 建议：使用SINTERSTORE存储结果，异步计算");
        
        // 清理
        redisTemplate.delete(set1);
        redisTemplate.delete(set2);
    }
    
    /**
     * 问题3：大量的Pipeline操作
     * 
     * 问题点：
     * 1. Pipeline中命令过多
     * 2. 单次Pipeline执行时间长
     * 3. 可能导致内存溢出
     */
    public void largePipeline_Problem() {
        System.out.println("\n=== 问题：大量Pipeline操作 ===");
        
        long startTime = System.currentTimeMillis();
        
        // 一次性Pipeline 10万条命令
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (int i = 1; i <= 100000; i++) {
                connection.set(
                    ("pipeline:key:" + i).getBytes(),
                    ("value" + i).getBytes()
                );
            }
            return null;
        });
        
        long endTime = System.currentTimeMillis();
        System.out.println("Pipeline耗时: " + (endTime - startTime) + "ms");
        
        System.out.println("\n问题：");
        System.out.println("1. 单次Pipeline命令过多");
        System.out.println("2. 执行时间长，阻塞Redis");
        System.out.println("3. 可能导致客户端内存溢出");
        System.out.println("4. 建议：分批执行，每批1000-5000条");
        
        // 清理
        Set<String> keys = redisTemplate.keys("pipeline:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    
    /**
     * 问题4：SORT命令
     * 
     * 问题点：
     * 1. SORT命令会阻塞Redis
     * 2. 数据量大时耗时长
     */
    public void sortCommand_Problem() {
        System.out.println("\n=== 问题：SORT命令 ===");
        
        String listKey = "sort:list";
        
        // 创建一个大List
        System.out.println("创建包含10000个元素的List...");
        for (int i = 10000; i >= 1; i--) {
            redisTemplate.opsForList().rightPush(listKey, String.valueOf(i));
        }
        
        // 执行SORT
        System.out.println("\n执行 SORT 命令...");
        long startTime = System.currentTimeMillis();
        
        List<Object> sorted = redisTemplate.opsForList().range(listKey, 0, -1);
        // 实际的SORT命令：SORT listKey
        
        long endTime = System.currentTimeMillis();
        System.out.println("SORT耗时: " + (endTime - startTime) + "ms");
        
        System.out.println("\n问题：");
        System.out.println("1. SORT命令会阻塞Redis");
        System.out.println("2. 数据量大时耗时长");
        System.out.println("3. 建议：使用ZSet代替，或在应用层排序");
        
        // 清理
        redisTemplate.delete(listKey);
    }
    
    /**
     * 阻塞问题总结
     */
    public void blockingProblemsSummary() {
        System.out.println("\n=== Redis 阻塞问题总结 ===");
        System.out.println("1. 禁用命令：");
        System.out.println("   - KEYS *（使用SCAN代替）");
        System.out.println("   - FLUSHALL/FLUSHDB");
        System.out.println("   - SAVE（使用BGSAVE）");
        System.out.println("");
        System.out.println("2. 慢查询：");
        System.out.println("   - 大集合的交集、并集");
        System.out.println("   - 复杂的Lua脚本");
        System.out.println("   - SORT命令");
        System.out.println("");
        System.out.println("3. Pipeline：");
        System.out.println("   - 单次命令不超过5000条");
        System.out.println("   - 分批执行");
        System.out.println("");
        System.out.println("4. 监控：");
        System.out.println("   - 开启慢查询日志");
        System.out.println("   - slowlog-log-slower-than 10000（10ms）");
        System.out.println("   - slowlog-max-len 128");
    }
    
    /**
     * 慢查询配置示例
     */
    public void slowlogConfig() {
        System.out.println("\n=== 慢查询配置 ===");
        System.out.println("# redis.conf");
        System.out.println("slowlog-log-slower-than 10000  # 超过10ms的命令记录");
        System.out.println("slowlog-max-len 128            # 最多保留128条");
        System.out.println("");
        System.out.println("# 查看慢查询");
        System.out.println("SLOWLOG GET 10");
        System.out.println("");
        System.out.println("# 清空慢查询");
        System.out.println("SLOWLOG RESET");
    }
}
