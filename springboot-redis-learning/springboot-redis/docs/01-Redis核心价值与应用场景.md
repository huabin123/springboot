# Redis 核心价值与应用场景

> **学习目标**：理解 Redis 解决了什么问题，为什么需要 Redis，以及它在实际业务中的应用场景。

## 一、没有 Redis 之前：性能瓶颈分析

### 1.1 传统架构的痛点

假设我们有一个电商系统，使用传统的 MySQL 架构：

```
用户请求 → 应用服务器 → MySQL 数据库 → 返回结果
```

**场景 1：商品详情页查询**

```java
// 传统方式：每次请求都查询数据库
@GetMapping("/product/{id}")
public Product getProduct(@PathVariable Long id) {
    // 每次都查询 MySQL
    Product product = productMapper.selectById(id);
    // 查询库存
    Stock stock = stockMapper.selectByProductId(id);
    // 查询评价数
    int reviewCount = reviewMapper.countByProductId(id);
    
    product.setStock(stock.getQuantity());
    product.setReviewCount(reviewCount);
    return product;
}
```

**问题分析：**

| 问题 | 影响 | 数据 |
|------|------|------|
| **数据库连接数有限** | 高并发时连接池耗尽 | MySQL 默认 151 个连接 |
| **磁盘 IO 慢** | 查询延迟高 | 磁盘 IOPS：200-500，内存：10万+ |
| **重复查询** | 资源浪费 | 热门商品被查询数万次 |
| **复杂查询慢** | 用户体验差 | 多表 JOIN 可能需要 100ms+ |

**实际数据对比：**

```
MySQL 查询延迟：
- 简单主键查询：1-5ms
- 带索引查询：5-20ms
- 多表 JOIN：20-100ms
- 无索引全表扫描：100ms-数秒

Redis 查询延迟：
- 简单 GET：0.1-1ms
- 复杂操作：1-5ms
```

### 1.2 高并发场景下的崩溃

**双十一场景模拟：**

```java
// 秒杀商品详情页
// 假设：100万用户同时访问同一个商品详情页
// MySQL 配置：最大连接数 1000

@GetMapping("/seckill/product/{id}")
public SeckillProduct getProduct(@PathVariable Long id) {
    // 问题1：数据库连接数不足
    // 1000个连接 vs 100万请求 = 99.9%的请求等待或失败
    
    // 问题2：磁盘IO成为瓶颈
    // MySQL 磁盘 IOPS：500次/秒
    // 实际需求：100万次/秒
    // 结果：数据库响应时间从5ms暴增到数秒
    
    // 问题3：CPU负载过高
    // 大量相同查询消耗CPU资源
    
    return productMapper.selectById(id);
}
```

**崩溃过程：**

```
1. 请求量暴增 → 数据库连接池耗尽
2. 大量请求等待 → 应用服务器线程池耗尽
3. 新请求无法处理 → 用户看到 504 Gateway Timeout
4. 数据库压力持续 → 慢查询堆积
5. 系统完全不可用 → 雪崩效应
```

---

## 二、Redis 的核心价值

### 2.1 与 MySQL 的本质区别

| 维度 | MySQL | Redis | 设计取舍 |
|------|-------|-------|----------|
| **存储介质** | 磁盘（持久化优先） | 内存（性能优先） | Redis 牺牲容量换取速度 |
| **数据结构** | 表（二维结构） | K-V + 丰富数据类型 | Redis 更灵活，适合缓存 |
| **查询方式** | SQL（复杂查询） | Key-Value（简单快速） | Redis 不支持复杂查询 |
| **事务支持** | ACID（强一致性） | 简单事务（最终一致） | Redis 弱化事务换取性能 |
| **并发模型** | 多线程 | 单线程 + IO多路复用 | Redis 避免锁竞争 |
| **适用场景** | 持久化存储、复杂查询 | 缓存、计数、排行榜 | 各有侧重 |

### 2.2 Redis 的三大核心优势

#### 优势 1：极致的性能

```java
// 性能对比实测
public class PerformanceTest {
    
    @Test
    public void testMySQLvsRedis() {
        // MySQL 查询：平均 5ms
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            productMapper.selectById(1L);
        }
        long mysqlTime = System.currentTimeMillis() - start;
        // 结果：约 50,000ms (50秒)
        
        // Redis 查询：平均 0.5ms
        start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            redisTemplate.opsForValue().get("product:1");
        }
        long redisTime = System.currentTimeMillis() - start;
        // 结果：约 5,000ms (5秒)
        
        // Redis 比 MySQL 快 10 倍
    }
}
```

