# Redis 生产问题全景图与解决方案

> **学习目标**：通过电商秒杀场景，系统掌握 Redis 在生产环境中的常见问题和解决方案。

## 一、问题全景图

根据 Redis 生产问题画像，问题主要分为以下几类：

```
Redis 生产问题分类
│
├── 主从库问题
│   ├── 数据丢失
│   └── 主从不一致
│
├── 性能问题
│   ├── 阻塞
│   ├── 抖动
│   └── BigKey
│
├── 持久化问题
│   ├── AOF 写满
│   ├── RDB
│   ├── AOF
│   └── 调兵机制
│
├── 内存问题
│   ├── 占用倾斜
│   ├── 数据结构
│   ├── 异步机制
│   └── 数据分布
│
├── 缓存问题
│   ├── 污染
│   ├── 雪崩
│   └── 穿透
│
└── 切片集群问题
    ├── 数据热点
    └── 秒杀
```

---

## 二、场景背景：电商秒杀系统

### 2.1 业务场景

某电商平台推出 iPhone 15 Pro Max 秒杀活动：

- **商品数量**：100 台
- **参与用户**：10 万人
- **秒杀时间**：10:00:00 开始
- **技术要求**：
  - 不能超卖
  - 高并发处理（10 万 QPS）
  - 响应时间 < 100ms
  - 系统稳定性 99.99%

### 2.2 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                    秒杀系统架构                          │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐      │
│  │  用户端  │ ───→ │  Nginx   │ ───→ │ 应用服务 │      │
│  └──────────┘      └──────────┘      └──────────┘      │
│                                             │            │
│                                             ↓            │
│                    ┌─────────────────────────────┐      │
│                    │      Redis Cluster          │      │
│                    │  (3主3从 + 哨兵)            │      │
│                    └─────────────────────────────┘      │
│                                             │            │
│                                             ↓            │
│                    ┌─────────────────────────────┐      │
│                    │         MySQL               │      │
│                    │    (订单、商品信息)          │      │
│                    └─────────────────────────────┘      │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

---

## 三、问题演示与解决方案

### 3.1 缓存穿透

**问题场景**：恶意用户不断查询不存在的商品 ID

**代码位置**：
- 问题代码：`com.huabin.redis.problem.cache.CachePenetrationProblem`
- 解决方案：`com.huabin.redis.solution.cache.CachePenetrationSolution`

**问题代码**：

```java
public Product getProduct_Problem(Long productId) {
    String cacheKey = "product:" + productId;
    
    // 1. 查缓存
    Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
    if (product != null) {
        return product;
    }
    
    // 2. 缓存未命中，查数据库
    product = queryFromDatabase(productId);
    
    // 问题：如果数据库也没有，直接返回null
    // 下次相同请求还会再次查询数据库
    return product;
}
```

**问题分析**：
- 查询不存在的商品 ID（如 -1, -999）
- 缓存中没有
- 数据库中也没有
- 每次请求都穿透到数据库
- 大量恶意请求会压垮数据库

**解决方案 1：布隆过滤器**

```java
// 初始化布隆过滤器
private BloomFilter<Long> productBloomFilter = BloomFilter.create(
    Funnels.longFunnel(),
    1000,
    0.01
);

@PostConstruct
public void initBloomFilter() {
    // 将所有存在的商品ID加入布隆过滤器
    for (long i = 1; i <= 1000; i++) {
        productBloomFilter.put(i);
    }
}

public Product getProduct_BloomFilter(Long productId) {
    // 1. 布隆过滤器判断
    if (!productBloomFilter.mightContain(productId)) {
        // 一定不存在，直接返回
        return null;
    }
    
    // 2. 可能存在，查缓存和数据库
    // ...
}
```

**解决方案 2：缓存空对象**

