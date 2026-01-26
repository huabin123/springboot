# Redis 内存管理与优化

> **学习目标**：掌握 Redis 内存分配机制、淘汰策略、过期删除策略，以及生产环境的内存优化技巧。

## 一、Redis 内存使用全景

### 1.1 内存占用分析

```bash
# 查看内存使用情况
127.0.0.1:6379> INFO memory
# Memory
used_memory:1073741824              # 已使用内存（字节）
used_memory_human:1.00G             # 已使用内存（人类可读）
used_memory_rss:1258291200          # 操作系统分配的内存
used_memory_peak:2147483648         # 内存使用峰值
used_memory_peak_human:2.00G
used_memory_overhead:52428800       # 内存开销（非数据部分）
used_memory_dataset:1021313024      # 实际数据占用
mem_fragmentation_ratio:1.17        # 内存碎片率
```

**内存组成：**

```
Redis 总内存
├── 数据内存（80-90%）
│   ├── Key 对象
│   ├── Value 对象
│   └── 过期字典
├── 进程内存（5-10%）
│   ├── 代码段
│   ├── 共享库
│   └── 栈空间
└── 缓冲内存（5-10%）
    ├── 客户端缓冲区
    ├── 复制缓冲区
    └── AOF 缓冲区
```

### 1.2 内存碎片

**什么是内存碎片？**

```
分配的内存：1000MB
实际使用：800MB
碎片：200MB

碎片率 = used_memory_rss / used_memory
- 碎片率 > 1.5：碎片严重，需要优化
- 碎片率 < 1：可能发生了 swap，性能严重下降
```

**产生原因：**

1. **频繁的增删改**：内存分配和释放不连续
2. **不同大小的对象**：无法完美复用内存块
3. **内存分配器策略**：jemalloc 的分配策略

**解决方案：**

```bash
# Redis 4.0+ 支持主动碎片整理
CONFIG SET activedefrag yes

# 配置参数
active-defrag-ignore-bytes 100mb        # 碎片达到100MB才整理
active-defrag-threshold-lower 10        # 碎片率超过10%才整理
active-defrag-cycle-min 5               # 最小CPU占用5%
active-defrag-cycle-max 75              # 最大CPU占用75%
```

---

## 二、内存淘汰策略

### 2.1 八种淘汰策略

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| **noeviction** | 不淘汰，内存满时拒绝写入 | 不允许数据丢失 |
| **allkeys-lru** | 所有key中淘汰最近最少使用 | **通用缓存（推荐）** |
| **allkeys-lfu** | 所有key中淘汰最少使用频率 | 热点数据明显 |
| **allkeys-random** | 所有key中随机淘汰 | 数据访问均匀 |
| **volatile-lru** | 设置过期时间的key中淘汰LRU | 部分数据可淘汰 |
| **volatile-lfu** | 设置过期时间的key中淘汰LFU | 部分数据可淘汰 |
| **volatile-random** | 设置过期时间的key中随机淘汰 | 部分数据可淘汰 |
| **volatile-ttl** | 淘汰即将过期的key | 优先淘汰快过期的 |

### 2.2 LRU vs LFU

**LRU（Least Recently Used）：最近最少使用**

```
访问序列：A B C D E A B C D E
淘汰顺序：E D C B A（最近没访问的先淘汰）

优点：实现简单，适合大部分场景
缺点：可能淘汰热点数据（偶尔访问的数据会被保留）
```

**LFU（Least Frequently Used）：最少使用频率**

```
访问频率：A(10次) B(8次) C(5次) D(2次) E(1次)
淘汰顺序：E D C B A（访问次数少的先淘汰）

优点：更精确识别热点数据
缺点：历史数据影响大，新数据容易被淘汰
```

**Redis 的近似 LRU 实现：**

```c
// Redis 不使用标准 LRU（需要维护链表，开销大）
// 而是使用采样 LRU

// 每个 key 有一个 24 位的时钟字段
typedef struct redisObject {
    unsigned type:4;
    unsigned encoding:4;
    unsigned lru:24;  // 最后访问时间（秒级时间戳）
    int refcount;
    void *ptr;
} robj;

// 淘汰算法：
1. 随机采样 N 个 key（默认 5 个）
2. 淘汰其中 lru 最小的
3. 重复直到内存足够

// 配置采样数量
maxmemory-samples 5  // 采样数越大，越接近真实 LRU，但性能越差
```

