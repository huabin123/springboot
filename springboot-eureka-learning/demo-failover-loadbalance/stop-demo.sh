#!/bin/bash

# Eureka故障转移和负载均衡演示项目停止脚本

echo "=========================================="
echo "停止Eureka故障转移和负载均衡演示项目"
echo "=========================================="

# 停止Consumer
if [ -f logs/consumer.pid ]; then
    CONSUMER_PID=$(cat logs/consumer.pid)
    echo "停止Consumer (PID: $CONSUMER_PID)..."
    kill $CONSUMER_PID 2>/dev/null
    rm logs/consumer.pid
fi

# 停止Provider实例
if [ -f logs/provider1.pid ]; then
    PROVIDER1_PID=$(cat logs/provider1.pid)
    echo "停止Provider-1 (PID: $PROVIDER1_PID)..."
    kill $PROVIDER1_PID 2>/dev/null
    rm logs/provider1.pid
fi

if [ -f logs/provider2.pid ]; then
    PROVIDER2_PID=$(cat logs/provider2.pid)
    echo "停止Provider-2 (PID: $PROVIDER2_PID)..."
    kill $PROVIDER2_PID 2>/dev/null
    rm logs/provider2.pid
fi

if [ -f logs/provider3.pid ]; then
    PROVIDER3_PID=$(cat logs/provider3.pid)
    echo "停止Provider-3 (PID: $PROVIDER3_PID)..."
    kill $PROVIDER3_PID 2>/dev/null
    rm logs/provider3.pid
fi

# 停止Eureka Server
if [ -f logs/eureka.pid ]; then
    EUREKA_PID=$(cat logs/eureka.pid)
    echo "停止Eureka Server (PID: $EUREKA_PID)..."
    kill $EUREKA_PID 2>/dev/null
    rm logs/eureka.pid
fi

echo ""
echo "所有服务已停止"
echo "=========================================="