**为什么这么快？**

1. **内存访问**：内存访问速度是磁盘的 10 万倍
2. **单线程模型**：避免线程切换和锁竞争
3. **高效数据结构**：针对性能优化的底层实现
4. **IO 多路复用**：单线程处理海量并发

#### 优势 2：丰富的数据结构

```java
// Redis 提供的数据结构解决不同场景
public class RedisDataStructures {
    
    // 1. String：缓存对象
    public void cacheProduct(Product product) {
        redisTemplate.opsForValue().set(
            "product:" + product.getId(),
            JSON.toJSONString(product),
            1, TimeUnit.HOURS
        );
    }
    
    // 2. Hash：存储对象字段
    public void cacheProductFields(Product product) {
        Map<String, String> fields = new HashMap<>();
        fields.put("name", product.getName());
        fields.put("price", String.valueOf(product.getPrice()));
        fields.put("stock", String.valueOf(product.getStock()));
        
        redisTemplate.opsForHash().putAll("product:hash:" + product.getId(), fields);
        // 优势：可以单独更新某个字段，节省网络传输
    }
    
    // 3. List：消息队列、最新列表
    public void addToRecentViews(Long userId, Long productId) {
        String key = "user:recent:views:" + userId;
        redisTemplate.opsForList().leftPush(key, productId.toString());
        redisTemplate.opsForList().trim(key, 0, 9); // 只保留最近10个
    }
    
    // 4. Set：去重、共同关注
    public Set<String> getCommonFollows(Long userId1, Long userId2) {
        String key1 = "user:follows:" + userId1;
        String key2 = "user:follows:" + userId2;
        return redisTemplate.opsForSet().intersect(key1, key2);
    }
    
    // 5. ZSet：排行榜
    public void updateProductScore(Long productId, double score) {
        redisTemplate.opsForZSet().add("product:ranking", productId.toString(), score);
    }
    
    public Set<String> getTopProducts(int count) {
        return redisTemplate.opsForZSet().reverseRange("product:ranking", 0, count - 1);
    }
}
```

#### 优势 3：原子操作支持

```java
// 解决并发问题
public class AtomicOperations {
    
    // 场景：秒杀库存扣减
    public boolean deductStock(Long productId, int quantity) {
        String key = "product:stock:" + productId;
        
        // 原子操作：不会出现超卖
        Long remaining = redisTemplate.opsForValue().decrement(key, quantity);
        
        if (remaining != null && remaining >= 0) {
            return true; // 扣减成功
        } else {
            // 库存不足，回滚
            redisTemplate.opsForValue().increment(key, quantity);
            return false;
        }
    }
    
    // 场景：分布式锁
    public boolean tryLock(String lockKey, String requestId, long expireTime) {
        // 原子操作：SET NX EX
        Boolean result = redisTemplate.opsForValue().setIfAbsent(
            lockKey,
            requestId,
            expireTime,
            TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(result);
    }
}
```

---

## 三、电商系统中的典型应用场景

### 3.1 场景全景图

