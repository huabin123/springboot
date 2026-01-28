# Redis 单线程模型与 IO 多路复用

> **学习目标**：理解 Redis 为什么使用单线程、IO 多路复用的原理，以及如何实现高性能。

## 一、为什么 Redis 使用单线程？

### 1.1 单线程的优势

```
多线程的问题：
├── 线程切换开销（上下文切换）
├── 锁竞争（synchronized、Lock）
├── 死锁风险
└── 调试困难

单线程的优势：
├── 无锁竞争，性能更高
├── 代码简单，易于维护
├── 避免线程切换开销
└── 原子操作天然支持
```

### 1.2 单线程为什么快？

**核心原因：**

1. **纯内存操作**：避免磁盘 IO
2. **高效的数据结构**：SDS、跳表、压缩列表
3. **IO 多路复用**：单线程处理海量连接
4. **避免线程切换**：减少 CPU 开销

**性能数据：**

```bash
# Redis 基准测试
redis-benchmark -t set,get -n 100000 -q

SET: 98039.22 requests per second
GET: 98814.23 requests per second

# 单线程处理 10 万 QPS
```

---

## 二、IO 多路复用原理

### 2.1 传统 IO 模型 vs 多路复用

**传统 BIO（阻塞 IO）：**

```java
// 每个连接一个线程
while (true) {
    Socket socket = serverSocket.accept(); // 阻塞等待
    new Thread(() -> {
        handleClient(socket); // 处理客户端请求
    }).start();
}

问题：
- 1000 个连接 = 1000 个线程
- 线程切换开销大
- 内存占用高（每个线程 1MB 栈空间）
```

**IO 多路复用：**

```java
// 一个线程处理多个连接
Selector selector = Selector.open();

while (true) {
    selector.select(); // 阻塞等待事件

    Set<SelectionKey> keys = selector.selectedKeys();
    for (SelectionKey key : keys) {
        if (key.isReadable()) {
            handleRead(key);
        } else if (key.isWritable()) {
            handleWrite(key);
        }
    }
}

优势：
- 1 个线程处理 10000+ 连接
- 无线程切换开销
- 内存占用低
```

### 2.2 三种多路复用实现


| 方式       | 时间复杂度 | 最大连接数 | 跨平台     | Redis 使用 |
| ---------- | ---------- | ---------- | ---------- | ---------- |
| **select** | O(N)       | 1024       | ✅         | ❌         |
| **poll**   | O(N)       | 无限制     | ✅         | ❌         |
| **epoll**  | O(1)       | 无限制     | ❌ (Linux) | ✅         |
| **kqueue** | O(1)       | 无限制     | ❌ (BSD)   | ✅         |

**Redis 的选择策略：**

```c
// Redis 源码中的选择逻辑
#ifdef HAVE_EPOLL
    #include "ae_epoll.c"  // Linux 使用 epoll
#else
    #ifdef HAVE_KQUEUE
        #include "ae_kqueue.c"  // macOS/BSD 使用 kqueue
    #else
        #include "ae_select.c"  // 其他系统使用 select
    #endif
#endif
```

### 2.3 epoll 原理

**核心概念：**

```c
// 1. 创建 epoll 实例
int epfd = epoll_create(1024);

// 2. 注册事件
struct epoll_event ev;
ev.events = EPOLLIN;  // 监听可读事件
ev.data.fd = sockfd;
epoll_ctl(epfd, EPOLL_CTL_ADD, sockfd, &ev);

// 3. 等待事件
struct epoll_event events[MAX_EVENTS];
int nfds = epoll_wait(epfd, events, MAX_EVENTS, -1);

// 4. 处理事件
for (int i = 0; i < nfds; i++) {
    if (events[i].events & EPOLLIN) {
        handleRead(events[i].data.fd);
    }
}
```

**epoll 的优势：**

```
select/poll 的问题：
1. 每次调用需要传递整个 fd 集合（O(N)）
2. 内核需要遍历所有 fd 检查事件（O(N)）
3. fd 数量有限制（select 最多 1024）

epoll 的优化：
1. 使用红黑树管理 fd（O(log N)）
2. 使用就绪列表存储活跃 fd（O(1)）
3. 无 fd 数量限制
4. 支持边缘触发（ET）和水平触发（LT）
```

