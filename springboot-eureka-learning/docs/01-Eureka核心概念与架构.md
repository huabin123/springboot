# Eureka核心概念与架构

## 一、Eureka简介

### 1.1 什么是Eureka？

**Eureka**是Netflix开源的一款基于REST的服务发现框架，主要用于AWS云服务的中间层服务发现。Spring Cloud将其集成为Spring Cloud Netflix Eureka，成为Spring Cloud微服务架构中的核心组件之一。

**核心功能**：
- 服务注册（Service Registration）
- 服务发现（Service Discovery）
- 健康检查（Health Check）
- 负载均衡（Load Balance）

**应用场景**：
- 微服务架构中的服务治理
- 动态服务发现和路由
- 服务实例的自动注册和注销
- 服务健康状态监控

---

## 二、Eureka核心概念

### 2.1 核心角色

```
┌─────────────────────────────────────────────────────────┐
│                    Eureka架构图                          │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────────────────────────┐      │
│  │         Eureka Server（注册中心）              │      │
│  │  - 服务注册表（Registry）                      │      │
│  │  - 接收心跳（Heartbeat）                       │      │
│  │  - 服务剔除（Eviction）                        │      │
│  └──────────────────────────────────────────────┘      │
│           ▲                           ▲                 │
│           │                           │                 │
│    注册/续约/注销              拉取服务列表              │
│           │                           │                 │
│           │                           │                 │
│  ┌────────┴────────┐         ┌───────┴────────┐        │
│  │  Eureka Client  │         │  Eureka Client │        │
│  │  (服务提供者)    │────────▶│  (服务消费者)   │        │
│  │  Producer       │  调用   │  Consumer      │        │
│  └─────────────────┘         └────────────────┘        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

#### 1. Eureka Server（服务端）

**职责**：
- 提供服务注册和发现功能
- 维护服务注册表
- 接收服务实例的心跳
- 剔除不可用的服务实例
- 集群间同步服务注册信息

**核心数据结构**：
```java
// 服务注册表
ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>> registry;
// Key1: 服务名称（如：USER-SERVICE）
// Key2: 实例ID（如：192.168.1.100:user-service:8001）
// Value: 租约信息（包含实例信息和租约时间）
```

#### 2. Eureka Client（客户端）

**职责**：
- 向Eureka Server注册服务实例
- 定期发送心跳续约
- 从Eureka Server获取服务列表
- 缓存服务列表到本地
- 服务下线时注销实例

**两种角色**：
- **Service Provider（服务提供者）**：提供服务的应用
- **Service Consumer（服务消费者）**：调用服务的应用

---

### 2.2 核心术语

#### 1. 服务注册（Register）

服务实例启动时，向Eureka Server注册自己的信息。

```java
// 注册的信息包括：
{
    "instanceId": "192.168.1.100:user-service:8001",
    "app": "USER-SERVICE",
    "ipAddr": "192.168.1.100",
    "port": 8001,
    "status": "UP",
    "homePageUrl": "http://192.168.1.100:8001/",
    "healthCheckUrl": "http://192.168.1.100:8001/actuator/health",
    "metadata": {
        "zone": "zone1",
        "version": "1.0.0"
    }
}
```

#### 2. 服务续约（Renew/Heartbeat）

服务实例定期向Eureka Server发送心跳，表明自己仍然存活。

**默认配置**：
- 心跳间隔：30秒
- 租约过期时间：90秒

```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 30  # 心跳间隔
    lease-expiration-duration-in-seconds: 90  # 租约过期时间
```

#### 3. 服务下线（Cancel）

服务实例正常关闭时，主动向Eureka Server注销自己。

```java
// 触发时机：
// 1. 应用正常关闭（shutdown hook）
// 2. 手动调用注销接口
```

#### 4. 服务剔除（Eviction）

Eureka Server定期检查服务实例的租约是否过期，剔除过期的实例。

**默认配置**：
- 剔除间隔：60秒
- 剔除条件：90秒内未收到心跳

```yaml
eureka:
  server:
    eviction-interval-timer-in-ms: 60000  # 剔除间隔（毫秒）
```

#### 5. 服务发现（Fetch Registry）

服务消费者从Eureka Server获取服务提供者列表。

**获取方式**：
- 全量获取：首次启动时
- 增量获取：定期更新（默认30秒）

```yaml
eureka:
  client:
    registry-fetch-interval-seconds: 30  # 拉取间隔
