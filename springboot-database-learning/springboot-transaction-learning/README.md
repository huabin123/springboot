# Spring Boot 事务学习项目

本项目用于学习MySQL事务机制和Spring事务管理，包含详细的代码示例和文档说明。

## 📚 学习内容

### MySQL事务机制
- **锁机制**：表级锁、行级锁、共享锁、排他锁、间隙锁、临键锁
- **MVCC**：多版本并发控制、Read View、快照读vs当前读
- **日志系统**：Redo Log、Undo Log、Binlog、WAL机制

### Spring事务管理
- **@Transactional原理**：AOP代理、事务拦截器
- **事务传播行为**：7种传播行为详解
- **事务隔离级别**：4种隔离级别对比
- **事务失效场景**：12种常见失效场景及解决方案

## 🚀 快速开始

### 1. 环境要求
- JDK 8+
- Maven 3.6+
- Docker & Docker Compose
- MySQL 5.7

### 2. 启动MySQL容器

```bash
cd ../doc/env/mysql
docker compose up -d
```

### 3. 初始化数据库

连接MySQL并执行初始化脚本：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -pHuabin123$

# 执行初始化脚本
source src/main/resources/sql/init.sql
```

或使用MySQL客户端工具执行`src/main/resources/sql/init.sql`。

### 4. 启动应用

```bash
mvn clean install
mvn spring-boot:run
```

应用启动后访问：http://localhost:8080

## 📖 文档目录

- [01-MySQL锁机制.md](doc/01-MySQL锁机制.md) - MySQL锁的分类、原理和使用
- [02-MVCC机制.md](doc/02-MVCC机制.md) - MVCC的实现原理和应用
- [03-分布式强一致性事务实现.md](doc/03-分布式强一致性事务实现.md) - 分布式事务的实现方案和原理
- [04-Redo和Undo日志.md](doc/04-Redo和Undo日志.md) - MySQL日志系统详解
- [05-Spring事务原理.md](doc/05-Spring事务原理.md) - Spring事务的实现原理
- [06-事务失效场景分析.md](doc/06-事务失效场景分析.md) - 常见事务失效场景及解决方案
- [07-学习指南.md](doc/07-学习指南.md) - 学习路径和实战练习

## 🎯 代码示例

### 行锁示例

```java
// 排他锁（FOR UPDATE）
@Transactional(rollbackFor = Exception.class)
public void transferWithExclusiveLock(Long fromId, Long toId, BigDecimal amount) {
    // 锁定转出账户
    Account from = accountMapper.selectByIdForUpdate(fromId);
    
    // 检查余额
    if (from.getBalance().compareTo(amount) < 0) {
        throw new RuntimeException("余额不足");
    }
    
    // 锁定转入账户
    Account to = accountMapper.selectByIdForUpdate(toId);
    
    // 执行转账
    accountMapper.deductBalance(fromId, amount);
    accountMapper.addBalance(toId, amount);
}
```

### 间隙锁示例

```java
// 范围查询产生间隙锁
@Transactional(rollbackFor = Exception.class)
public List<User> queryWithGapLock(Integer minAge, Integer maxAge) {
    // 会产生间隙锁，防止其他事务在间隙中插入数据
    return userMapper.selectByAgeRangeForUpdate(minAge, maxAge);
}
```

### 事务传播行为示例

```java
// REQUIRES_NEW：创建新事务
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void insertLogWithNewTransaction(String type, String desc) {
    TransactionLog log = new TransactionLog();
    log.setOperationType(type);
    log.setDescription(desc);
    logMapper.insert(log);
}
```

## 🧪 测试

### 运行单元测试

```bash
mvn test
```

### API测试

```bash
# 测试排他锁
curl -X POST "http://localhost:8080/transaction/row-lock/exclusive?fromId=1&toId=2&amount=100"

# 测试间隙锁
curl -X GET "http://localhost:8080/transaction/gap-lock/query?minAge=20&maxAge=30"

# 测试MVCC
curl -X GET "http://localhost:8080/transaction/mvcc/repeatable-read/1"

