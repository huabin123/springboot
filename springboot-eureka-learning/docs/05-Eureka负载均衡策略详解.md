# 05-Eureka负载均衡策略详解

## 一、Ribbon负载均衡架构

### 1.1 Ribbon在微服务架构中的位置

```
┌─────────────────────────────────────────────────────────────┐
│                      微服务调用链路                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Service A (Consumer)                                        │
│  ┌──────────────────────────────────────────────────┐       │
│  │  1. RestTemplate / Feign                         │       │
│  │     ↓                                             │       │
│  │  2. Ribbon LoadBalancer                          │       │
│  │     ├─ IRule (负载均衡策略)                       │       │
│  │     ├─ IPing (健康检查)                           │       │
│  │     ├─ ServerList (服务列表)                      │       │
│  │     └─ ServerListFilter (服务过滤)                │       │
│  │     ↓                                             │       │
│  │  3. 选择目标实例                                   │       │
│  │     ↓                                             │       │
│  │  4. HTTP Client (发送请求)                        │       │
│  └──────────────────────────────────────────────────┘       │
│                    ↓                                         │
│  ┌─────────────────────────────────────────────────┐        │
│  │         Eureka Server (注册中心)                 │        │
│  │  - 服务注册表                                     │        │
│  │  - 服务发现                                       │        │
│  └─────────────────────────────────────────────────┘        │
│                    ↓                                         │
│  Service B (Provider) - 多个实例                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ Instance1│  │ Instance2│  │ Instance3│                  │
│  │ :8081    │  │ :8082    │  │ :8083    │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Ribbon核心组件

**核心组件及其默认实现**：

| 组件 | 接口 | 作用 | 默认实现 | 说明 |
|------|------|------|---------|------|
| 负载均衡器 | ILoadBalancer | 管理服务列表，选择服务实例 | ZoneAwareLoadBalancer | Zone感知的负载均衡器 |
| 负载均衡规则 | IRule | 定义选择实例的策略 | **ZoneAvoidanceRule** | ⭐ 默认策略，Zone避让+轮询 |
| 服务列表 | ServerList | 获取可用服务列表 | DiscoveryEnabledNIWSServerList | 从Eureka获取服务列表 |
| 服务过滤器 | ServerListFilter | 过滤服务列表 | ZonePreferenceServerListFilter | 优先选择同Zone实例 |
| 健康检查 | IPing | 检查服务实例是否存活 | **DummyPing** | ⚠️ 默认不做健康检查 |
| 服务列表更新器 | ServerListUpdater | 更新服务列表 | PollingServerListUpdater | 默认30秒轮询更新 |

#### 1.2.1 ZoneAwareLoadBalancer详解

**ZoneAwareLoadBalancer** 是Ribbon的默认负载均衡器实现，它是一个**Zone感知的负载均衡器**。

**核心特点**：

```java
// ZoneAwareLoadBalancer继承关系
ILoadBalancer (接口)
    ↓
BaseLoadBalancer (基础实现)
    ↓
DynamicServerListLoadBalancer (动态服务列表)
    ↓
ZoneAwareLoadBalancer (Zone感知) ← Ribbon默认使用
```

**工作原理**：

```
ZoneAwareLoadBalancer的选择逻辑：

1. Zone级别选择
   ├─ 获取所有可用Zone
   ├─ 过滤掉故障Zone（可用实例比例过低）
   ├─ 过滤掉高负载Zone（负载超过平均值）
   └─ 优先选择与调用方同Zone的实例

2. 实例级别选择
   ├─ 在选定的Zone内获取实例列表
   ├─ 应用ServerListFilter过滤
   └─ 使用IRule策略选择具体实例（默认ZoneAvoidanceRule）

关键点：
- ZoneAwareLoadBalancer负责Zone级别的选择
- IRule（如ZoneAvoidanceRule）负责Zone内实例的选择
- 两者配合完成完整的负载均衡
```

**实际应用场景**：

```yaml
# 场景1：单Zone环境（大多数情况）
当前服务: zone1
可用实例: 
  - zone1: [实例A, 实例B, 实例C]

行为：
- ZoneAwareLoadBalancer检测到只有一个Zone
- 退化为普通负载均衡器
- 直接使用IRule策略（默认ZoneAvoidanceRule）在实例A、B、C中轮询选择
- 选择顺序：A → B → C → A → B → C...

# 场景2：多Zone环境
当前服务: zone1
可用实例:
  - zone1: [实例A, 实例B]  ← 优先选择
  - zone2: [实例C, 实例D]  ← 备选

行为：
- ZoneAwareLoadBalancer优先选择zone1
- 在zone1内使用IRule策略轮询
- 正常情况：A → B → A → B...
- zone1故障时：C → D → C → D...

# 场景3：跨机房部署
北京机房（zone-beijing）:
  - 服务A实例: [A1, A2, A3]
  - 服务B实例: [B1, B2]

上海机房（zone-shanghai）:
  - 服务A实例: [A4, A5]
  - 服务B实例: [B3, B4]

效果：
- 北京的服务B调用服务A时，优先选择A1、A2、A3
- 上海的服务B调用服务A时，优先选择A4、A5
- 降低跨机房延迟和带宽成本
```

**配置Zone**：

```yaml
# 方式1：使用metadata-map配置Zone
eureka:
  instance:
    metadata-map:
      zone: zone1  # 标识实例所属Zone

# 方式2：使用Eureka的zone配置
eureka:
  client:
    availability-zones:
      region1: zone1,zone2
    service-url:
      zone1: http://eureka1:8761/eureka/
      zone2: http://eureka2:8761/eureka/
  instance:
    metadata-map:
      zone: zone1
```

#### 1.2.2 同Zone内的实例选择策略

**关键问题：同一Zone下有多个实例时，如何选择？**

**答案：由IRule策略决定，默认使用ZoneAvoidanceRule，在同Zone内退化为轮询（RoundRobin）**

```java
// ZoneAwareLoadBalancer + ZoneAvoidanceRule的完整流程
public Server chooseServer(Object key) {
    // 第1步：ZoneAwareLoadBalancer选择Zone
    String targetZone = selectZone();  // 选择目标Zone（优先同Zone）
    
    // 第2步：获取目标Zone的实例列表
    List<Server> serversInZone = getServersInZone(targetZone);
    // 假设：serversInZone = [实例A, 实例B, 实例C]
    
    // 第3步：ZoneAvoidanceRule在Zone内选择实例
    // 在单Zone环境下，ZoneAvoidanceRule退化为RoundRobinRule
    Server chosen = rule.choose(serversInZone, key);
    
    return chosen;
}

// ZoneAvoidanceRule在同Zone内的行为
public class ZoneAvoidanceRule extends PredicateBasedRule {
    
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        
        // 1. 获取可用实例列表（已经是同Zone的实例）
        List<Server> servers = lb.getReachableServers();
        
        // 2. 应用可用性过滤（过滤熔断、高并发实例）
        List<Server> filtered = filterByAvailability(servers);
        
        // 3. 在过滤后的实例中轮询选择
        // 使用RoundRobin算法
        int index = incrementAndGetModulo(filtered.size());
        return filtered.get(index);
    }
}
```

**详细示例**：

```yaml
# 示例：同Zone内有3个实例
当前Zone: zone1
实例列表:
  - 192.168.1.100:8080 (实例A)
  - 192.168.1.101:8080 (实例B)
  - 192.168.1.102:8080 (实例C)

默认选择策略（ZoneAvoidanceRule）:
┌─────────────────────────────────────────┐
│ 请求1 → 实例A (index=0)                  │
│ 请求2 → 实例B (index=1)                  │
│ 请求3 → 实例C (index=2)                  │
│ 请求4 → 实例A (index=0, 循环)            │
│ 请求5 → 实例B (index=1)                  │
│ 请求6 → 实例C (index=2)                  │
│ ...                                     │
└─────────────────────────────────────────┘

特殊情况：实例B熔断或高并发
┌─────────────────────────────────────────┐
│ 可用实例: [A, C]  (B被过滤)              │
│ 请求1 → 实例A                            │
│ 请求2 → 实例C                            │
│ 请求3 → 实例A                            │
│ 请求4 → 实例C                            │
│ ...                                     │
└─────────────────────────────────────────┘
```

**不同策略下的行为对比**：

```yaml
# 策略1：ZoneAvoidanceRule（默认）
# 同Zone内：轮询
# 行为：A → B → C → A → B → C...
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule

# 策略2：RoundRobinRule（纯轮询）
# 行为：A → B → C → A → B → C...
# 与ZoneAvoidanceRule在单Zone下行为相同
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RoundRobinRule

# 策略3：RandomRule（随机）
# 行为：随机选择，如 B → A → C → B → A...
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RandomRule

# 策略4：WeightedResponseTimeRule（响应时间加权）
# 行为：响应快的实例被选中概率更高
# 假设A=100ms, B=200ms, C=300ms
# 选择概率：A(66.7%) > B(33.3%) > C(0%)
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.WeightedResponseTimeRule

# 策略5：BestAvailableRule（最低并发）
# 行为：选择当前并发数最少的实例
# 假设A=5并发, B=2并发, C=8并发
# 选择：B（并发最少）
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.BestAvailableRule
```

**总结**：

```
1. ZoneAwareLoadBalancer的职责
   - 负责Zone级别的选择和过滤
   - 优先选择同Zone的实例
   - 避让故障Zone

2. IRule的职责
   - 负责Zone内具体实例的选择
   - 默认ZoneAvoidanceRule在同Zone内使用轮询
   - 可以配置其他策略改变选择行为

3. 默认行为（ZoneAwareLoadBalancer + ZoneAvoidanceRule）
   - 单Zone环境：直接轮询所有实例
   - 多Zone环境：优先同Zone内轮询，故障时切换Zone
   - 这是最常见和推荐的配置
```

#### 1.2.3 负载均衡器与重试机制的关系

**❓ 常见误解：使用默认负载均衡器是否会失败重试？**

**✅ 答案：会重试！负载均衡器（ILoadBalancer）和重试机制是独立的两个层面**

```
重要澄清：
┌─────────────────────────────────────────────────────────────┐
│ 负载均衡器（ILoadBalancer）                                   │
│ - 职责：选择哪个实例                                          │
│ - 默认：ZoneAwareLoadBalancer                                │
│ - 不负责重试逻辑                                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 负载均衡规则（IRule）                                         │
│ - 职责：如何选择实例（轮询、随机、权重等）                      │
│ - 默认：ZoneAvoidanceRule                                    │
│ - 不负责重试逻辑                                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 重试机制（Ribbon Retry）                                      │
│ - 职责：请求失败时是否重试、重试几次                            │
│ - 配置：MaxAutoRetries、MaxAutoRetriesNextServer             │
│ - 默认：会重试（MaxAutoRetriesNextServer=1）                  │
│ - 与负载均衡器无关                                            │
└─────────────────────────────────────────────────────────────┘
```

**默认配置下的重试行为**：

```yaml
# 即使使用默认的ZoneAwareLoadBalancer，也会有重试机制
ribbon:
  # 负载均衡器配置（默认）
  NFLoadBalancerClassName: com.netflix.loadbalancer.ZoneAwareLoadBalancer
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
  
  # 重试配置（默认值）
  MaxAutoRetries: 0  # 同一实例不重试
  MaxAutoRetriesNextServer: 1  # 切换1次实例（会重试）
  OkToRetryOnAllOperations: false  # 只重试GET请求
  
