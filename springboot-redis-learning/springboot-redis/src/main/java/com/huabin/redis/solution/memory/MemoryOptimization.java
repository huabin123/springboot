package com.huabin.redis.solution.memory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 内存优化解决方案
 * 
 * 方案1：设置合理的过期时间
 * 方案2：限制集合大小
 * 方案3：配置内存淘汰策略
 * 方案4：内存碎片整理
 */
@Service
public class MemoryOptimization {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 解决方案1：设置合理的过期时间
     * 
     * 优点：
     * 1. 自动清理过期数据
     * 2. 避免内存泄漏
     * 3. 加随机值避免雪崩
     */
    public void setProperExpireTime() {
        System.out.println("=== 解决方案：设置合理的过期时间 ===");
        
        // Session：30分钟
        for (int i = 1; i <= 100; i++) {
            String sessionKey = "session:" + i;
            String sessionData = "user_session_data_" + i;
            
            // 基础过期时间 + 随机值
            int baseExpire = 1800; // 30分钟
            int randomExpire = ThreadLocalRandom.current().nextInt(300); // 0-5分钟
            
            redisTemplate.opsForValue().set(
                sessionKey, 
                sessionData, 
                baseExpire + randomExpire, 
                TimeUnit.SECONDS
            );
        }
        
        System.out.println("已创建100个Session，过期时间：30分钟 + (0-5分钟)");
        
        // 检查过期时间
        Long ttl = redisTemplate.getExpire("session:1", TimeUnit.SECONDS);
        System.out.println("session:1 的TTL: " + ttl + "秒");
        
        System.out.println("\n优势：");
        System.out.println("1. 自动清理过期数据");
        System.out.println("2. 避免内存泄漏");
        System.out.println("3. 加随机值避免雪崩");
    }
    
    /**
     * 解决方案2：限制集合大小
     * 
     * 优点：
     * 1. 控制内存占用
     * 2. 保留最新数据
     */
    public void limitCollectionSize() {
        System.out.println("\n=== 解决方案：限制集合大小 ===");
        
        String userFeedsKey = "user:feeds:1001";
        int maxSize = 100; // 最多保留100条动态
        
        // 添加新动态
        for (int i = 1; i <= 200; i++) {
            String feed = "feed_" + i + "_content";
            
            // 左侧插入（最新的在前面）
            redisTemplate.opsForList().leftPush(userFeedsKey, feed);
            
            // 保留最新的100条
            redisTemplate.opsForList().trim(userFeedsKey, 0, maxSize - 1);
        }
        
        Long size = redisTemplate.opsForList().size(userFeedsKey);
        System.out.println("用户动态数量: " + size + "（限制为" + maxSize + "条）");
        
        System.out.println("\n优势：");
        System.out.println("1. 控制内存占用");
        System.out.println("2. 保留最新数据");
        System.out.println("3. 自动淘汰旧数据");
        
        // 清理
        redisTemplate.delete(userFeedsKey);
    }
    
    /**
     * 解决方案3：配置内存淘汰策略
     * 
     * 推荐配置：allkeys-lru
     */
    public void configureEvictionPolicy() {
        System.out.println("\n=== 解决方案：配置内存淘汰策略 ===");
        
        System.out.println("推荐配置（redis.conf）：");
        System.out.println("");
        System.out.println("# 设置最大内存");
        System.out.println("maxmemory 2gb");
        System.out.println("");
        System.out.println("# 设置淘汰策略");
        System.out.println("maxmemory-policy allkeys-lru");
        System.out.println("");
        System.out.println("# 采样数量");
        System.out.println("maxmemory-samples 5");
        System.out.println("");
        
        System.out.println("淘汰策略说明：");
        System.out.println("1. noeviction：内存满时拒绝写入（不推荐）");
        System.out.println("2. allkeys-lru：淘汰最近最少使用的key（推荐）");
        System.out.println("3. allkeys-lfu：淘汰最少使用频率的key");
        System.out.println("4. volatile-lru：只淘汰设置了过期时间的key");
        System.out.println("5. volatile-ttl：淘汰即将过期的key");
    }
    
    /**
     * 解决方案4：内存碎片整理
     * 
     * Redis 4.0+ 支持自动碎片整理
     */
    public void configureDefragmentation() {
        System.out.println("\n=== 解决方案：内存碎片整理 ===");
        
        System.out.println("配置（redis.conf）：");
        System.out.println("");
        System.out.println("# 开启自动碎片整理");
        System.out.println("activedefrag yes");
        System.out.println("");
        System.out.println("# 碎片达到100MB才整理");
        System.out.println("active-defrag-ignore-bytes 100mb");
        System.out.println("");
        System.out.println("# 碎片率超过10%才整理");
        System.out.println("active-defrag-threshold-lower 10");
        System.out.println("");
        System.out.println("# CPU占用控制");
        System.out.println("active-defrag-cycle-min 5   # 最小5%");
        System.out.println("active-defrag-cycle-max 75  # 最大75%");
        System.out.println("");
        
        System.out.println("手动整理（重启Redis）：");
        System.out.println("1. 主从切换");
        System.out.println("2. 重启从节点");
        System.out.println("3. 主从切换回来");
    }
    
