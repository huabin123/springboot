# 04-Eureka故障转移机制深度剖析

## 一、问题场景分析

### 1.1 典型故障场景

在生产环境中，经常会遇到以下场景：

```
场景描述：
1. 服务实例A（192.168.1.100:8080）正常运行并注册到Eureka
2. 服务实例B（192.168.1.101:8080）正常运行并注册到Eureka
3. 服务实例A突然宕机（JVM崩溃、服务器断电、网络故障等）
4. Eureka Server的缓存中仍然保留着实例A的注册信息
5. 客户端从Eureka获取的服务列表中仍包含已宕机的实例A
6. 请求可能被路由到已宕机的实例A，导致请求失败
```

### 1.2 问题的本质

这个问题的核心在于**分布式系统的最终一致性**：

- **Eureka的设计理念**：AP模型（可用性 + 分区容错性），牺牲强一致性
- **服务剔除延迟**：从服务宕机到Eureka剔除该实例需要时间
- **客户端缓存**：Eureka Client本地缓存服务列表，定期更新

### 1.3 时间窗口分析

```
服务宕机 → Eureka Server检测到 → 剔除实例 → Client刷新缓存 → 停止路由到故障实例

时间线：
T0: 服务实例宕机
T1: 最后一次心跳超时（默认90秒）
T2: Eureka Server剔除实例
T3: Client下次刷新缓存（默认30秒）
T4: 客户端停止路由到该实例

总延迟 = T4 - T0 ≈ 90秒 + 30秒 = 120秒（最坏情况）
```

## 二、Eureka的故障检测机制

### 2.1 心跳机制（Heartbeat）

#### 2.1.1 心跳发送

```java
// Eureka Client配置
eureka:
  instance:
    lease-renewal-interval-in-seconds: 30  # 心跳间隔，默认30秒
    lease-expiration-duration-in-seconds: 90  # 租约过期时间，默认90秒
```

**工作原理**：
1. Eureka Client每隔30秒向Eureka Server发送一次心跳
2. Eureka Server收到心跳后，更新该实例的最后心跳时间
3. 如果90秒内没有收到心跳，Eureka Server认为该实例已失效

#### 2.1.2 心跳续约源码分析

```java
// DiscoveryClient.java - Eureka Client核心类
private class HeartbeatThread implements Runnable {
    public void run() {
        if (renew()) {
            lastSuccessfulHeartbeatTimestamp = System.currentTimeMillis();
        }
    }
}

boolean renew() {
    EurekaHttpResponse<InstanceInfo> httpResponse;
    try {
        httpResponse = eurekaTransport.registrationClient.sendHeartBeat(
            instanceInfo.getAppName(),
            instanceInfo.getId(),
            instanceInfo,
            null
        );
        if (httpResponse.getStatusCode() == 404) {
            // 心跳失败，重新注册
            return register();
        }
        return httpResponse.getStatusCode() == 200;
    } catch (Throwable e) {
        return false;
    }
}
```

### 2.2 服务剔除机制（Eviction）

#### 2.2.1 剔除任务

```java
// AbstractInstanceRegistry.java - Eureka Server核心类
public void evict() {
    if (!isLeaseExpirationEnabled()) {
        return;  // 自我保护模式下不剔除
    }
    
    List<Lease<InstanceInfo>> expiredLeases = new ArrayList<>();
    for (Entry<String, Map<String, Lease<InstanceInfo>>> entry : registry.entrySet()) {
        for (Entry<String, Lease<InstanceInfo>> leaseEntry : entry.getValue().entrySet()) {
            Lease<InstanceInfo> lease = leaseEntry.getValue();
            if (lease.isExpired()) {
                expiredLeases.add(lease);
            }
        }
    }
    
    // 剔除过期实例
    for (Lease<InstanceInfo> lease : expiredLeases) {
        String appName = lease.getHolder().getAppName();
        String id = lease.getHolder().getId();
        internalCancel(appName, id, false);
    }
}
```

#### 2.2.2 剔除配置

```yaml
eureka:
  server:
    eviction-interval-timer-in-ms: 60000  # 剔除任务执行间隔，默认60秒
    renewal-percent-threshold: 0.85  # 自我保护阈值，默认85%
    enable-self-preservation: true  # 是否开启自我保护，默认true
```

### 2.3 自我保护机制（Self Preservation）

#### 2.3.1 触发条件

```
自我保护触发条件：
实际收到的心跳数 < 期望心跳数 × 续约百分比阈值

期望心跳数 = 注册实例数 × 2（每分钟2次心跳）
续约百分比阈值 = 0.85（默认）

示例：
- 注册实例数：100
- 期望心跳数：100 × 2 = 200次/分钟
- 阈值心跳数：200 × 0.85 = 170次/分钟
- 如果实际心跳数 < 170，触发自我保护
```

