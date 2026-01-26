# Redis 为什么这么快？

> **架构师视角**：从底层原理到架构设计，深度剖析 Redis 高性能的本质

## 一、性能数据

### 1.1 官方基准测试

```bash
# Redis 单实例性能
redis-benchmark -t set,get -n 1000000 -q

SET: 110000.00 requests per second
GET: 120000.00 requests per second

# 使用 pipeline
redis-benchmark -t set,get -n 1000000 -P 16 -q

SET: 1200000.00 requests per second  # 提升 10 倍
GET: 1500000.00 requests per second
```

**性能指标**：
- **QPS**：单实例 10 万+，pipeline 可达 100 万+
- **延迟**：P99 < 1ms，P999 < 5ms
- **吞吐量**：单实例可达 100MB/s

### 1.2 与其他数据库对比

| 数据库 | QPS | P99延迟 | 适用场景 |
|--------|-----|---------|---------|
| **Redis** | 100,000+ | < 1ms | 缓存、会话、计数器 |
| MySQL | 5,000-10,000 | 10-50ms | 关系型数据存储 |
| MongoDB | 10,000-30,000 | 5-20ms | 文档型数据存储 |
| Elasticsearch | 5,000-15,000 | 10-100ms | 全文搜索 |

**结论**：Redis 比传统数据库快 **10-100 倍**

## 二、核心原因分析

### 2.1 架构设计层面

#### 1. 纯内存操作

```
内存 vs 磁盘访问速度对比：

内存访问：
- L1 Cache: 0.5 ns
- L2 Cache: 7 ns
- RAM: 100 ns

磁盘访问：
- SSD 随机读: 150,000 ns (0.15 ms)
- HDD 随机读: 10,000,000 ns (10 ms)

速度差异：内存比 SSD 快 1500 倍，比 HDD 快 100,000 倍
```

**Redis 的内存策略**：

```c
// Redis 所有数据存储在内存中
typedef struct redisDb {
    dict *dict;                 // 键空间，所有 key-value 存储在内存
    dict *expires;              // 过期字典
    dict *blocking_keys;        // 阻塞键
    dict *ready_keys;           // 就绪键
    dict *watched_keys;         // 监视键
    int id;                     // 数据库 ID
    long long avg_ttl;          // 平均 TTL
    unsigned long expires_cursor; // 过期游标
    list *defrag_later;         // 碎片整理列表
} redisDb;

// 所有操作直接在内存中完成，无磁盘 I/O
```

**对比 MySQL**：

```sql
-- MySQL 查询流程
SELECT * FROM users WHERE id = 1;

1. 解析 SQL（CPU）
2. 查询优化器（CPU）
3. 检查 Buffer Pool（内存）
4. 如果未命中，读取磁盘（I/O，10ms）
5. 加载到内存
6. 返回结果

总耗时：10-50ms

-- Redis 查询流程
GET user:1

1. 哈希定位（内存，O(1)）
2. 返回结果

总耗时：< 0.1ms
```

#### 2. 单线程模型（避免锁竞争）

```
传统多线程模型的问题：

线程 1: INCR counter
线程 2: INCR counter
线程 3: INCR counter

需要加锁：
1. 获取锁（10-100 ns）
2. 执行操作（10 ns）
3. 释放锁（10-100 ns）
4. 上下文切换（1000-10000 ns）

总耗时：1000+ ns

Redis 单线程模型：

请求队列 → 单线程顺序处理 → 响应

1. 无锁开销
2. 无上下文切换
3. CPU 缓存友好

总耗时：10-50 ns
```

**单线程的优势**：

```java
// 传统多线程（需要加锁）
public class Counter {
    private int count = 0;
    private final Lock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();  // 锁开销
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }
}

// Redis 单线程（无需加锁）
INCR counter  // 原子操作，无锁开销
```

**性能对比**：

| 模型 | 锁开销 | 上下文切换 | CPU缓存 | 并发度 |
|------|--------|-----------|---------|--------|
| 多线程 | 有 | 频繁 | 差 | 高 |
| 单线程 | 无 | 无 | 好 | 中 |