    /**
     * 解决方案5：定期清理过期Key
     * 
     * 使用定时任务扫描并删除过期Key
     */
    public void scheduledCleanup() {
        System.out.println("\n=== 解决方案：定期清理 ===");
        
        System.out.println("定时任务示例：");
        System.out.println("");
        System.out.println("@Scheduled(cron = \"0 0 2 * * ?\") // 每天凌晨2点");
        System.out.println("public void cleanupExpiredKeys() {");
        System.out.println("    // 扫描特定前缀的Key");
        System.out.println("    ScanOptions options = ScanOptions.scanOptions()");
        System.out.println("        .match(\"temp:*\")");
        System.out.println("        .count(100)");
        System.out.println("        .build();");
        System.out.println("    ");
        System.out.println("    // 删除过期Key");
        System.out.println("    Cursor<byte[]> cursor = redisTemplate.scan(options);");
        System.out.println("    while (cursor.hasNext()) {");
        System.out.println("        String key = new String(cursor.next());");
        System.out.println("        Long ttl = redisTemplate.getExpire(key);");
        System.out.println("        if (ttl != null && ttl < 0) {");
        System.out.println("            redisTemplate.delete(key);");
        System.out.println("        }");
        System.out.println("    }");
        System.out.println("}");
    }
    
    /**
     * 解决方案6：使用更节省内存的数据结构
     */
    public void useMemoryEfficientDataStructure() {
        System.out.println("\n=== 解决方案：使用节省内存的数据结构 ===");
        
        System.out.println("1. Hash 优化：");
        System.out.println("   - 字段数 < 512 使用 ziplist");
        System.out.println("   - 值大小 < 64字节 使用 ziplist");
        System.out.println("   - 配置：hash-max-ziplist-entries 512");
        System.out.println("   - 配置：hash-max-ziplist-value 64");
        System.out.println("");
        
        System.out.println("2. List 优化：");
        System.out.println("   - 使用 quicklist（ziplist + linkedlist）");
        System.out.println("   - 配置：list-max-ziplist-size -2");
        System.out.println("");
        
        System.out.println("3. Set 优化：");
        System.out.println("   - 元素都是整数且 < 512 使用 intset");
        System.out.println("   - 配置：set-max-intset-entries 512");
        System.out.println("");
        
        System.out.println("4. ZSet 优化：");
        System.out.println("   - 元素数 < 128 使用 ziplist");
        System.out.println("   - 配置：zset-max-ziplist-entries 128");
    }
    
    /**
     * 内存监控
     */
    public void memoryMonitoring() {
        System.out.println("\n=== 内存监控 ===");
        
        System.out.println("监控指标：");
        System.out.println("1. used_memory：已使用内存");
        System.out.println("2. used_memory_rss：操作系统分配的内存");
        System.out.println("3. mem_fragmentation_ratio：碎片率");
        System.out.println("4. evicted_keys：淘汰的key数量");
        System.out.println("5. expired_keys：过期的key数量");
        System.out.println("");
        
        System.out.println("告警规则：");
        System.out.println("1. 内存使用率 > 80%");
        System.out.println("2. 碎片率 > 1.5");
        System.out.println("3. 淘汰速率 > 100/秒");
    }
    
    /**
     * 内存优化总结
     */
    public void optimizationSummary() {
        System.out.println("\n=== 内存优化总结 ===");
        System.out.println("1. 过期时间：");
        System.out.println("   ✅ 所有Key设置过期时间");
        System.out.println("   ✅ 加随机值避免雪崩");
        System.out.println("");
        System.out.println("2. 集合大小：");
        System.out.println("   ✅ 限制集合元素数量");
        System.out.println("   ✅ 使用LTRIM保留最新数据");
        System.out.println("");
        System.out.println("3. 淘汰策略：");
        System.out.println("   ✅ maxmemory-policy allkeys-lru");
        System.out.println("   ✅ 设置合理的maxmemory");
        System.out.println("");
        System.out.println("4. 碎片整理：");
        System.out.println("   ✅ 开启activedefrag");
        System.out.println("   ✅ 定期重启从节点");
        System.out.println("");
        System.out.println("5. 数据结构：");
        System.out.println("   ✅ 利用ziplist节省内存");
        System.out.println("   ✅ 拆分BigKey");
    }
}
