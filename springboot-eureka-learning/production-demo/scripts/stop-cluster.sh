#!/bin/bash

# 生产级Eureka集群停止脚本

echo "=========================================="
echo "停止Eureka生产级集群"
echo "=========================================="

cd ..

# 停止Consumer实例
echo "停止Consumer实例..."
[ -f logs/consumer-9091.pid ] && kill $(cat logs/consumer-9091.pid) 2>/dev/null && rm logs/consumer-9091.pid
[ -f logs/consumer-9092.pid ] && kill $(cat logs/consumer-9092.pid) 2>/dev/null && rm logs/consumer-9092.pid

# 停止Provider实例
echo "停止Provider实例..."
[ -f logs/provider-8081.pid ] && kill $(cat logs/provider-8081.pid) 2>/dev/null && rm logs/provider-8081.pid
[ -f logs/provider-8082.pid ] && kill $(cat logs/provider-8082.pid) 2>/dev/null && rm logs/provider-8082.pid
[ -f logs/provider-8083.pid ] && kill $(cat logs/provider-8083.pid) 2>/dev/null && rm logs/provider-8083.pid
[ -f logs/provider-8084.pid ] && kill $(cat logs/provider-8084.pid) 2>/dev/null && rm logs/provider-8084.pid

# 停止Eureka Server集群
echo "停止Eureka Server集群..."
[ -f logs/eureka-peer1.pid ] && kill $(cat logs/eureka-peer1.pid) 2>/dev/null && rm logs/eureka-peer1.pid
[ -f logs/eureka-peer2.pid ] && kill $(cat logs/eureka-peer2.pid) 2>/dev/null && rm logs/eureka-peer2.pid
[ -f logs/eureka-peer3.pid ] && kill $(cat logs/eureka-peer3.pid) 2>/dev/null && rm logs/eureka-peer3.pid

echo ""
echo "所有服务已停止"
echo "=========================================="