# 结论：默认会切换实例重试1次
```

**完整的请求流程（默认配置）**：

```
场景：有3个实例[A, B, C]，实例A故障

请求流程：
┌─────────────────────────────────────────────────────────────┐
│ 1. 客户端发起GET请求                                          │
│    └─ 调用RestTemplate或Feign                               │
│                                                              │
│ 2. ZoneAwareLoadBalancer选择实例                             │
│    └─ 使用ZoneAvoidanceRule轮询                              │
│    └─ 选中实例A                                               │
│                                                              │
│ 3. 发送请求到实例A                                            │
│    └─ 连接超时（ConnectTimeout=1000ms）                      │
│    └─ 请求失败 ❌                                             │
│                                                              │
│ 4. Ribbon重试机制触发                                         │
│    ├─ 检查：MaxAutoRetries=0，不在实例A上重试                 │
│    ├─ 检查：MaxAutoRetriesNextServer=1，可以切换实例          │
│    └─ 决定：切换到下一个实例                                   │
│                                                              │
│ 5. ZoneAwareLoadBalancer再次选择实例                          │
│    └─ 使用ZoneAvoidanceRule轮询                              │
│    └─ 选中实例B                                               │
│                                                              │
│ 6. 发送请求到实例B                                            │
│    └─ 请求成功 ✅                                             │
│                                                              │
│ 总耗时：约2秒（1秒超时 + 1秒成功）                             │
└─────────────────────────────────────────────────────────────┘

关键点：
- ZoneAwareLoadBalancer负责选择实例（第2步和第5步）
- Ribbon重试机制负责决定是否重试（第4步）
- 两者配合完成故障转移
```

**不同负载均衡器下的重试行为对比**：

```yaml
# 配置1：默认负载均衡器 + 默认重试
ribbon:
  NFLoadBalancerClassName: com.netflix.loadbalancer.ZoneAwareLoadBalancer
  MaxAutoRetriesNextServer: 1
# 行为：实例A失败 → 切换到实例B → 重试成功 ✅

# 配置2：RoundRobinRule + 默认重试
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RoundRobinRule
  MaxAutoRetriesNextServer: 1
# 行为：实例A失败 → 切换到实例B → 重试成功 ✅
# 结论：与配置1行为相同

# 配置3：默认负载均衡器 + 禁用重试
ribbon:
  NFLoadBalancerClassName: com.netflix.loadbalancer.ZoneAwareLoadBalancer
  MaxAutoRetriesNextServer: 0  # 禁用切换实例重试
# 行为：实例A失败 → 直接返回失败 ❌
# 结论：这种配置才不会重试

# 配置4：RetryRule（特殊的重试策略）
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RetryRule
  MaxAutoRetriesNextServer: 1
# 行为：实例A失败 → 在500ms内不断重试选择实例 → 切换到实例B
# 结论：RetryRule是在IRule层面增加重试逻辑，与Ribbon重试机制叠加
```

**POST请求的特殊情况**：

```yaml
# 默认配置下，POST请求不会重试
ribbon:
  OkToRetryOnAllOperations: false  # 默认只重试GET

场景：POST请求失败
┌─────────────────────────────────────────────────────────────┐
│ 1. 客户端发起POST请求                                         │
│ 2. ZoneAwareLoadBalancer选择实例A                            │
│ 3. 请求失败 ❌                                                │
│ 4. Ribbon检查重试条件                                         │
│    ├─ 请求方法：POST                                          │
│    ├─ OkToRetryOnAllOperations：false                       │
│    └─ 决定：不重试，直接返回失败 ❌                            │
└─────────────────────────────────────────────────────────────┘

解决方案：
ribbon:
  OkToRetryOnAllOperations: true  # 允许所有操作重试
  # ⚠️ 注意：必须确保POST接口是幂等的
```

**总结**：

```
1. 负载均衡器不影响重试
   - ZoneAwareLoadBalancer（默认）：会重试 ✅
   - BaseLoadBalancer：会重试 ✅
   - 任何ILoadBalancer实现：都会重试 ✅
   
2. 重试机制由Ribbon配置决定
   - MaxAutoRetriesNextServer > 0：会切换实例重试
   - MaxAutoRetriesNextServer = 0：不会切换实例重试
   - 默认值 = 1：会重试1次
   
3. 特殊情况
   - POST/PUT/DELETE默认不重试（安全考虑）
   - 需要配置OkToRetryOnAllOperations=true
   - 必须确保接口幂等性
   
4. RetryRule的特殊性
   - RetryRule是IRule的一种实现
   - 在选择实例层面增加重试逻辑
   - 与Ribbon的重试机制是两个不同层面
   - 可以叠加使用
```

**⚠️ 重要提示：默认配置的局限性**

```yaml
# 默认的IPing实现是DummyPing，不做任何健康检查
# 这意味着：
# 1. Ribbon不会主动探测实例是否存活
# 2. 依赖Eureka的心跳机制来判断实例状态
# 3. 可能将请求路由到已宕机但未被Eureka剔除的实例

# 生产环境建议配置：
ribbon:
  NFLoadBalancerPingClassName: com.netflix.loadbalancer.PingUrl  # 使用HTTP Ping
  NFLoadBalancerPingInterval: 5  # 每5秒Ping一次
  PingUrl: /actuator/health  # Ping的健康检查URL
```

### 1.3 负载均衡流程

```java
// 伪代码展示Ribbon的工作流程
public Server chooseServer(Object key) {
    // 1. 获取所有服务实例
    List<Server> allServers = serverList.getUpdatedListOfServers();
    
    // 2. 过滤不可用的实例
    List<Server> reachableServers = serverListFilter.getFilteredListOfServers(allServers);
    
    // 3. Ping检查（可选）
    List<Server> upServers = new ArrayList<>();
    for (Server server : reachableServers) {
        if (ping.isAlive(server)) {
            upServers.add(server);
        }
    }
    
    // 4. 应用负载均衡策略选择实例
    Server chosenServer = rule.choose(key);
    
    return chosenServer;
}
```

## 二、Ribbon内置负载均衡策略

### 2.1 RoundRobinRule（轮询策略）

#### 2.1.1 算法原理

```
轮询算法：按顺序依次选择服务实例

实例列表：[A, B, C]
请求序列：
Request 1 → A
Request 2 → B
Request 3 → C
Request 4 → A
Request 5 → B
...
```

#### 2.1.2 源码实现

```java
public class RoundRobinRule extends AbstractLoadBalancerRule {
    
    private AtomicInteger nextServerCyclicCounter;
    
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        }
        
        Server server = null;
        int count = 0;
        while (server == null && count++ < 10) {
            List<Server> reachableServers = lb.getReachableServers();
            List<Server> allServers = lb.getAllServers();
            int upCount = reachableServers.size();
            int serverCount = allServers.size();
            
            if ((upCount == 0) || (serverCount == 0)) {
                return null;
            }
            
            // 原子递增，取模获取索引
            int nextServerIndex = incrementAndGetModulo(serverCount);
            server = allServers.get(nextServerIndex);
            
            if (server == null) {
                Thread.yield();
                continue;
            }
            
            if (server.isAlive() && (server.isReadyToServe())) {
                return server;
            }
            
            server = null;
        }
        
        return server;
    }
    
    private int incrementAndGetModulo(int modulo) {
        for (;;) {
            int current = nextServerCyclicCounter.get();
            int next = (current + 1) % modulo;
            if (nextServerCyclicCounter.compareAndSet(current, next)) {
                return next;
            }
        }
    }
}
```

#### 2.1.3 配置方式

```yaml
# 全局配置
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RoundRobinRule

# 针对特定服务
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RoundRobinRule
```

```java
// Java配置方式
@Configuration
public class RibbonConfig {
    @Bean
    public IRule ribbonRule() {
        return new RoundRobinRule();
    }
}
```

#### 2.1.4 适用场景

- ✅ 各实例性能相近
- ✅ 请求处理时间相近
- ✅ 简单场景，无特殊需求
- ❌ 实例性能差异大
- ❌ 需要考虑区域亲和性

### 2.2 RandomRule（随机策略）

#### 2.2.1 算法原理

```
随机算法：随机选择一个服务实例

实例列表：[A, B, C]
请求序列（示例）：
Request 1 → B
Request 2 → A
Request 3 → B
Request 4 → C
Request 5 → A
...
```

#### 2.2.2 源码实现

```java
public class RandomRule extends AbstractLoadBalancerRule {
    
    Random rand;
    
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        }
        Server server = null;
        
        while (server == null) {
            if (Thread.interrupted()) {
                return null;
            }
            List<Server> upList = lb.getReachableServers();
            List<Server> allList = lb.getAllServers();
            
            int serverCount = allList.size();
            if (serverCount == 0) {
                return null;
            }
            
            // 随机选择索引
            int index = rand.nextInt(serverCount);
            server = upList.get(index);
            
            if (server == null) {
                Thread.yield();
                continue;
            }
            
            if (server.isAlive()) {
                return server;
            }
            
            server = null;
        }
        
        return server;
    }
}
```

#### 2.2.3 适用场景

- ✅ 简单的负载分散
- ✅ 无状态服务
- ❌ 需要会话保持
- ❌ 需要精确的流量控制

### 2.3 WeightedResponseTimeRule（响应时间加权策略）

#### 2.3.1 算法原理

```
加权算法：根据实例的平均响应时间计算权重，响应时间越短权重越高

权重计算：
totalWeight = sum(maxResponseTime - instanceResponseTime)
instanceWeight = (maxResponseTime - instanceResponseTime) / totalWeight

示例：
实例A：平均响应时间 100ms
实例B：平均响应时间 200ms
实例C：平均响应时间 300ms

maxResponseTime = 300ms
totalWeight = (300-100) + (300-200) + (300-300) = 200 + 100 + 0 = 300

