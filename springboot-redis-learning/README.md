# Spring Boot Redis Learning

Redis学习项目，包含Redis基础操作和Redisson高级特性的学习示例。

## 项目结构

```
springboot-redis-learning/
├── springboot-redis/          # Redis基础学习
│   ├── RedisTemplate操作
│   ├── Spring Cache集成
│   ├── 五种数据类型操作
│   └── 分布式锁（简单实现）
│
└── springboot-redisson/       # Redisson高级特性
    ├── 分布式锁（多种实现）
    ├── 分布式集合
    ├── 分布式对象
    └── 分布式服务
```

## 子模块介绍

### 1. springboot-redis

**技术栈：**
- Spring Boot 2.2.5.RELEASE
- Spring Data Redis
- Lettuce（连接池）
- Jackson（序列化）

**学习内容：**
- ✅ RedisTemplate基础操作（String、Hash、List、Set、ZSet）
- ✅ Spring Cache注解使用（@Cacheable、@CachePut、@CacheEvict）
- ✅ Redis序列化配置（Jackson2JsonRedisSerializer）
- ✅ 连接池配置（Lettuce Pool）
- ✅ 简单分布式锁实现（SETNX）
- ✅ 缓存穿透、缓存雪崩、缓存击穿解决方案

**端口：** 8080

**示例接口：**
```bash
# 设置值
POST /redis/set?key=test&value=hello

# 获取值
GET /redis/get?key=test

# 设置带过期时间的值
POST /redis/setex?key=test&value=hello&seconds=60

# 自增
POST /redis/incr?key=counter

# 删除
DELETE /redis/delete?key=test
```

---

### 2. springboot-redisson

**技术栈：**
- Spring Boot 2.2.5.RELEASE
- Redisson 3.16.8
- Redisson Spring Boot Starter

**学习内容：**

#### 分布式锁
- ✅ 可重入锁（Reentrant Lock）
- ✅ 公平锁（Fair Lock）
- ✅ 读写锁（ReadWrite Lock）
- ✅ 联锁（MultiLock）
- ✅ 红锁（RedLock）
- ✅ 信号量（Semaphore）
- ✅ 可过期性信号量（PermitExpirableSemaphore）
- ✅ 闭锁（CountDownLatch）

#### 分布式集合
- ✅ Map（RMap）
- ✅ Set（RSet）
- ✅ List（RList）
- ✅ Queue（RQueue）
- ✅ Deque（RDeque）
- ✅ SortedSet（RScoredSortedSet）
- ✅ BlockingQueue（RBlockingQueue）
- ✅ PriorityQueue（RPriorityQueue）

#### 分布式对象
- ✅ Object Holder（RBucket）
- ✅ Binary Stream（RBinaryStream）
- ✅ Geo（RGeo）
- ✅ BitSet（RBitSet）
- ✅ AtomicLong（RAtomicLong）
- ✅ AtomicDouble（RAtomicDouble）
- ✅ Bloom Filter（RBloomFilter）
- ✅ HyperLogLog（RHyperLogLog）

#### 分布式服务
- ✅ Remote Service（远程服务）
- ✅ Live Object Service（活动对象服务）
- ✅ Executor Service（执行器服务）
- ✅ Scheduler Service（调度器服务）

**端口：** 8081

**示例接口：**
```bash
# 可重入锁
GET /lock/reentrant?lockKey=myLock

# 公平锁
GET /lock/fair?lockKey=fairLock

# 读锁
GET /lock/read?lockKey=rwLock

# 写锁
POST /lock/write?lockKey=rwLock

# 联锁
GET /lock/multi?key1=lock1&key2=lock2

# 分布式Map
POST /collection/map/put?mapKey=myMap&key=name&value=张三
GET /collection/map/get?mapKey=myMap&key=name

# 分布式Set
POST /collection/set/add?setKey=mySet&value=item1
GET /collection/set/all?setKey=mySet

# 分布式Queue
POST /collection/queue/offer?queueKey=myQueue&value=task1
GET /collection/queue/poll?queueKey=myQueue
```

---

## 快速开始

### 前置条件

1. **安装Redis**
```bash
# macOS
brew install redis
brew services start redis

# Docker
docker run -d -p 6379:6379 --name redis redis:latest

# 验证
redis-cli ping
# 返回 PONG 表示成功
```

2. **配置Redis连接**

修改 `application.yml` 中的Redis配置：
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password:        # 如果有密码，填写密码
    database: 0
```

### 运行项目

#### 方式1：IDE运行
```
1. 导入项目到IDEA
2. 运行 RedisApplication（端口8080）
3. 运行 RedissonApplication（端口8081）
```

#### 方式2：Maven运行
```bash
# 构建项目
mvn clean install