```

#### 6. 自我保护模式（Self Preservation）

当Eureka Server在短时间内丢失过多客户端心跳时，进入自我保护模式。

**触发条件**：
- 15分钟内心跳续约比例低于85%

**保护措施**：
- 不再剔除任何服务实例
- 仍然接受新服务注册
- 仍然接受心跳续约

```yaml
eureka:
  server:
    enable-self-preservation: true  # 是否启用自我保护（默认true）
    renewal-percent-threshold: 0.85  # 续约比例阈值
```

---

## 三、Eureka架构设计

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Eureka整体架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────────────────────────────────────┐     │
│  │              Eureka Server Cluster                │     │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────┐ │     │
│  │  │ Eureka Server│  │ Eureka Server│  │ Eureka  │ │     │
│  │  │   Node 1     │◀─│   Node 2     │◀─│ Server  │ │     │
│  │  │ (主节点)      │─▶│ (从节点)      │─▶│ Node 3  │ │     │
│  │  └──────────────┘  └──────────────┘  └─────────┘ │     │
│  │         ▲                  ▲               ▲       │     │
│  │         │                  │               │       │     │
│  │         └──────────────────┴───────────────┘       │     │
│  │                   集群同步                          │     │
│  └───────────────────────────────────────────────────┘     │
│                          ▲                                  │
│                          │                                  │
│                    注册/续约/拉取                            │
│                          │                                  │
│  ┌───────────────────────┴───────────────────────────┐     │
│  │              Eureka Client Layer                  │     │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐        │     │
│  │  │ Service  │  │ Service  │  │ Service  │        │     │
│  │  │ Instance │  │ Instance │  │ Instance │        │     │
│  │  │    A     │  │    B     │  │    C     │        │     │
│  │  └──────────┘  └──────────┘  └──────────┘        │     │
│  └───────────────────────────────────────────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### 1. InstanceRegistry（实例注册表）

**职责**：管理所有服务实例的注册信息

**核心方法**：
```java
public interface InstanceRegistry {
    // 注册实例
    void register(InstanceInfo info, int leaseDuration, boolean isReplication);
    
    // 注销实例
    boolean cancel(String appName, String id, boolean isReplication);
    
    // 续约
    boolean renew(String appName, String id, boolean isReplication);
    
    // 获取所有实例
    Applications getApplications();
    
    // 获取增量信息
    Applications getApplicationDeltas();
}
```

**数据结构**：
```java
// 双层Map结构
ConcurrentHashMap<String, Map<String, Lease<InstanceInfo>>> registry;

// 第一层：服务名称 -> 实例Map
// 第二层：实例ID -> 租约信息

// 示例：
{
    "USER-SERVICE": {
        "192.168.1.100:user-service:8001": Lease<InstanceInfo>,
        "192.168.1.101:user-service:8002": Lease<InstanceInfo>
    },
    "ORDER-SERVICE": {
        "192.168.1.102:order-service:9001": Lease<InstanceInfo>
    }
}
```

#### 2. PeerAwareInstanceRegistry（集群感知注册表）

**职责**：处理Eureka Server集群间的同步

**核心功能**：
- 同步注册信息到其他节点
- 接收其他节点的同步请求
- 避免循环复制

```java
public class PeerAwareInstanceRegistryImpl implements PeerAwareInstanceRegistry {
    
    @Override
    public void register(InstanceInfo info, boolean isReplication) {
        // 1. 注册到本地
        super.register(info, isReplication);
        
        // 2. 如果不是复制操作，则同步到其他节点
        if (!isReplication) {
            replicateToPeers(Action.Register, info.getAppName(), info.getId(), info);
        }
    }
    
    private void replicateToPeers(Action action, String appName, String id, InstanceInfo info) {
        for (PeerEurekaNode node : peerEurekaNodes.getPeerEurekaNodes()) {
            // 异步复制到其他节点
            node.register(info);
        }
    }
}
```

#### 3. ResponseCache（响应缓存）

**职责**：缓存服务列表，提高查询性能

**缓存策略**：
- 只读缓存（ReadOnlyCache）：定期从读写缓存更新
- 读写缓存（ReadWriteCache）：使用Guava Cache，有过期时间

```java
public class ResponseCacheImpl implements ResponseCache {
    
    // 只读缓存
    private final ConcurrentMap<Key, Value> readOnlyCacheMap;
    
    // 读写缓存（Guava Cache）
    private final LoadingCache<Key, Value> readWriteCacheMap;
    
    // 缓存更新间隔（默认30秒）
    private long responseCacheUpdateIntervalMs = 30 * 1000;
}
```

**缓存更新流程**：
```
1. 服务注册/下线 → 失效读写缓存
2. 定时任务（30秒） → 读写缓存更新到只读缓存
3. 客户端拉取 → 从只读缓存读取
```

#### 4. EvictionTask（剔除任务）

**职责**：定期剔除过期的服务实例

```java
class EvictionTask extends TimerTask {
    