---

## 三、Redis 事件驱动模型

### 3.1 事件循环（Event Loop）

```c
// Redis 事件循环伪代码
void aeMain(aeEventLoop *eventLoop) {
    while (!eventLoop->stop) {
        // 1. 处理文件事件（网络 IO）
        processFileEvents(eventLoop);

        // 2. 处理时间事件（定时任务）
        processTimeEvents(eventLoop);
    }
}
```

**事件类型：**

```
Redis 事件系统
├── 文件事件（File Event）
│   ├── 可读事件（客户端发送命令）
│   ├── 可写事件（向客户端返回结果）
│   └── 连接事件（新客户端连接）
└── 时间事件（Time Event）
    ├── 定期删除过期 key
    ├── RDB/AOF 持久化
    └── 主从复制心跳
```

### 3.2 命令执行流程

```
客户端请求处理流程：

1. 客户端发送命令
   ↓
2. epoll_wait 检测到可读事件
   ↓
3. 读取命令到输入缓冲区
   ↓
4. 解析命令
   ↓
5. 执行命令（查找命令表 → 执行函数）
   ↓
6. 将结果写入输出缓冲区
   ↓
7. epoll_wait 检测到可写事件
   ↓
8. 发送结果给客户端
```

**Java 客户端视角：**

```java
@Service
public class RedisCommandFlow {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 执行 SET 命令的完整流程
     */
    public void demonstrateCommandFlow() {
        // 1. 客户端发送命令：SET key value
        redisTemplate.opsForValue().set("key", "value");

        // 底层流程：
        // 1) Lettuce/Jedis 将命令序列化为 RESP 协议
        //    *3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n

        // 2) 通过 Socket 发送到 Redis 服务器

        // 3) Redis 的 epoll 检测到可读事件

        // 4) Redis 读取命令并解析

        // 5) 查找命令表，找到 SET 命令的处理函数

        // 6) 执行 setCommand() 函数

        // 7) 将结果 "+OK\r\n" 写入输出缓冲区

        // 8) epoll 检测到可写事件，发送结果

        // 9) 客户端接收到 "+OK\r\n"，解析为成功
    }
}
```

---

## 四、Redis 6.0 的多线程

### 4.1 Redis 真的是单线程吗？

**Redis 6.0 之前的版本真的是单线程吗？**

Redis 在处理客户端的请求时，包括**获取 (socket 读)、解析、执行、内容返回 (socket 写)** 等都由一个顺序串行的主线程处理，这就是所谓的"单线程"。

但如果严格来讲，**从 Redis 4.0 之后并不是单线程**，除了主线程外，它也有后台线程在处理一些较为缓慢的操作，例如：

```
Redis 4.0+ 的线程模型：

主线程（单线程）：
├── 接收客户端请求（socket 读）
├── 解析命令
├── 执行命令（内存操作）
└── 返回结果（socket 写）

后台线程（多线程）：
├── 清理脏数据
├── 无用连接的释放
├── 大 key 的删除（UNLINK、FLUSHDB ASYNC）
└── AOF 持久化（fsync）
```

**示例：异步删除大 key**

```bash
# Redis 4.0+ 支持异步删除
127.0.0.1:6379> UNLINK big_key
(integer) 1

# 传统的 DEL 是同步删除（阻塞主线程）
127.0.0.1:6379> DEL big_key
(integer) 1
```

```java
@Service
public class AsyncDeleteService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 删除大 key（推荐使用 UNLINK）
     */
    public void deleteBigKey(String key) {
        // ✅ 使用 UNLINK（异步删除，不阻塞主线程）
        redisTemplate.unlink(key);

        // ❌ 使用 DELETE（同步删除，可能阻塞主线程）
        // redisTemplate.delete(key);
    }
}
```

### 4.2 Redis 6.0 之前为什么一直不使用多线程？

**原因一：CPU 不是瓶颈**

使用 Redis 时，**几乎不存在 CPU 成为瓶颈的情况**，Redis 主要受限于**内存和网络**。