**为什么单线程还能高并发？**

```
秘密：I/O 多路复用

传统阻塞 I/O：
线程 1 → 等待客户端 1 → 阻塞
线程 2 → 等待客户端 2 → 阻塞
...
需要大量线程，上下文切换开销大

I/O 多路复用（epoll）：
单线程 → 监听 N 个客户端 → 哪个就绪处理哪个

优势：
1. 单线程处理 10000+ 并发连接
2. 无上下文切换
3. 无锁竞争
```

#### 3. I/O 多路复用

```c
// Redis 使用 epoll（Linux）/ kqueue（BSD）/ select（Windows）

// epoll 核心代码
int epfd = epoll_create(1024);  // 创建 epoll 实例

// 注册事件
struct epoll_event ev;
ev.events = EPOLLIN;  // 监听可读事件
ev.data.fd = clientfd;
epoll_ctl(epfd, EPOLL_CTL_ADD, clientfd, &ev);

// 等待事件
struct epoll_event events[MAX_EVENTS];
int nfds = epoll_wait(epfd, events, MAX_EVENTS, -1);

// 处理就绪事件
for (int i = 0; i < nfds; i++) {
    if (events[i].events & EPOLLIN) {
        // 读取数据并处理
        handleClient(events[i].data.fd);
    }
}
```

**I/O 多路复用 vs 传统 I/O**：

```
传统 BIO（阻塞 I/O）：
┌────────┐     ┌────────┐     ┌────────┐
│ 线程1  │────→│ 客户端1 │     │ 阻塞   │
└────────┘     └────────┘     └────────┘
┌────────┐     ┌────────┐     ┌────────┐
│ 线程2  │────→│ 客户端2 │     │ 阻塞   │
└────────┘     └────────┘     └────────┘

问题：
- 10000 个连接需要 10000 个线程
- 内存占用：10000 × 1MB = 10GB
- 上下文切换开销巨大

I/O 多路复用（epoll）：
┌────────┐     ┌────────┐
│ 单线程 │────→│ epoll  │
└────────┘     └───┬────┘
                   ├──→ 客户端1（就绪）
                   ├──→ 客户端2（就绪）
                   ├──→ 客户端3（等待）
                   └──→ 客户端N（等待）

优势：
- 单线程处理 10000+ 连接
- 内存占用：< 100MB
- 无上下文切换
```

**性能对比**：

| I/O 模型 | 连接数 | 线程数 | 内存占用 | 性能 |
|---------|--------|--------|---------|------|
| BIO | 10000 | 10000 | 10GB | 差 |
| NIO | 10000 | 100 | 1GB | 中 |
| epoll | 10000 | 1 | 100MB | 优 |

### 2.2 数据结构层面

#### 1. 高效的数据结构

```c
// Redis 核心数据结构

// 1. SDS（Simple Dynamic String）- 优化的字符串
typedef struct sdshdr {
    int len;        // 已使用长度（O(1) 获取长度）
    int free;       // 剩余空间（减少内存分配）
    char buf[];     // 实际数据
};

// vs C 字符串
char *str = "hello";  // 获取长度需要遍历，O(N)

// 2. 字典（Hash Table）- O(1) 查找
typedef struct dict {
    dictht ht[2];       // 两个哈希表（渐进式 rehash）
    long rehashidx;     // rehash 索引
};

// 3. 跳表（Skip List）- O(log N) 查找
typedef struct zskiplist {
    struct zskiplistNode *header, *tail;
    unsigned long length;
    int level;
};

// 4. 压缩列表（ziplist）- 节省内存
// 连续内存块，无指针开销
```

**数据结构性能对比**：

| 操作 | Redis | MySQL | MongoDB |
|------|-------|-------|---------|
| GET | O(1) | O(log N) | O(log N) |
| SET | O(1) | O(log N) | O(log N) |
| ZADD | O(log N) | O(log N) | O(log N) |
| ZRANGE | O(log N + M) | O(log N + M) | O(log N + M) |

