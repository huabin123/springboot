# Redis 数据结构深度解析

> **学习目标**：深入理解 Redis 五大数据结构的底层实现原理、时间复杂度、适用场景及最佳实践。

## 一、Redis 对象系统（RedisObject）

### 1.1 为什么需要 RedisObject？

Redis 并没有直接使用 SDS、ziplist、hashtable 等数据结构来存储键和值，而是通过一个统一的 `redisObject` 结构体来封装所有的键和值。

**设计目的：**
1. **类型检查**：在执行命令前检查类型是否匹配
2. **多态**：同一类型可以有多种底层实现
3. **内存回收**：引用计数机制
4. **内存淘汰**：记录访问时间，支持 LRU/LFU 策略

### 1.2 RedisObject 结构

```c
typedef struct redisObject {
    unsigned type:4;        // 对象类型（4位，16种类型）
    unsigned encoding:4;    // 编码方式（4位，16种编码）
    unsigned lru:24;        // LRU时间或LFU计数器（24位）
    int refcount;           // 引用计数
    void *ptr;              // 指向底层数据结构的指针
} robj;
```

**字段详解：**

| 字段 | 占用 | 说明 | 示例 |
|------|------|------|------|
| `type` | 4位 | 对象类型 | STRING, LIST, HASH, SET, ZSET |
| `encoding` | 4位 | 底层编码 | RAW, EMBSTR, INT, ZIPLIST, HT, INTSET, SKIPLIST |
| `lru` | 24位 | 访问时间/频率 | 用于内存淘汰策略 |
| `refcount` | 32位 | 引用计数 | 用于内存回收 |
| `ptr` | 64位 | 数据指针 | 指向实际数据结构 |

**内存占用：**
```
4位 + 4位 + 24位 + 32位 + 64位 = 128位 = 16字节
```

### 1.3 Redis Key 的数据结构

**Redis 的键（Key）始终是字符串对象**，底层使用 **SDS (Simple Dynamic String)**。

```bash
# 查看 Key 的编码
127.0.0.1:6379> SET name "Redis"
OK
127.0.0.1:6379> OBJECT ENCODING name
"embstr"

# 短键（≤44字节）：embstr 编码
127.0.0.1:6379> SET short_key "hello"
OK
127.0.0.1:6379> OBJECT ENCODING short_key
"embstr"

# 长键（>44字节）：raw 编码
127.0.0.1:6379> SET long_key "this is a very long key name that exceeds 44 bytes..."
OK
127.0.0.1:6379> OBJECT ENCODING long_key
"raw"
```

**Key 的内存布局：**

```
短键（embstr）：
┌─────────────┬─────────────┐
│ redisObject │     SDS     │
└─────────────┴─────────────┘
    16字节         N字节
    
连续内存分配，减少内存碎片

长键（raw）：
┌─────────────┐      ┌─────────────┐
│ redisObject │ ───→ │     SDS     │
└─────────────┘      └─────────────┘
    16字节               N字节
    
分开分配，ptr 指针指向 SDS
```

### 1.4 Redis Value 的数据结构

**Value 可以是五种数据类型之一**，Redis 会根据数据特征自动选择最优编码。

```bash
# String 类型的三种编码
127.0.0.1:6379> SET count 100
OK
127.0.0.1:6379> OBJECT ENCODING count
"int"                              # 整数编码

127.0.0.1:6379> SET name "Redis"
OK
127.0.0.1:6379> OBJECT ENCODING name
"embstr"                           # 短字符串编码

127.0.0.1:6379> SET description "very long text..."
OK
127.0.0.1:6379> OBJECT ENCODING description
"raw"                              # 长字符串编码
```

**编码转换示例：**

```bash
# Hash 的编码转换
127.0.0.1:6379> HSET user:1 name "Alice" age "25"
OK
127.0.0.1:6379> OBJECT ENCODING user:1
"ziplist"                          # 字段少，使用 ziplist

# 添加大量字段后
127.0.0.1:6379> HSET user:1 field1 "value1" field2 "value2" ... field600 "value600"
OK
127.0.0.1:6379> OBJECT ENCODING user:1
"hashtable"                        # 字段多，转换为 hashtable
```

### 1.5 类型与编码的对应关系

| 对象类型 | 可用编码 | 转换条件 |
|---------|---------|---------|
| **STRING** | `int` | 整数值 |
| | `embstr` | 字符串长度 ≤ 44字节 |
| | `raw` | 字符串长度 > 44字节 |
| **LIST** | `quicklist` | Redis 3.2+ 统一使用 |
| **HASH** | `ziplist` | 字段数 < 512 且 所有值 < 64字节 |
| | `hashtable` | 超过阈值 |
| **SET** | `intset` | 所有元素都是整数 且 元素数 < 512 |
| | `hashtable` | 超过阈值或有非整数元素 |
| **ZSET** | `ziplist` | 元素数 < 128 且 所有值 < 64字节 |
| | `skiplist` | 超过阈值 |

### 1.6 查看对象信息的命令

```bash
# OBJECT ENCODING：查看编码方式
127.0.0.1:6379> OBJECT ENCODING mykey

# OBJECT REFCOUNT：查看引用计数
127.0.0.1:6379> OBJECT REFCOUNT mykey

# OBJECT IDLETIME：查看空闲时间（秒）
127.0.0.1:6379> OBJECT IDLETIME mykey

# TYPE：查看对象类型
127.0.0.1:6379> TYPE mykey

# MEMORY USAGE：查看内存占用（字节）
127.0.0.1:6379> MEMORY USAGE mykey
```

### 1.7 实战示例：观察编码转换

```java
@Service
public class EncodingDemoService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 演示 Hash 的编码转换
     */
    public void demonstrateHashEncoding() {
        String key = "demo:hash";
        
        // 1. 添加少量字段（ziplist）
        for (int i = 1; i <= 10; i++) {
            redisTemplate.opsForHash().put(key, "field" + i, "value" + i);
        }
        System.out.println("10个字段，编码: ziplist");
        
        // 2. 添加大量字段（转换为 hashtable）
        for (int i = 11; i <= 600; i++) {
            redisTemplate.opsForHash().put(key, "field" + i, "value" + i);
        }
        System.out.println("600个字段，编码: hashtable");
        
        // 使用 redis-cli 查看：
        // OBJECT ENCODING demo:hash
    }
}
```

### 1.8 全局哈希表（Global Hash Table）

Redis 使用一个全局哈希表来存储所有的键值对，这是 Redis 数据库的核心数据结构。

#### 1.8.1 全局哈希表结构

```
                              全局哈希表
                                 │
                    ┌────────────┴────────────┐
                    │                         │
                哈希桶数组                  entry
         ┌───┬───┬───┬───┬───┬───┐          结构
         │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │      ┌──────────┐
         └─┬─┴───┴─┬─┴───┴─┬─┴───┘      │  *key    │ ──→ String
           │       │       │            │  *value  │ ──→ String / List / Hash
           │       │       │            └──────────┘      Set / Sorted Set
           ↓       ↓       ↓
        entry   entry   entry
```

**核心组成：**

1. **哈希桶（Hash Bucket）**：数组结构，默认大小为 4，会动态扩容
2. **Entry 节点**：存储键值对的节点，包含：
   - `*key`：指向键对象（String 类型）
   - `*value`：指向值对象（五种数据类型之一）
   - `*next`：指向下一个 entry（解决哈希冲突）

#### 1.8.2 哈希冲突解决：链地址法

```c
typedef struct dictEntry {
    void *key;              // 键
    union {
        void *val;          // 值
        uint64_t u64;
        int64_t s64;
        double d;
    } v;
    struct dictEntry *next; // 指向下一个节点（链表）
} dictEntry;
```

**冲突解决示例：**

```
假设 key1 和 key2 的哈希值都映射到桶 3：

哈希桶数组
┌───┬───┬───┬───┬───┬───┐
│ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │
└───┴───┴─┬─┴───┴───┴───┘
          │
          ↓
      ┌─────────┐      ┌─────────┐
      │ key1    │ ───→ │ key2    │ ───→ NULL
      │ value1  │      │ value2  │
      │ *next   │      │ *next   │
      └─────────┘      └─────────┘
      
链表头插法：新节点插入到链表头部
```

#### 1.8.3 渐进式 Rehash

当哈希表负载因子过高时，Redis 会进行扩容，但不是一次性完成，而是渐进式地迁移数据。

**Rehash 触发条件：**

