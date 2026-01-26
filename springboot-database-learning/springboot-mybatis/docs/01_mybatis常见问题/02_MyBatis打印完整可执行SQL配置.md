# MyBatis 打印完整可执行 SQL 配置指南

## 问题描述

MyBatis 默认日志输出的是预编译 SQL 和参数分离的格式：

```log
2026-01-21 18:48:27.606 [http-nio-8080-exec-2] DEBUG c.h.m.m.ComprehensiveInfoMapper.selectByPrimaryKey - ==>  Preparing: select prod_code, prod_name, data_date, prod_cls from comprehensive_info where prod_code = ? 
2026-01-21 18:48:27.620 [http-nio-8080-exec-2] DEBUG c.h.m.m.ComprehensiveInfoMapper.selectByPrimaryKey - ==> Parameters: PROD001(String)
```

**期望输出：** 完整的可执行 SQL，可以直接复制到数据库客户端执行

```sql
select prod_code, prod_name, data_date, prod_cls from comprehensive_info where prod_code = 'PROD001'
```

---

## 解决方案

### 方案一：使用 p6spy（推荐 ⭐）

**优点：**
- 输出完整可执行 SQL
- 支持多数据源
- 性能统计功能
- 配置简单

#### 1. 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>
```

#### 2. 修改数据源配置

修改 `application.yml`：

```yaml
spring:
  datasource:
    # 修改驱动类
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    # URL 前缀改为 jdbc:p6spy:mysql
    url: jdbc:p6spy:mysql://127.0.0.1:3306/springboot_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: DjeEZw2S2xS7vCq
```

#### 3. 创建 p6spy 配置文件

在 `src/main/resources` 目录下创建 `spy.properties`：

```properties
#################################################################
# P6Spy 配置文件
# 用于打印完整的可执行 SQL
#################################################################

# 指定应用的日志拦截模块，默认为com.p6spy.engine.spy.P6SpyFactory
modulelist=com.p6spy.engine.spy.P6SpyFactory,com.p6spy.engine.logging.P6LogFactory,com.p6spy.engine.outage.P6OutageFactory

# 真实JDBC driver，多个用逗号分隔
driverlist=com.mysql.cj.jdbc.Driver

# 使用日志系统记录sql
appender=com.p6spy.engine.spy.appender.Slf4JLogger

# 配置记录Log例外，可去掉的结果集有error, info, batch, debug, statement,
# commit, rollback, result, resultset.
excludecategories=info,debug,result,commit,resultset

# 日期格式
dateformat=yyyy-MM-dd HH:mm:ss

# 实际驱动可多个，用逗号分隔
#driverlist=com.mysql.cj.jdbc.Driver

# 是否开启慢SQL记录
outagedetection=true

# 慢SQL记录标准，单位秒
outagedetectioninterval=2

# 执行时间设置，单位毫秒
executionThreshold=0

# 自定义日志打印格式
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat

# 自定义日志打印内容
customLogMessageFormat=%(currentTime) | 耗时: %(executionTime)ms | 连接信息: %(category)-%(connectionId) | 执行SQL: %(sql)

# 是否显示类型
#显示指定过滤 Log 时排队的分类列表，取值: error, info, batch, debug, statement, commit, rollback, result and resultset are valid values
# (默认 info,debug,result,resultset,batch)
#excludecategories=info,debug,result,resultset

# 过滤 Log 时所包含的分类列表
# (默认 无)
#includecategories=

# 是否过滤二进制字段
# (default is false)
filter=false

# 是否包含二进制字段
# (default is false)
#include=

# 是否过滤包含二进制字段的SQL
# (default is false)
#sqlexpression=

# 设置 P6Spy driver 代理
#useprefix=false

# 打印堆栈跟踪信息
#stacktrace=false

# 堆栈跟踪的类列表
#stacktraceclass=

# 监测属性配置文件是否进行重新加载
reloadproperties=false

# 属性配置文件重新加载的时间间隔，单位:秒
reloadpropertiesinterval=60

