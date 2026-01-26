package com.huabin.redis.problem.performance;

import com.huabin.redis.model.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * BigKey 问题演示
 * 
 * 问题：单个key存储的数据过大，导致性能问题
 * 场景：
 * 1. 单个Hash存储大量字段
 * 2. 单个List存储大量元素
 * 3. 单个String存储大对象
 */
@Service
public class BigKeyProblem {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 问题1：单个Hash存储所有用户信息
     * 
     * 问题点：
     * 1. 100万用户存储在一个Hash中
     * 2. HGETALL 操作会阻塞Redis
     * 3. 删除操作耗时长
     * 4. 内存占用大，可能导致内存碎片
     */
    public void createBigHash_Problem() {
        System.out.println("=== 创建 BigKey（Hash）===");
        
        String bigHashKey = "users:all";
        long startTime = System.currentTimeMillis();
        
        // 模拟存储100万用户（实际演示用1000个）
        int userCount = 1000;
        for (int i = 1; i <= userCount; i++) {
            String userId = String.valueOf(i);
            String userInfo = "user" + i + "@example.com";
            redisTemplate.opsForHash().put(bigHashKey, userId, userInfo);
            
            if (i % 100 == 0) {
                System.out.println("已存储 " + i + " 个用户...");
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("存储完成，耗时: " + (endTime - startTime) + "ms");
        
        // 获取Hash大小
        Long size = redisTemplate.opsForHash().size(bigHashKey);
        System.out.println("Hash大小: " + size + " 个字段");
        
        // 问题：HGETALL 操作会阻塞
        System.out.println("\n执行 HGETALL 操作...");
        startTime = System.currentTimeMillis();
        Map<Object, Object> allUsers = redisTemplate.opsForHash().entries(bigHashKey);
        endTime = System.currentTimeMillis();
        System.out.println("HGETALL 耗时: " + (endTime - startTime) + "ms");
        System.out.println("问题：HGETALL 会阻塞Redis，影响其他请求！");
        
        // 问题：删除操作耗时长
        System.out.println("\n执行 DEL 操作...");
        startTime = System.currentTimeMillis();
        redisTemplate.delete(bigHashKey);
        endTime = System.currentTimeMillis();
        System.out.println("DEL 耗时: " + (endTime - startTime) + "ms");
        System.out.println("问题：删除大Key会阻塞Redis！");
    }
    
    /**
     * 问题2：单个List存储大量订单
     * 
     * 问题点：
     * 1. 单个List存储100万订单
     * 2. LRANGE 0 -1 会阻塞
     * 3. 内存占用大
     */
    public void createBigList_Problem() {
        System.out.println("\n=== 创建 BigKey（List）===");
        
        String bigListKey = "orders:all";
        long startTime = System.currentTimeMillis();
        
        // 模拟存储大量订单（实际演示用1000个）
        int orderCount = 1000;
        for (int i = 1; i <= orderCount; i++) {
            String order = "order_" + i;
            redisTemplate.opsForList().rightPush(bigListKey, order);
            
            if (i % 100 == 0) {
                System.out.println("已存储 " + i + " 个订单...");
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.println("存储完成，耗时: " + (endTime - startTime) + "ms");
        
        // 获取List大小
        Long size = redisTemplate.opsForList().size(bigListKey);
        System.out.println("List大小: " + size + " 个元素");
        
        // 问题：LRANGE 0 -1 会阻塞
        System.out.println("\n执行 LRANGE 0 -1 操作...");
        startTime = System.currentTimeMillis();
        redisTemplate.opsForList().range(bigListKey, 0, -1);
        endTime = System.currentTimeMillis();
        System.out.println("LRANGE 耗时: " + (endTime - startTime) + "ms");
        System.out.println("问题：LRANGE 0 -1 会返回所有元素，阻塞Redis！");
        
        // 清理
        redisTemplate.delete(bigListKey);
    }
    
    /**
     * 问题3：单个String存储大对象
     * 
     * 问题点：
     * 1. 单个String存储10MB的JSON
     * 2. GET操作耗时长
     * 3. 网络传输慢
     */
    public void createBigString_Problem() {
        System.out.println("\n=== 创建 BigKey（String）===");
        
        String bigStringKey = "product:detail:big";
        
        // 创建一个大对象（模拟10MB数据）
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            largeData.append("这是一段很长的商品描述信息，包含大量的文字和图片链接...");
        }
        
        String bigValue = largeData.toString();
        System.out.println("大对象大小: " + (bigValue.length() / 1024 / 1024) + "MB");
        
        // 存储
        long startTime = System.currentTimeMillis();
        redisTemplate.opsForValue().set(bigStringKey, bigValue);
        long endTime = System.currentTimeMillis();
        System.out.println("SET 耗时: " + (endTime - startTime) + "ms");
        
        // 获取
        System.out.println("\n执行 GET 操作...");
        startTime = System.currentTimeMillis();
        redisTemplate.opsForValue().get(bigStringKey);
        endTime = System.currentTimeMillis();
        System.out.println("GET 耗时: " + (endTime - startTime) + "ms");
        System.out.println("问题：大对象的GET操作耗时长，网络传输慢！");
        
        // 清理
        redisTemplate.delete(bigStringKey);
    }
    
    /**
     * BigKey 的危害总结
     */
    public void demonstrateBigKeyProblems() {
        System.out.println("\n=== BigKey 的危害 ===");
        System.out.println("1. 阻塞问题：");
        System.out.println("   - HGETALL、LRANGE 0 -1 等操作会阻塞Redis");
        System.out.println("   - 影响其他正常请求");
        System.out.println("");
        System.out.println("2. 网络拥塞：");
        System.out.println("   - 大Key的传输占用大量带宽");
        System.out.println("   - 导致网络延迟");
        System.out.println("");
        System.out.println("3. 内存问题：");
        System.out.println("   - 占用大量内存");
        System.out.println("   - 可能导致内存碎片");
        System.out.println("");
        System.out.println("4. 删除问题：");
        System.out.println("   - DEL 操作耗时长");
        System.out.println("   - 可能导致Redis阻塞");
        System.out.println("");
        System.out.println("5. 主从同步：");
        System.out.println("   - BigKey的同步会占用大量带宽");
        System.out.println("   - 可能导致主从延迟");
    }
    
    /**
     * 检测 BigKey
     */
    public void detectBigKey() {
        System.out.println("\n=== 检测 BigKey 的方法 ===");
        System.out.println("1. redis-cli --bigkeys");
        System.out.println("   扫描整个数据库，找出最大的key");
        System.out.println("");
        System.out.println("2. MEMORY USAGE key");
        System.out.println("   查看单个key的内存占用");
        System.out.println("");
        System.out.println("3. STRLEN key (String类型)");
        System.out.println("   查看String的长度");
        System.out.println("");
        System.out.println("4. HLEN key (Hash类型)");
        System.out.println("   查看Hash的字段数量");
        System.out.println("");
        System.out.println("5. LLEN key (List类型)");
        System.out.println("   查看List的元素数量");
    }
}
