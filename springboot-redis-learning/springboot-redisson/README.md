# Redisson 分布式锁深度学习项目

> 以问题驱动的方式深入学习 Redisson 分布式锁的实现原理、源码分析和最佳实践

## 📁 项目结构

```
springboot-redisson/
├── docs/                           # 📚 学习文档（核心理论）
│   ├── README.md                   # 文档总览和学习路径
│   ├── 01-为什么需要分布式锁.md      # 从单机到分布式的问题演进
│   ├── 02-分布式锁的演进历程.md      # 数据库、Zookeeper、Redis方案对比
│   ├── 03-Redisson分布式锁核心原理.md # 工作原理、流程图、Lua脚本
│   ├── 04-锁续期机制WatchDog源码分析.md # 自动续期原理和实现
│   ├── 05-可重入锁实现原理.md        # Hash结构和重入计数
│   └── 07-实战使用与踩坑指南.md      # 最佳实践和常见问题
│
├── src/main/java/com/huabin/redisson/
│   ├── controller/                 # 🎮 REST API接口
│   │   ├── RedissonLockController.java      # 分布式锁演示接口
│   │   └── RedissonCollectionController.java # 分布式集合演示接口
│   │
│   ├── demo/                       # 💡 演示代码（基础使用）
│   │   ├── BasicLockDemo.java              # 基础加锁演示
│   │   └── ReentrantLockDemo.java          # 可重入锁演示
│   │
│   ├── project/                    # 🚀 实际项目演示（真实场景）
│   │   ├── SecKillService.java             # 秒杀服务（防止超卖）
│   │   ├── InventoryService.java           # 库存服务（库存扣减）
│   │   └── OrderService.java               # 订单服务（防止重复下单）
│   │
│   └── RedissonApplication.java    # Spring Boot 启动类
│
└── src/main/resources/
    └── application.yml             # 配置文件
```

---

## 🎯 学习目标

通过本项目，你将掌握：

1. **理论基础**
   - 为什么需要分布式锁？
   - 分布式锁的演进历程
   - 各种方案的优缺点对比

2. **核心原理**
   - Redisson 的架构设计
   - Lua 脚本的实现细节
   - WatchDog 自动续期机制
   - 可重入锁的实现原理

3. **实战应用**
   - 如何正确使用分布式锁
   - 常见踩坑场景和解决方案
   - 真实业务场景的应用

4. **源码分析**
   - 加锁/解锁的完整流程
   - 发布订阅机制
   - 异常处理和容错设计

---

## 📚 学习路径

### 🔰 初学者路径（2-3小时）

1. **阅读文档**
   - [01-为什么需要分布式锁](./docs/01-为什么需要分布式锁.md) - 理解背景和必要性
   - [07-实战使用与踩坑指南](./docs/07-实战使用与踩坑指南.md) - 学会基本使用

2. **运行演示代码**
   - `BasicLockDemo` - 基础加锁演示
   - `ReentrantLockDemo` - 可重入锁演示

3. **实践项目代码**
   - `SecKillService` - 秒杀场景
   - `InventoryService` - 库存扣减

### 🚀 进阶路径（5-8小时）

1. **深入理论**
   - [02-分布式锁的演进历程](./docs/02-分布式锁的演进历程.md)
   - [03-Redisson分布式锁核心原理](./docs/03-Redisson分布式锁核心原理.md)

2. **源码分析**
   - [04-锁续期机制WatchDog源码分析](./docs/04-锁续期机制WatchDog源码分析.md)
   - [05-可重入锁实现原理](./docs/05-可重入锁实现原理.md)

3. **动手实践**
   - 修改演示代码，观察行为变化
   - 使用 Redis 客户端观察数据结构
   - 编写测试用例验证理解

### 🏆 架构师路径（10+小时）

1. **全面学习所有文档**
2. **阅读 Redisson 源码**
3. **对比不同方案的优缺点**
4. **设计适合自己业务的锁方案**
5. **制定团队规范和最佳实践**

---

## 🚀 快速开始

### 1. 环境准备

**前置条件**：
- JDK 1.8+
- Maven 3.6+
- Redis 5.0+

**启动 Redis**：
```bash
# 使用 Docker 启动 Redis
docker run -d --name redis -p 6379:6379 redis:latest

# 或者使用本地 Redis
redis-server
```

### 2. 配置项目

修改 `application.yml`：
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0
```

### 3. 启动项目

```bash
# 编译项目
mvn clean install

# 启动应用
mvn spring-boot:run
```

### 4. 测试接口

**基础加锁测试**：
```bash
curl http://localhost:8080/lock/simple-lock
```

**可重入锁测试**：
```bash
curl http://localhost:8080/lock/reentrant-lock
```

**秒杀测试**：
```bash
# 初始化库存
curl -X POST "http://localhost:8080/seckill/init?productId=1001&stock=100"

# 模拟秒杀
curl -X POST "http://localhost:8080/seckill/buy?productId=1001&userId=1"
```

---

## 💡 核心知识点

### 1. 为什么需要分布式锁？

```
单机环境：
  synchronized / ReentrantLock ✅

分布式环境：
  多个JVM实例 → 单机锁失效 ❌
  需要跨JVM的全局锁 → 分布式锁 ✅