# 测试事务传播
curl -X POST "http://localhost:8080/transaction/propagation/requires-new?throwException=true"
```

## 📁 项目结构

```
springboot-transaction-learning/
├── src/
│   ├── main/
│   │   ├── java/com/huabin/transaction/
│   │   │   ├── controller/          # 控制器
│   │   │   │   └── TransactionDemoController.java
│   │   │   ├── entity/              # 实体类
│   │   │   │   ├── Account.java
│   │   │   │   ├── User.java
│   │   │   │   ├── Orders.java
│   │   │   │   └── TransactionLog.java
│   │   │   ├── mapper/              # MyBatis Mapper
│   │   │   │   ├── AccountMapper.java
│   │   │   │   ├── UserMapper.java
│   │   │   │   ├── OrdersMapper.java
│   │   │   │   └── TransactionLogMapper.java
│   │   │   ├── service/             # 业务服务
│   │   │   │   ├── RowLockService.java              # 行锁演示
│   │   │   │   ├── GapLockService.java              # 间隙锁演示
│   │   │   │   ├── MvccService.java                 # MVCC演示
│   │   │   │   ├── TransactionPropagationService.java  # 事务传播
│   │   │   │   └── TransactionFailureService.java   # 事务失效场景
│   │   │   └── TransactionLearningApplication.java  # 启动类
│   │   └── resources/
│   │       ├── mapper/              # MyBatis XML
│   │       ├── sql/
│   │       │   └── init.sql         # 初始化脚本
│   │       └── application.yml      # 配置文件
│   └── test/
│       └── java/com/huabin/transaction/
│           └── TransactionTest.java # 测试类
├── doc/                             # 学习文档
│   ├── 01-MySQL锁机制.md
│   ├── 02-MVCC机制.md
│   ├── 03-Redo和Undo日志.md
│   ├── 04-Spring事务原理.md
│   ├── 05-事务失效场景分析.md
│   └── 06-学习指南.md
├── pom.xml
└── README.md
```

## 🔑 核心知识点

### MySQL锁机制
- ✅ 表级锁 vs 行级锁
- ✅ 共享锁 vs 排他锁
- ✅ 乐观锁 vs 悲观锁
- ✅ 记录锁、间隙锁、临键锁
- ✅ 死锁的产生和避免

### MVCC机制
- ✅ 隐藏字段（DB_TRX_ID、DB_ROLL_PTR）
- ✅ Undo Log版本链
- ✅ Read View可见性判断
- ✅ 快照读 vs 当前读
- ✅ RC vs RR隔离级别

### Redo和Undo日志
- ✅ Redo Log的作用和原理
- ✅ Undo Log的作用和原理
- ✅ WAL（Write-Ahead Logging）机制
- ✅ 两阶段提交
- ✅ 崩溃恢复流程

### Spring事务
- ✅ @Transactional原理（AOP代理）
- ✅ 7种事务传播行为
- ✅ 4种事务隔离级别
- ✅ 事务回滚规则
- ✅ 12种事务失效场景

## 💡 学习建议

1. **循序渐进**：按照文档顺序学习，先理解原理再实践
2. **动手实践**：运行示例代码，观察日志输出
3. **多做实验**：修改参数，观察不同场景下的行为
4. **总结归纳**：学完每个章节，写总结笔记
5. **实战应用**：在实际项目中应用所学知识

## 📝 常用命令

### MySQL命令

```sql
-- 查看隔离级别
SELECT @@transaction_isolation;

-- 查看当前事务
SELECT * FROM information_schema.innodb_trx;

-- 查看锁等待
SELECT * FROM performance_schema.data_lock_waits;

-- 查看死锁信息
SHOW ENGINE INNODB STATUS;
```

### Docker命令

```bash
# 启动容器
docker compose up -d

# 停止容器
docker compose down

# 查看日志
docker compose logs -f

# 进入容器
docker exec -it mysql57 bash
```

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📄 License

MIT License

## 👨‍💻 作者

huabin

---

**Happy Learning! 🎉**