**示例：跳表 vs 平衡树**

```
跳表（Skip List）：
Level 3:  1 ----------------→ 7
Level 2:  1 ------→ 4 ------→ 7
Level 1:  1 → 2 → 3 → 4 → 5 → 6 → 7

查找 6：
1. 从 Level 3 开始：1 → 7（过大）
2. 降到 Level 2：1 → 4 → 7（过大）
3. 降到 Level 1：4 → 5 → 6（找到）

时间复杂度：O(log N)
优势：实现简单，无需旋转操作

平衡树（AVL/红黑树）：
        4
       / \
      2   6
     / \ / \
    1  3 5  7

查找 6：
1. 从根节点 4 开始
2. 6 > 4，走右子树
3. 到达节点 6（找到）

时间复杂度：O(log N)
劣势：实现复杂，需要旋转操作
```

#### 2. 编码优化（内存和性能的权衡）

```c
// Redis 根据数据量自动选择编码

// Hash 的两种编码
typedef struct redisObject {
    unsigned type:4;      // 数据类型
    unsigned encoding:4;  // 编码方式
    void *ptr;            // 指向实际数据
} robj;

// 小对象使用 ziplist（节省内存）
// 大对象使用 hashtable（性能优先）

// 自动转换策略
if (fields < 512 && max_value_size < 64) {
    encoding = ZIPLIST;  // 内存优先
} else {
    encoding = HASHTABLE;  // 性能优先
}
```

**编码性能对比**：

| 编码 | 内存占用 | 查询性能 | 适用场景 |
|------|---------|---------|---------|
| ziplist | 低（节省 80%） | O(N) | 小对象 |
| hashtable | 高 | O(1) | 大对象 |
| intset | 极低 | O(log N) | 整数集合 |
| skiplist | 中 | O(log N) | 有序集合 |

### 2.3 协议层面

#### 1. RESP 协议（Redis Serialization Protocol）

```
RESP 协议特点：
1. 简单：易于解析
2. 高效：无需复杂的序列化/反序列化
3. 人类可读：便于调试

示例：
客户端发送：
*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n

解析：
*3          → 3 个参数
$3\r\nSET   → 第 1 个参数：SET（3 字节）
$3\r\nkey   → 第 2 个参数：key（3 字节）
$5\r\nvalue → 第 3 个参数：value（5 字节）

服务端响应：
+OK\r\n     → 简单字符串
```

**对比其他协议**：

```
HTTP 协议：
POST /api/set HTTP/1.1
Host: localhost
Content-Type: application/json
Content-Length: 25

{"key":"key","value":"value"}

解析开销：
1. HTTP 头解析
2. JSON 序列化/反序列化
3. 字符串拼接

总耗时：100-500 ns

RESP 协议：
*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n

解析开销：
1. 简单的字符串解析
2. 无需序列化

总耗时：10-50 ns

性能提升：5-10 倍
```

#### 2. Pipeline（管道）

```bash
# 不使用 pipeline（RTT = 1ms）
SET key1 value1  # 1ms
SET key2 value2  # 1ms
SET key3 value3  # 1ms
总耗时：3ms

# 使用 pipeline
SET key1 value1
SET key2 value2
SET key3 value3
总耗时：1ms（节省 2ms）

# 性能提升：3 倍
```

**Java 代码示例**：

```java
// 不使用 pipeline
@Test
public void testWithoutPipeline() {
    long start = System.currentTimeMillis();
    for (int i = 0; i < 10000; i++) {
        redisTemplate.opsForValue().set("key" + i, "value" + i);
    }
    long end = System.currentTimeMillis();
    System.out.println("耗时：" + (end - start) + "ms");  // 约 5000ms
}

// 使用 pipeline
@Test
public void testWithPipeline() {
    long start = System.currentTimeMillis();
    redisTemplate.executePipelined(new RedisCallback<Object>() {
        @Override
        public Object doInRedis(RedisConnection connection) {
            for (int i = 0; i < 10000; i++) {
                connection.set(
                    ("key" + i).getBytes(), 
                    ("value" + i).getBytes()
                );
            }
            return null;
        }
    });
    long end = System.currentTimeMillis();
    System.out.println("耗时：" + (end - start) + "ms");  // 约 500ms
}

// 性能提升：10 倍
```