```
┌─────────────────────────────────────────────────────────────┐
│                        电商系统架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐          │
│  │  用户层  │ ───→ │  应用层  │ ───→ │ Redis层  │          │
│  └──────────┘      └──────────┘      └──────────┘          │
│                           │                 │                │
│                           ↓                 ↓                │
│                    ┌──────────┐      ┌──────────┐          │
│                    │ MySQL层  │      │  MQ层    │          │
│                    └──────────┘      └──────────┘          │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 场景一：商品详情页缓存

**业务特点：**
- 读多写少（读写比 1000:1）
- 热点数据明显（二八定律）
- 对一致性要求不高

**解决方案：**

```java
@Service
public class ProductService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private ProductMapper productMapper;
    
    /**
     * 查询商品详情 - 缓存优化版本
     * 
     * 优化效果：
     * - 缓存命中率：95%+
     * - 响应时间：从 20ms 降低到 1ms
     * - 数据库压力：降低 95%
     */
    public Product getProductById(Long productId) {
        String cacheKey = "product:detail:" + productId;
        
        // 1. 先查缓存
        String cachedProduct = redisTemplate.opsForValue().get(cacheKey);
        if (cachedProduct != null) {
            return JSON.parseObject(cachedProduct, Product.class);
        }
        
        // 2. 缓存未命中，查询数据库
        Product product = productMapper.selectById(productId);
        if (product == null) {
            // 防止缓存穿透：缓存空对象
            redisTemplate.opsForValue().set(cacheKey, "", 5, TimeUnit.MINUTES);
            return null;
        }
        
        // 3. 写入缓存
        redisTemplate.opsForValue().set(
            cacheKey,
            JSON.toJSONString(product),
            1, TimeUnit.HOURS
        );
        
        return product;
    }
    
    /**
     * 更新商品信息
     * 
     * 缓存更新策略：Cache Aside Pattern
     */
    public void updateProduct(Product product) {
        // 1. 先更新数据库
        productMapper.updateById(product);
        
        // 2. 删除缓存（而不是更新缓存）
        // 原因：避免并发更新导致的数据不一致
        String cacheKey = "product:detail:" + product.getId();
        redisTemplate.delete(cacheKey);
    }
}
```

**性能提升数据：**

| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 平均响应时间 | 20ms | 1ms | **20倍** |
| QPS | 5,000 | 50,000 | **10倍** |
| 数据库负载 | 100% | 5% | **降低95%** |
| 成本 | 10台MySQL | 2台MySQL + 3台Redis | **节省50%** |

### 3.3 场景二：秒杀系统

**业务特点：**
- 瞬时高并发（10万+ QPS）
- 库存有限
- 不能超卖

**解决方案：**

```java
@Service
public class SeckillService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private OrderService orderService;
    
    /**
     * 秒杀下单
     * 
     * 核心思路：
     * 1. 库存预热到 Redis
     * 2. 使用 Redis 原子操作扣减库存
     * 3. 异步创建订单
     */
    public SeckillResult seckill(Long userId, Long productId) {
        String stockKey = "seckill:stock:" + productId;
        String userKey = "seckill:user:" + productId + ":" + userId;
        
        // 1. 检查用户是否已经秒杀过（防止重复购买）
        Boolean hasOrdered = redisTemplate.hasKey(userKey);
        if (Boolean.TRUE.equals(hasOrdered)) {
            return SeckillResult.fail("您已经参与过该商品的秒杀");
        }
        
        // 2. 原子扣减库存
        Long stock = redisTemplate.opsForValue().decrement(stockKey);
        if (stock == null || stock < 0) {
            // 库存不足，回滚
            if (stock != null) {
                redisTemplate.opsForValue().increment(stockKey);
            }
            return SeckillResult.fail("商品已售罄");
        }
        
        // 3. 标记用户已秒杀
        redisTemplate.opsForValue().set(userKey, "1", 1, TimeUnit.DAYS);
        
        // 4. 异步创建订单（使用消息队列）
        SeckillOrder order = new SeckillOrder(userId, productId);
        orderService.createOrderAsync(order);
        
        return SeckillResult.success("秒杀成功，订单生成中");
    }
    
    /**
     * 秒杀活动预热
     * 
     * 在秒杀开始前，将库存加载到 Redis
     */
    public void warmUp(Long productId, int stock) {
        String stockKey = "seckill:stock:" + productId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }
}
```

**架构优势：**

```
传统方案（直接操作数据库）：
用户请求 → 应用服务器 → MySQL 扣减库存 → 创建订单
问题：数据库成为瓶颈，QPS 只有 1000

Redis 方案：
用户请求 → 应用服务器 → Redis 扣减库存 → MQ 异步创建订单
优势：QPS 可达 10万+，数据库压力小
```

### 3.4 场景三：分布式 Session

**业务特点：**
- 多台应用服务器
- 用户需要保持登录状态
- Session 需要共享

**解决方案：**

```java
@Component
public class RedisSessionManager {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final long SESSION_TIMEOUT = 30 * 60; // 30分钟
    
    /**
     * 用户登录
     */
    public String login(Long userId, UserInfo userInfo) {
        // 1. 生成 Session ID
        String sessionId = UUID.randomUUID().toString();
        
        // 2. 存储到 Redis
        String sessionKey = "session:" + sessionId;
        redisTemplate.opsForValue().set(
            sessionKey,
            userInfo,
            SESSION_TIMEOUT,
            TimeUnit.SECONDS
        );
        
        // 3. 建立用户ID到SessionID的映射（支持踢人功能）
        String userSessionKey = "user:session:" + userId;
        redisTemplate.opsForValue().set(userSessionKey, sessionId);
        
        return sessionId;
    }
    
