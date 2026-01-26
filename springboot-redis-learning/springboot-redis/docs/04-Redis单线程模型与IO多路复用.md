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

| 方式 | 时间复杂度 | 最大连接数 | 跨平台 | Redis 使用 |
|------|-----------|-----------|--------|-----------|
| **select** | O(N) | 1024 | ✅ | ❌ |
| **poll** | O(N) | 无限制 | ✅ | ❌ |
| **epoll** | O(1) | 无限制 | ❌ (Linux) | ✅ |
| **kqueue** | O(1) | 无限制 | ❌ (BSD) | ✅ |

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

### 4.1 为什么引入多线程？

**瓶颈分析：**

```
Redis 性能瓶颈（单线程）：
├── 网络 IO（读取请求、发送响应）← 耗时
├── 命令执行（内存操作）← 很快
└── 持久化（RDB/AOF）← 已经是多线程

结论：网络 IO 成为瓶颈
```

### 4.2 多线程模型

```
Redis 6.0 多线程模型：

主线程：
├── 命令执行（仍然是单线程）
└── 事件循环

IO 线程池：
├── 读取客户端请求
└── 发送响应给客户端

优势：
- 命令执行仍然是单线程（无锁）
- 网络 IO 使用多线程（提升吞吐量）
- 兼容性好（默认关闭）
```

**配置多线程：**

```bash
# redis.conf

# 开启多线程
io-threads-do-reads yes

# 设置 IO 线程数（建议：CPU 核数 - 1）
io-threads 4
```

**性能提升：**

```
测试环境：4 核 CPU

单线程：
- QPS: 10 万

多线程（4 个 IO 线程）：
- QPS: 15 万

提升：50%
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

```java
@Service
public class PipelineService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * ❌ 不使用 Pipeline（慢）
     */
    public void setWithoutPipeline(Map<String, String> data) {
        long start = System.currentTimeMillis();
        
        for (Map.Entry<String, String> entry : data.entrySet()) {
            redisTemplate.opsForValue().set(entry.getKey(), entry.getValue());
        }
        
        long cost = System.currentTimeMillis() - start;
        // 1000 条数据：约 500ms
        System.out.println("Without pipeline: " + cost + "ms");
    }
    
    /**
     * ✅ 使用 Pipeline（快）
     */
    public void setWithPipeline(Map<String, String> data) {
        long start = System.currentTimeMillis();
        
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
        
        long cost = System.currentTimeMillis() - start;
        // 1000 条数据：约 50ms
        System.out.println("With pipeline: " + cost + "ms");
    }
}
```

**Pipeline 原理：**

```
不使用 Pipeline：
客户端 → SET key1 value1 → Redis
       ← OK              ←
       → SET key2 value2 →
       ← OK              ←
       ...
RTT * N 次

使用 Pipeline：
客户端 → SET key1 value1 →
       → SET key2 value2 → Redis
       → SET key3 value3 →
       ← OK              ←
       ← OK              ←
       ← OK              ←
RTT * 1 次

性能提升：10 倍+
```

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