### 2.4 持久化层面

#### 1. RDB（快照）

```c
// RDB 持久化策略
save 900 1      // 900 秒内至少 1 次修改
save 300 10     // 300 秒内至少 10 次修改
save 60 10000   // 60 秒内至少 10000 次修改

// fork 子进程进行持久化
pid_t pid = fork();
if (pid == 0) {
    // 子进程：写入 RDB 文件
    rdbSave("dump.rdb");
    exit(0);
} else {
    // 父进程：继续处理请求
    // 使用 COW（Copy-On-Write）机制
}
```

**COW（Copy-On-Write）机制**：

```
初始状态：
父进程内存：[A][B][C][D]
           ↑
子进程内存：共享父进程内存

父进程修改 A：
父进程内存：[A'][B][C][D]  ← 复制 A 并修改
           ↑
子进程内存：[A][B][C][D]   ← 保持不变

优势：
1. 子进程持久化时，父进程继续服务
2. 只复制修改的页，节省内存
3. 持久化不影响性能
```

#### 2. AOF（追加日志）

```c
// AOF 持久化策略
appendfsync always      // 每次写入都同步（安全，慢）
appendfsync everysec    // 每秒同步一次（平衡）
appendfsync no          // 由操作系统决定（快，不安全）

// AOF 重写（压缩日志）
// 原始 AOF：
SET key1 value1
SET key1 value2
SET key1 value3
DEL key2
SET key2 value4

// 重写后：
SET key1 value3  // 合并多次操作
SET key2 value4
```

**性能对比**：

| 持久化方式 | 性能影响 | 数据安全 | 恢复速度 |
|-----------|---------|---------|---------|
| 无持久化 | 无 | 差 | - |
| RDB | 极小 | 中 | 快 |
| AOF（everysec） | 小 | 好 | 中 |
| AOF（always） | 大 | 极好 | 慢 |

### 2.5 网络层面

#### 1. 零拷贝（Zero Copy）

```c
// 传统数据传输（4 次拷贝，4 次上下文切换）
read(file_fd, buffer, size);     // 1. 磁盘 → 内核缓冲区
                                  // 2. 内核缓冲区 → 用户缓冲区
write(socket_fd, buffer, size);  // 3. 用户缓冲区 → Socket 缓冲区
                                  // 4. Socket 缓冲区 → 网卡

// 零拷贝（2 次拷贝，2 次上下文切换）
sendfile(socket_fd, file_fd, offset, size);
// 1. 磁盘 → 内核缓冲区
// 2. 内核缓冲区 → 网卡（DMA）

性能提升：2-3 倍
```

#### 2. TCP 优化

```conf
# Redis 网络优化配置
tcp-backlog 511           # TCP 连接队列长度
tcp-keepalive 300         # TCP keepalive 时间
timeout 0                 # 客户端超时时间（0 表示永不超时）

# 操作系统层面优化
net.core.somaxconn = 65535          # 最大连接数
net.ipv4.tcp_max_syn_backlog = 8192 # SYN 队列长度
net.ipv4.tcp_tw_reuse = 1           # TIME_WAIT 重用
```

## 三、架构师视角的优化策略

### 3.1 客户端优化

