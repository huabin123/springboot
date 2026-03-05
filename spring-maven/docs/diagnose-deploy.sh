#!/bin/bash

# Maven Deploy 问题诊断脚本
# 使用方法: ./diagnose-deploy.sh [项目目录]

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 获取项目目录
PROJECT_DIR=${1:-.}

if [ ! -d "$PROJECT_DIR" ]; then
    echo -e "${RED}错误: 目录不存在: $PROJECT_DIR${NC}"
    exit 1
fi

cd "$PROJECT_DIR" || exit 1

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔍 Maven Deploy 配置诊断${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "项目目录: $(pwd)"
echo ""

# 检查计数
ISSUE_COUNT=0
WARNING_COUNT=0

# 检查1: pom.xml 是否存在
echo -e "${CYAN}[检查 1/8]${NC} 检查 pom.xml 文件..."
if [ -f "pom.xml" ]; then
    echo -e "  ${GREEN}✓${NC} pom.xml 存在"
else
    echo -e "  ${RED}✗${NC} pom.xml 不存在"
    echo -e "  ${RED}提示:${NC} 请确保在Maven项目根目录下运行此脚本"
    ((ISSUE_COUNT++))
    exit 1
fi
echo ""

# 检查2: distributionManagement 配置
echo -e "${CYAN}[检查 2/8]${NC} 检查 distributionManagement 配置..."
if grep -q "<distributionManagement>" pom.xml; then
    echo -e "  ${GREEN}✓${NC} 找到 distributionManagement 配置"
    
    # 检查 repository
    if grep -A 5 "<distributionManagement>" pom.xml | grep -q "<repository>"; then
        echo -e "  ${GREEN}✓${NC} 配置了 repository（Release仓库）"
        
        # 提取并显示 repository 配置
        REPO_ID=$(grep -A 10 "<repository>" pom.xml | grep "<id>" | head -1 | sed 's/.*<id>\(.*\)<\/id>.*/\1/' | xargs)
        REPO_URL=$(grep -A 10 "<repository>" pom.xml | grep "<url>" | head -1 | sed 's/.*<url>\(.*\)<\/url>.*/\1/' | xargs)
        echo -e "    Repository ID: ${YELLOW}$REPO_ID${NC}"
        echo -e "    Repository URL: ${YELLOW}$REPO_URL${NC}"
    else
        echo -e "  ${YELLOW}⚠${NC} 未配置 repository（Release仓库）"
        ((WARNING_COUNT++))
    fi
    
    # 检查 snapshotRepository
    if grep -A 5 "<distributionManagement>" pom.xml | grep -q "<snapshotRepository>"; then
        echo -e "  ${GREEN}✓${NC} 配置了 snapshotRepository（Snapshot仓库）"
        
        # 提取并显示 snapshotRepository 配置
        SNAPSHOT_ID=$(grep -A 10 "<snapshotRepository>" pom.xml | grep "<id>" | head -1 | sed 's/.*<id>\(.*\)<\/id>.*/\1/' | xargs)
        SNAPSHOT_URL=$(grep -A 10 "<snapshotRepository>" pom.xml | grep "<url>" | head -1 | sed 's/.*<url>\(.*\)<\/url>.*/\1/' | xargs)
        echo -e "    Snapshot ID: ${YELLOW}$SNAPSHOT_ID${NC}"
        echo -e "    Snapshot URL: ${YELLOW}$SNAPSHOT_URL${NC}"
    else
        echo -e "  ${YELLOW}⚠${NC} 未配置 snapshotRepository（Snapshot仓库）"
        ((WARNING_COUNT++))
    fi
else
    echo -e "  ${RED}✗${NC} 未找到 distributionManagement 配置"
    echo -e "  ${RED}这是导致错误的主要原因！${NC}"
    echo ""
    echo -e "  ${YELLOW}解决方案:${NC}"
    echo -e "  在 pom.xml 中添加以下配置："
    echo ""
    echo -e "${YELLOW}<distributionManagement>"
    echo -e "    <repository>"
    echo -e "        <id>releases</id>"
    echo -e "        <url>http://your-nexus:8081/repository/maven-releases/</url>"
    echo -e "    </repository>"
    echo -e "    <snapshotRepository>"
    echo -e "        <id>snapshots</id>"
    echo -e "        <url>http://your-nexus:8081/repository/maven-snapshots/</url>"
    echo -e "    </snapshotRepository>"
    echo -e "</distributionManagement>${NC}"
    ((ISSUE_COUNT++))
fi
echo ""

# 检查3: 项目版本号
echo -e "${CYAN}[检查 3/8]${NC} 检查项目版本号..."
if grep -q "<version>" pom.xml; then
    VERSION=$(grep "<version>" pom.xml | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | xargs)
    echo -e "  ${GREEN}✓${NC} 项目版本: ${YELLOW}$VERSION${NC}"
    
    if [[ "$VERSION" == *"-SNAPSHOT"* ]]; then
        echo -e "  ${BLUE}ℹ${NC} 这是 SNAPSHOT 版本，将部署到 snapshotRepository"
        if [ -z "$SNAPSHOT_ID" ]; then
            echo -e "  ${RED}✗${NC} 但未配置 snapshotRepository！"
            ((ISSUE_COUNT++))
        fi
    else
        echo -e "  ${BLUE}ℹ${NC} 这是 Release 版本，将部署到 repository"
        if [ -z "$REPO_ID" ]; then
            echo -e "  ${RED}✗${NC} 但未配置 repository！"
            ((ISSUE_COUNT++))
        fi
    fi
else
    echo -e "  ${YELLOW}⚠${NC} 未找到版本号（可能继承自父POM）"
    ((WARNING_COUNT++))
fi
echo ""

# 检查4: 父POM配置
echo -e "${CYAN}[检查 4/8]${NC} 检查父POM配置..."
if grep -q "<parent>" pom.xml; then
    echo -e "  ${GREEN}✓${NC} 项目继承了父POM"
    
    PARENT_GROUP=$(grep -A 5 "<parent>" pom.xml | grep "<groupId>" | head -1 | sed 's/.*<groupId>\(.*\)<\/groupId>.*/\1/' | xargs)
    PARENT_ARTIFACT=$(grep -A 5 "<parent>" pom.xml | grep "<artifactId>" | head -1 | sed 's/.*<artifactId>\(.*\)<\/artifactId>.*/\1/' | xargs)
    PARENT_VERSION=$(grep -A 5 "<parent>" pom.xml | grep "<version>" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/' | xargs)
    
    echo -e "    Parent: ${YELLOW}$PARENT_GROUP:$PARENT_ARTIFACT:$PARENT_VERSION${NC}"
    
    # 检查 relativePath
    if grep -A 5 "<parent>" pom.xml | grep -q "<relativePath>"; then
        RELATIVE_PATH=$(grep -A 5 "<parent>" pom.xml | grep "<relativePath>" | sed 's/.*<relativePath>\(.*\)<\/relativePath>.*/\1/' | xargs)
        echo -e "    Relative Path: ${YELLOW}$RELATIVE_PATH${NC}"
        
        # 检查父POM文件是否存在
        if [ -f "$RELATIVE_PATH" ]; then
            echo -e "  ${GREEN}✓${NC} 父POM文件存在"
            
            # 检查父POM中的 distributionManagement
            if grep -q "<distributionManagement>" "$RELATIVE_PATH"; then
                echo -e "  ${GREEN}✓${NC} 父POM中配置了 distributionManagement"
            else
                echo -e "  ${YELLOW}⚠${NC} 父POM中未配置 distributionManagement"
            fi
        else
            echo -e "  ${RED}✗${NC} 父POM文件不存在: $RELATIVE_PATH"
            ((ISSUE_COUNT++))
        fi
    fi
else
    echo -e "  ${BLUE}ℹ${NC} 项目没有父POM（独立项目）"
fi
echo ""

# 检查5: settings.xml 配置
echo -e "${CYAN}[检查 5/8]${NC} 检查 settings.xml 配置..."
SETTINGS_FILE="$HOME/.m2/settings.xml"
if [ -f "$SETTINGS_FILE" ]; then
    echo -e "  ${GREEN}✓${NC} settings.xml 存在: $SETTINGS_FILE"
    
    # 检查是否配置了 servers
    if grep -q "<servers>" "$SETTINGS_FILE"; then
        echo -e "  ${GREEN}✓${NC} 配置了 servers 节点"
        
        # 检查是否有对应的 server 配置
        if [ -n "$REPO_ID" ]; then
            if grep -A 3 "<server>" "$SETTINGS_FILE" | grep -q "<id>$REPO_ID</id>"; then
                echo -e "  ${GREEN}✓${NC} 找到 repository 对应的 server 配置 (id: $REPO_ID)"
            else
                echo -e "  ${RED}✗${NC} 未找到 repository 对应的 server 配置 (id: $REPO_ID)"
                echo -e "  ${YELLOW}提示:${NC} 需要在 settings.xml 中添加认证信息"
                ((ISSUE_COUNT++))
            fi
        fi
        
        if [ -n "$SNAPSHOT_ID" ]; then
            if grep -A 3 "<server>" "$SETTINGS_FILE" | grep -q "<id>$SNAPSHOT_ID</id>"; then
                echo -e "  ${GREEN}✓${NC} 找到 snapshotRepository 对应的 server 配置 (id: $SNAPSHOT_ID)"
            else
                echo -e "  ${RED}✗${NC} 未找到 snapshotRepository 对应的 server 配置 (id: $SNAPSHOT_ID)"
                echo -e "  ${YELLOW}提示:${NC} 需要在 settings.xml 中添加认证信息"
                ((ISSUE_COUNT++))
            fi
        fi
    else
        echo -e "  ${RED}✗${NC} 未配置 servers 节点"
        echo -e "  ${YELLOW}提示:${NC} 需要在 settings.xml 中添加认证信息"
        ((ISSUE_COUNT++))
    fi
else
    echo -e "  ${YELLOW}⚠${NC} settings.xml 不存在: $SETTINGS_FILE"
    echo -e "  ${YELLOW}提示:${NC} 如果Maven私服需要认证，请创建此文件"
    ((WARNING_COUNT++))
fi
echo ""

# 检查6: Maven 是否安装
echo -e "${CYAN}[检查 6/8]${NC} 检查 Maven 安装..."
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -1)
    echo -e "  ${GREEN}✓${NC} Maven 已安装: ${YELLOW}$MVN_VERSION${NC}"
else
    echo -e "  ${RED}✗${NC} Maven 未安装或不在 PATH 中"
    ((ISSUE_COUNT++))
fi
echo ""

# 检查7: 有效POM配置
echo -e "${CYAN}[检查 7/8]${NC} 检查有效POM配置（合并后的配置）..."
if command -v mvn &> /dev/null; then
    echo -e "  ${BLUE}ℹ${NC} 正在生成有效POM..."
    
    EFFECTIVE_POM=$(mvn help:effective-pom -q 2>/dev/null)
    if [ $? -eq 0 ]; then
        if echo "$EFFECTIVE_POM" | grep -q "<distributionManagement>"; then
            echo -e "  ${GREEN}✓${NC} 有效POM中包含 distributionManagement 配置"
            
            # 提取有效配置
            echo ""
            echo -e "  ${CYAN}有效的 distributionManagement 配置:${NC}"
            echo "$EFFECTIVE_POM" | grep -A 20 "<distributionManagement>" | head -21 | sed 's/^/    /'
        else
            echo -e "  ${RED}✗${NC} 有效POM中不包含 distributionManagement 配置"
            echo -e "  ${RED}这是导致错误的根本原因！${NC}"
            ((ISSUE_COUNT++))
        fi
    else
        echo -e "  ${YELLOW}⚠${NC} 无法生成有效POM（可能是POM配置有误）"
        ((WARNING_COUNT++))
    fi
else
    echo -e "  ${YELLOW}⊘${NC} 跳过（Maven未安装）"
fi
echo ""

# 检查8: 网络连通性（如果有URL）
echo -e "${CYAN}[检查 8/8]${NC} 检查仓库网络连通性..."
if [ -n "$REPO_URL" ]; then
    echo -e "  ${BLUE}ℹ${NC} 测试 Release 仓库: $REPO_URL"
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$REPO_URL" 2>/dev/null)
    if [ $? -eq 0 ]; then
        if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 401 ]; then
            echo -e "  ${GREEN}✓${NC} 仓库可访问 (HTTP $HTTP_CODE)"
        else
            echo -e "  ${YELLOW}⚠${NC} 仓库返回异常状态码: HTTP $HTTP_CODE"
            ((WARNING_COUNT++))
        fi
    else
        echo -e "  ${RED}✗${NC} 无法连接到仓库"
        ((ISSUE_COUNT++))
    fi
