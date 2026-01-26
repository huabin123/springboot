package com.huabin.redis.problem.memory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 内存泄漏问题演示
 * 
 * 问题：Redis内存持续增长，无法释放
 * 场景：
 * 1. 没有设置过期时间
 * 2. 集合无限增长
 * 3. 内存淘汰策略不当
 */
@Service
public class MemoryLeakProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 问题1：没有设置过期时间
     * 
     * 问题点：
     * 1. 缓存数据永不过期
     * 2. 内存持续增长
     * 3. 最终导致内存溢出
     */
    public void noExpireTime_Problem() {
        System.out.println("=== 问题：没有设置过期时间 ===");
        
        // 模拟缓存用户Session
        for (int i = 1; i <= 1000; i++) {
            String sessionKey = "session:" + i;
            String sessionData = "user_session_data_" + i;
            
            // 问题：没有设置过期时间
            redisTemplate.opsForValue().set(sessionKey, sessionData);
        }
        
        System.out.println("已创建1000个Session，但没有设置过期时间");
        System.out.println("问题：这些Session永不过期，内存持续增长！");
        
        // 检查过期时间
        Long ttl = redisTemplate.getExpire("session:1", TimeUnit.SECONDS);
        System.out.println("session:1 的TTL: " + ttl + "秒（-1表示永不过期）");
    }
    
    /**
     * 问题2：集合无限增长
     * 
     * 问题点：
     * 1. List/Set/ZSet 不断添加元素
     * 2. 没有限制大小
     * 3. 内存持续增长
     */
    public void unlimitedCollection_Problem() {
        System.out.println("\n=== 问题：集合无限增长 ===");
        
        String userFeedsKey = "user:feeds:1001";
        
        // 模拟用户动态不断增加
        for (int i = 1; i <= 10000; i++) {
            String feed = "feed_" + i + "_content";
            redisTemplate.opsForList().leftPush(userFeedsKey, feed);
        }
        
        Long size = redisTemplate.opsForList().size(userFeedsKey);
        System.out.println("用户动态数量: " + size);
        System.out.println("问题：动态不断增加，没有限制，内存持续增长！");
        
        // 清理
        redisTemplate.delete(userFeedsKey);
    }
    
    /**
     * 问题3：内存淘汰策略不当
     * 
     * 问题点：
     * 1. 使用 noeviction 策略
     * 2. 内存满时拒绝写入
     * 3. 应用报错
     */
    public void wrongEvictionPolicy_Problem() {
        System.out.println("\n=== 问题：内存淘汰策略不当 ===");
        
        System.out.println("当前可能的问题配置：");
        System.out.println("maxmemory-policy noeviction");
        System.out.println("");
        System.out.println("问题：");
        System.out.println("1. 内存满时，拒绝所有写入操作");
        System.out.println("2. 应用报错：OOM command not allowed");
        System.out.println("3. 缓存无法更新");
        System.out.println("");
        System.out.println("建议配置：");
        System.out.println("maxmemory-policy allkeys-lru");
    }
    
    /**
     * 问题4：过期Key未被及时清理
     * 
     * 问题点：
     * 1. 大量Key设置了过期时间
     * 2. 但过期Key占用内存
     * 3. Redis的惰性删除和定期删除可能不够及时
     */
    public void expiredKeyNotCleaned_Problem() {
        System.out.println("\n=== 问题：过期Key未被及时清理 ===");
        
        // 创建大量短期Key
        for (int i = 1; i <= 10000; i++) {
            String key = "temp:key:" + i;
            redisTemplate.opsForValue().set(key, "value" + i, 1, TimeUnit.SECONDS);
        }
        
        System.out.println("已创建10000个1秒过期的Key");
        System.out.println("");
        System.out.println("问题：");
        System.out.println("1. 过期Key不会立即删除");
        System.out.println("2. Redis使用惰性删除+定期删除");
        System.out.println("3. 大量过期Key可能占用内存");
        System.out.println("");
        System.out.println("Redis的删除策略：");
        System.out.println("1. 惰性删除：访问时检查是否过期");
        System.out.println("2. 定期删除：每秒10次随机抽样删除");
    }
    
    /**
     * 问题5：内存碎片
     * 
     * 问题点：
     * 1. 频繁的增删改操作
     * 2. 产生内存碎片
     * 3. 实际内存占用大于数据大小
     */
    public void memoryFragmentation_Problem() {
        System.out.println("\n=== 问题：内存碎片 ===");
        
        // 模拟频繁的增删改
        for (int i = 1; i <= 1000; i++) {
            String key = "frag:key:" + i;
            
            // 创建
            redisTemplate.opsForValue().set(key, "value" + i);
            
            // 修改（改变大小）
            redisTemplate.opsForValue().set(key, "new_longer_value_" + i);
            
            // 删除部分
            if (i % 2 == 0) {
                redisTemplate.delete(key);
            }
        }
        
        System.out.println("已执行1000次增删改操作");
        System.out.println("");
        System.out.println("问题：");
        System.out.println("1. 频繁的增删改产生内存碎片");
        System.out.println("2. 内存碎片率 = used_memory_rss / used_memory");
        System.out.println("3. 碎片率 > 1.5 时需要整理");
        System.out.println("");
        System.out.println("查看碎片率：");
        System.out.println("INFO memory");
        System.out.println("mem_fragmentation_ratio:1.52");
    }
    
    /**
     * 内存泄漏检测
     */
    public void detectMemoryLeak() {
        System.out.println("\n=== 内存泄漏检测方法 ===");
        System.out.println("1. 监控内存使用：");
        System.out.println("   INFO memory");
        System.out.println("   used_memory_human");
        System.out.println("   used_memory_peak_human");
        System.out.println("");
        System.out.println("2. 检查没有过期时间的Key：");
        System.out.println("   SCAN 0 MATCH * COUNT 100");
        System.out.println("   TTL key（-1表示永不过期）");
        System.out.println("");
        System.out.println("3. 检查大Key：");
        System.out.println("   redis-cli --bigkeys");
        System.out.println("   MEMORY USAGE key");
        System.out.println("");
        System.out.println("4. 检查内存碎片：");
        System.out.println("   INFO memory");
        System.out.println("   mem_fragmentation_ratio");
    }
    
    /**
     * 内存泄漏危害
     */
    public void memoryLeakDangers() {
        System.out.println("\n=== 内存泄漏的危害 ===");
        System.out.println("1. 内存溢出：");
        System.out.println("   - Redis进程被OOM Killer杀死");
        System.out.println("   - 服务不可用");
        System.out.println("");
        System.out.println("2. 性能下降：");
        System.out.println("   - 内存不足导致频繁淘汰");
        System.out.println("   - 缓存命中率下降");
        System.out.println("");
        System.out.println("3. 成本增加：");
        System.out.println("   - 需要更大内存的服务器");
        System.out.println("   - 运维成本上升");
    }
}
