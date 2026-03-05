# Maven私服上传权限配置指南

## 概述

Maven上传权限由私服（如Nexus、Artifactory）控制。本指南介绍如何在不同的Maven仓库管理器中配置上传权限。

---

## 一、Sonatype Nexus Repository Manager

### Nexus 3.x 配置

#### 1. 登录Nexus管理界面

访问: `http://your-nexus-server:8081`

默认管理员账号:
- 用户名: `admin`
- 密码: 首次安装在 `$NEXUS_DATA/admin.password` 文件中

#### 2. 创建部署用户

**路径**: `Security` → `Users` → `Create local user`

```
User ID: deployer
First name: Maven
Last name: Deployer
Email: deployer@example.com
Status: Active
Password: <设置强密码>
```

#### 3. 配置角色权限

**方式一：使用内置角色（快速）**

编辑用户 → `Roles` 标签 → 添加以下角色:
- `nx-admin` - 完全管理权限（不推荐用于部署）
- `nx-deploy` - 部署权限（推荐）
- `nx-anonymous` - 匿名访问权限

**方式二：创建自定义角色（推荐）**

**路径**: `Security` → `Roles` → `Create role`

```yaml
角色ID: maven-deployer
角色名称: Maven Deployer
描述: 允许上传JAR包到Maven仓库

权限 (Privileges):
  # 查看仓库
  - nx-repository-view-maven2-*-browse
  - nx-repository-view-maven2-*-read
  
  # 上传权限
  - nx-repository-view-maven2-*-add
  - nx-repository-view-maven2-*-edit
  
  # 针对特定仓库（更安全）
  - nx-repository-view-maven2-maven-releases-*
  - nx-repository-view-maven2-maven-snapshots-*
```

#### 4. 分配角色给用户

**路径**: `Security` → `Users` → 选择用户 → `Roles` 标签

将 `maven-deployer` 角色添加到 `deployer` 用户。

#### 5. 配置仓库部署策略

**路径**: `Repository` → `Repositories` → 选择仓库 → `Settings`

**关键配置项:**

```yaml
# Release仓库配置
Name: maven-releases
Format: maven2
Type: hosted
Version policy: Release
Layout policy: Strict
Deployment policy: Allow redeploy  # 或 Disable redeploy（生产环境推荐）

# Snapshot仓库配置
Name: maven-snapshots
Format: maven2
Type: hosted
Version policy: Snapshot
Layout policy: Strict
Deployment policy: Allow redeploy
```

**Deployment policy说明:**
- `Allow redeploy`: 允许覆盖已存在的版本
- `Disable redeploy`: 禁止覆盖（推荐用于Release仓库）
- `Read-only`: 只读，禁止上传

#### 6. 启用匿名访问（可选）

**路径**: `Security` → `Anonymous Access`

- ✅ 勾选 `Allow anonymous users to access the server`
- 分配角色: `nx-anonymous`

**注意**: 匿名访问只能下载，不能上传。

---

### Nexus 2.x 配置

#### 1. 登录管理界面

访问: `http://your-nexus-server:8081/nexus`

默认账号: `admin` / `admin123`

#### 2. 创建部署用户

**路径**: `Security` → `Users` → `Add` → `Nexus User`

```
User ID: deployer
Email: deployer@example.com
Status: Active
Password: <设置密码>
```

#### 3. 分配角色

在用户配置中，添加以下角色:
- `Repo: All Maven Repositories (Full Control)` - 所有仓库完全控制
- `Deployment` - 部署权限

或创建自定义角色:

**路径**: `Security` → `Roles` → `Add` → `Nexus Role`

```
Role ID: maven-deployer
Name: Maven Deployer
Privileges:
  - All M2 Repositories - (create)
  - All M2 Repositories - (update)
  - All M2 Repositories - (read)
```

#### 4. 配置仓库部署策略

**路径**: `Repositories` → 选择仓库 → `Configuration`

```
Deployment Policy: Allow Redeploy
```

---

## 二、JFrog Artifactory

### Artifactory 配置

#### 1. 登录管理界面

访问: `http://your-artifactory:8081/artifactory`

默认账号: `admin` / `password`

#### 2. 创建部署用户

**路径**: `Administration` → `Security` → `Users` → `New User`

```
Username: deployer
Email: deployer@example.com
Password: <设置密码>
Admin: ❌ (不勾选)
```

#### 3. 配置权限