```c
// 负载因子 = 已使用节点数 / 哈希桶数量
load_factor = ht[0].used / ht[0].size;

// 扩容条件
if (没有执行 BGSAVE/BGREWRITEAOF && load_factor >= 1) {
    执行扩容;
}
if (正在执行 BGSAVE/BGREWRITEAOF && load_factor >= 5) {
    执行扩容;
}

// 缩容条件
if (load_factor < 0.1) {
    执行缩容;
}
```

**渐进式 Rehash 过程：**

```
步骤 1：分配新哈希表 ht[1]，大小为 ht[0] 的 2 倍

    ht[0]（旧表）              ht[1]（新表）
    ┌───┬───┬───┬───┐          ┌───┬───┬───┬───┬───┬───┬───┬───┐
    │ 1 │ 2 │ 3 │ 4 │          │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ 8 │
    └─┬─┴─┬─┴─┬─┴─┬─┘          └───┴───┴───┴───┴───┴───┴───┴───┘
      │   │   │   │
    entry entry entry


步骤 2：每次对哈希表的操作，顺带迁移一个桶的数据

    ht[0]（旧表）              ht[1]（新表）
    ┌───┬───┬───┬───┐          ┌───┬───┬───┬───┬───┬───┬───┬───┐
    │   │ 2 │ 3 │ 4 │          │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ 8 │
    └───┴─┬─┴─┬─┴─┬─┘          └─┬─┴───┴───┴───┴───┴───┴───┴───┘
          │   │   │              │
        entry entry            entry（从 ht[0] 迁移过来）


步骤 3：全部迁移完成后，释放 ht[0]，ht[1] 变为 ht[0]

    ht[0]（新表）
    ┌───┬───┬───┬───┬───┬───┬───┬───┐
    │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ 8 │
    └─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┘
      │   │   │   │   │   │   │   │
    entry entry entry entry ...
```

**渐进式 Rehash 的优势：**

1. **避免阻塞**：不会一次性迁移所有数据，分摊到多次操作中
2. **平滑扩容**：对性能影响小
3. **查询策略**：Rehash 期间，先查 ht[0]，再查 ht[1]
4. **插入策略**：Rehash 期间，新数据只插入 ht[1]

#### 1.8.4 全局哈希表的操作复杂度

| 操作 | 平均时间复杂度 | 最坏时间复杂度 | 说明 |
|------|---------------|---------------|------|
| **GET** | O(1) | O(N) | N 为链表长度（冲突严重时） |
| **SET** | O(1) | O(N) | 可能触发 Rehash |
| **DEL** | O(1) | O(N) | 删除键 |
| **EXISTS** | O(1) | O(N) | 检查键是否存在 |
| **KEYS** | O(N) | O(N) | 遍历所有键，生产环境禁用 |
| **SCAN** | O(1) | O(N) | 渐进式遍历，推荐使用 |

#### 1.8.5 实战示例：观察 Rehash

```bash
# 查看数据库信息
127.0.0.1:6379> INFO stats
# Keyspace
db0:keys=1000,expires=0,avg_ttl=0

# 查看内存使用
127.0.0.1:6379> INFO memory
used_memory:1048576
used_memory_human:1.00M

# 使用 DEBUG 命令查看哈希表状态（仅开发环境）
127.0.0.1:6379> DEBUG OBJECT mykey
Value at:0x7f8a1c0a3c00 refcount:1 encoding:raw serializedlength:5 lru:123456 lru_seconds_idle:10
```

**Java 代码示例：**

```java
@Service
public class GlobalHashTableDemo {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 演示大量插入导致 Rehash
     */
    public void demonstrateRehash() {
        String keyPrefix = "test:key:";
        
        // 插入 10000 个键
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            redisTemplate.opsForValue().set(keyPrefix + i, "value" + i);
            
            // 每 1000 个键打印一次耗时
            if (i > 0 && i % 1000 == 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("插入 " + i + " 个键，耗时: " + elapsed + "ms");
            }
        }
        
        // 观察：Rehash 期间，插入操作会有轻微的性能波动
    }
    
    /**
     * 使用 SCAN 代替 KEYS（生产环境最佳实践）
     */
    public Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)  // 每次扫描 100 个
                .build();
        
        Cursor<String> cursor = redisTemplate.scan(options);
        while (cursor.hasNext()) {
            keys.add(cursor.next());
        }
        
        return keys;
    }
}
```

#### 1.8.6 全局哈希表最佳实践

1. **避免使用 KEYS 命令**
   ```bash
   # ❌ 错误：会阻塞 Redis
   KEYS user:*
   
   # ✅ 正确：使用 SCAN
   SCAN 0 MATCH user:* COUNT 100
   ```

2. **合理设计键名**
   ```bash
   # ✅ 好的键名：层次清晰，便于管理
   user:1001:profile
   user:1001:orders
   product:2001:info
   
   # ❌ 不好的键名：难以维护
   u1001p
   p2001i
   ```

3. **监控哈希表状态**
   ```bash
   # 定期检查键空间
   INFO keyspace
   
   # 检查内存使用
   INFO memory
   
   # 检查慢查询
   SLOWLOG GET 10
   ```

4. **控制键的数量**
   - 单个 Redis 实例建议不超过 **1 亿个键**
   - 超过后考虑分片（Redis Cluster）

---

## 二、数据结构全景图

```
Redis 数据结构体系
│
├── 对象层（RedisObject）
│   ├── type：对象类型
│   ├── encoding：编码方式
│   ├── lru：访问时间
│   ├── refcount：引用计数
│   └── ptr：数据指针
│
├── String（字符串）
│   ├── 底层实现：SDS（Simple Dynamic String）
│   ├── 编码：int / embstr / raw
│   └── 应用：缓存、计数器、分布式锁
│
├── Hash（哈希）
│   ├── 底层实现：ziplist（压缩列表）/ hashtable（哈希表）
│   └── 应用：对象存储、购物车
│
├── List（列表）
│   ├── 底层实现：quicklist（快速列表 = ziplist + linkedlist）
│   └── 应用：消息队列、最新列表、栈/队列
│
├── Set（集合）
│   ├── 底层实现：intset（整数集合）/ hashtable
│   └── 应用：去重、共同好友、标签系统
│
└── ZSet（有序集合）
    ├── 底层实现：ziplist / skiplist（跳表）+ hashtable
    └── 应用：排行榜、延时队列、范围查询
```

---

## 二、String - 字符串

### 2.1 底层实现：SDS（Simple Dynamic String）

**为什么不用 C 语言原生字符串？**

```c
// C 语言字符串的问题
char *str = "hello";
// 1. 获取长度需要遍历：O(N)
// 2. 不能存储二进制数据（遇到 \0 就结束）
// 3. 容易缓冲区溢出

// Redis SDS 结构
struct sdshdr {
    int len;        // 已使用长度
    int free;       // 剩余可用长度
    char buf[];     // 实际存储数据
};
```

**SDS 的优势：**

| 特性 | C 字符串 | SDS | 优势 |
|------|---------|-----|------|
| 获取长度 | O(N) | O(1) | len 字段直接记录 |
| 二进制安全 | ❌ | ✅ | 不依赖 \0 判断结束 |
| 缓冲区溢出 | 容易 | 不会 | 自动扩容 |
| 内存重分配 | 频繁 | 减少 | 空间预分配 + 惰性释放 |

**空间预分配策略：**

```c
// 当 SDS 需要扩容时
if (新长度 < 1MB) {
    分配空间 = 新长度 * 2;  // 预留一倍空间
} else {
    分配空间 = 新长度 + 1MB; // 预留 1MB
}
```

### 2.2 String 的三种编码方式

```bash
# 1. int 编码：存储整数
127.0.0.1:6379> SET count 100
OK
127.0.0.1:6379> OBJECT ENCODING count
"int"

# 2. embstr 编码：短字符串（≤44字节）
127.0.0.1:6379> SET name "Redis"
OK
127.0.0.1:6379> OBJECT ENCODING name
"embstr"

# 3. raw 编码：长字符串（>44字节）
127.0.0.1:6379> SET description "This is a very long string..."
OK
127.0.0.1:6379> OBJECT ENCODING description
"raw"
```

**为什么 44 字节是分界线？**

```
Redis 对象头：16字节
SDS 头：3字节（len + free + 1字节类型）
字符串内容：44字节
结尾 \0：1字节
总计：64字节（正好一个缓存行）
```

### 2.3 String 常用命令与时间复杂度