fi

if [ -n "$SNAPSHOT_URL" ]; then
    echo -e "  ${BLUE}ℹ${NC} 测试 Snapshot 仓库: $SNAPSHOT_URL"
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$SNAPSHOT_URL" 2>/dev/null)
    if [ $? -eq 0 ]; then
        if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 401 ]; then
            echo -e "  ${GREEN}✓${NC} 仓库可访问 (HTTP $HTTP_CODE)"
        else
            echo -e "  ${YELLOW}⚠${NC} 仓库返回异常状态码: HTTP $HTTP_CODE"
            ((WARNING_COUNT++))
        fi
    else
        echo -e "  ${RED}✗${NC} 无法连接到仓库"
        ((ISSUE_COUNT++))
    fi
fi

if [ -z "$REPO_URL" ] && [ -z "$SNAPSHOT_URL" ]; then
    echo -e "  ${YELLOW}⊘${NC} 跳过（未配置仓库URL）"
fi
echo ""

# 输出诊断结果
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 诊断结果${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  ${RED}严重问题:${NC} $ISSUE_COUNT"
echo -e "  ${YELLOW}警告:${NC} $WARNING_COUNT"
echo ""

if [ $ISSUE_COUNT -eq 0 ]; then
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}✅ 配置检查通过！${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "可以尝试执行部署命令:"
    echo -e "  ${CYAN}mvn clean deploy${NC}"
    echo ""
    if [ $WARNING_COUNT -gt 0 ]; then
        echo -e "${YELLOW}注意: 有 $WARNING_COUNT 个警告，建议检查后再部署${NC}"
        echo ""
    fi
else
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${RED}❌ 发现 $ISSUE_COUNT 个严重问题！${NC}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "请根据上述检查结果修复问题，然后重新运行此脚本。"
    echo ""
    echo "常见解决方案:"
    echo "  1. 在 pom.xml 中添加 distributionManagement 配置"
    echo "  2. 在 ~/.m2/settings.xml 中添加 server 认证信息"
    echo "  3. 确保 server id 与 repository id 一致"
    echo ""
    echo "详细文档:"
    echo "  • docs/DEPLOY_ERROR_SOLUTIONS.md - Deploy错误解决方案"
    echo "  • docs/MAVEN_DEPLOY_GUIDE.md - Maven部署完整指南"
    echo ""
fi
