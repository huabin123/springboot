#!/bin/bash

# Maven仓库上传权限测试脚本
# 使用方法: ./test-maven-upload.sh

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置（请修改为你的实际配置）
NEXUS_URL="http://your-server:8081"
REPO_PATH="repository/maven-releases"
USERNAME="deployer"
PASSWORD="your-password"

# 打印配置信息
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}🔍 Maven仓库上传权限测试${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "配置信息:"
echo "  Nexus URL: $NEXUS_URL"
echo "  仓库路径: $REPO_PATH"
echo "  用户名: $USERNAME"
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# 测试计数
PASS_COUNT=0
FAIL_COUNT=0

# 测试1: 网络连通性
echo -e "${YELLOW}[测试 1/5]${NC} 检查网络连通性..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 $NEXUS_URL)
if [ $? -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} 网络连通 (HTTP $HTTP_CODE)"
    ((PASS_COUNT++))
else
    echo -e "  ${RED}✗${NC} 无法连接到 $NEXUS_URL"
    echo -e "  ${RED}提示:${NC} 请检查网络连接和Nexus服务是否启动"
    ((FAIL_COUNT++))
    exit 1
fi
echo ""

# 测试2: 仓库是否存在
echo -e "${YELLOW}[测试 2/5]${NC} 检查仓库是否存在..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" $NEXUS_URL/$REPO_PATH/)
if [ $HTTP_CODE -eq 200 ] || [ $HTTP_CODE -eq 401 ]; then
    echo -e "  ${GREEN}✓${NC} 仓库存在 (HTTP $HTTP_CODE)"
    ((PASS_COUNT++))
elif [ $HTTP_CODE -eq 404 ]; then
    echo -e "  ${RED}✗${NC} 仓库不存在 (HTTP 404)"
    echo -e "  ${RED}提示:${NC} 请检查仓库路径是否正确: $REPO_PATH"
    ((FAIL_COUNT++))
    exit 1
else
    echo -e "  ${YELLOW}⚠${NC} 未知状态 (HTTP $HTTP_CODE)"
fi
echo ""

# 测试3: 用户认证
echo -e "${YELLOW}[测试 3/5]${NC} 测试用户认证..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $USERNAME:$PASSWORD $NEXUS_URL/$REPO_PATH/)
if [ $HTTP_CODE -eq 200 ]; then
    echo -e "  ${GREEN}✓${NC} 认证成功 (HTTP 200)"
    ((PASS_COUNT++))
elif [ $HTTP_CODE -eq 401 ]; then
    echo -e "  ${RED}✗${NC} 认证失败 (HTTP 401)"
    echo -e "  ${RED}提示:${NC} 请检查用户名和密码是否正确"
    ((FAIL_COUNT++))
    exit 1
elif [ $HTTP_CODE -eq 403 ]; then
    echo -e "  ${RED}✗${NC} 权限不足 (HTTP 403)"
    echo -e "  ${RED}提示:${NC} 用户没有访问该仓库的权限"
    ((FAIL_COUNT++))
    exit 1
else
    echo -e "  ${YELLOW}⚠${NC} 未知状态 (HTTP $HTTP_CODE)"
fi
echo ""

# 测试4: 上传权限（PUT请求）
echo -e "${YELLOW}[测试 4/5]${NC} 测试上传权限..."
echo "test-content-$(date +%s)" > /tmp/maven-test-file.txt
TEST_PATH="com/test/permission-test/1.0.0/test-$(date +%s).txt"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $USERNAME:$PASSWORD \
    -X PUT --upload-file /tmp/maven-test-file.txt \
    $NEXUS_URL/$REPO_PATH/$TEST_PATH)

if [ $HTTP_CODE -eq 201 ] || [ $HTTP_CODE -eq 200 ]; then
    echo -e "  ${GREEN}✓${NC} 上传成功 (HTTP $HTTP_CODE)"
    ((PASS_COUNT++))
elif [ $HTTP_CODE -eq 403 ]; then
    echo -e "  ${RED}✗${NC} 上传被拒绝 (HTTP 403)"
    echo -e "  ${RED}提示:${NC} 用户没有部署权限，请添加 nx-deploy 角色"
    ((FAIL_COUNT++))
elif [ $HTTP_CODE -eq 405 ]; then
    echo -e "  ${RED}✗${NC} 方法不允许 (HTTP 405)"
    echo -e "  ${RED}提示:${NC} 仓库可能是只读的或类型错误（proxy/group）"
    echo -e "  ${RED}解决:${NC} 确保仓库类型为 hosted 且 Deployment policy 不是 Read-only"
    ((FAIL_COUNT++))
else
    echo -e "  ${RED}✗${NC} 上传失败 (HTTP $HTTP_CODE)"
    ((FAIL_COUNT++))
fi
rm -f /tmp/maven-test-file.txt
echo ""

# 测试5: 删除权限（可选）
echo -e "${YELLOW}[测试 5/5]${NC} 测试删除权限（清理测试文件）..."
if [ $HTTP_CODE -eq 201 ] || [ $HTTP_CODE -eq 200 ]; then
    DELETE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u $USERNAME:$PASSWORD \
        -X DELETE $NEXUS_URL/$REPO_PATH/$TEST_PATH)
    if [ $DELETE_CODE -eq 204 ] || [ $DELETE_CODE -eq 200 ]; then
        echo -e "  ${GREEN}✓${NC} 删除成功 (HTTP $DELETE_CODE)"
        ((PASS_COUNT++))
    else
        echo -e "  ${YELLOW}⚠${NC} 删除失败 (HTTP $DELETE_CODE) - 可能需要手动清理"
    fi
else
    echo -e "  ${YELLOW}⊘${NC} 跳过（上传未成功）"
fi
echo ""

# 输出测试结果
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${BLUE}📊 测试结果${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "  ${GREEN}通过:${NC} $PASS_COUNT"
echo -e "  ${RED}失败:${NC} $FAIL_COUNT"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}🎉 所有测试通过！${NC}"
    echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "✅ Maven仓库配置正确，可以开始上传JAR包了！"
    echo ""
    echo "下一步操作:"
    echo "  1. 配置 pom.xml 中的 distributionManagement"
    echo "  2. 配置 ~/.m2/settings.xml 中的认证信息"
    echo "  3. 执行: mvn clean deploy"
    echo ""
    exit 0
else
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${RED}❌ 测试失败！${NC}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "请根据上述错误提示修复问题，然后重新运行此脚本。"
    echo ""
    echo "常见问题排查:"
    echo "  • 401错误: 检查用户名密码是否正确"
    echo "  • 403错误: 检查用户是否有 nx-deploy 角色"
    echo "  • 405错误: 检查仓库类型是否为 hosted"
    echo "  • 404错误: 检查仓库路径是否正确"
    echo ""
    echo "详细文档: docs/MAVEN_PERMISSION_GUIDE.md"
    echo ""
    exit 1
fi