    /**
     * 获取用户信息
     */
    public UserInfo getUserInfo(String sessionId) {
        String sessionKey = "session:" + sessionId;
        UserInfo userInfo = (UserInfo) redisTemplate.opsForValue().get(sessionKey);
        
        if (userInfo != null) {
            // 续期
            redisTemplate.expire(sessionKey, SESSION_TIMEOUT, TimeUnit.SECONDS);
        }
        
        return userInfo;
    }
    
    /**
     * 登出
     */
    public void logout(String sessionId) {
        String sessionKey = "session:" + sessionId;
        redisTemplate.delete(sessionKey);
    }
    
    /**
     * 踢人（强制下线）
     */
    public void kickOut(Long userId) {
        String userSessionKey = "user:session:" + userId;
        String sessionId = (String) redisTemplate.opsForValue().get(userSessionKey);
        
        if (sessionId != null) {
            logout(sessionId);
            redisTemplate.delete(userSessionKey);
        }
    }
}
```

### 3.5 场景四：实时排行榜

**业务特点：**
- 需要实时更新
- 需要快速查询 Top N
- 数据量大

**解决方案：**

```java
@Service
public class RankingService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * 更新商品销量排行榜
     */
    public void updateSalesRanking(Long productId, int salesIncrement) {
        String key = "ranking:sales:daily:" + LocalDate.now();
        
        // 增加销量分数
        redisTemplate.opsForZSet().incrementScore(
            key,
            productId.toString(),
            salesIncrement
        );
        
        // 设置过期时间（保留7天）
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }
    
    /**
     * 获取销量 Top 10
     * 
     * 时间复杂度：O(log(N) + M)，N是总数，M是返回数量
     * 即使有百万商品，查询 Top 10 也只需要 1ms
     */
    public List<ProductRanking> getTopSales(int count) {
        String key = "ranking:sales:daily:" + LocalDate.now();
        
        // 获取 Top N（按分数降序）
        Set<ZSetOperations.TypedTuple<String>> topProducts = 
            redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, count - 1);
        
        if (topProducts == null) {
            return Collections.emptyList();
        }
        
        List<ProductRanking> rankings = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : topProducts) {
            ProductRanking ranking = new ProductRanking();
            ranking.setRank(rank++);
            ranking.setProductId(Long.parseLong(tuple.getValue()));
            ranking.setSales(tuple.getScore().intValue());
            rankings.add(ranking);
        }
        
        return rankings;
    }
    
    /**
     * 获取指定商品的排名
     */
    public Long getProductRank(Long productId) {
        String key = "ranking:sales:daily:" + LocalDate.now();
        Long rank = redisTemplate.opsForZSet().reverseRank(key, productId.toString());
        return rank == null ? null : rank + 1; // rank 从0开始，需要+1
    }
}
```

**性能对比：**

| 操作 | MySQL 方案 | Redis ZSet | 性能提升 |
|------|-----------|------------|----------|
| 更新分数 | UPDATE + ORDER BY | ZINCRBY | **100倍** |
| 查询 Top 100 | SELECT + ORDER BY LIMIT | ZREVRANGE | **50倍** |
| 查询排名 | COUNT(*) WHERE score > | ZREVRANK | **1000倍** |

### 3.6 场景五：限流控制

**业务特点：**
- 防止接口被刷
- 保护系统稳定性
- 需要精确控制

**解决方案：**

```java
@Component
public class RateLimiter {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 固定窗口限流
     * 
     * 场景：每个用户每分钟最多调用 100 次
     */
    public boolean tryAcquireFixedWindow(Long userId, int maxRequests, int windowSeconds) {
        String key = "rate:limit:" + userId + ":" + (System.currentTimeMillis() / 1000 / windowSeconds);
        
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return false;
        }
        
        if (count == 1) {
            // 第一次访问，设置过期时间
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        
        return count <= maxRequests;
    }
    
    /**
     * 滑动窗口限流（更精确）
     * 
     * 使用 ZSet 实现，score 为时间戳
     */
    public boolean tryAcquireSlidingWindow(Long userId, int maxRequests, int windowSeconds) {
        String key = "rate:limit:sliding:" + userId;
        long now = System.currentTimeMillis();
        long windowStart = now - windowSeconds * 1000;
        
        // 1. 删除窗口外的记录
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        
        // 2. 统计窗口内的请求数
        Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
        
        if (count != null && count < maxRequests) {
            // 3. 添加当前请求
            redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
            // 4. 设置过期时间
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            return true;
        }
        
        return false;
    }
}
```

---

## 四、Redis 不适合的场景

### 4.1 大数据量存储

❌ **不适合**：存储所有历史订单数据（TB级别）

**原因：**
- Redis 基于内存，成本高
- 内存有限，无法存储海量数据

✅ **正确做法**：
- 热数据放 Redis（最近30天订单）
- 冷数据放 MySQL/HBase（历史订单）

### 4.2 复杂查询

❌ **不适合**：多条件组合查询

```java
// 这种查询不适合用 Redis
SELECT * FROM orders 
WHERE user_id = ? 
  AND status = ? 
  AND create_time BETWEEN ? AND ?
  AND amount > ?
