#!/bin/bash

# 生产级Eureka集群启动脚本

echo "=========================================="
echo "启动Eureka生产级集群"
echo "=========================================="

# 检查Java版本
java -version
if [ $? -ne 0 ]; then
    echo "错误: 未找到Java，请先安装JDK 1.8"
    exit 1
fi

# 创建日志目录
mkdir -p ../logs

# 编译项目
echo ""
echo "1. 编译项目..."
cd ..
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "错误: 编译失败"
    exit 1
fi

cd scripts

# 启动Eureka Server集群
echo ""
echo "2. 启动Eureka Server集群..."

# Eureka Server Peer1 (Zone1)
echo "   启动Eureka Peer1 (端口8761, Zone1)..."
cd ../eureka-server-cluster
nohup java -jar target/eureka-server-cluster-1.0.0.jar --spring.profiles.active=peer1 > ../logs/eureka-peer1.log 2>&1 &
echo $! > ../logs/eureka-peer1.pid
cd ../scripts
sleep 10

# Eureka Server Peer2 (Zone2)
echo "   启动Eureka Peer2 (端口8762, Zone2)..."
cd ../eureka-server-cluster
nohup java -jar target/eureka-server-cluster-1.0.0.jar --spring.profiles.active=peer2 > ../logs/eureka-peer2.log 2>&1 &
echo $! > ../logs/eureka-peer2.pid
cd ../scripts
sleep 10

# Eureka Server Peer3 (Zone1)
echo "   启动Eureka Peer3 (端口8763, Zone1)..."
cd ../eureka-server-cluster
nohup java -jar target/eureka-server-cluster-1.0.0.jar --spring.profiles.active=peer3 > ../logs/eureka-peer3.log 2>&1 &
echo $! > ../logs/eureka-peer3.pid
cd ../scripts

# 等待Eureka集群启动
echo "   等待Eureka集群启动..."
sleep 20

# 启动Provider实例 (Zone1)
echo ""
echo "3. 启动Provider实例 (Zone1)..."

echo "   启动Provider-1 (端口8081, Zone1)..."
cd ../service-provider
nohup java -jar target/service-provider-1.0.0.jar --spring.profiles.active=zone1 --server.port=8081 > ../logs/provider-8081.log 2>&1 &
echo $! > ../logs/provider-8081.pid
cd ../scripts
sleep 10

echo "   启动Provider-2 (端口8082, Zone1)..."
cd ../service-provider
nohup java -jar target/service-provider-1.0.0.jar --spring.profiles.active=zone1 --server.port=8082 > ../logs/provider-8082.log 2>&1 &
echo $! > ../logs/provider-8082.pid
cd ../scripts
sleep 10

# 启动Provider实例 (Zone2)
echo ""
echo "4. 启动Provider实例 (Zone2)..."

echo "   启动Provider-3 (端口8083, Zone2)..."
cd ../service-provider
nohup java -jar target/service-provider-1.0.0.jar --spring.profiles.active=zone2 --server.port=8083 > ../logs/provider-8083.log 2>&1 &
echo $! > ../logs/provider-8083.pid
cd ../scripts
sleep 10

echo "   启动Provider-4 (端口8084, Zone2)..."
cd ../service-provider
nohup java -jar target/service-provider-1.0.0.jar --spring.profiles.active=zone2 --server.port=8084 > ../logs/provider-8084.log 2>&1 &
echo $! > ../logs/provider-8084.pid
cd ../scripts
sleep 10

# 启动Consumer实例
echo ""
echo "5. 启动Consumer实例..."

echo "   启动Consumer-1 (端口9091, Zone1)..."
cd ../service-consumer
nohup java -jar target/service-consumer-1.0.0.jar --server.port=9091 --eureka.instance.metadata-map.zone=zone1 > ../logs/consumer-9091.log 2>&1 &
echo $! > ../logs/consumer-9091.pid
cd ../scripts
sleep 10

echo "   启动Consumer-2 (端口9092, Zone2)..."
cd ../service-consumer
nohup java -jar target/service-consumer-1.0.0.jar --server.port=9092 --eureka.instance.metadata-map.zone=zone2 > ../logs/consumer-9092.log 2>&1 &
echo $! > ../logs/consumer-9092.pid
cd ../scripts

# 等待所有服务启动
echo ""
echo "等待所有服务启动..."
sleep 15

echo ""
echo "=========================================="
echo "集群启动完成！"
echo "=========================================="
echo ""
echo "Eureka Server集群："
echo "  Peer1 (Zone1): http://localhost:8761"
echo "  Peer2 (Zone2): http://localhost:8762"
echo "  Peer3 (Zone1): http://localhost:8763"
echo ""
echo "Provider实例："
echo "  Zone1: http://localhost:8081, http://localhost:8082"
echo "  Zone2: http://localhost:8083, http://localhost:8084"
echo ""
echo "Consumer实例："
echo "  Zone1: http://localhost:9091/consumer/hello"
echo "  Zone2: http://localhost:9092/consumer/hello"
echo ""
echo "监控地址："
echo "  Prometheus: http://localhost:9090 (需单独启动)"
echo "  Grafana: http://localhost:3000 (需单独启动)"
echo ""
echo "日志目录: ../logs/"
echo "停止集群: ./stop-cluster.sh"
echo ""
echo "=========================================="