#### 2.3.2 自我保护的影响

**优点**：
- 防止网络分区导致的大规模服务剔除
- 保护已注册的服务实例信息

**缺点**：
- 已宕机的实例不会被及时剔除
- 客户端可能获取到失效的服务实例

## 三、客户端缓存机制

### 3.1 缓存层级

```
三级缓存架构：

1. Eureka Server Registry（注册表）
   ├─ ReadWriteCacheMap（读写缓存）- 实时数据
   └─ ReadOnlyCacheMap（只读缓存）- 定期同步

2. Eureka Client Local Cache（本地缓存）
   └─ 定期从Server拉取更新

3. Ribbon LoadBalancer Cache（负载均衡器缓存）
   └─ 从Client缓存获取服务列表
```

### 3.2 Server端缓存

```java
// ResponseCacheImpl.java
public class ResponseCacheImpl {
    // 读写缓存 - 实时更新
    private final LoadingCache<Key, Value> readWriteCacheMap;
    
    // 只读缓存 - 定期同步
    private final ConcurrentMap<Key, Value> readOnlyCacheMap;
    
    // 同步任务
    private TimerTask getCacheUpdateTask() {
        return new TimerTask() {
            public void run() {
                for (Key key : readOnlyCacheMap.keySet()) {
                    Value cacheValue = readWriteCacheMap.get(key);
                    Value currentCacheValue = readOnlyCacheMap.get(key);
                    if (cacheValue != currentCacheValue) {
                        readOnlyCacheMap.put(key, cacheValue);
                    }
                }
            }
        };
    }
}
```

**配置参数**：

```yaml
eureka:
  server:
    response-cache-update-interval-ms: 30000  # 只读缓存更新间隔，默认30秒
    response-cache-auto-expiration-in-seconds: 180  # 缓存过期时间，默认180秒
    use-read-only-response-cache: true  # 是否使用只读缓存，默认true
```

### 3.3 Client端缓存

```java
// DiscoveryClient.java
class CacheRefreshThread implements Runnable {
    public void run() {
        refreshRegistry();
    }
}

void refreshRegistry() {
    try {
        // 从Server获取增量更新
        boolean success = fetchRegistry(false);
        if (!success) {
            // 增量更新失败，全量获取
            fetchRegistry(true);
        }
    } catch (Throwable e) {
        logger.error("Cannot fetch registry from server", e);
    }
}
```

**配置参数**：

```yaml
eureka:
  client:
    registry-fetch-interval-seconds: 30  # 拉取注册表间隔，默认30秒
    disable-delta: false  # 是否禁用增量更新，默认false
    cache-refresh-executor-thread-pool-size: 2  # 缓存刷新线程池大小
```

## 四、故障转移的完整流程

### 4.1 正常情况下的请求流程

```
1. Client发起请求
   ↓
2. Ribbon从本地缓存获取服务实例列表
   ↓
3. 根据负载均衡策略选择一个实例
   ↓
4. 发送HTTP请求到选中的实例
   ↓
5. 返回响应
```

### 4.2 故障情况下的请求流程

```
1. Client发起请求
   ↓
2. Ribbon选择实例A（已宕机但未剔除）
   ↓
3. 发送请求失败（连接超时/拒绝）
   ↓
4. 触发Ribbon重试机制
   ↓
5. 选择实例B（健康实例）
   ↓
6. 请求成功
```

### 4.3 关键时间节点

```
场景：服务实例在T0时刻宕机

T0: 服务宕机
├─ 0s: 实例A崩溃
│
T1: 心跳超时检测
├─ 0-90s: 等待心跳超时（lease-expiration-duration-in-seconds）
│
T2: Server剔除实例
├─ 90-150s: 等待剔除任务执行（eviction-interval-timer-in-ms）
│
T3: Server缓存更新
├─ 150-180s: 只读缓存同步（response-cache-update-interval-ms）
│
T4: Client缓存更新
├─ 180-210s: Client拉取更新（registry-fetch-interval-seconds）
│
T5: Ribbon缓存更新
├─ 210-240s: Ribbon刷新服务列表（ServerListRefreshInterval）

总延迟：最长可达240秒（4分钟）
```

## 五、Ribbon的重试机制

### 5.1 Ribbon重试配置