---

#### 使用 LinkedHashMap 实现标准 LRU 缓存

**为什么 Redis 不用标准 LRU？**

标准 LRU 需要维护双向链表，每次访问都要移动节点，开销大。但在 Java 应用层，我们可以使用 `LinkedHashMap` 轻松实现标准 LRU。

```java
/**
 * 基于 LinkedHashMap 的 LRU 缓存实现
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    
    private final int capacity;
    
    public LRUCache(int capacity) {
        // 参数说明：
        // capacity + 1: 初始容量（多 1 避免扩容）
        // 0.75f: 负载因子
        // true: accessOrder=true，按访问顺序排序（LRU 的关键！）
        super(capacity + 1, 0.75f, true);
        this.capacity = capacity;
    }
    
    /**
     * 重写此方法，当返回 true 时删除最老的元素
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        // 当 size > capacity 时，删除最老的元素（链表头部）
        return size() > capacity;
    }
    
    /**
     * 线程安全版本（使用 synchronized）
     */
    public synchronized V getSafe(K key) {
        return super.get(key);
    }
    
    public synchronized V putSafe(K key, V value) {
        return super.put(key, value);
    }
}
```

**使用示例：**

```java
@Service
public class LocalCacheService {
    
    // 创建容量为 100 的 LRU 缓存
    private final LRUCache<String, String> cache = new LRUCache<>(100);
    
    /**
     * 演示 LRU 淘汰过程
     */
    public void demonstrateLRU() {
        // 1. 添加 3 个元素
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        System.out.println("初始状态：" + cache.keySet());
        // 输出：[key1, key2, key3]
        
        // 2. 访问 key1（会移到链表尾部）
        cache.get("key1");
        System.out.println("访问 key1 后：" + cache.keySet());
        // 输出：[key2, key3, key1]
        
        // 3. 添加新元素（假设容量为 3）
        cache.put("key4", "value4");
        System.out.println("添加 key4 后：" + cache.keySet());
        // 输出：[key3, key1, key4]（key2 被淘汰）
    }
}
```

**完整的生产级 LRU 缓存实现：**

```java
/**
 * 生产级 LRU 缓存（支持过期时间、统计信息）
 */
public class AdvancedLRUCache<K, V> {
    
    private final int capacity;
    private final LinkedHashMap<K, CacheEntry<V>> cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 统计信息
    private long hits = 0;
    private long misses = 0;
    private long evictions = 0;
    
    public AdvancedLRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<K, CacheEntry<V>>(capacity + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                boolean shouldRemove = size() > capacity;
                if (shouldRemove) {
                    evictions++;
                }
                return shouldRemove;
            }
        };
    }
    
    /**
     * 获取缓存值
     */
    public V get(K key) {
        lock.readLock().lock();
        try {
            CacheEntry<V> entry = cache.get(key);
            
            if (entry == null) {
                misses++;
                return null;
            }
            
            // 检查是否过期
            if (entry.isExpired()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(key);
                    misses++;
                    return null;
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }
            
            hits++;
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 设置缓存值（带过期时间）
     */
    public void put(K key, V value, long ttlMillis) {
        lock.writeLock().lock();
        try {
            long expireTime = ttlMillis > 0 ? 
                System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
            cache.put(key, new CacheEntry<>(value, expireTime));
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total : 0;
            return new CacheStats(hits, misses, evictions, cache.size(), hitRate);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 缓存条目（包含过期时间）
     */
    private static class CacheEntry<V> {
        private final V value;
        private final long expireTime;
        
        public CacheEntry(V value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }
        
        public V getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private final long hits;
        private final long misses;
        private final long evictions;
        private final int size;
        private final double hitRate;
        
        public CacheStats(long hits, long misses, long evictions, int size, double hitRate) {
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.size = size;
            this.hitRate = hitRate;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats{hits=%d, misses=%d, evictions=%d, size=%d, hitRate=%.2f%%}",
                hits, misses, evictions, size, hitRate * 100
            );
        }
    }
}
```

**使用示例：**

