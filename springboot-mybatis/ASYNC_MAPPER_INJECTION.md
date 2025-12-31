# MyBatis Mapper 异步调用指南

## 📌 核心结论

**异步调用本身不会导致 Mapper 注入失败！**

Mapper 的注入发生在 Spring 容器启动时，与是否异步调用无关。但异步调用时需要注意以下几点：

---

## ✅ 正确的做法

### 1. Mapper 注入是正常的
```java
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;  // ✅ 注入没问题
    
    @Async
    public CompletableFuture<User> getUserAsync(Long id) {
        // ✅ 可以正常使用 mapper
        User user = userMapper.selectById(id);
        return CompletableFuture.completedFuture(user);
    }
}
```

### 2. 异步方法中直接使用 Mapper
```java
@Service
public class OrderService {
    @Autowired
    private OrderMapper orderMapper;
    
    @Async("taskExecutor")
    public void processOrderAsync(Long orderId) {
        // ✅ 异步线程中可以直接使用 mapper
        Order order = orderMapper.selectById(orderId);
        // 处理业务逻辑
        orderMapper.updateStatus(orderId, "PROCESSED");
    }
}
```

---

## ⚠️ 常见问题及解决方案

### 问题1：事务传播问题

**现象**：
```java
@Async
@Transactional  // ⚠️ 事务可能不生效
public void asyncMethod() {
    userMapper.insert(user);
    // 事务可能不会回滚
}
```

**原因**：
- `@Async` 会在新线程中执行
- 新线程没有继承原线程的事务上下文
- 事务管理器无法跨线程传播事务

**解决方案**：
```java
// 方案1：在异步方法内部开启新事务
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    @Async
    public CompletableFuture<Void> asyncSave(User user) {
        // 调用带事务的方法
        saveWithTransaction(user);
        return CompletableFuture.completedFuture(null);
    }
    
    @Transactional  // ✅ 在同步方法上加事务
    public void saveWithTransaction(User user) {
        userMapper.insert(user);
    }
}

// 方案2：使用编程式事务
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    @Async
    public CompletableFuture<Void> asyncSave(User user) {
        transactionTemplate.execute(status -> {
            try {
                userMapper.insert(user);
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
        return CompletableFuture.completedFuture(null);
    }
}
```

---

### 问题2：数据库连接池耗尽

**现象**：
```
Could not get JDBC Connection
HikariPool - Connection is not available
```

**原因**：
- 大量异步任务同时执行
- 每个任务都占用一个数据库连接
- 连接池大小不足

**解决方案**：

```yaml
# application.yml
spring:
  datasource:
    hikari:
      # 调整连接池大小
      minimum-idle: 10
      maximum-pool-size: 50  # 根据实际情况调整
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  # 限制异步线程池大小
  task:
    execution:
      pool:
        core-size: 10
        max-size: 20  # 不要超过数据库连接池大小
        queue-capacity: 100
```

---

### 问题3：MyBatis Session 线程安全问题

**现象**：
```
org.apache.ibatis.executor.ExecutorException: Error getting generated key
```

**原因**：
- MyBatis 的 SqlSession 默认不是线程安全的
- 但 Spring 集成后，每次调用都会创建新的 SqlSession

**解决方案**：
```java
// ✅ Spring 管理的 Mapper 是线程安全的，可以放心使用
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;  // Spring 代理，线程安全
    
    @Async
    public void asyncMethod1() {
        userMapper.selectById(1L);  // ✅ 安全
    }
    
    @Async
    public void asyncMethod2() {
        userMapper.selectById(2L);  // ✅ 安全
    }
}
```

---

### 问题4：异步方法自调用失效

**现象**：
```java
@Service
public class UserService {
    @Async
    public void asyncMethod() {
        // 异步逻辑
    }
    
    public void normalMethod() {
        this.asyncMethod();  // ⚠️ 不会异步执行！
    }
}
```

**原因**：
- Spring AOP 代理机制
- 同类内部调用不会经过代理

**解决方案**：
```java
// 方案1：拆分到不同的类
@Service
public class UserAsyncService {
    @Autowired
    private UserMapper userMapper;
    
    @Async
    public void asyncMethod() {
        userMapper.selectAll();
    }
}

@Service
public class UserService {
    @Autowired
    private UserAsyncService asyncService;
    
    public void normalMethod() {
        asyncService.asyncMethod();  // ✅ 会异步执行
    }
}

// 方案2：自己注入自己
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    @Lazy
    private UserService self;  // 注入代理对象
    
    @Async
    public void asyncMethod() {
        userMapper.selectAll();
    }
    
    public void normalMethod() {
        self.asyncMethod();  // ✅ 会异步执行
    }
}
```

---

### 问题5：异步方法返回值处理

**错误示例**：
```java
@Async
public User getUser(Long id) {  // ⚠️ 返回值会丢失
    return userMapper.selectById(id);
}
```