权重分布：
A: 200/300 = 66.7%
B: 100/300 = 33.3%
C: 0/300 = 0%
```

#### 2.3.2 源码分析

```java
public class WeightedResponseTimeRule extends RoundRobinRule {
    
    // 每个实例的累积权重
    private volatile List<Double> accumulatedWeights = new ArrayList<>();
    
    // 定时任务，每30秒更新一次权重
    class ServerWeightTask extends TimerTask {
        public void run() {
            ServerWeight serverWeight = new ServerWeight();
            try {
                serverWeight.maintainWeights();
            } catch (Exception e) {
                logger.error("Error maintaining weights", e);
            }
        }
    }
    
    class ServerWeight {
        public void maintainWeights() {
            ILoadBalancer lb = getLoadBalancer();
            List<Server> allServers = lb.getAllServers();
            
            // 计算总响应时间
            double totalResponseTime = 0;
            for (Server server : allServers) {
                ServerStats stats = getServerStats(server);
                totalResponseTime += stats.getResponseTimeAvg();
            }
            
            // 计算权重
            Double weightSoFar = 0.0;
            List<Double> weights = new ArrayList<>();
            for (Server server : allServers) {
                ServerStats stats = getServerStats(server);
                double weight = totalResponseTime - stats.getResponseTimeAvg();
                weightSoFar += weight;
                weights.add(weightSoFar);
            }
            
            accumulatedWeights = weights;
        }
    }
    
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        }
        
        List<Server> allServers = lb.getAllServers();
        List<Double> weights = accumulatedWeights;
        
        if (weights.isEmpty()) {
            // 权重未初始化，使用轮询
            return super.choose(lb, key);
        }
        
        // 生成随机数
        double randomWeight = random.nextDouble() * weights.get(weights.size() - 1);
        
        // 二分查找选择实例
        int serverIndex = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (weights.get(i) >= randomWeight) {
                serverIndex = i;
                break;
            }
        }
        
        return allServers.get(serverIndex);
    }
}
```

#### 2.3.3 配置方式

```yaml
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.WeightedResponseTimeRule
```

#### 2.3.4 适用场景

- ✅ 实例性能差异大
- ✅ 需要自动适应性能变化
- ✅ 长期运行的服务
- ❌ 短时间运行（权重未收敛）
- ❌ 响应时间波动大

### 2.4 RetryRule（重试策略）

#### 2.4.1 算法原理

```
重试算法：在指定时间内，使用子规则选择实例，如果失败则重试

配置：
- 子规则：默认RoundRobinRule
- 重试时间：默认500ms
```

#### 2.4.2 源码实现

```java
public class RetryRule extends AbstractLoadBalancerRule {
    
    IRule subRule = new RoundRobinRule();
    long maxRetryMillis = 500;  // 最大重试时间
    
    public Server choose(ILoadBalancer lb, Object key) {
        long requestTime = System.currentTimeMillis();
        long deadline = requestTime + maxRetryMillis;
        
        Server answer = null;
        answer = subRule.choose(key);
        
        if (((answer == null) || (!answer.isAlive()))
                && (System.currentTimeMillis() < deadline)) {
            
            InterruptTask task = new InterruptTask(deadline - System.currentTimeMillis());
            
            while (!Thread.interrupted()) {
                answer = subRule.choose(key);
                
                if (((answer == null) || (!answer.isAlive()))
                        && (System.currentTimeMillis() < deadline)) {
                    Thread.yield();
                } else {
                    break;
                }
            }
            
            task.cancel();
        }
        
        return answer;
    }
}
```

#### 2.4.3 配置方式

```yaml
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.RetryRule
    MaxAutoRetries: 1
    MaxAutoRetriesNextServer: 2
    ConnectTimeout: 1000
    ReadTimeout: 3000
```

```java
@Configuration
public class RibbonConfig {
    @Bean
    public IRule ribbonRule() {
        RetryRule retryRule = new RetryRule();
        retryRule.setSubRule(new RoundRobinRule());
        retryRule.setMaxRetryMillis(500);
        return retryRule;
    }
}
```

### 2.5 BestAvailableRule（最低并发策略）

#### 2.5.1 算法原理

```
最低并发算法：选择当前并发请求数最少的实例

实例状态：
实例A：当前并发 5
实例B：当前并发 2  ← 选择
实例C：当前并发 8
```

#### 2.5.2 源码实现

```java
public class BestAvailableRule extends ClientConfigEnabledRoundRobinRule {
    
    public Server choose(Object key) {
        if (loadBalancerStats == null) {
            return super.choose(key);
        }
        
        List<Server> serverList = getLoadBalancer().getAllServers();
        int minimalConcurrentConnections = Integer.MAX_VALUE;
        long currentTime = System.currentTimeMillis();
        Server chosen = null;
        
        for (Server server : serverList) {
            ServerStats serverStats = loadBalancerStats.getSingleServerStat(server);
            
            if (!serverStats.isCircuitBreakerTripped(currentTime)) {
                int concurrentConnections = serverStats.getActiveRequestsCount(currentTime);
                
                if (concurrentConnections < minimalConcurrentConnections) {
                    minimalConcurrentConnections = concurrentConnections;
                    chosen = server;
                }
            }
        }
        
        if (chosen == null) {
            return super.choose(key);
        } else {
            return chosen;
        }
    }
}
```

#### 2.5.3 适用场景

- ✅ 请求处理时间差异大
- ✅ 需要避免实例过载
- ✅ 有状态服务
- ❌ 无法获取并发数统计

### 2.6 AvailabilityFilteringRule（可用性过滤策略）

#### 2.6.1 算法原理

```
可用性过滤：过滤掉以下实例，然后使用轮询
1. 连接失败次数超过阈值的实例
2. 并发连接数超过阈值的实例
3. 熔断器打开的实例
```

#### 2.6.2 源码实现

```java
public class AvailabilityFilteringRule extends PredicateBasedRule {
    
    private AbstractServerPredicate predicate;
    
    public AvailabilityFilteringRule() {
        super();
        predicate = CompositePredicate.withPredicate(
            new AvailabilityPredicate(this, null))
            .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
            .build();
    }
    
    @Override
    public AbstractServerPredicate getPredicate() {
        return predicate;
    }
}

// 可用性判断
class AvailabilityPredicate extends AbstractServerPredicate {
    
    public boolean apply(PredicateKey input) {
        LoadBalancerStats stats = getLBStats();
        if (stats == null) {
            return true;
        }
        
        // 检查熔断器状态
        if (stats.getSingleServerStat(input.getServer())
                .isCircuitBreakerTripped()) {
            return false;
        }
        
        // 检查并发连接数
        int activeConnections = stats.getSingleServerStat(input.getServer())
                .getActiveRequestsCount();
        if (activeConnections >= activeConnectionsLimit) {
            return false;
        }
        
        return true;
    }
}
```

#### 2.6.3 配置方式

```yaml
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.AvailabilityFilteringRule
    # 并发连接数阈值
    ActiveConnectionsLimit: 100
    # 熔断器触发阈值
    CircuitTripMaxTimeoutCount: 3
```

### 2.7 ZoneAvoidanceRule（区域亲和策略）⭐ **Ribbon默认策略**

#### 2.7.1 为什么是默认策略

**Ribbon默认使用ZoneAvoidanceRule的原因**：

```java
// RibbonClientConfiguration.java - Spring Cloud Netflix默认配置
@Bean
@ConditionalOnMissingBean
public IRule ribbonRule(IClientConfig config) {
    if (this.propertiesFactory.isSet(IRule.class, name)) {
        return this.propertiesFactory.get(IRule.class, config, name);
    }
    // 默认使用ZoneAvoidanceRule
    ZoneAvoidanceRule rule = new ZoneAvoidanceRule();
    rule.initWithNiwsConfig(config);
    return rule;
}
```

**选择ZoneAvoidanceRule作为默认策略的理由**：

```
1. 兼容性好
   - 单机房部署时，退化为轮询策略
   - 多机房部署时，自动启用Zone感知
   - 无需修改配置即可适应不同环境

2. 性能优化
   - 优先选择同Zone的实例，减少跨Zone延迟
   - 自动避让故障Zone，提高可用性
   - 结合了可用性过滤和Zone感知的优点

3. 生产级特性
   - 支持熔断器集成
   - 自动过滤不可用实例
   - 适合大规模分布式部署

4. 平衡性
   - 在可用性和性能之间取得平衡
   - 既不像RoundRobin那样简单，也不像WeightedResponseTime那样复杂
   - 适合大多数生产场景
```

**不配置时的默认行为**：

```yaml
# 如果不配置NFLoadBalancerRuleClassName，默认行为如下：

# 情况1：单Zone环境（大多数情况）
# - ZoneAvoidanceRule退化为轮询策略
# - 行为类似RoundRobinRule
# - 按顺序选择实例：A → B → C → A → B → C...

# 情况2：多Zone环境
# - 优先选择本Zone的实例
# - 如果本Zone实例不可用，选择其他Zone
# - 自动避让故障Zone

# 示例：
# 当前Zone: zone1
# Zone1实例: [A, B]  ← 优先选择
# Zone2实例: [C, D]  ← 备选
# 
# 正常情况：A → B → A → B...
# Zone1故障：C → D → C → D...
```

#### 2.7.2 算法原理

```
区域避让算法：
1. 计算每个Zone的可用性和性能
2. 过滤掉不可用或性能差的Zone
3. 在剩余Zone中使用轮询选择实例

Zone评估标准：
- 可用实例比例
- 平均响应时间
- 熔断器状态
```

#### 2.7.2 源码实现

```java
public class ZoneAvoidanceRule extends PredicateBasedRule {
    
    private CompositePredicate compositePredicate;
    
    public ZoneAvoidanceRule() {
        super();
        ZoneAvoidancePredicate zonePredicate = new ZoneAvoidancePredicate(this);
        AvailabilityPredicate availabilityPredicate = new AvailabilityPredicate(this);
        
        compositePredicate = createCompositePredicate(zonePredicate, availabilityPredicate);
    }
    
    private CompositePredicate createCompositePredicate(
            ZoneAvoidancePredicate p1, AvailabilityPredicate p2) {
        return CompositePredicate.withPredicate(p1)
                .addFallbackPredicate(p2)
                .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
                .build();
    }
}

// Zone避让判断
class ZoneAvoidancePredicate extends AbstractServerPredicate {
    