**路径**: `Administration` → `Security` → `Permissions` → `New Permission`

```yaml
Permission Name: maven-deploy-permission

Repositories:
  - 选择目标仓库（如 libs-release-local, libs-snapshot-local）

Users/Groups:
  Users: deployer
  
Permissions:
  ✅ Read
  ✅ Annotate
  ✅ Deploy/Cache
  ✅ Delete/Overwrite (可选)
  ❌ Manage (不推荐)
```

#### 4. 仓库配置

**路径**: `Administration` → `Repositories` → `Local` → 选择仓库

```yaml
Repository Key: libs-release-local
Package Type: Maven
Repository Layout: maven-2-default

Advanced:
  Suppress POM Consistency Checks: ❌
  Handle Releases: ✅
  Handle Snapshots: ❌
```

---

## 三、Apache Archiva

### Archiva 配置

#### 1. 登录管理界面

访问: `http://your-archiva:8080/archiva`

默认账号: `admin` / `admin1`

#### 2. 创建用户

**路径**: `Manage` → `Users` → `Create New User`

```
Username: deployer
Full Name: Maven Deployer
Email: deployer@example.com
Password: <设置密码>
```

#### 3. 分配角色

**路径**: `Manage` → `User Roles` → 选择用户

勾选以下角色:
- `Repository Manager - <repository-id>`
- `Repository Observer - <repository-id>`

---

## 四、通用权限验证方法

### 1. 使用curl测试上传权限

```bash
# 测试认证是否成功
curl -u deployer:password \
  http://your-nexus:8081/repository/maven-releases/

# 测试上传权限（PUT请求）
curl -u deployer:password \
  -X PUT \
  --upload-file test.jar \
  http://your-nexus:8081/repository/maven-releases/com/test/test/1.0.0/test-1.0.0.jar
```

**预期结果:**
- ✅ 200/201: 上传成功
- ❌ 401: 认证失败（用户名密码错误）
- ❌ 403: 权限不足（用户没有上传权限）
- ❌ 405: 方法不允许（仓库不支持PUT，可能是只读仓库）

### 2. 使用Maven测试

```bash
# 测试部署
mvn deploy:deploy-file \
  -DgroupId=com.test \
  -DartifactId=test \
  -Dversion=1.0.0 \
  -Dpackaging=jar \
  -Dfile=test.jar \
  -DrepositoryId=releases \
  -Durl=http://your-nexus:8081/repository/maven-releases/ \
  -X  # 开启调试模式
```

---

## 五、常见权限问题排查

### 问题1: 401 Unauthorized

**原因:**
- 用户名或密码错误
- settings.xml中的认证配置错误
- server id不匹配

**解决方案:**

```bash
# 1. 验证用户名密码
curl -u username:password http://your-nexus:8081/

# 2. 检查settings.xml配置
cat ~/.m2/settings.xml

# 3. 确保server id匹配
# pom.xml中的 <repository><id>releases</id>
# settings.xml中的 <server><id>releases</id>
```

### 问题2: 403 Forbidden

**原因:**
- 用户没有部署权限
- 仓库设置为只读
- IP白名单限制

**解决方案:**

```bash
# Nexus 3: 检查用户角色
Security → Users → 选择用户 → Roles
# 确保有 nx-deploy 或自定义部署角色

# 检查仓库配置
Repository → Repositories → 选择仓库
# Deployment policy: 不能是 Read-only
```

### 问题3: 405 Method Not Allowed

**原因:**
- 仓库类型错误（proxy或group类型不支持上传）
- 仓库URL错误
- 仓库设置为只读

**解决方案:**

```bash
# 1. 确认仓库类型为 hosted
Repository → Repositories → 查看 Type 列

# 2. 确认URL正确
# ✅ 正确: http://nexus:8081/repository/maven-releases/
# ❌ 错误: http://nexus:8081/repository/maven-public/ (group类型)
# ❌ 错误: http://nexus:8081/repository/maven-central/ (proxy类型)

# 3. 检查Deployment policy
Deployment policy: Allow redeploy 或 Disable redeploy
# 不能是 Read-only
```

### 问题4: 版本冲突

**原因:**
- Release仓库禁止覆盖已存在版本
- 版本号与仓库策略不匹配

**解决方案:**