#### 1. 连接池

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        
        // 连接池优化
        config.setMaxTotal(200);        // 最大连接数
        config.setMaxIdle(50);          // 最大空闲连接
        config.setMinIdle(10);          // 最小空闲连接
        config.setMaxWaitMillis(3000);  // 最大等待时间
        
        // 连接检测
        config.setTestOnBorrow(true);   // 获取连接时检测
        config.setTestOnReturn(false);  // 归还连接时不检测
        config.setTestWhileIdle(true);  // 空闲时检测
        
        // 驱逐策略
        config.setTimeBetweenEvictionRunsMillis(30000);  // 30秒检测一次
        config.setMinEvictableIdleTimeMillis(60000);     // 空闲60秒驱逐
        
        return config;
    }
}
```

**连接池 vs 短连接**：

```
短连接（每次请求创建连接）：
1. 创建 TCP 连接（3 次握手，1.5 RTT）
2. 执行命令
3. 关闭连接（4 次挥手，2 RTT）

总耗时：3.5 RTT + 命令执行时间
如果 RTT = 1ms，总耗时 = 3.5ms + 0.1ms = 3.6ms

连接池（复用连接）：
1. 从连接池获取连接（< 0.01ms）
2. 执行命令（0.1ms）
3. 归还连接（< 0.01ms）

总耗时：0.12ms

性能提升：30 倍
```

#### 2. 批量操作

```java
// ❌ 不推荐：逐个操作
public void saveUsers(List<User> users) {
    for (User user : users) {
        redisTemplate.opsForValue().set("user:" + user.getId(), user);
    }
    // 耗时：N × RTT
}

// ✅ 推荐：批量操作（MSET）
public void saveUsersBatch(List<User> users) {
    Map<String, User> userMap = users.stream()
        .collect(Collectors.toMap(
            user -> "user:" + user.getId(),
            user -> user
        ));
    redisTemplate.opsForValue().multiSet(userMap);
    // 耗时：1 × RTT
}

// ✅ 推荐：Pipeline
public void saveUsersPipeline(List<User> users) {
    redisTemplate.executePipelined(new RedisCallback<Object>() {
        @Override
        public Object doInRedis(RedisConnection connection) {
            for (User user : users) {
                connection.set(
                    ("user:" + user.getId()).getBytes(),
                    serialize(user)
                );
            }
            return null;
        }
    });
    // 耗时：1 × RTT
}
```

#### 3. Lua 脚本（原子操作）

```java
// 场景：限流器（令牌桶算法）

// ❌ 不推荐：多次网络请求
public boolean tryAcquire(String key, int limit, int window) {
    Long current = redisTemplate.opsForValue().increment(key);
    if (current == 1) {
        redisTemplate.expire(key, window, TimeUnit.SECONDS);
    }
    return current <= limit;
    // 问题：
    // 1. 两次网络请求（increment + expire）
    // 2. 非原子操作，可能导致 key 永不过期
}

// ✅ 推荐：Lua 脚本（原子操作）
public boolean tryAcquireLua(String key, int limit, int window) {
    String script = 
        "local current = redis.call('incr', KEYS[1]) " +
        "if current == 1 then " +
        "    redis.call('expire', KEYS[1], ARGV[1]) " +
        "end " +
        "return current <= tonumber(ARGV[2])";
    
    return redisTemplate.execute(
        new DefaultRedisScript<>(script, Boolean.class),
        Collections.singletonList(key),
        String.valueOf(window),
        String.valueOf(limit)
    );
    // 优势：
    // 1. 一次网络请求
    // 2. 原子操作
    // 3. 性能提升 2 倍
}
```

### 3.2 服务端优化

#### 1. 内存优化

```conf
# 内存淘汰策略
maxmemory 2gb
maxmemory-policy allkeys-lru

# 淘汰策略对比
noeviction       # 不淘汰，写入失败（默认）
allkeys-lru      # 所有 key，LRU 淘汰（推荐）
allkeys-lfu      # 所有 key，LFU 淘汰（Redis 4.0+）
volatile-lru     # 有过期时间的 key，LRU 淘汰
volatile-lfu     # 有过期时间的 key，LFU 淘汰
allkeys-random   # 所有 key，随机淘汰
volatile-random  # 有过期时间的 key，随机淘汰
volatile-ttl     # 有过期时间的 key，TTL 最小的优先淘汰
```

**LRU vs LFU**：

```
LRU（Least Recently Used）：
淘汰最久未使用的 key