    public boolean apply(PredicateKey input) {
        if (!ENABLED.get()) {
            return true;
        }
        
        String serverZone = input.getServer().getZone();
        if (serverZone == null) {
            return true;
        }
        
        LoadBalancerStats lbStats = getLBStats();
        if (lbStats == null) {
            return true;
        }
        
        ZoneSnapshot snapshot = lbStats.getZoneSnapshot(serverZone);
        
        // Zone可用性检查
        if (snapshot.getCircuitTrippedCount() >= snapshot.getInstanceCount() * 0.5) {
            return false;  // 超过50%实例熔断，避让该Zone
        }
        
        // Zone性能检查
        double avgLoadPerServer = snapshot.getLoadPerServer();
        double overallAvgLoad = lbStats.getGlobalLoadPerServer();
        if (avgLoadPerServer >= overallAvgLoad * 1.2) {
            return false;  // 负载超过平均值20%，避让该Zone
        }
        
        return true;
    }
}
```

#### 2.7.3 配置方式

```yaml
# 方式1：显式配置（推荐，便于理解）
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
    # Zone相关配置
    EnableZoneAffinity: true  # 默认true，启用Zone亲和性
    EnableZoneExclusivity: false  # 默认false，不强制Zone独占

# 方式2：不配置（使用默认值）
# 如果不配置NFLoadBalancerRuleClassName，Ribbon会自动使用ZoneAvoidanceRule
user-service:
  ribbon:
    # 其他配置...
    ConnectTimeout: 1000
    ReadTimeout: 3000
    # NFLoadBalancerRuleClassName不配置，默认就是ZoneAvoidanceRule
```

**Zone配置说明**：

```yaml
# Eureka实例配置Zone
eureka:
  instance:
    metadata-map:
      zone: zone1  # 标识实例所属Zone
      
# Ribbon Zone配置
ribbon:
  EnableZoneAffinity: true  # 默认true，是否启用Zone亲和性
  EnableZoneExclusivity: false  # 默认false，是否强制只使用同Zone实例
  
# 配置说明：
# EnableZoneAffinity=true:
#   - 优先选择同Zone实例
#   - 同Zone实例不可用时，可以选择其他Zone
#   
# EnableZoneExclusivity=true:
#   - 强制只使用同Zone实例
#   - 同Zone实例全部不可用时，请求失败
#   - 生产环境不推荐，容错性差
```

#### 2.7.4 适用场景

- ✅ 多机房部署
- ✅ 跨区域服务调用
- ✅ 需要就近访问
- ✅ 生产环境推荐

## 三、自定义负载均衡策略

### 3.1 基于IP Hash的策略

**⚠️ 重要提示：生产环境最佳实践**

```
IP Hash的单点故障问题:
❌ 实例宕机导致部分用户无法访问
❌ 会话数据丢失
❌ 扩缩容时所有用户会话重新分配

解决方案:
✅ 一致性Hash: 减少影响范围，只有1/N用户受影响
✅ 故障转移: 自动切换到可用实例
✅ Redis会话存储: 彻底解决单点问题（推荐）

生产环境最佳实践:
1. 使用Redis等外部存储保存会话数据
2. 使用默认的ZoneAvoidanceRule负载均衡策略
3. 配合健康检查和重试机制
4. 避免依赖实例内存存储关键数据

结论：
- 开发/测试环境可以使用IP Hash快速实现会话保持
- 生产环境强烈推荐使用Redis等外部存储方案
- 如果必须使用IP Hash，应采用一致性Hash算法
```

#### 3.1.1 适用场景：会话保持

**实际业务场景：在线协作文档编辑系统**

```yaml
业务背景：
- 多人在线协作编辑文档（类似Google Docs、腾讯文档）
- 用户的编辑操作需要实时同步给其他协作者
- 每个文档的编辑状态缓存在服务实例的内存中

技术挑战：
┌─────────────────────────────────────────────────────────────┐
│ 问题：使用轮询策略会导致会话丢失                              │
│                                                              │
│ 用户A编辑文档123：                                            │
│ 请求1 → 实例A（创建编辑会话，缓存在内存）                      │
│ 请求2 → 实例B（找不到会话，需要重新创建）❌                    │
│ 请求3 → 实例C（找不到会话，需要重新创建）❌                    │
│ 请求4 → 实例A（找到会话）✅                                   │
│                                                              │
│ 影响：                                                       │
│ - 编辑状态丢失，用户体验差                                    │
│ - 频繁重建会话，性能开销大                                    │
│ - 实时同步失效，协作者看不到最新内容                           │
└─────────────────────────────────────────────────────────────┘

解决方案：使用IP Hash保持会话亲和性
┌─────────────────────────────────────────────────────────────┐
│ 用户A（IP: 192.168.1.100）编辑文档123：                       │
│                                                              │
│ 请求1 → Hash(192.168.1.100) % 3 = 1 → 实例B                 │
│ 请求2 → Hash(192.168.1.100) % 3 = 1 → 实例B ✅               │
│ 请求3 → Hash(192.168.1.100) % 3 = 1 → 实例B ✅               │
│ 请求4 → Hash(192.168.1.100) % 3 = 1 → 实例B ✅               │
│                                                              │
│ 优势：                                                       │
│ - 同一用户的请求始终路由到同一实例                             │
│ - 编辑会话保持在内存中，无需重建                               │
│ - WebSocket连接稳定，实时同步正常                             │
└─────────────────────────────────────────────────────────────┘
```

**其他典型会话保持场景**：

```yaml
场景1：购物车系统
问题：
  - 用户添加商品到购物车（存储在服务实例内存）
  - 轮询策略导致切换实例后购物车为空
解决：
  - IP Hash确保用户请求路由到同一实例
  - 购物车数据保持一致

场景2：在线游戏大厅
问题：
  - 玩家进入游戏房间（房间状态缓存在实例内存）
  - 切换实例导致玩家掉线，需要重新进入房间
解决：
  - IP Hash保持玩家与实例的绑定关系
  - 游戏状态稳定，用户体验好

场景3：视频会议系统
问题：
  - 用户加入会议室（WebSocket连接建立在某个实例）
  - 切换实例导致WebSocket断开，需要重连
解决：
  - IP Hash确保用户的所有请求路由到同一实例
  - WebSocket连接稳定，音视频流不中断

场景4：文件上传（分片上传）
问题：
  - 大文件分片上传，每个分片需要合并
  - 分片上传到不同实例，合并时找不到所有分片
解决：
  - IP Hash确保同一用户的所有分片上传到同一实例
  - 分片合并成功率高

场景5：OAuth2授权流程
问题：
  - 授权码存储在实例内存中
  - 回调请求路由到其他实例，找不到授权码
解决：
  - IP Hash保持授权流程在同一实例完成
  - 授权成功率提高
```

**完整实现示例**：

```java
/**
 * IP Hash策略：根据客户端IP选择固定的服务实例
 * 适用场景：需要会话保持的场景
 * 
 * 实际应用：在线协作文档编辑系统
 * - 用户的编辑会话缓存在服务实例内存中
 * - 需要确保同一用户的请求路由到同一实例
 * - 避免会话丢失和频繁重建
 */
public class IpHashRule extends AbstractLoadBalancerRule {
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // 初始化配置
    }
    
    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        if (lb == null) {
            return null;
        }
        
        List<Server> servers = lb.getReachableServers();
        if (servers.isEmpty()) {
            return null;
        }
        
        // 获取客户端IP
        String clientIp = getClientIp();
        if (clientIp == null) {
            // 无法获取IP，降级为轮询
            return servers.get(0);
        }
        
        // 计算Hash值
        int hash = Math.abs(clientIp.hashCode());
        int index = hash % servers.size();
        
        return servers.get(index);
    }
    
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }
}
```

#### 3.1.2 IP Hash的单点故障问题与解决方案

**❌ 问题：IP Hash导致的单点故障风险**

```yaml
场景：3个实例，使用IP Hash策略
实例列表: [实例A, 实例B, 实例C]

用户分布（基于IP Hash）:
- 用户1（IP: 192.168.1.100） → Hash % 3 = 0 → 实例A
- 用户2（IP: 192.168.1.101） → Hash % 3 = 1 → 实例B
- 用户3（IP: 192.168.1.102） → Hash % 3 = 2 → 实例C
- 用户4（IP: 192.168.1.103） → Hash % 3 = 0 → 实例A
- ...

问题：实例B宕机
┌─────────────────────────────────────────────────────────────┐
│ 实例B宕机后：                                                 │
│ - 用户2的所有请求失败 ❌                                      │
│ - 用户2的编辑会话丢失                                         │
│ - 用户2无法继续工作                                           │
│                                                              │
│ 即使实例A和实例C正常，用户2也无法访问服务                       │
│ 这就是单点故障问题                                            │
└─────────────────────────────────────────────────────────────┘
```

**✅ 解决方案1：一致性Hash算法（推荐）**

**什么是一致性Hash？用生活中的例子来理解**

```
想象一个圆形的钟表盘（0点到12点，首尾相连）：

普通IP Hash（取模）的问题：
┌─────────────────────────────────────────────────────────────┐
│ 3个实例，用户IP % 3 = 0/1/2 来分配                             │
│                                                              │
│ 实例A（0） 实例B（1） 实例C（2）                               │
│    ↓          ↓          ↓                                   │
│  用户1      用户2      用户3                                  │
│  用户4      用户5      用户6                                  │
│                                                              │
│ 问题：实例B宕机后，变成 % 2                                    │
│ 所有用户重新分配！用户1可能从实例A变到实例B                     │
└─────────────────────────────────────────────────────────────┘

一致性Hash（钟表盘）的解决方案：
┌─────────────────────────────────────────────────────────────┐
│ 把实例和用户都放在一个圆形钟表盘上：                            │
│                                                              │
│              12点                                            │
│               │                                              │
│         用户3 │ 实例A                                         │
│              ╱│╲                                             │
│            ╱  │  ╲                                           │
│          ╱    │    ╲                                         │
│  9点 ─ 实例C  │  实例B ─ 3点                                  │
│          ╲    │    ╱                                         │
│            ╲  │  ╱                                           │
│         用户1 │ 用户2                                         │
│               │                                              │
│              6点                                             │
│                                                              │
│ 规则：顺时针找到第一个实例                                     │
│ - 用户1（6点位置）→ 顺时针 → 实例B（3点）                      │
│ - 用户2（4点位置）→ 顺时针 → 实例B（3点）                      │
│ - 用户3（11点位置）→ 顺时针 → 实例A（12点）                    │
│                                                              │
│ 实例B宕机后：                                                 │
│ - 用户1（6点）→ 顺时针 → 实例C（9点）✅ 只有用户1受影响        │
│ - 用户2（4点）→ 顺时针 → 实例C（9点）✅ 只有用户2受影响        │
│ - 用户3（11点）→ 顺时针 → 实例A（12点）✅ 不受影响             │
│                                                              │
│ 优势：只有原本在实例B"管辖范围"的用户受影响！                   │
└─────────────────────────────────────────────────────────────┘
```

**核心原理（简单理解）**：

```
1. Hash环（钟表盘）
   - 把0到2^32-1的数字首尾相连，形成一个圆环
   - 就像一个有40多亿个刻度的超大钟表盘