| 命令 | 时间复杂度 | 说明 | 应用场景 |
|------|-----------|------|----------|
| SET | O(1) | 设置值 | 缓存对象 |
| GET | O(1) | 获取值 | 查询缓存 |
| INCR | O(1) | 原子自增 | 计数器、ID生成 |
| DECR | O(1) | 原子自减 | 库存扣减 |
| APPEND | O(1) | 追加字符串 | 日志追加 |
| GETRANGE | O(N) | 获取子串 | 分页查询 |
| SETEX | O(1) | 设置值+过期时间 | Session |

### 2.4 实战案例

#### 案例 1：分布式全局ID生成器

```java
@Service
public class IdGenerator {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 生成全局唯一ID
     * 
     * 格式：时间戳(41位) + 机器ID(10位) + 序列号(12位)
     * 类似 Snowflake 算法
     */
    public long generateId(String businessKey) {
        // 1. 获取当前时间戳
        long timestamp = System.currentTimeMillis();
        
        // 2. 从 Redis 获取自增序列号
        String key = "id:generator:" + businessKey + ":" + timestamp;
        Long sequence = redisTemplate.opsForValue().increment(key);
        
        // 3. 设置过期时间（1秒后过期，避免内存泄漏）
        redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        
        // 4. 组装ID（简化版，实际需要加机器ID）
        // 时间戳左移12位 + 序列号
        return (timestamp << 12) | (sequence & 0xFFF);
    }
}
```

**为什么用 Redis 而不是数据库自增？**

| 方案 | QPS | 可用性 | 性能瓶颈 |
|------|-----|--------|----------|
| MySQL 自增 | ~1000 | 单点故障 | 磁盘IO |
| Redis INCR | ~10万 | 主从高可用 | 网络IO |

#### 案例 2：接口限流（计数器方案）

```java
@Component
public class SimpleRateLimiter {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 简单计数器限流
     * 
     * 限制：每个IP每秒最多访问10次
     */
    public boolean tryAcquire(String ip) {
        String key = "rate:limit:" + ip + ":" + System.currentTimeMillis() / 1000;
        
        // 原子自增
        Long count = redisTemplate.opsForValue().increment(key);
        
        if (count == null) {
            return false;
        }
        
        // 第一次访问，设置过期时间
        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }
        
        return count <= 10;
    }
}
```

#### 案例 3：分布式锁

```java
@Component
public class RedisDistributedLock {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 获取锁
     * 
     * 使用 SET NX EX 原子命令
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
     * 释放锁
     * 
     * 使用 Lua 脚本保证原子性
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
}
```

---

## 三、Hash - 哈希表

### 3.1 底层实现：两种编码方式

Redis Hash 根据数据量和数据大小，会自动选择两种不同的底层实现：

#### 3.1.1 编码选择策略

```bash
# 1. ziplist（压缩列表）：节省内存
# 条件：字段数 < 512 且 所有值 < 64字节
127.0.0.1:6379> HSET user:1 name "Alice" age "25"
(integer) 2
127.0.0.1:6379> OBJECT ENCODING user:1
"ziplist"

# 2. hashtable（哈希表）：性能优先
# 条件：字段数 >= 512 或 某个值 >= 64字节
127.0.0.1:6379> HSET user:2 name "Bob" description "very long text..."
(integer) 2
127.0.0.1:6379> OBJECT ENCODING user:2
"hashtable"
```

**编码转换触发条件：**

| 条件 | ziplist | hashtable |
|------|---------|-----------|  
| 字段数量 | < 512 | ≥ 512 |
| 单个值大小 | < 64 字节 | ≥ 64 字节 |
| 内存占用 | 低 | 高 |
| 查询性能 | O(N) | O(1) |
| 适用场景 | 小对象存储 | 大对象存储 |

**重要特性：**
- 编码转换是**单向的**：ziplist → hashtable（不可逆）
- 一旦转换为 hashtable，即使删除数据也不会转回 ziplist

**ziplist 结构：**

```
┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐
│zlbytes│zltail│zllen│ entry1│ entry2│ ... │zlend│
└─────┴─────┴─────┴─────┴─────┴─────┴─────┘
         ↑                ↑
         |                |
    列表尾偏移        entry数量

每个 entry：
┌────────┬────────┬────────┐
│previous│encoding│ content│
└────────┴────────┴────────┘
```

**为什么用 ziplist？**

```
假设存储 100 个字段的用户对象：

hashtable 方式：
- 每个字段需要：dictEntry(24字节) + key(SDS) + value(SDS)
- 总内存：约 100 * 50 = 5000 字节

ziplist 方式：
- 连续内存存储，无指针开销
- 总内存：约 100 * 20 = 2000 字节

节省：60% 内存
```

### 3.2 Hash 常用命令

| 命令 | 时间复杂度 | 说明 | 应用场景 |
|------|-----------|------|----------|
| HSET | O(1) | 设置字段 | 存储对象 |
| HGET | O(1) | 获取字段 | 查询对象字段 |
| HMSET | O(N) | 批量设置 | 批量更新 |
| HGETALL | O(N) | 获取所有字段 | 获取完整对象 |
| HINCRBY | O(1) | 字段自增 | 统计计数 |
| HDEL | O(N) | 删除字段 | 删除属性 |

### 3.3 实战案例

#### 案例 1：购物车

```java
@Service
public class ShoppingCartService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 添加商品到购物车
     * 
     * Key: cart:userId
     * Field: productId
     * Value: quantity
     */
    public void addToCart(Long userId, Long productId, int quantity) {
        String key = "cart:" + userId;
        
        // 增加商品数量（如果不存在则设置为quantity）
        redisTemplate.opsForHash().increment(
            key,
            productId.toString(),
            quantity
        );
        
        // 设置过期时间（30天）
        redisTemplate.expire(key, 30, TimeUnit.DAYS);
    }
    
    /**
     * 获取购物车
     */
    public Map<Long, Integer> getCart(Long userId) {
        String key = "cart:" + userId;
        Map<Object, Object> cart = redisTemplate.opsForHash().entries(key);
        
        Map<Long, Integer> result = new HashMap<>();
        cart.forEach((k, v) -> {
            result.put(Long.parseLong(k.toString()), Integer.parseInt(v.toString()));
        });
        
        return result;
    }
    
    /**
     * 删除商品
     */
    public void removeFromCart(Long userId, Long productId) {
        String key = "cart:" + userId;
        redisTemplate.opsForHash().delete(key, productId.toString());
    }
    
    /**
     * 清空购物车
     */
    public void clearCart(Long userId) {
        String key = "cart:" + userId;
        redisTemplate.delete(key);
    }
}
```

**为什么用 Hash 而不是 String？**

```java
// 方案1：String 存储（不推荐）
// Key: cart:userId:productId
// Value: quantity
// 问题：
// 1. 获取整个购物车需要多次网络请求
// 2. Key 数量多，内存开销大

// 方案2：String 存储整个对象（不推荐）
// Key: cart:userId
// Value: JSON序列化的购物车
// 问题：
// 1. 修改单个商品需要反序列化整个对象
// 2. 并发修改容易丢失数据

// 方案3：Hash 存储（推荐）✅
// Key: cart:userId
// Field: productId
// Value: quantity
// 优势：
// 1. 一次网络请求获取整个购物车
// 2. 可以单独修改某个商品
// 3. 内存占用小
```

#### 案例 2：对象缓存

```java
@Service
public class UserCacheService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 缓存用户对象
     * 
     * 优势：可以单独更新某个字段，不需要重新缓存整个对象
     */
    public User getUserById(Long userId) {
        String key = "user:hash:" + userId;
        
        // 1. 尝试从缓存获取
        Map<Object, Object> userMap = redisTemplate.opsForHash().entries(key);
        
        if (!userMap.isEmpty()) {
            return convertToUser(userMap);
        }
        
        // 2. 缓存未命中，查询数据库
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        
        // 3. 写入缓存
        Map<String, String> fields = new HashMap<>();
        fields.put("id", user.getId().toString());
        fields.put("name", user.getName());
        fields.put("email", user.getEmail());
        fields.put("age", user.getAge().toString());
        
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        return user;
    }
    
    /**
     * 更新用户邮箱
     * 
     * 只需要更新一个字段，不需要重新缓存整个对象
     */
    public void updateEmail(Long userId, String newEmail) {
        // 1. 更新数据库
        userMapper.updateEmail(userId, newEmail);
        
        // 2. 更新缓存中的字段
        String key = "user:hash:" + userId;
        redisTemplate.opsForHash().put(key, "email", newEmail);
    }
    
    private User convertToUser(Map<Object, Object> map) {
        User user = new User();
        user.setId(Long.parseLong(map.get("id").toString()));
        user.setName(map.get("name").toString());
        user.setEmail(map.get("email").toString());
        user.setAge(Integer.parseInt(map.get("age").toString()));
        return user;
    }
}
```