```java
@Service
public class UserCacheService {
    
    // 创建容量为 1000 的 LRU 缓存
    private final AdvancedLRUCache<Long, User> userCache = new AdvancedLRUCache<>(1000);
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 获取用户（带缓存）
     */
    public User getUser(Long userId) {
        // 1. 尝试从缓存获取
        User user = userCache.get(userId);
        
        if (user != null) {
            return user;
        }
        
        // 2. 缓存未命中，查询数据库
        user = userRepository.findById(userId).orElse(null);
        
        if (user != null) {
            // 3. 写入缓存，TTL 5 分钟
            userCache.put(userId, user, 5 * 60 * 1000);
        }
        
        return user;
    }
    
    /**
     * 定期打印缓存统计信息
     */
    @Scheduled(fixedRate = 60000)
    public void printCacheStats() {
        log.info("User cache stats: {}", userCache.getStats());
    }
}
```

**LRU vs Redis 对比：**

| 特性 | LinkedHashMap LRU | Redis LRU |
|------|------------------|-----------|
| **实现方式** | 双向链表 | 采样近似 |
| **时间复杂度** | O(1) | O(N)（N=采样数） |
| **空间开销** | 每个节点 2 个指针 | 每个 key 24 位时间戳 |
| **精确度** | 100% 精确 | 近似（采样越多越精确） |
| **适用场景** | 应用层缓存 | 分布式缓存 |
| **并发性能** | 需要加锁 | 单线程无锁 |

---

#### 其他中间件的 LRU 应用

**1. MySQL InnoDB Buffer Pool**

```
InnoDB 使用改进的 LRU 算法管理 Buffer Pool：

┌─────────────────────────────────────────┐
│          InnoDB Buffer Pool             │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────┐  ┌──────────────┐   │
│  │   New区      │  │   Old区      │   │
│  │  (5/8)       │  │  (3/8)       │   │
│  └──────────────┘  └──────────────┘   │
│         ↑                ↑              │
│         │                │              │
│    热点数据          预读数据           │
│                                         │
└─────────────────────────────────────────┘

改进点：
1. 分为 New 区和 Old 区（避免全表扫描污染缓存）
2. 新读取的页先放入 Old 区
3. 在 Old 区停留超过 1 秒后，再移到 New 区
4. 这样全表扫描的页不会污染热点数据

配置参数：
innodb_buffer_pool_size = 8G          # Buffer Pool 大小
innodb_old_blocks_pct = 37            # Old 区占比（默认 37%）
innodb_old_blocks_time = 1000         # 停留时间（毫秒）
```

**Java 示例（模拟 InnoDB 的改进 LRU）：**

```java
/**
 * 模拟 InnoDB 的两段式 LRU
 */
public class TwoSegmentLRUCache<K, V> {
    
    private final int capacity;
    private final int oldSegmentSize;
    private final long promotionDelay; // 晋升延迟（毫秒）
    
    // Old 区（新数据）
    private final LinkedHashMap<K, CacheEntry<V>> oldSegment;
    // New 区（热点数据）
    private final LinkedHashMap<K, CacheEntry<V>> newSegment;
    
    public TwoSegmentLRUCache(int capacity, double oldRatio, long promotionDelay) {
        this.capacity = capacity;
        this.oldSegmentSize = (int) (capacity * oldRatio);
        this.promotionDelay = promotionDelay;
        
        int newSegmentSize = capacity - oldSegmentSize;
        
        this.oldSegment = new LinkedHashMap<K, CacheEntry<V>>(
            oldSegmentSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                return size() > oldSegmentSize;
            }
        };
        
        this.newSegment = new LinkedHashMap<K, CacheEntry<V>>(
            newSegmentSize + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                if (size() > newSegmentSize) {
                    // New 区满了，降级到 Old 区
                    oldSegment.put(eldest.getKey(), eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }
    
    public V get(K key) {
        // 1. 先查 New 区
        CacheEntry<V> entry = newSegment.get(key);
        if (entry != null) {
            return entry.getValue();
        }
        
        // 2. 再查 Old 区
        entry = oldSegment.get(key);
        if (entry != null) {
            // 检查是否可以晋升到 New 区
            if (entry.canPromote(promotionDelay)) {
                oldSegment.remove(key);
                newSegment.put(key, entry);
            }
            return entry.getValue();
        }
        
        return null;
    }
    
    public void put(K key, V value) {
        // 新数据先放入 Old 区
        oldSegment.put(key, new CacheEntry<>(value));
    }
    
    private static class CacheEntry<V> {
        private final V value;
        private final long createTime;
        
        public CacheEntry(V value) {
            this.value = value;
            this.createTime = System.currentTimeMillis();
        }
        
        public V getValue() {
            return value;
        }
        
        public boolean canPromote(long delay) {
            return System.currentTimeMillis() - createTime > delay;
        }
    }
}
```

