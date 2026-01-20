#!/bin/bash

# Spring Cloud Eureka 学习项目 - 停止脚本
# 用途：一键停止所有服务

echo "======================================"
echo "  停止 Spring Cloud Eureka 服务"
echo "======================================"
echo ""

# 停止 Consumer 服务
if [ -f logs/consumer.pid ]; then
    CONSUMER_PID=$(cat logs/consumer.pid)
    if ps -p $CONSUMER_PID > /dev/null; then
        echo "🛑 停止 Consumer 服务（PID: $CONSUMER_PID）..."
        kill $CONSUMER_PID
        echo "✅ Consumer 服务已停止"
    else
        echo "⚠️  Consumer 服务未运行"
    fi
    rm logs/consumer.pid
else
    echo "⚠️  未找到 Consumer 服务的 PID 文件"
fi
echo ""

# 停止 Producer 服务
if [ -f logs/producer.pid ]; then
    PRODUCER_PID=$(cat logs/producer.pid)
    if ps -p $PRODUCER_PID > /dev/null; then
        echo "🛑 停止 Producer 服务（PID: $PRODUCER_PID）..."
        kill $PRODUCER_PID
        echo "✅ Producer 服务已停止"
    else
        echo "⚠️  Producer 服务未运行"
    fi
    rm logs/producer.pid
else
    echo "⚠️  未找到 Producer 服务的 PID 文件"
fi
echo ""

# 停止 Eureka Server
if [ -f logs/eureka-server.pid ]; then
    EUREKA_PID=$(cat logs/eureka-server.pid)
    if ps -p $EUREKA_PID > /dev/null; then
        echo "🛑 停止 Eureka Server（PID: $EUREKA_PID）..."
        kill $EUREKA_PID
        echo "✅ Eureka Server 已停止"
    else
        echo "⚠️  Eureka Server 未运行"
    fi
    rm logs/eureka-server.pid
else
    echo "⚠️  未找到 Eureka Server 的 PID 文件"
fi
echo ""

echo "======================================"
echo "  ✅ 所有服务已停止"
echo "======================================"
echo ""