---

## 四、List - 列表

### 4.1 底层实现：quicklist

**演进历史：**

```
Redis 3.0 之前：
- 短列表：ziplist（压缩列表）
- 长列表：linkedlist（双向链表）

Redis 3.2 之后：
- 统一使用 quicklist（快速列表）
- quicklist = ziplist + linkedlist 的结合
```

**quicklist 结构：**

```
┌────────────────────────────────────────────┐
│              quicklist                      │
├────────────────────────────────────────────┤
│  ┌──────┐   ┌──────┐   ┌──────┐           │
│  │ node │ → │ node │ → │ node │           │
│  └──────┘   └──────┘   └──────┘           │
│     ↓          ↓          ↓                │
│  ziplist   ziplist   ziplist               │
└────────────────────────────────────────────┘

优势：
1. 兼顾内存和性能
2. 每个 node 存储一个 ziplist
3. 避免 linkedlist 的指针开销
4. 避免 ziplist 过长导致的性能问题
```

### 4.2 List 常用命令

| 命令 | 时间复杂度 | 说明 | 应用场景 |
|------|-----------|------|----------|
| LPUSH | O(1) | 左侧插入 | 消息队列 |
| RPUSH | O(1) | 右侧插入 | 消息队列 |
| LPOP | O(1) | 左侧弹出 | 队列消费 |
| RPOP | O(1) | 右侧弹出 | 栈操作 |
| LRANGE | O(N) | 范围查询 | 分页查询 |
| LINDEX | O(N) | 按索引查询 | 随机访问 |
| LTRIM | O(N) | 修剪列表 | 保留最新N条 |

### 4.3 实战案例

#### 案例 1：最新动态列表

```java
@Service
public class FeedService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final int MAX_FEED_SIZE = 100; // 最多保留100条
    
    /**
     * 发布动态
     */
    public void publishFeed(Long userId, String feedContent) {
        String key = "user:feeds:" + userId;
        
        // 1. 左侧插入（最新的在最前面）
        redisTemplate.opsForList().leftPush(key, feedContent);
        
        // 2. 保留最新的100条
        redisTemplate.opsForList().trim(key, 0, MAX_FEED_SIZE - 1);
        
        // 3. 设置过期时间
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }
    
    /**
     * 获取最新动态（分页）
     */
    public List<String> getFeeds(Long userId, int page, int size) {
        String key = "user:feeds:" + userId;
        
        long start = (long) (page - 1) * size;
        long end = start + size - 1;
        
        return redisTemplate.opsForList().range(key, start, end);
    }
    
    /**
     * 获取动态总数
     */
    public long getFeedCount(Long userId) {
        String key = "user:feeds:" + userId;
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0 : size;
    }
}
```

#### 案例 2：简单消息队列

```java
@Service
public class SimpleMessageQueue {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 生产者：发送消息
     */
    public void sendMessage(String queue, String message) {
        redisTemplate.opsForList().rightPush("queue:" + queue, message);
    }
    
    /**
     * 消费者：阻塞获取消息
     * 
     * BLPOP：阻塞式左侧弹出
     * 如果队列为空，会阻塞等待，直到有新消息或超时
     */
    public String receiveMessage(String queue, long timeout) {
        List<String> result = redisTemplate.opsForList().leftPop(
            "queue:" + queue,
            timeout,
            TimeUnit.SECONDS
        );
        
        return result != null && !result.isEmpty() ? result.get(1) : null;
    }
}
```

**List 作为消息队列的问题：**

```
优点：
✅ 实现简单
✅ 支持阻塞消费（BLPOP）

缺点：
❌ 没有 ACK 机制（消息可能丢失）
❌ 不支持消息重试
❌ 不支持消息持久化保证

建议：
- 简单场景可以用 List
- 生产环境建议用 Stream 或专业 MQ（RabbitMQ、Kafka）
```

---

## 五、Set - 集合

### 5.1 底层实现

**两种编码方式：**

```bash
# 1. intset（整数集合）：节省内存
# 条件：所有元素都是整数 且 元素数量 < 512
127.0.0.1:6379> SADD numbers 1 2 3 4 5
127.0.0.1:6379> OBJECT ENCODING numbers
"intset"

# 2. hashtable（哈希表）
# 条件：有非整数元素 或 元素数量 >= 512
127.0.0.1:6379> SADD tags "redis" "cache" "nosql"
127.0.0.1:6379> OBJECT ENCODING tags
"hashtable"
```

**intset 结构：**

```c
typedef struct intset {
    uint32_t encoding; // 编码方式：int16/int32/int64
    uint32_t length;   // 元素数量
    int8_t contents[]; // 实际存储（有序数组）
} intset;

特点：
1. 有序存储（方便二分查找）
2. 自动升级编码（int16 → int32 → int64）
3. 内存紧凑
```

### 5.2 Set 常用命令

| 命令 | 时间复杂度 | 说明 | 应用场景 |
|------|-----------|------|----------|
| SADD | O(1) | 添加元素 | 标签系统 |
| SREM | O(1) | 删除元素 | 取消关注 |
| SISMEMBER | O(1) | 判断存在 | 检查权限 |
| SCARD | O(1) | 获取数量 | 统计粉丝数 |
| SMEMBERS | O(N) | 获取所有元素 | 获取标签列表 |
| SINTER | O(N*M) | 交集 | 共同好友 |
| SUNION | O(N) | 并集 | 合并标签 |
| SDIFF | O(N) | 差集 | 可能认识的人 |

### 5.3 实战案例

#### 案例 1：标签系统

```java
@Service
public class TagService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 给文章添加标签
     */
    public void addTags(Long articleId, String... tags) {
        String key = "article:tags:" + articleId;
        redisTemplate.opsForSet().add(key, tags);
    }
    
    /**
     * 获取文章的所有标签
     */
    public Set<String> getTags(Long articleId) {
        String key = "article:tags:" + articleId;
        return redisTemplate.opsForSet().members(key);
    }
    
    /**
     * 查找有相同标签的文章（推荐系统）
     */
    public Set<String> findSimilarArticles(Long articleId) {
        String key = "article:tags:" + articleId;
        
        // 1. 获取当前文章的标签
        Set<String> tags = redisTemplate.opsForSet().members(key);
        if (tags == null || tags.isEmpty()) {
            return Collections.emptySet();
        }
        
        // 2. 遍历每个标签，找到包含该标签的文章
        Set<String> similarArticles = new HashSet<>();
        for (String tag : tags) {
            String tagKey = "tag:articles:" + tag;
            Set<String> articles = redisTemplate.opsForSet().members(tagKey);
            if (articles != null) {
                similarArticles.addAll(articles);
            }
        }
        
        // 3. 移除当前文章
        similarArticles.remove(articleId.toString());
        
        return similarArticles;
    }
}
```

#### 案例 2：共同好友

```java
@Service
public class FriendService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 添加好友
     */
    public void follow(Long userId, Long friendId) {
        String key = "user:friends:" + userId;
        redisTemplate.opsForSet().add(key, friendId.toString());
    }
    
    /**
     * 取消关注
     */
    public void unfollow(Long userId, Long friendId) {
        String key = "user:friends:" + userId;
        redisTemplate.opsForSet().remove(key, friendId.toString());
    }
    
    /**
     * 获取共同好友
     * 
     * 使用 SINTER 命令求交集
     */
    public Set<String> getCommonFriends(Long userId1, Long userId2) {
        String key1 = "user:friends:" + userId1;
        String key2 = "user:friends:" + userId2;
        
        return redisTemplate.opsForSet().intersect(key1, key2);
    }
    
    /**
     * 可能认识的人
     * 
     * 逻辑：好友的好友 - 已有好友 - 自己
     */
    public Set<String> getSuggestedFriends(Long userId) {
        String userKey = "user:friends:" + userId;
        Set<String> myFriends = redisTemplate.opsForSet().members(userKey);
        
        if (myFriends == null || myFriends.isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<String> suggested = new HashSet<>();
        
        // 遍历每个好友，获取他们的好友
        for (String friendId : myFriends) {
            String friendKey = "user:friends:" + friendId;
            Set<String> friendsOfFriend = redisTemplate.opsForSet().members(friendKey);
            
            if (friendsOfFriend != null) {
                suggested.addAll(friendsOfFriend);
            }
        }
        
        // 移除已有好友和自己
        suggested.removeAll(myFriends);
        suggested.remove(userId.toString());
        
        return suggested;
    }
}
```

