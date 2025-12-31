# Spring Boot + MyBatis 多数据源项目

## 📖 项目简介

这是一个基于 Spring Boot 2.x 和 MyBatis 的多数据源示例项目，展示了如何在一个应用中同时连接和操作多个 MySQL 数据库。

### 主要特性

- ✅ **多数据源配置**：支持同时连接两个 MySQL 数据库
- ✅ **独立事务管理**：每个数据源拥有独立的事务管理器
- ✅ **代码自动生成**：集成 MyBatis Generator，支持自动生成实体类、Mapper 和 XML
- ✅ **完整示例代码**：包含 Entity、Mapper、Service、Controller 完整示例
- ✅ **异步任务处理**：批量创建产品异步任务，支持状态跟踪和日志记录
- ✅ **线程池管理**：合理配置线程池，支持高并发异步任务
- ✅ **详细文档**：提供配置说明、使用指南和 API 测试示例
- ✅ **最佳实践**：遵循 Spring Boot 和 MyBatis 最佳实践

### 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 1.8 | Java 开发工具包 |
| Spring Boot | 2.x | Spring Boot 框架 |
| MyBatis | 3.x | 持久层框架 |
| MyBatis Spring Boot Starter | 2.1.2 | MyBatis 与 Spring Boot 集成 |
| MySQL | 8.0+ | 关系型数据库 |
| HikariCP | 3.x | 数据库连接池 |
| MyBatis Generator | 1.4.0 | 代码生成器 |
| Maven | 3.x | 项目构建工具 |

---

## 🚀 快速开始

### 1. 环境准备

**必需环境：**
- JDK 1.8+
- Maven 3.x
- MySQL 8.0+

**创建数据库：**

```sql
-- 创建数据库
CREATE DATABASE IF NOT EXISTS springboot_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS springboot_db2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户并授权
CREATE USER 'springboot'@'localhost' IDENTIFIED BY 'Huabin123$';
GRANT ALL PRIVILEGES ON springboot_db.* TO 'springboot'@'localhost';
GRANT ALL PRIVILEGES ON springboot_db2.* TO 'springboot'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 克隆项目

```bash
git clone <repository-url>
cd springboot-mybatis-multi-ds
```

### 3. 配置数据源

编辑 `src/main/resources/application.yml`，修改数据库连接信息（如果需要）：

```yaml
spring:
  datasource:
    primary:
      jdbc-url: jdbc:mysql://localhost:3306/springboot_db?...
      username: springboot
      password: Huabin123$
    
    secondary:
      jdbc-url: jdbc:mysql://localhost:3306/springboot_db2?...
      username: springboot
      password: Huabin123$
```

### 4. 创建数据表

执行 SQL 脚本创建测试表（参考 [03-使用示例.md](./03-使用示例.md)）

### 5. 启动应用

```bash
# 使用 Maven 启动
mvn spring-boot:run

# 或者打包后启动
mvn clean package
java -jar target/springboot-mybatis-multi-ds-1.0-SNAPSHOT.jar
```

### 6. 测试接口

```bash
# 测试主数据源（用户服务）
curl http://localhost:8080/api/users/health