```
性能数据：
- 在一个普通的 Linux 系统上
- Redis 通过使用 pipelining 每秒可以处理 100 万个请求
- 如果应用程序主要使用 O(N) 或 O(log(N)) 的命令
- CPU 占用率通常很低（< 20%）
```

**原因二：可维护性高**

使用了单线程后，**可维护性高**。多线程模型虽然在某些方面表现优异，但是它却引入了程序执行顺序的不确定性，带来了并发读写的一系列问题：

```
多线程的问题：
├── 并发读写问题（需要加锁）
├── 线程切换开销（上下文切换）
├── 死锁风险
├── 调试困难
└── 系统复杂度增加

单线程的优势：
├── 无锁竞争，性能更高
├── 代码简单，易于维护
├── 避免线程切换开销
└── "线程不安全" 的命令都可以无锁进行
    ├── Hash 的惰性 Rehash
    ├── LPUSH
    └── ZADD 等
```

**原因三：IO 多路复用已经足够高效**

Redis 通过 **AE 事件模型** 以及 **IO 多路复用** 等技术，处理性能非常高，因此没有必要使用多线程。

```
单线程 + IO 多路复用：
- 一个线程处理 10000+ 连接
- QPS 可达 10 万+
- 对于 80% 的公司来说已经足够
```

### 4.3 Redis 6.0 为什么要引入多线程？

**原因一：业务场景越来越复杂**

Redis 将所有数据放在内存中，内存的响应时长大约为 **100 纳秒**，对于小数据包，Redis 服务器可以处理 **80,000 到 100,000 QPS**，这也是 Redis 处理的极限了。

```
性能瓶颈：
- 对于 80% 的公司：单线程 Redis 已经足够
- 但随着越来越复杂的业务场景
- 有些公司动不动就上亿的交易量
- 需要更大的 QPS
```

**传统解决方案的问题：**

常见的解决方案是在分布式架构中对数据进行分区并采用多个服务器（**Redis Cluster**），但该方案有非常大的缺点：

```
Redis Cluster 的缺点：
├── 要管理的 Redis 服务器太多，维护代价大
├── 某些适用于单个 Redis 服务器的命令不适用于数据分区
│   └── 例如：MGET、事务、Lua 脚本等
├── 数据分区无法解决热点读/写问题
├── 数据偏斜，重新分配和扩容/缩容变得更加复杂
└── 跨节点操作性能差
```

**原因二：网络 IO 成为瓶颈**

从 Redis 自身角度来说，因为**读写网络的 read/write 系统调用占用了 Redis 执行期间大部分 CPU 时间**，瓶颈主要在于**网络的 IO 消耗**。

```
Redis 性能分析：
┌─────────────────────────────────┐
│ Redis 主线程时间分配            │
├─────────────────────────────────┤
│ 网络 IO (read/write)  │ 60%     │ ← 瓶颈
│ 命令解析              │ 10%     │
│ 命令执行 (内存操作)   │ 20%     │
│ 返回结果              │ 10%     │
└─────────────────────────────────┘
```

**优化方向：**

优化主要有两个方向：

1. **提高网络 IO 性能**

   - 典型实现：使用 **DPDK** 来替代内核网络栈的方式
   - 缺点：与 Redis 关系不大，改动成本高
2. **使用多线程充分利用多核** ✅

   - 典型实现：Memcached
   - 优点：最有效最便捷的操作方式

**总结：Redis 支持多线程主要就是两个原因**

1. **可以充分利用服务器 CPU 资源**，目前主线程只能利用一个核
2. **多线程任务可以分摊 Redis 同步 IO 读写负荷**

### 4.4 Redis 6.0 多线程怎么实现的？

**核心设计：和多 Reactor 模式类似**

```
Redis 6.0 多线程模型：

┌─────────────────────────────────────────────────┐
│                  主线程 (Main Thread)            │
│  ┌──────────────────────────────────────────┐  │
│  │  1. 事件循环 (Event Loop)                │  │
│  │  2. 命令执行 (单线程，无锁)              │  │
│  │  3. 数据操作 (内存读写)                  │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
           ↑                           ↓
           │                           │
    读取请求缓冲区              写入响应缓冲区
           │                           │
           ↑                           ↓
┌─────────────────────────────────────────────────┐
│              IO 线程池 (IO Threads)              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ IO线程1  │  │ IO线程2  │  │ IO线程3  │      │
│  │ socket读 │  │ socket读 │  │ socket读 │      │
│  │ socket写 │  │ socket写 │  │ socket写 │      │
│  └──────────┘  └──────────┘  └──────────┘      │
└─────────────────────────────────────────────────┘
```