---

## 六、ZSet - 有序集合

### 6.1 底层实现：两种编码方式

Redis ZSet 根据数据量和数据大小，会自动选择两种不同的底层实现：

#### 6.1.1 编码选择策略

```bash
# 1. ziplist（压缩列表）：节省内存
# 条件：元素数 < 128 且 所有值 < 64字节
127.0.0.1:6379> ZADD leaderboard 100 "Alice" 90 "Bob"
(integer) 2
127.0.0.1:6379> OBJECT ENCODING leaderboard
"ziplist"

# 2. skiplist（跳表）+ hashtable：性能优先
# 条件：元素数 >= 128 或 某个值 >= 64字节
127.0.0.1:6379> ZADD large:leaderboard ...（添加大量元素）
127.0.0.1:6379> OBJECT ENCODING large:leaderboard
"skiplist"
```

**编码转换触发条件：**

| 条件 | ziplist | skiplist + hashtable |
|------|---------|---------------------|
| 元素数量 | < 128 | ≥ 128 |
| 单个值大小 | < 64 字节 | ≥ 64 字节 |
| 内存占用 | 低 | 高 |
| 查询性能 | O(N) | O(log N) |
| 适用场景 | 小排行榜 | 大排行榜 |

**配置参数：**

```bash
# redis.conf
zset-max-ziplist-entries 128  # 元素数阈值
zset-max-ziplist-value 64     # 值大小阈值
```

---

### 6.2 跳表（Skip List）深度解析

#### 6.2.1 为什么需要跳表？

**问题：有序链表的查找效率低**

```
有序链表查找元素 5：
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10
↑                   ↑
从头开始            找到（需要遍历 5 次）

时间复杂度：O(N)
```

**解决方案：跳表 = 有序链表 + 多级索引**

```
Level 3:  1 -----------------------> 7 -----------------------> 13
Level 2:  1 --------> 4 -----------> 7 -----------> 10 -------> 13
Level 1:  1 --> 3 --> 4 --> 5 --> 6 --> 7 --> 8 --> 9 --> 10 --> 11 --> 12 --> 13
Level 0:  1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13

查找元素 8：
1. Level 3: 1 → 7（7 < 8，继续）→ 13（13 > 8，下降）
2. Level 2: 7 → 10（10 > 8，下降）
3. Level 1: 7 → 8（找到！）

只需要 3 次比较，而不是 8 次！
时间复杂度：O(log N)
```

#### 6.2.2 跳表 vs 平衡树

**为什么 Redis 选择跳表而不是红黑树或 AVL 树？**

| 特性 | 跳表 | 红黑树 | AVL 树 | Redis 选择跳表的原因 |
|------|------|--------|--------|---------------------|
| **实现复杂度** | 简单（~200行） | 复杂（~500行） | 复杂（~600行） | 代码易维护 |
| **插入/删除** | O(log N) | O(log N) | O(log N) | 性能相当 |
| **查找** | O(log N) | O(log N) | O(log N) | 性能相当 |
| **范围查询** | O(log N + M) | O(log N + M) | O(log N + M) | 性能相当，但跳表更直观 |
| **内存占用** | 1.33N | 1N | 1N | 可接受（多 33%） |
| **并发友好** | ✅ 天然支持无锁 | ❌ 需要复杂的锁 | ❌ 需要复杂的锁 | 跳表更适合无锁化 |
| **实现难度** | 插入/删除无需旋转 | 需要复杂的旋转 | 需要复杂的旋转 | 跳表更简单 |

**Redis 作者 Antirez 的原话：**

> "There are a few reasons:
> 1. They are not very memory intensive. It's up to you basically. Changing parameters about the probability of a node to have a given number of levels will make then less memory intensive than btrees.
> 2. A sorted set is often target of many ZRANGE or ZREVRANGE operations, that is, traversing the skip list as a linked list. With this operation the cache locality of skip lists is at least as good as with other kind of balanced trees.
> 3. They are simpler to implement, debug, and so forth. For instance thanks to the skip list simplicity I received a patch (already in Redis master) with augmented skip lists implementing ZRANK in O(log(N)). It required little changes to the code."

#### 6.2.3 跳表的数据结构

**Redis 跳表节点定义：**

```c
// 跳表节点
typedef struct zskiplistNode {
    sds ele;                          // 成员对象（字符串）
    double score;                     // 分数
    struct zskiplistNode *backward;   // 后退指针（用于从尾到头遍历）
    struct zskiplistLevel {
        struct zskiplistNode *forward; // 前进指针
        unsigned long span;            // 跨度（用于计算排名）
    } level[];                         // 层级数组（柔性数组）
} zskiplistNode;

// 跳表结构
typedef struct zskiplist {
    struct zskiplistNode *header;     // 头节点
    struct zskiplistNode *tail;       // 尾节点
    unsigned long length;             // 节点数量
    int level;                        // 最大层数
} zskiplist;
```

**跳表的完整结构图：**

```
zskiplist
┌─────────────────────────────────────────────────────────────┐
│ header: → 头节点                                             │
│ tail: → 尾节点                                               │
│ length: 7                                                    │
│ level: 4                                                     │
└─────────────────────────────────────────────────────────────┘
         │
         ↓
    头节点（不存储数据）
    ┌──────────┐
    │ level[3] │ ──────────────────────────────────────→ NULL
    ├──────────┤
    │ level[2] │ ──────────────→ node3 ─────────────────→ NULL
    ├──────────┤
    │ level[1] │ ─────→ node2 ─→ node3 ─→ node5 ────────→ NULL
    ├──────────┤
    │ level[0] │ ─→ node1 ─→ node2 ─→ node3 ─→ node4 ─→ node5 ─→ node6 ─→ node7 ─→ NULL
    └──────────┘
         ↓           ↓           ↓           ↓           ↓           ↓           ↓
       NULL      backward    backward    backward    backward    backward    backward
                    ←───────────────────────────────────────────────────────────

节点详细结构（以 node3 为例）：
┌─────────────────────────┐
│ ele: "Alice"            │ ← 成员对象
│ score: 100.0            │ ← 分数
│ backward: → node2       │ ← 后退指针
├─────────────────────────┤
│ level[2].forward: → NULL│ ← 第 2 层前进指针
│ level[2].span: 0        │ ← 跨度
├─────────────────────────┤
│ level[1].forward: → node5│
│ level[1].span: 2        │ ← 跨度为 2（跳过 node4）
├─────────────────────────┤
│ level[0].forward: → node4│
│ level[0].span: 1        │ ← 跨度为 1
└─────────────────────────┘
```

#### 6.2.4 跳表的核心操作

**1. 查找操作**

```java
/**
 * 跳表查找（Java 实现）
 */
public class SkipList {
    
    private static final int MAX_LEVEL = 32;
    private static final double P = 0.25; // Redis 使用 0.25
    
    private Node header;
    private int level;
    private int size;
    
    /**
     * P 值说明：
     * 
     * P 是跳表的概率因子，决定了节点晋升到更高层的概率。
     * 
     * - Redis 使用 P = 0.25，意味着每个节点有 25% 的概率出现在上一层
     * - 这是一个经过实践验证的最优值，平衡了空间和时间复杂度
     * 
     * 为什么选择 0.25？
     * 1. 空间占用：P 越小，高层节点越少，内存占用越低
     *    - P=0.5：平均每个节点有 2 个指针
     *    - P=0.25：平均每个节点有 1.33 个指针（节省 33% 空间）
     * 
     * 2. 查询性能：P 越小，层数越多，但每层节点越少
     *    - P=0.5：查询复杂度 O(log₂N)
     *    - P=0.25：查询复杂度 O(log₄N)，实际性能差异很小
     * 
     * 3. 实际测试：Redis 作者 Antirez 通过大量测试发现 P=0.25 是最佳选择
     *    - 在保证性能的前提下，最大化节省内存
     *    - 对于百万级数据，可节省约 30% 的内存
     * 
     * 层数期望值计算：
     * - 第 1 层：100% 的节点（所有节点）
     * - 第 2 层：25% 的节点（P = 0.25）
     * - 第 3 层：6.25% 的节点（P² = 0.0625）
     * - 第 4 层：1.56% 的节点（P³ = 0.015625）
     * 
     * 平均层数 = 1/(1-P) = 1/(1-0.25) = 1.33 层
     */
    
    static class Node {
        String key;
        double score;
        Node[] forward;  // 前进指针数组
        
        Node(int level) {
            forward = new Node[level];
        }
    }
    
    public SkipList() {
        header = new Node(MAX_LEVEL);
        level = 1;
        size = 0;
    }
    
    /**
     * 查找节点
     */
    public Node search(double score) {
        Node current = header;
        
        // 从最高层开始查找
        for (int i = level - 1; i >= 0; i--) {
            // 在当前层向前移动，直到下一个节点的分数 >= 目标分数
            while (current.forward[i] != null && 
                   current.forward[i].score < score) {
                current = current.forward[i];
            }
        }
        
        // 移动到第 0 层的下一个节点
        current = current.forward[0];
        
        // 检查是否找到
        if (current != null && current.score == score) {
            return current;
        }
        
        return null;
    }
}
```

