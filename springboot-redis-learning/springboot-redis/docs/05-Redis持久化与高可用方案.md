# Redis 持久化与高可用方案

> **学习目标**：掌握 RDB、AOF 持久化机制，以及主从复制、哨兵、Cluster 集群方案。

## 一、持久化机制

### 1.1 RDB（Redis Database）

**原理：快照持久化**

```bash
# 触发方式

# 1. 手动触发
SAVE      # 阻塞主线程（不推荐）
BGSAVE    # 后台异步保存（推荐）

# 2. 自动触发（配置文件）
save 900 1      # 900秒内至少1个key变化
save 300 10     # 300秒内至少10个key变化
save 60 10000   # 60秒内至少10000个key变化
```

**RDB 文件格式：**

```
dump.rdb 文件结构：
├── REDIS 魔数（5字节）
├── 版本号（4字节）
├── 数据库编号
├── Key-Value 数据
│   ├── 过期时间（可选）
│   ├── 类型
│   ├── Key
│   └── Value
├── EOF 标志
└── CRC64 校验和
```

**优缺点：**

| 优点 | 缺点 |
|------|------|
| ✅ 文件紧凑，适合备份 | ❌ 数据丢失风险（最后一次快照后的数据） |
| ✅ 恢复速度快 | ❌ fork 子进程耗时（数据量大时） |
| ✅ 性能影响小 | ❌ 不适合实时持久化 |

### 1.2 AOF（Append Only File）

**原理：命令日志**

```bash
# 配置
appendonly yes
appendfilename "appendonly.aof"

# 同步策略
appendfsync always    # 每个命令都同步（最安全，最慢）
appendfsync everysec  # 每秒同步（推荐）
appendfsync no        # 由操作系统决定（最快，最不安全）
```

**AOF 文件内容：**

```bash
# 示例：执行 SET key value
*3
$3
SET
$3
key
$5
value
```

**AOF 重写：**

```bash
# 问题：AOF 文件会越来越大

# 解决：AOF 重写（压缩）
# 原理：根据当前内存数据生成最小命令集

# 触发方式
BGREWRITEAOF  # 手动触发

# 自动触发配置
auto-aof-rewrite-percentage 100  # 文件大小增长100%时重写
auto-aof-rewrite-min-size 64mb   # 文件至少64MB才重写
```

**优缺点：**

| 优点 | 缺点 |
|------|------|
| ✅ 数据安全性高 | ❌ 文件体积大 |
| ✅ 可读性好（文本格式） | ❌ 恢复速度慢 |
| ✅ 支持秒级持久化 | ❌ 性能影响稍大 |

### 1.3 混合持久化（Redis 4.0+）

```bash
# 开启混合持久化
aof-use-rdb-preamble yes

# 原理：
# AOF 重写时，将当前数据以 RDB 格式写入 AOF 文件开头
# 后续的增量命令以 AOF 格式追加

# 优势：
# - RDB 的快速恢复
# - AOF 的数据安全
```

### 1.4 持久化策略选择

```java
@Configuration
public class RedisPersistenceConfig {
    
    /**
     * 场景1：缓存场景（可以接受数据丢失）
     */
    public void cacheScenario() {
        // 配置：
        // - 关闭 AOF
        // - RDB：save 900 1
        // 
        // 理由：性能优先，数据丢失可以重新加载
    }
    
    /**
     * 场景2：数据重要（不能丢失）
     */
    public void criticalDataScenario() {
        // 配置：
        // - 开启 AOF：appendfsync everysec
        // - 开启混合持久化：aof-use-rdb-preamble yes
        // - RDB：save 900 1（作为备份）
        //
        // 理由：数据安全优先
    }
    
    /**
     * 场景3：高性能要求
     */
    public void highPerformanceScenario() {
        // 配置：
        // - 关闭持久化
        // - 使用主从复制保证可用性
        //
        // 理由：极致性能，通过集群保证可用性
    }
}
```

---

## 二、主从复制

### 2.1 架构

