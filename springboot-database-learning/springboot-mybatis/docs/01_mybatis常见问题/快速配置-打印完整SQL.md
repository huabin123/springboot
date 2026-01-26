# 快速配置：打印完整可执行 SQL

## 3 步完成配置

### 步骤 1：添加 p6spy 依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>
```

### 步骤 2：修改数据源配置

修改 `application.yml`：

```yaml
spring:
  datasource:
    # 修改驱动类
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    # URL 前缀改为 jdbc:p6spy:mysql（在原有 jdbc:mysql 前加 p6spy:）
    url: jdbc:p6spy:mysql://127.0.0.1:3306/springboot_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: your_password
```

### 步骤 3：创建 spy.properties

在 `src/main/resources` 目录下创建 `spy.properties`：

```properties
# 指定应用的日志拦截模块
modulelist=com.p6spy.engine.spy.P6SpyFactory,com.p6spy.engine.logging.P6LogFactory,com.p6spy.engine.outage.P6OutageFactory

# 真实JDBC driver
driverlist=com.mysql.cj.jdbc.Driver

# 使用日志系统记录sql
appender=com.p6spy.engine.spy.appender.Slf4JLogger

# 配置记录Log例外
excludecategories=info,debug,result,commit,resultset

# 日期格式
dateformat=yyyy-MM-dd HH:mm:ss

# 是否开启慢SQL记录
outagedetection=true

# 慢SQL记录标准，单位秒
outagedetectioninterval=2

# 执行时间设置，单位毫秒
executionThreshold=0

# 自定义日志打印格式
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat

# 自定义日志打印内容
customLogMessageFormat=%(currentTime) | 耗时: %(executionTime)ms | 执行SQL: %(sql)
```

---

## 完成！重启应用

重启 Spring Boot 应用后，日志输出将变为：

**之前：**
```log
==>  Preparing: select prod_code, prod_name, data_date, prod_cls from comprehensive_info where prod_code = ?
==> Parameters: PROD001(String)
```

**现在：**
```log
2026-01-21 18:48:27.606 | 耗时: 14ms | 执行SQL: select prod_code, prod_name, data_date, prod_cls from comprehensive_info where prod_code = 'PROD001'
```

可以直接复制 SQL 到数据库客户端执行！

---

## 常用配置调整

### 只记录慢 SQL（生产环境推荐）

修改 `spy.properties`：

```properties
# 只记录超过 1 秒的 SQL
executionThreshold=1000
```

### 修改日志格式

```properties
# 简洁格式
customLogMessageFormat=%(executionTime)ms | %(sql)

# 详细格式
customLogMessageFormat=[%(currentTime)] [%(executionTime)ms] [%(category)] %(sql)
```

### 过滤特定表或操作

```properties
# 排除查询操作
excludecategories=statement

# 只记录更新操作
includecategories=commit
```

---

## 故障排查

### 问题 1：应用启动报错

**错误信息：**
```
Cannot load driver class: com.p6spy.engine.spy.P6SpyDriver
```

**解决方案：**
1. 检查 `pom.xml` 中是否添加了 p6spy 依赖
2. 执行 `mvn clean install` 重新构建
3. 刷新 IDE 的 Maven 依赖

### 问题 2：SQL 没有打印

**检查清单：**
1. ✅ `spy.properties` 文件是否在 `src/main/resources` 目录下
2. ✅ `application.yml` 中 driver 和 url 是否正确修改
3. ✅ 检查 `excludecategories` 配置是否过滤了 SQL
4. ✅ 查看日志级别是否正确

### 问题 3：SQL 打印了两次

**原因：** MyBatis 日志和 p6spy 同时开启

**解决方案：** 在 `application.yml` 中关闭 MyBatis 日志

```yaml
mybatis:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

---

## 更多配置

详细配置说明请参考：[02_MyBatis打印完整可执行SQL配置.md](./02_MyBatis打印完整可执行SQL配置.md)
