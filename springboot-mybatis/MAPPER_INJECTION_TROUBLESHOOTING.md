# MyBatis Mapper 自动注入失败排查指南

## 常见原因及解决方案

### 1. 缺少 @Mapper 或 @MapperScan 注解

**问题现象：**
```
Field xxxMapper in xxxService required a bean of type 'xxx.mapper.XxxMapper' that could not be found.
```

**原因：**
- Mapper接口没有被Spring扫描到
- 没有标记为Spring Bean

**解决方案：**

方式一：在每个Mapper接口上添加 `@Mapper` 注解
```java
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    // ...
}
```

方式二：在启动类上使用 `@MapperScan` 扫描包（推荐）
```java
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.huabin.mybatis.mapper")  // 指定mapper包路径
public class SpringbootMybatisApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringbootMybatisApplication.class, args);
    }
}
```

---

### 2. Mapper XML 文件路径配置错误

**问题现象：**
```
Invalid bound statement (not found): com.huabin.mybatis.mapper.UserMapper.selectById
```

**原因：**
- Mapper XML文件没有被正确加载
- XML文件路径与配置不匹配
- namespace配置错误

**解决方案：**

在 `application.yml` 中配置：
```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml  # 指定XML文件位置
  type-aliases-package: com.huabin.mybatis.entity  # 实体类包路径
  configuration:
    map-underscore-to-camel-case: true  # 下划线转驼峰
```

检查XML文件：
- namespace必须是Mapper接口的全限定名
- 方法id必须与接口方法名一致

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.huabin.mybatis.mapper.UserMapper">
    <select id="selectById" resultType="com.huabin.mybatis.entity.User">
        SELECT * FROM user WHERE id = #{id}
    </select>
</mapper>
```

---

### 3. 包扫描路径不正确

**问题现象：**
- Mapper无法注入
- 启动时没有错误，但运行时报NoSuchBeanDefinitionException

**原因：**
- @MapperScan 指定的包路径不包含Mapper接口
- @ComponentScan 排除了Mapper所在的包

**解决方案：**
```java
// 确保包路径正确，可以使用通配符
@MapperScan({"com.huabin.mybatis.mapper", "com.huabin.*.mapper"})

// 或者扫描多个包
@MapperScan(basePackages = {
    "com.huabin.mybatis.mapper",
    "com.huabin.other.mapper"
})
```

---

### 4. 数据源配置缺失或错误

**问题现象：**
```
Failed to configure a DataSource: 'url' attribute is not specified
```

**原因：**
- application.yml 中没有配置数据源
- 数据源配置信息错误

**解决方案：**

在 `application.yml` 中配置数据源：
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/your_database?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

---

### 5. Maven 依赖缺失

**问题现象：**
- 编译错误
- 找不到MyBatis相关类

**原因：**
- pom.xml中缺少必要的依赖

**解决方案：**

确保 `pom.xml` 包含以下依赖：
```xml
<dependencies>
    <!-- MyBatis Spring Boot Starter -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>2.1.2</version>
    </dependency>
    
    <!-- MySQL驱动 -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
    </dependency>
</dependencies>
```

---

### 6. Mapper接口与XML文件不匹配

**问题现象：**
```
org.apache.ibatis.binding.BindingException: Invalid bound statement
```

**原因：**
- XML的namespace与Mapper接口全限定名不一致
- XML中的方法id与接口方法名不一致
- 参数类型或返回值类型不匹配

**解决方案：**

Mapper接口：
```java
package com.huabin.mybatis.mapper;

public interface UserMapper {
    User selectById(Long id);
}
```

对应的XML：
```xml
<mapper namespace="com.huabin.mybatis.mapper.UserMapper">
    <select id="selectById" parameterType="long" resultType="User">
        SELECT * FROM user WHERE id = #{id}
    </select>
</mapper>
```

---

### 7. 循环依赖问题

**问题现象：**
```
The dependencies of some of the beans in the application context form a cycle
```

**原因：**
- Service之间相互注入
- Mapper被多个Service循环引用

**解决方案：**
```java
// 使用 @Lazy 延迟加载
@Service
public class UserService {
    @Autowired
    @Lazy
    private OrderService orderService;
}
```

---

### 8. 多数据源配置冲突

**问题现象：**
- Mapper注入失败
- 找不到对应的SqlSessionFactory

**原因：**
- 配置了多数据源但没有正确指定Mapper使用哪个数据源

**解决方案：**
```java
@Configuration
@MapperScan(
    basePackages = "com.huabin.mybatis.mapper.db1",
    sqlSessionFactoryRef = "db1SqlSessionFactory"
)
public class DataSource1Config {
    // 配置第一个数据源
}

@Configuration
@MapperScan(
    basePackages = "com.huabin.mybatis.mapper.db2",
    sqlSessionFactoryRef = "db2SqlSessionFactory"
)
public class DataSource2Config {
    // 配置第二个数据源
}
```

---

## 排查步骤

### Step 1: 检查启动日志
查看是否有以下信息：
- Mapper接口是否被扫描到
- SqlSessionFactory是否创建成功
- 数据源是否初始化成功

### Step 2: 验证配置
```bash
# 检查配置文件是否存在
ls -la src/main/resources/application.yml

# 检查Mapper XML是否在正确位置
ls -la src/main/resources/mapper/

# 检查Mapper接口是否存在
ls -la src/main/java/com/huabin/mybatis/mapper/
```

### Step 3: 添加调试日志
在 `application.yml` 中添加：
```yaml
logging:
  level:
    com.huabin.mybatis.mapper: DEBUG  # 打印SQL日志
    org.mybatis: DEBUG  # MyBatis调试日志
    org.springframework.jdbc: DEBUG  # JDBC调试日志
```

### Step 4: 测试数据库连接
创建测试类验证数据源：
```java
@SpringBootTest
public class DataSourceTest {
    @Autowired
    private DataSource dataSource;
    
    @Test
    public void testConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        assertNotNull(connection);
        connection.close();
    }
}
```

### Step 5: 验证Mapper Bean
```java
@SpringBootTest
public class MapperTest {
    @Autowired
    private ApplicationContext context;
    
    @Test
    public void testMapperBean() {
        // 检查Mapper是否被注册为Bean
        UserMapper mapper = context.getBean(UserMapper.class);
        assertNotNull(mapper);
    }
}
```

---

## 最佳实践

1. **统一使用 @MapperScan**：在启动类上使用，避免在每个Mapper上重复添加@Mapper

2. **规范目录结构**：
   ```
   src/main/java/com/huabin/mybatis/
   ├── mapper/          # Mapper接口
   ├── entity/          # 实体类
   ├── service/         # 业务逻辑
   └── controller/      # 控制器
   
   src/main/resources/
   ├── mapper/          # Mapper XML文件
   └── application.yml  # 配置文件
   ```

3. **使用配置文件集中管理**：所有MyBatis配置放在application.yml中

4. **开启日志**：开发环境开启DEBUG日志，便于排查问题

5. **编写单元测试**：为每个Mapper编写测试用例，及时发现问题

---

## 快速检查清单

- [ ] Mapper接口是否添加了 @Mapper 或启动类添加了 @MapperScan
- [ ] application.yml 中是否配置了 mybatis.mapper-locations
- [ ] Mapper XML 的 namespace 是否正确
- [ ] 数据源配置是否正确
- [ ] pom.xml 中是否包含 mybatis-spring-boot-starter 依赖
- [ ] Mapper接口方法名与XML中的id是否一致
- [ ] 包扫描路径是否包含Mapper接口所在的包
- [ ] XML文件是否在resources目录下，能否被正确加载
