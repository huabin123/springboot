#!/bin/bash

# Spring Cloud Eureka 学习项目 - 启动脚本
# 用途：一键启动所有服务（单机模式）

echo "======================================"
echo "  Spring Cloud Eureka 学习项目启动"
echo "======================================"
echo ""

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误：未找到 Java 环境，请先安装 JDK 1.8 或更高版本"
    exit 1
fi

echo "✅ Java 版本："
java -version
echo ""

# 检查 Maven 环境
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误：未找到 Maven 环境，请先安装 Maven 3.6 或更高版本"
    exit 1
fi

echo "✅ Maven 版本："
mvn -version | head -n 1
echo ""

# 编译项目
echo "📦 开始编译项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 编译失败，请检查错误信息"
    exit 1
fi
echo "✅ 编译成功"
echo ""

# 启动 Eureka Server
echo "🚀 启动 Eureka Server（端口：8761）..."
cd eureka-server
nohup java -jar target/eureka-server-1.0.0.jar > ../logs/eureka-server.log 2>&1 &
EUREKA_PID=$!
echo "   进程 ID: $EUREKA_PID"
cd ..

# 等待 Eureka Server 启动
echo "⏳ 等待 Eureka Server 启动（30秒）..."
sleep 30

# 检查 Eureka Server 是否启动成功
if curl -s -u eureka:eureka123 http://localhost:8761/eureka/apps > /dev/null; then
    echo "✅ Eureka Server 启动成功"
else
    echo "❌ Eureka Server 启动失败，请查看日志：logs/eureka-server.log"
    exit 1
fi
echo ""

# 启动 Producer 服务
echo "🚀 启动 Producer 服务（端口：8001）..."
cd eureka-client-producer
nohup java -jar target/eureka-client-producer-1.0.0.jar > ../logs/producer.log 2>&1 &
PRODUCER_PID=$!
echo "   进程 ID: $PRODUCER_PID"
cd ..

# 等待 Producer 启动
echo "⏳ 等待 Producer 服务启动（15秒）..."
sleep 15
echo ""

# 启动 Consumer 服务
echo "🚀 启动 Consumer 服务（端口：9001）..."
cd eureka-client-consumer
nohup java -jar target/eureka-client-consumer-1.0.0.jar > ../logs/consumer.log 2>&1 &
CONSUMER_PID=$!
echo "   进程 ID: $CONSUMER_PID"
cd ..

# 等待 Consumer 启动
echo "⏳ 等待 Consumer 服务启动（15秒）..."
sleep 15
echo ""

# 保存进程 ID
mkdir -p logs
echo $EUREKA_PID > logs/eureka-server.pid
echo $PRODUCER_PID > logs/producer.pid
echo $CONSUMER_PID > logs/consumer.pid

echo "======================================"
echo "  ✅ 所有服务启动完成！"
echo "======================================"
echo ""
echo "📋 服务信息："
echo "   - Eureka Server: http://localhost:8761 (用户名: eureka, 密码: eureka123)"
echo "   - Producer 服务: http://localhost:8001"
echo "   - Consumer 服务: http://localhost:9001"
echo ""
echo "🧪 快速测试："
echo "   curl http://localhost:8001/hello/World"
echo "   curl http://localhost:9001/consumer/hello/World"
echo ""
echo "📊 查看日志："
echo "   tail -f logs/eureka-server.log"
echo "   tail -f logs/producer.log"
echo "   tail -f logs/consumer.log"
echo ""
echo "🛑 停止服务："
echo "   ./stop-all.sh"
echo ""