ORDER BY create_time DESC
LIMIT 10
```

✅ **正确做法**：
- 复杂查询用 MySQL/Elasticsearch
- 查询结果缓存到 Redis

### 4.3 强一致性要求

❌ **不适合**：金融交易、账户余额

**原因：**
- Redis 主从复制是异步的
- 可能出现数据丢失

✅ **正确做法**：
- 核心数据用 MySQL（ACID保证）
- Redis 只做缓存或辅助功能

---

## 五、面试重点

### 5.1 高频问题

**Q1：为什么 Redis 这么快？**

**A：** 四个核心原因：
1. **内存操作**：避免磁盘IO
2. **单线程模型**：避免线程切换和锁竞争
3. **IO多路复用**：单线程处理海量连接
4. **高效数据结构**：针对性能优化

**Q2：Redis 和 Memcached 的区别？**

| 特性 | Redis | Memcached |
|------|-------|-----------|
| 数据类型 | 5种+ | 只有String |
| 持久化 | 支持 | 不支持 |
| 集群 | 原生支持 | 需要客户端实现 |
| 线程模型 | 单线程 | 多线程 |
| 适用场景 | 更丰富 | 纯缓存 |

**Q3：缓存穿透、缓存击穿、缓存雪崩是什么？**

- **缓存穿透**：查询不存在的数据，缓存和数据库都没有
  - 解决：布隆过滤器、缓存空对象
  
- **缓存击穿**：热点key过期，大量请求打到数据库
  - 解决：互斥锁、热点数据永不过期
  
- **缓存雪崩**：大量key同时过期
  - 解决：过期时间加随机值、多级缓存

### 5.2 架构设计问题

**Q：如何设计一个高可用的缓存系统？**

**A：** 关键要素：
1. **缓存更新策略**：Cache Aside Pattern
2. **过期策略**：根据业务设置合理的TTL
3. **容灾方案**：主从复制 + 哨兵
4. **监控告警**：缓存命中率、慢查询
5. **降级方案**：Redis挂了，直接查数据库

---

## 六、实践要点

### 6.1 Key 设计规范

```java
// ✅ 好的 Key 设计
"product:detail:123"           // 业务:功能:ID
"user:session:abc123"          // 清晰的层级结构
"ranking:sales:daily:20260122" // 包含时间维度

// ❌ 不好的 Key 设计
"p123"                         // 不知道是什么
"user_session_abc123"          // 下划线不如冒号清晰
"verylongkeyname..."           // 太长浪费内存
```

### 6.2 过期时间设置

```java
// 根据业务特点设置
redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS);   // 商品详情
redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES); // Session
redisTemplate.opsForValue().set(key, value, 1, TimeUnit.DAYS);     // 排行榜

// 避免雪崩：加随机值
int randomSeconds = ThreadLocalRandom.current().nextInt(300); // 0-5分钟
redisTemplate.opsForValue().set(key, value, 3600 + randomSeconds, TimeUnit.SECONDS);
```

### 6.3 序列化选择

```java
// 推荐：使用 JSON 序列化（可读性好）
RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());

// 性能优先：使用 Protobuf/Kryo（体积小）
redisTemplate.setValueSerializer(new KryoRedisSerializer<>());
```

---

## 七、下一步学习

掌握了 Redis 的核心价值和应用场景后，接下来需要深入学习：

1. **数据结构原理** → [02-Redis数据结构深度解析.md](./02-Redis数据结构深度解析.md)
2. **内存管理机制** → [03-Redis内存管理与优化.md](./03-Redis内存管理与优化.md)
3. **持久化方案** → [05-Redis持久化机制详解.md](./05-Redis持久化机制详解.md)

---

**总结：Redis 的核心价值在于用内存换性能，用简单换速度。它不是万能的，但在缓存、计数、排行榜等场景下是最佳选择。**