```java
public Product getProduct_CacheNull(Long productId) {
    String cacheKey = "product:" + productId;
    
    // 1. 查缓存（包括空对象）
    if (redisTemplate.hasKey(cacheKey)) {
        Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null && product.getId() != null) {
            return product;
        } else {
            // 缓存的是空对象
            return null;
        }
    }
    
    // 2. 查数据库
    Product product = queryFromDatabase(productId);
    
    // 3. 写入缓存
    if (product != null) {
        redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
    } else {
        // 缓存空对象，设置较短过期时间
        Product emptyProduct = new Product();
        redisTemplate.opsForValue().set(cacheKey, emptyProduct, 5, TimeUnit.MINUTES);
    }
    
    return product;
}
```

**效果对比**：

| 方案 | 第一次请求 | 后续请求 | 内存占用 | 推荐度 |
|------|-----------|---------|---------|--------|
| 无防护 | 查数据库 | 查数据库 | 低 | ❌ |
| 缓存空对象 | 查数据库 | 命中缓存 | 中 | ⭐⭐⭐ |
| 布隆过滤器 | 直接拦截 | 直接拦截 | 低 | ⭐⭐⭐⭐⭐ |
| 组合方案 | 直接拦截 | 直接拦截 | 中 | ⭐⭐⭐⭐⭐ |

---

### 3.2 缓存击穿

**问题场景**：热点商品缓存过期，大量请求同时打到数据库

**代码位置**：
- 问题代码：`com.huabin.redis.problem.cache.CacheBreakdownProblem`
- 解决方案：`com.huabin.redis.solution.cache.CacheBreakdownSolution`

**问题代码**：

```java
public Product getHotProduct_Problem(Long productId) {
    String cacheKey = "hot:product:" + productId;
    
    // 1. 查缓存
    Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
    if (product != null) {
        return product;
    }
    
    // 2. 缓存未命中，查数据库
    // 问题：多个线程同时执行到这里，都会查询数据库
    product = queryHotProductFromDatabase(productId);
    
    // 3. 写入缓存
    if (product != null) {
        redisTemplate.opsForValue().set(cacheKey, product, 10, TimeUnit.SECONDS);
    }
    
    return product;
}
```

**问题分析**：
- 热点商品缓存过期
- 1000 个并发请求同时发现缓存不存在
- 所有请求都去查询数据库
- 数据库瞬间压力巨大

**解决方案 1：互斥锁**

```java
private final Lock lock = new ReentrantLock();

public Product getHotProduct_Mutex(Long productId) {
    String cacheKey = "hot:product:" + productId;
    
    // 1. 查缓存
    Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
    if (product != null) {
        return product;
    }
    
    // 2. 获取锁
    lock.lock();
    try {
        // 3. 双重检查
        product = (Product) redisTemplate.opsForValue().get(cacheKey);
        if (product != null) {
            return product;
        }
        
        // 4. 查询数据库
        product = queryHotProductFromDatabase(productId);
        
        // 5. 写入缓存
        if (product != null) {
            redisTemplate.opsForValue().set(cacheKey, product, 30, TimeUnit.SECONDS);
        }
        
        return product;
    } finally {
        lock.unlock();
    }
}
```

**解决方案 2：热点数据永不过期**

```java
public Product getHotProduct_NeverExpire(Long productId) {
    String cacheKey = "hot:product:never:" + productId;
    
    // 1. 查缓存（永不过期）
    Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
    if (product != null) {
        return product;
    }
    
    // 2. 首次访问，查询数据库
    product = queryHotProductFromDatabase(productId);
    
    // 3. 写入缓存（不设置过期时间）
    if (product != null) {
        redisTemplate.opsForValue().set(cacheKey, product);
        
        // 启动异步线程定期更新缓存
        startAsyncRefresh(productId);
    }
    
    return product;
}
```

**效果对比**：

| 方案 | 数据库查询次数 | 响应时间 | 复杂度 | 推荐度 |
|------|---------------|---------|--------|--------|
| 无防护 | 1000 次 | 慢 | 低 | ❌ |
| 互斥锁 | 1 次 | 中 | 中 | ⭐⭐⭐⭐ |
| 永不过期 | 0 次 | 快 | 高 | ⭐⭐⭐⭐⭐ |

