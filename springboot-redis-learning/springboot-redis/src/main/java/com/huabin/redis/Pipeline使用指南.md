# Redis Pipeline 使用指南

## 一、快速开始

### 1.1 启动应用

```bash
# 确保 Redis 已启动
redis-server

# 启动 Spring Boot 应用
mvn spring-boot:run
```

### 1.2 测试接口

#### 性能对比测试

```bash
# 测试 1000 条数据的性能对比
curl "http://localhost:8080/api/pipeline/performance?size=1000"

# 响应示例
{
  "dataSize": 1000,
  "withoutPipeline": "450ms",
  "withPipeline": "15ms",
  "improvement": "30.00 倍"
}
```

#### 批量插入用户

```bash
# 批量插入 100 个用户
curl -X POST "http://localhost:8080/api/pipeline/users/batch?count=100"

# 响应示例
{
  "success": true,
  "count": 100,
  "cost": "25ms"
}
```

#### 批量查询用户

```bash
# 批量查询用户
curl "http://localhost:8080/api/pipeline/users/batch?ids=1,2,3,4,5"

# 响应示例
{
  "success": true,
  "users": [
    {
      "id": 1,
      "name": "User1",
      "email": "user1@example.com",
      "age": 21
    },
    ...
  ],
  "cost": "5ms"
}
```

#### 批量增加商品浏览量

```bash
# 批量增加商品浏览量
curl -X POST "http://localhost:8080/api/pipeline/products/views?ids=1,2,3,4,5"

# 响应示例
{
  "success": true,
  "count": 5,
  "cost": "3ms"
}
```

#### 用户登录（混合操作）

```bash
# 用户登录
curl -X POST "http://localhost:8080/api/pipeline/login?userId=user123"

# 响应示例
{
  "success": true,
  "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "cost": "2ms"
}
```

#### 秒杀商品预热

```bash
# 预热 1000 个秒杀商品
curl -X POST "http://localhost:8080/api/pipeline/seckill/preload?count=1000"

# 响应示例
{
  "success": true,
  "count": 1000,
  "cost": "120ms"
}
```

#### 批量查询秒杀库存

```bash
# 查询秒杀库存
curl "http://localhost:8080/api/pipeline/seckill/stock?ids=1,10,100,500,1000"

# 响应示例
{
  "success": true,
  "stocks": {
    "1": 101,
    "10": 110,
    "100": 200,
    "500": 100,
    "1000": 100
  },
  "cost": "4ms"
}
```

## 二、单元测试

### 2.1 运行所有测试

```bash
# 运行所有 Pipeline 测试
mvn test -Dtest=PipelineServiceTest
```

### 2.2 运行单个测试

```bash
# 性能对比测试
mvn test -Dtest=PipelineServiceTest#testPerformanceComparison

# 批量插入测试
mvn test -Dtest=PipelineServiceTest#testBatchInsertUsers

# 批量查询测试
mvn test -Dtest=PipelineServiceTest#testBatchGetUsers

# 秒杀预热测试
mvn test -Dtest=PipelineServiceTest#testSeckillPreload

# 综合性能测试
mvn test -Dtest=PipelineServiceTest#testComprehensivePerformance
```

## 三、核心代码示例

### 3.1 基本用法

```java
@Autowired
private StringRedisTemplate redisTemplate;

// 使用 Pipeline 批量写入
public void batchSet(Map<String, String> data) {
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
}
```

### 3.2 批量读取

```java
// 使用 Pipeline 批量读取
public List<String> batchGet(List<String> keys) {
    List<Object> results = redisTemplate.executePipelined(new RedisCallback<Object>() {
        @Override
        public Object doInRedis(RedisConnection connection) throws DataAccessException {
            for (String key : keys) {
                connection.get(key.getBytes());
            }
            return null;
        }
    });
    
    // 处理结果
    List<String> values = new ArrayList<>();
    for (Object result : results) {
        if (result != null) {
            values.add(new String((byte[]) result));
        }
    }
    return values;
}
```

### 3.3 分批执行

