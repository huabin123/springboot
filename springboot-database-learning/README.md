# Spring Boot 数据库学习项目

> 整合所有数据库相关的学习模块，包括 MyBatis、MyBatis-Plus、分库分表、多数据源、事务管理等

## 📁 项目结构

```
springboot-database-learning/
├── base-mybatis-generator          # MyBatis 代码生成器
├── springboot-mybatis              # MyBatis 基础使用
├── springboot-mybatis-multi-ds     # MyBatis 多数据源
├── springboot-mybatis-plus         # MyBatis-Plus 增强工具
├── springboot-sharding-jdbc        # Sharding-JDBC 分库分表
└── springboot-transaction-learning # Spring 事务管理
```

---

## 🎯 学习目标

### 1. MyBatis 基础
- **base-mybatis-generator** - MyBatis 代码生成器的使用
- **springboot-mybatis** - MyBatis 与 Spring Boot 的整合
  - 基础 CRUD 操作
  - 动态 SQL
  - ResultMap 映射
  - 一对一、一对多关联查询

### 2. 多数据源
- **springboot-mybatis-multi-ds** - 多数据源配置和使用
  - 动态数据源切换
  - 读写分离
  - 多数据源事务管理

### 3. MyBatis-Plus
- **springboot-mybatis-plus** - MyBatis-Plus 增强工具
  - 通用 CRUD
  - 条件构造器
  - 分页插件
  - 代码生成器
  - 乐观锁、逻辑删除

### 4. 分库分表
- **springboot-sharding-jdbc** - Sharding-JDBC 分库分表
  - 水平分表
  - 水平分库
  - 读写分离
  - 分布式主键

### 5. 事务管理
- **springboot-transaction-learning** - Spring 事务管理
  - 编程式事务
  - 声明式事务
  - 事务传播行为
  - 事务隔离级别
  - 分布式事务

---

## 🚀 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+ / 8.0+

### 启动步骤

1. **准备数据库**
   ```sql
   CREATE DATABASE test_db DEFAULT CHARACTER SET utf8mb4;
   ```

2. **配置数据库连接**
   
   修改各模块的 `application.yml`：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/test_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
       username: root
       password: your_password
   ```

3. **启动应用**
   ```bash
   cd springboot-mybatis
   mvn spring-boot:run
   ```

---

## 📚 学习路径

### 🔰 初学者路径

1. **MyBatis 基础**
   - 学习 `springboot-mybatis` 模块
   - 掌握基本的 CRUD 操作
   - 理解 MyBatis 的工作原理

2. **代码生成器**
   - 学习 `base-mybatis-generator` 模块
   - 使用代码生成器快速生成 Mapper、Entity

3. **MyBatis-Plus**
   - 学习 `springboot-mybatis-plus` 模块
   - 体验 MyBatis-Plus 的便捷性

### 🚀 进阶路径

1. **多数据源**
   - 学习 `springboot-mybatis-multi-ds` 模块
   - 掌握动态数据源切换
   - 理解读写分离的实现

2. **事务管理**
   - 学习 `springboot-transaction-learning` 模块
   - 深入理解事务的传播行为
   - 掌握分布式事务的处理

3. **分库分表**
   - 学习 `springboot-sharding-jdbc` 模块
   - 理解分库分表的原理
   - 掌握 Sharding-JDBC 的使用

---

## 🔧 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.2.5.RELEASE | 基础框架 |
| MyBatis | 2.1.2 | ORM 框架 |
| MyBatis-Plus | 3.4.2 | MyBatis 增强工具 |
| Sharding-JDBC | 4.1.1 | 分库分表中间件 |
| Druid | 1.2.8 | 数据库连接池 |
| MySQL | 8.0.28 | 数据库 |

---

## 📖 各模块详细说明

### 1. base-mybatis-generator

**功能**：MyBatis 代码生成器

**核心内容**：
- 根据数据库表自动生成 Entity、Mapper、XML
- 支持自定义模板
- 支持批量生成

**使用示例**：
```java
public class GeneratorMain {
    public static void main(String[] args) {
        // 配置数据库连接
        // 配置生成路径
        // 执行生成
    }
}
```

---

### 2. springboot-mybatis

**功能**：MyBatis 基础使用

**核心内容**：
- 基础 CRUD 操作
- 动态 SQL（if、choose、foreach）
- ResultMap 映射
- 一对一、一对多关联查询
- 分页查询

**目录结构**：
```
springboot-mybatis/
├── mapper/          # Mapper 接口
├── entity/          # 实体类
├── service/         # 业务层
└── resources/
    └── mapper/      # MyBatis XML 文件
```

---

### 3. springboot-mybatis-multi-ds

**功能**：多数据源配置

**核心内容**：
- 配置多个数据源
- 动态数据源切换（AOP + 注解）
- 读写分离
- 多数据源事务管理

**使用示例**：
```java
@Service
public class UserService {
    