示例：
访问序列：A B C D E A
LRU 队列：A E D C B（A 最近访问，B 最久未访问）
淘汰：B

适用场景：热点数据访问

LFU（Least Frequently Used）：
淘汰访问频率最低的 key

示例：
访问次数：A(10) B(5) C(3) D(2) E(1)
淘汰：E

适用场景：长期热点数据
```

#### 2. 慢查询优化

```bash
# 配置慢查询
slowlog-log-slower-than 10000  # 10ms
slowlog-max-len 128            # 最多保留 128 条

# 查看慢查询
127.0.0.1:6379> SLOWLOG GET 10
1) 1) (integer) 5
   2) (integer) 1642838400
   3) (integer) 15000  # 耗时 15ms
   4) 1) "HGETALL"
      2) "user:12345"
   5) "127.0.0.1:6379"
   6) ""

# 分析慢查询
# 1. KEYS * → 使用 SCAN
# 2. HGETALL（大 Hash）→ 拆分数据
# 3. SMEMBERS（大 Set）→ 使用 SSCAN
```

#### 3. 大 Key 优化

```java
// ❌ 问题：大 Hash（10000 个字段）
HSET product:1 field1 value1 field2 value2 ... field10000 value10000

// 问题：
// 1. 单次操作耗时长（> 10ms）
// 2. 阻塞其他请求
// 3. 内存占用大

// ✅ 解决方案 1：拆分为多个小 Hash
HSET product:1:part1 field1 value1 ... field100 value100
HSET product:1:part2 field101 value101 ... field200 value200
...

// ✅ 解决方案 2：使用 String + 序列化
SET product:1 <serialized_data>

// ✅ 解决方案 3：分片存储
public String getShardKey(String key, String field) {
    int shard = Math.abs(field.hashCode()) % 10;
    return key + ":shard:" + shard;
}

HSET product:1:shard:0 field1 value1
HSET product:1:shard:1 field2 value2
...
```

### 3.3 集群优化

#### 1. 主从复制

```
架构：
┌────────┐
│  主节点 │ ← 写入
└────┬───┘
     │ 复制
     ├──→ ┌────────┐
     │    │ 从节点1 │ ← 读取
     │    └────────┘
     │
     └──→ ┌────────┐
          │ 从节点2 │ ← 读取
          └────────┘

优势：
1. 读写分离，提升读性能
2. 高可用，主节点故障时从节点接管
3. 数据备份
```

**Java 代码示例**：

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // 主从配置
        RedisStaticMasterReplicaConfiguration config = 
            new RedisStaticMasterReplicaConfiguration("master-host", 6379);
        
        // 添加从节点
        config.addNode("slave1-host", 6379);
        config.addNode("slave2-host", 6379);
        
        // 读写策略
        LettuceClientConfiguration clientConfig = 
            LettuceClientConfiguration.builder()
                .readFrom(ReadFrom.REPLICA_PREFERRED)  // 优先从从节点读
                .build();
        
        return new LettuceConnectionFactory(config, clientConfig);
    }
}

// 读写策略
ReadFrom.MASTER              // 只从主节点读
ReadFrom.MASTER_PREFERRED    // 优先主节点，主节点不可用时从从节点读
ReadFrom.REPLICA             // 只从从节点读
ReadFrom.REPLICA_PREFERRED   // 优先从节点，从节点不可用时从主节点读（推荐）
```

#### 2. 哨兵模式（高可用）

```
架构：
┌─────────┐   ┌─────────┐   ┌─────────┐
│ 哨兵1   │   │ 哨兵2   │   │ 哨兵3   │
└────┬────┘   └────┬────┘   └────┬────┘
     │            │            │
     └────────────┼────────────┘
                  │ 监控
         ┌────────┴────────┐
         │                 │
    ┌────┴───┐        ┌────┴───┐
    │  主节点 │        │ 从节点 │
    └────────┘        └────────┘

故障转移：
1. 哨兵检测到主节点故障
2. 哨兵投票选举新主节点
3. 从节点晋升为主节点
4. 其他从节点复制新主节点
5. 客户端自动切换到新主节点

优势：
1. 自动故障转移
2. 高可用（99.9%+）
3. 无需人工干预
```

