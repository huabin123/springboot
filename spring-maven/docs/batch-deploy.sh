#!/bin/bash

# Maven批量上传JAR包到私服脚本
# 使用方法: ./batch-deploy.sh <jar包目录>

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Maven仓库配置
REPOSITORY_ID="releases"  # 对应settings.xml中的server id
REPOSITORY_URL="http://your-maven-server:8081/repository/maven-releases/"

# 检查参数
if [ $# -eq 0 ]; then
    echo -e "${RED}错误: 请提供JAR包所在目录${NC}"
    echo "使用方法: $0 <jar包目录>"
    exit 1
fi

JAR_DIR=$1

# 检查目录是否存在
if [ ! -d "$JAR_DIR" ]; then
    echo -e "${RED}错误: 目录不存在: $JAR_DIR${NC}"
    exit 1
fi

echo -e "${GREEN}开始批量上传JAR包...${NC}"
echo "目录: $JAR_DIR"
echo "仓库: $REPOSITORY_URL"
echo "----------------------------------------"

# 统计变量
SUCCESS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0

# 遍历目录下的所有JAR文件
find "$JAR_DIR" -name "*.jar" | while read jar_file; do
    echo -e "\n${YELLOW}处理: $(basename $jar_file)${NC}"
    
    # 提取文件名（不含扩展名）
    filename=$(basename "$jar_file" .jar)
    
    # 尝试从文件名解析 groupId, artifactId, version
    # 常见格式: artifactId-version.jar 或 groupId-artifactId-version.jar
    
    # 检查是否有对应的POM文件
    pom_file="${jar_file%.jar}.pom"
    
    if [ -f "$pom_file" ]; then
        echo "找到POM文件: $(basename $pom_file)"
        # 使用maven-deploy-plugin上传（带POM）
        mvn deploy:deploy-file \
            -DgroupId=com.example \
            -DartifactId=$filename \
            -Dversion=1.0.0 \
            -Dpackaging=jar \
            -Dfile="$jar_file" \
            -DpomFile="$pom_file" \
            -DrepositoryId=$REPOSITORY_ID \
            -Durl=$REPOSITORY_URL
    else
        echo -e "${YELLOW}警告: 未找到POM文件，使用默认配置上传${NC}"
        # 需要手动指定groupId, artifactId, version
        # 这里使用默认值，你需要根据实际情况修改
        mvn deploy:deploy-file \
            -DgroupId=com.example \
            -DartifactId=$filename \
            -Dversion=1.0.0 \
            -Dpackaging=jar \
            -Dfile="$jar_file" \
            -DgeneratePom=true \
            -DrepositoryId=$REPOSITORY_ID \
            -Durl=$REPOSITORY_URL
    fi
    
    # 检查上传结果
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ 上传成功: $(basename $jar_file)${NC}"
        ((SUCCESS_COUNT++))
    else
        echo -e "${RED}✗ 上传失败: $(basename $jar_file)${NC}"
        ((FAIL_COUNT++))
    fi
done

# 输出统计结果
echo -e "\n========================================"
echo -e "${GREEN}上传完成！${NC}"
echo "成功: $SUCCESS_COUNT"
echo "失败: $FAIL_COUNT"
echo "跳过: $SKIP_COUNT"
echo "========================================"