**关键特性：**

1. **网络 IO 改成了多线程**

   - 多个 IO 线程并行处理 socket 读写
   - 分摊网络 IO 负载
2. **事件处理（对内存的操作）还是单线程的**

   - 命令执行仍然是单线程
   - **所以不会有并发问题**
   - 无需加锁，保持了 Redis 的简单性

**工作流程：**

```
客户端请求处理流程（多线程模式）：

1. IO 线程池：并行读取多个客户端的请求
   ├── IO 线程 1：读取客户端 1、2、3 的请求
   ├── IO 线程 2：读取客户端 4、5、6 的请求
   └── IO 线程 3：读取客户端 7、8、9 的请求
   ↓
2. 主线程：等待所有 IO 线程读取完成
   ↓
3. 主线程：顺序执行所有命令（单线程，无锁）
   ├── 解析命令
   ├── 执行命令
   └── 将结果写入输出缓冲区
   ↓
4. IO 线程池：并行发送响应给客户端
   ├── IO 线程 1：发送响应给客户端 1、2、3
   ├── IO 线程 2：发送响应给客户端 4、5、6
   └── IO 线程 3：发送响应给客户端 7、8、9
```

### 4.5 配置多线程

**配置文件：**

```bash
# redis.conf

# 开启多线程 IO（默认关闭）
io-threads-do-reads yes

# 设置 IO 线程数
# 建议：CPU 核数 - 1（留一个核给主线程）
# 例如：4 核 CPU 设置为 3
io-threads 4

# 注意：
# 1. 线程数不是越多越好，建议不超过 8
# 2. 单核或双核 CPU 不建议开启多线程
# 3. 如果 QPS < 10 万，不建议开启多线程
```

**Java 客户端无需修改：**

```java
@Configuration
public class RedisConfig {

    /**
     * Redis 6.0 多线程对客户端透明
     * 无需修改任何代码
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);

        return new LettuceConnectionFactory(config);
    }
}
```

**性能提升：**

```
测试环境：4 核 CPU，10 万并发连接

单线程模式：
- QPS: 10 万
- CPU 使用率: 100%（单核）

多线程模式（4 个 IO 线程）：
- QPS: 15 万
- CPU 使用率: 80%（多核）

提升：50%

注意：
- 性能提升取决于网络 IO 占比
- 如果命令执行时间长（如 SORT），提升有限
- 小数据包场景提升明显
```

### 4.6 多线程 vs 单线程对比


| 特性           | 单线程模式   | 多线程模式 (Redis 6.0)         |
| -------------- | ------------ | ------------------------------ |
| **网络 IO**    | 单线程处理   | 多线程并行处理                 |
| **命令执行**   | 单线程       | 单线程（保持不变）             |
| **并发安全**   | 天然安全     | 天然安全（命令执行仍是单线程） |
| **CPU 利用率** | 单核 100%    | 多核负载均衡                   |
| **QPS**        | 10 万+       | 15 万+（提升 50%）             |
| **适用场景**   | 中小规模业务 | 高并发、大流量业务             |
| **配置复杂度** | 简单         | 需要调优                       |
| **默认状态**   | 开启         | 关闭（需手动开启）             |

### 4.7 何时开启多线程？

**建议开启的场景：**

```
✅ 适合开启多线程：
├── QPS 需求 > 10 万
├── CPU 核数 >= 4
├── 网络带宽充足
├── 主要是小数据包操作（GET、SET）
└── 对延迟不是特别敏感

❌ 不建议开启多线程：
├── QPS < 10 万（单线程已够用）
├── CPU 核数 < 4（线程切换开销大）
├── 大量复杂命令（SORT、ZUNIONSTORE）
├── 对延迟极度敏感（多线程可能增加延迟）
└── 内存是瓶颈（多线程无法解决）
```