# 测试从数据源（产品服务）
curl http://localhost:8080/api/products/health
```

---

## 📁 项目结构

```
springboot-mybatis-multi-ds/
├── src/
│   ├── main/
│   │   ├── java/com/huabin/multids/
│   │   │   ├── config/                      # 配置类
│   │   │   │   ├── PrimaryDataSourceConfig.java    # 主数据源配置
│   │   │   │   ├── SecondaryDataSourceConfig.java  # 从数据源配置
│   │   │   │   └── ThreadPoolConfig.java            # 线程池配置
│   │   │   ├── db1/                         # 主数据源相关
│   │   │   │   ├── entity/                  # 实体类
│   │   │   │   │   └── User.java
│   │   │   │   └── mapper/                  # Mapper接口
│   │   │   │       └── UserMapper.java
│   │   │   ├── db2/                         # 从数据源相关
│   │   │   │   ├── entity/                  # 实体类
│   │   │   │   │   ├── Product.java
│   │   │   │   │   └── ProductCreateLog.java       # 产品创建日志
│   │   │   │   └── mapper/                  # Mapper接口
│   │   │   │       ├── ProductMapper.java
│   │   │   │       └── ProductCreateLogMapper.java
│   │   │   ├── dto/                         # 数据传输对象
│   │   │   │   ├── ProductCreateRequest.java
│   │   │   │   └── BatchCreateRequest.java
│   │   │   ├── enums/                       # 枚举类
│   │   │   │   └── ProductCreateStatus.java
│   │   │   ├── task/                        # 异步任务
│   │   │   │   └── ProductCreateTask.java
│   │   │   ├── service/                     # 业务逻辑层
│   │   │   │   ├── UserService.java
│   │   │   │   ├── ProductService.java
│   │   │   │   └── ProductBatchCreateService.java   # 批量创建服务
│   │   │   ├── controller/                  # 控制器层
│   │   │   │   ├── UserController.java
│   │   │   │   ├── ProductController.java
│   │   │   │   └── ProductBatchController.java      # 批量创建控制器
│   │   │   └── MultiDataSourceApplication.java  # 启动类
│   │   └── resources/
│   │       ├── mapper/                      # Mapper XML文件
│   │       │   ├── db1/                     # 主数据源XML
│   │       │   │   └── UserMapper.xml
│   │       │   └── db2/                     # 从数据源XML
│   │       │       ├── ProductMapper.xml
│   │       │       └── ProductCreateLogMapper.xml
│   │       ├── generator/                   # 代码生成器配置
│   │       │   ├── generatorConfig-db1.xml  # 主数据源生成配置
│   │       │   └── generatorConfig-db2.xml  # 从数据源生成配置
│   │       └── application.yml              # 应用配置文件
│   └── test/                                # 测试代码
├── sql/                                     # SQL脚本
│   └── product_create_log.sql               # 产品创建日志表
├── pom.xml                                  # Maven配置文件
├── 01-多数据源配置说明.md                    # 配置说明文档
├── 02-代码生成器使用指南.md                  # 代码生成器文档
├── 03-使用示例.md                           # 使用示例文档
├── 04-批量创建产品异步任务使用指南.md         # 异步任务使用指南
└── README.md                                # 项目说明文档
```

---

## 🔧 核心配置

### 数据源配置

项目配置了两个独立的数据源：

| 数据源 | 数据库 | 标识 | Mapper包 | XML路径 |
|--------|--------|------|----------|---------|
| 主数据源 | springboot_db | primary | com.huabin.multids.db1.mapper | mapper/db1/*.xml |
| 从数据源 | springboot_db2 | secondary | com.huabin.multids.db2.mapper | mapper/db2/*.xml |

### 关键配置类

1. **PrimaryDataSourceConfig** - 主数据源配置
   - 使用 `@Primary` 注解标记为主数据源
   - 配置 DataSource、SqlSessionFactory、TransactionManager
   - 扫描 `com.huabin.multids.db1.mapper` 包

2. **SecondaryDataSourceConfig** - 从数据源配置
   - 不使用 `@Primary` 注解
   - 配置独立的 DataSource、SqlSessionFactory、TransactionManager
   - 扫描 `com.huabin.multids.db2.mapper` 包

3. **MultiDataSourceApplication** - 启动类
   - 排除 Spring Boot 的数据源自动配置
   - 不在启动类上使用 `@MapperScan`

4. **ThreadPoolConfig** - 线程池配置
   - 配置产品创建任务线程池
   - 配置通用异步任务线程池
   - 合理设置核心线程数、最大线程数、队列容量

---

## 📚 文档导航

### 01-多数据源配置说明.md

详细介绍多数据源的配置方法，包括：
- 配置文件详解
- 数据源配置类说明
- Bean 命名规范
- 事务管理配置
- 常见问题解答

👉 [查看详细配置说明](./01-多数据源配置说明.md)

### 02-代码生成器使用指南.md

介绍 MyBatis Generator 的使用方法，包括：
- 快速开始指南
- 配置文件详解
- 生成策略说明
- 高级配置技巧
- 常见问题解答

👉 [查看代码生成器指南](./02-代码生成器使用指南.md)

### 03-使用示例.md

提供完整的使用示例，包括：
- 项目启动步骤
- API 接口测试
- 代码使用示例
- 单元测试示例
- 性能测试示例

👉 [查看使用示例](./03-使用示例.md)

### 04-批量创建产品异步任务使用指南.md

提供批量创建产品异步任务的完整指南，包括：
- 功能概述和架构设计
- 快速开始和API接口文档
- 线程池配置和事务管理
- 使用示例和常见问题
- 性能优化建议

👉 [查看异步任务使用指南](./04-批量创建产品异步任务使用指南.md)

---

## 🎯 核心功能

### 1. 多数据源支持

```java
// 主数据源 - 自动使用 springboot_db
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;  // 自动注入主数据源的Mapper
    
    @Transactional  // 默认使用主数据源事务
    public void createUser(User user) {
        userMapper.insert(user);
    }
}

