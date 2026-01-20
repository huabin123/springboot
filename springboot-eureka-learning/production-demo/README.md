# Eureka生产级项目演示

## 项目简介

本项目是Eureka在生产环境中的完整实践，包括：
- Eureka Server三节点集群
- 多Zone部署（模拟多机房）
- 完整的健康检查机制
- Prometheus监控集成
- 灰度发布支持
- Docker容器化部署

## 项目结构

```
production-demo/
├── pom.xml                                    # 父POM
├── eureka-server-cluster/                     # Eureka Server集群
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   └── com/example/eureka/
│       │       ├── EurekaServerApplication.java
│       │       ├── config/
│       │       │   └── SecurityConfig.java
│       │       └── monitor/
│       │           └── EurekaServerMetrics.java
│       └── resources/
│           ├── application.yml
│           ├── application-peer1.yml
│           ├── application-peer2.yml
│           ├── application-peer3.yml
│           └── logback-spring.xml
├── service-provider/                          # 服务提供者
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   └── com/example/provider/
│       │       ├── ProviderApplication.java
│       │       ├── controller/
│       │       │   └── UserController.java
│       │       ├── service/
│       │       │   └── UserService.java
│       │       ├── config/
│       │       │   └── MetricsConfig.java
│       │       ├── health/
│       │       │   └── CustomHealthCheck.java
│       │       └── monitor/
│       │           └── ServiceMetrics.java
│       └── resources/
│           ├── application.yml
│           ├── application-zone1.yml
│           └── application-zone2.yml
├── service-consumer/                          # 服务消费者
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   └── com/example/consumer/
│       │       ├── ConsumerApplication.java
│       │       ├── controller/
│       │       │   └── ConsumerController.java
│       │       ├── config/
│       │       │   ├── RibbonConfig.java
│       │       │   └── HystrixConfig.java
│       │       ├── rule/
│       │       │   └── GrayReleaseRule.java
│       │       └── fallback/
│       │           └── UserServiceFallback.java
│       └── resources/
│           └── application.yml
├── monitoring/                                # 监控组件
│   ├── prometheus/
│   │   ├── prometheus.yml
│   │   └── alerts.yml
│   ├── grafana/
│   │   └── dashboards/
│   │       ├── eureka-dashboard.json
│   │       └── service-dashboard.json
│   └── docker-compose.yml
├── scripts/                                   # 脚本目录
│   ├── start-cluster.sh                       # 启动集群
│   ├── stop-cluster.sh                        # 停止集群
│   ├── test-failover.sh                       # 故障转移测试
│   ├── test-gray-release.sh                   # 灰度发布测试
│   └── performance-test.sh                    # 性能测试
├── docker/                                    # Docker相关
│   ├── Dockerfile-eureka
│   ├── Dockerfile-provider
│   ├── Dockerfile-consumer
│   └── docker-compose.yml
└── README.md                                  # 本文件
```

## 环境要求

- JDK 1.8
- Maven 3.6+
- Docker & Docker Compose（可选）
- 可用端口：8761-8763（Eureka）、8081-8084（Provider）、9091-9092（Consumer）

## 快速开始

### 方式1: 本地部署

```bash
# 1. 编译项目
mvn clean package -DskipTests

# 2. 启动集群
cd scripts
chmod +x *.sh
./start-cluster.sh

# 3. 验证服务
./verify-cluster.sh
```

### 方式2: Docker部署

```bash
# 1. 构建镜像
cd docker
docker-compose build

# 2. 启动服务
docker-compose up -d

# 3. 查看状态
docker-compose ps

# 4. 查看日志
docker-compose logs -f
```

## 核心功能

### 1. Eureka Server集群

**三节点集群配置**：

| 节点 | 端口 | Zone | 角色 |
|------|------|------|------|
| peer1 | 8761 | zone1 | 主节点 |
| peer2 | 8762 | zone2 | 备节点 |
| peer3 | 8763 | zone1 | 备节点 |

**访问地址**：
- http://localhost:8761 (peer1)
- http://localhost:8762 (peer2)
- http://localhost:8763 (peer3)

### 2. 多Zone部署

**Zone1（机房A）**：
- Eureka-1 (8761)
- Eureka-3 (8763)
- Provider-1 (8081)
- Provider-2 (8082)
- Consumer-1 (9091)

**Zone2（机房B）**：
- Eureka-2 (8762)
- Provider-3 (8083)
- Provider-4 (8084)
- Consumer-2 (9092)

### 3. 健康检查

自定义健康检查包括：
- 数据库连接检查
- Redis连接检查
- 依赖服务检查
- 系统资源检查（磁盘、内存）

