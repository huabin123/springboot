# Spring Cloud Eureka 深度学习项目

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Hoxton.SR3-blue.svg)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## 📖 项目简介

本项目是一个完整的 Spring Cloud Eureka 学习项目，涵盖了 Eureka 服务注册与发现的核心功能，包括：

- ✅ Eureka Server 搭建与配置
- ✅ Spring Security 安全认证
- ✅ 服务提供者（Producer）实现
- ✅ 服务消费者（Consumer）实现
- ✅ Ribbon 客户端负载均衡
- ✅ 高可用集群部署
- ✅ 完整的文档和示例

## 🏗️ 项目结构

```
springboot-eureka-learning/
├── eureka-server/                    # Eureka服务端（带鉴权）
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/huabin/eureka/server/
│   │   │   │       ├── EurekaServerApplication.java      # 启动类
│   │   │   │       └── config/
│   │   │   │           └── WebSecurityConfig.java        # Security配置
│   │   │   └── resources/
│   │   │       ├── application.yml                        # 单机配置
│   │   │       ├── application-peer1.yml                  # 集群节点1配置
│   │   │       ├── application-peer2.yml                  # 集群节点2配置
│   │   │       └── application-peer3.yml                  # 集群节点3配置
│   │   └── test/
│   └── pom.xml
│
├── eureka-client-producer/           # 服务提供者
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/huabin/eureka/producer/
│   │   │   │       ├── ProducerApplication.java          # 启动类
│   │   │   │       └── controller/
│   │   │   │           └── HelloController.java          # REST接口
│   │   │   └── resources/
│   │   │       └── application.yml                        # 配置文件
│   │   └── test/
│   └── pom.xml
│
├── eureka-client-consumer/           # 服务消费者
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/huabin/eureka/consumer/
│   │   │   │       ├── ConsumerApplication.java          # 启动类
│   │   │   │       └── controller/
│   │   │   │           └── ConsumerController.java       # REST接口
│   │   │   └── resources/
│   │   │       └── application.yml                        # 配置文件
│   │   └── test/
│   └── pom.xml
│
├── docs/                             # 文档目录
│   ├── 01-Eureka核心概念与架构.md
│   ├── 02-Eureka安全认证机制.md
│   └── 03-Eureka端口鉴权实战.md
│
├── pom.xml                           # 父POM
└── README.md                         # 项目说明
```

## 🚀 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 1.8 | Java开发工具包 |
| Spring Boot | 2.2.5.RELEASE | Spring Boot框架 |
| Spring Cloud | Hoxton.SR3 | Spring Cloud框架 |
| Eureka Server | 2.2.5.RELEASE | 服务注册中心 |
| Eureka Client | 2.2.5.RELEASE | 服务注册客户端 |
| Spring Security | 5.2.2.RELEASE | 安全认证框架 |
| Ribbon | 2.2.5.RELEASE | 客户端负载均衡 |
| Maven | 3.6+ | 项目构建工具 |

## 📋 前置要求

- JDK 1.8 或更高版本
- Maven 3.6 或更高版本
- IDE（推荐 IntelliJ IDEA 或 Eclipse）

## 🔧 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd springboot-eureka-learning
```

### 2. 编译项目

```bash
# 在项目根目录执行
mvn clean install
```

### 3. 启动 Eureka Server（单机模式）

```bash
cd eureka-server
mvn spring-boot:run
```

访问 Eureka 控制台：http://localhost:8761
- 用户名：`eureka`
- 密码：`eureka123`

### 4. 启动 Producer 服务

```bash
cd eureka-client-producer
mvn spring-boot:run
```

服务端口：`8001`

### 5. 启动 Consumer 服务

```bash
cd eureka-client-consumer
mvn spring-boot:run
```

服务端口：`9001`

## 🧪 功能测试

### 1. 测试 Producer 服务

```bash
# 直接调用 Producer
curl http://localhost:8001/hello/World

# 预期响应：
# Hello World! 来自服务: producer-service, 端口: 8001, 时间: 2024-01-19 10:30:00

# 获取服务信息
curl http://localhost:8001/hello/info
```

### 2. 测试 Consumer 服务

```bash
# 通过 Consumer 调用 Producer
curl http://localhost:9001/consumer/hello/World

# 预期响应：
# 消费者调用结果: Hello World! 来自服务: producer-service, 端口: 8001, 时间: 2024-01-19 10:30:00

# 获取所有服务列表
curl http://localhost:9001/consumer/services

# 获取 Producer 服务的所有实例
curl http://localhost:9001/consumer/instances/producer-service
```

### 3. 测试负载均衡

```bash
# 启动第二个 Producer 实例（端口 8002）
cd eureka-client-producer
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8002