**2. Linux Page Cache**

```
Linux 内核使用 LRU 管理页缓存：

┌─────────────────────────────────────────┐
│           Linux Page Cache              │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────┐  ┌──────────────┐   │
│  │ Active List  │  │ Inactive List│   │
│  │  (活跃页)    │  │  (非活跃页)  │   │
│  └──────────────┘  └──────────────┘   │
│         ↑                ↓              │
│         │                │              │
│    再次访问          首次访问           │
│                                         │
└─────────────────────────────────────────┘

工作原理：
1. 新页面加入 Inactive List
2. 再次访问时移到 Active List
3. 内存不足时，先从 Inactive List 淘汰
4. Active List 的页面长时间未访问会降级到 Inactive List

查看页缓存统计：
cat /proc/meminfo | grep -E "Cached|Active|Inactive"
```

**3. Caffeine（高性能 Java 缓存库）**

```java
/**
 * Caffeine 使用 W-TinyLFU 算法（比 LRU 更优）
 */
@Configuration
public class CaffeineConfig {
    
    @Bean
    public Cache<String, User> userCache() {
        return Caffeine.newBuilder()
            // 最大容量
            .maximumSize(10_000)
            // 写入后过期时间
            .expireAfterWrite(5, TimeUnit.MINUTES)
            // 访问后过期时间
            .expireAfterAccess(10, TimeUnit.MINUTES)
            // 记录统计信息
            .recordStats()
            // 淘汰监听器
            .removalListener((key, value, cause) -> {
                log.info("Evicted: key={}, cause={}", key, cause);
            })
            .build();
    }
}

// Caffeine 的优势：
// 1. 比 LRU 更精确（结合了 LRU 和 LFU）
// 2. 高并发性能好（无锁设计）
// 3. 内存开销小（使用 Bloom Filter）
```

**4. Nginx Proxy Cache**

```nginx
# Nginx 使用 LRU 管理代理缓存

http {
    # 定义缓存路径和配置
    proxy_cache_path /var/cache/nginx 
        levels=1:2                    # 两级目录
        keys_zone=my_cache:10m        # 缓存键空间（10MB）
        max_size=1g                   # 最大缓存大小（1GB）
        inactive=60m                  # 60分钟未访问则淘汰（LRU）
        use_temp_path=off;
    
    server {
        location / {
            proxy_cache my_cache;
            proxy_cache_valid 200 1h;  # 200 响应缓存 1 小时
            proxy_cache_use_stale error timeout;
        }
    }
}

# LRU 策略：
# 1. 缓存满时，淘汰最久未访问的文件
# 2. inactive 参数控制淘汰时间
# 3. 支持缓存预热和手动清理
```

**5. Guava Cache**

```java
/**
 * Guava Cache 使用分段 LRU
 */
@Service
public class GuavaCacheService {
    
    private final LoadingCache<String, User> cache = CacheBuilder.newBuilder()
        // 最大容量（LRU 淘汰）
        .maximumSize(1000)
        // 并发级别（分段锁）
        .concurrencyLevel(10)
        // 写入后过期
        .expireAfterWrite(10, TimeUnit.MINUTES)
        // 访问后过期
        .expireAfterAccess(5, TimeUnit.MINUTES)
        // 统计信息
        .recordStats()
        // 自动加载
        .build(new CacheLoader<String, User>() {
            @Override
            public User load(String key) throws Exception {
                return loadUserFromDB(key);
            }
        });
    
    public User getUser(String userId) throws ExecutionException {
        return cache.get(userId);
    }
    
    @Scheduled(fixedRate = 60000)
    public void printStats() {
        CacheStats stats = cache.stats();
        log.info("Cache stats: hitRate={}, evictionCount={}", 
            stats.hitRate(), stats.evictionCount());
    }
}
```

**LRU 在各中间件中的对比：**

| 中间件 | LRU 变种 | 特点 | 适用场景 |
|--------|---------|------|---------|
| **Redis** | 采样 LRU | 近似算法，低开销 | 分布式缓存 |
| **InnoDB** | 两段式 LRU | 防止扫描污染 | 数据库缓存 |
| **Linux** | Active/Inactive | 二次机会算法 | 操作系统页缓存 |
| **Caffeine** | W-TinyLFU | LRU+LFU 混合 | 应用层缓存 |
| **Nginx** | 标准 LRU | 基于文件系统 | HTTP 代理缓存 |
| **Guava** | 分段 LRU | 并发友好 | 应用层缓存 |