    @Override
    public void run() {
        try {
            // 获取补偿时间（考虑网络延迟）
            long compensationTimeMs = getCompensationTimeMs();
            
            // 遍历所有服务实例
            for (Entry<String, Map<String, Lease<InstanceInfo>>> entry : registry.entrySet()) {
                for (Entry<String, Lease<InstanceInfo>> leaseEntry : entry.getValue().entrySet()) {
                    Lease<InstanceInfo> lease = leaseEntry.getValue();
                    
                    // 判断是否过期
                    if (lease.isExpired(compensationTimeMs)) {
                        // 剔除过期实例
                        internalCancel(appName, id, false);
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("Could not run the evict task", e);
        }
    }
}
```

---

### 3.3 服务注册流程

```
┌─────────────┐                                    ┌─────────────┐
│   Client    │                                    │   Server    │
│  (服务实例)  │                                    │ (注册中心)   │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │  1. POST /eureka/apps/{APP_NAME}                │
       │     Body: InstanceInfo                          │
       ├────────────────────────────────────────────────▶│
       │                                                  │
       │                                         2. 验证实例信息
       │                                         3. 添加到注册表
       │                                         4. 失效缓存
       │                                         5. 同步到其他节点
       │                                                  │
       │  6. 返回 204 No Content                         │
       │◀────────────────────────────────────────────────┤
       │                                                  │
       │  7. 定期发送心跳（默认30秒）                     │
       │     PUT /eureka/apps/{APP}/{ID}                 │
       ├────────────────────────────────────────────────▶│
       │                                                  │
       │                                         8. 更新租约时间
       │                                                  │
       │  9. 返回 200 OK                                 │
       │◀────────────────────────────────────────────────┤
       │                                                  │
```

**详细步骤**：

1. **客户端发起注册请求**
   ```http
   POST /eureka/apps/USER-SERVICE HTTP/1.1
   Content-Type: application/json
   
   {
       "instance": {
           "instanceId": "192.168.1.100:user-service:8001",
           "app": "USER-SERVICE",
           "ipAddr": "192.168.1.100",
           "port": {"$": 8001, "@enabled": true},
           "status": "UP",
           "homePageUrl": "http://192.168.1.100:8001/",
           "healthCheckUrl": "http://192.168.1.100:8001/actuator/health"
       }
   }
   ```

2. **服务端处理注册**
   ```java
   @POST
   @Path("{appName}")
   public Response addInstance(@PathParam("appName") String appName,
                               InstanceInfo info) {
       // 1. 验证实例信息
       validateInstanceInfo(info);
       
       // 2. 注册到注册表
       registry.register(info, leaseDuration, false);
       
       // 3. 返回响应
       return Response.status(204).build();
   }
   ```

3. **更新注册表**
   ```java
   public void register(InstanceInfo info, int leaseDuration, boolean isReplication) {
       // 1. 获取服务的实例Map
       Map<String, Lease<InstanceInfo>> gMap = registry.get(info.getAppName());
       
       // 2. 创建租约
       Lease<InstanceInfo> lease = new Lease<>(info, leaseDuration);
       
       // 3. 添加到注册表
       gMap.put(info.getId(), lease);
       
       // 4. 失效缓存
       invalidateCache(info.getAppName());
       
       // 5. 同步到其他节点
       if (!isReplication) {
           replicateToPeers(Action.Register, info.getAppName(), info.getId(), info);
       }
   }
   ```

---

### 3.4 服务发现流程

```
┌─────────────┐                                    ┌─────────────┐
│   Client    │                                    │   Server    │
│  (消费者)    │                                    │ (注册中心)   │
└──────┬──────┘                                    └──────┬──────┘
       │                                                  │
       │  1. GET /eureka/apps                            │
       │     (首次全量获取)                               │
       ├────────────────────────────────────────────────▶│
       │                                                  │
       │                                         2. 从缓存读取
       │                                         3. 构造响应
       │                                                  │
       │  4. 返回所有服务列表                             │
       │◀────────────────────────────────────────────────┤
       │                                                  │
       │  5. 缓存到本地                                   │
       │                                                  │
       │  6. 定期增量获取（默认30秒）                     │
       │     GET /eureka/apps/delta                      │
       ├────────────────────────────────────────────────▶│
       │                                                  │
       │                                         7. 返回增量信息
       │                                                  │
       │  8. 返回增量数据                                 │
       │◀────────────────────────────────────────────────┤
       │                                                  │
       │  9. 合并到本地缓存                               │
       │                                                  │
```

**全量获取响应示例**：
```json
{
    "applications": {
        "application": [
            {
                "name": "USER-SERVICE",
                "instance": [
                    {
                        "instanceId": "192.168.1.100:user-service:8001",
                        "app": "USER-SERVICE",
                        "ipAddr": "192.168.1.100",
                        "port": {"$": 8001, "@enabled": true},
                        "status": "UP",
                        "homePageUrl": "http://192.168.1.100:8001/"
                    },
                    {
                        "instanceId": "192.168.1.101:user-service:8002",
                        "app": "USER-SERVICE",
                        "ipAddr": "192.168.1.101",
                        "port": {"$": 8002, "@enabled": true},
                        "status": "UP",
                        "homePageUrl": "http://192.168.1.101:8002/"
                    }
                ]
            },
            {
                "name": "ORDER-SERVICE",
                "instance": [
                    {
                        "instanceId": "192.168.1.102:order-service:9001",
                        "app": "ORDER-SERVICE",
                        "ipAddr": "192.168.1.102",
                        "port": {"$": 9001, "@enabled": true},
                        "status": "UP",
                        "homePageUrl": "http://192.168.1.102:9001/"
                    }
                ]
            }
        ]
    }
}
```

---

## 四、Eureka工作原理

### 4.1 服务注册与续约

```java
// Eureka Client启动流程
public class DiscoveryClient {
    
    // 1. 初始化
    DiscoveryClient(ApplicationInfoManager applicationInfoManager, 
                    EurekaClientConfig config) {
        // 1.1 初始化定时任务
        initScheduledTasks();
    }
    
    private void initScheduledTasks() {
        // 1.2 注册定时任务：拉取服务列表（默认30秒）
        scheduler.schedule(
            new CacheRefreshThread(),
            registryFetchIntervalSeconds,
            TimeUnit.SECONDS
        );
        
        // 1.3 注册定时任务：发送心跳（默认30秒）
        scheduler.schedule(
            new HeartbeatThread(),
            renewalIntervalInSecs,
            TimeUnit.SECONDS
        );
        
        // 1.4 注册实例
        if (clientConfig.shouldRegisterWithEureka()) {
            instanceInfoReplicator.start(initialDelayMs);
        }
    }
    
    // 2. 注册实例
    boolean register() {
        EurekaHttpResponse<Void> httpResponse = eurekaTransport.registrationClient
            .register(instanceInfo);
        return httpResponse.getStatusCode() == 204;
    }
    
    // 3. 发送心跳
    boolean renew() {
        EurekaHttpResponse<InstanceInfo> httpResponse = eurekaTransport.registrationClient
            .sendHeartBeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo);
        return httpResponse.getStatusCode() == 200;
    }
}
```

### 4.2 服务剔除机制

```java
// Eureka Server剔除流程
public class AbstractInstanceRegistry implements InstanceRegistry {
    
    // 剔除任务（默认60秒执行一次）
    class EvictionTask extends TimerTask {
        @Override
        public void run() {
            try {
                // 1. 计算需要剔除的数量
                long compensationTimeMs = getCompensationTimeMs();
                
                // 2. 收集所有过期的租约
                List<Lease<InstanceInfo>> expiredLeases = new ArrayList<>();
                for (Entry<String, Map<String, Lease<InstanceInfo>>> groupEntry : registry.entrySet()) {
                    for (Entry<String, Lease<InstanceInfo>> leaseEntry : groupEntry.getValue().entrySet()) {
                        Lease<InstanceInfo> lease = leaseEntry.getValue();
                        if (lease.isExpired(compensationTimeMs) && lease.getHolder() != null) {
                            expiredLeases.add(lease);
                        }
                    }
                }
                
                // 3. 计算剔除数量上限（考虑自我保护）
                int registrySize = (int) getLocalRegistrySize();
                int registrySizeThreshold = (int) (registrySize * serverConfig.getRenewalPercentThreshold());
                int evictionLimit = registrySize - registrySizeThreshold;
                
                int toEvict = Math.min(expiredLeases.size(), evictionLimit);
                
                // 4. 随机剔除
                if (toEvict > 0) {
                    Random random = new Random(System.currentTimeMillis());
                    for (int i = 0; i < toEvict; i++) {
                        int next = i + random.nextInt(expiredLeases.size() - i);
                        Collections.swap(expiredLeases, i, next);
                        Lease<InstanceInfo> lease = expiredLeases.get(i);
                        
                        String appName = lease.getHolder().getAppName();
                        String id = lease.getHolder().getId();
                        
                        // 剔除实例
                        internalCancel(appName, id, false);
                    }
                }
            } catch (Throwable e) {
                logger.error("Could not run the evict task", e);
            }
        }
    }
    
    // 判断租约是否过期
    public boolean isExpired(long additionalLeaseMs) {
        return (evictionTimestamp > 0 || 
                System.currentTimeMillis() > (lastUpdateTimestamp + duration + additionalLeaseMs));
    }
}
```

### 4.3 自我保护机制

```java
public class PeerAwareInstanceRegistryImpl extends AbstractInstanceRegistry {
    
    // 判断是否进入自我保护模式
    public boolean isLeaseExpirationEnabled() {
        if (!isSelfPreservationModeEnabled()) {
            // 未启用自我保护，直接返回true
            return true;
        }
        
        // 计算每分钟应该收到的心跳数
        int expectedNumberOfRenewsPerMin = getNumOfRenewsInLastMin();
        int numberOfRenewsPerMinThreshold = (int) (expectedNumberOfRenewsPerMin * 
            serverConfig.getRenewalPercentThreshold());
        
        // 实际收到的心跳数
        int actualNumberOfRenewsPerMin = this.getNumOfRenewsInLastMin();
        
        // 如果实际心跳数低于阈值，进入自我保护
        if (actualNumberOfRenewsPerMin < numberOfRenewsPerMinThreshold) {
            logger.warn("进入自我保护模式，不再剔除服务实例");
            return false;
        }
        
        return true;
    }
}
```

---

## 五、Eureka与其他注册中心对比

### 5.1 Eureka vs Zookeeper

| 对比项 | Eureka | Zookeeper |
|-------|--------|-----------|
| **CAP理论** | AP（可用性+分区容错性） | CP（一致性+分区容错性） |
| **一致性** | 最终一致性 | 强一致性 |
| **健康检查** | 客户端心跳 | 临时节点+会话 |
| **自我保护** | 有 | 无 |
| **负载均衡** | Ribbon | 需要自己实现 |
| **适用场景** | 微服务架构 | 分布式协调 |

**选择建议**：
- **Eureka**：适合对可用性要求高的场景，允许短暂的数据不一致
- **Zookeeper**：适合对一致性要求高的场景，如分布式锁、配置中心

### 5.2 Eureka vs Consul

| 对比项 | Eureka | Consul |
|-------|--------|--------|
| **语言** | Java | Go |
| **健康检查** | 客户端心跳 | 服务端主动检查 |
| **多数据中心** | 不支持 | 支持 |
| **KV存储** | 不支持 | 支持 |
| **UI界面** | 简单 | 丰富 |
| **社区活跃度** | Netflix停止维护 | 活跃 |

### 5.3 Eureka vs Nacos

| 对比项 | Eureka | Nacos |
|-------|--------|-------|
| **功能** | 服务注册发现 | 服务注册发现+配置中心 |
| **健康检查** | 客户端心跳 | 客户端心跳+服务端检查 |
| **权重** | 不支持 | 支持 |
| **命名空间** | 不支持 | 支持 |
| **国产化** | 否 | 是（阿里） |

---

## 六、总结

### 6.1 核心要点

```
1. Eureka是Netflix开源的服务注册与发现框架
2. 核心角色：Eureka Server（注册中心）、Eureka Client（服务实例）
3. 核心功能：服务注册、服务发现、健康检查、自我保护
4. 架构特点：AP模型、最终一致性、去中心化
5. 适用场景：微服务架构、对可用性要求高的系统
```

### 6.2 关键配置

```yaml
# Eureka Server配置
eureka:
  server:
    enable-self-preservation: true  # 自我保护模式
    eviction-interval-timer-in-ms: 60000  # 剔除间隔
    renewal-percent-threshold: 0.85  # 续约比例阈值

# Eureka Client配置
eureka:
  instance:
    lease-renewal-interval-in-seconds: 30  # 心跳间隔
    lease-expiration-duration-in-seconds: 90  # 租约过期时间
  client:
    registry-fetch-interval-seconds: 30  # 拉取服务列表间隔
    register-with-eureka: true  # 是否注册
    fetch-registry: true  # 是否拉取服务列表
```

### 6.3 最佳实践

```
1. 生产环境部署多节点集群（至少3个节点）
2. 合理配置心跳间隔和租约过期时间
3. 根据实际情况决定是否启用自我保护
4. 使用健康检查确保服务可用性
5. 配置安全认证保护注册中心
6. 监控Eureka Server的运行状态
7. 定期备份注册信息
```

---

**下一篇**：[02-Eureka安全认证机制.md](./02-Eureka安全认证机制.md) - 详细介绍Eureka的安全认证机制和HTTP Basic认证原理
