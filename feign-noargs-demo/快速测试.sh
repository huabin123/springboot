#!/bin/bash

# 快速测试脚本 - 测试所有场景

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "========================================"
echo -e "${BLUE}Feign 无参构造方法问题 - 快速测试${NC}"
echo "========================================"
echo ""

# 检查服务是否启动
echo -e "${YELLOW}检查服务状态...${NC}"
if ! curl -s http://localhost:8081/api/users/health > /dev/null 2>&1; then
    echo -e "${RED}✗ Provider Service 未启动 (端口 8081)${NC}"
    echo "请先运行: ./启动脚本.sh"
    exit 1
fi
echo -e "${GREEN}✓ Provider Service 运行中${NC}"

if ! curl -s http://localhost:8082/test/health > /dev/null 2>&1; then
    echo -e "${RED}✗ Consumer Service 未启动 (端口 8082)${NC}"
    echo "请先运行: ./启动脚本.sh"
    exit 1
fi
echo -e "${GREEN}✓ Consumer Service 运行中${NC}"
echo ""

# 测试场景 1: 问题复现
echo "========================================"
echo -e "${YELLOW}场景 1: 问题复现（无参构造方法缺失）${NC}"
echo "========================================"
echo -e "${BLUE}接口: GET http://localhost:8082/test/problem/1${NC}"
echo ""
RESULT1=$(curl -s http://localhost:8082/test/problem/1)
echo "$RESULT1" | python3 -m json.tool 2>/dev/null || echo "$RESULT1"
SUCCESS1=$(echo "$RESULT1" | grep -o '"success"[[:space:]]*:[[:space:]]*false' | wc -l)
if [ $SUCCESS1 -gt 0 ]; then
    echo ""
    echo -e "${GREEN}✓ 测试通过：成功复现问题（预期失败）${NC}"
else
    echo ""
    echo -e "${RED}✗ 测试失败：应该失败但成功了${NC}"
fi
echo ""

# 测试场景 2: 问题修复
echo "========================================"
echo -e "${YELLOW}场景 2: 问题修复（添加无参构造方法）${NC}"
echo "========================================"
echo -e "${BLUE}接口: GET http://localhost:8082/test/fixed/1${NC}"
echo ""
RESULT2=$(curl -s http://localhost:8082/test/fixed/1)
echo "$RESULT2" | python3 -m json.tool 2>/dev/null || echo "$RESULT2"
SUCCESS2=$(echo "$RESULT2" | grep -o '"success"[[:space:]]*:[[:space:]]*true' | wc -l)
if [ $SUCCESS2 -gt 0 ]; then
    echo ""
    echo -e "${GREEN}✓ 测试通过：调用成功（预期成功）${NC}"
else
    echo ""
    echo -e "${RED}✗ 测试失败：应该成功但失败了${NC}"
fi
echo ""

# 测试场景 3: Lombok 版本
echo "========================================"
echo -e "${YELLOW}场景 3: Lombok 版本（最佳实践）${NC}"
echo "========================================"
echo -e "${BLUE}接口: GET http://localhost:8082/test/lombok/1${NC}"
echo ""
RESULT3=$(curl -s http://localhost:8082/test/lombok/1)
echo "$RESULT3" | python3 -m json.tool 2>/dev/null || echo "$RESULT3"
SUCCESS3=$(echo "$RESULT3" | grep -o '"success"[[:space:]]*:[[:space:]]*true' | wc -l)
if [ $SUCCESS3 -gt 0 ]; then
    echo ""
    echo -e "${GREEN}✓ 测试通过：调用成功（预期成功）${NC}"
else
    echo ""
    echo -e "${RED}✗ 测试失败：应该成功但失败了${NC}"
fi
echo ""

# 测试总结
echo "========================================"
echo -e "${BLUE}测试总结${NC}"
echo "========================================"
TOTAL=3
PASSED=0
[ $SUCCESS1 -gt 0 ] && PASSED=$((PASSED+1))
[ $SUCCESS2 -gt 0 ] && PASSED=$((PASSED+1))
[ $SUCCESS3 -gt 0 ] && PASSED=$((PASSED+1))

echo "总测试数: $TOTAL"
echo -e "通过数量: ${GREEN}$PASSED${NC}"
echo -e "失败数量: ${RED}$((TOTAL-PASSED))${NC}"
echo ""

if [ $PASSED -eq $TOTAL ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
else
    echo -e "${RED}✗ 部分测试失败，请检查日志${NC}"
fi
echo "========================================"