### 2.3 配置示例

```bash
# redis.conf

# 设置最大内存（生产环境必须设置）
maxmemory 2gb

# 设置淘汰策略
maxmemory-policy allkeys-lru

# 设置采样数量
maxmemory-samples 5
```

**Java 配置：**

```java
@Configuration
public class RedisConfig {
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);
        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        
        // 连接后执行配置命令
        factory.afterPropertiesSet();
        
        return factory;
    }
    
    @PostConstruct
    public void configureMaxMemory() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            // 设置最大内存
            connection.setConfig("maxmemory", "2gb");
            // 设置淘汰策略
            connection.setConfig("maxmemory-policy", "allkeys-lru");
            return null;
        });
    }
}
```

---

## 三、过期删除策略

### 3.1 三种删除策略

**1. 定时删除（主动）**

```
优点：内存友好，过期立即删除
缺点：CPU不友好，需要维护大量定时器

Redis 不采用此策略
```

**2. 惰性删除（被动）**

```java
// 访问 key 时检查是否过期
public String get(String key) {
    // 1. 检查是否过期
    if (isExpired(key)) {
        delete(key);
        return null;
    }
    
    // 2. 返回值
    return getValue(key);
}

优点：CPU友好，只在访问时检查
缺点：内存不友好，过期key可能长期占用内存
```

**3. 定期删除（主动）**

```c
// Redis 的定期删除策略
void activeExpireCycle() {
    for (每个数据库) {
        for (20次循环) {  // 限制单次执行时间
            // 1. 随机抽取 20 个设置了过期时间的 key
            keys = randomSample(20);
            
            // 2. 删除过期的 key
            for (key in keys) {
                if (isExpired(key)) {
                    delete(key);
                }
            }
            
            // 3. 如果过期 key 比例 > 25%，继续循环
            if (过期比例 < 25%) {
                break;
            }
        }
    }
}

// 执行频率：每秒 10 次（100ms 一次）
```

**Redis 采用：惰性删除 + 定期删除**

```
┌─────────────────────────────────────┐
│         Redis 过期删除策略          │
├─────────────────────────────────────┤
│                                     │
│  ┌──────────┐      ┌──────────┐   │
│  │ 惰性删除 │      │ 定期删除 │   │
│  └──────────┘      └──────────┘   │
│       │                  │          │
│       ↓                  ↓          │
│  访问时检查        定时随机检查     │
│                                     │
└─────────────────────────────────────┘

优点：平衡 CPU 和内存
缺点：仍可能有过期 key 残留（通过内存淘汰解决）
```

### 3.2 过期时间设置最佳实践

```java
@Service
public class CacheService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * ✅ 推荐：设置过期时间 + 随机值（避免雪崩）
     */
    public void cacheWithRandomExpire(String key, String value) {
        // 基础过期时间：1小时
        long baseExpire = 3600;
        
        // 随机增加 0-300 秒（5分钟）
        long randomExpire = ThreadLocalRandom.current().nextInt(300);
        
        redisTemplate.opsForValue().set(
            key,
            value,
            baseExpire + randomExpire,
            TimeUnit.SECONDS
        );
    }
    
    /**
     * ✅ 热点数据：永不过期 + 异步更新
     */
    public String getHotData(String key) {
        String value = redisTemplate.opsForValue().get(key);
        
        if (value == null) {
            // 缓存未命中，查询数据库
            value = queryFromDB(key);
            
            // 写入缓存，不设置过期时间
            redisTemplate.opsForValue().set(key, value);
            
            // 异步定时更新（通过定时任务）
            scheduleRefresh(key);
        }
        
        return value;
    }
    
    /**
     * ❌ 不推荐：所有 key 相同过期时间
     */
    public void cacheWithSameExpire(String key, String value) {
        // 问题：大量 key 同时过期，导致缓存雪崩
        redisTemplate.opsForValue().set(key, value, 3600, TimeUnit.SECONDS);
    }
}
```

---

## 四、内存优化实战

### 4.1 Key 优化

**1. Key 命名规范**