```yaml
# 全局配置
ribbon:
  ConnectTimeout: 1000  # 连接超时时间（毫秒）
  ReadTimeout: 3000  # 读取超时时间（毫秒）
  MaxAutoRetries: 1  # 同一实例最大重试次数（不包括首次）
  MaxAutoRetriesNextServer: 2  # 切换实例的最大次数
  OkToRetryOnAllOperations: false  # 是否对所有操作都重试
  
# 针对特定服务的配置
user-service:
  ribbon:
    ConnectTimeout: 1000
    ReadTimeout: 3000
    MaxAutoRetries: 0
    MaxAutoRetriesNextServer: 1
    OkToRetryOnAllOperations: false
```

### 5.2 重试次数计算

```
总请求次数 = (MaxAutoRetries + 1) × (MaxAutoRetriesNextServer + 1)

示例1：
MaxAutoRetries = 1
MaxAutoRetriesNextServer = 2
总请求次数 = (1 + 1) × (2 + 1) = 6次

请求顺序：
1. 首次请求实例A
2. 重试实例A（MaxAutoRetries = 1）
3. 切换到实例B并请求
4. 重试实例B
5. 切换到实例C并请求
6. 重试实例C
```

### 5.3 重试策略源码

```java
// RibbonLoadBalancedRetryPolicy.java
public boolean canRetry(LoadBalancedRetryContext context) {
    HttpMethod method = context.getRequest().getMethod();
    return HttpMethod.GET == method || lbContext.isOkToRetryOnAllOperations();
}

public boolean canRetrySameServer(LoadBalancedRetryContext context) {
    return sameServerCount < lbContext.getRetryHandler().getMaxRetriesOnSameServer() 
           && canRetry(context);
}

public boolean canRetryNextServer(LoadBalancedRetryContext context) {
    return nextServerCount < lbContext.getRetryHandler().getMaxRetriesOnNextServer() 
           && canRetry(context);
}
```

### 5.4 重试的注意事项

**⚠️ 重要提示**：

1. **幂等性要求**：
   - 默认只对GET请求重试
   - POST/PUT/DELETE需要保证幂等性才能开启重试
   - 设置`OkToRetryOnAllOperations=true`需谨慎

2. **超时时间设置**：
   ```
   总超时时间 = (ConnectTimeout + ReadTimeout) × 总请求次数
   
   示例：
   ConnectTimeout = 1000ms
   ReadTimeout = 3000ms
   总请求次数 = 6次
   最大总超时 = (1000 + 3000) × 6 = 24秒
   ```

3. **Hystrix超时配置**：
   ```yaml
   hystrix:
     command:
       default:
         execution:
           isolation:
             thread:
               timeoutInMilliseconds: 30000  # 必须大于Ribbon总超时时间
   ```

## 六、故障转移的优化策略

### 6.1 快速故障检测

#### 方案1：缩短心跳和剔除时间

```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 5  # 心跳间隔改为5秒
    lease-expiration-duration-in-seconds: 15  # 过期时间改为15秒
  server:
    eviction-interval-timer-in-ms: 5000  # 剔除任务改为5秒执行一次
    response-cache-update-interval-ms: 3000  # 缓存更新改为3秒
  client:
    registry-fetch-interval-seconds: 5  # 拉取间隔改为5秒
```

**优点**：故障检测更快（从120秒降至30秒左右）  
**缺点**：网络开销增大，Server压力增加

#### 方案2：关闭自我保护（生产慎用）

```yaml
eureka:
  server:
    enable-self-preservation: false  # 关闭自我保护
```

**优点**：故障实例会被及时剔除  
**缺点**：网络抖动可能导致大量误剔除

### 6.2 客户端快速失败

#### 方案1：优化超时配置

```yaml
ribbon:
  ConnectTimeout: 500  # 连接超时500ms
  ReadTimeout: 2000  # 读取超时2秒
  MaxAutoRetries: 0  # 不在同一实例重试
  MaxAutoRetriesNextServer: 2  # 最多切换2次实例
```

#### 方案2：使用Hystrix熔断

```yaml
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 3000
      circuitBreaker:
        enabled: true
        requestVolumeThreshold: 10  # 10个请求后开始统计
        errorThresholdPercentage: 50  # 错误率50%触发熔断
        sleepWindowInMilliseconds: 5000  # 熔断5秒后尝试恢复
```

### 6.3 健康检查增强

#### 方案1：自定义健康检查