**正确示例**：
```java
// 使用 CompletableFuture
@Async
public CompletableFuture<User> getUser(Long id) {
    User user = userMapper.selectById(id);
    return CompletableFuture.completedFuture(user);
}

// 使用 ListenableFuture
@Async
public ListenableFuture<User> getUser(Long id) {
    User user = userMapper.selectById(id);
    return new AsyncResult<>(user);
}

// 无返回值
@Async
public void processUser(Long id) {
    User user = userMapper.selectById(id);
    // 处理逻辑
}
```

---

## 🔧 完整配置示例

### 1. 启用异步支持
```java
@Configuration
@EnableAsync  // 启用异步支持
public class AsyncConfig implements AsyncConfigurer {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
    
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            System.err.println("异步方法执行异常: " + method.getName());
            ex.printStackTrace();
        };
    }
}
```

### 2. 异步 Service 示例
```java
@Service
public class UserAsyncService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private TransactionTemplate transactionTemplate;
    
    /**
     * 异步查询 - 无事务
     */
    @Async("taskExecutor")
    public CompletableFuture<List<User>> getAllUsersAsync() {
        List<User> users = userMapper.selectAll();
        return CompletableFuture.completedFuture(users);
    }
    
    /**
     * 异步插入 - 带事务
     */
    @Async("taskExecutor")
    public CompletableFuture<Void> saveUserAsync(User user) {
        transactionTemplate.execute(status -> {
            try {
                userMapper.insert(user);
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 批量异步处理
     */
    @Async("taskExecutor")
    public void batchProcessAsync(List<Long> userIds) {
        for (Long userId : userIds) {
            transactionTemplate.execute(status -> {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    // 处理逻辑
                    userMapper.updateStatus(userId, "PROCESSED");
                }
                return null;
            });
        }
    }
}
```

---

## 📊 性能优化建议

### 1. 合理设置线程池大小
```java
// CPU 密集型任务
int coreSize = Runtime.getRuntime().availableProcessors() + 1;

// IO 密集型任务（数据库操作）
int coreSize = Runtime.getRuntime().availableProcessors() * 2;

// 根据实际情况调整
executor.setCorePoolSize(coreSize);
executor.setMaxPoolSize(coreSize * 2);
```

### 2. 避免过度异步
```java
// ❌ 不好的做法
@Async
public void simpleQuery() {
    userMapper.selectById(1L);  // 简单查询不需要异步
}

// ✅ 好的做法
@Async
public void complexBatchProcess() {
    List<User> users = userMapper.selectAll();
    // 复杂的批量处理逻辑
    for (User user : users) {
        // 耗时操作
    }
}
```

### 3. 使用批量操作
```java
// ❌ 不好的做法
@Async
public void processUsers(List<Long> ids) {
    for (Long id : ids) {
        userMapper.updateStatus(id, "PROCESSED");  // N次数据库调用
    }
}

// ✅ 好的做法
@Async
public void processUsers(List<Long> ids) {
    userMapper.batchUpdateStatus(ids, "PROCESSED");  // 1次数据库调用
}
```

---

## 🧪 测试验证

```java
@SpringBootTest
public class AsyncMapperTest {
    
    @Autowired
    private UserAsyncService asyncService;
    
    @Test
    public void testAsyncMapper() throws Exception {
        // 调用异步方法
        CompletableFuture<List<User>> future = asyncService.getAllUsersAsync();
        
        // 等待结果
        List<User> users = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(users);
        System.out.println("异步查询成功，结果数量: " + users.size());
    }
    
    @Test
    public void testMultipleAsyncCalls() throws Exception {
        // 并发调用多个异步方法
        CompletableFuture<User> future1 = asyncService.getUserAsync(1L);
        CompletableFuture<User> future2 = asyncService.getUserAsync(2L);
        CompletableFuture<User> future3 = asyncService.getUserAsync(3L);
        
        // 等待所有完成
        CompletableFuture.allOf(future1, future2, future3).get();
        
        System.out.println("所有异步查询完成");
    }
}
```

---

## ✅ 检查清单

- [ ] 启动类或配置类添加了 `@EnableAsync`
- [ ] 异步方法在不同的类中（避免自调用失效）
- [ ] 异步方法返回 `void`、`Future` 或 `CompletableFuture`
- [ ] 配置了合适的线程池大小
- [ ] 数据库连接池大小 ≥ 异步线程池大小
- [ ] 异步方法中的事务处理正确
- [ ] 添加了异常处理机制
- [ ] Mapper 注入使用 `@Autowired`（不是手动创建）

---

## 🎯 总结

1. **Mapper 注入不受异步影响** - Spring 管理的 Mapper 是线程安全的
2. **注意事务传播** - 异步方法中需要重新开启事务
3. **控制并发数** - 线程池和连接池要匹配
4. **避免自调用** - 异步方法要通过 Spring 代理调用
5. **正确处理返回值** - 使用 `CompletableFuture` 或 `void`

**异步调用 Mapper 是完全可行的，只要配置正确！**