```java
// ✅ 好的 Key 设计
"user:profile:123"           // 清晰的层级结构
"product:stock:456"          // 业务含义明确
"session:abc123"             // 简洁明了

// ❌ 不好的 Key 设计
"u123"                       // 不知道是什么
"user_profile_123"           // 下划线不如冒号清晰
"this_is_a_very_long_key_name_for_user_profile_123"  // 太长浪费内存

// Key 长度影响
// 假设 100万 个 key
// Key 长度 10 字节：10MB
// Key 长度 100 字节：100MB
// 差距：90MB
```

**2. 控制 Key 数量**

```java
// ❌ 方案1：每个商品一个 key（不推荐）
// Key: product:stock:1, product:stock:2, ...
// 问题：100万商品 = 100万个 key，内存开销大

// ✅ 方案2：使用 Hash 聚合（推荐）
// Key: product:stock
// Field: 1, 2, 3, ...
// 优势：只有 1 个 key，内存节省 50%+

@Service
public class ProductStockService {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private static final String STOCK_KEY = "product:stock";
    
    public void setStock(Long productId, int stock) {
        redisTemplate.opsForHash().put(
            STOCK_KEY,
            productId.toString(),
            stock
        );
    }
    
    public Integer getStock(Long productId) {
        Object stock = redisTemplate.opsForHash().get(STOCK_KEY, productId.toString());
        return stock == null ? null : Integer.parseInt(stock.toString());
    }
}
```

### 4.2 Value 优化

**1. 选择合适的数据结构**

```java
// 场景：存储用户信息

// ❌ 方案1：String 存储 JSON（不推荐）
String userJson = "{\"id\":1,\"name\":\"Alice\",\"age\":25}";
redisTemplate.opsForValue().set("user:1", userJson);
// 问题：
// 1. 修改单个字段需要反序列化整个对象
// 2. 内存占用大（JSON 格式冗余）

// ✅ 方案2：Hash 存储（推荐）
Map<String, String> user = new HashMap<>();
user.put("id", "1");
user.put("name", "Alice");
user.put("age", "25");
redisTemplate.opsForHash().putAll("user:1", user);
// 优势：
// 1. 可以单独修改字段
// 2. 内存占用小（ziplist 编码）
// 3. 操作灵活
```

**2. 控制集合大小**

```java
// ✅ 限制集合元素数量
@Service
public class FeedService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final int MAX_FEED_SIZE = 100;
    
    public void addFeed(Long userId, String feed) {
        String key = "user:feeds:" + userId;
        
        // 1. 添加到列表
        redisTemplate.opsForList().leftPush(key, feed);
        
        // 2. 保留最新的 100 条（避免无限增长）
        redisTemplate.opsForList().trim(key, 0, MAX_FEED_SIZE - 1);
    }
}

// ❌ 不限制大小的问题
// 用户动态无限增长 → 单个 key 占用数百 MB → 内存爆炸
```

**3. 压缩数据**

```java
@Service
public class CompressedCacheService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 压缩存储大对象
     */
    public void setCompressed(String key, String value) throws IOException {
        // 1. 压缩数据
        byte[] compressed = compress(value);
        
        // 2. Base64 编码
        String encoded = Base64.getEncoder().encodeToString(compressed);
        
        // 3. 存储
        redisTemplate.opsForValue().set(key, encoded);
    }
    
    public String getCompressed(String key) throws IOException {
        String encoded = redisTemplate.opsForValue().get(key);
        if (encoded == null) {
            return null;
        }
        
        // 1. Base64 解码
        byte[] compressed = Base64.getDecoder().decode(encoded);
        
        // 2. 解压缩
        return decompress(compressed);
    }
    
    private byte[] compress(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(bos);
        gzip.write(data.getBytes(StandardCharsets.UTF_8));
        gzip.close();
        return bos.toByteArray();
    }
    
    private String decompress(byte[] compressed) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
        GZIPInputStream gzip = new GZIPInputStream(bis);
        BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8));
        
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        
        return result.toString();
    }
}

// 压缩效果：
// 原始数据：100KB
// 压缩后：20KB
// 节省：80%
```

### 4.3 编码优化

**利用 Redis 的自动编码优化**

