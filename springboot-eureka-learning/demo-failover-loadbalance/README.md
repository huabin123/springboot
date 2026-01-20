# Eureka故障转移与负载均衡演示项目

## 项目简介

本项目通过简化的代码演示Eureka的故障转移机制和负载均衡策略的核心概念。

## 项目结构

```
demo-failover-loadbalance/
├── pom.xml                          # 父POM
├── demo-eureka-server/              # Eureka Server
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   └── com/example/eureka/
│       │       └── DemoEurekaServerApplication.java
│       └── resources/
│           └── application.yml
├── demo-service-provider/           # 服务提供者
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   └── com/example/provider/
│       │       ├── DemoProviderApplication.java
│       │       └── controller/
│       │           └── HelloController.java
│       └── resources/
│           └── application.yml
├── demo-service-consumer/           # 服务消费者
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   └── com/example/consumer/
│       │       ├── DemoConsumerApplication.java
│       │       ├── controller/
│       │       │   └── ConsumerController.java
│       │       ├── config/
│       │       │   └── RibbonConfig.java
│       │       └── rule/
│       │           ├── CustomIpHashRule.java
│       │           ├── CustomWeightedRule.java
│       │           └── CustomGrayReleaseRule.java
│       └── resources/
│           └── application.yml
├── start-demo.sh                    # 启动脚本
├── stop-demo.sh                     # 停止脚本
├── test-failover.sh                 # 故障转移测试
├── test-loadbalance.sh              # 负载均衡测试
├── test-gray-release.sh             # 灰度发布测试
└── README.md                        # 本文件
```

## 环境要求

- JDK 1.8
- Maven 3.6+
- 可用端口：8761、8081-8083、9090

## 快速开始

### 1. 启动所有服务

```bash
# 赋予脚本执行权限
chmod +x *.sh

# 启动所有服务
./start-demo.sh
```

启动脚本会依次启动：
1. Eureka Server (端口8761)
2. Provider实例1 (端口8081)
3. Provider实例2 (端口8082)
4. Provider实例3 (端口8083)
5. Consumer (端口9090)

### 2. 验证服务

```bash
# 访问Eureka控制台
open http://localhost:8761

# 测试Provider
curl http://localhost:8081/hello
curl http://localhost:8082/hello
curl http://localhost:8083/hello

# 测试Consumer（通过Ribbon负载均衡）
curl http://localhost:9090/consumer/hello
```

### 3. 停止所有服务

```bash
./stop-demo.sh
```

## 演示场景

### 场景1: 故障转移测试

**目的**：演示Eureka如何检测服务实例故障并自动切换

**步骤**：

```bash
# 1. 运行故障转移测试脚本
./test-failover.sh

# 2. 在另一个终端停止一个Provider实例
kill $(cat logs/provider1.pid)

# 3. 观察测试脚本输出，查看请求是否自动切换到其他实例
```

**预期结果**：
- 实例停止后，请求自动切换到其他健康实例
- 成功率保持在高水平（>95%）
- 约15-20秒后，Eureka剔除失效实例

**关键配置**：
```yaml
# 优化的故障检测配置
eureka:
  instance:
    lease-renewal-interval-in-seconds: 5   # 心跳间隔5秒
    lease-expiration-duration-in-seconds: 15  # 过期时间15秒
  server:
    eviction-interval-timer-in-ms: 5000    # 剔除间隔5秒
```

### 场景2: 负载均衡测试

**目的**：演示不同负载均衡策略的效果

**步骤**：

```bash
# 运行负载均衡测试脚本
./test-loadbalance.sh
```

**预期结果**：
- 使用RoundRobinRule（默认）：请求均匀分布到3个实例
- 每个实例约接收33次请求（总共100次）

**切换负载均衡策略**：

编辑 `demo-service-consumer/src/main/java/com/example/consumer/config/RibbonConfig.java`：

```java
@Bean
public IRule ribbonRule() {
    // 轮询策略（默认）
    return new RoundRobinRule();
    
    // 随机策略
    // return new RandomRule();
    
    // 响应时间加权策略
    // return new WeightedResponseTimeRule();
    
    // 自定义IP Hash策略
    // return new CustomIpHashRule();
    
    // 自定义权重策略
    // return new CustomWeightedRule();
}
```

修改后需要重启Consumer：
```bash
kill $(cat logs/consumer.pid)
cd demo-service-consumer
nohup java -jar target/demo-service-consumer-1.0.0.jar > ../logs/consumer.log 2>&1 &
echo $! > ../logs/consumer.pid
cd ..
```

### 场景3: 灰度发布测试

**目的**：演示基于版本的灰度发布

**步骤**：

```bash
# 1. 修改Consumer配置，使用灰度发布策略
# 编辑 demo-service-consumer/src/main/java/com/example/consumer/config/RibbonConfig.java
# 改为: return new CustomGrayReleaseRule();

# 2. 启动灰度版本Provider
cd demo-service-provider
java -jar target/demo-service-provider-1.0.0.jar \
  --server.port=8084 \
  --eureka.instance.metadata-map.version=v2.0 &
cd ..

# 3. 重启Consumer

# 4. 运行灰度测试
./test-gray-release.sh
```

**测试请求**：

```bash
# 普通请求（路由到稳定版）
curl http://localhost:9090/consumer/hello

# 灰度请求（路由到v2.0版本）
curl -H "X-Gray-Version: v2.0" http://localhost:9090/consumer/hello
```

**预期结果**：
- 普通请求路由到端口8081、8082、8083的稳定版实例
- 带灰度标识的请求路由到端口8084的v2.0版本实例

### 场景4: Ribbon重试机制

**目的**：验证Ribbon在请求失败时的自动重试

**步骤**：

