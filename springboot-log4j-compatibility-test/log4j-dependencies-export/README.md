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
