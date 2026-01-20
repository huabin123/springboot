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

| 组件 | 接口 | 作用 | 默认实现 |
|------|------|------|---------|
| 负载均衡器 | ILoadBalancer | 管理服务列表，选择服务实例 | ZoneAwareLoadBalancer |
| 负载均衡规则 | IRule | 定义选择实例的策略 | ZoneAvoidanceRule |
| 服务列表 | ServerList | 获取可用服务列表 | DiscoveryEnabledNIWSServerList |
| 服务过滤器 | ServerListFilter | 过滤服务列表 | ZonePreferenceServerListFilter |
| 健康检查 | IPing | 检查服务实例是否存活 | DummyPing |
| 服务列表更新器 | ServerListUpdater | 更新服务列表 | PollingServerListUpdater |

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

### 2.7 ZoneAvoidanceRule（区域亲和策略，默认）

#### 2.7.1 算法原理

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
user-service:
  ribbon:
    NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
    # Zone相关配置
    EnableZoneAffinity: true
    EnableZoneExclusivity: false
```

#### 2.7.4 适用场景

- ✅ 多机房部署
- ✅ 跨区域服务调用
- ✅ 需要就近访问
- ✅ 生产环境推荐

## 三、自定义负载均衡策略

### 3.1 基于IP Hash的策略

```java
/**
 * IP Hash策略：根据客户端IP选择固定的服务实例
 * 适用场景：需要会话保持的场景
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

```java
/**
 * 灰度发布策略：根据用户标识路由到不同版本
 * 适用场景：A/B测试、金丝雀发布
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

| 策略 | 算法 | 优点 | 缺点 | 适用场景 |
|------|------|------|------|---------|
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
  MaxConnectionsPerHost: 200  # 每个主机最大连接数
  MaxTotalConnections: 500  # 总连接数
  PoolMinThreads: 1
  PoolMaxThreads: 200
  
  # 连接管理
  IsSecure: false
  GZipPayload: true  # 启用GZIP压缩
```

#### 5.2.2 缓存优化

```yaml
ribbon:
  # 服务列表缓存
  ServerListRefreshInterval: 30000  # 30秒刷新一次
  
  # Ping检查
  NFLoadBalancerPingInterval: 10  # 10秒Ping一次
```

#### 5.2.3 超时优化

```yaml
# 根据实际情况调整
ribbon:
  ConnectTimeout: 500  # 连接超时
  ReadTimeout: 2000  # 读取超时
  
# 确保Hystrix超时 > Ribbon总超时
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 10000
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

## 六、总结

### 6.1 核心要点

1. **Ribbon是客户端负载均衡**：在服务消费端选择实例
2. **多种内置策略**：根据场景选择合适的策略
3. **支持自定义扩展**：实现IRule接口
4. **配合重试机制**：提高容错能力
5. **需要监控调优**：关注关键指标

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