```bash
# 1. 调用随机失败接口（30%失败率）
for i in {1..20}; do 
  curl http://localhost:9090/consumer/random-fail?failRate=30
  echo
done

# 2. 查看统计信息
curl http://localhost:9090/consumer/stats
```

**配置说明**：

```yaml
ribbon:
  ConnectTimeout: 1000              # 连接超时1秒
  ReadTimeout: 3000                 # 读取超时3秒
  MaxAutoRetries: 0                 # 同一实例不重试
  MaxAutoRetriesNextServer: 2       # 切换实例重试2次
```

**预期结果**：
- 请求失败时自动切换到其他实例重试
- 最多尝试3个实例（1次首次请求 + 2次重试）
- Hystrix降级保证最终有响应

### 场景5: Hystrix熔断

**目的**：演示Hystrix熔断器的快速失败

**步骤**：

```bash
# 1. 停止所有Provider实例
kill $(cat logs/provider1.pid)
kill $(cat logs/provider2.pid)
kill $(cat logs/provider3.pid)

# 2. 发送请求，触发熔断
for i in {1..20}; do
  curl http://localhost:9090/consumer/hello
  echo
  sleep 0.5
done
```

**预期结果**：
- 前几次请求会尝试调用Provider（失败）
- 达到熔断阈值后，直接返回降级响应
- 响应中包含 `"fallback": true`

## 核心代码说明

### 1. 自定义IP Hash策略

`CustomIpHashRule.java` - 根据客户端IP选择固定实例

```java
public Server choose(ILoadBalancer lb, Object key) {
    String clientIp = getClientIp();
    int hash = Math.abs(clientIp.hashCode());
    int index = hash % servers.size();
    return servers.get(index);
}
```

### 2. 自定义权重策略

`CustomWeightedRule.java` - 根据配置的权重分配流量

```java
// 权重配置
weightMap.put(8081, 5);  // 权重5
weightMap.put(8082, 3);  // 权重3
weightMap.put(8083, 2);  // 权重2
```

### 3. 自定义灰度发布策略

`CustomGrayReleaseRule.java` - 根据请求头路由到不同版本

```java
String grayVersion = request.getHeader("X-Gray-Version");
if (grayVersion != null) {
    // 路由到灰度版本
    return filterServersByVersion(servers, grayVersion);
} else {
    // 路由到稳定版本
    return filterStableServers(servers);
}
```

## 配置说明

### Eureka Server配置

```yaml
eureka:
  server:
    enable-self-preservation: false  # 关闭自我保护（演示环境）
    eviction-interval-timer-in-ms: 5000  # 快速剔除
    response-cache-update-interval-ms: 3000  # 快速缓存更新
```

### Eureka Client配置

```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 5  # 快速心跳
    lease-expiration-duration-in-seconds: 15  # 快速过期
  client:
    registry-fetch-interval-seconds: 5  # 快速拉取
```

### Ribbon配置

```yaml
ribbon:
  ConnectTimeout: 1000
  ReadTimeout: 3000
  MaxAutoRetries: 0
  MaxAutoRetriesNextServer: 2
```

### Hystrix配置

```yaml
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 15000
      circuitBreaker:
        enabled: true
        requestVolumeThreshold: 10
        errorThresholdPercentage: 50
```

## 常用测试命令

```bash
# 查看服务实例列表
curl http://localhost:9090/consumer/instances

# 查看统计信息
curl http://localhost:9090/consumer/stats

# 重置统计
curl http://localhost:9090/consumer/reset-stats

# 测试负载均衡（发送10次请求）
for i in {1..10}; do 
  curl http://localhost:9090/consumer/hello | grep message
done

# 测试慢响应
curl "http://localhost:9090/consumer/slow?delay=2000"

# 查看日志
tail -f logs/consumer.log
tail -f logs/eureka-server.log
tail -f logs/provider-8081.log
```

## 故障排查

### 问题1: 服务启动失败

**检查**：
```bash
# 查看日志
cat logs/eureka-server.log
cat logs/provider-8081.log
cat logs/consumer.log

# 检查端口占用
lsof -i :8761
lsof -i :8081
lsof -i :9090
```

### 问题2: Eureka控制台看不到实例

**解决**：
1. 等待30秒左右（注册需要时间）
2. 检查Eureka Server是否启动
3. 检查配置的`defaultZone`是否正确

### 问题3: 负载不均衡

**原因**：
1. 使用了有状态的策略（如IpHashRule）
2. 实例数量太少
3. 缓存未更新

**解决**：
1. 切换到RoundRobinRule
2. 增加测试请求数量
3. 等待缓存更新（5秒）

## 扩展练习

### 练习1: 调整时间参数

修改心跳、剔除、缓存等时间参数，观察对故障转移速度的影响。

### 练习2: 实现自定义策略

实现一个基于时间段的负载均衡策略（如白天和晚上使用不同的权重）。

### 练习3: 添加监控

集成Micrometer和Prometheus，采集负载均衡相关指标。

### 练习4: 压力测试

使用Apache Bench或JMeter进行压力测试：

```bash
# 使用ab进行压力测试
ab -n 1000 -c 10 http://localhost:9090/consumer/hello
```

## 相关文档

- [04-Eureka故障转移机制深度剖析.md](../docs/04-Eureka故障转移机制深度剖析.md)
- [05-Eureka负载均衡策略详解.md](../docs/05-Eureka负载均衡策略详解.md)
- [08-演示代码说明.md](../docs/08-演示代码说明.md)

## 技术栈

- JDK 1.8
- Spring Boot 2.3.12.RELEASE
- Spring Cloud Hoxton.SR12
- Netflix Eureka
- Netflix Ribbon
- Netflix Hystrix

---

**版本**: v1.0  
**更新时间**: 2026-01-20
