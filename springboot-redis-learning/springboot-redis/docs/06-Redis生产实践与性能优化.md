# Redis 生产实践与性能优化

> **学习目标**：掌握 Redis 在生产环境中的最佳实践、常见问题排查和性能优化技巧。

## 一、缓存设计模式

### 1.1 Cache Aside Pattern（旁路缓存）

```java
@Service
public class CacheAsideService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 读取数据
     */
    public User getUser(Long userId) {
        String key = "user:" + userId;
        
        // 1. 先查缓存
        User user = (User) redisTemplate.opsForValue().get(key);
        if (user != null) {
            return user;
        }
        
        // 2. 缓存未命中，查数据库
        user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        
        // 3. 写入缓存
        redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
        
        return user;
    }
    
    /**
     * 更新数据
     * 
     * 策略：先更新数据库，再删除缓存
     */
    public void updateUser(User user) {
        // 1. 更新数据库
        userMapper.updateById(user);
        
        // 2. 删除缓存（而不是更新缓存）
        String key = "user:" + user.getId();
        redisTemplate.delete(key);
        
        // 为什么删除而不是更新？
        // 1. 避免并发更新导致数据不一致
        // 2. 懒加载，下次读取时再缓存
    }
}
```

### 1.2 缓存穿透解决方案

**问题：查询不存在的数据，缓存和数据库都没有**

```java
@Service
public class CachePenetrationSolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 方案1：缓存空对象
     */
    public User getUserWithNullCache(Long userId) {
        String key = "user:" + userId;
        
        // 1. 查缓存
        if (redisTemplate.hasKey(key)) {
            User user = (User) redisTemplate.opsForValue().get(key);
            return user; // 可能是 null
        }
        
        // 2. 查数据库
        User user = userMapper.selectById(userId);
        
        // 3. 缓存结果（包括 null）
        if (user == null) {
            // 缓存空对象，设置较短过期时间
            redisTemplate.opsForValue().set(key, new User(), 5, TimeUnit.MINUTES);
        } else {
            redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
        }
        
        return user;
    }
    
    /**
     * 方案2：布隆过滤器
     */
    @Autowired
    private BloomFilter<Long> userIdBloomFilter;
    
    public User getUserWithBloomFilter(Long userId) {
        // 1. 布隆过滤器判断
        if (!userIdBloomFilter.mightContain(userId)) {
            // 一定不存在
            return null;
        }
        
        // 2. 可能存在，查缓存和数据库
        String key = "user:" + userId;
        User user = (User) redisTemplate.opsForValue().get(key);
        if (user != null) {
            return user;
        }
        
        user = userMapper.selectById(userId);
        if (user != null) {
            redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
        }
        
        return user;
    }
}
```

### 1.3 缓存击穿解决方案

**问题：热点 key 过期，大量请求打到数据库**

```java
@Service
public class CacheBreakdownSolution {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 方案1：互斥锁
     */
    public User getUserWithMutex(Long userId) {
        String key = "user:" + userId;
        String lockKey = "lock:user:" + userId;
        
        // 1. 查缓存
        User user = (User) redisTemplate.opsForValue().get(key);
        if (user != null) {
            return user;
        }
        
        // 2. 获取锁
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(10, TimeUnit.SECONDS)) {
                // 3. 双重检查
                user = (User) redisTemplate.opsForValue().get(key);
                if (user != null) {
                    return user;
                }
                
                // 4. 查数据库
                user = userMapper.selectById(userId);
                
                // 5. 写缓存
                if (user != null) {
                    redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
                }
                
                return user;
            }
        } finally {
            lock.unlock();
        }
        
        return null;
    }
    
    /**
     * 方案2：热点数据永不过期
     */
    public User getHotUser(Long userId) {
        String key = "hot:user:" + userId;
        
        // 1. 查缓存（不设置过期时间）
        User user = (User) redisTemplate.opsForValue().get(key);
        if (user != null) {
            return user;
        }
        
        // 2. 查数据库并缓存
        user = userMapper.selectById(userId);
        if (user != null) {
            // 不设置过期时间
            redisTemplate.opsForValue().set(key, user);
            
            // 通过定时任务异步更新
            scheduleRefresh(key, userId);
        }
        
        return user;
    }
}
```

### 1.4 缓存雪崩解决方案

**问题：大量 key 同时过期**