```

### 2. Redisson vs 手写 Redis 命令

| 特性 | 手写 Redis | Redisson |
|------|-----------|----------|
| 可重入 | ❌ | ✅ |
| 自动续期 | ❌ | ✅ (WatchDog) |
| 公平锁 | ❌ | ✅ |
| 红锁 | ❌ | ✅ |
| 代码量 | 多 | 少 |
| 可靠性 | 低 | 高 |

### 3. 核心实现原理

**数据结构**：
```
myLock (Hash)
  └─ uuid:threadId → 重入次数
```

**加锁 Lua 脚本**：
```lua
if (redis.call('exists', KEYS[1]) == 0) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
```

**WatchDog 续期**：
```
T0:   加锁，设置30秒过期
T10:  WatchDog续期 → 延长到T40
T20:  WatchDog续期 → 延长到T50
T30:  业务完成，unlock()
```

### 4. 最佳实践

```java
// ✅ 推荐写法
RLock lock = redissonClient.getLock(lockKey);
try {
    boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
    if (locked) {
        try {
            // 业务逻辑
        } finally {
            lock.unlock();
        }
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

---

## 📊 实际应用场景

### 场景1：秒杀防超卖

**问题**：高并发下库存扣减可能超卖

**解决方案**：
```java
RLock lock = redissonClient.getLock("secKill:lock:" + productId);
lock.lock();
try {
    // 查询库存
    // 扣减库存
    // 创建订单
} finally {
    lock.unlock();
}
```

### 场景2：防止重复下单

**问题**：用户并发请求可能创建多个订单

**解决方案**：
```java
RLock lock = redissonClient.getLock("order:lock:" + userId);
lock.lock();
try {
    // 检查是否已有订单
    // 创建订单
} finally {
    lock.unlock();
}
```

### 场景3：库存预占

**问题**：下单时预占库存，支付成功后扣减

**解决方案**：
```java
// 下单时预占
reserveStock(productId, orderId, quantity);

// 支付成功后确认
confirmStock(productId, orderId);

// 超时未支付，释放预占
releaseReservedStock(productId, orderId);
```

---

## ⚠️ 常见问题

### Q1: 忘记释放锁会怎样？

**A**: 如果指定了过期时间，锁会自动释放；如果使用 WatchDog，锁会一直续期直到进程崩溃（30秒后自动过期）。

**建议**：始终在 `finally` 块中释放锁。

### Q2: 锁的粒度如何设计？

**A**: 
- ❌ 过大：所有用户共用一把锁 → 并发性能差
- ❌ 过小：无法保护共享资源 → 数据不一致
- ✅ 合适：按业务维度加锁（用户锁、商品锁）

### Q3: 如何避免死锁？

**A**:
1. 设置超时时间（使用 `tryLock`）
2. 锁排序（按固定顺序获取多个锁）
3. 使用 `MultiLock`（原子获取多个锁）

### Q4: WatchDog 会影响性能吗？

**A**: 影响很小，每10秒一次续期请求，只在未指定过期时间时启用。

---

## 📈 性能对比

### 加锁性能

```
场景：10000次加锁/解锁

数据库锁：
  - TPS: 500-1000
  - 平均延迟: 50-100ms

Zookeeper锁：
  - TPS: 5000-10000
  - 平均延迟: 10-20ms

Redis锁（Redisson）：
  - TPS: 10000-50000
  - 平均延迟: 1-5ms
```

---

## 🔧 调试技巧

### 1. 观察 Redis 数据结构

```bash
# 查看锁的数据结构
redis-cli
> HGETALL myLock
1) "uuid:threadId"
2) "3"

# 查看锁的过期时间
> TTL myLock
(integer) 25
```

### 2. 监控锁的获取情况

```java
log.info("获取锁成功, lockKey={}, waitTime={}ms", lockKey, waitTime);
log.info("业务执行完成, lockKey={}, execTime={}ms", lockKey, execTime);
```

### 3. 检测锁的状态

```java
boolean isLocked = lock.isLocked();
boolean isHeldByCurrentThread = lock.isHeldByCurrentThread();
int holdCount = lock.getHoldCount();
```

---

## 📖 参考资料

- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [Redis 官方文档](https://redis.io/documentation)
- [分布式锁的实现与优化](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)

---

## 🤝 贡献指南

欢迎提出问题和建议！如果你发现文档中的错误或有更好的实践，请提交 Issue 或 Pull Request。

---

## 📝 学习建议

1. **先理解问题，再学习方案**
   - 不要直接学习 Redisson，先理解为什么需要分布式锁

2. **动手实践，观察现象**
   - 运行演示代码，使用 Redis 客户端观察数据变化

3. **对比不同方案**
   - 理解数据库、Zookeeper、Redis 的优缺点

4. **阅读源码，深入理解**
   - 结合文档阅读 Redisson 源码，理解实现细节

5. **应用到实际项目**
   - 根据业务场景选择合适的锁机制

---

## ✅ 学习检查清单

- [ ] 理解为什么需要分布式锁
- [ ] 了解分布式锁的演进历程
- [ ] 掌握 Redisson 的基本使用
- [ ] 理解 Lua 脚本的作用
- [ ] 理解 WatchDog 自动续期机制
- [ ] 理解可重入锁的实现原理
- [ ] 掌握常见踩坑场景和解决方案
- [ ] 能够在实际项目中应用分布式锁
- [ ] 能够根据业务场景设计锁方案
- [ ] 能够优化锁的性能

---

**最后更新时间**：2026-01-16

**作者**：huabin

**版本**：v1.0

**JDK版本**：1.8

**Redisson版本**：3.16.8
