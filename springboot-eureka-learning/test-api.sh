#!/bin/bash

# Spring Cloud Eureka 学习项目 - API 测试脚本
# 用途：测试所有服务接口

echo "======================================"
echo "  API 功能测试"
echo "======================================"
echo ""

# 测试 Eureka Server
echo "1️⃣  测试 Eureka Server"
echo "   URL: http://localhost:8761/eureka/apps"
echo "   认证: eureka:eureka123"
echo ""
if curl -s -u eureka:eureka123 http://localhost:8761/eureka/apps > /dev/null; then
    echo "   ✅ Eureka Server 正常"
else
    echo "   ❌ Eureka Server 异常"
fi
echo ""

# 测试 Producer 服务
echo "2️⃣  测试 Producer 服务"
echo "   URL: http://localhost:8001/hello/World"
echo ""
PRODUCER_RESPONSE=$(curl -s http://localhost:8001/hello/World)
if [ -n "$PRODUCER_RESPONSE" ]; then
    echo "   ✅ Producer 服务正常"
    echo "   响应: $PRODUCER_RESPONSE"
else
    echo "   ❌ Producer 服务异常"
fi
echo ""

# 测试 Producer 服务信息接口
echo "3️⃣  测试 Producer 服务信息"
echo "   URL: http://localhost:8001/hello/info"
echo ""
PRODUCER_INFO=$(curl -s http://localhost:8001/hello/info)
if [ -n "$PRODUCER_INFO" ]; then
    echo "   ✅ Producer 信息接口正常"
    echo "   响应: $PRODUCER_INFO"
else
    echo "   ❌ Producer 信息接口异常"
fi
echo ""

# 测试 Consumer 服务
echo "4️⃣  测试 Consumer 服务（调用 Producer）"
echo "   URL: http://localhost:9001/consumer/hello/World"
echo ""
CONSUMER_RESPONSE=$(curl -s http://localhost:9001/consumer/hello/World)
if [ -n "$CONSUMER_RESPONSE" ]; then
    echo "   ✅ Consumer 服务正常"
    echo "   响应: $CONSUMER_RESPONSE"
else
    echo "   ❌ Consumer 服务异常"
fi
echo ""

# 测试服务列表
echo "5️⃣  测试服务列表"
echo "   URL: http://localhost:9001/consumer/services"
echo ""
SERVICES=$(curl -s http://localhost:9001/consumer/services)
if [ -n "$SERVICES" ]; then
    echo "   ✅ 服务列表接口正常"
    echo "   响应: $SERVICES"
else
    echo "   ❌ 服务列表接口异常"
fi
echo ""

# 测试服务实例
echo "6️⃣  测试服务实例"
echo "   URL: http://localhost:9001/consumer/instances/producer-service"
echo ""
INSTANCES=$(curl -s http://localhost:9001/consumer/instances/producer-service)
if [ -n "$INSTANCES" ]; then
    echo "   ✅ 服务实例接口正常"
    echo "   响应: $INSTANCES"
else
    echo "   ❌ 服务实例接口异常"
fi
echo ""

# 测试认证（无认证，应该失败）
echo "7️⃣  测试认证（无认证访问，应该返回 401）"
echo "   URL: http://localhost:8761/eureka/apps"
echo ""
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8761/eureka/apps)
if [ "$HTTP_CODE" == "401" ]; then
    echo "   ✅ 认证保护正常（返回 401）"
else
    echo "   ⚠️  认证保护异常（返回 $HTTP_CODE）"
fi
echo ""

echo "======================================"
echo "  ✅ 测试完成"
echo "======================================"
echo ""