---

### 3.3 缓存雪崩

**问题场景**：大量商品缓存同时过期

**代码位置**：
- 问题代码：`com.huabin.redis.problem.cache.CacheAvalancheProblem`
- 解决方案：`com.huabin.redis.solution.cache.CacheAvalancheSolution`

**问题代码**：

```java
public void cacheProducts_Problem(List<Product> products) {
    // 问题：所有商品都设置相同的过期时间（10秒）
    for (Product product : products) {
        String cacheKey = "product:" + product.getId();
        redisTemplate.opsForValue().set(cacheKey, product, 10, TimeUnit.SECONDS);
    }
}
```

**问题分析**：
- 100 个商品缓存同时过期
- 大量请求同时访问这些商品
- 所有请求都打到数据库
- 数据库瞬间压力巨大

**解决方案：过期时间加随机值**

```java
public void cacheProducts_RandomExpire(List<Product> products) {
    int baseExpireSeconds = 3600; // 基础过期时间：1小时
    
    for (Product product : products) {
        String cacheKey = "product:random:" + product.getId();
        
        // 加上随机值：0-300秒（5分钟）
        int randomSeconds = ThreadLocalRandom.current().nextInt(300);
        int expireSeconds = baseExpireSeconds + randomSeconds;
        
        redisTemplate.opsForValue().set(cacheKey, product, expireSeconds, TimeUnit.SECONDS);
    }
}
```

**效果对比**：

| 方案 | 过期时间 | 雪崩风险 | 推荐度 |
|------|---------|---------|--------|
| 相同过期时间 | 3600秒 | 高 | ❌ |
| 加随机值 | 3600 + (0~300)秒 | 低 | ⭐⭐⭐⭐⭐ |

---

### 3.4 BigKey 问题

**问题场景**：单个 Hash 存储 100 万用户信息

**代码位置**：
- 问题代码：`com.huabin.redis.problem.performance.BigKeyProblem`
- 解决方案：`com.huabin.redis.solution.performance.BigKeySolution`

**问题代码**：

```java
public void createBigHash_Problem() {
    String bigHashKey = "users:all";
    
    // 问题：100万用户存储在一个Hash中
    for (int i = 1; i <= 1000000; i++) {
        String userId = String.valueOf(i);
        String userInfo = "user" + i + "@example.com";
        redisTemplate.opsForHash().put(bigHashKey, userId, userInfo);
    }
    
    // 问题：HGETALL 操作会阻塞Redis
    Map<Object, Object> allUsers = redisTemplate.opsForHash().entries(bigHashKey);
}
```

**问题分析**：
- 单个 Hash 包含 100 万个字段
- HGETALL 操作会阻塞 Redis
- 删除操作耗时长
- 内存占用大

**解决方案：拆分 BigHash**

```java
public void splitBigHash() {
    int userCount = 1000000;
    int shardCount = 100; // 拆分成100个分片
    
    for (int i = 1; i <= userCount; i++) {
        // 计算分片ID（取模）
        int shardId = i % shardCount;
        String shardKey = "users:shard:" + shardId;
        
        String userId = String.valueOf(i);
        String userInfo = "user" + i + "@example.com";
        
        redisTemplate.opsForHash().put(shardKey, userId, userInfo);
    }
    
    // 优势：每个分片只有1万用户，操作快速
}
```

**效果对比**：

| 方案 | Hash 数量 | 每个 Hash 大小 | HGETALL 耗时 | 推荐度 |
|------|----------|---------------|-------------|--------|
| 单个大 Hash | 1 个 | 100 万字段 | 数秒 | ❌ |
| 拆分 Hash | 100 个 | 1 万字段 | 毫秒级 | ⭐⭐⭐⭐⭐ |

---

### 3.5 热点 Key 问题

**问题场景**：秒杀商品被 10 万用户同时访问

**代码位置**：
- 问题代码：`com.huabin.redis.problem.cluster.HotKeyProblem`
- 解决方案：`com.huabin.redis.solution.cluster.HotKeySolution`