### 4. 监控告警

**Prometheus指标**：
- `eureka_registered_instances_total` - 注册实例总数
- `eureka_available_instances_total` - 可用实例总数
- `service_requests_total` - 请求总数
- `service_requests_duration_seconds` - 请求耗时
- `ribbon_retry_count_total` - 重试次数

**Grafana Dashboard**：
- Eureka监控面板
- 服务监控面板

**访问地址**：
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### 5. 灰度发布

支持三种灰度策略：
1. **按版本灰度**：请求头携带版本标识
2. **按用户灰度**：白名单用户访问新版本
3. **按百分比灰度**：指定百分比流量到新版本

## 测试场景

### 场景1: 故障转移测试

```bash
cd scripts
./test-failover.sh
```

**测试内容**：
1. 持续发送请求
2. 随机停止一个Provider实例
3. 观察请求自动切换
4. 统计成功率和响应时间

**预期结果**：
- 成功率 > 99%
- 平均响应时间增加 < 50%
- 故障实例在30秒内被剔除

### 场景2: 灰度发布测试

```bash
cd scripts
./test-gray-release.sh
```

**测试内容**：
1. 启动灰度版本Provider
2. 普通请求路由到稳定版
3. 灰度请求路由到新版本
4. 验证流量分配比例

### 场景3: 性能测试

```bash
cd scripts
./performance-test.sh
```

**测试指标**：
- QPS（每秒请求数）
- 响应时间（P50/P95/P99）
- 错误率
- 资源使用率

**性能基准**：
- QPS > 1000
- P95响应时间 < 100ms
- 错误率 < 0.1%
- CPU使用率 < 70%

## 生产环境配置

### Eureka Server配置

```yaml
eureka:
  server:
    enable-self-preservation: true  # 生产必须开启
    renewal-percent-threshold: 0.85
    eviction-interval-timer-in-ms: 10000
    response-cache-update-interval-ms: 3000

spring:
  security:
    user:
      name: admin
      password: ${EUREKA_PASSWORD}  # 从环境变量读取
```

### Provider配置

```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
    metadata-map:
      zone: zone1
      version: stable
```

### Consumer配置

```yaml
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
  ConnectTimeout: 1000
  ReadTimeout: 3000
  MaxAutoRetriesNextServer: 2

hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 15000
      circuitBreaker:
        enabled: true
```

## 监控告警配置

### Prometheus告警规则

```yaml
groups:
  - name: eureka_alerts
    rules:
      - alert: EurekaInstanceDown
        expr: eureka_available_instances_total < eureka_registered_instances_total
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Eureka实例下线"
```

### Grafana Dashboard

导入Dashboard：
1. 访问 http://localhost:3000
2. 登录（admin/admin）
3. 导入 `monitoring/grafana/dashboards/*.json`

## 常见问题

### Q1: 如何扩展Eureka Server集群？

**A**: 添加新节点并配置互相注册：

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://peer1:8761/eureka/,http://peer2:8762/eureka/,http://peer3:8763/eureka/,http://peer4:8764/eureka/
```

### Q2: 如何实现跨机房部署？

**A**: 配置Zone和Region：

```yaml
eureka:
  instance:
    metadata-map:
      zone: zone1
  client:
    prefer-same-zone-eureka: true
    availability-zones:
      region1: zone1,zone2
    region: region1
```

### Q3: 如何优化故障转移速度？

**A**: 调整时间参数：

```yaml
# 快速配置（30秒内完成故障转移）
eureka:
  instance:
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
  server:
    eviction-interval-timer-in-ms: 10000
    response-cache-update-interval-ms: 3000
  client:
    registry-fetch-interval-seconds: 10
```

## 最佳实践

1. ✅ **Eureka Server至少3节点**
2. ✅ **开启自我保护机制**
3. ✅ **启用HTTPS和认证**
4. ✅ **配置健康检查**
5. ✅ **集成监控告警**
6. ✅ **定期备份配置**
7. ✅ **进行故障演练**

## 相关文档

- [06-Eureka生产环境最佳实践.md](../docs/06-Eureka生产环境最佳实践.md)
- [09-生产级项目演示说明.md](../docs/09-生产级项目演示说明.md)

## 技术栈

- JDK 1.8
- Spring Boot 2.3.12.RELEASE
- Spring Cloud Hoxton.SR12
- Netflix Eureka
- Netflix Ribbon
- Netflix Hystrix
- Prometheus
- Grafana
- Docker

---

**版本**: v1.0  
**更新时间**: 2026-01-20  
**状态**: 生产级演示项目

**注意**: 本项目为演示项目，实际生产环境需要根据具体情况调整配置和架构。
