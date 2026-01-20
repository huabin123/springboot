# Spring事务原理详解

## 一、Spring事务概述

### 1.1 什么是Spring事务

Spring事务是Spring框架提供的声明式事务管理机制，通过AOP（面向切面编程）实现。

### 1.2 Spring事务的优势

- **声明式事务**：通过注解或XML配置，无需手动编写事务代码
- **统一的事务抽象**：支持多种事务管理器（JDBC、Hibernate、JPA等）
- **灵活的传播行为**：支持7种事务传播行为
- **易于测试**：可以方便地进行事务回滚测试

## 二、@Transactional注解原理

### 2.1 核心组件

#### TransactionInterceptor（事务拦截器）
- 实现了AOP的MethodInterceptor接口
- 在目标方法执行前后进行事务管理

#### PlatformTransactionManager（事务管理器）
- 定义了事务的基本操作：开启、提交、回滚
- 常用实现：
  - `DataSourceTransactionManager`：JDBC事务
  - `JpaTransactionManager`：JPA事务
  - `HibernateTransactionManager`：Hibernate事务

#### TransactionDefinition（事务定义）
- 定义事务的属性：隔离级别、传播行为、超时时间等

#### TransactionStatus（事务状态）
- 表示事务的当前状态

### 2.2 工作原理

```
1. Spring容器启动
   ↓
2. 扫描@Transactional注解
   ↓
3. 为标注了@Transactional的类创建代理对象（AOP）
   ↓
4. 调用方法时，先执行TransactionInterceptor
   ↓
5. TransactionInterceptor调用TransactionManager开启事务
   ↓
6. 执行目标方法
   ↓
7. 方法正常返回：提交事务
   ↓
8. 方法抛出异常：回滚事务
```

### 2.3 代理方式

#### JDK动态代理
- **条件**：目标类实现了接口
- **原理**：基于接口创建代理对象
- **限制**：只能代理接口方法

#### CGLIB代理
- **条件**：目标类未实现接口
- **原理**：通过继承目标类创建代理对象
- **限制**：不能代理final类和final方法

**配置**：
```java
@EnableTransactionManagement(proxyTargetClass = true)  // 强制使用CGLIB
```

## 三、事务传播行为

### 3.1 七种传播行为

#### 1. REQUIRED（默认）
```java
@Transactional(propagation = Propagation.REQUIRED)
```
- **行为**：如果当前存在事务，则加入该事务；如果不存在，则创建新事务
- **场景**：最常用的传播行为

#### 2. SUPPORTS
```java
@Transactional(propagation = Propagation.SUPPORTS)
```
- **行为**：如果当前存在事务，则加入该事务；如果不存在，则以非事务方式执行
- **场景**：查询操作

#### 3. MANDATORY
```java
@Transactional(propagation = Propagation.MANDATORY)
```
- **行为**：如果当前存在事务，则加入该事务；如果不存在，则抛出异常
- **场景**：必须在事务中执行的方法

#### 4. REQUIRES_NEW
```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
```
- **行为**：创建新事务，如果当前存在事务，则挂起当前事务
- **场景**：记录日志（即使主业务回滚，日志也要保存）

#### 5. NOT_SUPPORTED
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
```
- **行为**：以非事务方式执行，如果当前存在事务，则挂起当前事务
- **场景**：不需要事务的操作

#### 6. NEVER
```java
@Transactional(propagation = Propagation.NEVER)
```
- **行为**：以非事务方式执行，如果当前存在事务，则抛出异常
- **场景**：明确不能在事务中执行的方法

#### 7. NESTED
```java
@Transactional(propagation = Propagation.NESTED)
```
- **行为**：如果当前存在事务，则在嵌套事务内执行；如果不存在，则创建新事务
- **实现**：使用JDBC的Savepoint机制
- **特点**：
  - 外层事务回滚，嵌套事务也回滚
  - 嵌套事务回滚，不影响外层事务（可以被捕获）

### 3.2 传播行为对比

| 传播行为 | 当前有事务 | 当前无事务 |
|---------|-----------|-----------|
| REQUIRED | 加入当前事务 | 创建新事务 |
| SUPPORTS | 加入当前事务 | 非事务执行 |
| MANDATORY | 加入当前事务 | 抛出异常 |
| REQUIRES_NEW | 挂起当前事务，创建新事务 | 创建新事务 |
| NOT_SUPPORTED | 挂起当前事务，非事务执行 | 非事务执行 |
| NEVER | 抛出异常 | 非事务执行 |
| NESTED | 创建嵌套事务 | 创建新事务 |

## 四、事务隔离级别

### 4.1 五种隔离级别

```java
@Transactional(isolation = Isolation.DEFAULT)         // 使用数据库默认隔离级别
@Transactional(isolation = Isolation.READ_UNCOMMITTED) // 读未提交
@Transactional(isolation = Isolation.READ_COMMITTED)   // 读已提交
@Transactional(isolation = Isolation.REPEATABLE_READ)  // 可重复读（MySQL默认）
@Transactional(isolation = Isolation.SERIALIZABLE)     // 串行化
```

### 4.2 隔离级别对比

| 隔离级别 | 脏读 | 不可重复读 | 幻读 | 性能 |
|---------|------|-----------|------|------|
| READ_UNCOMMITTED | 可能 | 可能 | 可能 | 最高 |
| READ_COMMITTED | 不可能 | 可能 | 可能 | 较高 |
| REPEATABLE_READ | 不可能 | 不可能 | 可能 | 较低 |
| SERIALIZABLE | 不可能 | 不可能 | 不可能 | 最低 |

## 五、事务回滚规则

### 5.1 默认回滚规则

```java
@Transactional  // 默认只回滚RuntimeException和Error
```

- **回滚**：RuntimeException及其子类、Error及其子类
- **不回滚**：受检异常（Checked Exception）

### 5.2 自定义回滚规则

```java
// 指定回滚的异常类型
@Transactional(rollbackFor = Exception.class)