```
主从复制架构：

        ┌─────────┐
        │  Master │ (读写)
        └─────────┘
             │
      ┌──────┴──────┐
      │             │
  ┌───▼───┐   ┌───▼───┐
  │ Slave1│   │ Slave2│ (只读)
  └───────┘   └───────┘
```

### 2.2 配置

```bash
# Slave 配置
replicaof 192.168.1.100 6379  # 指定 Master
masterauth password            # Master 密码

# 只读模式（默认）
replica-read-only yes
```

**Java 配置：**

```java
@Configuration
public class RedisReplicationConfig {
    
    @Bean
    public LettuceConnectionFactory masterConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("192.168.1.100");
        config.setPort(6379);
        return new LettuceConnectionFactory(config);
    }
    
    @Bean
    public LettuceConnectionFactory slaveConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("192.168.1.101");
        config.setPort(6379);
        return new LettuceConnectionFactory(config);
    }
    
    /**
     * 读写分离：写主库，读从库
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(masterConnectionFactory());
        return template;
    }
    
    @Bean
    public RedisTemplate<String, Object> readOnlyRedisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(slaveConnectionFactory());
        return template;
    }
}
```

### 2.3 复制原理

```
全量复制流程：

1. Slave 发送 PSYNC 命令
2. Master 执行 BGSAVE 生成 RDB
3. Master 发送 RDB 文件给 Slave
4. Slave 加载 RDB 文件
5. Master 发送缓冲区的增量命令

增量复制流程：

1. Master 维护复制积压缓冲区（默认 1MB）
2. Slave 断线重连后发送 offset
3. 如果 offset 在缓冲区内，发送增量数据
4. 否则，进行全量复制
```

---

## 三、哨兵（Sentinel）

### 3.1 架构

```
哨兵架构：

    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │Sentinel1 │  │Sentinel2 │  │Sentinel3 │
    └─────┬────┘  └─────┬────┘  └─────┬────┘
          │             │             │
          └─────────────┼─────────────┘
                        │
        ┌───────────────┴───────────────┐
        │                               │
    ┌───▼───┐                     ┌─────▼──┐
    │ Master│                     │ Slave  │
    └───────┘                     └────────┘

功能：
- 监控：检测 Master 和 Slave 是否正常
- 通知：故障时通知管理员
- 自动故障转移：Master 宕机时自动选举新 Master
- 配置提供：客户端通过 Sentinel 获取 Master 地址
```

### 3.2 配置

```bash
# sentinel.conf

# 监控 Master
sentinel monitor mymaster 192.168.1.100 6379 2
# mymaster：Master 名称
# 192.168.1.100 6379：Master 地址
# 2：至少 2 个 Sentinel 认为 Master 下线才进行故障转移

# 判断下线时间
sentinel down-after-milliseconds mymaster 30000  # 30秒

# 故障转移超时时间
sentinel failover-timeout mymaster 180000  # 3分钟

# 同时进行同步的 Slave 数量
sentinel parallel-syncs mymaster 1
```

**Java 配置：**

```java
@Configuration
public class RedisSentinelConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisSentinelConfiguration sentinelConfig = new RedisSentinelConfiguration()
            .master("mymaster")
            .sentinel("192.168.1.101", 26379)
            .sentinel("192.168.1.102", 26379)
            .sentinel("192.168.1.103", 26379);
        
        return new LettuceConnectionFactory(sentinelConfig);
    }
}
```

### 3.3 故障转移流程

```
1. 主观下线（SDOWN）：
   - 单个 Sentinel 认为 Master 下线

2. 客观下线（ODOWN）：
   - 多数 Sentinel 认为 Master 下线

3. 选举 Leader Sentinel：
   - 使用 Raft 算法选举

4. 故障转移：
   - 从 Slave 中选择新 Master
   - 让其他 Slave 复制新 Master
   - 通知客户端新 Master 地址
```

---

## 四、Cluster 集群

### 4.1 架构

