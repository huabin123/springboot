# Maven批量上传JAR包到私服指南

## 问题诊断

### 405错误原因分析

当遇到 `405 Method Not Allowed, Allow: GET, HEAD` 错误时，通常是以下原因：

1. **仓库URL错误** - 使用了只读仓库地址（如Maven中央仓库镜像）
2. **缺少认证** - 私服需要用户名密码但未配置
3. **权限不足** - 用户没有部署权限
4. **仓库类型错误** - 尝试向hosted类型的只读仓库上传
5. **缺少distributionManagement配置**

## 解决方案

### 方案一：配置Maven Deploy（推荐）

#### 1. 配置pom.xml

在项目的 `pom.xml` 中添加 `distributionManagement` 配置：

```xml
<distributionManagement>
    <repository>
        <id>releases</id>
        <name>Release Repository</name>
        <url>http://your-maven-server:8081/repository/maven-releases/</url>
    </repository>
    <snapshotRepository>
        <id>snapshots</id>
        <name>Snapshot Repository</name>
        <url>http://your-maven-server:8081/repository/maven-snapshots/</url>
    </snapshotRepository>
</distributionManagement>
```

**常见Maven私服URL格式：**
- Nexus 3: `http://host:8081/repository/maven-releases/`
- Nexus 2: `http://host:8081/nexus/content/repositories/releases/`
- Artifactory: `http://host:8081/artifactory/libs-release-local/`

#### 2. 配置settings.xml

在 `~/.m2/settings.xml` 中添加认证信息（**注意：server的id必须与pom.xml中的id一致**）：

```xml
<settings>
    <servers>
        <server>
            <id>releases</id>
            <username>admin</username>
            <password>admin123</password>
        </server>
        <server>
            <id>snapshots</id>
            <username>admin</username>
            <password>admin123</password>
        </server>
    </servers>
</settings>
```

#### 3. 执行部署命令

```bash
# 部署当前项目
mvn clean deploy

# 跳过测试部署
mvn clean deploy -DskipTests

# 只部署不重新构建
mvn deploy:deploy
```

### 方案二：使用deploy-file插件上传单个JAR

适用于上传第三方JAR包或本地JAR包：

```bash
mvn deploy:deploy-file \
  -DgroupId=com.example \
  -DartifactId=my-library \
  -Dversion=1.0.0 \
  -Dpackaging=jar \
  -Dfile=/path/to/your.jar \
  -DrepositoryId=releases \
  -Durl=http://your-maven-server:8081/repository/maven-releases/
```

**参数说明：**
- `groupId`: Maven坐标的groupId
- `artifactId`: Maven坐标的artifactId
- `version`: 版本号
- `packaging`: 打包类型（jar/war/pom等）
- `file`: JAR文件路径
- `repositoryId`: 对应settings.xml中的server id
- `url`: Maven仓库URL

**如果有POM文件：**
```bash
mvn deploy:deploy-file \
  -Dfile=/path/to/your.jar \
  -DpomFile=/path/to/your.pom \
  -DrepositoryId=releases \
  -Durl=http://your-maven-server:8081/repository/maven-releases/
```

**自动生成POM：**
```bash
mvn deploy:deploy-file \
  -DgroupId=com.example \
  -DartifactId=my-library \
  -Dversion=1.0.0 \
  -Dpackaging=jar \
  -Dfile=/path/to/your.jar \
  -DgeneratePom=true \
  -DrepositoryId=releases \
  -Durl=http://your-maven-server:8081/repository/maven-releases/
```

### 方案三：批量上传脚本

使用提供的 `batch-deploy.sh` 脚本批量上传JAR包：

#### 1. 修改脚本配置

编辑 `batch-deploy.sh`，修改以下配置：

```bash
REPOSITORY_ID="releases"  # 对应settings.xml中的server id
REPOSITORY_URL="http://your-maven-server:8081/repository/maven-releases/"
```

#### 2. 赋予执行权限

```bash
chmod +x batch-deploy.sh
```

#### 3. 执行批量上传

```bash
# 上传指定目录下的所有JAR包
./batch-deploy.sh /path/to/jar/directory

# 示例
./batch-deploy.sh ~/Downloads/libs
```

