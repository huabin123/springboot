#!/bin/bash

# Eureka故障转移和负载均衡演示项目启动脚本

echo "=========================================="
echo "启动Eureka故障转移和负载均衡演示项目"
echo "=========================================="

# 检查Java版本
java -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Java，请先安装JDK 1.8"
    exit 1
fi

# 创建日志目录
mkdir -p logs

# 编译项目
echo ""
echo "1. 编译项目..."
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "错误: 编译失败"
    exit 1
fi

# 启动Eureka Server
echo ""
echo "2. 启动Eureka Server (端口8761)..."
cd demo-eureka-server
nohup java -jar target/demo-eureka-server-1.0.0.jar > ../logs/eureka-server.log 2>&1 &
EUREKA_PID=$!
echo "Eureka Server PID: $EUREKA_PID"
cd ..

# 等待Eureka Server启动
echo "等待Eureka Server启动..."
sleep 15

# 启动Provider实例1
echo ""
echo "3. 启动Provider实例1 (端口8081)..."
cd demo-service-provider
nohup java -jar target/demo-service-provider-1.0.0.jar --server.port=8081 > ../logs/provider-8081.log 2>&1 &
PROVIDER1_PID=$!
echo "Provider-1 PID: $PROVIDER1_PID"
cd ..

# 等待实例1启动
sleep 10

# 启动Provider实例2
echo ""
echo "4. 启动Provider实例2 (端口8082)..."
cd demo-service-provider
nohup java -jar target/demo-service-provider-1.0.0.jar --server.port=8082 > ../logs/provider-8082.log 2>&1 &
PROVIDER2_PID=$!
echo "Provider-2 PID: $PROVIDER2_PID"
cd ..

# 等待实例2启动
sleep 10

# 启动Provider实例3
echo ""
echo "5. 启动Provider实例3 (端口8083)..."
cd demo-service-provider
nohup java -jar target/demo-service-provider-1.0.0.jar --server.port=8083 > ../logs/provider-8083.log 2>&1 &
PROVIDER3_PID=$!
echo "Provider-3 PID: $PROVIDER3_PID"
cd ..

# 等待实例3启动
sleep 10

# 启动Consumer
echo ""
echo "6. 启动Consumer (端口9090)..."
cd demo-service-consumer
nohup java -jar target/demo-service-consumer-1.0.0.jar > ../logs/consumer.log 2>&1 &
CONSUMER_PID=$!
echo "Consumer PID: $CONSUMER_PID"
cd ..

# 保存PID到文件
echo $EUREKA_PID > logs/eureka.pid
echo $PROVIDER1_PID > logs/provider1.pid
echo $PROVIDER2_PID > logs/provider2.pid
echo $PROVIDER3_PID > logs/provider3.pid
echo $CONSUMER_PID > logs/consumer.pid

# 等待所有服务启动
echo ""
echo "等待所有服务启动..."
sleep 15

echo ""
echo "=========================================="
echo "所有服务启动完成！"
echo "=========================================="
echo ""
echo "服务访问地址："
echo "  Eureka Server:  http://localhost:8761"
echo "  Provider-1:     http://localhost:8081/hello"
echo "  Provider-2:     http://localhost:8082/hello"
echo "  Provider-3:     http://localhost:8083/hello"
echo "  Consumer:       http://localhost:9090/consumer/hello"
echo ""
echo "测试命令："
echo "  # 测试负载均衡"
echo "  for i in {1..10}; do curl http://localhost:9090/consumer/hello; echo; done"
echo ""
echo "  # 查看服务实例"
echo "  curl http://localhost:9090/consumer/instances"
echo ""
echo "  # 查看统计信息"
echo "  curl http://localhost:9090/consumer/stats"
echo ""
echo "日志文件："
echo "  Eureka Server:  logs/eureka-server.log"
echo "  Provider-1:     logs/provider-8081.log"
echo "  Provider-2:     logs/provider-8082.log"
echo "  Provider-3:     logs/provider-8083.log"
echo "  Consumer:       logs/consumer.log"
echo ""
echo "停止服务："
echo "  ./stop-demo.sh"
echo ""
echo "=========================================="