**查找过程示例（查找 score=100）：**

```
Level 3:  header ──────────────────────→ node(150) ──→ NULL
Level 2:  header ──────→ node(50) ─────→ node(150) ──→ NULL
Level 1:  header ──→ node(30) ─→ node(50) ─→ node(80) ─→ node(150) ──→ NULL
Level 0:  header → node(20) → node(30) → node(50) → node(80) → node(100) → node(150) → NULL

查找 score=100 的过程：
1. Level 3: header → node(150)（150 > 100，下降）
2. Level 2: header → node(50)（50 < 100，继续）→ node(150)（150 > 100，下降）
3. Level 1: node(50) → node(80)（80 < 100，继续）→ node(150)（150 > 100，下降）
4. Level 0: node(80) → node(100)（找到！）

比较次数：6 次（而不是遍历整个链表的 5 次）
```

**2. 插入操作**

```java
/**
 * 插入节点
 */
public void insert(String key, double score) {
    Node[] update = new Node[MAX_LEVEL]; // 记录每层需要更新的节点
    Node current = header;
    
    // 1. 查找插入位置，记录每层的前驱节点
    for (int i = level - 1; i >= 0; i--) {
        while (current.forward[i] != null && 
               current.forward[i].score < score) {
            current = current.forward[i];
        }
        update[i] = current;
    }
    
    // 2. 随机生成层数
    int newLevel = randomLevel();
    
    // 3. 如果新层数大于当前最大层数，更新 header
    if (newLevel > level) {
        for (int i = level; i < newLevel; i++) {
            update[i] = header;
        }
        level = newLevel;
    }
    
    // 4. 创建新节点
    Node newNode = new Node(newLevel);
    newNode.key = key;
    newNode.score = score;
    
    // 5. 插入节点，更新指针
    for (int i = 0; i < newLevel; i++) {
        newNode.forward[i] = update[i].forward[i];
        update[i].forward[i] = newNode;
    }
    
    size++;
}

/**
 * 随机层数生成（Redis 算法）
 * 
 * 算法原理：
 * 这是跳表的核心算法之一，用于决定新插入节点的层数。
 * 
 * 工作流程：
 * 1. 初始层数为 1（所有节点至少在第 1 层）
 * 2. 生成一个 [0, 1) 的随机数
 * 3. 如果随机数 < P (0.25)，则层数 +1，继续循环
 * 4. 如果随机数 >= P 或达到 MAX_LEVEL，则停止
 * 
 * 概率分析：
 * - 层数 = 1 的概率：75%（1 - P = 0.75）
 * - 层数 = 2 的概率：18.75%（P × (1-P) = 0.25 × 0.75）
 * - 层数 = 3 的概率：4.69%（P² × (1-P) = 0.0625 × 0.75）
 * - 层数 = 4 的概率：1.17%（P³ × (1-P)）
 * - ...以此类推
 * 
 * 示例：插入 100 个节点的层数分布
 * - 约 75 个节点在第 1 层
 * - 约 19 个节点在第 2 层
 * - 约 5 个节点在第 3 层
 * - 约 1 个节点在第 4 层及以上
 * 
 * 为什么这样设计？
 * 1. 随机性保证平衡：避免最坏情况（如有序插入导致的链表退化）
 * 2. 概率递减：高层节点少，形成"金字塔"结构，加速查找
 * 3. 简单高效：算法简单，时间复杂度 O(1)（期望循环次数 = 1/(1-P) = 1.33）
 * 
 * 与其他方案对比：
 * - 固定层数：无法适应数据规模变化
 * - 完全随机：可能出现极端情况，性能不稳定
 * - 概率递减（Redis 方案）：✅ 平衡性能和空间，适应各种场景
 */
private int randomLevel() {
    int level = 1;
    while (Math.random() < P && level < MAX_LEVEL) {
        level++;
    }
    return level;
}
```

**插入过程示例（插入 score=90）：**

```
插入前：
Level 2:  header ──────→ node(50) ─────→ node(150) ──→ NULL
Level 1:  header ──→ node(30) ─→ node(50) ─→ node(80) ─→ node(150) ──→ NULL
Level 0:  header → node(30) → node(50) → node(80) → node(150) → NULL

1. 查找插入位置，记录 update 数组：
   update[2] = header
   update[1] = node(80)
   update[0] = node(80)

2. 随机生成层数：假设生成 level=2

3. 创建新节点 node(90, level=2)

4. 更新指针：
   Level 2: header → node(90) → node(150)
   Level 1: node(80) → node(90) → node(150)
   Level 0: node(80) → node(90) → node(150)

插入后：
Level 2:  header ──────→ node(50) ─────→ node(90) ─→ node(150) ──→ NULL
Level 1:  header ──→ node(30) ─→ node(50) ─→ node(80) ─→ node(90) ─→ node(150) ──→ NULL
Level 0:  header → node(30) → node(50) → node(80) → node(90) → node(150) → NULL
```

**3. 删除操作**

```java
/**
 * 删除节点
 */
public boolean delete(double score) {
    Node[] update = new Node[MAX_LEVEL];
    Node current = header;
    
    // 1. 查找要删除的节点，记录每层的前驱节点
    for (int i = level - 1; i >= 0; i--) {
        while (current.forward[i] != null && 
               current.forward[i].score < score) {
            current = current.forward[i];
        }
        update[i] = current;
    }
    
    // 2. 获取要删除的节点
    current = current.forward[0];
    
    if (current == null || current.score != score) {
        return false; // 未找到
    }
    
    // 3. 更新指针，删除节点
    for (int i = 0; i < level; i++) {
        if (update[i].forward[i] == current) {
            update[i].forward[i] = current.forward[i];
        }
    }
    
    // 4. 更新最大层数
    while (level > 1 && header.forward[level - 1] == null) {
        level--;
    }
    
    size--;
    return true;
}
```

**4. 范围查询**

```java
/**
 * 范围查询（按分数）
 */
public List<Node> rangeByScore(double minScore, double maxScore) {
    List<Node> result = new ArrayList<>();
    Node current = header;
    
    // 1. 找到第一个 >= minScore 的节点
    for (int i = level - 1; i >= 0; i--) {
        while (current.forward[i] != null && 
               current.forward[i].score < minScore) {
            current = current.forward[i];
        }
    }
    
    current = current.forward[0];
    
    // 2. 从第 0 层遍历，收集范围内的节点
    while (current != null && current.score <= maxScore) {
        result.add(current);
        current = current.forward[0];
    }
    
    return result;
}
```

#### 6.2.5 跳表的时间复杂度分析

**为什么跳表的时间复杂度是 O(log N)？**

```
假设有 N 个节点，每层的节点数是下一层的 1/2（P=0.5）

Level 3:  N/8 个节点
Level 2:  N/4 个节点
Level 1:  N/2 个节点
Level 0:  N 个节点

最大层数：log₂(N)

查找过程：
- 每层最多遍历 2 个节点（因为每层节点数是下一层的 1/2）
- 总层数：log₂(N)
- 总比较次数：2 * log₂(N) = O(log N)

Redis 使用 P=0.25，期望层数：
Level 1: 100%
Level 2: 25%
Level 3: 6.25%
Level 4: 1.56%

最大层数：log₄(N) ≈ 0.5 * log₂(N)
```

**操作复杂度总结：**

| 操作 | 平均时间复杂度 | 最坏时间复杂度 | 空间复杂度 |
|------|--------------|--------------|-----------|
| **查找** | O(log N) | O(N) | O(1) |
| **插入** | O(log N) | O(N) | O(1) |
| **删除** | O(log N) | O(N) | O(1) |
| **范围查询** | O(log N + M) | O(N) | O(M) |

