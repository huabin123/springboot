#!/bin/bash

# ============================================
# 验证导出的 Log4j 依赖
# ============================================

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

EXPORT_DIR="./log4j-dependencies-export"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}验证 Log4j 依赖导出${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 检查导出目录
if [ ! -d "${EXPORT_DIR}" ]; then
    echo -e "${RED}✗ 导出目录不存在：${EXPORT_DIR}${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/4] 检查目录结构...${NC}"
echo -e "${GREEN}✓ 导出目录存在${NC}"
echo ""

# 检查必需文件
echo -e "${YELLOW}[2/4] 检查必需文件...${NC}"
files=(
    "dependencies-list.txt"
    "import-to-nexus.sh"
    "settings.xml"
    "README.md"
)

for file in "${files[@]}"; do
    if [ -f "${EXPORT_DIR}/${file}" ]; then
        echo -e "${GREEN}✓ ${file}${NC}"
    else
        echo -e "${RED}✗ ${file} 不存在${NC}"
    fi
done
echo ""

# 检查 jar 文件
echo -e "${YELLOW}[3/4] 检查 jar 文件...${NC}"
jar_count=$(find "${EXPORT_DIR}/repository" -name "*.jar" | wc -l | tr -d ' ')
echo -e "${GREEN}✓ 找到 ${jar_count} 个 jar 文件${NC}"

find "${EXPORT_DIR}/repository" -name "*.jar" | while read jar; do
    size=$(ls -lh "$jar" | awk '{print $5}')
    name=$(basename "$jar")
    echo -e "  ${BLUE}${name}${NC} (${size})"
done
echo ""

# 检查 pom 文件
echo -e "${YELLOW}[4/4] 检查 pom 文件...${NC}"
pom_count=$(find "${EXPORT_DIR}/repository" -name "*.pom" | wc -l | tr -d ' ')
echo -e "${GREEN}✓ 找到 ${pom_count} 个 pom 文件${NC}"
echo ""

# 统计信息
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}统计信息${NC}"
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}导出目录：${NC}${EXPORT_DIR}"
echo -e "${GREEN}jar 文件数：${NC}${jar_count}"
echo -e "${GREEN}pom 文件数：${NC}${pom_count}"

if [ -f "log4j-dependencies-"*.tar.gz ]; then
    latest_tar=$(ls -t log4j-dependencies-*.tar.gz | head -1)
    tar_size=$(ls -lh "$latest_tar" | awk '{print $5}')
    echo -e "${GREEN}压缩包：${NC}${latest_tar} (${tar_size})"
fi

echo ""
echo -e "${GREEN}✓ 验证完成！${NC}"
