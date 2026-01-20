#!/bin/bash

# Spring Cloud Eureka 学习项目 - 集群停止脚本
# 用途：停止 Eureka Server 集群

echo "======================================"
echo "  停止 Eureka Server 集群"
echo "======================================"
echo ""

# 停止节点3
if [ -f logs/eureka-peer3.pid ]; then
    PEER3_PID=$(cat logs/eureka-peer3.pid)
    if ps -p $PEER3_PID > /dev/null; then
        echo "🛑 停止节点3（PID: $PEER3_PID）..."
        kill $PEER3_PID
        echo "✅ 节点3已停止"
    else
        echo "⚠️  节点3未运行"
    fi
    rm logs/eureka-peer3.pid
fi
echo ""

# 停止节点2
if [ -f logs/eureka-peer2.pid ]; then
    PEER2_PID=$(cat logs/eureka-peer2.pid)
    if ps -p $PEER2_PID > /dev/null; then
        echo "🛑 停止节点2（PID: $PEER2_PID）..."
        kill $PEER2_PID
        echo "✅ 节点2已停止"
    else
        echo "⚠️  节点2未运行"
    fi
    rm logs/eureka-peer2.pid
fi
echo ""

# 停止节点1
if [ -f logs/eureka-peer1.pid ]; then
    PEER1_PID=$(cat logs/eureka-peer1.pid)
    if ps -p $PEER1_PID > /dev/null; then
        echo "🛑 停止节点1（PID: $PEER1_PID）..."
        kill $PEER1_PID
        echo "✅ 节点1已停止"
    else
        echo "⚠️  节点1未运行"
    fi
    rm logs/eureka-peer1.pid
fi
echo ""

echo "======================================"
echo "  ✅ Eureka Server 集群已停止"
echo "======================================"
echo ""