```java
// 分批执行 Pipeline（推荐）
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

## 四、性能测试结果

### 4.1 本地环境测试

**测试环境：**
- Redis: 本地部署
- RTT: ~0.1ms
- 数据: 10000 条 key-value

**测试结果：**

| 操作类型 | 不使用 Pipeline | 使用 Pipeline | 性能提升 |
|---------|---------------|--------------|---------|
| 批量写入 (10000) | 1500ms | 50ms | 30 倍 |
| 批量读取 (10000) | 1200ms | 45ms | 26 倍 |
| 批量计数 (10000) | 1300ms | 48ms | 27 倍 |
| 批量删除 (10000) | 1100ms | 42ms | 26 倍 |

### 4.2 远程环境测试

**测试环境：**
- Redis: 远程服务器
- RTT: ~1ms
- 数据: 10000 条 key-value

**测试结果：**

| 操作类型 | 不使用 Pipeline | 使用 Pipeline | 性能提升 |
|---------|---------------|--------------|---------|
| 批量写入 (10000) | 12000ms | 100ms | 120 倍 |
| 批量读取 (10000) | 11500ms | 95ms | 121 倍 |
| 批量计数 (10000) | 11800ms | 98ms | 120 倍 |
| 批量删除 (10000) | 11200ms | 92ms | 121 倍 |

**结论：网络延迟越大，Pipeline 优势越明显！**

## 五、最佳实践

### 5.1 何时使用 Pipeline

✅ **适合使用 Pipeline 的场景：**
- 批量读写操作
- 不需要原子性保证
- 追求极致性能
- 命令之间无依赖关系

❌ **不适合使用 Pipeline 的场景：**
- 需要原子性保证（使用事务或 Lua 脚本）
- 需要使用前一个命令的结果
- 单次操作（没有批量需求）

### 5.2 控制批次大小

```java
// ✅ 推荐：分批执行，每批 500-1000 个命令
int batchSize = 500;
for (int i = 0; i < totalSize; i += batchSize) {
    // 执行一批 Pipeline 操作
}

// ❌ 不推荐：一次性执行过多命令
// 可能导致内存占用过高，阻塞 Redis 服务器
```

### 5.3 错误处理

```java
// Pipeline 不保证原子性，需要注意错误处理
try {
    redisTemplate.executePipelined(new RedisCallback<Object>() {
        @Override
        public Object doInRedis(RedisConnection connection) throws DataAccessException {
            // 批量操作
            return null;
        }
    });
} catch (Exception e) {
    // 记录日志，进行补偿操作
    log.error("Pipeline 执行失败", e);
}
```

### 5.4 性能优化建议

1. **合理设置批次大小**
   - 单次 Pipeline 不超过 1000 个命令
   - 根据网络环境和数据大小调整

2. **避免大 Value**
   - 单个 Value 不超过 10KB
   - 大数据考虑分片存储

3. **使用连接池**
   - 配置合理的连接池参数
   - 避免频繁创建连接

4. **监控性能指标**
   - 监控 Pipeline 执行时间
   - 监控 Redis 内存使用
   - 监控网络 IO

## 六、常见问题

### Q1: Pipeline 和事务有什么区别？

**A:** 
- **Pipeline**: 批量发送命令，减少网络往返，**不保证原子性**
- **事务**: 保证原子性，但需要 2 次网络往返（MULTI + EXEC）

### Q2: Pipeline 能否获取中间结果？

**A:** 
不能。Pipeline 中的命令返回值都是 null，只能在 Pipeline 执行完成后获取所有结果。

### Q3: Pipeline 单次最多能执行多少命令？

**A:** 
理论上没有限制，但建议：
- 单次不超过 1000 个命令
- 避免占用过多内存
- 避免阻塞 Redis 服务器时间过长

### Q4: Pipeline 执行失败怎么办？

**A:** 
Pipeline 不保证原子性，部分命令可能成功，部分失败。建议：
- 记录详细日志
- 实现补偿机制
- 如需原子性，使用事务或 Lua 脚本

## 七、参考资料

- [Redis Pipeline 官方文档](https://redis.io/topics/pipelining)
- [Spring Data Redis 文档](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [04-Redis单线程模型与IO多路复用.md](./04-Redis单线程模型与IO多路复用.md)

## 八、示例代码位置

```
src/main/java/com/huabin/redis/
├── service/
│   ├── PipelineService.java              # 基础 Pipeline 操作
│   └── PipelineAdvancedService.java      # 高级 Pipeline 操作
├── controller/
│   └── PipelineController.java           # HTTP 接口
└── model/
    └── User.java                          # 用户实体类

src/test/java/com/huabin/redis/
└── PipelineServiceTest.java              # 单元测试
```
