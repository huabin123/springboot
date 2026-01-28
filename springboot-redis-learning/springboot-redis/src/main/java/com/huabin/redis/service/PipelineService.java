package com.huabin.redis.service;

import com.alibaba.fastjson.JSON;
import com.huabin.redis.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Pipeline 批量操作示例
 * 
 * @author huabin
 * @description 演示 Pipeline 的基本用法和性能对比
 */
@Service
public class PipelineService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * ❌ 方式一：不使用 Pipeline（慢）
     * 
     * 问题：每个命令都需要一次网络往返
     * 耗时：RTT × N
     */
    public long setWithoutPipeline(Map<String, String> data) {
        long start = System.currentTimeMillis();
        
        for (Map.Entry<String, String> entry : data.entrySet()) {
            redisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
        }
        
        long cost = System.currentTimeMillis() - start;
        System.out.println("不使用 Pipeline: " + cost + "ms");
        return cost;
    }
    
    /**
     * ✅ 方式二：使用 Pipeline（快）
     * 
     * 优势：批量发送命令，只需一次网络往返
     * 耗时：1 × RTT + 执行时间
     */
    public long setWithPipeline(Map<String, String> data) {
        long start = System.currentTimeMillis();
        
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    connection.set(
                        entry.getKey().getBytes(),
                        entry.getValue().getBytes()
                    );
                }
                return null;
            }
        });
        
        long cost = System.currentTimeMillis() - start;
        System.out.println("使用 Pipeline: " + cost + "ms");
        return cost;
    }
    
    /**
     * 性能对比测试
     */
    public Map<String, Object> performanceComparison(int dataSize) {
        // 准备测试数据
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < dataSize; i++) {
            testData.put("test:key:" + i, "value:" + i);
        }
        
        // 测试不使用 Pipeline
        long withoutPipeline = setWithoutPipeline(testData);
        
        // 清空数据
        redisTemplate.delete(testData.keySet());
        
        // 测试使用 Pipeline
        long withPipeline = setWithPipeline(testData);
        
        // 清空测试数据
        redisTemplate.delete(testData.keySet());
        
        // 性能提升倍数
        double improvement = (double) withoutPipeline / withPipeline;
        
        Map<String, Object> result = new HashMap<>();
        result.put("dataSize", dataSize);
        result.put("withoutPipeline", withoutPipeline + "ms");
        result.put("withPipeline", withPipeline + "ms");
        result.put("improvement", String.format("%.2f", improvement) + " 倍");
        
        return result;
    }
    
    /**
     * 批量写入用户数据
     */
    public void batchInsertUsers(List<User> users) {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (User user : users) {
                    String key = "user:" + user.getId();
                    String value = JSON.toJSONString(user);
                    connection.set(key.getBytes(), value.getBytes());
                    
                    // 设置过期时间（1小时）
                    connection.expire(key.getBytes(), 3600);
                }
                return null;
            }
        });
    }
    
    /**
     * 批量读取用户数据
     */
    public List<User> batchGetUsers(List<Long> userIds) {
        List<Object> results = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long userId : userIds) {
                    String key = "user:" + userId;
                    connection.get(key.getBytes());
                }
                return null;
            }
        });
        
        // 解析结果
        List<User> users = new ArrayList<>();
        for (Object result : results) {
            if (result != null) {
                User user = JSON.parseObject(new String((byte[]) result), User.class);
                users.add(user);
            }
        }
        
        return users;
    }
    
    /**
     * 批量删除过期数据
     */
    public void batchDeleteExpiredKeys(List<String> keys) {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (String key : keys) {
                    connection.del(key.getBytes());
                }
                return null;
            }
        });
    }
    
    /**
     * 批量增加商品浏览量
     */
    public void batchIncrementViewCount(List<Long> productIds) {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long productId : productIds) {
                    String key = "product:view:" + productId;
                    connection.incr(key.getBytes());
                }
                return null;
            }
        });
    }
    
    /**
     * 混合操作：同时执行 SET、INCR、EXPIRE
     */
    public void mixedOperations(String userId, String sessionId) {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                // 1. 保存 Session
                String sessionKey = "session:" + sessionId;
                connection.set(sessionKey.getBytes(), userId.getBytes());
                connection.expire(sessionKey.getBytes(), 1800); // 30分钟
                
                // 2. 增加登录次数
                String loginCountKey = "user:login:count:" + userId;
                connection.incr(loginCountKey.getBytes());
                
                // 3. 记录最后登录时间
                String lastLoginKey = "user:last:login:" + userId;
                connection.set(lastLoginKey.getBytes(), String.valueOf(System.currentTimeMillis()).getBytes());
                
                return null;
            }
        });
    }
    
    /**
     * ✅ 推荐：分批执行 Pipeline
     * 
     * 原因：
     * 1. 避免单次 Pipeline 命令过多，占用过多内存
     * 2. 避免阻塞 Redis 服务器时间过长
     * 3. 建议单次 Pipeline 不超过 1000 个命令
     */
    public void batchPipelineWithLimit(List<String> keys, int batchSize) {
        for (int i = 0; i < keys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keys.size());
            List<String> batch = keys.subList(i, end);
            
            redisTemplate.executePipelined(new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    for (String key : batch) {
                        connection.get(key.getBytes());
                    }
                    return null;
                }
            });
        }
    }
}