**最佳实践：**

```java
@Service
public class RedisPerformanceMonitor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 监控 Redis 性能指标
     * 决定是否需要开启多线程
     */
    public void monitorPerformance() {
        // 1. 查看 QPS
        // redis-cli: INFO stats
        // 如果 instantaneous_ops_per_sec > 100000，考虑开启多线程

        // 2. 查看 CPU 使用率
        // 如果单核 CPU 使用率 > 80%，考虑开启多线程

        // 3. 查看网络 IO
        // redis-cli: INFO stats
        // 如果 total_net_input_bytes 增长快，考虑开启多线程

        // 4. 查看延迟
        // redis-cli: --latency
        // 如果延迟可接受，可以开启多线程
    }
}
```

---

## 五、实战：高并发场景优化

### 5.1 连接池配置

```java
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 连接池配置
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();

        // 最大连接数（根据业务调整）
        poolConfig.setMaxTotal(50);

        // 最大空闲连接
        poolConfig.setMaxIdle(20);

        // 最小空闲连接
        poolConfig.setMinIdle(5);

        // 连接耗尽时是否阻塞（true：阻塞，false：抛异常）
        poolConfig.setBlockWhenExhausted(true);

        // 最大等待时间（毫秒）
        poolConfig.setMaxWaitMillis(3000);

        // 空闲连接检测
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRunsMillis(60000);

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
            .poolConfig(poolConfig)
            .commandTimeout(Duration.ofSeconds(2))
            .build();

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName("localhost");
        serverConfig.setPort(6379);

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }
}
```

### 5.2 Pipeline 批量操作

#### 5.2.1 什么是 Pipeline？

**Pipeline（管道）** 是 Redis 提供的一种批量执行命令的机制，可以将多个命令打包一次性发送给 Redis 服务器，然后一次性接收所有响应。

**核心优势：**

```
问题：网络延迟（RTT）是性能瓶颈
- 每次 Redis 命令都需要一次网络往返
- 即使 Redis 执行很快（微秒级），网络延迟也可能达到毫秒级
- 1000 次命令 = 1000 次 RTT

解决方案：Pipeline
- 将 1000 个命令打包发送
- 只需要 1 次 RTT
- 性能提升：10 倍 ~ 100 倍
```

**Pipeline 原理图：**

```
不使用 Pipeline（串行）：
┌─────────┐                          ┌─────────┐
│ 客户端  │                          │  Redis  │
└────┬────┘                          └────┬────┘
     │ SET key1 value1                    │
     │ ──────────────────────────────────→│
     │                                    │ 执行 (1μs)
     │                           OK       │
     │ ←──────────────────────────────────│
     │ (RTT: 1ms)                         │
     │                                    │
     │ SET key2 value2                    │
     │ ──────────────────────────────────→│
     │                                    │ 执行 (1μs)
     │                           OK       │
     │ ←──────────────────────────────────│
     │ (RTT: 1ms)                         │
     │                                    │
     │ ... 重复 N 次                      │

总耗时 = N × RTT ≈ 1000ms (1000 个命令)


使用 Pipeline（批量）：
┌─────────┐                          ┌─────────┐
│ 客户端  │                          │  Redis  │
└────┬────┘                          └────┬────┘
     │ SET key1 value1                    │
     │ SET key2 value2                    │
     │ SET key3 value3                    │
     │ ... (1000 个命令)                  │
     │ ──────────────────────────────────→│
     │                                    │ 执行 (1ms)
     │                           OK       │
     │                           OK       │
     │                           OK       │
     │                           ...      │
     │ ←──────────────────────────────────│
     │ (RTT: 1ms)                         │

总耗时 = 1 × RTT + 执行时间 ≈ 2ms

性能提升：500 倍！
```

#### 5.2.2 Pipeline vs 普通命令

**性能对比测试：**

```java
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
    public void performanceComparison() {
        // 准备测试数据
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            testData.put("test:key:" + i, "value:" + i);
        }

        // 测试不使用 Pipeline
        long withoutPipeline = setWithoutPipeline(testData);

        // 清空数据
        redisTemplate.delete(testData.keySet());

        // 测试使用 Pipeline
        long withPipeline = setWithPipeline(testData);

        // 性能提升倍数
        double improvement = (double) withoutPipeline / withPipeline;
        System.out.println("性能提升: " + String.format("%.2f", improvement) + " 倍");
    }
}
```

