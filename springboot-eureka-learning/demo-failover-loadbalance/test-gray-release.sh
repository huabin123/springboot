#!/bin/bash

# 灰度发布测试脚本

echo "=========================================="
echo "Eureka灰度发布测试"
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

echo "=========================================="
echo "准备工作"
echo "=========================================="
echo ""
echo "灰度发布测试需要："
echo "1. 修改 demo-service-consumer/src/main/java/com/example/consumer/config/RibbonConfig.java"
echo "   将负载均衡策略改为: return new CustomGrayReleaseRule();"
echo ""
echo "2. 启动一个灰度版本的Provider实例："
echo "   cd demo-service-provider"
echo "   java -jar target/demo-service-provider-1.0.0.jar \\"
echo "     --server.port=8084 \\"
echo "     --eureka.instance.metadata-map.version=v2.0"
echo ""
echo "3. 重启Consumer使配置生效"
echo ""
echo "按Enter继续测试（确保已完成上述步骤）..."
read

echo ""
echo "=========================================="
echo "测试场景1: 普通请求（路由到稳定版）"
echo "=========================================="
echo ""

for i in {1..5}; do
    echo "请求 $i:"
    RESPONSE=$(curl -s http://localhost:9090/consumer/hello)
    echo "$RESPONSE" | python -m json.tool
    echo ""
    sleep 1
done

echo ""
echo "=========================================="
echo "测试场景2: 灰度请求（路由到v2.0版本）"
echo "=========================================="
echo ""

for i in {1..5}; do
    echo "灰度请求 $i:"
    RESPONSE=$(curl -s -H "X-Gray-Version: v2.0" http://localhost:9090/consumer/hello)
    echo "$RESPONSE" | python -m json.tool
    echo ""
    sleep 1
done

echo ""
echo "=========================================="
echo "测试场景3: 混合请求"
echo "=========================================="
echo ""

declare -A VERSION_COUNT

for i in {1..20}; do
    # 20%的请求带灰度标识
    if [ $((i % 5)) -eq 0 ]; then
        RESPONSE=$(curl -s -H "X-Gray-Version: v2.0" http://localhost:9090/consumer/hello)
        VERSION_COUNT["v2.0"]=$((${VERSION_COUNT["v2.0"]:-0} + 1))
        echo "[$i] 灰度请求 → v2.0"
    else
        RESPONSE=$(curl -s http://localhost:9090/consumer/hello)
        VERSION_COUNT["stable"]=$((${VERSION_COUNT["stable"]:-0} + 1))
        echo "[$i] 普通请求 → stable"
    fi
done

echo ""
echo "=========================================="
echo "统计结果"
echo "=========================================="
echo ""
echo "稳定版请求: ${VERSION_COUNT["stable"]:-0} 次"
echo "灰度版请求: ${VERSION_COUNT["v2.0"]:-0} 次"
echo ""
