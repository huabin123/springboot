# Redisson 分布式锁深度学习指南

> 以问题驱动的方式深入学习 Redisson 分布式锁的实现原理、源码分析和最佳实践

## 📚 学习路径

本系列文档采用**问题驱动**的方式，从基础概念到源码实现，循序渐进地讲解 Redisson 分布式锁。

### 第一阶段：理论基础（为什么需要？）

#### [01-为什么需要分布式锁](./01-为什么需要分布式锁.md)
**核心问题**：
- 单机环境下的并发问题是什么？
- 为什么单机锁在分布式环境下失效了？
- 分布式锁需要解决哪些核心问题？
- 为什么选择 Redis 实现分布式锁？

**关键知识点**：
- 从单机到分布式的问题演进
- 分布式锁的五大核心诉求（互斥性、防死锁、容错性、可重入、性能）
- Redis vs 数据库 vs Zookeeper 的选型对比

---

#### [02-分布式锁的演进历程](./02-分布式锁的演进历程.md)
**核心问题**：
- 如何用数据库实现分布式锁？
- Zookeeper 如何实现分布式锁？
- Redis 分布式锁的演进过程是怎样的？
- 为什么 Redisson 比手写 Redis 命令好？

**关键知识点**：
- 数据库锁：唯一索引、悲观锁（FOR UPDATE）
- Zookeeper 锁：临时顺序节点、Watch 机制
- Redis 锁演进：SETNX+EXPIRE → SET NX EX → Lua 脚本 → Redisson
- 各方案的性能对比和适用场景

---

### 第二阶段：核心原理（如何实现？）

#### [03-Redisson分布式锁核心原理](./03-Redisson分布式锁核心原理.md)
**核心问题**：
- Redisson 的整体架构是什么样的？
- 加锁的完整流程是什么？
- 加锁的 Lua 脚本是如何实现的？
- 为什么必须使用 Lua 脚本？
- 发布订阅机制如何工作？

**关键知识点**：
- Redisson 架构：应用层、核心层、通信层
- 加锁流程图和 Lua 脚本详解
- 解锁流程图和 Lua 脚本详解
- 发布订阅机制避免无效轮询
- Hash 结构支持可重入

**核心 Lua 脚本**：
```lua
-- 加锁
if (redis.call('exists', KEYS[1]) == 0) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then
    redis.call('hincrby', KEYS[1], ARGV[2], 1);
    redis.call('pexpire', KEYS[1], ARGV[1]);
    return nil;
end;
return redis.call('pttl', KEYS[1]);
```

---

#### [04-锁续期机制WatchDog源码分析](./04-锁续期机制WatchDog源码分析.md)
**核心问题**：
- 固定过期时间的锁有什么问题？
- WatchDog 如何解决这个问题？
- WatchDog 的完整工作流程是什么？
- 为什么续期间隔是过期时间的 1/3？
- WatchDog 会不会导致锁永远不释放？

**关键知识点**：
- WatchDog 的启动条件和取消时机
- 定时任务的调度机制
- 续期 Lua 脚本的实现
- ExpirationEntry 的作用
- 可重入锁的 WatchDog 处理

**时间线示例**：
```
T0:   加锁成功，设置30秒过期
T10:  WatchDog第1次续期 → 过期时间延长到 T40
T20:  WatchDog第2次续期 → 过期时间延长到 T50
T30:  WatchDog第3次续期 → 过期时间延长到 T60
T35:  业务执行完毕，unlock()
T35:  取消WatchDog，删除锁
```

---

#### [05-可重入锁实现原理](./05-可重入锁实现原理.md)
**核心问题**：
- 为什么需要可重入锁？
- Redisson 如何在 Redis 中实现可重入？
- 为什么使用 Hash 而不是 String？
- 加锁和解锁的 Lua 脚本如何处理重入？
- 重入次数有上限吗？

**关键知识点**：
- 可重入的必要性（避免自己等待自己）
- Hash 结构：field 存储线程标识，value 存储重入次数
- 线程标识：UUID + ThreadId
- 加锁流程：锁不存在 → 创建；锁存在且是当前线程 → 重入次数+1
- 解锁流程：重入次数-1；为0时删除锁

**数据结构**：
```
myLock (Hash)
  └─ uuid:threadId → 重入次数
```

---

### 第三阶段：实战应用（如何使用？）

