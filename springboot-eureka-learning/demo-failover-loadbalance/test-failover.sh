#!/bin/bash

# 故障转移测试脚本

echo "=========================================="
echo "Eureka故障转移测试"
echo "=========================================="
echo ""

# 检查服务是否启动
echo "1. 检查服务状态..."
curl -s http://localhost:8761 > /dev/null
if [ $? -ne 0 ]; then
    echo "错误: Eureka Server未启动，请先运行 ./start-demo.sh"
    exit 1
fi

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
echo "统计已重置"
echo ""

# 持续发送请求
echo "4. 开始持续发送请求（每秒1次）..."
echo "   请在另一个终端手动停止一个Provider实例，观察故障转移"
echo "   停止命令示例: kill \$(cat logs/provider1.pid)"
echo ""
echo "   按Ctrl+C停止测试"
echo ""

SUCCESS_COUNT=0
FAIL_COUNT=0
TOTAL_COUNT=0

while true; do
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    # 发送请求
    RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:9090/consumer/hello 2>&1)
    HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
    BODY=$(echo "$RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" = "200" ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        # 提取实例信息
        INSTANCE=$(echo "$BODY" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
        echo "[$(date '+%H:%M:%S')] ✓ 请求成功 - $INSTANCE"
    else
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo "[$(date '+%H:%M:%S')] ✗ 请求失败 - HTTP $HTTP_CODE"
    fi
    
    # 每10次请求显示统计
    if [ $((TOTAL_COUNT % 10)) -eq 0 ]; then
        SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", $SUCCESS_COUNT * 100.0 / $TOTAL_COUNT}")
        echo ""
        echo "--- 统计信息 (总计: $TOTAL_COUNT 次) ---"
        echo "成功: $SUCCESS_COUNT 次"
        echo "失败: $FAIL_COUNT 次"
        echo "成功率: $SUCCESS_RATE%"
        echo ""
    fi
    
    sleep 1
done