**测试结果：**

```
测试数据：10000 个 key-value 对
网络环境：本地 Redis（RTT ≈ 0.1ms）

不使用 Pipeline: 1500ms
使用 Pipeline: 50ms
性能提升: 30 倍

测试数据：10000 个 key-value 对
网络环境：远程 Redis（RTT ≈ 1ms）

不使用 Pipeline: 12000ms
使用 Pipeline: 100ms
性能提升: 120 倍

结论：网络延迟越大，Pipeline 优势越明显
```

#### 5.2.3 Pipeline 的多种使用场景

**场景一：批量写入**

```java
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
```

**场景二：批量读取**

```java
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
```

**场景三：批量删除**

```java
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
```

**场景四：批量计数器操作**

```java
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
```

**场景五：混合操作**

```java
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
```

#### 5.2.4 Pipeline 的注意事项

**1. Pipeline 不保证原子性**

```java
/**
 * ❌ 错误示例：Pipeline 不是事务
 *
 * 问题：如果中间某个命令失败，其他命令仍会执行
 */
public void pipelineIsNotTransaction() {
    redisTemplate.executePipelined(new RedisCallback<Object>() {
        @Override
        public Object doInRedis(RedisConnection connection) throws DataAccessException {
            connection.set("key1".getBytes(), "value1".getBytes());
            // 假设这个命令失败
            connection.incr("not_a_number".getBytes()); // 错误：不是数字
            connection.set("key2".getBytes(), "value2".getBytes());
            return null;
        }
    });

    // 结果：key1 和 key2 都会被设置，即使中间命令失败
}

/**
 * ✅ 正确示例：需要原子性使用事务
 */
public void useTransactionForAtomicity() {
    redisTemplate.execute(new SessionCallback<Object>() {
        @Override
        public Object execute(RedisOperations operations) throws DataAccessException {
            operations.multi(); // 开启事务
            operations.opsForValue().set("key1", "value1");
            operations.opsForValue().increment("counter");
            operations.opsForValue().set("key2", "value2");
            return operations.exec(); // 提交事务
        }
    });
}
```

**2. 控制 Pipeline 批次大小**

```java
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
```

**3. Pipeline 不支持中间结果依赖**

```java
/**
 * ❌ 错误示例：Pipeline 中无法使用前一个命令的结果
 */
public void pipelineCannotUsePreviousResult() {
    // 这样做是错误的
    redisTemplate.executePipelined(new RedisCallback<Object>() {
        @Override
        public Object doInRedis(RedisConnection connection) throws DataAccessException {
            // 无法获取这个命令的返回值
            byte[] value = connection.get("key1".getBytes());

            // ❌ value 是 null，无法使用
            // connection.set("key2".getBytes(), value);

            return null;
        }
    });
}

/**
 * ✅ 正确示例：需要依赖结果时，分开执行
 */
public void executeSequentially() {
    // 先获取值
    String value = redisTemplate.opsForValue().get("key1");

    // 再使用这个值
    if (value != null) {
        redisTemplate.opsForValue().set("key2", value);
    }
}
```

#### 5.2.5 Pipeline vs 事务 vs Lua 脚本


| 特性         | Pipeline        | 事务 (MULTI/EXEC)     | Lua 脚本    |
| ------------ | --------------- | --------------------- | ----------- |
| **原子性**   | ❌ 不保证       | ✅ 保证               | ✅ 保证     |
| **性能**     | ⭐⭐⭐⭐⭐ 最快 | ⭐⭐⭐⭐ 快           | ⭐⭐⭐ 中等 |
| **网络往返** | 1 次            | 2 次 (MULTI + EXEC)   | 1 次        |
| **中间结果** | ❌ 不可用       | ❌ 不可用             | ✅ 可用     |
| **条件判断** | ❌ 不支持       | ⚠️ 有限支持 (WATCH) | ✅ 完全支持 |
| **适用场景** | 批量操作        | 需要原子性            | 复杂逻辑    |

