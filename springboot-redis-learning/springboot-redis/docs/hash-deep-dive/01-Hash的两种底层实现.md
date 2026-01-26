# Hash 的两种底层实现

## 一、为什么需要两种实现？

Redis Hash 根据数据量和数据大小，会自动选择两种不同的底层实现：

| 实现方式 | 内存占用 | 查询性能 | 适用场景 | 典型应用 |
|---------|---------|---------|---------|---------|
| **ziplist** | 低（节省 60%+） | O(N) | 小对象存储 | 用户信息、商品属性 |
| **hashtable** | 高 | O(1) | 大对象存储 | 大型配置、复杂对象 |

**核心思想**：
- **ziplist**：用时间换空间（内存优先）
- **hashtable**：用空间换时间（性能优先）

## 二、编码选择策略

### 2.1 实战演示

```bash
# 场景 1：小对象 → ziplist
127.0.0.1:6379> HSET user:1 name "Alice" age "25" city "Beijing"
(integer) 3
127.0.0.1:6379> OBJECT ENCODING user:1
"ziplist"

# 场景 2：字段数量超过阈值 → hashtable
127.0.0.1:6379> HSET user:2 field1 "value1" field2 "value2" ... field600 "value600"
(integer) 600
127.0.0.1:6379> OBJECT ENCODING user:2
"hashtable"

# 场景 3：单个值过大 → hashtable
127.0.0.1:6379> HSET user:3 name "Alice" description "这是一个超过64字节的很长很长很长很长很长很长很长很长很长很长的描述"
(integer) 2
127.0.0.1:6379> OBJECT ENCODING user:3
"hashtable"
```

### 2.2 转换条件（默认配置）

```
ziplist → hashtable 转换条件（满足任一即转换）：

条件 1：字段数量 >= 512
条件 2：单个值大小 >= 64 字节

⚠️ 重要特性：
- 转换是单向的，不可逆！
- 一旦转换为 hashtable，即使删除数据也不会转回 ziplist
```

### 2.3 配置参数

在 `redis.conf` 中可以调整转换阈值：

```conf
# 控制字段数量阈值（默认 512）
hash-max-ziplist-entries 512

# 控制单个值大小阈值（默认 64 字节）
hash-max-ziplist-value 64
```

## 三、内存占用对比

### 3.1 实际测试

存储 100 个用户对象，每个用户 5 个字段（name, age, city, email, phone）：

```bash
# 方案 1：使用 Hash（ziplist 编码）
127.0.0.1:6379> HSET user:1 name "Alice" age "25" city "Beijing" email "alice@example.com" phone "13800138000"
127.0.0.1:6379> MEMORY USAGE user:1
(integer) 128

# 方案 2：使用 Hash（hashtable 编码，强制转换）
127.0.0.1:6379> CONFIG SET hash-max-ziplist-entries 0
127.0.0.1:6379> HSET user:2 name "Bob" age "30" city "Shanghai" email "bob@example.com" phone "13900139000"
127.0.0.1:6379> MEMORY USAGE user:2
(integer) 312
```

**内存对比：**

| 存储方式 | 单个对象内存 | 100个对象内存 | 节省比例 |
|---------|------------|-------------|---------|
| ziplist | 128 字节 | 12.5 KB | - |
| hashtable | 312 字节 | 30.5 KB | **59%** |

## 四、性能对比

### 4.1 查询性能测试

```bash
# 测试 ziplist 查询性能（10个字段）
127.0.0.1:6379> HSET test:ziplist f1 v1 f2 v2 f3 v3 f4 v4 f5 v5 f6 v6 f7 v7 f8 v8 f9 v9 f10 v10
127.0.0.1:6379> HGET test:ziplist f10
"v10"
# 平均耗时：~0.05ms

# 测试 hashtable 查询性能（600个字段）
127.0.0.1:6379> HSET test:hashtable f1 v1 f2 v2 ... f600 v600
127.0.0.1:6379> HGET test:hashtable f600
"v600"
# 平均耗时：~0.01ms
```

**性能对比：**

| 编码方式 | 字段数量 | HGET 耗时 | HSET 耗时 | 适用场景 |
|---------|---------|----------|----------|---------|
| ziplist | < 100 | ~0.05ms | ~0.08ms | 小对象，内存敏感 |
| hashtable | > 500 | ~0.01ms | ~0.02ms | 大对象，性能敏感 |

## 五、如何选择？

### 5.1 决策树

```
开始
  ↓
字段数量 < 100？
  ↓ 是
单个值 < 64 字节？
  ↓ 是
对查询性能要求不高？
  ↓ 是
【选择 ziplist】→ 调大 hash-max-ziplist-entries
  ↓ 否
【选择 hashtable】→ 保持默认配置
```

### 5.2 典型场景推荐

| 场景 | 推荐编码 | 配置建议 |
|------|---------|---------|
| 用户基本信息（5-10个字段） | ziplist | entries=512, value=64 |
| 商品属性（10-20个字段） | ziplist | entries=512, value=128 |
| 购物车（动态增长） | hashtable | 默认配置 |
| 会话数据（大量字段） | hashtable | 默认配置 |
| 配置中心（少量大值） | hashtable | 默认配置 |

## 六、实战建议

### 6.1 内存优化优先

如果你的场景是：
- ✅ 内存紧张，成本敏感
- ✅ 对象字段数量较少（< 100）
- ✅ 单个值较小（< 64 字节）
- ✅ 查询 QPS 不高（< 1000）

**建议**：调大 `hash-max-ziplist-entries` 到 1024 或更高

```conf
hash-max-ziplist-entries 1024
hash-max-ziplist-value 128
```

### 6.2 性能优化优先

如果你的场景是：
- ✅ 查询 QPS 很高（> 10000）
- ✅ 对象字段数量很多（> 500）
- ✅ 需要频繁更新
- ✅ 内存充足

**建议**：使用默认配置或调小阈值

```conf
hash-max-ziplist-entries 256
hash-max-ziplist-value 64
```

## 七、验证编码类型

### 7.1 查看当前编码

```bash
# 方法 1：OBJECT ENCODING
127.0.0.1:6379> OBJECT ENCODING user:1
"ziplist"

# 方法 2：DEBUG OBJECT（更详细）
127.0.0.1:6379> DEBUG OBJECT user:1
Value at:0x7f8b8c0a3e00 refcount:1 encoding:ziplist serializedlength:85 lru:12345678
```

### 7.2 批量检查

```bash
# 使用 SCAN 遍历所有 Hash 键
127.0.0.1:6379> SCAN 0 MATCH user:* TYPE hash
1) "0"
2) 1) "user:1"
   2) "user:2"
   3) "user:3"

# 检查每个键的编码
127.0.0.1:6379> OBJECT ENCODING user:1
"ziplist"
127.0.0.1:6379> OBJECT ENCODING user:2
"hashtable"
```

## 八、总结

| 对比维度 | ziplist | hashtable |
|---------|---------|-----------|
| **内存占用** | ⭐⭐⭐⭐⭐ 极低 | ⭐⭐ 较高 |
| **查询性能** | ⭐⭐⭐ 一般（O(N)） | ⭐⭐⭐⭐⭐ 极快（O(1)） |
| **写入性能** | ⭐⭐ 较慢 | ⭐⭐⭐⭐ 较快 |
| **适用字段数** | < 512 | >= 512 |
| **适用值大小** | < 64 字节 | 任意大小 |
| **典型场景** | 用户信息、商品属性 | 购物车、会话数据 |

**核心原则**：
- 内存紧张 → ziplist
- 性能优先 → hashtable
- 不确定 → 先用 ziplist，让 Redis 自动转换