```java
@Component
public class CustomHealthCheckHandler implements HealthCheckHandler {
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus) {
        // 检查数据库连接
        try {
            Connection conn = dataSource.getConnection();
            conn.close();
        } catch (Exception e) {
            return InstanceInfo.InstanceStatus.DOWN;
        }
        
        // 检查其他依赖服务
        // ...
        
        return InstanceInfo.InstanceStatus.UP;
    }
}

// 注册健康检查
@Configuration
public class EurekaConfig {
    @Bean
    public EurekaInstanceConfigBean eurekaInstanceConfig(CustomHealthCheckHandler healthCheckHandler) {
        EurekaInstanceConfigBean config = new EurekaInstanceConfigBean();
        config.setHealthCheckHandler(healthCheckHandler);
        return config;
    }
}
```

#### 方案2：集成Spring Boot Actuator

```yaml
eureka:
  client:
    healthcheck:
      enabled: true  # 启用健康检查
      
management:
  endpoint:
    health:
      show-details: always
  health:
    defaults:
      enabled: true
```

### 6.4 使用Ribbon的Ping机制

```java
@Configuration
public class RibbonConfig {
    
    @Bean
    public IPing ribbonPing() {
        return new PingUrl(false, "/actuator/health");  // 使用HTTP Ping
    }
    
    @Bean
    public IRule ribbonRule() {
        return new AvailabilityFilteringRule();  // 过滤故障实例
    }
}
```

```yaml
user-service:
  ribbon:
    NFLoadBalancerPingClassName: com.netflix.loadbalancer.PingUrl
    NFLoadBalancerPingInterval: 5  # 每5秒Ping一次
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.AvailabilityFilteringRule
```

## 七、故障转移的监控与告警

### 7.1 关键指标监控

```java
@Component
public class EurekaMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 监控心跳失败次数
    public void recordHeartbeatFailure(String serviceName) {
        meterRegistry.counter("eureka.heartbeat.failure", 
            "service", serviceName).increment();
    }
    
    // 监控实例剔除事件
    public void recordEviction(String serviceName, String instanceId) {
        meterRegistry.counter("eureka.instance.evicted",
            "service", serviceName,
            "instance", instanceId).increment();
    }
    
    // 监控请求重试次数
    public void recordRetry(String serviceName, int retryCount) {
        meterRegistry.counter("ribbon.retry.count",
            "service", serviceName,
            "count", String.valueOf(retryCount)).increment();
    }
}
```

### 7.2 日志增强

```yaml
logging:
  level:
    com.netflix.discovery: DEBUG  # Eureka Client日志
    com.netflix.eureka: DEBUG  # Eureka Server日志
    com.netflix.loadbalancer: DEBUG  # Ribbon日志
```

### 7.3 告警规则

```
1. 心跳失败率告警
   - 指标：eureka.heartbeat.failure
   - 阈值：1分钟内失败次数 > 5
   - 级别：WARNING

2. 实例剔除告警
   - 指标：eureka.instance.evicted
   - 阈值：任何剔除事件
   - 级别：CRITICAL

3. 重试次数告警
   - 指标：ribbon.retry.count
   - 阈值：1分钟内重试次数 > 100
   - 级别：WARNING

4. 自我保护模式告警
   - 指标：eureka.server.self-preservation-mode
   - 阈值：进入自我保护模式
   - 级别：WARNING
```

## 八、总结

### 8.1 故障转移的核心机制

1. **心跳检测**：Client定期向Server发送心跳，Server检测超时
2. **服务剔除**：Server定期清理过期实例
3. **缓存更新**：Server和Client的多级缓存逐层同步
4. **重试机制**：Ribbon在请求失败时自动切换实例重试

### 8.2 延迟来源

```
总延迟 = 心跳超时 + 剔除延迟 + Server缓存延迟 + Client缓存延迟 + Ribbon缓存延迟
默认配置：90s + 60s + 30s + 30s + 30s = 240s（4分钟）
优化配置：15s + 5s + 3s + 5s + 5s = 33s（半分钟）
```

### 8.3 优化建议

| 场景 | 配置策略 | 说明 |
|------|---------|------|
| 生产环境 | 适度优化 | 心跳15s，剔除10s，缓存5s |
| 测试环境 | 激进优化 | 心跳5s，剔除5s，缓存3s |
| 开发环境 | 默认配置 | 使用默认值即可 |

### 8.4 最佳实践

1. ✅ 启用健康检查，及时上报实例状态
2. ✅ 配置合理的重试策略，避免雪崩
3. ✅ 使用Hystrix熔断，快速失败
4. ✅ 监控关键指标，及时发现问题
5. ✅ 生产环境保持自我保护开启
6. ✅ 使用Ribbon的Ping机制主动探测
7. ⚠️ 谨慎缩短心跳时间，平衡性能和时效
8. ⚠️ 非幂等操作不要开启全量重试

---

**下一篇**：[05-Eureka负载均衡策略详解.md](./05-Eureka负载均衡策略详解.md)
