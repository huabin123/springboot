# Redis 生产问题演示 - 快速启动指南

## 🚀 5分钟快速体验

### 步骤1：启动 Redis

```bash
# macOS
brew services start redis

# 或直接启动
redis-server
```

### 步骤2：启动项目

```bash
cd springboot-redis-learning/springboot-redis
mvn spring-boot:run
```

### 步骤3：访问演示

浏览器打开：`http://localhost:8080/redis/demo/`

---

## 📋 演示列表

### 1️⃣ 缓存穿透演示

**问题**：恶意用户查询不存在的商品

```bash
# 访问
http://localhost:8080/redis/demo/cache/penetration/problem

# 观察控制台输出
缓存未命中，查询数据库: -1000
缓存未命中，查询数据库: -1001
...
问题：每次请求都穿透到数据库！
```

**解决方案**：

```bash
# 访问
http://localhost:8080/redis/demo/cache/penetration/solution

# 观察控制台输出
布隆过滤器判断：商品不存在 -2000
布隆过滤器判断：商品不存在 -2001
...
优势：所有请求都被过滤！
```

---

### 2️⃣ 缓存击穿演示

**问题**：热点商品缓存过期，1000个请求同时打到数据库

```bash
# 访问
http://localhost:8080/redis/demo/cache/breakdown/problem

# 观察控制台输出
并发请求数: 1000
数据库查询次数: 1000
问题：大量请求同时打到数据库！
```

**解决方案**：

```bash
# 访问
http://localhost:8080/redis/demo/cache/breakdown/solution

# 观察控制台输出
并发请求数: 1000
数据库查询次数: 1
优势：只有一个线程查询数据库！
```

---

### 3️⃣ 缓存雪崩演示

**问题**：100个商品缓存同时过期

```bash
# 访问
http://localhost:8080/redis/demo/cache/avalanche/problem

# 观察控制台输出
等待10秒，让缓存过期...
缓存已过期，开始大量请求...
数据库查询次数: 100
问题：所有缓存同时失效！
```

**解决方案**：

```bash
# 访问
http://localhost:8080/redis/demo/cache/avalanche/solution

# 观察控制台输出
检查过期时间分布：
商品1 的剩余过期时间: 3712秒
商品2 的剩余过期时间: 3845秒
商品3 的剩余过期时间: 3623秒
优势：过期时间分散，避免雪崩！
```

---

### 4️⃣ BigKey 演示

**问题**：单个Hash存储大量数据

```bash
# 访问
http://localhost:8080/redis/demo/performance/bigkey/problem

# 观察控制台输出
Hash大小: 1000 个字段
HGETALL 耗时: 150ms
DEL 耗时: 80ms
问题：HGETALL 会阻塞Redis！
```

**解决方案**：

```bash
# 访问
http://localhost:8080/redis/demo/performance/bigkey/solution

# 观察控制台输出
已拆分成 10 个分片
优势：每个分片数据量小，操作快速！
```

---

### 5️⃣ 热点Key演示

**问题**：秒杀商品被10000人同时访问

```bash
# 访问
http://localhost:8080/redis/demo/cluster/hotkey/problem

# 观察控制台输出
并发访问数: 10000
问题：所有请求都打到同一个Redis节点！
```

**解决方案**：

```bash
# 访问
http://localhost:8080/redis/demo/cluster/hotkey/solution

# 观察控制台输出
优化方案总结：
1. 本地缓存（推荐）
2. 热点Key复制
3. 热点Key打散
```

---

### 6️⃣ 完整秒杀场景

**最重要的演示**：整合所有问题和解决方案

```bash
# 访问
http://localhost:8080/redis/demo/scenario/seckill

# 观察控制台输出
╔════════════════════════════════════════════════╗
║     Redis 生产问题全景图 - 秒杀场景演示       ║
╚════════════════════════════════════════════════╝

=== 压测：问题版本 ===
并发用户数: 1000
初始库存: 100
最终库存: -50（超卖！）
耗时: 5000ms
数据库查询次数: 800

=== 压测：优化版本 ===
并发用户数: 1000
初始库存: 100
最终库存: 0
成功秒杀: 100
失败次数: 900
耗时: 500ms
数据库查询次数: 1

╔════════════════════════════════════════════════╗
║              优化方案总结                      ║
╚════════════════════════════════════════════════╝

1. 缓存穿透 → 布隆过滤器
2. 缓存击穿 → 互斥锁 + 双重检查
3. 缓存雪崩 → 随机过期时间
4. 热点Key → Key复制 + 随机读取
5. 超卖问题 → Lua脚本原子操作
6. 性能优化 → 异步创建订单
```

---

## 🔍 查看代码

### 问题代码位置

