#!/bin/bash

# ============================================
# 导入依赖到内网 Nexus 仓库
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置（请根据实际情况修改）
NEXUS_URL="http://your-nexus-server:8081/repository/maven-releases/"
NEXUS_USER="admin"
NEXUS_PASSWORD="admin123"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}导入依赖到 Nexus 仓库${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 检查配置
if [ "${NEXUS_URL}" = "http://your-nexus-server:8081/repository/maven-releases/" ]; then
    echo -e "${RED}错误：请先修改脚本中的 Nexus 配置！${NC}"
    echo -e "${YELLOW}需要修改的配置：${NC}"
    echo -e "  - NEXUS_URL: Nexus 仓库地址"
    echo -e "  - NEXUS_USER: Nexus 用户名"
    echo -e "  - NEXUS_PASSWORD: Nexus 密码"
    exit 1
fi

echo -e "${YELLOW}Nexus 配置：${NC}"
echo -e "  URL: ${NEXUS_URL}"
echo -e "  User: ${NEXUS_USER}"
echo ""

# 读取依赖清单
if [ ! -f "dependencies-list.txt" ]; then
    echo -e "${RED}错误：找不到 dependencies-list.txt${NC}"
    exit 1
fi

# 统计
total=0
success=0
failed=0

# 遍历 repository 目录
echo -e "${YELLOW}开始导入依赖...${NC}"
echo ""

while IFS= read -r line; do
    # 跳过注释和空行
    [[ "$line" =~ ^#.*$ ]] && continue
    [[ -z "$line" ]] && continue
    
    IFS=':' read -r groupId artifactId version <<< "$line"
    groupPath="${groupId//./\/}"
    
    jarFile="repository/${groupPath}/${artifactId}/${version}/${artifactId}-${version}.jar"
    pomFile="repository/${groupPath}/${artifactId}/${version}/${artifactId}-${version}.pom"
    
    if [ -f "${jarFile}" ] && [ -f "${pomFile}" ]; then
        echo -e "${BLUE}导入：${groupId}:${artifactId}:${version}${NC}"
        
        # 使用 mvn deploy:deploy-file 上传
        mvn deploy:deploy-file \
            -DgroupId="${groupId}" \
            -DartifactId="${artifactId}" \
            -Dversion="${version}" \
            -Dpackaging=jar \
            -Dfile="${jarFile}" \
            -DpomFile="${pomFile}" \
            -Durl="${NEXUS_URL}" \
            -DrepositoryId=nexus \
            -s settings.xml \
            > /dev/null 2>&1
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ 导入成功${NC}"
            ((success++))
        else
            echo -e "${RED}✗ 导入失败${NC}"
            ((failed++))
        fi
        ((total++))
    else
        echo -e "${RED}✗ 文件不存在：${jarFile}${NC}"
        ((failed++))
        ((total++))
    fi
    echo ""
done < dependencies-list.txt

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}导入完成${NC}"
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}总计：${total}${NC}"
echo -e "${GREEN}成功：${success}${NC}"
echo -e "${RED}失败：${failed}${NC}"