# 运行springboot-redis
cd springboot-redis
mvn spring-boot:run

# 运行springboot-redisson
cd springboot-redisson
mvn spring-boot:run
```

---

## Redis vs Redisson 对比

| 特性 | Spring Data Redis | Redisson |
|------|------------------|----------|
| **定位** | 轻量级Redis客户端 | 功能丰富的Redis框架 |
| **基础操作** | ✅ 简单易用 | ✅ 功能更强 |
| **分布式锁** | ⚠️ 需要自己实现 | ✅ 开箱即用，多种实现 |
| **分布式集合** | ❌ 不支持 | ✅ 完整支持 |
| **性能** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **学习成本** | 低 | 中 |
| **适用场景** | 简单缓存、计数器 | 分布式系统、复杂场景 |

### 选择建议

**使用 Spring Data Redis：**
- ✅ 简单的缓存场景
- ✅ 基础的数据存储
- ✅ 性能要求极高
- ✅ 团队对Redis熟悉

**使用 Redisson：**
- ✅ 需要分布式锁
- ✅ 需要分布式集合
- ✅ 复杂的分布式场景
- ✅ 希望减少开发工作量

---

## 学习路径

### 第一阶段：Redis基础（springboot-redis）

1. **数据类型操作**
   - String：缓存、计数器、分布式ID
   - Hash：对象存储、购物车
   - List：消息队列、时间线
   - Set：标签、共同好友
   - ZSet：排行榜、延时队列

2. **Spring Cache集成**
   - @Cacheable：查询缓存
   - @CachePut：更新缓存
   - @CacheEvict：删除缓存
   - @Caching：组合操作

3. **常见问题解决**
   - 缓存穿透：布隆过滤器、缓存空值
   - 缓存雪崩：过期时间随机化、多级缓存
   - 缓存击穿：互斥锁、逻辑过期

### 第二阶段：Redisson进阶（springboot-redisson）

1. **分布式锁**
   - 可重入锁：防止死锁
   - 公平锁：按顺序获取
   - 读写锁：读写分离
   - 联锁/红锁：多资源锁定

2. **分布式集合**
   - Map/Set/List：基础集合
   - Queue/Deque：队列操作
   - SortedSet：有序集合
   - BlockingQueue：阻塞队列

3. **分布式对象**
   - AtomicLong：分布式计数器
   - BitSet：位图操作
   - BloomFilter：布隆过滤器
   - HyperLogLog：基数统计

### 第三阶段：实战应用

1. **分布式锁应用**
   - 秒杀系统：防止超卖
   - 定时任务：防止重复执行
   - 订单处理：防止并发修改

2. **缓存应用**
   - 热点数据缓存
   - 多级缓存架构
   - 缓存预热和更新策略

3. **性能优化**
   - Pipeline批量操作
   - Lua脚本原子操作
   - 连接池优化

---

## 常见问题

### Q1: Redis连接失败？
```bash
# 检查Redis是否启动
redis-cli ping

# 检查端口是否被占用
lsof -i:6379

# 查看Redis日志
tail -f /usr/local/var/log/redis.log
```

### Q2: 序列化异常？
确保实体类实现了 `Serializable` 接口，或者使用Jackson序列化。

### Q3: 分布式锁死锁？
- 使用 `tryLock(waitTime, leaseTime, TimeUnit)` 设置超时时间
- 使用 `try-finally` 确保锁被释放
- Redisson的锁自带看门狗机制，会自动续期

### Q4: Redisson配置不生效？
检查 `application.yml` 中的配置格式，确保使用 `redisson.single-server-config` 而不是 `redisson.singleServerConfig`。

---

## 参考资料

### 官方文档
- [Redis官方文档](https://redis.io/documentation)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Redisson官方文档](https://github.com/redisson/redisson/wiki)
- [Redisson中文文档](https://github.com/redisson/redisson/wiki/%E7%9B%AE%E5%BD%95)

### 推荐阅读
- 《Redis设计与实现》- 黄健宏
- 《Redis深度历险》- 钱文品
- [Redis命令参考](http://redisdoc.com/)
- [Redisson分布式锁原理](https://github.com/redisson/redisson/wiki/8.-%E5%88%86%E5%B8%83%E5%BC%8F%E9%94%81%E5%92%8C%E5%90%8C%E6%AD%A5%E5%99%A8)

---

## 后续计划

- [ ] Redis Cluster集群模式
- [ ] Redis Sentinel哨兵模式
- [ ] Redis持久化（RDB、AOF）
- [ ] Redis主从复制
- [ ] Redis性能优化
- [ ] Redis监控和运维
- [ ] Redisson分布式服务
- [ ] Redisson分布式调度

---

## 贡献

欢迎提交Issue和Pull Request！

## License

MIT License