```java
@Service
public class CacheAvalancheSolution {
    
    /**
     * 方案1：过期时间加随机值
     */
    public void setWithRandomExpire(String key, Object value) {
        // 基础过期时间：1小时
        long baseExpire = 3600;
        
        // 随机增加 0-300 秒
        long randomExpire = ThreadLocalRandom.current().nextInt(300);
        
        redisTemplate.opsForValue().set(
            key,
            value,
            baseExpire + randomExpire,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * 方案2：多级缓存
     */
    @Autowired
    private Cache localCache; // 本地缓存（Caffeine/Guava）
    
    public User getUserWithMultiLevelCache(Long userId) {
        String key = "user:" + userId;
        
        // 1. 查本地缓存
        User user = localCache.get(key);
        if (user != null) {
            return user;
        }
        
        // 2. 查 Redis
        user = (User) redisTemplate.opsForValue().get(key);
        if (user != null) {
            localCache.put(key, user);
            return user;
        }
        
        // 3. 查数据库
        user = userMapper.selectById(userId);
        if (user != null) {
            redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
            localCache.put(key, user);
        }
        
        return user;
    }
}
```

---

## 二、分布式锁实现

### 2.1 基础实现

```java
@Component
public class RedisDistributedLock {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 获取锁
     */
    public boolean tryLock(String lockKey, String requestId, long expireTime) {
        // SET key value NX EX seconds
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
            lockKey,
            requestId,
            expireTime,
            TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(result);
    }
    
    /**
     * 释放锁（Lua 脚本保证原子性）
     */
    public boolean unlock(String lockKey, String requestId) {
        String script = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(lockKey),
            requestId
        );
        
        return Long.valueOf(1).equals(result);
    }
    
    /**
     * 使用示例
     */
    public void executeWithLock() {
        String lockKey = "lock:order:123";
        String requestId = UUID.randomUUID().toString();
        
        try {
            if (tryLock(lockKey, requestId, 30)) {
                // 执行业务逻辑
                processOrder();
            } else {
                throw new RuntimeException("获取锁失败");
            }
        } finally {
            unlock(lockKey, requestId);
        }
    }
}
```

### 2.2 Redisson 实现（推荐）

```java
@Service
public class RedissonLockService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 可重入锁
     */
    public void executeWithReentrantLock() {
        RLock lock = redissonClient.getLock("lock:order:123");
        
        try {
            // 尝试获取锁，最多等待 10 秒，锁自动释放时间 30 秒
            if (lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                // 执行业务逻辑
                processOrder();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 读写锁
     */
    public void executeWithReadWriteLock() {
        RReadWriteLock rwLock = redissonClient.getReadWriteLock("lock:data");
        
        // 读锁
        RLock readLock = rwLock.readLock();
        try {
            readLock.lock();
            // 读取数据
        } finally {
            readLock.unlock();
        }
        
        // 写锁
        RLock writeLock = rwLock.writeLock();
        try {
            writeLock.lock();
            // 写入数据
        } finally {
            writeLock.unlock();
        }
    }
}
```

---

## 三、性能优化实战

### 3.1 慢查询分析

```bash
# 配置慢查询
slowlog-log-slower-than 10000  # 10ms
slowlog-max-len 128            # 保留最近 128 条

# 查看慢查询
127.0.0.1:6379> SLOWLOG GET 10
1) 1) (integer) 1
   2) (integer) 1640000000
   3) (integer) 12000
   4) 1) "KEYS"
      2) "user:*"
```

**Java 监控：**

```java
@Component
public class SlowLogMonitor {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Scheduled(fixedRate = 60000)
    public void monitorSlowLog() {
        List<Object> slowLogs = redisTemplate.execute(
            (RedisCallback<List<Object>>) connection -> 
                connection.slowlogGet(10)
        );
        
        if (slowLogs != null && !slowLogs.isEmpty()) {
            log.warn("发现慢查询: {}", slowLogs);
            // 发送告警
        }
    }
}
```

### 3.2 大 Key 优化

```java
@Service
public class BigKeyOptimization {
    
    /**
     * ❌ 问题：单个 Hash 存储所有用户
     */
    public void badPractice() {
        // Key: users
        // Field: userId
        // 问题：100万用户 = 1个超大 Hash
        redisTemplate.opsForHash().put("users", "1", user1);
        redisTemplate.opsForHash().put("users", "2", user2);
        // ...
    }
    
    /**
     * ✅ 优化：分片存储
     */
    public void goodPractice(Long userId, User user) {
        // 按用户ID分片（取模）
        int shardId = (int) (userId % 100);
        String key = "users:shard:" + shardId;
        
        redisTemplate.opsForHash().put(key, userId.toString(), user);
        
        // 优势：
        // - 100万用户分成 100 个 Hash
        // - 每个 Hash 约 1万用户
        // - 操作更快，影响范围小
    }
}
```

### 3.3 批量操作优化

```java
@Service
public class BatchOptimization {
    
    /**
     * ❌ 逐个操作（慢）
     */
    public void slowBatchSet(Map<String, String> data) {
        for (Map.Entry<String, String> entry : data.entrySet()) {
            redisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
        }
        // 1000 条数据：约 500ms
    }
    
    /**
     * ✅ Pipeline 批量操作（快）
     */
    public void fastBatchSet(Map<String, String> data) {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    connection.set(
                        entry.getKey().getBytes(),
                        entry.getValue().getBytes()
                    );
                }
                return null;
            }
        });
        // 1000 条数据：约 50ms
    }
    
    /**
     * ✅ MGET/MSET（最快）
     */
    public void fastestBatchSet(Map<String, String> data) {
        redisTemplate.opsForValue().multiSet(data);
        // 1000 条数据：约 10ms
    }
}
```