2. 实例上环
   - 每个实例（A、B、C）计算Hash值，放在环上的某个位置
   - 比如：实例A的Hash值是100，就放在100这个刻度上

3. 用户找实例
   - 用户IP也计算Hash值，找到环上的位置
   - 顺时针找到第一个实例，就是要访问的实例
   - 比如：用户IP的Hash值是50，顺时针找到100（实例A）

4. 虚拟节点（解决负载不均）
   - 如果只有3个实例，可能分布不均匀
   - 给每个实例创建150个"分身"（虚拟节点）
   - 实例A → A#1, A#2, A#3, ..., A#150
   - 这样在环上分布更均匀，负载更平衡

5. 实例宕机的影响
   - 实例B宕机，只影响"B到A之间"的用户
   - 这些用户会自动找到下一个实例（实例C或A）
   - 其他用户完全不受影响
```

**生活类比**：

```
想象你在一个环形商业街买东西：

普通Hash（取模）：
- 街上有3家店（A、B、C）
- 规定：身份证尾号 % 3 = 0去A店，1去B店，2去C店
- B店关门了，规则变成 % 2
- 所有人都要重新分配，原来去A店的可能要去C店

一致性Hash（环形街道）：
- 街上有3家店，均匀分布在环形街道上
- 规定：从你的位置顺时针走，遇到的第一家店就是你的店
- B店关门了，只有原本要去B店的人需要多走几步到C店
- 其他人还是去原来的店，不受影响

虚拟节点：
- 每家店开3个分店（A1、A2、A3），分散在街道各处
- 这样无论你在哪个位置，都能快速找到最近的店
- 负载更均衡，不会出现某家店特别忙的情况
```

**代码实现**：

```java
/**
 * 一致性Hash策略：解决IP Hash的单点故障问题
 * 
 * 核心思想：
 * 1. 使用Hash环，实例宕机时只影响部分用户
 * 2. 引入虚拟节点，提高负载均衡性
 * 3. 实例故障时，请求自动迁移到下一个实例
 */
public class ConsistentHashRule extends AbstractLoadBalancerRule {
    
    private static final int VIRTUAL_NODE_COUNT = 150; // 每个实例的虚拟节点数
    private TreeMap<Long, Server> hashRing = new TreeMap<>();
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // 初始化配置
    }
    
    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        if (lb == null) {
            return null;
        }
        
        List<Server> servers = lb.getReachableServers();
        if (servers.isEmpty()) {
            return null;
        }
        
        // 构建Hash环（实际应该缓存，这里简化）
        buildHashRing(servers);
        
        // 获取客户端IP
        String clientIp = getClientIp();
        if (clientIp == null) {
            return servers.get(0);
        }
        
        // 计算客户端IP的Hash值
        long hash = hash(clientIp);
        
        // 在Hash环上顺时针查找第一个大于等于该Hash值的节点
        Map.Entry<Long, Server> entry = hashRing.ceilingEntry(hash);
        if (entry == null) {
            // 如果没有找到，返回环上的第一个节点
            entry = hashRing.firstEntry();
        }
        
        return entry.getValue();
    }
    
    /**
     * 构建Hash环
     * 为每个实例创建多个虚拟节点，提高负载均衡性
     */
    private void buildHashRing(List<Server> servers) {
        hashRing.clear();
        
        for (Server server : servers) {
            // 为每个实例创建虚拟节点
            for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
                String virtualNodeKey = server.getId() + "#" + i;
                long hash = hash(virtualNodeKey);
                hashRing.put(hash, server);
            }
        }
    }
    
    /**
     * Hash函数（使用MurmurHash或FNV等）
     */
    private long hash(String key) {
        // 简化实现，实际应使用更好的Hash算法
        return Math.abs(key.hashCode());
    }
    
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }
}
```

**一致性Hash的优势**：

```yaml
场景：3个实例，使用一致性Hash
实例列表: [实例A, 实例B, 实例C]

Hash环示意（简化，实际有150个虚拟节点/实例）:
    0 ────────────────────────────────────── 2^32-1
    │                                           │
    A1  A2  B1  C1  A3  B2  C2  B3  C3  A4  ...  │
    └───────────────────────────────────────────┘

用户分布:
- 用户1（IP Hash=100） → 顺时针找到 A1 → 实例A
- 用户2（IP Hash=500） → 顺时针找到 B1 → 实例B
- 用户3（IP Hash=800） → 顺时针找到 C1 → 实例C

实例B宕机后：
┌─────────────────────────────────────────────────────────────┐
│ Hash环变化:                                                   │
│     0 ────────────────────────────────────── 2^32-1          │
│     │                                           │            │
│     A1  A2  [B1] C1  A3  [B2] C2  [B3] C3  A4  ...           │
│                ↓          ↓          ↓                       │
│              移除        移除        移除                      │
│                                                              │
│ 新的Hash环:                                                   │
│     0 ────────────────────────────────────── 2^32-1          │
│     │                                           │            │
│     A1  A2  C1  A3  C2  C3  A4  ...              │           │
│                                                              │
│ 用户请求变化:                                                 │
│ - 用户1（Hash=100） → A1 → 实例A ✅ (不变)                    │
│ - 用户2（Hash=500） → C1 → 实例C ✅ (自动迁移)                │
│ - 用户3（Hash=800） → C1 → 实例C ✅ (不变)                    │
│                                                              │
│ 优势:                                                        │
│ 1. 只有原本路由到实例B的用户受影响（约1/3）                    │
│ 2. 这些用户自动迁移到相邻实例（实例C或实例A）                   │
│ 3. 其他用户（约2/3）完全不受影响                               │
│ 4. 实例恢复后，用户会自动迁移回来                              │
└─────────────────────────────────────────────────────────────┘

对比普通IP Hash:
- 普通IP Hash: 实例宕机后，Hash % 2 重新计算，所有用户重新分配
- 一致性Hash: 只有1/N的用户受影响，其他用户不变
```

**✅ 解决方案2：IP Hash + 故障转移（简单实现）**

```java
/**
 * IP Hash + 故障转移策略
 * 当目标实例不可用时，自动切换到下一个实例
 */
public class IpHashWithFailoverRule extends AbstractLoadBalancerRule {
    
    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        if (lb == null) {
            return null;
        }
        
        List<Server> servers = lb.getReachableServers();
        if (servers.isEmpty()) {
            return null;
        }
        
        // 获取客户端IP
        String clientIp = getClientIp();
        if (clientIp == null) {
            return servers.get(0);
        }
        
        // 计算Hash值
        int hash = Math.abs(clientIp.hashCode());
        int index = hash % servers.size();
        
        // 尝试选择目标实例
        Server targetServer = servers.get(index);
        
        // 检查实例是否可用
        if (isServerAvailable(targetServer)) {
            return targetServer;
        }
        
        // 故障转移：顺序尝试其他实例
        for (int i = 1; i < servers.size(); i++) {
            int nextIndex = (index + i) % servers.size();
            Server nextServer = servers.get(nextIndex);
            if (isServerAvailable(nextServer)) {
                // 记录故障转移日志
                log.warn("IP Hash target server {} is unavailable, failover to {}", 
                    targetServer.getId(), nextServer.getId());
                return nextServer;
            }
        }
        
        // 所有实例都不可用，返回null
        return null;
    }
    
    /**
     * 检查实例是否可用
     * 可以结合Ping检查、熔断器状态等
     */
    private boolean isServerAvailable(Server server) {
        ILoadBalancer lb = getLoadBalancer();
        IPing ping = lb.getPing();
        
        // 使用Ping检查实例是否存活
        if (ping != null && !ping.isAlive(server)) {
            return false;
        }
        
        // 可以增加其他检查：熔断器状态、并发数等
        // ...
        
        return true;
    }
    
    private String getClientIp() {
        // 实现同上
        return null;
    }
}
```

**✅ 解决方案3：会话持久化到Redis（推荐生产环境）**

```yaml
最佳实践：不依赖IP Hash，使用外部存储

方案：会话数据存储到Redis
┌─────────────────────────────────────────────────────────────┐
│ 架构:                                                        │
│                                                              │
│  用户请求 → 任意实例 → Redis（会话存储）                      │
│                                                              │
│ 优势:                                                        │
│ 1. 任何实例都可以处理任何用户的请求                            │
│ 2. 实例宕机不影响会话数据                                     │
│ 3. 可以使用任何负载均衡策略（轮询、随机等）                     │
│ 4. 水平扩展简单，增减实例不影响会话                            │
│                                                              │
│ 实现:                                                        │
│ - Spring Session + Redis                                    │
│ - 自定义会话管理 + Redis                                      │
│ - 分布式缓存（Hazelcast、Ehcache等）                          │
└─────────────────────────────────────────────────────────────┘

配置示例（Spring Session + Redis）:
```

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  session:
    store-type: redis  # 会话存储到Redis
    timeout: 1800  # 会话超时时间（秒）
  redis:
    host: localhost
    port: 6379
    
# 使用默认的轮询策略，不需要IP Hash
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
```

```java
// 启用Spring Session
@EnableRedisHttpSession
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// 使用示例
@RestController
public class DocumentController {
    
    @PostMapping("/document/{id}/edit")
    public Result edit(@PathVariable String id, 
                      @RequestBody EditRequest request,
                      HttpSession session) {
        // 会话数据自动存储到Redis
        // 任何实例都可以访问
        DocumentSession docSession = (DocumentSession) session.getAttribute("doc_" + id);
        if (docSession == null) {
            docSession = new DocumentSession(id);
            session.setAttribute("doc_" + id, docSession);
        }
        
        // 处理编辑操作
        docSession.applyEdit(request);
        
        return Result.success();
    }
}
```

**方案对比**：

```yaml
方案对比表:
┌──────────────────┬─────────────┬─────────────┬─────────────┐
│ 方案              │ 复杂度      │ 可用性      │ 推荐度      │
├──────────────────┼─────────────┼─────────────┼─────────────┤
│ 普通IP Hash      │ ⭐ 简单     │ ❌ 低       │ ⚠️ 不推荐   │
│ 一致性Hash       │ ⭐⭐ 中等   │ ✅ 中       │ ✅ 推荐     │
│ IP Hash+故障转移 │ ⭐⭐ 中等   │ ✅ 中       │ ✅ 可用     │
│ Redis会话存储    │ ⭐⭐⭐ 复杂 │ ✅✅ 高     │ ✅✅ 强烈推荐│
└──────────────────┴─────────────┴─────────────┴─────────────┘

选择建议:
1. 开发/测试环境: 普通IP Hash（简单快速）
2. 小规模生产环境: 一致性Hash（平衡性能和可用性）
3. 大规模生产环境: Redis会话存储（高可用、易扩展）
4. 临时方案: IP Hash+故障转移（快速解决单点问题）
```