// 从数据源 - 自动使用 springboot_db2
@Service
public class ProductService {
    @Autowired
    private ProductMapper productMapper;  // 自动注入从数据源的Mapper
    
    @Transactional(transactionManager = "secondaryTransactionManager")  // 指定从数据源事务
    public void createProduct(Product product) {
        productMapper.insert(product);
    }
}
```

### 2. 代码自动生成

```bash
# 生成主数据源代码
mvn mybatis-generator:generate -Dmybatis.generator.configurationFile=src/main/resources/generator/generatorConfig-db1.xml

# 生成从数据源代码
mvn mybatis-generator:generate -Dmybatis.generator.configurationFile=src/main/resources/generator/generatorConfig-db2.xml
```

### 3. RESTful API

**用户管理 API（主数据源）：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/users | 查询所有用户 |
| GET | /api/users/{id} | 根据ID查询用户 |
| GET | /api/users/search | 条件查询用户 |
| POST | /api/users | 创建用户 |
| PUT | /api/users | 更新用户 |
| DELETE | /api/users/{id} | 删除用户 |

**产品管理 API（从数据源）：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/products | 查询所有产品 |
| GET | /api/products/{id} | 根据ID查询产品 |
| GET | /api/products/search | 条件查询产品 |
| POST | /api/products | 创建产品 |
| PUT | /api/products | 更新产品 |
| DELETE | /api/products/{id} | 删除产品 |

**产品批量创建 API（异步任务）：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/products/batch | 批量创建产品（异步） |
| GET | /api/products/batch/{batchNo} | 查询批次状态 |
| GET | /api/products/batch/creator/{creator} | 查询创建人的所有批次 |

### 4. 异步任务处理

```bash
# 批量创建产品（异步）
curl -X POST http://localhost:8080/api/products/batch \
  -H "Content-Type: application/json" \
  -d '{
    "creator": "zhangsan",
    "products": [
      {
        "productName": "iPhone 15",
        "productCode": "IP15001",
        "price": 5999.00,
        "stock": 100,
        "description": "最新款iPhone"
      }
    ]
  }'