---

## 四、监控与告警

### 4.1 关键指标

```java
@Component
public class RedisMetricsCollector {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Scheduled(fixedRate = 60000)
    public void collectMetrics() {
        Properties info = redisTemplate.execute(
            (RedisCallback<Properties>) connection -> connection.info()
        );
        
        if (info == null) {
            return;
        }
        
        // 1. 内存使用率
        long usedMemory = Long.parseLong(info.getProperty("used_memory"));
        long maxMemory = Long.parseLong(info.getProperty("maxmemory"));
        double memoryUsage = (double) usedMemory / maxMemory * 100;
        
        // 2. 命中率
        long hits = Long.parseLong(info.getProperty("keyspace_hits"));
        long misses = Long.parseLong(info.getProperty("keyspace_misses"));
        double hitRate = (double) hits / (hits + misses) * 100;
        
        // 3. 连接数
        int connectedClients = Integer.parseInt(info.getProperty("connected_clients"));
        
        // 4. OPS
        long totalCommands = Long.parseLong(info.getProperty("total_commands_processed"));
        
        // 5. 慢查询数
        long slowlogLen = Long.parseLong(info.getProperty("slowlog_len"));
        
        // 发送到监控系统
        sendToMonitoring(memoryUsage, hitRate, connectedClients, slowlogLen);
    }
}
```

### 4.2 告警规则

```java
@Component
public class RedisAlertRules {
    
    /**
     * 告警规则
     */
    public void checkAlerts(RedisMetrics metrics) {
        // 1. 内存使用率 > 80%
        if (metrics.getMemoryUsage() > 80) {
            alert("Redis 内存使用率过高: " + metrics.getMemoryUsage() + "%");
        }
        
        // 2. 缓存命中率 < 90%
        if (metrics.getHitRate() < 90) {
            alert("Redis 缓存命中率过低: " + metrics.getHitRate() + "%");
        }
        
        // 3. 连接数 > 1000
        if (metrics.getConnectedClients() > 1000) {
            alert("Redis 连接数过多: " + metrics.getConnectedClients());
        }
        
        // 4. 慢查询数 > 10
        if (metrics.getSlowlogCount() > 10) {
            alert("Redis 慢查询过多: " + metrics.getSlowlogCount());
        }
    }
}
```

---

## 五、生产环境检查清单

### 5.1 配置检查

```bash
# 1. 内存配置
maxmemory 4gb
maxmemory-policy allkeys-lru

# 2. 持久化配置
appendonly yes
appendfsync everysec
aof-use-rdb-preamble yes

# 3. 慢查询配置
slowlog-log-slower-than 10000
slowlog-max-len 128

# 4. 客户端配置
timeout 300
tcp-keepalive 60

# 5. 安全配置
requirepass your_password
rename-command FLUSHALL ""
rename-command FLUSHDB ""
rename-command CONFIG ""
```

### 5.2 性能优化清单

```
✅ Key 设计
- 使用简短有意义的 key
- 使用冒号分隔层级
- 避免过长的 key

✅ Value 优化
- 选择合适的数据结构
- 控制集合大小（< 10000）
- 压缩大对象

✅ 过期时间
- 所有 key 设置过期时间
- 加随机值避免雪崩
- 热点数据特殊处理

✅ 批量操作
- 使用 Pipeline
- 使用 MGET/MSET
- 避免大量单个操作

✅ 监控告警
- 内存使用率
- 缓存命中率
- 慢查询
- 连接数
```

---

## 六、面试重点

**Q1：如何解决缓存穿透、击穿、雪崩？**

| 问题 | 解决方案 |
|------|----------|
| 穿透 | 布隆过滤器、缓存空对象 |
| 击穿 | 互斥锁、热点数据永不过期 |
| 雪崩 | 过期时间加随机值、多级缓存 |

**Q2：分布式锁的实现要点？**

**A：** 
1. 原子性：SET NX EX
2. 唯一性：requestId 标识
3. 释放锁：Lua 脚本保证原子性
4. 超时时间：防止死锁
5. 推荐：使用 Redisson

**Q3：如何优化 Redis 性能？**

**A：** 
1. 合理的数据结构
2. 批量操作（Pipeline）
3. 避免大 Key
4. 设置合理的过期时间
5. 监控慢查询

---

**总结**：Redis 在生产环境中需要关注缓存设计、性能优化、监控告警三个方面，遵循最佳实践可以避免大部分问题。
