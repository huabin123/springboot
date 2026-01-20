#!/bin/bash

# Spring Cloud Eureka 学习项目 - 集群启动脚本
# 用途：启动 Eureka Server 集群（3个节点）

echo "======================================"
echo "  Eureka Server 集群启动"
echo "======================================"
echo ""

# 检查 hosts 配置
echo "🔍 检查 hosts 配置..."
if ! grep -q "peer1" /etc/hosts || ! grep -q "peer2" /etc/hosts || ! grep -q "peer3" /etc/hosts; then
    echo "⚠️  警告：未找到 peer1、peer2、peer3 的 hosts 配置"
    echo ""
    echo "请在 /etc/hosts 文件中添加以下配置："
    echo "127.0.0.1 peer1"
    echo "127.0.0.1 peer2"
    echo "127.0.0.1 peer3"
    echo ""
    read -p "是否继续启动？(y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi
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

# 创建日志目录
mkdir -p logs

# 启动节点1（端口 8761）
echo "🚀 启动 Eureka Server 节点1（peer1:8761）..."
cd eureka-server
nohup java -jar target/eureka-server-1.0.0.jar --spring.profiles.active=peer1 > ../logs/eureka-peer1.log 2>&1 &
PEER1_PID=$!
echo "   进程 ID: $PEER1_PID"
echo $PEER1_PID > ../logs/eureka-peer1.pid
cd ..
echo ""

# 等待节点1启动
echo "⏳ 等待节点1启动（30秒）..."
sleep 30

# 启动节点2（端口 8762）
echo "🚀 启动 Eureka Server 节点2（peer2:8762）..."
cd eureka-server
nohup java -jar target/eureka-server-1.0.0.jar --spring.profiles.active=peer2 > ../logs/eureka-peer2.log 2>&1 &
PEER2_PID=$!
echo "   进程 ID: $PEER2_PID"
echo $PEER2_PID > ../logs/eureka-peer2.pid
cd ..
echo ""

# 等待节点2启动
echo "⏳ 等待节点2启动（30秒）..."
sleep 30

# 启动节点3（端口 8763）
echo "🚀 启动 Eureka Server 节点3（peer3:8763）..."
cd eureka-server
nohup java -jar target/eureka-server-1.0.0.jar --spring.profiles.active=peer3 > ../logs/eureka-peer3.log 2>&1 &
PEER3_PID=$!
echo "   进程 ID: $PEER3_PID"
echo $PEER3_PID > ../logs/eureka-peer3.pid
cd ..
echo ""

# 等待节点3启动
echo "⏳ 等待节点3启动（30秒）..."
sleep 30

echo "======================================"
echo "  ✅ Eureka Server 集群启动完成！"
echo "======================================"
echo ""
echo "📋 集群节点信息："
echo "   - 节点1: http://peer1:8761 (用户名: eureka, 密码: eureka123)"
echo "   - 节点2: http://peer2:8762 (用户名: eureka, 密码: eureka123)"
echo "   - 节点3: http://peer3:8763 (用户名: eureka, 密码: eureka123)"
echo ""
echo "📊 查看日志："
echo "   tail -f logs/eureka-peer1.log"
echo "   tail -f logs/eureka-peer2.log"
echo "   tail -f logs/eureka-peer3.log"
echo ""
echo "🛑 停止集群："
echo "   ./stop-cluster.sh"
echo ""
