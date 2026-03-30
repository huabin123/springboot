#!/bin/bash

# Feign 无参构造方法问题演示项目 - 启动脚本

echo "========================================"
echo "Feign 无参构造方法问题演示项目"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Java 环境
echo -e "${YELLOW}1. 检查 Java 环境...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}错误: 未找到 Java 命令，请先安装 JDK 1.8+${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo -e "${GREEN}✓ Java 版本: $JAVA_VERSION${NC}"
echo ""

# 检查 Maven 环境
echo -e "${YELLOW}2. 检查 Maven 环境...${NC}"
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}错误: 未找到 Maven 命令，请先安装 Maven 3.6+${NC}"
    exit 1
fi
MVN_VERSION=$(mvn -version | head -n 1)
echo -e "${GREEN}✓ $MVN_VERSION${NC}"
echo ""

# 检查端口占用
echo -e "${YELLOW}3. 检查端口占用...${NC}"
if lsof -Pi :8081 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${RED}错误: 端口 8081 已被占用，请先关闭占用该端口的进程${NC}"
    lsof -i :8081
    exit 1
fi
echo -e "${GREEN}✓ 端口 8081 可用${NC}"

if lsof -Pi :8082 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${RED}错误: 端口 8082 已被占用，请先关闭占用该端口的进程${NC}"
    lsof -i :8082
    exit 1
fi
echo -e "${GREEN}✓ 端口 8082 可用${NC}"
echo ""

# 编译项目
echo -e "${YELLOW}4. 编译项目...${NC}"

echo "  编译 Provider Service..."
cd provider-service
mvn clean package -DskipTests > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${RED}错误: Provider Service 编译失败${NC}"
    exit 1
fi
echo -e "${GREEN}  ✓ Provider Service 编译成功${NC}"
cd ..

echo "  编译 Consumer Service..."
cd consumer-service
mvn clean package -DskipTests > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo -e "${RED}错误: Consumer Service 编译失败${NC}"
    exit 1
fi
echo -e "${GREEN}  ✓ Consumer Service 编译成功${NC}"
cd ..
echo ""

# 创建日志目录
mkdir -p logs

# 启动 Provider Service
echo -e "${YELLOW}5. 启动 Provider Service (端口 8081)...${NC}"
cd provider-service
nohup mvn spring-boot:run > ../logs/provider.log 2>&1 &
PROVIDER_PID=$!
echo $PROVIDER_PID > ../logs/provider.pid
cd ..
echo -e "${GREEN}✓ Provider Service 已启动 (PID: $PROVIDER_PID)${NC}"
echo ""

# 等待 Provider Service 启动
echo -e "${YELLOW}6. 等待 Provider Service 启动...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8081/api/users/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Provider Service 启动成功！${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}错误: Provider Service 启动超时${NC}"
        echo "查看日志: tail -f logs/provider.log"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""

# 启动 Consumer Service
echo -e "${YELLOW}7. 启动 Consumer Service (端口 8082)...${NC}"
cd consumer-service
nohup mvn spring-boot:run > ../logs/consumer.log 2>&1 &
CONSUMER_PID=$!
echo $CONSUMER_PID > ../logs/consumer.pid
cd ..
echo -e "${GREEN}✓ Consumer Service 已启动 (PID: $CONSUMER_PID)${NC}"
echo ""

# 等待 Consumer Service 启动
echo -e "${YELLOW}8. 等待 Consumer Service 启动...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8082/test/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Consumer Service 启动成功！${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}错误: Consumer Service 启动超时${NC}"
        echo "查看日志: tail -f logs/consumer.log"
        exit 1
    fi
    echo -n "."
    sleep 1
done
echo ""

# 启动成功提示
echo "========================================"
echo -e "${GREEN}✓ 所有服务启动成功！${NC}"
echo "========================================"
echo ""
echo "服务信息："
echo "  Provider Service:  http://localhost:8081"
echo "  Consumer Service:  http://localhost:8082"
echo ""
echo "测试接口："
echo "  场景1 (问题复现): curl http://localhost:8082/test/problem/1"
echo "  场景2 (问题修复): curl http://localhost:8082/test/fixed/1"
echo "  场景3 (Lombok):   curl http://localhost:8082/test/lombok/1"
echo ""
echo "查看日志："
echo "  Provider: tail -f logs/provider.log"
echo "  Consumer: tail -f logs/consumer.log"
echo ""
echo "停止服务："
echo "  ./停止脚本.sh"
echo ""
echo "========================================"