**问题分析**：
- 在 Redis Cluster 中，Key 通过 CRC16(key) % 16384 计算 slot
- 热点 Key 只在一个节点上
- 所有请求都打到这一个节点
- 该节点 CPU、网络 IO 压力巨大
- 其他节点空闲

**解决方案 1：本地缓存**

```java
// 使用 Caffeine 本地缓存
private Cache<String, Object> localCache = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(60, TimeUnit.SECONDS)
    .build();

public Product getHotProduct_LocalCache(Long productId) {
    String cacheKey = "hot:product:" + productId;
    
    // 1. 先查本地缓存
    Product product = localCache.get(cacheKey);
    if (product != null) {
        return product;
    }
    
    // 2. 查 Redis
    product = (Product) redisTemplate.opsForValue().get(cacheKey);
    if (product != null) {
        localCache.put(cacheKey, product);
        return product;
    }
    
    // 3. 查数据库
    product = queryFromDatabase(productId);
    if (product != null) {
        redisTemplate.opsForValue().set(cacheKey, product, 1, TimeUnit.HOURS);
        localCache.put(cacheKey, product);
    }
    
    return product;
}
```

**解决方案 2：热点 Key 复制**

```java
public Product getHotProduct_Replicate(Long productId) {
    // 复制10份
    int replicaCount = 10;
    
    // 随机选择一个副本
    int replicaId = ThreadLocalRandom.current().nextInt(replicaCount);
    String cacheKey = "hot:product:" + productId + ":replica:" + replicaId;
    
    // 查询副本
    Product product = (Product) redisTemplate.opsForValue().get(cacheKey);
    return product;
}
```

**效果对比**：

| 方案 | Redis 访问次数 | 单节点压力 | 推荐度 |
|------|---------------|-----------|--------|
| 无优化 | 10 万次 | 极高 | ❌ |
| 本地缓存 | 1 次 | 低 | ⭐⭐⭐⭐⭐ |
| Key 复制 | 1 万次/节点 | 中 | ⭐⭐⭐⭐ |

---

## 四、完整秒杀场景

**代码位置**：`com.huabin.redis.scenario.SeckillScenario`

### 4.1 秒杀流程

```
用户请求
  ↓
布隆过滤器判断（防穿透）
  ↓
查询商品信息（热点Key复制）
  ↓
Lua脚本扣减库存（原子操作）
  ↓
异步创建订单
  ↓
返回结果
```

### 4.2 核心代码

```java
public String seckill_Optimized(Long userId, Long productId) {
    // 1. 布隆过滤器判断（防止缓存穿透）
    if (!productBloomFilter.mightContain(productId)) {
        return "商品不存在";
    }
    
    // 2. 查询商品信息（使用热点Key复制）
    Product product = getProductWithHotKeyReplica(productId);
    if (product == null) {
        return "商品不存在";
    }
    
    // 3. 使用Lua脚本原子扣减库存
    String luaScript = 
        "-- 检查用户是否已经秒杀过\n" +
        "if redis.call('exists', KEYS[2]) == 1 then\n" +
        "    return -2\n" +
        "end\n" +
        "-- 获取库存\n" +
        "local stock = tonumber(redis.call('get', KEYS[1]))\n" +
        "if stock == nil or stock <= 0 then\n" +
        "    return -1\n" +
        "end\n" +
        "-- 扣减库存\n" +
        "redis.call('decr', KEYS[1])\n" +
        "-- 标记用户已秒杀\n" +
        "redis.call('setex', KEYS[2], 86400, '1')\n" +
        "return stock - 1";
    
    Long result = redisTemplate.execute(
        new DefaultRedisScript<>(luaScript, Long.class),
        Arrays.asList(stockKey, userKey)
    );
    
    if (result == null || result == -1) {
        return "库存不足";
    } else if (result == -2) {
        return "您已经参与过该商品的秒杀";
    }
    
    // 4. 异步创建订单
    createOrderAsync(userId, productId);
    
    return "秒杀成功，剩余库存：" + result;
}
```

