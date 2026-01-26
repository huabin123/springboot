package com.huabin.redis.solution.performance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BigKey 解决方案
 * 
 * 方案1：拆分BigKey
 * 方案2：使用SCAN代替全量操作
 * 方案3：异步删除
 * 方案4：压缩数据
 */
@Service
public class BigKeySolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 解决方案1：拆分BigHash
     * 
     * 原理：
     * 将一个大Hash拆分成多个小Hash
     * 例如：users:all → users:shard:0, users:shard:1, ...
     */
    public void splitBigHash() {
        System.out.println("=== 解决方案：拆分BigHash ===");
        
        int userCount = 1000;
        int shardCount = 10; // 拆分成10个分片
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 1; i <= userCount; i++) {
            // 计算分片ID（取模）
            int shardId = i % shardCount;
            String shardKey = "users:shard:" + shardId;
            
            String userId = String.valueOf(i);
            String userInfo = "user" + i + "@example.com";
            
            redisTemplate.opsForHash().put(shardKey, userId, userInfo);
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("拆分完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("已拆分成 " + shardCount + " 个分片");
        
        // 查询单个用户
        int userId = 123;
        int shardId = userId % shardCount;
        String shardKey = "users:shard:" + shardId;
        Object userInfo = redisTemplate.opsForHash().get(shardKey, String.valueOf(userId));
        System.out.println("查询用户" + userId + ": " + userInfo);
        
        // 优势：每个分片只有100个用户，操作快速
        System.out.println("\n优势：");
        System.out.println("1. 每个分片数据量小，操作快速");
        System.out.println("2. 删除单个分片不会阻塞Redis");
        System.out.println("3. 可以并行操作多个分片");
    }
    
    /**
     * 解决方案2：使用SCAN代替HGETALL
     * 
     * 原理：
     * SCAN是渐进式遍历，不会阻塞Redis
     */
    public void useScanInsteadOfHgetall(String hashKey) {
        System.out.println("\n=== 解决方案：使用HSCAN代替HGETALL ===");
        
        long startTime = System.currentTimeMillis();
        
        // 使用HSCAN遍历Hash
        ScanOptions options = ScanOptions.scanOptions()
            .count(100) // 每次返回100个
            .build();
        
        Cursor<Map.Entry<Object, Object>> cursor = redisTemplate.opsForHash()
            .scan(hashKey, options);
        
        int count = 0;
        while (cursor.hasNext()) {
            Map.Entry<Object, Object> entry = cursor.next();
            count++;
            
            // 处理数据
            if (count % 100 == 0) {
                System.out.println("已处理 " + count + " 条数据");
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("HSCAN完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("总共处理: " + count + " 条数据");
        
        System.out.println("\n优势：");
        System.out.println("1. 渐进式遍历，不会阻塞Redis");
        System.out.println("2. 可以中断遍历");
        System.out.println("3. 适合大数据量场景");
    }
    
    /**
     * 解决方案3：异步删除BigKey
     * 
     * Redis 4.0+ 支持 UNLINK 命令
     * 原理：后台异步删除，不阻塞主线程
     */
    public void asyncDeleteBigKey(String bigKey) {
        System.out.println("\n=== 解决方案：异步删除BigKey ===");
        
        long startTime = System.currentTimeMillis();
        
        // 使用 UNLINK 代替 DEL
        // redisTemplate.unlink(bigKey);
        
        // Spring Data Redis 2.0+ 支持
        redisTemplate.delete(bigKey);
        
        long endTime = System.currentTimeMillis();
        System.out.println("删除完成，耗时: " + (endTime - startTime) + "ms");
        
        System.out.println("\n优势：");
        System.out.println("1. 后台异步删除，不阻塞主线程");
        System.out.println("2. 适合删除大Key");
        System.out.println("3. Redis 4.0+ 支持");
    }
    
    /**
     * 解决方案4：渐进式删除BigHash
     * 
     * 原理：
     * 使用HSCAN + HDEL，分批删除
     */
    public void progressiveDeleteBigHash(String bigHashKey) {
        System.out.println("\n=== 解决方案：渐进式删除BigHash ===");
        
        long startTime = System.currentTimeMillis();
        int deletedCount = 0;
        
        // 使用HSCAN遍历
        ScanOptions options = ScanOptions.scanOptions()
            .count(100)
            .build();
        
        Cursor<Map.Entry<Object, Object>> cursor = redisTemplate.opsForHash()
            .scan(bigHashKey, options);
        
        List<Object> fieldsToDelete = new ArrayList<>();
        
        while (cursor.hasNext()) {
            Map.Entry<Object, Object> entry = cursor.next();
            fieldsToDelete.add(entry.getKey());
            
            // 每100个删除一次
            if (fieldsToDelete.size() >= 100) {
                redisTemplate.opsForHash().delete(bigHashKey, fieldsToDelete.toArray());
                deletedCount += fieldsToDelete.size();
                fieldsToDelete.clear();
                
                System.out.println("已删除 " + deletedCount + " 个字段");
                
                // 休眠一下，避免阻塞
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // 删除剩余的
        if (!fieldsToDelete.isEmpty()) {
            redisTemplate.opsForHash().delete(bigHashKey, fieldsToDelete.toArray());
            deletedCount += fieldsToDelete.size();
        }
        
        // 最后删除key
        redisTemplate.delete(bigHashKey);
        
        long endTime = System.currentTimeMillis();
        System.out.println("渐进式删除完成，耗时: " + (endTime - startTime) + "ms");
        System.out.println("总共删除: " + deletedCount + " 个字段");
        
        System.out.println("\n优势：");
        System.out.println("1. 分批删除，不会长时间阻塞");
        System.out.println("2. 可控制删除速度");
        System.out.println("3. 适合超大Hash");
    }
    
    /**
     * 解决方案5：拆分BigList
     * 
     * 原理：
     * 将一个大List拆分成多个小List
     */
    public void splitBigList() {
        System.out.println("\n=== 解决方案：拆分BigList ===");
        
        int orderCount = 1000;
        int listSize = 100; // 每个List最多100个元素
        
        for (int i = 1; i <= orderCount; i++) {
            // 计算List索引
            int listIndex = (i - 1) / listSize;
            String listKey = "orders:list:" + listIndex;
            
            String order = "order_" + i;
            redisTemplate.opsForList().rightPush(listKey, order);
        }
        
        System.out.println("拆分完成，共 " + (orderCount / listSize) + " 个List");
        
        System.out.println("\n优势：");
        System.out.println("1. 每个List数据量小");
        System.out.println("2. LRANGE操作快速");
        System.out.println("3. 可以按时间分片（如按天）");
    }
    
    /**
     * 解决方案6：压缩大String
     * 
     * 原理：
     * 使用GZIP压缩大对象
     */
    public void compressBigString() {
        System.out.println("\n=== 解决方案：压缩大String ===");
        
        // 创建大对象
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeData.append("这是一段很长的商品描述信息，包含大量的文字和图片链接...");
        }
        
        String originalData = largeData.toString();
        int originalSize = originalData.length();
        
        // 压缩（实际应使用GZIP）
        // byte[] compressed = compress(originalData);
        // String compressedData = Base64.getEncoder().encodeToString(compressed);
        
        System.out.println("原始大小: " + (originalSize / 1024) + "KB");
        // System.out.println("压缩后大小: " + (compressedData.length() / 1024) + "KB");
        // System.out.println("压缩率: " + (100 - compressedData.length() * 100 / originalSize) + "%");
        
        System.out.println("\n优势：");
        System.out.println("1. 减少内存占用");
        System.out.println("2. 减少网络传输");
        System.out.println("3. 适合文本数据");
    }
    
    /**
     * BigKey 优化建议
     */
    public void optimizationSuggestions() {
        System.out.println("\n=== BigKey 优化建议 ===");
        System.out.println("1. 拆分策略：");
        System.out.println("   - Hash: 按分片拆分（取模）");
        System.out.println("   - List: 按大小或时间拆分");
        System.out.println("   - Set: 按分片拆分");
        System.out.println("   - ZSet: 按分数范围拆分");
        System.out.println("");
        System.out.println("2. 操作建议：");
        System.out.println("   - 使用SCAN代替KEYS");
        System.out.println("   - 使用HSCAN代替HGETALL");
        System.out.println("   - 使用SSCAN代替SMEMBERS");
        System.out.println("   - 使用ZSCAN代替ZRANGE");
        System.out.println("");
        System.out.println("3. 删除建议：");
        System.out.println("   - 使用UNLINK代替DEL（Redis 4.0+）");
        System.out.println("   - 渐进式删除大集合");
        System.out.println("   - 分批删除，避免阻塞");
        System.out.println("");
        System.out.println("4. 监控建议：");
        System.out.println("   - 定期扫描BigKey");
        System.out.println("   - 监控内存使用");
        System.out.println("   - 设置告警阈值");
    }
}