# 指定 Log 的 appender，取值：
#   com.p6spy.engine.spy.appender.Slf4JLogger
#   com.p6spy.engine.spy.appender.StdoutLogger
#   com.p6spy.engine.spy.appender.FileLogger
appender=com.p6spy.engine.spy.appender.Slf4JLogger

# 指定 Log 的文件名
#logfile=spy.log

# 指定是否每次是增加 Log，设置为 false 则每次都会先进行清空
#append=true

# 指定日志输出样式
# 默认为com.p6spy.engine.spy.appender.SingleLineFormat
# 单行输出 不格式化语句
#logMessageFormat=com.p6spy.engine.spy.appender.SingleLineFormat
# 使用自定义格式
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat

# 指定应用的日志拦截模块，默认为com.p6spy.engine.spy.P6SpyFactory
#modulelist=com.p6spy.engine.spy.P6SpyFactory,com.p6spy.engine.logging.P6LogFactory,com.p6spy.engine.outage.P6OutageFactory

# jmx
#jmx=true

# jmx 端口
#jmxPrefix=

# 显示查询的值
# (default is true)
#excludebinary=false
```

#### 4. 输出效果

```log
2026-01-21 18:48:27.606 | 耗时: 14ms | 连接信息: statement-1 | 执行SQL: select prod_code, prod_name, data_date, prod_cls from comprehensive_info where prod_code = 'PROD001'
```

---

### 方案二：使用 MyBatis-Plus（如果使用了 MyBatis-Plus）

#### 1. 添加依赖

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>
```

#### 2. 配置

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  # 开启 SQL 性能分析
  global-config:
    banner: false
```

#### 3. 添加 SQL 打印拦截器（可选）

```java
@Configuration
public class MybatisPlusConfig {
    
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // SQL 性能规范插件
        PerformanceInterceptor performanceInterceptor = new PerformanceInterceptor();
        performanceInterceptor.setFormat(true); // 格式化 SQL
        
        return interceptor;
    }
}
```

---

### 方案三：自定义 MyBatis 拦截器

适用于不想引入额外依赖的场景。

#### 1. 创建 SQL 拦截器

```java
package com.huabin.mybatis.config;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * MyBatis SQL 拦截器 - 打印完整可执行 SQL
 */
@Component
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
    @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SqlPrintInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(SqlPrintInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行原方法
            return invocation.proceed();
        } finally {
            long endTime = System.currentTimeMillis();
            long sqlCost = endTime - startTime;
            
            StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
            BoundSql boundSql = statementHandler.getBoundSql();
            
            // 获取完整 SQL
            String sql = getCompleteSql(boundSql);
            
            // 打印日志
            logger.info("\n======================  SQL  ======================");
            logger.info("执行SQL: {}", sql);
            logger.info("耗时: {}ms", sqlCost);
            logger.info("===================================================\n");
        }
    }

    /**
     * 获取完整的 SQL 语句
     */
    private String getCompleteSql(BoundSql boundSql) {
        String sql = boundSql.getSql();
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        
        // 替换空白字符
        sql = sql.replaceAll("[\\s]+", " ");
        
        if (parameterMappings.size() > 0 && parameterObject != null) {
            for (ParameterMapping parameterMapping : parameterMappings) {
                String propertyName = parameterMapping.getProperty();
                Object value = null;
                
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject != null) {
                    try {
                        value = getFieldValue(parameterObject, propertyName);
                    } catch (Exception e) {
                        value = parameterObject;
                    }
                }
                
                String paramValueStr = getParameterValue(value);
                sql = sql.replaceFirst("\\?", paramValueStr);
            }
        }
        
        return sql;
    }

    /**
     * 获取参数值的字符串表示
     */
    private String getParameterValue(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return "'" + obj + "'";
        } else if (obj instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return "'" + sdf.format(obj) + "'";
        } else {
            return obj.toString();
        }
    }

    /**
     * 通过反射获取字段值
     */
    private Object getFieldValue(Object obj, String fieldName) throws Exception {
        try {
            String methodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            return obj.getClass().getMethod(methodName).invoke(obj);
        } catch (Exception e) {
            return obj.getClass().getDeclaredField(fieldName).get(obj);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以从配置文件读取属性
    }
}
```

#### 2. 配置拦截器

在 `application.yml` 中：

```yaml
mybatis:
  configuration:
    # 关闭默认的 SQL 日志
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