# 多次调用 Consumer，观察响应的端口号（轮询）
curl http://localhost:9001/consumer/hello/World
```

### 4. 测试认证

```bash
# 测试无认证访问（应该失败）
curl http://localhost:8761/eureka/apps
# 预期响应：401 Unauthorized

# 测试带认证访问（应该成功）
curl -u eureka:eureka123 http://localhost:8761/eureka/apps
# 预期响应：返回所有注册的服务列表（XML格式）
```

## 🔐 安全认证

### Eureka Server 认证配置

本项目使用 Spring Security 实现 HTTP Basic 认证：

**默认账号**：
- 管理员账号：`admin` / `admin123`（拥有所有权限）
- 服务账号：`eureka` / `eureka123`（用于服务注册和发现）

**配置方式**：

```yaml
# Eureka Server 配置
spring:
  security:
    user:
      name: eureka
      password: eureka123

# Eureka Client 配置（需要带认证信息）
eureka:
  client:
    service-url:
      defaultZone: http://eureka:eureka123@localhost:8761/eureka/
```

## 🌐 集群部署

### 配置 hosts 文件

```bash
# Linux/Mac: /etc/hosts
# Windows: C:\Windows\System32\drivers\etc\hosts

127.0.0.1 peer1
127.0.0.1 peer2
127.0.0.1 peer3
```

### 启动集群节点

```bash
# 启动节点1（端口 8761）
java -jar eureka-server/target/eureka-server-1.0.0.jar --spring.profiles.active=peer1

# 启动节点2（端口 8762）
java -jar eureka-server/target/eureka-server-1.0.0.jar --spring.profiles.active=peer2

# 启动节点3（端口 8763）
java -jar eureka-server/target/eureka-server-1.0.0.jar --spring.profiles.active=peer3
```

### 访问集群节点

- 节点1：http://peer1:8761
- 节点2：http://peer2:8762
- 节点3：http://peer3:8763

## 📚 核心功能说明

### 1. 服务注册与发现

- **服务注册**：服务启动时自动向 Eureka Server 注册
- **服务发现**：通过服务名称从 Eureka Server 获取服务实例列表
- **心跳机制**：服务定期向 Eureka Server 发送心跳，保持注册状态

### 2. 负载均衡

- 使用 Ribbon 实现客户端负载均衡
- 支持多种负载均衡策略（轮询、随机、权重等）
- 自动剔除不可用的服务实例

### 3. 安全认证

- 基于 Spring Security 的 HTTP Basic 认证
- 支持多用户、多角色管理
- 密码使用 BCrypt 加密存储

### 4. 高可用集群

- 支持多节点部署
- 节点间自动同步注册信息
- 提供更高的可用性和容错能力

## 📖 详细文档

- [01-Eureka核心概念与架构](docs/01-Eureka核心概念与架构.md)
- [02-Eureka安全认证机制](docs/02-Eureka安全认证机制.md)
- [03-Eureka端口鉴权实战](docs/03-Eureka端口鉴权实战.md)

## ⚠️ 常见问题

### 1. 服务无法注册

**问题**：服务启动后无法在 Eureka 控制台看到

**解决方案**：
- 检查 Eureka Server 是否启动
- 检查认证信息是否正确
- 检查网络连接是否正常
- 查看服务日志，确认是否有错误信息

### 2. CSRF 错误

**问题**：403 Forbidden - Could not verify the provided CSRF token

**解决方案**：
确认 Security 配置中已关闭 CSRF：
```java
http.csrf().disable();
```

### 3. 服务调用失败

**问题**：java.net.UnknownHostException: producer-service

**解决方案**：
- 确认服务已在 Eureka Server 注册
- 确认 RestTemplate 配置了 `@LoadBalanced` 注解
- 确认服务名称正确

## 🎯 最佳实践

1. **密码管理**
   - 使用强密码，定期更换
   - 不要将密码硬编码在代码中
   - 使用配置中心管理敏感信息

2. **生产环境**
   - 启用 HTTPS
   - 配置访问控制和防火墙
   - 启用自我保护模式
   - 记录审计日志

3. **性能优化**
   - 合理配置心跳间隔和超时时间
   - 调整缓存更新频率
   - 使用集群部署提高可用性

4. **监控告警**
   - 使用 Actuator 暴露监控端点
   - 集成监控系统（如 Prometheus、Grafana）
   - 配置告警规则

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 👨‍💻 作者

**huabin**

- GitHub: [@huabin123](https://github.com/huabin123)
- Email: your.email@example.com

## 🙏 致谢

感谢 Spring Cloud 团队提供的优秀框架！

---

**如果这个项目对你有帮助，请给个 ⭐️ Star 支持一下！**