### 4.3 压测结果

**问题版本**：
```
并发用户数: 1000
初始库存: 100
最终库存: -50（超卖！）
耗时: 5000ms
数据库查询次数: 800
```

**优化版本**：
```
并发用户数: 1000
初始库存: 100
最终库存: 0
成功秒杀: 100
失败次数: 900
耗时: 500ms
数据库查询次数: 1
```

---

## 五、运行演示

### 5.1 启动项目

```bash
cd springboot-redis-learning/springboot-redis
mvn spring-boot:run
```

### 5.2 访问演示接口

浏览器访问：`http://localhost:8080/redis/demo/`

可用接口：

1. **缓存穿透**：`/redis/demo/cache/penetration/problem`
2. **缓存击穿**：`/redis/demo/cache/breakdown/problem`
3. **缓存雪崩**：`/redis/demo/cache/avalanche/problem`
4. **BigKey**：`/redis/demo/performance/bigkey/problem`
5. **热点Key**：`/redis/demo/cluster/hotkey/problem`
6. **完整秒杀**：`/redis/demo/scenario/seckill`

---

## 六、问题总结

### 6.1 问题分类与解决方案

| 问题类型 | 具体问题 | 解决方案 | 代码位置 |
|---------|---------|---------|---------|
| **缓存问题** | 穿透 | 布隆过滤器 + 缓存空对象 | `CachePenetrationSolution` |
| | 击穿 | 互斥锁 + 永不过期 | `CacheBreakdownSolution` |
| | 雪崩 | 随机过期时间 + 多级缓存 | `CacheAvalancheSolution` |
| **性能问题** | BigKey | 拆分 + SCAN | `BigKeySolution` |
| | 阻塞 | SCAN 代替 KEYS + 分批 Pipeline | `BlockingSolution` |
| **内存问题** | 泄漏 | 设置过期时间 + 限制集合大小 | `MemoryOptimization` |
| **集群问题** | 热点Key | 本地缓存 + Key复制 | `HotKeySolution` |

### 6.2 最佳实践

1. **缓存设计**：
   - ✅ 所有 Key 设置过期时间
   - ✅ 加随机值避免雪崩
   - ✅ 使用布隆过滤器防穿透
   - ✅ 热点数据使用互斥锁或永不过期

2. **性能优化**：
   - ✅ 避免 BigKey（拆分）
   - ✅ 使用 SCAN 代替 KEYS
   - ✅ Pipeline 分批执行
   - ✅ 使用 Lua 脚本保证原子性

3. **内存管理**：
   - ✅ 设置 maxmemory
   - ✅ 配置淘汰策略（allkeys-lru）
   - ✅ 限制集合大小
   - ✅ 定期清理过期 Key

4. **集群优化**：
   - ✅ 热点 Key 使用本地缓存
   - ✅ Key 复制分散压力
   - ✅ 监控热点 Key

---

## 七、监控与告警

### 7.1 关键指标

```bash
# 内存使用率
used_memory / maxmemory > 80%

# 缓存命中率
keyspace_hits / (keyspace_hits + keyspace_misses) < 90%

# 慢查询
slowlog_len > 10

# 连接数
connected_clients > 1000

# 内存碎片率
mem_fragmentation_ratio > 1.5
```

### 7.2 告警规则

| 指标 | 阈值 | 级别 | 处理方案 |
|------|------|------|---------|
| 内存使用率 | > 80% | 警告 | 扩容或清理 |
| 缓存命中率 | < 90% | 警告 | 优化缓存策略 |
| 慢查询数 | > 10 | 严重 | 优化慢查询 |
| 连接数 | > 1000 | 警告 | 检查连接泄漏 |
| 内存碎片率 | > 1.5 | 警告 | 碎片整理 |

---

**总结**：通过秒杀场景，我们系统地演示了 Redis 在生产环境中的常见问题和解决方案。掌握这些知识，可以有效应对 99% 的 Redis 生产问题。