```
Cluster 架构（3主3从）：

    ┌─────────┐  ┌─────────┐  ┌─────────┐
    │Master1  │  │Master2  │  │Master3  │
    │Slot:    │  │Slot:    │  │Slot:    │
    │0-5460   │  │5461-    │  │10923-   │
    │         │  │10922    │  │16383    │
    └────┬────┘  └────┬────┘  └────┬────┘
         │            │            │
    ┌────▼────┐  ┌────▼────┐  ┌────▼────┐
    │ Slave1  │  │ Slave2  │  │ Slave3  │
    └─────────┘  └─────────┘  └─────────┘

特点：
- 无中心架构
- 数据分片（16384 个 slot）
- 自动故障转移
- 支持横向扩展
```

### 4.2 配置

```bash
# redis.conf

# 开启集群模式
cluster-enabled yes

# 集群配置文件
cluster-config-file nodes-6379.conf

# 节点超时时间
cluster-node-timeout 15000

# 故障转移投票时间
cluster-replica-validity-factor 10
```

**Java 配置：**

```java
@Configuration
public class RedisClusterConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        clusterConfig.addClusterNode(new RedisNode("192.168.1.101", 6379));
        clusterConfig.addClusterNode(new RedisNode("192.168.1.102", 6379));
        clusterConfig.addClusterNode(new RedisNode("192.168.1.103", 6379));
        clusterConfig.addClusterNode(new RedisNode("192.168.1.104", 6379));
        clusterConfig.addClusterNode(new RedisNode("192.168.1.105", 6379));
        clusterConfig.addClusterNode(new RedisNode("192.168.1.106", 6379));
        
        return new LettuceConnectionFactory(clusterConfig);
    }
}
```

### 4.3 数据分片

```java
// Slot 计算
public class ClusterSlotCalculator {
    
    /**
     * 计算 key 所属的 slot
     */
    public static int calculateSlot(String key) {
        // 1. 检查是否有 hash tag
        int start = key.indexOf('{');
        int end = key.indexOf('}', start + 1);
        
        if (start != -1 && end != -1 && end > start + 1) {
            // 使用 hash tag 内的内容计算
            key = key.substring(start + 1, end);
        }
        
        // 2. CRC16 算法
        int crc = CRC16.crc16(key.getBytes());
        
        // 3. 对 16384 取模
        return crc & 0x3FFF;  // 等价于 crc % 16384
    }
    
    /**
     * Hash Tag 示例
     */
    public void hashTagExample() {
        // 这些 key 会分配到同一个 slot
        String key1 = "user:{123}:profile";
        String key2 = "user:{123}:orders";
        String key3 = "user:{123}:cart";
        
        // 都使用 "123" 计算 slot
        int slot1 = calculateSlot(key1);
        int slot2 = calculateSlot(key2);
        int slot3 = calculateSlot(key3);
        
        // slot1 == slot2 == slot3
        // 优势：可以使用 MGET、事务等多 key 操作
    }
}
```

---

## 五、方案对比与选择

| 方案 | 可用性 | 性能 | 扩展性 | 复杂度 | 适用场景 |
|------|--------|------|--------|--------|----------|
| **单机** | ❌ | ⭐⭐⭐⭐⭐ | ❌ | ⭐ | 开发测试 |
| **主从** | ⭐⭐ | ⭐⭐⭐⭐ | ❌ | ⭐⭐ | 读多写少 |
| **哨兵** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ❌ | ⭐⭐⭐ | 中小型应用 |
| **Cluster** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 大型应用 |

---

## 六、面试重点

**Q1：RDB 和 AOF 的区别？**

| 特性 | RDB | AOF |
|------|-----|-----|
| 文件大小 | 小 | 大 |
| 恢复速度 | 快 | 慢 |
| 数据安全 | 低 | 高 |
| 性能影响 | 小 | 稍大 |

**Q2：Redis 如何保证高可用？**

**A：** 三种方案：
1. **主从复制**：读写分离，提高读性能
2. **哨兵**：自动故障转移
3. **Cluster**：数据分片 + 高可用

**Q3：Cluster 如何实现数据分片？**

**A：** 
- 16384 个 slot
- CRC16(key) % 16384 计算 slot
- 支持 Hash Tag 控制分片

---

**下一步学习**：[06-Redis生产实践与性能优化.md](./06-Redis生产实践与性能优化.md)
