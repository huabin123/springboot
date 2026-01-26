package com.huabin.redis.solution.performance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis 阻塞问题解决方案
 * 
 * 方案1：使用SCAN代替KEYS
 * 方案2：分批Pipeline
 * 方案3：使用ZSet代替SORT
 * 方案4：异步操作
 */
@Service
public class BlockingSolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 解决方案1：使用SCAN代替KEYS
     * 
     * 优点：
     * 1. 渐进式遍历，不阻塞Redis
     * 2. 可以中断遍历
     * 3. 适合生产环境
     */
    public void useScanInsteadOfKeys(String pattern) {
        System.out.println("=== 解决方案：使用SCAN代替KEYS ===");
        
        long startTime = System.currentTimeMillis();
        
        // 使用SCAN遍历
        ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(100) // 每次返回约100个
            .build();
        
        Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
            connection -> connection.scan(options)
        );
        
        int count = 0;
        List<String> keys = new ArrayList<>();
        
        while (cursor != null && cursor.hasNext()) {
            byte[] keyBytes = cursor.next();
            String key = new String(keyBytes);
            keys.add(key);
            count++;
            
            if (count % 100 == 0) {
                System.out.println("已扫描 " + count + " 个key");
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("SCAN完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("找到 " + count + " 个key");
        
        System.out.println("\n优势：");
        System.out.println("1. 不阻塞Redis");
        System.out.println("2. 可以随时中断");
        System.out.println("3. 适合生产环境");
    }
    
    /**
     * 解决方案2：分批Pipeline
     * 
     * 优点：
     * 1. 避免单次Pipeline命令过多
     * 2. 减少内存占用
     * 3. 可控制执行时间
     */
    public void batchPipeline() {
        System.out.println("\n=== 解决方案：分批Pipeline ===");
        
        int totalCount = 100000;
        int batchSize = 1000; // 每批1000条
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < totalCount; i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, totalCount);
            
            // 每批执行一次Pipeline
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (int j = start; j < end; j++) {
                    connection.set(
                        ("batch:key:" + j).getBytes(),
                        ("value" + j).getBytes()
                    );
                }
                return null;
            });
            
            if ((i + batchSize) % 10000 == 0) {
                System.out.println("已处理 " + (i + batchSize) + " 条");
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("分批Pipeline完成，耗时: " + (endTime - startTime) + "ms");
        
        System.out.println("\n优势：");
        System.out.println("1. 单次Pipeline命令可控");
        System.out.println("2. 避免内存溢出");
        System.out.println("3. 可以监控进度");
        
        // 清理
        Set<String> keys = redisTemplate.keys("batch:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    
    /**
     * 解决方案3：使用ZSet代替SORT
     * 
     * 优点：
     * 1. ZSet天然有序
     * 2. 查询速度快 O(log N)
     * 3. 不需要SORT命令
     */
    public void useZSetInsteadOfSort() {
        System.out.println("\n=== 解决方案：使用ZSet代替SORT ===");
        
        String zsetKey = "sorted:scores";
        
        // 添加数据到ZSet
        System.out.println("添加10000个元素到ZSet...");
        long startTime = System.currentTimeMillis();
        
        for (int i = 1; i <= 10000; i++) {
            redisTemplate.opsForZSet().add(zsetKey, "member" + i, i);
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("添加完成，耗时: " + (endTime - startTime) + "ms");
        
        // 查询Top 10
        System.out.println("\n查询Top 10...");
        startTime = System.currentTimeMillis();
        
        Set<Object> top10 = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, 9);
        
        endTime = System.currentTimeMillis();
        System.out.println("查询完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("Top 10: " + top10);
        
        System.out.println("\n优势：");
        System.out.println("1. 天然有序，无需SORT");
        System.out.println("2. 查询速度快");
        System.out.println("3. 支持范围查询");
        
        // 清理
        redisTemplate.delete(zsetKey);
    }
    
    /**
     * 解决方案4：异步删除大集合
     * 
     * 优点：
     * 1. 不阻塞主线程
     * 2. 分批删除
     */
    public void asyncDeleteLargeSet(String setKey) {
        System.out.println("\n=== 解决方案：异步删除大集合 ===");
        
        // 启动异步线程删除
        new Thread(() -> {
            System.out.println("开始异步删除...");
            
            // 使用SSCAN遍历
            ScanOptions options = ScanOptions.scanOptions()
                .count(100)
                .build();
            
            Cursor<Object> cursor = redisTemplate.opsForSet().scan(setKey, options);
            
            List<Object> membersToDelete = new ArrayList<>();
            int deletedCount = 0;
            
            while (cursor.hasNext()) {
                Object member = cursor.next();
                membersToDelete.add(member);
                
                // 每100个删除一次
                if (membersToDelete.size() >= 100) {
                    redisTemplate.opsForSet().remove(setKey, membersToDelete.toArray());
                    deletedCount += membersToDelete.size();
                    membersToDelete.clear();
                    
                    System.out.println("已删除 " + deletedCount + " 个元素");
                    
                    // 休眠一下，避免占用过多资源
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // 删除剩余的
            if (!membersToDelete.isEmpty()) {
                redisTemplate.opsForSet().remove(setKey, membersToDelete.toArray());
                deletedCount += membersToDelete.size();
            }
            
            // 最后删除key
            redisTemplate.delete(setKey);
            
            System.out.println("异步删除完成，总共删除: " + deletedCount + " 个元素");
        }).start();
        
        System.out.println("已启动异步删除任务");
    }
    
    /**
     * 解决方案5：使用SINTERSTORE异步计算交集
     * 
     * 优点：
     * 1. 异步计算，不阻塞客户端
     * 2. 结果存储在Redis中
     */
    public void asyncIntersection(String set1, String set2, String destKey) {
        System.out.println("\n=== 解决方案：异步计算交集 ===");
        
        // 使用SINTERSTORE，结果存储到destKey
        long startTime = System.currentTimeMillis();
        
        redisTemplate.opsForSet().intersectAndStore(set1, set2, destKey);
        
        long endTime = System.currentTimeMillis();
        System.out.println("SINTERSTORE完成，耗时: " + (endTime - startTime) + "ms");
        
        // 获取结果大小
        Long size = redisTemplate.opsForSet().size(destKey);
        System.out.println("交集大小: " + size);
        
        System.out.println("\n优势：");
        System.out.println("1. 结果存储在Redis中");
        System.out.println("2. 可以多次使用结果");
        System.out.println("3. 避免重复计算");
    }
    
    /**
     * 解决方案6：Lua脚本优化
     * 
     * 优点：
     * 1. 原子操作
     * 2. 减少网络往返
     * 3. 但要注意脚本复杂度
     */
    public void optimizedLuaScript() {
        System.out.println("\n=== 解决方案：优化Lua脚本 ===");
        
        // 简单的Lua脚本
        String script = 
            "local count = redis.call('get', KEYS[1]) " +
            "if count then " +
            "    return redis.call('incr', KEYS[1]) " +
            "else " +
            "    redis.call('set', KEYS[1], 1) " +
            "    return 1 " +
            "end";
        
        System.out.println("Lua脚本优化建议：");
        System.out.println("1. 避免复杂逻辑");
        System.out.println("2. 避免大量循环");
        System.out.println("3. 避免阻塞操作");
        System.out.println("4. 控制脚本执行时间");
    }
    
    /**
     * 性能优化总结
     */
    public void performanceOptimizationSummary() {
        System.out.println("\n=== 性能优化总结 ===");
        System.out.println("1. 命令选择：");
        System.out.println("   - 使用SCAN代替KEYS");
        System.out.println("   - 使用HSCAN代替HGETALL");
        System.out.println("   - 使用SSCAN代替SMEMBERS");
        System.out.println("   - 使用ZSCAN代替ZRANGE 0 -1");
        System.out.println("");
        System.out.println("2. 批量操作：");
        System.out.println("   - Pipeline分批执行（每批1000-5000）");
        System.out.println("   - 使用MGET/MSET代替多次GET/SET");
        System.out.println("");
        System.out.println("3. 数据结构：");
        System.out.println("   - 使用ZSet代替List+SORT");
        System.out.println("   - 拆分BigKey");
        System.out.println("");
        System.out.println("4. 异步操作：");
        System.out.println("   - 使用UNLINK代替DEL");
        System.out.println("   - 使用BGSAVE代替SAVE");
        System.out.println("   - 异步删除大集合");
    }
}