#### 3. 集群模式（水平扩展）

```
架构：
┌─────────┐   ┌─────────┐   ┌─────────┐
│ 节点1   │   │ 节点2   │   │ 节点3   │
│ 0-5460  │   │5461-10922│  │10923-16383│
└─────────┘   └─────────┘   └─────────┘

分片策略：
slot = CRC16(key) % 16384

示例：
key = "user:1"
slot = CRC16("user:1") % 16384 = 5000
路由到：节点1

优势：
1. 水平扩展，支持 PB 级数据
2. 高可用，节点故障自动转移
3. 性能线性提升
```

**Java 代码示例**：

```java
@Configuration
public class RedisClusterConfig {
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration config = new RedisClusterConfiguration(
            Arrays.asList(
                "node1:6379",
                "node2:6379",
                "node3:6379"
            )
        );
        
        // 集群配置
        config.setMaxRedirects(3);  // 最大重定向次数
        
        return new LettuceConnectionFactory(config);
    }
}

// Hash Tag（确保相关 key 在同一节点）
// ❌ 问题：user:1 和 order:1 可能在不同节点，无法使用事务
SET user:1 "Alice"
SET order:1 "Order1"

// ✅ 解决方案：使用 Hash Tag
SET {user:1}:info "Alice"
SET {user:1}:order "Order1"
// {user:1} 部分用于计算 slot，确保在同一节点
```

## 四、性能调优实战

### 4.1 性能基准测试

```bash
# 1. 基础性能测试
redis-benchmark -t set,get -n 1000000 -q

# 2. 不同数据大小测试
redis-benchmark -t set,get -n 100000 -d 100 -q  # 100 字节
redis-benchmark -t set,get -n 100000 -d 1000 -q # 1KB
redis-benchmark -t set,get -n 100000 -d 10000 -q # 10KB

# 3. Pipeline 测试
redis-benchmark -t set,get -n 100000 -P 16 -q

# 4. 并发测试
redis-benchmark -t set,get -n 100000 -c 50 -q   # 50 个并发
redis-benchmark -t set,get -n 100000 -c 100 -q  # 100 个并发
```

### 4.2 监控指标

```java
@Component
public class RedisMonitor {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Scheduled(fixedRate = 60000)  // 每分钟执行一次
    public void monitor() {
        Properties info = redisTemplate.execute(
            (RedisCallback<Properties>) connection -> 
                connection.info()
        );
        
        // 1. 内存使用
        String usedMemory = info.getProperty("used_memory_human");
        String maxMemory = info.getProperty("maxmemory_human");
        log.info("内存使用：{} / {}", usedMemory, maxMemory);
        
        // 2. 命令统计
        String totalCommands = info.getProperty("total_commands_processed");
        String opsPerSec = info.getProperty("instantaneous_ops_per_sec");
        log.info("总命令数：{}，QPS：{}", totalCommands, opsPerSec);
        
        // 3. 连接数
        String connectedClients = info.getProperty("connected_clients");
        log.info("连接数：{}", connectedClients);
        
        // 4. 命中率
        String keyspaceHits = info.getProperty("keyspace_hits");
        String keyspaceMisses = info.getProperty("keyspace_misses");
        double hitRate = Double.parseDouble(keyspaceHits) / 
            (Double.parseDouble(keyspaceHits) + Double.parseDouble(keyspaceMisses));
        log.info("命中率：{}", hitRate);
        
        // 5. 慢查询
        List<Object> slowlogs = redisTemplate.execute(
            (RedisCallback<List<Object>>) connection -> 
                connection.slowlogGet(10)
        );
        log.info("慢查询数量：{}", slowlogs.size());
    }
}
```

### 4.3 性能优化清单

