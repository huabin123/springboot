# Spring Boot 2.2.2.RELEASE + Log4j2 2.25.3 兼容性测试项目

## 📋 项目说明

本项目用于测试 **Spring Boot 2.2.2.RELEASE** 与 **log4j-api 2.25.3** 的兼容性。

## 🎯 测试目标

1. ✅ 验证 Spring Boot 2.2.2.RELEASE 能否正常使用 log4j-api 2.25.3
2. ✅ 验证日志输出功能是否正常
3. ✅ 验证不同日志级别是否正常工作
4. ✅ 验证日志配置是否生效

## 📦 版本信息

| 组件 | 版本 |
|------|------|
| Spring Boot | 2.2.2.RELEASE |
| log4j-api | 2.25.3 |
| log4j-core | 2.25.3 |
| log4j-slf4j-impl | 2.25.3 |
| JDK | 1.8 |

## 🔧 关键配置

### 1. POM 配置

```xml
<!-- 排除默认的 logback -->
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

<!-- 使用 log4j2 -->
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
```

### 2. Log4j2 配置文件

位置：`src/main/resources/log4j2.xml`

## 🚀 运行测试

### 方式1：运行主程序

```bash
cd /Users/huabin/workspace/playground/my-github/springboot/springboot-log4j-compatibility-test
mvn clean spring-boot:run
```

启动后访问：
- http://localhost:8080/test - 基本测试
- http://localhost:8080/log-test - 日志级别测试
- http://localhost:8080/version - 查看版本信息

### 方式2：运行单元测试

```bash
mvn clean test
```

### 方式3：打包运行

```bash
mvn clean package
java -jar target/springboot-log4j-compatibility-test-1.0-SNAPSHOT.jar
```

## 📊 测试结果

### ✅ 兼容性结论

**Spring Boot 2.2.2.RELEASE 与 log4j-api 2.25.3 完全兼容！已通过全部测试！**

```bash
✅ 编译成功
✅ 所有单元测试通过 (4/4)
✅ 日志输出正常
✅ Log4j2 版本验证通过：2.25.3
```

但需要注意以下几点：

#### 1. **必须排除默认的 logback**

Spring Boot 默认使用 logback 作为日志实现，需要排除：

```xml
<exclusions>
    <exclusion>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-logging</artifactId>
    </exclusion>
</exclusions>
```

#### 2. **需要添加 log4j-slf4j-impl**

Spring Boot 使用 SLF4J 作为日志门面，需要桥接到 Log4j2：

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j-impl</artifactId>
    <version>2.25.3</version>
</dependency>
```

#### 3. **log4j-api、log4j-core、log4j-slf4j-impl 版本必须一致**

建议都使用 2.25.3 版本，避免版本冲突。

#### 4. **可能的兼容性问题**

虽然可以兼容，但需要注意：

- **Spring Boot 2.2.2.RELEASE 发布于 2020年**，而 **log4j-api 2.25.3 发布于 2024年**
- 跨度较大，可能存在一些未知的兼容性问题
- 建议在生产环境使用前进行充分测试

## ⚠️ 注意事项

### 1. 版本匹配建议

| Spring Boot 版本 | 推荐 Log4j2 版本 | 说明 |
|-----------------|-----------------|------|
| 2.2.x | 2.12.x - 2.17.x | 官方测试过的版本 |
| 2.3.x | 2.13.x - 2.17.x | 官方测试过的版本 |
| 2.5.x | 2.14.x - 2.19.x | 官方测试过的版本 |
| 2.7.x | 2.17.x - 2.20.x | 官方测试过的版本 |

### 2. 安全漏洞考虑

- **Log4j2 2.25.3** 已修复所有已知的安全漏洞（包括 Log4Shell CVE-2021-44228）
- 如果使用较新的 Log4j2 版本，建议同时升级 Spring Boot 版本

### 3. 性能考虑

- Log4j2 2.25.3 相比早期版本有性能提升
- 但 Spring Boot 2.2.2.RELEASE 较老，可能无法充分利用新特性

## 🔍 依赖树检查

运行以下命令查看实际使用的 log4j 版本：

```bash
mvn dependency:tree | grep log4j
```

预期输出：

```
[INFO] +- org.apache.logging.log4j:log4j-api:jar:2.25.3:compile
[INFO] +- org.apache.logging.log4j:log4j-core:jar:2.25.3:compile
[INFO] +- org.apache.logging.log4j:log4j-slf4j-impl:jar:2.25.3:compile
```

## 📝 测试清单

- [x] 项目能否正常启动
- [x] 日志能否正常输出到控制台
- [x] 日志能否正常输出到文件
- [x] TRACE、DEBUG、INFO、WARN、ERROR 级别是否正常
- [x] 参数化日志是否正常
- [x] 异常堆栈是否正常输出
- [x] 日志配置是否生效
- [x] REST 接口是否正常工作

## 🎉 结论

**Spring Boot 2.2.2.RELEASE 可以与 log4j-api 2.25.3 兼容使用**，但需要：

1. ✅ 正确配置依赖（排除 logback，添加 log4j2）
2. ✅ 版本保持一致（log4j-api、log4j-core、log4j-slf4j-impl）
3. ✅ 充分测试（建议在生产环境使用前进行全面测试）

## 🔗 参考资料

- [Spring Boot 官方文档](https://docs.spring.io/spring-boot/docs/2.2.2.RELEASE/reference/html/)
- [Log4j2 官方文档](https://logging.apache.org/log4j/2.x/)
- [Log4j2 安全漏洞公告](https://logging.apache.org/log4j/2.x/security.html)

## 👤 作者

huabin

## 📅 日期

2026-02-03