**总结**：

IP Hash的单点故障问题及解决方案已在章节开头说明，详细实现请参考上述三种方案。

### 3.2 基于权重的策略

```java
/**
 * 权重策略：根据配置的权重分配流量
 * 配置示例：
 * user-service:
 *   ribbon:
 *     listOfServers: 192.168.1.100:8080,192.168.1.101:8080,192.168.1.102:8080
 *     server-weights: 5,3,2  # 权重比例 5:3:2
 */
public class WeightedRule extends AbstractLoadBalancerRule {
    
    private Map<String, Integer> serverWeights = new ConcurrentHashMap<>();
    private Random random = new Random();
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        String weightsConfig = clientConfig.getPropertyAsString("server-weights", "");
        if (!weightsConfig.isEmpty()) {
            parseWeights(weightsConfig);
        }
    }
    
    private void parseWeights(String weightsConfig) {
        String[] weights = weightsConfig.split(",");
        ILoadBalancer lb = getLoadBalancer();
        List<Server> servers = lb.getAllServers();
        
        for (int i = 0; i < Math.min(weights.length, servers.size()); i++) {
            Server server = servers.get(i);
            int weight = Integer.parseInt(weights[i].trim());
            serverWeights.put(server.getId(), weight);
        }
    }
    
    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        if (lb == null) {
            return null;
        }
        
        List<Server> servers = lb.getReachableServers();
        if (servers.isEmpty()) {
            return null;
        }
        
        // 计算总权重
        int totalWeight = 0;
        for (Server server : servers) {
            Integer weight = serverWeights.getOrDefault(server.getId(), 1);
            totalWeight += weight;
        }
        
        // 随机选择
        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;
        
        for (Server server : servers) {
            Integer weight = serverWeights.getOrDefault(server.getId(), 1);
            currentWeight += weight;
            if (randomWeight < currentWeight) {
                return server;
            }
        }
        
        return servers.get(0);
    }
}
```

### 3.3 基于灰度发布的策略

#### 3.3.1 什么是灰度发布

**灰度发布（Gray Release）**，也称为金丝雀发布（Canary Release），是一种平滑过渡的发布策略。

```
传统发布 vs 灰度发布：

传统发布（一次性全量发布）：
┌─────────────────────────────────────────────────────────────┐
│ 旧版本 v1.0（3个实例）                                         │
│ ├─ 实例A (v1.0)                                              │
│ ├─ 实例B (v1.0)                                              │
│ └─ 实例C (v1.0)                                              │
│                                                              │
│ 发布新版本 v2.0 ↓                                             │
│                                                              │
│ 新版本 v2.0（3个实例）                                         │
│ ├─ 实例A (v2.0) ← 全部切换                                    │
│ ├─ 实例B (v2.0) ← 全部切换                                    │
│ └─ 实例C (v2.0) ← 全部切换                                    │
│                                                              │
│ 风险：如果v2.0有bug，所有用户都受影响 ❌                        │
└─────────────────────────────────────────────────────────────┘

灰度发布（逐步放量）：
┌─────────────────────────────────────────────────────────────┐
│ 阶段1：小流量验证（5%）                                        │
│ ├─ 实例A (v1.0) ← 95%流量                                    │
│ ├─ 实例B (v1.0) ← 95%流量                                    │
│ ├─ 实例C (v1.0) ← 95%流量                                    │
│ └─ 实例D (v2.0) ← 5%流量（灰度实例）                          │
│                                                              │
│ 阶段2：扩大范围（50%）                                         │
│ ├─ 实例A (v1.0) ← 50%流量                                    │
│ ├─ 实例B (v2.0) ← 25%流量                                    │
│ ├─ 实例C (v2.0) ← 25%流量                                    │
│ └─ 实例D (v2.0) ← 已验证                                      │
│                                                              │
│ 阶段3：全量发布（100%）                                        │
│ ├─ 实例A (v2.0) ← 全部切换                                    │
│ ├─ 实例B (v2.0)                                              │
│ ├─ 实例C (v2.0)                                              │
│ └─ 实例D (v2.0)                                              │
│                                                              │
│ 优势：发现问题可以快速回滚，只影响少量用户 ✅                    │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.2 灰度发布的典型场景

**场景1：新功能验证**

```yaml
业务场景：电商平台上线新的推荐算法

灰度策略：
阶段1（内部员工）：
  - 标识：员工账号（userId in [1001, 1002, 1003]）
  - 流量：0.1%
  - 目的：内部验证功能是否正常

阶段2（种子用户）：
  - 标识：活跃用户（vip=true）
  - 流量：5%
  - 目的：收集真实用户反馈

阶段3（部分地区）：
  - 标识：指定城市（city=北京）
  - 流量：20%
  - 目的：区域性验证

阶段4（全量发布）：
  - 标识：所有用户
  - 流量：100%
  - 目的：正式上线

回滚策略：
- 任何阶段发现问题，立即停止灰度
- 将灰度用户切回稳定版本
- 影响范围可控
```

**场景2：A/B测试**

```yaml
业务场景：测试两种不同的UI设计，看哪种转化率更高

版本划分：
- 版本A（蓝色主题）：50%用户
- 版本B（绿色主题）：50%用户

路由策略：
- 根据userId % 2 决定版本
- userId为偶数 → 版本A
- userId为奇数 → 版本B

数据收集：
- 版本A转化率：3.2%
- 版本B转化率：4.1%
- 结论：版本B效果更好，全量发布版本B
```

**场景3：性能优化验证**

```yaml
业务场景：优化了数据库查询，需要验证性能提升

灰度策略：
- 新版本（优化后）：10%流量
- 旧版本（当前）：90%流量

监控指标：
- 响应时间：新版本 200ms vs 旧版本 500ms ✅
- 错误率：新版本 0.1% vs 旧版本 0.1% ✅
- CPU使用率：新版本 30% vs 旧版本 50% ✅

结论：新版本性能更好，逐步扩大到100%
```

#### 3.3.3 灰度发布的路由策略

**策略1：基于用户标识**

```java
// 根据用户ID、用户类型等标识路由
if (userId % 100 < 5) {
    // 5%的用户路由到灰度版本
    return grayServers;
} else {
    // 95%的用户路由到稳定版本
    return stableServers;
}
```

**策略2：基于请求头**

```java
// 通过HTTP Header控制
String grayVersion = request.getHeader("X-Gray-Version");
if ("v2.0".equals(grayVersion)) {
    return grayServers;
}
```

**策略3：基于地理位置**

```java
// 根据用户所在城市
String city = request.getHeader("X-User-City");
if ("北京".equals(city) || "上海".equals(city)) {
    return grayServers; // 一线城市先灰度
}
```

**策略4：基于时间段**

```java
// 在特定时间段启用灰度
int hour = LocalDateTime.now().getHour();
if (hour >= 2 && hour <= 6) {
    return grayServers; // 凌晨低峰期灰度
}
```

#### 3.3.4 完整实现示例

**代码实现**：

```java
/**
 * 灰度发布策略：根据用户标识路由到不同版本
 * 适用场景：A/B测试、金丝雀发布
 * 
 * 支持多种灰度规则：
 * 1. 基于用户ID（userId % 100 < grayPercent）
 * 2. 基于请求头（X-Gray-Version）
 * 3. 基于白名单（指定用户ID列表）
 */
public class GrayReleaseRule extends AbstractLoadBalancerRule {
    
    private static final String GRAY_VERSION_HEADER = "X-Gray-Version";
    private static final String METADATA_VERSION_KEY = "version";
    
    @Override
    public Server choose(Object key) {
        ILoadBalancer lb = getLoadBalancer();
        if (lb == null) {
            return null;
        }
        
        List<Server> servers = lb.getReachableServers();
        if (servers.isEmpty()) {
            return null;
        }
        
        // 获取灰度版本标识
        String grayVersion = getGrayVersion();
        
        if (grayVersion != null) {
            // 过滤出指定版本的实例
            List<Server> grayServers = servers.stream()
                .filter(server -> {
                    if (server instanceof DiscoveryEnabledServer) {
                        DiscoveryEnabledServer discoveryServer = (DiscoveryEnabledServer) server;
                        InstanceInfo instanceInfo = discoveryServer.getInstanceInfo();
                        String version = instanceInfo.getMetadata().get(METADATA_VERSION_KEY);
                        return grayVersion.equals(version);
                    }
                    return false;
                })
                .collect(Collectors.toList());
            
            if (!grayServers.isEmpty()) {
                // 在灰度实例中轮询
                int index = new Random().nextInt(grayServers.size());
                return grayServers.get(index);
            }
        }
        
        // 默认路由到稳定版本
        List<Server> stableServers = servers.stream()
            .filter(server -> {
                if (server instanceof DiscoveryEnabledServer) {
                    DiscoveryEnabledServer discoveryServer = (DiscoveryEnabledServer) server;
                    InstanceInfo instanceInfo = discoveryServer.getInstanceInfo();
                    String version = instanceInfo.getMetadata().get(METADATA_VERSION_KEY);
                    return version == null || "stable".equals(version);
                }
                return true;
            })
            .collect(Collectors.toList());
        
        if (!stableServers.isEmpty()) {
            int index = new Random().nextInt(stableServers.size());
            return stableServers.get(index);
        }
        
        return servers.get(0);
    }
    
    private String getGrayVersion() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader(GRAY_VERSION_HEADER);
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return null;
    }
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        // 初始化配置
    }
}
```

#### 3.3.5 灰度发布的配置与使用

**Eureka实例配置（标识版本）**：

```yaml
# 稳定版本实例配置
eureka:
  instance:
    metadata-map:
      version: stable  # 标识为稳定版本
      
# 灰度版本实例配置
eureka:
  instance:
    metadata-map:
      version: v2.0  # 标识为灰度版本v2.0
```

**网关层配置（灰度路由）**：

```java
/**
 * 网关过滤器：根据用户信息添加灰度标识
 */
@Component
public class GrayReleaseFilter implements GlobalFilter, Ordered {
    
    @Autowired
    private GrayReleaseConfig grayConfig;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 获取用户ID
        String userId = getUserId(request);
        
