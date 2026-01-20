#!/bin/bash

# 负载均衡测试脚本

echo "=========================================="
echo "Eureka负载均衡测试"
echo "=========================================="
echo ""

# 检查服务是否启动
echo "1. 检查服务状态..."
curl -s http://localhost:9090/consumer/instances > /dev/null
if [ $? -ne 0 ]; then
    echo "错误: Consumer未启动，请先运行 ./start-demo.sh"
    exit 1
fi

echo "服务状态正常"
echo ""

# 查看当前实例列表
echo "2. 当前服务实例："
curl -s http://localhost:9090/consumer/instances | python -m json.tool
echo ""

# 重置统计
echo "3. 重置统计信息..."
curl -s http://localhost:9090/consumer/reset-stats > /dev/null
echo ""

# 发送测试请求
echo "4. 发送100个测试请求..."
echo ""

declare -A INSTANCE_COUNT

for i in {1..100}; do
    RESPONSE=$(curl -s http://localhost:9090/consumer/hello)
    INSTANCE=$(echo "$RESPONSE" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
    
    # 统计每个实例的请求数
    INSTANCE_COUNT[$INSTANCE]=$((${INSTANCE_COUNT[$INSTANCE]:-0} + 1))
    
    # 每10次显示进度
    if [ $((i % 10)) -eq 0 ]; then
        echo "已完成: $i/100"
    fi
done

echo ""
echo "=========================================="
echo "负载均衡统计结果"
echo "=========================================="
echo ""

# 显示统计结果
for instance in "${!INSTANCE_COUNT[@]}"; do
    count=${INSTANCE_COUNT[$instance]}
    percentage=$(awk "BEGIN {printf \"%.2f\", $count}")
    echo "$instance: $count 次 ($percentage%)"
done

echo ""
echo "总请求数: 100"
echo ""

# 查看Consumer统计
echo "=========================================="
echo "Consumer统计信息"
echo "=========================================="
curl -s http://localhost:9090/consumer/stats | python -m json.tool
echo ""