```
src/main/java/com/huabin/redis/problem/
├── cache/
│   ├── CachePenetrationProblem.java      # 缓存穿透
│   ├── CacheBreakdownProblem.java        # 缓存击穿
│   └── CacheAvalancheProblem.java        # 缓存雪崩
├── performance/
│   ├── BigKeyProblem.java                # BigKey问题
│   └── BlockingProblem.java              # 阻塞问题
├── memory/
│   └── MemoryLeakProblem.java            # 内存泄漏
└── cluster/
    └── HotKeyProblem.java                # 热点Key
```

### 解决方案代码位置

```
src/main/java/com/huabin/redis/solution/
├── cache/
│   ├── CachePenetrationSolution.java     # 布隆过滤器
│   ├── CacheBreakdownSolution.java       # 互斥锁
│   └── CacheAvalancheSolution.java       # 随机过期
├── performance/
│   ├── BigKeySolution.java               # 拆分BigKey
│   └── BlockingSolution.java             # SCAN优化
├── memory/
│   └── MemoryOptimization.java           # 内存优化
└── cluster/
    └── HotKeySolution.java               # 本地缓存
```

### 完整场景代码

```
src/main/java/com/huabin/redis/scenario/
└── SeckillScenario.java                  # 秒杀场景（整合所有优化）
```

---

## 📖 学习建议

### 第1天：理解问题

1. 依次访问所有 `/problem` 接口
2. 观察控制台输出
3. 理解每个问题的现象

### 第2天：学习解决方案

1. 依次访问所有 `/solution` 接口
2. 对比优化效果
3. 阅读解决方案代码

### 第3天：实战演练

1. 运行完整秒杀场景
2. 修改代码参数
3. 观察不同配置的效果

### 第4天：总结提升

1. 阅读完整文档
2. 整理笔记
3. 准备面试题

---

## 🎯 核心知识点

### 1. 布隆过滤器

```java
// 创建布隆过滤器
BloomFilter<Long> filter = BloomFilter.create(
    Funnels.longFunnel(),
    1000,      // 预计元素数量
    0.01       // 误判率
);

// 添加元素
filter.put(123L);

// 判断元素是否存在
if (filter.mightContain(456L)) {
    // 可能存在
} else {
    // 一定不存在
}
```

### 2. Lua 脚本

```java
String luaScript = 
    "local stock = tonumber(redis.call('get', KEYS[1]))\n" +
    "if stock == nil or stock <= 0 then\n" +
    "    return -1\n" +
    "end\n" +
    "redis.call('decr', KEYS[1])\n" +
    "return stock - 1";

Long result = redisTemplate.execute(
    new DefaultRedisScript<>(luaScript, Long.class),
    Collections.singletonList(stockKey)
);
```

### 3. 互斥锁

```java
lock.lock();
try {
    // 双重检查
    product = redisTemplate.opsForValue().get(cacheKey);
    if (product != null) {
        return product;
    }
    
    // 查询数据库
    product = queryFromDatabase(productId);
    
    // 写入缓存
    redisTemplate.opsForValue().set(cacheKey, product);
} finally {
    lock.unlock();
}
```

### 4. 随机过期时间

```java
int baseExpire = 3600;
int randomExpire = ThreadLocalRandom.current().nextInt(300);
redisTemplate.opsForValue().set(
    key, 
    value, 
    baseExpire + randomExpire, 
    TimeUnit.SECONDS
);
```

---

## 💡 常见问题

### Q1：为什么访问接口没有输出？

**A**：输出在控制台，不是浏览器。请查看运行 `mvn spring-boot:run` 的终端窗口。

### Q2：如何停止项目？

**A**：在终端按 `Ctrl + C`

### Q3：如何修改代码后重新运行？

**A**：
```bash
# 停止项目（Ctrl + C）
# 重新启动
mvn spring-boot:run
```

### Q4：Redis 连接失败怎么办？

**A**：
```bash
# 检查 Redis 是否启动
redis-cli ping

# 如果返回 PONG，说明 Redis 正常
# 如果报错，请启动 Redis
redis-server
```

### Q5：如何查看 Redis 中的数据？

**A**：
```bash
# 连接 Redis
redis-cli

# 查看所有 key
KEYS *

# 查看某个 key 的值
GET key_name

# 查看 Hash
HGETALL hash_key
```

---

## 📚 延伸阅读

1. **完整文档**：`docs/08-Redis生产问题全景图与解决方案.md`
2. **项目总结**：`PROJECT_SUMMARY.md`
3. **Redis 官方文档**：https://redis.io/documentation

---

## 🎓 面试准备

运行完所有演示后，你将能够回答：

✅ 如何解决缓存穿透？  
✅ 如何解决缓存击穿？  
✅ 如何解决缓存雪崩？  
✅ 如何处理 BigKey？  
✅ 如何优化热点 Key？  
✅ 如何设计秒杀系统？  

---

**祝学习顺利！有问题欢迎交流。**