#### 6.2.6 Redis ZSet 的双重数据结构

**为什么 ZSet 同时使用 skiplist 和 hashtable？**

```c
// Redis ZSet 结构
typedef struct zset {
    dict *dict;          // hashtable：member → score（O(1) 查询分数）
    zskiplist *zsl;      // skiplist：按 score 排序（O(log N) 范围查询）
} zset;
```

**双重结构的优势：**

| 操作 | 只用 skiplist | skiplist + hashtable | 优势 |
|------|--------------|---------------------|------|
| **ZSCORE** | O(log N) | O(1) | hashtable 直接查询 |
| **ZRANK** | O(log N) | O(log N) | skiplist 计算排名 |
| **ZRANGE** | O(log N + M) | O(log N + M) | skiplist 范围查询 |
| **ZADD** | O(log N) | O(log N) | 同时更新两个结构 |

**内存代价：**

```
假设 100 万个元素：
- skiplist：约 40MB（节点 + 指针）
- hashtable：约 30MB（哈希表）
- 总计：约 70MB

虽然内存多了，但查询性能大幅提升！
```

**示例：ZSCORE 命令的实现**

```c
// 使用 hashtable 实现 O(1) 查询
double zscore(zset *zs, sds member) {
    dictEntry *de = dictFind(zs->dict, member);
    if (de == NULL) {
        return NULL;  // 成员不存在
    }
    return *(double*)dictGetVal(de);
}

// 如果只用 skiplist，需要 O(log N)
double zscoreWithSkiplistOnly(zskiplist *zsl, sds member) {
    zskiplistNode *node = zsl->header;
    
    // 从最高层开始查找
    for (int i = zsl->level - 1; i >= 0; i--) {
        while (node->level[i].forward != NULL) {
            int cmp = sdscmp(node->level[i].forward->ele, member);
            if (cmp == 0) {
                return node->level[i].forward->score;
            } else if (cmp > 0) {
                break;
            }
            node = node->level[i].forward;
        }
    }
    
    return NULL;  // 未找到
}
```

---

### 6.3 完整的跳表 Java 实现

```java
/**
 * 完整的跳表实现（生产级代码）
 */
public class SkipList<K extends Comparable<K>, V> {
    
    private static final int MAX_LEVEL = 32;
    private static final double P = 0.25;
    
    private final Node<K, V> header;
    private int level;
    private int size;
    private final Random random;
    
    /**
     * 跳表节点
     */
    static class Node<K, V> {
        K key;
        V value;
        Node<K, V>[] forward;
        
        @SuppressWarnings("unchecked")
        Node(int level) {
            forward = new Node[level];
        }
        
        Node(K key, V value, int level) {
            this.key = key;
            this.value = value;
            this.forward = new Node[level];
        }
    }
    
    public SkipList() {
        header = new Node<>(MAX_LEVEL);
        level = 1;
        size = 0;
        random = new Random();
    }
    
    /**
     * 插入元素
     */
    public void put(K key, V value) {
        @SuppressWarnings("unchecked")
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V> current = header;
        
        // 1. 查找插入位置
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null && 
                   current.forward[i].key.compareTo(key) < 0) {
                current = current.forward[i];
            }
            update[i] = current;
        }
        
        current = current.forward[0];
        
        // 2. 如果 key 已存在，更新 value
        if (current != null && current.key.compareTo(key) == 0) {
            current.value = value;
            return;
        }
        
        // 3. 生成随机层数
        int newLevel = randomLevel();
        
        // 4. 如果新层数大于当前最大层数
        if (newLevel > level) {
            for (int i = level; i < newLevel; i++) {
                update[i] = header;
            }
            level = newLevel;
        }
        
        // 5. 创建新节点并插入
        Node<K, V> newNode = new Node<>(key, value, newLevel);
        for (int i = 0; i < newLevel; i++) {
            newNode.forward[i] = update[i].forward[i];
            update[i].forward[i] = newNode;
        }
        
        size++;
    }
    
    /**
     * 查找元素
     */
    public V get(K key) {
        Node<K, V> current = header;
        
        // 从最高层开始查找
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null && 
                   current.forward[i].key.compareTo(key) < 0) {
                current = current.forward[i];
            }
        }
        
        current = current.forward[0];
        
        if (current != null && current.key.compareTo(key) == 0) {
            return current.value;
        }
        
        return null;
    }
    
    /**
     * 删除元素
     */
    public boolean remove(K key) {
        @SuppressWarnings("unchecked")
        Node<K, V>[] update = new Node[MAX_LEVEL];
        Node<K, V> current = header;
        
        // 1. 查找要删除的节点
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null && 
                   current.forward[i].key.compareTo(key) < 0) {
                current = current.forward[i];
            }
            update[i] = current;
        }
        
        current = current.forward[0];
        
        // 2. 如果节点不存在
        if (current == null || current.key.compareTo(key) != 0) {
            return false;
        }
        
        // 3. 删除节点
        for (int i = 0; i < level; i++) {
            if (update[i].forward[i] == current) {
                update[i].forward[i] = current.forward[i];
            }
        }
        
        // 4. 更新最大层数
        while (level > 1 && header.forward[level - 1] == null) {
            level--;
        }
        
        size--;
        return true;
    }
    
    /**
     * 范围查询
     */
    public List<Entry<K, V>> range(K minKey, K maxKey) {
        List<Entry<K, V>> result = new ArrayList<>();
        Node<K, V> current = header;
        
        // 1. 找到第一个 >= minKey 的节点
        for (int i = level - 1; i >= 0; i--) {
            while (current.forward[i] != null && 
                   current.forward[i].key.compareTo(minKey) < 0) {
                current = current.forward[i];
            }
        }
        
        current = current.forward[0];
        
        // 2. 收集范围内的节点
        while (current != null && current.key.compareTo(maxKey) <= 0) {
            result.add(new Entry<>(current.key, current.value));
            current = current.forward[0];
        }
        
        return result;
    }
    
    /**
     * 获取大小
     */
    public int size() {
        return size;
    }
    
    /**
     * 是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }
    
    /**
     * 随机层数生成
     */
    private int randomLevel() {
        int lvl = 1;
        while (random.nextDouble() < P && lvl < MAX_LEVEL) {
            lvl++;
        }
        return lvl;
    }
    
    /**
     * 打印跳表结构（调试用）
     */
    public void print() {
        for (int i = level - 1; i >= 0; i--) {
            System.out.print("Level " + i + ": ");
            Node<K, V> current = header.forward[i];
            while (current != null) {
                System.out.print(current.key + "(" + current.value + ") -> ");
                current = current.forward[i];
            }
            System.out.println("NULL");
        }
    }
    
    /**
     * 键值对
     */
    public static class Entry<K, V> {
        private final K key;
        private final V value;
        
        public Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
        
        public K getKey() {
            return key;
        }
        
        public V getValue() {
            return value;
        }
    }
}
```

**使用示例：**

```java
@Service
public class SkipListDemo {
    
    /**
     * 演示跳表的基本操作
     */
    public void demonstrateSkipList() {
        SkipList<Integer, String> skipList = new SkipList<>();
        
        // 1. 插入数据
        skipList.put(10, "Alice");
        skipList.put(20, "Bob");
        skipList.put(30, "Charlie");
        skipList.put(40, "David");
        skipList.put(50, "Eve");
        
        System.out.println("Size: " + skipList.size()); // 5
        
        // 2. 查找数据
        System.out.println("Get 30: " + skipList.get(30)); // Charlie
        System.out.println("Get 100: " + skipList.get(100)); // null
        
        // 3. 范围查询
        List<SkipList.Entry<Integer, String>> range = skipList.range(20, 40);
        System.out.println("Range [20, 40]:");
        for (SkipList.Entry<Integer, String> entry : range) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        // 输出：
        // 20 -> Bob
        // 30 -> Charlie
        // 40 -> David
        
        // 4. 删除数据
        skipList.remove(30);
        System.out.println("After remove 30, size: " + skipList.size()); // 4
        
        // 5. 打印跳表结构
        skipList.print();
        // 输出示例：
        // Level 2: 20(Bob) -> 50(Eve) -> NULL
        // Level 1: 10(Alice) -> 20(Bob) -> 40(David) -> 50(Eve) -> NULL
        // Level 0: 10(Alice) -> 20(Bob) -> 40(David) -> 50(Eve) -> NULL
    }
    
    /**
     * 性能测试：跳表 vs TreeMap
     */
    public void performanceTest() {
        int n = 100000;
        
        // 测试跳表
        SkipList<Integer, String> skipList = new SkipList<>();
        long start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            skipList.put(i, "value" + i);
        }
        long skipListInsertTime = System.currentTimeMillis() - start;
        
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            skipList.get(i);
        }
        long skipListSearchTime = System.currentTimeMillis() - start;
        
        // 测试 TreeMap
        TreeMap<Integer, String> treeMap = new TreeMap<>();
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            treeMap.put(i, "value" + i);
        }
        long treeMapInsertTime = System.currentTimeMillis() - start;
        
        start = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            treeMap.get(i);
        }
        long treeMapSearchTime = System.currentTimeMillis() - start;
        
        System.out.println("=== 性能对比（" + n + " 个元素）===");
        System.out.println("SkipList 插入: " + skipListInsertTime + "ms");
        System.out.println("TreeMap 插入: " + treeMapInsertTime + "ms");
        System.out.println("SkipList 查找: " + skipListSearchTime + "ms");
        System.out.println("TreeMap 查找: " + treeMapSearchTime + "ms");
    }
}
```