    @DS("master")  // 使用主库
    public void save(User user) {
        userMapper.insert(user);
    }
    
    @DS("slave")   // 使用从库
    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}
```

---

### 4. springboot-mybatis-plus

**功能**：MyBatis-Plus 增强工具

**核心内容**：
- 通用 CRUD（无需编写 SQL）
- 条件构造器（QueryWrapper、LambdaQueryWrapper）
- 分页插件
- 代码生成器
- 乐观锁插件
- 逻辑删除

**使用示例**：
```java
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    public List<User> listByAge(int minAge, int maxAge) {
        return list(new LambdaQueryWrapper<User>()
                .ge(User::getAge, minAge)
                .le(User::getAge, maxAge));
    }
}
```

---

### 5. springboot-sharding-jdbc

**功能**：分库分表

**核心内容**：
- 水平分表（按时间、按ID）
- 水平分库
- 读写分离
- 分布式主键（雪花算法）
- 分片策略

**配置示例**：
```yaml
spring:
  shardingsphere:
    datasource:
      names: ds0,ds1
    sharding:
      tables:
        t_order:
          actual-data-nodes: ds$->{0..1}.t_order_$->{0..1}
          table-strategy:
            inline:
              sharding-column: order_id
              algorithm-expression: t_order_$->{order_id % 2}
```

---

### 6. springboot-transaction-learning

**功能**：Spring 事务管理

**核心内容**：
- 编程式事务（TransactionTemplate）
- 声明式事务（@Transactional）
- 事务传播行为（7种）
- 事务隔离级别（4种）
- 事务失效场景
- 分布式事务（Seata）

**使用示例**：
```java
@Service
public class OrderService {
    
    @Transactional(
        propagation = Propagation.REQUIRED,
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    public void createOrder(Order order) {
        // 创建订单
        orderMapper.insert(order);
        
        // 扣减库存
        inventoryService.deduct(order.getProductId(), order.getQuantity());
        
        // 如果抛出异常，事务回滚
    }
}
```

---

## 💡 核心知识点

### MyBatis vs MyBatis-Plus

| 特性 | MyBatis | MyBatis-Plus |
|------|---------|--------------|
| CRUD | 需要手写 SQL | 自动生成 |
| 条件查询 | 手写 SQL | 条件构造器 |
| 分页 | 手动分页 | 分页插件 |
| 代码量 | 多 | 少 |
| 学习成本 | 低 | 中 |

### 多数据源方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **静态配置** | 简单 | 不灵活 | 数据源固定 |
| **动态切换** | 灵活 | 需要手动切换 | 读写分离 |
| **AOP + 注解** | 自动切换 | 配置复杂 | 多数据源 |

### 事务传播行为

| 传播行为 | 说明 |
|----------|------|
| **REQUIRED** | 如果当前存在事务，则加入该事务；如果不存在，则创建新事务 |
| **REQUIRES_NEW** | 创建新事务，如果当前存在事务，则挂起当前事务 |
| **NESTED** | 如果当前存在事务，则在嵌套事务内执行 |
| **SUPPORTS** | 如果当前存在事务，则加入该事务；如果不存在，则以非事务方式执行 |
| **NOT_SUPPORTED** | 以非事务方式执行，如果当前存在事务，则挂起当前事务 |
| **MANDATORY** | 如果当前存在事务，则加入该事务；如果不存在，则抛出异常 |
| **NEVER** | 以非事务方式执行，如果当前存在事务，则抛出异常 |

---

## ⚠️ 常见问题

### Q1: MyBatis 和 MyBatis-Plus 能同时使用吗？

**A**: 可以。MyBatis-Plus 是 MyBatis 的增强工具，完全兼容 MyBatis。

### Q2: 多数据源如何保证事务一致性？

**A**: 
- 单数据源事务：使用 Spring 的 @Transactional
- 多数据源事务：使用分布式事务（Seata、XA）

### Q3: 分库分表后如何查询？

**A**: 
- Sharding-JDBC 会自动路由到对应的表
- 需要在查询条件中包含分片键

### Q4: 事务失效的常见场景？

**A**:
1. 方法不是 public
2. 同类方法调用（this.method()）
3. 异常被捕获未抛出
4. 数据库引擎不支持事务（MyISAM）

---

## 📈 性能优化建议

### 1. MyBatis 优化
- 使用 ResultMap 避免重复查询
- 开启二级缓存
- 批量操作使用 foreach
- 避免 N+1 查询

### 2. 连接池优化
- 合理配置连接池大小
- 设置合理的超时时间
- 使用 Druid 监控 SQL 性能

### 3. 分库分表优化
- 选择合适的分片键
- 避免跨库 join
- 使用分布式主键

---

## 🤝 贡献指南

欢迎提出问题和建议！

---

**最后更新时间**：2026-01-16

**作者**：huabin

**版本**：v1.0
