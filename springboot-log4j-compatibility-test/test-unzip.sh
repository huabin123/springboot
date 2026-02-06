#!/bin/bash

# ============================================
# 测试解压 zip 包
# ============================================

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}测试解压 Log4j 依赖包${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 查找最新的 zip 文件
ZIP_FILE=$(ls -t log4j-dependencies-*.zip 2>/dev/null | head -1)

if [ -z "$ZIP_FILE" ]; then
    echo -e "${RED}✗ 找不到 zip 文件${NC}"
    echo -e "${YELLOW}请先运行 ./export-log4j-dependencies.sh${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/4] 找到 zip 文件...${NC}"
echo -e "${GREEN}✓ ${ZIP_FILE}${NC}"
echo -e "${GREEN}  大小：$(ls -lh ${ZIP_FILE} | awk '{print $5}')${NC}"
echo ""

# 创建测试目录
TEST_DIR="./test-unzip-$(date +%s)"
echo -e "${YELLOW}[2/4] 创建测试目录...${NC}"
mkdir -p "${TEST_DIR}"
echo -e "${GREEN}✓ ${TEST_DIR}${NC}"
echo ""

# 解压到测试目录
echo -e "${YELLOW}[3/4] 解压 zip 文件...${NC}"
cd "${TEST_DIR}"
unzip -q "../${ZIP_FILE}"
cd - > /dev/null
echo -e "${GREEN}✓ 解压成功${NC}"
echo ""

# 验证内容
echo -e "${YELLOW}[4/4] 验证解压内容...${NC}"

# 检查目录结构
if [ -d "${TEST_DIR}/log4j-dependencies-export" ]; then
    echo -e "${GREEN}✓ 目录结构正确${NC}"
else
    echo -e "${RED}✗ 目录结构错误${NC}"
    exit 1
fi

# 检查 jar 文件
jar_count=$(find "${TEST_DIR}/log4j-dependencies-export/repository" -name "*.jar" | wc -l | tr -d ' ')
echo -e "${GREEN}✓ jar 文件数：${jar_count}${NC}"

# 检查必需文件
files=(
    "dependencies-list.txt"
    "import-to-nexus.sh"
    "settings.xml"
    "README.md"
)

for file in "${files[@]}"; do
    if [ -f "${TEST_DIR}/log4j-dependencies-export/${file}" ]; then
        echo -e "${GREEN}✓ ${file}${NC}"
    else
        echo -e "${RED}✗ ${file} 不存在${NC}"
    fi
done

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}测试完成${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "${YELLOW}测试目录：${NC}${TEST_DIR}"
echo -e "${YELLOW}是否删除测试目录？(y/n)${NC}"
read -r answer

if [ "$answer" = "y" ] || [ "$answer" = "Y" ]; then
    rm -rf "${TEST_DIR}"
    echo -e "${GREEN}✓ 测试目录已删除${NC}"
else
    echo -e "${YELLOW}保留测试目录：${TEST_DIR}${NC}"
fi