```
✅ 客户端优化
- [ ] 使用连接池
- [ ] 使用 Pipeline 批量操作
- [ ] 使用 Lua 脚本减少网络请求
- [ ] 避免大 key（> 10KB）
- [ ] 设置合理的超时时间

✅ 数据结构优化
- [ ] 选择合适的数据类型
- [ ] 使用 ziplist 节省内存
- [ ] 避免 KEYS * 命令
- [ ] 使用 SCAN 代替 KEYS

✅ 持久化优化
- [ ] 根据场景选择 RDB/AOF
- [ ] AOF 使用 everysec 策略
- [ ] 定期执行 AOF 重写
- [ ] 在从节点执行持久化

✅ 集群优化
- [ ] 读写分离
- [ ] 使用哨兵模式保证高可用
- [ ] 使用集群模式水平扩展
- [ ] 合理设置分片数量

✅ 监控告警
- [ ] 监控内存使用率
- [ ] 监控 QPS
- [ ] 监控慢查询
- [ ] 监控命中率
```

## 五、总结

### 5.1 Redis 快的核心原因

| 层面 | 原因 | 性能提升 |
|------|------|---------|
| **架构设计** | 纯内存操作 | 1000-10000 倍 |
| **并发模型** | 单线程 + I/O 多路复用 | 无锁竞争，无上下文切换 |
| **数据结构** | 高效的数据结构（Hash、跳表） | O(1) 或 O(log N) |
| **协议** | RESP 协议简单高效 | 5-10 倍 |
| **网络** | Pipeline、零拷贝 | 10-100 倍 |
| **持久化** | 异步持久化，不阻塞主线程 | 无影响 |

### 5.2 架构师视角的关键点

1. **理解底层原理**
   - 单线程模型的优势和局限
   - I/O 多路复用的原理
   - 数据结构的时间复杂度

2. **合理使用**
   - 根据场景选择数据类型
   - 避免大 key 和慢查询
   - 使用 Pipeline 和 Lua 脚本

3. **性能优化**
   - 客户端连接池
   - 批量操作
   - 读写分离

4. **高可用**
   - 主从复制
   - 哨兵模式
   - 集群模式

5. **监控运维**
   - 实时监控关键指标
   - 定期性能测试
   - 及时发现和解决问题

### 5.3 最佳实践

```java
/**
 * Redis 最佳实践示例
 */
@Service
public class RedisBestPractice {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 1. 使用连接池
     */
    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(200);
        config.setMaxIdle(50);
        config.setMinIdle(10);
        return config;
    }
    
    /**
     * 2. 使用 Pipeline 批量操作
     */
    public void batchSet(Map<String, String> data) {
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) {
                data.forEach((k, v) -> 
                    connection.set(k.getBytes(), v.getBytes())
                );
                return null;
            }
        });
    }
    
    /**
     * 3. 使用 Lua 脚本保证原子性
     */
    public boolean tryLock(String key, String value, long expireTime) {
        String script = 
            "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then " +
            "    redis.call('expire', KEYS[1], ARGV[2]) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";
        
        return redisTemplate.execute(
            new DefaultRedisScript<>(script, Boolean.class),
            Collections.singletonList(key),
            value,
            String.valueOf(expireTime)
        );
    }
    
    /**
     * 4. 设置合理的过期时间
     */
    public void setWithExpire(String key, Object value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }
    
    /**
     * 5. 使用 SCAN 代替 KEYS
     */
    public Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();
            
            Cursor<byte[]> cursor = connection.scan(options);
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
            return null;
        });
        return keys;
    }
}
```

**Redis 之所以快，是多个层面优化的结果**：
- ✅ 架构设计：纯内存 + 单线程 + I/O 多路复用
- ✅ 数据结构：高效的数据结构和编码优化
- ✅ 协议设计：简单高效的 RESP 协议
- ✅ 网络优化：Pipeline、零拷贝
- ✅ 持久化：异步持久化，不阻塞主线程

作为架构师，需要：
- 🎯 深入理解底层原理
- 🎯 根据场景合理使用
- 🎯 持续监控和优化
- 🎯 保证高可用和高性能