        // 判断是否为灰度用户
        if (isGrayUser(userId)) {
            // 添加灰度标识到请求头
            ServerHttpRequest newRequest = request.mutate()
                .header("X-Gray-Version", "v2.0")
                .build();
            
            return chain.filter(exchange.mutate().request(newRequest).build());
        }
        
        return chain.filter(exchange);
    }
    
    /**
     * 判断是否为灰度用户
     */
    private boolean isGrayUser(String userId) {
        if (userId == null) {
            return false;
        }
        
        // 策略1：白名单
        if (grayConfig.getWhiteList().contains(userId)) {
            return true;
        }
        
        // 策略2：按百分比
        long userIdNum = Long.parseLong(userId);
        return userIdNum % 100 < grayConfig.getGrayPercent();
    }
    
    @Override
    public int getOrder() {
        return -100; // 优先级高，先执行
    }
}

/**
 * 灰度配置
 */
@Configuration
@ConfigurationProperties(prefix = "gray.release")
public class GrayReleaseConfig {
    
    private boolean enabled = false;  // 是否启用灰度
    private int grayPercent = 5;  // 灰度流量百分比
    private List<String> whiteList = new ArrayList<>();  // 白名单用户
    
    // getter/setter...
}
```

**配置文件**：

```yaml
# application.yml
gray:
  release:
    enabled: true  # 启用灰度发布
    gray-percent: 5  # 5%流量灰度
    white-list:  # 白名单用户（内部测试）
      - "1001"
      - "1002"
      - "1003"

# Ribbon配置
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.example.GrayReleaseRule
```

#### 3.3.6 灰度发布的监控与回滚

**监控指标**：

```java
/**
 * 灰度发布监控
 */
@Component
public class GrayReleaseMonitor {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    /**
     * 记录灰度流量
     */
    public void recordGrayTraffic(String version, boolean success) {
        meterRegistry.counter("gray.release.traffic",
            "version", version,
            "success", String.valueOf(success))
            .increment();
    }
    
