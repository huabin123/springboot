# Spring Boot Circuit Breaker & Rate Limiter

这个父模块包含了各种熔断器和限流相关的示例项目。

## 子模块

### 1. springboot-ratelimiter
基于 Guava RateLimiter 的限流示例，演示单机限流功能。

### 2. springboot-hystrix
Netflix Hystrix 熔断器示例，演示服务熔断、降级和隔离功能。

### 3. springboot-hystrix-dashboard
Hystrix Dashboard 监控面板，用于可视化监控 Hystrix 的运行状态。

## 未来计划

- **Sentinel**: 阿里巴巴开源的流量控制组件，支持分布式限流和熔断

## 构建

```bash
# 构建整个父模块
mvn clean install

# 构建特定子模块
mvn clean install -pl springboot-ratelimiter
mvn clean install -pl springboot-hystrix
mvn clean install -pl springboot-hystrix-dashboard
```

## 技术栈

- Spring Boot 2.2.5.RELEASE
- Spring Cloud Hoxton.SR3
- Netflix Hystrix
- Guava RateLimiter