```bash
# 1. Release版本使用递增版本号
1.0.0 → 1.0.1 → 1.1.0

# 2. 开发版本使用SNAPSHOT
1.0.0-SNAPSHOT

# 3. 如需覆盖（不推荐）
# Nexus: Deployment policy → Allow redeploy
# Artifactory: 勾选 Delete/Overwrite 权限
```

---

## 六、安全最佳实践

### 1. 用户权限最小化原则

```yaml
# ❌ 不推荐：使用admin账号部署
<username>admin</username>

# ✅ 推荐：创建专用部署账号
<username>deployer</username>
```

### 2. 密码加密

```bash
# 生成主密码
mvn --encrypt-master-password yourMasterPassword

# 加密仓库密码
mvn --encrypt-password yourPassword

# 配置到settings.xml
<server>
  <id>releases</id>
  <username>deployer</username>
  <password>{COQLCE6DU6GtcS5P=}</password>
</server>
```

### 3. 使用Token认证（推荐）

**Nexus 3:**

```bash
# 生成User Token
Security → Users → 选择用户 → More → Access user token

# 在settings.xml中使用
<server>
  <id>releases</id>
  <username>token-username</username>
  <password>token-password</password>
</server>
```

**Artifactory:**

```bash
# 生成API Key
User Profile → Authentication Settings → Generate API Key

# 使用API Key
<server>
  <id>releases</id>
  <username>deployer</username>
  <password>AKCp5dK...</password>
</server>
```

### 4. 分离Release和Snapshot权限

```yaml
# 创建两个不同的用户
deployer-release:  # 只能上传到Release仓库
  - nx-repository-view-maven2-maven-releases-*

deployer-snapshot: # 只能上传到Snapshot仓库
  - nx-repository-view-maven2-maven-snapshots-*
```

### 5. 启用审计日志

**Nexus 3:**
```
Administration → System → Capabilities → Audit
```

**Artifactory:**
```
Administration → Artifactory → Security → General → Audit
```

---

## 七、快速配置检查清单

上传前请确认：

**服务端配置:**
- [ ] 用户已创建且状态为Active
- [ ] 用户具有部署权限（nx-deploy或自定义角色）
- [ ] 仓库类型为hosted（不是proxy或group）
- [ ] Deployment policy不是Read-only
- [ ] 版本策略匹配（Release/Snapshot）

**客户端配置:**
- [ ] settings.xml中配置了正确的用户名密码
- [ ] server id与repository id一致
- [ ] 仓库URL正确（指向hosted仓库）
- [ ] 网络可访问Maven私服

**权限验证:**
- [ ] curl测试认证成功（返回200）
- [ ] curl测试PUT请求成功（返回201）
- [ ] Maven部署测试成功

---

## 八、示例配置

### Nexus 3 完整配置示例

**1. 创建角色（通过REST API）**

```bash
curl -X POST \
  http://localhost:8081/service/rest/v1/security/roles \
  -H 'Content-Type: application/json' \
  -u admin:admin123 \
  -d '{
    "id": "maven-deployer",
    "name": "Maven Deployer",
    "description": "Deploy to Maven repositories",
    "privileges": [
      "nx-repository-view-maven2-*-browse",
      "nx-repository-view-maven2-*-read",
      "nx-repository-view-maven2-*-add",
      "nx-repository-view-maven2-*-edit"
    ]
  }'
```

**2. 创建用户（通过REST API）**

```bash
curl -X POST \
  http://localhost:8081/service/rest/v1/security/users \
  -H 'Content-Type: application/json' \
  -u admin:admin123 \
  -d '{
    "userId": "deployer",
    "firstName": "Maven",
    "lastName": "Deployer",
    "emailAddress": "deployer@example.com",
    "password": "DeployPassword123!",
    "status": "active",
    "roles": ["maven-deployer"]
  }'
```

### settings.xml配置

```xml
<settings>
    <servers>
        <server>
            <id>releases</id>
            <username>deployer</username>
            <password>DeployPassword123!</password>
        </server>
        <server>
            <id>snapshots</id>
            <username>deployer</username>
            <password>DeployPassword123!</password>
        </server>
    </servers>
</settings>
```

---

## 参考资料

- [Nexus 3 Security](https://help.sonatype.com/repomanager3/nexus-repository-administration/access-control)
- [Artifactory Permissions](https://www.jfrog.com/confluence/display/JFROG/Permissions)
- [Maven Settings Reference](https://maven.apache.org/settings.html)
- [Maven Deploy Plugin](https://maven.apache.org/plugins/maven-deploy-plugin/)