# 查询批次状态
curl http://localhost:8080/api/products/batch/BATCH_20251229193000_000001
```

**特性：**
- ✅ 接口调用后立即返回批次号
- ✅ 任务在线程池中异步执行
- ✅ 支持状态跟踪（创建中、成功、失败）
- ✅ 完整的日志记录
- ✅ 独立事务管理

---

## ⚠️ 注意事项

### 1. 配置文件

- ✅ 多数据源必须使用 `jdbc-url` 而不是 `url`
- ✅ 每个数据源需要独立的连接池配置
- ✅ 启动类必须排除 `DataSourceAutoConfiguration`

### 2. 事务管理

- ✅ 主数据源可以省略 `transactionManager` 参数
- ✅ 从数据源必须明确指定 `transactionManager = "secondaryTransactionManager"`
- ❌ 跨数据源操作无法使用统一事务

### 3. Mapper 扫描

- ✅ 在数据源配置类中使用 `@MapperScan`
- ❌ 不要在启动类上使用 `@MapperScan`
- ✅ 不同数据源的 Mapper 必须在不同的包下

### 4. 代码生成

- ✅ 首次生成后，备份 XML 文件
- ❌ 不要覆盖包含自定义 SQL 的 XML 文件
- ✅ 实体类和 Mapper 接口可以重新生成

---

## 🔍 常见问题

### Q1: 启动时报错 "Failed to configure a DataSource"

**A:** 检查以下几点：
1. 配置文件中是否使用了 `jdbc-url` 而不是 `url`
2. 启动类是否排除了 `DataSourceAutoConfiguration`
3. 数据库连接信息是否正确

### Q2: Mapper 注入失败

**A:** 检查以下几点：
1. `@MapperScan` 的包路径是否正确
2. Mapper 接口是否在正确的包下
3. 是否在启动类上错误地使用了 `@MapperScan`

### Q3: 从数据源事务不生效

**A:** 检查以下几点：
1. 是否明确指定了 `transactionManager = "secondaryTransactionManager"`
2. 事务管理器的 Bean 名称是否正确
3. 是否使用了正确的数据源

### Q4: 代码生成失败

**A:** 检查以下几点：
1. 数据库连接信息是否正确
2. 表名是否存在
3. MySQL 8.x 是否配置了 `nullCatalogMeansCurrent=true`

---

## 📊 性能优化建议

### 1. 连接池配置

```yaml
spring:
  datasource:
    primary:
      hikari:
        minimum-idle: 5          # 最小空闲连接数
        maximum-pool-size: 20    # 最大连接数
        connection-timeout: 30000 # 连接超时时间（毫秒）
        idle-timeout: 600000     # 空闲超时时间（毫秒）
        max-lifetime: 1800000    # 连接最大生命周期（毫秒）
```

### 2. MyBatis 配置

```yaml
mybatis:
  configuration:
    cache-enabled: true          # 开启二级缓存
    lazy-loading-enabled: false  # 关闭延迟加载（按需开启）
    default-executor-type: simple # 执行器类型
```

### 3. 批量操作

```java
// 使用批量插入而不是循环插入
@Transactional
public void batchInsert(List<User> users) {
    // 推荐：使用批量插入
    userMapper.batchInsert(users);
    
    // 不推荐：循环插入
    // for (User user : users) {
    //     userMapper.insert(user);
    // }
}
```

### 4. 线程池配置

```java
// 根据实际情况调整线程池参数
@Bean(name = "productCreateExecutor")
public Executor productCreateExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    
    // 核心线程数：CPU核心数 * 2
    int corePoolSize = Runtime.getRuntime().availableProcessors() * 2;
    executor.setCorePoolSize(corePoolSize);
    
    // 最大线程数：核心线程数 * 2
    executor.setMaxPoolSize(corePoolSize * 2);
    
    // 队列容量
    executor.setQueueCapacity(corePoolSize * 2 * 10);
    
    return executor;
}
```

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 开发规范

1. 代码风格遵循阿里巴巴 Java 开发手册
2. 提交信息使用中文，格式清晰
3. 添加必要的注释和文档
4. 确保所有测试通过

---

## 📄 许可证

本项目仅供学习和参考使用。

---

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发送邮件

---

## 🎉 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [MyBatis](https://mybatis.org/)
- [MyBatis Spring Boot Starter](https://github.com/mybatis/spring-boot-starter)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [MyBatis Generator](http://mybatis.org/generator/)

---

## 📝 更新日志

### v1.0.0 (2025-12-29)

- ✅ 初始版本发布
- ✅ 实现多数据源配置
- ✅ 集成 MyBatis Generator
- ✅ 提供完整示例代码
- ✅ 编写详细文档

---

**祝你使用愉快！如有问题，请查阅文档或提交 Issue。** 🚀