#### [07-实战使用与踩坑指南](./07-实战使用与踩坑指南.md)
**核心问题**：
- 如何正确使用 Redisson 分布式锁？
- lock() vs tryLock() 如何选择？
- 忘记释放锁会怎样？
- 锁的粒度如何设计？
- 锁超时时间如何设置？
- 如何避免死锁？
- 如何监控锁的使用情况？

**关键知识点**：
- 标准使用模板
- 常见踩坑场景和解决方案
- 性能优化实践
- 锁粒度设计原则
- 死锁预防策略
- 监控和告警

**最佳实践**：
```java
RLock lock = redissonClient.getLock(lockKey);
try {
    boolean locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
    if (!locked) {
        throw new BusinessException("系统繁忙");
    }
    try {
        // 业务逻辑
    } finally {
        lock.unlock();
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

---

## 🎯 学习建议

### 初学者路径
1. 先阅读 **01-为什么需要分布式锁**，理解背景和必要性
2. 再阅读 **02-分布式锁的演进历程**，了解技术选型
3. 然后阅读 **07-实战使用与踩坑指南**，学会基本使用
4. 最后根据兴趣深入学习原理部分

### 进阶路径
1. 深入学习 **03-Redisson分布式锁核心原理**，理解 Lua 脚本
2. 研究 **04-锁续期机制WatchDog源码分析**，掌握自动续期
3. 分析 **05-可重入锁实现原理**，理解 Hash 结构设计
4. 结合源码阅读，加深理解

### 架构师路径
1. 全面学习所有文档
2. 对比不同方案的优缺点
3. 根据业务场景选择合适的锁机制
4. 设计监控和告警方案
5. 制定团队规范和最佳实践

---

## 📊 核心知识图谱

```
分布式锁
├── 为什么需要？
│   ├── 单机锁失效
│   ├── 资源竞争
│   └── 数据一致性
│
├── 如何实现？
│   ├── 数据库（性能差）
│   ├── Zookeeper（强一致）
│   └── Redis（高性能）
│       └── Redisson（生产级）
│
├── 核心机制
│   ├── Lua 脚本（原子性）
│   ├── Hash 结构（可重入）
│   ├── WatchDog（自动续期）
│   └── Pub/Sub（高效通知）
│
└── 最佳实践
    ├── 使用 tryLock
    ├── finally 释放锁
    ├── 合理设计粒度
    └── 监控和告警
```

---

## 🔧 环境准备

### 依赖配置

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.16.8</version>
</dependency>
```

### Redis 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    database: 0
```

### Redisson 配置

```java
@Configuration
public class RedissonConfig {
    
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://localhost:6379")
                .setPassword(null)
                .setDatabase(0)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10);
        
        return Redisson.create(config);
    }
}
```

---

## 📝 快速开始

### 基础使用

```java
@Service
public class DemoService {
    
    @Autowired
    private RedissonClient redissonClient;
    
    public void basicExample() {
        RLock lock = redissonClient.getLock("myLock");
        
        lock.lock();
        try {
            // 业务逻辑
            System.out.println("执行业务");
        } finally {
            lock.unlock();
        }
    }
    
    public void tryLockExample() {
        RLock lock = redissonClient.getLock("myLock");
        
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
    }
}
```

---

## 🚀 进阶主题

### 待补充文档

#### 06-公平锁与红锁实现
- 公平锁的实现原理（基于 List 的队列）
- 红锁（RedLock）的实现和争议
- 读写锁的使用场景

#### 08-源码中的设计精髓
- Lua 脚本的巧妙运用
- 异步编程模型
- Netty 通信优化
- 连接池管理

---

## 💡 常见问题

### Q1: Redisson 和 Jedis 有什么区别？
- Jedis：Redis 的 Java 客户端，提供基础命令
- Redisson：基于 Jedis/Lettuce，提供分布式对象和服务

### Q2: 什么时候用 lock()，什么时候用 tryLock()？
- lock()：必须执行的任务（后台任务）
- tryLock()：可以快速失败的任务（Web 请求）

### Q3: WatchDog 会影响性能吗？
- 影响很小，每10秒一次续期请求
- 只在未指定过期时间时启用

### Q4: 如何选择锁的粒度？
- 按业务维度：用户锁、商品锁、订单锁
- 避免全局锁：影响并发性能
- 避免过细锁：无法保护资源

---

## 📖 参考资料

- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [Redis 官方文档](https://redis.io/documentation)
- [分布式锁的实现与优化](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)

---

## 🤝 贡献指南

欢迎提出问题和建议！

---

**最后更新时间**：2026-01-16

**作者**：huabin

**版本**：v1.0