// 指定不回滚的异常类型
@Transactional(noRollbackFor = IllegalArgumentException.class)

// 指定回滚的异常类名
@Transactional(rollbackForClassName = "java.lang.Exception")

// 指定不回滚的异常类名
@Transactional(noRollbackForClassName = "java.lang.IllegalArgumentException")
```

**推荐做法**：
```java
@Transactional(rollbackFor = Exception.class)  // 回滚所有异常
```

## 六、事务超时和只读

### 6.1 事务超时

```java
@Transactional(timeout = 30)  // 30秒超时
```

- **作用**：防止长事务占用资源
- **单位**：秒
- **默认值**：-1（使用数据库默认超时时间）

### 6.2 只读事务

```java
@Transactional(readOnly = true)
```

- **作用**：优化性能，告诉数据库这是只读操作
- **优化**：
  - 数据库可能不加锁
  - 不需要维护Undo Log
  - 不需要记录Binlog
- **场景**：查询操作

## 七、@Transactional注解的使用位置

### 7.1 类级别

```java
@Service
@Transactional  // 类中所有public方法都有事务
public class UserService {
    public void method1() { }
    public void method2() { }
}
```

### 7.2 方法级别

```java
@Service
public class UserService {
    @Transactional  // 只有这个方法有事务
    public void method1() { }
    
    public void method2() { }  // 无事务
}
```

### 7.3 优先级

方法级别的@Transactional优先级高于类级别。

```java
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class UserService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 使用REQUIRES_NEW
    public void method1() { }
    
    public void method2() { }  // 使用REQUIRED
}
```

## 八、事务的完整配置

```java
@Transactional(
    propagation = Propagation.REQUIRED,      // 传播行为
    isolation = Isolation.DEFAULT,           // 隔离级别
    timeout = 30,                            // 超时时间（秒）
    readOnly = false,                        // 是否只读
    rollbackFor = Exception.class,           // 回滚异常类型
    noRollbackFor = IllegalArgumentException.class  // 不回滚异常类型
)
public void complexMethod() {
    // 业务逻辑
}
```

## 九、编程式事务

### 9.1 TransactionTemplate

```java
@Autowired
private TransactionTemplate transactionTemplate;

public void method() {
    transactionTemplate.execute(status -> {
        try {
            // 业务逻辑
            return result;
        } catch (Exception e) {
            status.setRollbackOnly();  // 标记回滚
            throw e;
        }
    });
}
```

### 9.2 PlatformTransactionManager

```java
@Autowired
private PlatformTransactionManager transactionManager;

public void method() {
    TransactionDefinition def = new DefaultTransactionDefinition();
    TransactionStatus status = transactionManager.getTransaction(def);
    
    try {
        // 业务逻辑
        transactionManager.commit(status);
    } catch (Exception e) {
        transactionManager.rollback(status);
        throw e;
    }
}
```

## 十、事务源码分析

### 10.1 TransactionInterceptor核心代码

```java
public class TransactionInterceptor extends TransactionAspectSupport 
        implements MethodInterceptor {
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取目标类
        Class<?> targetClass = invocation.getThis().getClass();
        
        // 执行事务方法
        return invokeWithinTransaction(
            invocation.getMethod(), 
            targetClass, 
            invocation::proceed
        );
    }
}
```

### 10.2 invokeWithinTransaction核心逻辑

```java
protected Object invokeWithinTransaction(Method method, Class<?> targetClass,
        InvocationCallback invocation) throws Throwable {
    
    // 1. 获取事务属性
    TransactionAttributeSource tas = getTransactionAttributeSource();
    TransactionAttribute txAttr = tas.getTransactionAttribute(method, targetClass);
    
    // 2. 获取事务管理器
    PlatformTransactionManager tm = determineTransactionManager(txAttr);
    
    // 3. 创建事务信息
    TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
    
    Object retVal;
    try {
        // 4. 执行目标方法
        retVal = invocation.proceedWithInvocation();
    } catch (Throwable ex) {
        // 5. 异常处理：回滚或提交
        completeTransactionAfterThrowing(txInfo, ex);
        throw ex;
    } finally {
        cleanupTransactionInfo(txInfo);
    }
    
    // 6. 提交事务
    commitTransactionAfterReturning(txInfo);
    return retVal;
}
```

### 10.3 事务创建流程

```java
protected TransactionInfo createTransactionIfNecessary(
        PlatformTransactionManager tm, TransactionAttribute txAttr, String joinpointIdentification) {
    
    // 1. 获取事务状态
    TransactionStatus status = tm.getTransaction(txAttr);
    
    // 2. 创建事务信息
    return prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
}
```

## 十一、配置事务管理器

### 11.1 Java配置

```java
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

### 11.2 Spring Boot自动配置

Spring Boot会自动配置事务管理器，只需添加：

```java
@SpringBootApplication
@EnableTransactionManagement  // 可选，Spring Boot默认已开启
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 十二、代码示例位置

- **事务传播行为**：`TransactionPropagationService.java`
- **事务失效场景**：`TransactionFailureService.java`
- **Controller示例**：`TransactionDemoController.java`

## 十三、相关配置

```yaml
spring:
  datasource:
    # 数据源配置
    
  transaction:
    # 事务超时时间（秒）
    default-timeout: 30
    # 回滚异常类型
    rollback-on-commit-failure: true

logging:
  level:
    # 开启事务日志
    org.springframework.jdbc.datasource.DataSourceTransactionManager: debug
    org.springframework.transaction: debug
```