```bash
# Hash 编码配置
hash-max-ziplist-entries 512  # 字段数 < 512 使用 ziplist
hash-max-ziplist-value 64     # 值大小 < 64字节 使用 ziplist

# List 编码配置
list-max-ziplist-size -2      # 每个 quicklist 节点大小

# Set 编码配置
set-max-intset-entries 512    # 元素数 < 512 且都是整数，使用 intset

# ZSet 编码配置
zset-max-ziplist-entries 128  # 元素数 < 128 使用 ziplist
zset-max-ziplist-value 64     # 值大小 < 64字节 使用 ziplist
```

**示例：**

```java
// ✅ 利用 ziplist 优化
@Service
public class OptimizedCacheService {
    
    /**
     * 存储用户信息（利用 ziplist）
     */
    public void cacheUser(User user) {
        Map<String, String> fields = new HashMap<>();
        fields.put("id", user.getId().toString());
        fields.put("name", user.getName());
        fields.put("age", user.getAge().toString());
        
        // 字段数 < 512，每个值 < 64字节
        // Redis 会自动使用 ziplist 编码，节省内存
        redisTemplate.opsForHash().putAll("user:" + user.getId(), fields);
    }
    
    /**
     * ❌ 超过 ziplist 限制
     */
    public void cacheLargeUser(User user) {
        Map<String, String> fields = new HashMap<>();
        fields.put("id", user.getId().toString());
        fields.put("description", user.getDescription()); // 假设 > 64字节
        
        // 会使用 hashtable 编码，内存占用更大
        redisTemplate.opsForHash().putAll("user:" + user.getId(), fields);
    }
}
```

---

## 五、内存监控与诊断

### 5.1 内存分析命令

```bash
# 1. 查看内存使用情况
127.0.0.1:6379> INFO memory

# 2. 查看 key 的内存占用
127.0.0.1:6379> MEMORY USAGE user:1
(integer) 72

# 3. 查看 key 的编码方式
127.0.0.1:6379> OBJECT ENCODING user:1
"ziplist"

# 4. 查看 key 的空闲时间（LRU）
127.0.0.1:6379> OBJECT IDLETIME user:1
(integer) 300  # 300秒未访问

# 5. 分析内存使用（Redis 4.0+）
127.0.0.1:6379> MEMORY DOCTOR
Sam, I detected a few issues in your Redis instance:
* High memory fragmentation: 1.50
* Peak memory: 2.00G, current: 1.00G
```

### 5.2 大 Key 检测

```bash
# 使用 redis-cli 扫描大 key
redis-cli --bigkeys

# 输出示例：
-------- summary -------
Biggest string found: 'large:key' has 10485760 bytes
Biggest list found: 'large:list' has 100000 items
Biggest hash found: 'large:hash' has 50000 fields
```

**Java 实现大 Key 检测：**

```java
@Service
public class BigKeyDetector {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final long BIG_KEY_THRESHOLD = 10 * 1024; // 10KB
    
    /**
     * 扫描大 Key
     */
    public List<String> scanBigKeys(String pattern) {
        List<String> bigKeys = new ArrayList<>();
        
        ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(100)
            .build();
        
        Cursor<String> cursor = redisTemplate.scan(options);
        
        while (cursor.hasNext()) {
            String key = cursor.next();
            
            // 获取 key 的内存占用
            Long memoryUsage = redisTemplate.execute(
                (RedisCallback<Long>) connection -> 
                    connection.memoryUsage(key.getBytes())
            );
            
            if (memoryUsage != null && memoryUsage > BIG_KEY_THRESHOLD) {
                bigKeys.add(key + " (" + memoryUsage + " bytes)");
            }
        }
        
        return bigKeys;
    }
}
```

### 5.3 内存泄漏排查

**常见原因：**

1. **没有设置过期时间**
2. **集合无限增长**
3. **大 Key 问题**
4. **内存碎片**

**排查步骤：**

