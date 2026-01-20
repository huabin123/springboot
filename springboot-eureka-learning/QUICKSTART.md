# 快速开始指南

本文档帮助你在 **5 分钟内** 快速启动并体验 Spring Cloud Eureka 项目。

## 📋 前置检查

确保你的环境已安装：

```bash
# 检查 Java 版本（需要 1.8+）
java -version

# 检查 Maven 版本（需要 3.6+）
mvn -version
```

## 🚀 一键启动（推荐）

### 方式一：使用启动脚本（Mac/Linux）

```bash
# 1. 进入项目目录
cd springboot-eureka-learning

# 2. 一键启动所有服务
./start-all.sh

# 等待约 1 分钟，所有服务将自动启动
```

### 方式二：手动启动

#### 步骤 1：编译项目

```bash
mvn clean package -DskipTests
```

#### 步骤 2：启动 Eureka Server

```bash
cd eureka-server
java -jar target/eureka-server-1.0.0.jar
```

等待启动完成，访问：http://localhost:8761
- 用户名：`eureka`
- 密码：`eureka123`

#### 步骤 3：启动 Producer 服务（新终端）

```bash
cd eureka-client-producer
java -jar target/eureka-client-producer-1.0.0.jar
```

#### 步骤 4：启动 Consumer 服务（新终端）

```bash
cd eureka-client-consumer
java -jar target/eureka-client-consumer-1.0.0.jar
```

## 🧪 快速测试

### 1. 访问 Eureka 控制台

打开浏览器访问：http://localhost:8761

输入用户名 `eureka` 和密码 `eureka123`，你应该能看到：
- `PRODUCER-SERVICE` - 1 个实例
- `CONSUMER-SERVICE` - 1 个实例

### 2. 测试 Producer 服务

```bash
curl http://localhost:8001/hello/World
```

**预期响应**：
```
Hello World! 来自服务: producer-service, 端口: 8001, 时间: 2024-01-19 10:30:00
```

### 3. 测试 Consumer 服务

```bash
curl http://localhost:9001/consumer/hello/World
```

**预期响应**：
```
消费者调用结果: Hello World! 来自服务: producer-service, 端口: 8001, 时间: 2024-01-19 10:30:00
```

### 4. 运行完整测试（推荐）

```bash
./test-api.sh
```

这个脚本会自动测试所有接口，并显示测试结果。

## 🎯 体验负载均衡

### 1. 启动第二个 Producer 实例

```bash
cd eureka-client-producer
java -jar target/eureka-client-producer-1.0.0.jar --server.port=8002
```

### 2. 刷新 Eureka 控制台

访问 http://localhost:8761，你应该能看到 `PRODUCER-SERVICE` 有 2 个实例。

### 3. 多次调用 Consumer

```bash
# 第 1 次调用
curl http://localhost:9001/consumer/hello/Test1

# 第 2 次调用
curl http://localhost:9001/consumer/hello/Test2

# 第 3 次调用
curl http://localhost:9001/consumer/hello/Test3
```

观察响应中的 **端口号**，你会发现它在 `8001` 和 `8002` 之间轮询切换！

## 🌐 体验集群模式

### 1. 配置 hosts

编辑 hosts 文件：

```bash
# Mac/Linux
sudo vim /etc/hosts

# Windows
# 编辑 C:\Windows\System32\drivers\etc\hosts
```

添加以下内容：

```
127.0.0.1 peer1
127.0.0.1 peer2
127.0.0.1 peer3
```

### 2. 启动集群

```bash
./start-cluster.sh
```

### 3. 访问集群节点

- 节点 1：http://peer1:8761
- 节点 2：http://peer2:8762
- 节点 3：http://peer3:8763

在任意节点的控制台中，你都能看到其他两个节点的信息，说明集群同步成功！

## 🛑 停止服务

### 停止单机模式

```bash
./stop-all.sh
```

### 停止集群模式

```bash
./stop-cluster.sh
```

### 手动停止

```bash
# 查找 Java 进程
jps

# 停止指定进程
kill <PID>
```

## 📊 查看日志

```bash
# Eureka Server 日志
tail -f logs/eureka-server.log

# Producer 日志
tail -f logs/producer.log

# Consumer 日志
tail -f logs/consumer.log
```

## 🔍 常见问题

### Q1: 启动失败，提示端口被占用

**解决方案**：

```bash
# 查看端口占用情况
lsof -i:8761  # Eureka Server
lsof -i:8001  # Producer
lsof -i:9001  # Consumer

# 停止占用端口的进程
kill -9 <PID>
```

### Q2: 服务无法注册到 Eureka

**检查清单**：
1. Eureka Server 是否启动成功
2. 认证信息是否正确（eureka:eureka123）
3. 网络连接是否正常
4. 查看服务日志，确认错误信息

### Q3: Consumer 调用 Producer 失败

**检查清单**：
1. Producer 是否已注册到 Eureka（查看控制台）
2. RestTemplate 是否配置了 `@LoadBalanced` 注解
3. 服务名称是否正确（producer-service）

## 📚 下一步

恭喜你完成了快速开始！接下来你可以：

1. **深入学习**：阅读 [docs](docs/) 目录下的详细文档
2. **修改代码**：尝试添加新的接口和功能
3. **调整配置**：修改配置文件，观察效果
4. **集成项目**：将 Eureka 集成到你的实际项目中

## 🎓 学习资源

- [README.md](README.md) - 项目完整说明
- [docs/README.md](docs/README.md) - 文档目录
- [docs/01-Eureka核心概念与架构.md](docs/01-Eureka核心概念与架构.md)
- [docs/02-Eureka安全认证机制.md](docs/02-Eureka安全认证机制.md)
- [docs/03-Eureka端口鉴权实战.md](docs/03-Eureka端口鉴权实战.md)

## 💡 小贴士

- 启动服务后，等待 30 秒再进行测试，确保服务完全注册
- 使用 `./test-api.sh` 可以快速验证所有功能
- 查看日志文件可以帮助你理解服务的运行过程
- 尝试修改配置参数，观察 Eureka 的行为变化

---

**祝你学习愉快！🎉**

如有问题，请查看 [README.md](README.md) 或提交 Issue。