    /**
     * 记录响应时间
     */
    public void recordResponseTime(String version, long duration) {
        meterRegistry.timer("gray.release.response.time",
            "version", version)
            .record(duration, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 记录错误率
     */
    public void recordError(String version, String errorType) {
        meterRegistry.counter("gray.release.error",
            "version", version,
            "error_type", errorType)
            .increment();
    }
}
```

**自动回滚策略**：

```java
/**
 * 灰度发布自动回滚
 */
@Component
@Slf4j
public class GrayReleaseAutoRollback {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private GrayReleaseConfig grayConfig;
    
    /**
     * 定时检查灰度版本健康状态
     */
    @Scheduled(fixedRate = 60000)  // 每分钟检查一次
    public void checkGrayHealth() {
        if (!grayConfig.isEnabled()) {
            return;
        }
        
        // 获取灰度版本的错误率
        double grayErrorRate = getErrorRate("v2.0");
        double stableErrorRate = getErrorRate("stable");
        
        // 如果灰度版本错误率超过稳定版本2倍，自动回滚
        if (grayErrorRate > stableErrorRate * 2 && grayErrorRate > 0.05) {
            log.error("Gray version error rate too high: {}%, auto rollback!", 
                grayErrorRate * 100);
            
            // 禁用灰度
            grayConfig.setEnabled(false);
            
            // 发送告警
            sendAlert("灰度版本错误率过高，已自动回滚");
        }
        
        // 检查响应时间
        double grayAvgTime = getAvgResponseTime("v2.0");
        double stableAvgTime = getAvgResponseTime("stable");
        
        // 如果灰度版本响应时间超过稳定版本1.5倍，告警
        if (grayAvgTime > stableAvgTime * 1.5) {
            log.warn("Gray version response time too slow: {}ms vs {}ms", 
                grayAvgTime, stableAvgTime);
            sendAlert("灰度版本响应时间过慢");
        }
    }
    
    private double getErrorRate(String version) {
        // 从监控系统获取错误率
        // 简化实现
        return 0.0;
    }
    
    private double getAvgResponseTime(String version) {
        // 从监控系统获取平均响应时间
        // 简化实现
        return 0.0;
    }
    
    private void sendAlert(String message) {
        // 发送告警（邮件、短信、钉钉等）
        log.error("ALERT: {}", message);
    }
}
```

#### 3.3.7 灰度发布最佳实践

```yaml
最佳实践总结：

1. 灰度流量控制
   ✅ 从小流量开始（1% → 5% → 20% → 50% → 100%）
   ✅ 每个阶段观察足够时间（至少30分钟）
   ✅ 设置灰度上限，避免影响过大
   ⚠️ 不要一次性放开太多流量

2. 监控指标
   ✅ 必须监控：错误率、响应时间、QPS
   ✅ 建议监控：CPU、内存、数据库连接数
   ✅ 业务监控：转化率、订单量等业务指标
   ⚠️ 设置告警阈值，异常时自动回滚

3. 回滚策略
   ✅ 准备快速回滚方案（一键回滚）
   ✅ 设置自动回滚条件（错误率、响应时间）
   ✅ 保留稳定版本实例，随时可切换
   ⚠️ 回滚后要分析原因，不要盲目重试

4. 用户体验
   ✅ 保持用户会话一致性（同一用户始终路由到同一版本）
   ✅ 避免频繁切换版本
   ✅ 对核心用户（VIP）谨慎灰度
   ⚠️ 重要时间段（大促）避免灰度

5. 技术实现
   ✅ 使用配置中心动态调整灰度比例
   ✅ 灰度规则支持多维度（用户、地域、时间）
   ✅ 版本标识清晰，便于追踪
   ⚠️ 避免硬编码，提高灵活性

6. 团队协作
   ✅ 灰度前通知相关团队（运维、测试、产品）
   ✅ 准备灰度checklist
   ✅ 指定灰度负责人，全程跟踪
   ⚠️ 非工作时间避免灰度，确保有人值守
```

**灰度发布流程图**：

```
灰度发布完整流程：

1. 准备阶段
   ├─ 代码review通过
   ├─ 单元测试通过
   ├─ 集成测试通过
   └─ 准备回滚方案

2. 灰度发布
   ├─ 部署灰度实例（v2.0）
   ├─ 配置版本标识（metadata: version=v2.0）
   ├─ 启用灰度规则（5%流量）
   └─ 观察监控指标（30分钟）

3. 监控验证
   ├─ 检查错误率（< 0.1%）
   ├─ 检查响应时间（< 500ms）
   ├─ 检查业务指标（转化率正常）
   └─ 收集用户反馈

4. 扩大范围
   ├─ 逐步提升流量（5% → 20% → 50%）
   ├─ 每次提升后观察30分钟
   └─ 持续监控各项指标

5. 全量发布
   ├─ 流量切换到100%
   ├─ 下线旧版本实例
   └─ 灰度发布完成

6. 异常处理
   ├─ 发现问题立即停止灰度
   ├─ 将流量切回稳定版本
   ├─ 分析问题原因
   └─ 修复后重新灰度
```

### 3.4 自定义策略的注册

```java
@Configuration
public class CustomRibbonConfig {
    
    /**
     * 全局配置
     */
    @Bean
    public IRule globalRibbonRule() {
        return new WeightedRule();
    }
    
    /**
     * 针对特定服务的配置
     */
    @RibbonClient(name = "user-service", configuration = UserServiceRibbonConfig.class)
    public class RibbonClientConfig {
    }
    
    static class UserServiceRibbonConfig {
        @Bean
        public IRule ribbonRule() {
            return new GrayReleaseRule();
        }
    }
}
```

## 四、负载均衡策略对比与选择

### 4.1 策略对比表

| 组件 | 接口 | 作用 | 默认实现 | 说明 |
|------|------|------|---------|------|-------|
| RoundRobinRule | 轮询 | 简单、均匀 | 不考虑性能差异 | 实例性能相近 |
| RandomRule | 随机 | 简单、分散 | 分布不均匀 | 简单场景 |
| WeightedResponseTimeRule | 响应时间加权 | 自适应性能 | 需要预热时间 | 性能差异大 |
| RetryRule | 重试 | 容错性强 | 增加延迟 | 网络不稳定 |
| BestAvailableRule | 最低并发 | 避免过载 | 需要统计支持 | 请求时长差异大 |
| AvailabilityFilteringRule | 可用性过滤 | 自动剔除故障 | 配置复杂 | 需要高可用 |
| ZoneAvoidanceRule | 区域避让 | 就近访问 | 需要Zone配置 | 多机房部署 |

### 4.2 选择决策树

```
开始
  │
  ├─ 是否多机房部署？
  │   ├─ 是 → ZoneAvoidanceRule（推荐）
  │   └─ 否 ↓
  │
  ├─ 实例性能是否差异大？
  │   ├─ 是 → WeightedResponseTimeRule
  │   └─ 否 ↓
  │
  ├─ 是否需要会话保持？
  │   ├─ 是 → IpHashRule（自定义）
  │   └─ 否 ↓
  │
  ├─ 是否需要灰度发布？
  │   ├─ 是 → GrayReleaseRule（自定义）
  │   └─ 否 ↓
  │
  ├─ 网络是否不稳定？
  │   ├─ 是 → RetryRule + AvailabilityFilteringRule
  │   └─ 否 ↓
  │
  └─ 默认 → RoundRobinRule 或 ZoneAvoidanceRule
```

### 4.3 生产环境推荐配置

#### 场景1：单机房部署

```yaml
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.AvailabilityFilteringRule
  NFLoadBalancerPingClassName: com.netflix.loadbalancer.PingUrl
  NFLoadBalancerPingInterval: 5
  ConnectTimeout: 1000
  ReadTimeout: 3000
  MaxAutoRetries: 0
  MaxAutoRetriesNextServer: 2
  OkToRetryOnAllOperations: false
```

#### 场景2：多机房部署

```yaml
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
  EnableZoneAffinity: true
  EnableZoneExclusivity: false
  ConnectTimeout: 1000
  ReadTimeout: 3000
  MaxAutoRetries: 0
  MaxAutoRetriesNextServer: 1
  
eureka:
  instance:
    metadata-map:
      zone: zone1  # 标识所属Zone
```

#### 场景3：灰度发布

```yaml
ribbon:
  NFLoadBalancerRuleClassName: com.example.rule.GrayReleaseRule
  
eureka:
  instance:
    metadata-map:
      version: v2.0  # 标识版本
```

## 五、负载均衡的监控与调优

### 5.1 关键指标

```java
@Component
public class RibbonMetrics {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    /**
     * 监控负载均衡选择耗时
     */
    public void recordChooseServerTime(String serviceName, long duration) {
        meterRegistry.timer("ribbon.choose.server.time",
            "service", serviceName)
            .record(duration, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 监控实例选择分布
     */
    public void recordServerChosen(String serviceName, String instanceId) {
        meterRegistry.counter("ribbon.server.chosen",
            "service", serviceName,
            "instance", instanceId)
            .increment();
    }
    
    /**
     * 监控重试次数
     */
    public void recordRetryCount(String serviceName, int retryCount) {
        meterRegistry.counter("ribbon.retry.count",
            "service", serviceName,
            "count", String.valueOf(retryCount))
            .increment();
    }
    
    /**
     * 监控服务实例状态
     */
    public void recordServerStatus(String serviceName, String instanceId, boolean isAlive) {
        meterRegistry.gauge("ribbon.server.status",
            Tags.of("service", serviceName, "instance", instanceId),
            isAlive ? 1 : 0);
    }
}
```

### 5.2 性能调优

#### 5.2.1 连接池优化

```yaml
ribbon:
  # HTTP连接池配置
  MaxConnectionsPerHost: 200  # 每个主机最大连接数（默认50）
  MaxTotalConnections: 500  # 总连接数（默认200）
  PoolMinThreads: 1  # 最小线程数（默认1）
  PoolMaxThreads: 200  # 最大线程数（默认200）
  
  # 连接管理
  IsSecure: false  # 是否使用HTTPS（默认false）
  GZipPayload: true  # 启用GZIP压缩（默认false）
  
  # 连接保活
  FollowRedirects: false  # 是否跟随重定向（默认false）
  ConnectionManagerTimeout: 2000  # 从连接池获取连接的超时时间（默认2000ms）
```

**默认连接池配置说明**：

```yaml
# 如果不配置，Ribbon使用以下默认值：
ribbon:
  MaxConnectionsPerHost: 50  # 每个主机最大50个连接
  MaxTotalConnections: 200  # 总共最大200个连接
  
# 影响：
# - 高并发场景下可能出现连接池耗尽
# - 建议根据实际QPS调整
# - 经验值：MaxConnectionsPerHost = QPS × 平均响应时间（秒）× 1.5
```

#### 5.2.2 缓存优化

```yaml
ribbon:
  # 服务列表缓存
  ServerListRefreshInterval: 30000  # 服务列表刷新间隔（默认30000ms，即30秒）
  
  # Ping检查
  NFLoadBalancerPingInterval: 10  # Ping间隔（默认10秒）
  
  # 其他缓存相关配置
  ServerListRefreshIntervalOnStartup: 1000  # 启动时刷新间隔（默认1000ms）
```

**默认缓存配置说明**：

```yaml
# 不配置时的默认行为：
ribbon:
  ServerListRefreshInterval: 30000  # 默认30秒
  NFLoadBalancerPingInterval: 10  # 默认10秒
  
# 影响：
# 1. 服务列表30秒更新一次
#    - 新实例上线后，最多30秒才能被发现
#    - 实例下线后，最多30秒才会从列表移除
#    
# 2. Ping检查10秒一次（如果配置了非DummyPing）
#    - 可以更快发现实例故障
#    - 但默认使用DummyPing，不做检查
#    
# 优化建议：
# - 生产环境：ServerListRefreshInterval=10000（10秒）
# - 测试环境：ServerListRefreshInterval=5000（5秒）
# - 必须配置：NFLoadBalancerPingClassName=PingUrl
```

#### 5.2.3 超时优化

```yaml
# 根据实际情况调整
ribbon:
  ConnectTimeout: 500  # 连接超时（默认1000ms）
  ReadTimeout: 2000  # 读取超时（默认1000ms）
  
# 确保Hystrix超时 > Ribbon总超时
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 10000  # 默认1000ms
```

**默认超时配置说明**：

```yaml
# 不配置时的默认值：
ribbon:
  ConnectTimeout: 1000  # 默认1秒连接超时
  ReadTimeout: 1000  # 默认1秒读取超时
  
# 默认重试配置：
ribbon:
  MaxAutoRetries: 0  # 默认不重试同一实例
  MaxAutoRetriesNextServer: 1  # 默认切换1次实例
  OkToRetryOnAllOperations: false  # 默认只重试GET请求
  
# 总超时计算：
# 总超时 = (ConnectTimeout + ReadTimeout) × (MaxAutoRetries + 1) × (MaxAutoRetriesNextServer + 1)
# 默认值 = (1000 + 1000) × (0 + 1) × (1 + 1) = 4000ms（4秒）

# ⚠️ Hystrix默认超时只有1秒，会导致问题：
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 1000  # 默认1秒
            
# 问题：Hystrix超时(1秒) < Ribbon总超时(4秒)
# 结果：Hystrix会提前超时，Ribbon的重试机制无法生效

# 正确配置：
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 10000  # 必须 > Ribbon总超时
```

### 5.3 故障排查

#### 5.3.1 开启调试日志

```yaml
logging:
  level:
    com.netflix.loadbalancer: DEBUG
    com.netflix.client: DEBUG
```

#### 5.3.2 常见问题

**问题1：负载不均衡**

```
原因：
1. 实例启动时间不同，权重未收敛
2. 使用了有状态的策略（如IpHash）
3. 缓存未及时更新

解决：
1. 等待权重收敛（WeightedResponseTimeRule）
2. 检查策略配置
3. 缩短缓存刷新时间
```

**问题2：请求总是失败**

```
原因：
1. 所有实例都不可用
2. 超时配置过短
3. 重试次数不足

解决：
1. 检查服务实例状态
2. 调整超时配置
3. 增加重试次数
```

**问题3：响应时间过长**

```
原因：
1. 重试次数过多
2. 超时时间过长
3. 连接池耗尽

解决：
1. 减少重试次数
2. 缩短超时时间
3. 增大连接池
```

## 六、Ribbon默认配置汇总

### 6.1 完整的默认配置清单

```yaml
# Ribbon完整默认配置（不配置时的行为）
ribbon:
  # 负载均衡策略
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule  # 默认策略
  
  # 健康检查
  NFLoadBalancerPingClassName: com.netflix.niws.loadbalancer.NIWSDiscoveryPing  # 默认Ping实现
  NFLoadBalancerPingInterval: 10  # Ping间隔10秒
  
  # 超时配置
  ConnectTimeout: 1000  # 连接超时1秒
  ReadTimeout: 1000  # 读取超时1秒
  
  # 重试配置
  MaxAutoRetries: 0  # 不重试同一实例
  MaxAutoRetriesNextServer: 1  # 切换1次实例
  OkToRetryOnAllOperations: false  # 只重试GET请求
  
  # 连接池配置
  MaxConnectionsPerHost: 50  # 每个主机最大50个连接
  MaxTotalConnections: 200  # 总共最大200个连接
  PoolMinThreads: 1  # 最小线程数1
  PoolMaxThreads: 200  # 最大线程数200
  
  # 缓存配置
  ServerListRefreshInterval: 30000  # 服务列表30秒刷新一次
  
  # Zone配置
  EnableZoneAffinity: true  # 启用Zone亲和性
  EnableZoneExclusivity: false  # 不强制Zone独占
  
  # 其他配置
  IsSecure: false  # 不使用HTTPS
  GZipPayload: false  # 不启用GZIP压缩
  FollowRedirects: false  # 不跟随重定向
  ConnectionManagerTimeout: 2000  # 连接池获取连接超时2秒
```

### 6.2 默认配置的优缺点分析

**优点**：

```
✅ 开箱即用
   - 无需配置即可工作
   - 适合快速开发和测试
   
✅ 合理的默认值
   - ZoneAvoidanceRule适合大多数场景
   - 超时和重试配置相对保守
   
✅ 兼容性好
   - 单机房和多机房都能工作
   - 向后兼容
```

**缺点**：

```
❌ 性能不是最优
   - 连接池较小（50/200）
   - 服务列表刷新较慢（30秒）
   
❌ 健康检查不足
   - 默认不做主动健康检查
   - 依赖Eureka心跳机制
   
❌ 超时配置冲突
   - Ribbon默认4秒总超时
   - Hystrix默认1秒超时
   - 需要手动调整
   
❌ 容错能力有限
   - 只切换1次实例
   - 不重试同一实例
```

### 6.3 生产环境推荐配置

```yaml
# 生产环境推荐配置（覆盖默认值）
ribbon:
  # 保持默认策略（已经很好）
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
  
  # ⭐ 必须修改：启用健康检查
  NFLoadBalancerPingClassName: com.netflix.loadbalancer.PingUrl
  NFLoadBalancerPingInterval: 5  # 改为5秒
  PingUrl: /actuator/health
  
  # ⭐ 建议修改：优化超时
  ConnectTimeout: 500  # 改为500ms，快速失败
  ReadTimeout: 3000  # 改为3秒，适应慢接口
  
  # ⭐ 建议修改：增强重试
  MaxAutoRetries: 0  # 保持不重试同一实例
  MaxAutoRetriesNextServer: 2  # 改为切换2次（尝试3个实例）
  OkToRetryOnAllOperations: false  # 保持只重试GET
  
  # ⭐ 建议修改：扩大连接池
  MaxConnectionsPerHost: 200  # 改为200
  MaxTotalConnections: 500  # 改为500
  
  # ⭐ 建议修改：加快缓存刷新
  ServerListRefreshInterval: 10000  # 改为10秒
  
  # 其他优化
  GZipPayload: true  # 启用压缩
  
# ⭐ 必须修改：调整Hystrix超时
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            # 必须大于Ribbon总超时
            # 总超时 = (500+3000) × 1 × 3 = 10500ms
            timeoutInMilliseconds: 15000  # 设置为15秒
```

## 七、总结

### 7.1 核心要点

1. **Ribbon默认使用ZoneAvoidanceRule**：兼容性好，适合大多数场景
2. **默认配置相对保守**：适合开发测试，生产环境需要调优
3. **默认不做健康检查**：必须配置PingUrl启用主动探测
4. **注意Hystrix超时冲突**：Hystrix超时必须大于Ribbon总超时
5. **支持自定义扩展**：实现IRule接口满足特殊需求

### 6.2 最佳实践

| 实践 | 说明 |
|------|------|
| ✅ 生产环境使用ZoneAvoidanceRule | 支持多机房，性能好 |
| ✅ 配置合理的超时和重试 | 平衡性能和容错 |
| ✅ 启用健康检查 | 及时发现故障实例 |
| ✅ 监控负载分布 | 确保负载均衡 |
| ✅ 根据场景自定义策略 | 满足特殊需求 |
| ⚠️ 谨慎使用全量重试 | 避免雪崩 |
| ⚠️ 避免过短的超时 | 防止误判 |

---

**下一篇**：[06-Eureka生产环境最佳实践.md](./06-Eureka生产环境最佳实践.md)