**使用建议：**

```
选择 Pipeline：
✅ 批量读写操作
✅ 不需要原子性
✅ 追求极致性能

选择事务：
✅ 需要原子性
✅ 命令之间无依赖
✅ 简单的批量操作

选择 Lua 脚本：
✅ 需要原子性
✅ 有条件判断逻辑
✅ 需要使用中间结果
✅ 复杂的业务逻辑
```

#### 5.2.6 实战案例：秒杀系统

```java
/**
 * 秒杀系统：使用 Pipeline 批量预热库存
 */
@Service
public class SeckillService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 预热秒杀商品库存
     *
     * 场景：秒杀开始前，将 10000 个商品的库存写入 Redis
     * 使用 Pipeline 批量写入，提升性能
     */
    public void preloadSeckillStock(List<SeckillProduct> products) {
        long start = System.currentTimeMillis();

        // 分批执行，每批 500 个
        int batchSize = 500;
        for (int i = 0; i < products.size(); i += batchSize) {
            int end = Math.min(i + batchSize, products.size());  // 优雅的处理了最后一批，值得借鉴
            List<SeckillProduct> batch = products.subList(i, end);

            redisTemplate.executePipelined(new RedisCallback<Object>() {
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    for (SeckillProduct product : batch) {
                        // 设置库存
                        String stockKey = "seckill:stock:" + product.getId();
                        connection.set(stockKey.getBytes(),
                                     String.valueOf(product.getStock()).getBytes());

                        // 设置商品信息
                        String infoKey = "seckill:info:" + product.getId();
                        connection.set(infoKey.getBytes(),
                                     JSON.toJSONString(product).getBytes());

                        // 设置过期时间（秒杀结束后 1 小时）
                        connection.expire(stockKey.getBytes(), 7200);
                        connection.expire(infoKey.getBytes(), 7200);
                    }
                    return null;
                }
            });
        }

        long cost = System.currentTimeMillis() - start;
        System.out.println("预热 " + products.size() + " 个商品，耗时: " + cost + "ms");
    }
}
```

**完整示例代码请参考：**

- `com.huabin.redis.service.PipelineService`
- `com.huabin.redis.service.PipelineAdvancedService`
- `com.huabin.redis.controller.PipelineController`

---

## 六、面试重点

**Q1：Redis 为什么使用单线程？**

**A：** 三个核心原因：

1. **纯内存操作**：CPU 不是瓶颈，避免多线程的锁开销
2. **IO 多路复用**：单线程可以处理海量连接
3. **简化设计**：无锁、无死锁、易维护

**Q2：单线程的 Redis 如何处理高并发？**

**A：**

1. **IO 多路复用**：epoll 监听多个连接
2. **事件驱动**：异步非阻塞处理
3. **高效数据结构**：减少 CPU 时间
4. **Pipeline**：批量操作减少网络开销

**Q3：Redis 6.0 的多线程是什么？**

**A：**

- **命令执行**：仍然是单线程（保证原子性）
- **网络 IO**：使用多线程（提升吞吐量）
- **默认关闭**：需要手动开启

---

## 七、实践要点

### 7.1 性能优化建议

```java
// ✅ 1. 使用连接池
LettuceConnectionFactory factory = new LettuceConnectionFactory();
factory.setPoolConfig(poolConfig);

// ✅ 2. 批量操作使用 Pipeline
redisTemplate.executePipelined(...);

// ✅ 3. 避免大 key
// 单个 key 不要超过 10KB

// ✅ 4. 合理设置超时时间
redisTemplate.setDefaultTimeout(Duration.ofSeconds(2));

// ❌ 5. 避免阻塞命令
// 不要使用 KEYS *（生产环境）
// 使用 SCAN 代替
```

### 7.2 监控指标

```bash
# 查看连接数
127.0.0.1:6379> INFO clients
connected_clients:100

# 查看命令统计
127.0.0.1:6379> INFO commandstats
cmdstat_get:calls=1000000,usec=500000,usec_per_call=0.50

# 查看慢查询
127.0.0.1:6379> SLOWLOG GET 10
```

---

**下一步学习**：[05-Redis持久化机制详解.md](./05-Redis持久化机制详解.md)
