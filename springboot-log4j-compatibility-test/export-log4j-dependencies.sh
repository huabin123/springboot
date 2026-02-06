#!/bin/bash

# ============================================
# Log4j 依赖打包脚本
# 用途：将 log4j 升级所需的依赖打包，以便导入内网 Maven 仓库
# 作者：huabin
# 日期：2026-02-03
# ============================================

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
EXPORT_DIR="./log4j-dependencies-export"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
PACKAGE_NAME="log4j-dependencies-${TIMESTAMP}.zip"

# Log4j 相关依赖列表（只包含升级所需的核心依赖）
# 注意：这里只包含 Spring Boot 2.2.2.RELEASE 升级必需的3个核心依赖
declare -a DEPENDENCIES=(
    "org.apache.logging.log4j:log4j-api:2.25.3"
    "org.apache.logging.log4j:log4j-core:2.25.3"
    "org.apache.logging.log4j:log4j-slf4j-impl:2.25.3"
)

# 可选依赖（如果需要，可以取消注释）
# declare -a OPTIONAL_DEPENDENCIES=(
#     "org.apache.logging.log4j:log4j-jul:2.25.3"        # JUL 桥接
#     "org.apache.logging.log4j:log4j-jcl:2.25.3"        # Commons Logging 桥接
#     "org.apache.logging.log4j:log4j-web:2.25.3"        # Web 应用支持
#     "org.apache.logging.log4j:log4j-1.2-api:2.25.3"    # Log4j 1.x 兼容
# )

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Log4j 依赖打包工具${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 设置 Java 1.8 环境
echo -e "${YELLOW}[1/6] 设置 Java 环境...${NC}"
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
export PATH=$JAVA_HOME/bin:$PATH
echo -e "${GREEN}✓ Java 版本：${NC}"
java -version 2>&1 | head -n 1
echo ""

# 创建导出目录
echo -e "${YELLOW}[2/6] 创建导出目录...${NC}"
rm -rf "${EXPORT_DIR}"
mkdir -p "${EXPORT_DIR}/repository"
echo -e "${GREEN}✓ 导出目录：${EXPORT_DIR}${NC}"
echo ""

# 下载依赖到本地仓库
echo -e "${YELLOW}[3/6] 下载 Log4j 依赖...${NC}"
for dep in "${DEPENDENCIES[@]}"; do
    IFS=':' read -r groupId artifactId version <<< "$dep"
    echo -e "${BLUE}  下载：${groupId}:${artifactId}:${version}${NC}"
    
    mvn dependency:get \
        -DgroupId="${groupId}" \
        -DartifactId="${artifactId}" \
        -Dversion="${version}" \
        -Dtransitive=false \
        -DremoteRepositories=https://maven.aliyun.com/repository/public \
        > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  ✓ 下载成功${NC}"
    else
        echo -e "${RED}  ✗ 下载失败${NC}"
        exit 1
    fi
done
echo ""

# 从本地仓库复制依赖
echo -e "${YELLOW}[4/6] 复制依赖到导出目录...${NC}"

# 检测本地 Maven 仓库路径
if [ -d "/Users/huabin/lib/repository" ]; then
    LOCAL_REPO="/Users/huabin/lib/repository"
    echo -e "${GREEN}✓ 使用自定义仓库：${LOCAL_REPO}${NC}"
elif [ -d "${HOME}/.m2/repository" ]; then
    LOCAL_REPO="${HOME}/.m2/repository"
    echo -e "${GREEN}✓ 使用默认仓库：${LOCAL_REPO}${NC}"
else
    echo -e "${RED}✗ 找不到 Maven 本地仓库！${NC}"
    echo -e "${YELLOW}请设置环境变量 LOCAL_REPO 指向你的 Maven 仓库${NC}"
    exit 1
fi
echo ""

for dep in "${DEPENDENCIES[@]}"; do
    IFS=':' read -r groupId artifactId version <<< "$dep"
    
    # 转换 groupId 为路径（com.example -> com/example）
    groupPath=$(echo "${groupId}" | tr '.' '/')
    
    # 源路径
    sourcePath="${LOCAL_REPO}/${groupPath}/${artifactId}/${version}"
    
    # 目标路径
    targetPath="${EXPORT_DIR}/repository/${groupPath}/${artifactId}/${version}"
    
    if [ -d "${sourcePath}" ]; then
        echo -e "${BLUE}  复制：${groupId}:${artifactId}:${version}${NC}"
        mkdir -p "${targetPath}"
        cp -r "${sourcePath}"/* "${targetPath}/"
        echo -e "${GREEN}  ✓ 复制成功${NC}"
    else
        echo -e "${RED}  ✗ 源路径不存在：${sourcePath}${NC}"
    fi
done
echo ""

# 生成依赖清单
echo -e "${YELLOW}[5/6] 生成依赖清单...${NC}"
cat > "${EXPORT_DIR}/dependencies-list.txt" << EOF
# Log4j 2.25.3 依赖清单
# 生成时间：$(date '+%Y-%m-%d %H:%M:%S')
# 用途：Spring Boot 2.2.2.RELEASE 升级 Log4j 到 2.25.3

EOF

for dep in "${DEPENDENCIES[@]}"; do
    echo "${dep}" >> "${EXPORT_DIR}/dependencies-list.txt"
done

echo -e "${GREEN}✓ 依赖清单已生成：${EXPORT_DIR}/dependencies-list.txt${NC}"
echo ""

# 生成导入脚本
echo -e "${YELLOW}[5.5/6] 生成导入脚本...${NC}"
cat > "${EXPORT_DIR}/import-to-nexus.sh" << 'EOF'
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
EOF

chmod +x "${EXPORT_DIR}/import-to-nexus.sh"
echo -e "${GREEN}✓ 导入脚本已生成：${EXPORT_DIR}/import-to-nexus.sh${NC}"
echo ""

# 生成 Maven settings.xml（用于 Nexus 认证）
cat > "${EXPORT_DIR}/settings.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>nexus</id>
            <username>admin</username>
            <password>admin123</password>
        </server>
    </servers>
</settings>
EOF

echo -e "${GREEN}✓ Maven settings.xml 已生成${NC}"
echo ""

# 生成使用说明
cat > "${EXPORT_DIR}/README.md" << 'EOF'
# Log4j 依赖包使用说明

## 📦 包含内容

本压缩包包含以下内容：

1. **repository/** - Maven 仓库目录结构，包含所有 Log4j 2.25.3 依赖
2. **dependencies-list.txt** - 依赖清单
3. **import-to-nexus.sh** - 导入到 Nexus 的脚本
4. **settings.xml** - Maven 配置文件（用于 Nexus 认证）
5. **README.md** - 本说明文件

## 📋 依赖列表

- org.apache.logging.log4j:log4j-api:2.25.3
- org.apache.logging.log4j:log4j-core:2.25.3
- org.apache.logging.log4j:log4j-slf4j-impl:2.25.3
- org.apache.logging.log4j:log4j-jul:2.25.3
- org.apache.logging.log4j:log4j-jcl:2.25.3
- org.apache.logging.log4j:log4j-web:2.25.3
- org.apache.logging.log4j:log4j-1.2-api:2.25.3

## 🚀 使用方法

### 方式1：导入到 Nexus 仓库（推荐）

1. **修改配置**

编辑 `import-to-nexus.sh`，修改以下配置：

```bash
NEXUS_URL="http://your-nexus-server:8081/repository/maven-releases/"
NEXUS_USER="admin"
NEXUS_PASSWORD="admin123"
```

同时修改 `settings.xml` 中的用户名和密码。

2. **执行导入**

```bash
chmod +x import-to-nexus.sh
./import-to-nexus.sh
```

### 方式2：复制到本地 Maven 仓库

```bash
# 复制到本地仓库
cp -r repository/* ~/.m2/repository/
```

### 方式3：使用本地文件仓库

在项目的 `pom.xml` 中添加：

```xml
<repositories>
    <repository>
        <id>local-log4j</id>
        <url>file://${project.basedir}/log4j-dependencies-export/repository</url>
    </repository>
</repositories>
```

## ⚠️ 注意事项

1. **版本一致性**
   - 所有 log4j 相关依赖必须使用相同版本（2.25.3）
   - 避免版本冲突

2. **Spring Boot 兼容性**
   - 本依赖包适用于 Spring Boot 2.2.2.RELEASE
   - 其他版本请测试后使用

3. **SnakeYAML 版本**
   - Spring Boot 2.2.2 需要 SnakeYAML 1.25
   - 如果父 POM 使用了 2.0，需要在项目中覆盖

4. **排除 logback**
   - 使用 log4j2 时必须排除 Spring Boot 默认的 logback

## 📝 POM 配置示例

```xml
<dependencies>
    <!-- Spring Boot Web Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <exclusions>
            <exclusion>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-logging</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <!-- Spring Boot Log4j2 Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-log4j2</artifactId>
    </dependency>

    <!-- 强制使用 log4j-api 2.25.3 -->
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-api</artifactId>
        <version>2.25.3</version>
    </dependency>

    <!-- 强制使用 log4j-core 2.25.3 -->
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-core</artifactId>
        <version>2.25.3</version>
    </dependency>

    <!-- log4j-slf4j-impl -->
    <dependency>
        <groupId>org.apache.logging.log4j</groupId>
        <artifactId>log4j-slf4j-impl</artifactId>
        <version>2.25.3</version>
    </dependency>
</dependencies>
```

## 🔍 验证导入

导入完成后，可以通过以下命令验证：

```bash
# 查看依赖
mvn dependency:tree | grep log4j

# 预期输出
[INFO] +- org.apache.logging.log4j:log4j-api:jar:2.25.3:compile
[INFO] +- org.apache.logging.log4j:log4j-core:jar:2.25.3:compile
[INFO] +- org.apache.logging.log4j:log4j-slf4j-impl:jar:2.25.3:compile
```

## 📞 技术支持

如有问题，请联系：huabin

## 📅 生成时间

$(date '+%Y-%m-%d %H:%M:%S')
EOF

echo -e "${GREEN}✓ 使用说明已生成：${EXPORT_DIR}/README.md${NC}"
echo ""

# 打包
echo -e "${YELLOW}[6/6] 打包依赖...${NC}"

# 使用 zip 打包
zip -r "${PACKAGE_NAME}" "${EXPORT_DIR}" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 打包完成：${PACKAGE_NAME}${NC}"
else
    echo -e "${RED}✗ 打包失败${NC}"
    exit 1
fi
echo ""

# 显示统计信息
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}打包完成${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "${GREEN}导出目录：${NC}${EXPORT_DIR}"
echo -e "${GREEN}压缩包：${NC}${PACKAGE_NAME}"
echo -e "${GREEN}压缩包大小：${NC}$(du -h ${PACKAGE_NAME} | cut -f1)"
echo ""
echo -e "${YELLOW}包含的依赖：${NC}"
for dep in "${DEPENDENCIES[@]}"; do
    echo -e "  ${BLUE}✓${NC} ${dep}"
done
echo ""
echo -e "${YELLOW}下一步操作：${NC}"
echo -e "  1. 将 ${PACKAGE_NAME} 传输到内网环境"
echo -e "  2. 解压：unzip ${PACKAGE_NAME}"
echo -e "  3. 修改 import-to-nexus.sh 中的 Nexus 配置"
echo -e "  4. 执行导入：./import-to-nexus.sh"
echo ""
echo -e "${GREEN}✓ 所有操作完成！${NC}"