```java
@Service
public class MemoryLeakDetector {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 检测没有设置过期时间的 key
     */
    public List<String> findKeysWithoutExpire(String pattern) {
        List<String> keys = new ArrayList<>();
        
        ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(100)
            .build();
        
        Cursor<String> cursor = redisTemplate.scan(options);
        
        while (cursor.hasNext()) {
            String key = cursor.next();
            
            // 获取 TTL
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            
            if (ttl != null && ttl == -1) {
                // TTL = -1 表示没有设置过期时间
                keys.add(key);
            }
        }
        
        return keys;
    }
    
    /**
     * 检测异常增长的集合
     */
    public Map<String, Long> findGrowingCollections(String pattern) {
        Map<String, Long> collections = new HashMap<>();
        
        ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(100)
            .build();
        
        Cursor<String> cursor = redisTemplate.scan(options);
        
        while (cursor.hasNext()) {
            String key = cursor.next();
            
            // 获取集合大小
            Long size = getCollectionSize(key);
            
            if (size != null && size > 10000) {
                collections.put(key, size);
            }
        }
        
        return collections;
    }
    
    private Long getCollectionSize(String key) {
        DataType type = redisTemplate.type(key);
        
        if (type == null) {
            return null;
        }
        
        switch (type) {
            case LIST:
                return redisTemplate.opsForList().size(key);
            case SET:
                return redisTemplate.opsForSet().size(key);
            case ZSET:
                return redisTemplate.opsForZSet().size(key);
            case HASH:
                return redisTemplate.opsForHash().size(key);
            default:
                return null;
        }
    }
}
```

---

## 六、生产环境最佳实践

### 6.1 内存配置建议

```bash
# 1. 设置最大内存（必须）
maxmemory 4gb

# 2. 设置淘汰策略
maxmemory-policy allkeys-lru

# 3. 开启内存碎片整理
activedefrag yes

# 4. 禁用 swap（重要）
# 在 /etc/sysctl.conf 中设置
vm.swappiness = 0

# 5. 设置合理的持久化策略
# 如果内存紧张，可以关闭 RDB
save ""
# 只使用 AOF
appendonly yes
appendfsync everysec
```

### 6.2 监控指标

```java
@Component
public class RedisMonitor {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Scheduled(fixedRate = 60000) // 每分钟执行一次
    public void monitorMemory() {
        Properties info = redisTemplate.execute(
            (RedisCallback<Properties>) connection -> connection.info("memory")
        );
        
        if (info == null) {
            return;
        }
        
        // 1. 内存使用率
        long usedMemory = Long.parseLong(info.getProperty("used_memory"));
        long maxMemory = Long.parseLong(info.getProperty("maxmemory"));
        double memoryUsageRatio = (double) usedMemory / maxMemory;
        
        if (memoryUsageRatio > 0.8) {
            // 告警：内存使用率超过 80%
            log.warn("Redis memory usage is high: {}%", memoryUsageRatio * 100);
        }
        
        // 2. 内存碎片率
        long usedMemoryRss = Long.parseLong(info.getProperty("used_memory_rss"));
        double fragRatio = (double) usedMemoryRss / usedMemory;
        
        if (fragRatio > 1.5) {
            // 告警：内存碎片率过高
            log.warn("Redis memory fragmentation is high: {}", fragRatio);
        }
        
        // 3. 淘汰 key 数量
        long evictedKeys = Long.parseLong(info.getProperty("evicted_keys"));
        if (evictedKeys > 0) {
            log.info("Redis evicted {} keys", evictedKeys);
        }
    }
}
```

### 6.3 容量规划

```
容量规划公式：

总内存 = 数据内存 + 内存碎片 + 缓冲区 + 预留空间

示例：
- 预计数据量：10GB
- 内存碎片：20%（2GB）
- 缓冲区：10%（1GB）
- 预留空间：20%（2GB）

总内存 = 10 + 2 + 1 + 2 = 15GB

建议配置：16GB 内存，maxmemory 设置为 12GB
```

---

## 七、面试重点

**Q1：Redis 的内存淘汰策略有哪些？**

**A：** 8种策略，分为三类：
1. **不淘汰**：noeviction
2. **所有key**：allkeys-lru、allkeys-lfu、allkeys-random
3. **设置过期的key**：volatile-lru、volatile-lfu、volatile-random、volatile-ttl

**生产环境推荐**：allkeys-lru

**Q2：Redis 的过期删除策略是什么？**

**A：** 惰性删除 + 定期删除
- **惰性删除**：访问时检查是否过期
- **定期删除**：每秒 10 次随机抽样删除

**Q3：如何优化 Redis 内存？**

**A：** 五个方向：
1. **Key 优化**：缩短 key 长度，使用 Hash 聚合
2. **Value 优化**：选择合适的数据结构，控制集合大小
3. **编码优化**：利用 ziplist、intset
4. **过期时间**：设置合理的 TTL，避免雪崩
5. **监控告警**：定期检测大 key、内存碎片

---

**下一步学习**：[04-Redis网络模型与单线程原理.md](./04-Redis网络模型与单线程原理.md)
