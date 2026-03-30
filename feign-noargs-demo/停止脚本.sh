#!/bin/bash

# Feign 无参构造方法问题演示项目 - 停止脚本

echo "========================================"
echo "停止 Feign 演示项目服务"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 停止 Provider Service
if [ -f logs/provider.pid ]; then
    PROVIDER_PID=$(cat logs/provider.pid)
    echo -e "${YELLOW}停止 Provider Service (PID: $PROVIDER_PID)...${NC}"
    kill $PROVIDER_PID 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Provider Service 已停止${NC}"
        rm logs/provider.pid
    else
        echo -e "${RED}✗ Provider Service 停止失败或已停止${NC}"
    fi
else
    echo -e "${YELLOW}Provider Service 未运行${NC}"
fi
echo ""

# 停止 Consumer Service
if [ -f logs/consumer.pid ]; then
    CONSUMER_PID=$(cat logs/consumer.pid)
    echo -e "${YELLOW}停止 Consumer Service (PID: $CONSUMER_PID)...${NC}"
    kill $CONSUMER_PID 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Consumer Service 已停止${NC}"
        rm logs/consumer.pid
    else
        echo -e "${RED}✗ Consumer Service 停止失败或已停止${NC}"
    fi
else
    echo -e "${YELLOW}Consumer Service 未运行${NC}"
fi
echo ""

# 检查并清理残留进程
echo -e "${YELLOW}检查残留进程...${NC}"

# 查找并杀死占用 8081 端口的进程
if lsof -Pi :8081 -sTCP:LISTEN -t >/dev/null 2>&1; then
    PID=$(lsof -Pi :8081 -sTCP:LISTEN -t)
    echo -e "${YELLOW}发现端口 8081 被进程 $PID 占用，正在清理...${NC}"
    kill -9 $PID 2>/dev/null
    echo -e "${GREEN}✓ 端口 8081 已释放${NC}"
fi

# 查找并杀死占用 8082 端口的进程
if lsof -Pi :8082 -sTCP:LISTEN -t >/dev/null 2>&1; then
    PID=$(lsof -Pi :8082 -sTCP:LISTEN -t)
    echo -e "${YELLOW}发现端口 8082 被进程 $PID 占用，正在清理...${NC}"
    kill -9 $PID 2>/dev/null
    echo -e "${GREEN}✓ 端口 8082 已释放${NC}"
fi

echo ""
echo "========================================"
echo -e "${GREEN}✓ 所有服务已停止${NC}"
echo "========================================"
