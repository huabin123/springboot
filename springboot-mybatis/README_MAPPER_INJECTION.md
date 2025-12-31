# MyBatis Mapper 注入快速参考

## 🚀 快速开始

### 1. 确保依赖正确（pom.xml）
```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>2.1.2</version>
</dependency>
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
</dependency>
```

### 2. 配置启动类
```java
@SpringBootApplication
@MapperScan("com.huabin.mybatis.mapper")  // ← 关键配置
public class SpringbootMybatisApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringbootMybatisApplication.class, args);
    }
}
```

### 3. 配置 application.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.huabin.mybatis.entity
  configuration:
    map-underscore-to-camel-case: true
```

### 4. 创建 Mapper 接口
```java
package com.huabin.mybatis.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper  // 可选，如果使用了@MapperScan
public interface UserMapper {
    User selectById(Long id);
}
```

### 5. 在 Service 中注入
```java
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;  // ← 自动注入
    
    public User getUser(Long id) {
        return userMapper.selectById(id);
    }
}
```

## ⚠️ 常见错误

### 错误1：Field xxxMapper required a bean that could not be found
**原因**：Mapper未被扫描  
**解决**：添加 `@MapperScan("com.huabin.mybatis.mapper")` 到启动类

### 错误2：Invalid bound statement (not found)
**原因**：Mapper XML未加载  
**解决**：检查 `mybatis.mapper-locations` 配置

### 错误3：Failed to configure a DataSource
**原因**：数据源配置缺失  
**解决**：在 application.yml 中配置数据源

## 🔍 排查步骤

1. **检查启动类** - 是否有 `@MapperScan`
2. **检查配置文件** - application.yml 是否存在且配置正确
3. **检查包路径** - Mapper接口是否在扫描路径下
4. **检查XML文件** - namespace 是否与接口全限定名一致
5. **运行测试** - 执行 `MapperInjectionTest` 验证配置

## 📝 测试验证

运行测试类验证配置：
```bash
mvn test -Dtest=MapperInjectionTest
```

## 📚 详细文档

- 完整排查指南：[MAPPER_INJECTION_TROUBLESHOOTING.md](./MAPPER_INJECTION_TROUBLESHOOTING.md)
- 示例代码：
  - Mapper: `src/main/java/com/huabin/mybatis/mapper/ComprehensiveInfoMapper.java`
  - Service: `src/main/java/com/huabin/mybatis/service/ComprehensiveInfoService.java`
  - Controller: `src/main/java/com/huabin/mybatis/controller/ComprehensiveInfoController.java`

## 🎯 最佳实践

1. ✅ 使用 `@MapperScan` 统一扫描，不要在每个Mapper上加 `@Mapper`
2. ✅ 使用构造器注入代替字段注入（更易测试）
3. ✅ 开发环境开启 SQL 日志（`logging.level.com.huabin.mybatis.mapper: DEBUG`）
4. ✅ 为每个 Mapper 编写单元测试
5. ✅ 使用 `type-aliases-package` 简化 XML 配置

## 🔧 调试技巧

### 查看已注册的 Mapper Bean
```java
@Autowired
private ApplicationContext context;

@Test
public void listMappers() {
    String[] names = context.getBeanNamesForType(Object.class);
    for (String name : names) {
        if (name.contains("Mapper")) {
            System.out.println(name);
        }
    }
}
```

### 开启 MyBatis 调试日志
```yaml
logging:
  level:
    org.mybatis: DEBUG
    com.huabin.mybatis.mapper: DEBUG
```

### 验证数据源
```java
@Autowired
private DataSource dataSource;

@Test
public void testDataSource() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
        System.out.println("连接成功: " + conn.getMetaData().getURL());
    }
}
```

## 📞 需要帮助？

如果以上方法都无法解决问题：
1. 查看完整的启动日志
2. 检查是否有异常堆栈信息
3. 参考 `MAPPER_INJECTION_TROUBLESHOOTING.md` 详细排查
4. 运行 `MapperInjectionTest` 定位具体问题