**脚本特性：**
- ✅ 自动遍历目录下所有JAR文件
- ✅ 自动检测并使用对应的POM文件
- ✅ 彩色输出，清晰显示上传结果
- ✅ 统计成功/失败数量

### 方案四：使用Nexus Web界面上传

如果使用Nexus作为Maven私服：

1. 登录Nexus Web界面
2. 点击左侧 `Upload` 按钮
3. 选择目标仓库（如 `maven-releases`）
4. 选择上传方式：
   - **Maven 2**: 需要填写GAV坐标
   - **Component**: 直接上传JAR和POM文件
5. 上传文件并提交

## 常见问题排查

### 1. 确认仓库URL是否正确

```bash
# 测试仓库是否可访问（应该返回200或401，不应该是405）
curl -I http://your-maven-server:8081/repository/maven-releases/
```

**错误示例：**
```bash
# ❌ 错误：使用了只读镜像地址
<url>https://maven.aliyun.com/repository/public</url>

# ✅ 正确：使用私服的写入地址
<url>http://your-nexus:8081/repository/maven-releases/</url>
```

### 2. 检查认证配置

确保 `settings.xml` 中的 `<server>` 的 `id` 与 `pom.xml` 中 `<repository>` 的 `id` 一致：

```xml
<!-- pom.xml -->
<repository>
    <id>releases</id>  <!-- 这个id -->
    ...
</repository>

<!-- settings.xml -->
<server>
    <id>releases</id>  <!-- 必须与上面一致 -->
    <username>admin</username>
    <password>admin123</password>
</server>
```

### 3. 检查用户权限

确保Maven私服中的用户具有部署权限：

- **Nexus**: 用户需要 `nx-repository-view-*-*-*` 和 `nx-repository-admin-*-*-*` 权限
- **Artifactory**: 用户需要 `Deploy/Cache` 权限

### 4. 检查仓库类型

- **hosted**: 可以上传
- **proxy**: 只读，不能上传
- **group**: 只读，不能上传

确保上传到 `hosted` 类型的仓库。

### 5. 版本策略检查

- **Release仓库**: 只接受非SNAPSHOT版本（如 `1.0.0`）
- **Snapshot仓库**: 只接受SNAPSHOT版本（如 `1.0.0-SNAPSHOT`）

确保版本号与仓库策略匹配。

### 6. 开启Maven调试模式

```bash
# 查看详细的上传日志
mvn deploy -X

# 或
mvn deploy:deploy-file -X -Dfile=your.jar ...
```

## 最佳实践

### 1. 使用加密密码

不要在 `settings.xml` 中明文存储密码，使用Maven密码加密：

```bash
# 生成主密码
mvn --encrypt-master-password <your-master-password>

# 加密仓库密码
mvn --encrypt-password <your-password>
```

### 2. 使用环境变量

```xml
<server>
    <id>releases</id>
    <username>${env.MAVEN_REPO_USERNAME}</username>
    <password>${env.MAVEN_REPO_PASSWORD}</password>
</server>
```

### 3. CI/CD集成

在Jenkins/GitLab CI中配置：

```yaml
# .gitlab-ci.yml
deploy:
  script:
    - mvn clean deploy -s settings.xml -DskipTests
  only:
    - master
```

### 4. 版本管理规范

- 开发版本使用 `SNAPSHOT` 后缀
- 发布版本使用语义化版本号（如 `1.0.0`）
- 使用 `maven-release-plugin` 管理版本发布

## 快速检查清单

上传前请确认：

- [ ] Maven私服地址正确（不是镜像地址）
- [ ] settings.xml中配置了正确的认证信息
- [ ] server id与repository id一致
- [ ] 用户具有部署权限
- [ ] 仓库类型为hosted
- [ ] 版本号与仓库策略匹配（Release/Snapshot）
- [ ] 网络可以访问Maven私服

## 示例配置文件

完整的配置示例已提供：
- `settings.xml.example` - Maven配置文件示例
- `batch-deploy.sh` - 批量上传脚本

## 参考资料

- [Maven Deploy Plugin](https://maven.apache.org/plugins/maven-deploy-plugin/)
- [Nexus Repository Manager](https://help.sonatype.com/repomanager3)
- [Maven Settings Reference](https://maven.apache.org/settings.html)