---

### 6.4 ZSet 常用命令

| 命令 | 时间复杂度 | 说明 | 应用场景 |
|------|-----------|------|----------|
| ZADD | O(log N) | 添加元素 | 更新排行榜 |
| ZREM | O(log N) | 删除元素 | 移除排名 |
| ZSCORE | O(1) | 获取分数 | 查询积分 |
| ZRANK | O(log N) | 获取排名 | 查询名次 |
| ZRANGE | O(log N + M) | 范围查询 | Top N |
| ZINCRBY | O(log N) | 增加分数 | 增加积分 |
| ZCOUNT | O(log N) | 统计数量 | 分数段统计 |

### 6.5 实战案例

#### 案例 1：实时排行榜

```java
@Service
public class LeaderboardService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 更新用户分数
     */
    public void updateScore(Long userId, double score) {
        String key = "leaderboard:daily:" + LocalDate.now();
        
        // 增加分数
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), score);
        
        // 设置过期时间（保留7天）
        redisTemplate.expire(key, 7, TimeUnit.DAYS);
    }
    
    /**
     * 获取 Top N
     */
    public List<LeaderboardEntry> getTopN(int count) {
        String key = "leaderboard:daily:" + LocalDate.now();
        
        // 按分数降序获取
        Set<ZSetOperations.TypedTuple<String>> top = 
            redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, count - 1);
        
        if (top == null) {
            return Collections.emptyList();
        }
        
        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : top) {
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setRank(rank++);
            entry.setUserId(Long.parseLong(tuple.getValue()));
            entry.setScore(tuple.getScore());
            result.add(entry);
        }
        
        return result;
    }
    
    /**
     * 获取用户排名
     */
    public Long getUserRank(Long userId) {
        String key = "leaderboard:daily:" + LocalDate.now();
        
        // 获取排名（从0开始）
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());
        
        return rank == null ? null : rank + 1;
    }
    
    /**
     * 获取用户分数
     */
    public Double getUserScore(Long userId) {
        String key = "leaderboard:daily:" + LocalDate.now();
        return redisTemplate.opsForZSet().score(key, userId.toString());
    }
    
    /**
     * 获取指定分数范围的用户数
     */
    public long countByScoreRange(double min, double max) {
        String key = "leaderboard:daily:" + LocalDate.now();
        Long count = redisTemplate.opsForZSet().count(key, min, max);
        return count == null ? 0 : count;
    }
}
```

#### 案例 2：延时队列

```java
@Service
public class DelayQueue {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final String DELAY_QUEUE_KEY = "delay:queue";
    
    /**
     * 添加延时任务
     * 
     * @param taskId 任务ID
     * @param delaySeconds 延时秒数
     */
    public void addTask(String taskId, long delaySeconds) {
        long executeTime = System.currentTimeMillis() + delaySeconds * 1000;
        
        // 使用执行时间作为分数
        redisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, taskId, executeTime);
    }
    
    /**
     * 获取到期的任务
     * 
     * 定时任务每秒执行一次，获取到期任务并处理
     */
    @Scheduled(fixedRate = 1000)
    public void processTasks() {
        long now = System.currentTimeMillis();
        
        // 获取分数 <= now 的任务
        Set<String> tasks = redisTemplate.opsForZSet().rangeByScore(
            DELAY_QUEUE_KEY,
            0,
            now
        );
        
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        
        for (String taskId : tasks) {
            // 处理任务
            processTask(taskId);
            
            // 删除已处理的任务
            redisTemplate.opsForZSet().remove(DELAY_QUEUE_KEY, taskId);
        }
    }
    
    private void processTask(String taskId) {
        // 实际业务逻辑
        System.out.println("Processing task: " + taskId);
    }
}
```

---

## 七、数据结构选择指南

### 7.1 选择流程图

```
需要存储什么？
│
├─ 单个值
│  └─ String
│     ├─ 缓存对象（JSON序列化）
│     ├─ 计数器（INCR/DECR）
│     └─ 分布式锁（SET NX EX）
│
├─ 对象的多个字段
│  └─ Hash
│     ├─ 购物车（field=商品ID, value=数量）
│     ├─ 用户信息（field=属性名, value=属性值）
│     └─ 配置信息
│
├─ 有序列表
│  └─ List
│     ├─ 最新动态（LPUSH + LTRIM）
│     ├─ 消息队列（LPUSH + BRPOP）
│     └─ 栈/队列
│
├─ 无序集合（去重）
│  └─ Set
│     ├─ 标签系统（SADD + SMEMBERS）
│     ├─ 共同好友（SINTER）
│     └─ 抽奖（SRANDMEMBER）
│
└─ 有序集合（排序）
   └─ ZSet
      ├─ 排行榜（ZADD + ZREVRANGE）
      ├─ 延时队列（score=执行时间）
      └─ 范围查询（ZRANGEBYSCORE）
```

### 7.2 性能对比

| 操作 | String | Hash | List | Set | ZSet |
|------|--------|------|------|-----|------|
| 插入 | O(1) | O(1) | O(1) | O(1) | O(log N) |
| 查询 | O(1) | O(1) | O(N) | O(1) | O(log N) |
| 删除 | O(1) | O(1) | O(N) | O(1) | O(log N) |
| 范围查询 | ❌ | ❌ | O(N) | ❌ | O(log N + M) |
| 排序 | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## 八、面试重点

**Q1：Redis 为什么快？（数据结构角度）**

**A：**
1. **高效的数据结构**：SDS、跳表、压缩列表等针对性能优化
2. **编码优化**：根据数据量自动选择最优编码（ziplist vs hashtable）
3. **内存操作**：所有数据结构都在内存中，避免磁盘IO

**Q2：跳表和平衡树的区别？**

| 特性 | 跳表 | 平衡树 |
|------|------|--------|
| 实现难度 | 简单 | 复杂 |
| 范围查询 | 高效 | 高效 |
| 并发性能 | 更好（无锁化） | 需要加锁 |
| 内存占用 | 稍高 | 稍低 |

**Q3：什么时候用 Hash，什么时候用 String？**

**A：**
- **Hash**：对象有多个字段，需要单独操作某个字段（如购物车）
- **String**：简单的 K-V 存储，或需要原子操作（如计数器）

---

## 九、实践要点

### 9.1 内存优化

```java
// ✅ 使用 Hash 存储对象（节省内存）
// 100万用户，每个用户10个字段
// Hash 方式：约 200MB
// String 方式：约 500MB

// ✅ 控制 ziplist 大小
hash-max-ziplist-entries 512
hash-max-ziplist-value 64

// ✅ 使用 intset 存储整数集合
SADD user:ids 1 2 3 4 5  // 使用 intset，内存更小
```

### 9.2 性能优化

```java
// ✅ 批量操作
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (int i = 0; i < 1000; i++) {
        connection.set(("key" + i).getBytes(), ("value" + i).getBytes());
    }
    return null;
});

// ❌ 避免大 Key
// List/Set/ZSet 元素数量不要超过 10000
// Hash 字段数量不要超过 10000

// ✅ 使用 SCAN 代替 KEYS
Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions()
    .match("user:*")
    .count(100)
    .build());
```

---

**下一步学习**：[03-Redis内存管理与优化.md](./03-Redis内存管理与优化.md)