#### 3. 输出效果

```log
======================  SQL  ======================
执行SQL: select prod_code, prod_name, data_date, prod_cls from comprehensive_info where prod_code = 'PROD001'
耗时: 14ms
===================================================
```

---

## 方案对比

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|----------|
| **p6spy** | 配置简单、功能强大、支持多数据源 | 需要额外依赖 | ⭐ 生产环境推荐 |
| **MyBatis-Plus** | 功能丰富、集成度高 | 需要使用 MyBatis-Plus | 已使用 MP 的项目 |
| **自定义拦截器** | 无额外依赖、可定制 | 代码量大、维护成本高 | 学习研究 |

---

## 性能影响说明

### 开发环境
- **建议开启**：方便调试和问题排查
- 性能影响可忽略

### 生产环境
- **建议关闭或仅记录慢 SQL**
- 可通过 `executionThreshold` 设置阈值
- 避免大量日志影响性能

### p6spy 生产环境配置示例

```properties
# 只记录慢 SQL（超过 1 秒）
executionThreshold=1000

# 关闭普通 SQL 日志
excludecategories=info,debug,result,commit,resultset,batch

# 只开启慢 SQL 检测
outagedetection=true
outagedetectioninterval=1
```

---

## 常见问题

### Q1: p6spy 影响性能吗？

**A:** 开发环境影响可忽略，生产环境建议：
- 只记录慢 SQL
- 设置合理的阈值
- 定期清理日志文件

### Q2: 如何在不同环境使用不同配置？

**A:** 使用 Spring Profile：

```yaml
# application-dev.yml（开发环境）
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://...

# application-prod.yml（生产环境）
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://...
```

### Q3: 打印的 SQL 中文乱码？

**A:** 检查以下配置：
1. 数据库连接 URL 中添加 `characterEncoding=utf8`
2. `spy.properties` 中设置 `dateformat` 使用 UTF-8
3. IDE 控制台编码设置为 UTF-8

### Q4: 如何只打印特定 Mapper 的 SQL？

**A:** 在 `spy.properties` 中配置过滤器：

```properties
# 自定义过滤器
filter=true
# 包含的包路径
include=com.huabin.mybatis.mapper.ComprehensiveInfoMapper
```

---

## 完整示例项目配置

### pom.xml

```xml
<dependency>
    <groupId>p6spy</groupId>
    <artifactId>p6spy</artifactId>
    <version>3.9.1</version>
</dependency>
```

### application.yml

```yaml
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://127.0.0.1:3306/springboot_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
    username: root
    password: DjeEZw2S2xS7vCq

mybatis:
  configuration:
    # 可以关闭 MyBatis 默认日志
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
```

### spy.properties（简化版）

```properties
modulelist=com.p6spy.engine.spy.P6SpyFactory,com.p6spy.engine.logging.P6LogFactory
driverlist=com.mysql.cj.jdbc.Driver
appender=com.p6spy.engine.spy.appender.Slf4JLogger
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=%(currentTime) | 耗时: %(executionTime)ms | SQL: %(sql)
excludecategories=info,debug,result,commit,resultset
```

---

## 总结

推荐使用 **p6spy** 方案：
1. ✅ 配置简单（3 步完成）
2. ✅ 输出格式友好
3. ✅ 性能统计功能
4. ✅ 支持多数据源
5. ✅ 生产环境可控

快速开始：
```bash
# 1. 添加依赖到 pom.xml
# 2. 修改 driver 和 url
# 3. 创建 spy.properties
# 4. 重启应用
```
