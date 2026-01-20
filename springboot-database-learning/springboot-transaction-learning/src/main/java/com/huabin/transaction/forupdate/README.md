# FOR UPDATE在不同事务隔离级别下的行为演示

## 一、项目说明

本演示项目用于回答以下核心问题：

1. **当一个事务执行`FOR UPDATE`锁定记录时，其他线程能否读取这条记录？**
2. **读取到的是什么数据？**
3. **不同的事务隔离级别表现有什么不同？**

## 二、环境准备

### 2.1 数据库准备

```sql
-- 1. 创建数据库（如果还没有）
CREATE DATABASE IF NOT EXISTS springboot_db;

-- 2. 使用数据库
USE springboot_db;

-- 3. 执行建表脚本
-- 执行 src/main/resources/sql/product.sql
```

### 2.2 启动应用

```bash
# 1. 确保MySQL已启动
# 2. 启动Spring Boot应用
mvn spring-boot:run

# 或者在IDE中运行 TransactionLearningApplication
```

### 2.3 初始化测试数据

```bash
# 访问初始化接口
curl http://localhost:8080/forupdate/init
```

## 三、测试场景

### 场景1：READ UNCOMMITTED（读未提交）

#### 测试步骤：

```bash
# 1. 初始化数据
curl http://localhost:8080/forupdate/init

# 2. 启动事务A（持有FOR UPDATE锁，修改数据但不提交）
curl "http://localhost:8080/forupdate/read-uncommitted/demo?productId=1"

# 3. 在5秒内，启动事务B - 快照读
curl "http://localhost:8080/forupdate/read-uncommitted/snapshot-read?productId=1"

# 4. 观察日志，查看事务B是否能读取，读到什么数据
```

#### 预期结果：

- **快照读**：✅ 能读取，⚠️ 读到事务A未提交的数据（脏读）
- **当前读**：❌ 阻塞等待，必须等事务A提交

#### 测试当前读：

```bash
# 1. 启动事务A
curl "http://localhost:8080/forupdate/read-uncommitted/demo?productId=1"

# 2. 在5秒内，启动事务B - 当前读
curl "http://localhost:8080/forupdate/read-uncommitted/current-read?productId=1"

# 3. 观察日志，查看等待时间
```

---

### 场景2：READ COMMITTED（读已提交）

#### 测试步骤：

```bash
# 1. 初始化数据
curl http://localhost:8080/forupdate/init

# 2. 启动事务A
curl "http://localhost:8080/forupdate/read-committed/demo?productId=1"

# 3. 在5秒内，启动事务B - 快照读
curl "http://localhost:8080/forupdate/read-committed/snapshot-read?productId=1"

# 4. 观察日志
```

#### 预期结果：

- **快照读**：✅ 能读取，✅ 读到已提交的数据（避免脏读），⚠️ 不可重复读
- **当前读**：❌ 阻塞等待

---

### 场景3：REPEATABLE READ（可重复读，MySQL默认）

#### 测试步骤（演示MVCC）：

```bash
# 1. 初始化数据
curl http://localhost:8080/forupdate/init

# 2. 先启动事务B（建立快照）
curl "http://localhost:8080/forupdate/repeatable-read/snapshot-read?productId=1" &

# 3. 立即启动事务A（修改并提交）
sleep 1
curl "http://localhost:8080/forupdate/repeatable-read/demo?productId=1"

# 4. 观察事务B的三次读取结果
```

#### 预期结果：

- **第一次快照读**：读到事务开始时的数据（price=7999）
- **第二次快照读**：仍然读到快照数据（price=7999）✅ 可重复读
- **第三次当前读**：读到最新数据（price=8299）⚠️ 快照读 vs 当前读

---

### 场景4：SERIALIZABLE（串行化）

#### 测试步骤：

```bash
# 1. 初始化数据
curl http://localhost:8080/forupdate/init

# 2. 启动事务A（持有FOR UPDATE锁）
curl "http://localhost:8080/forupdate/serializable/demo?productId=1"

# 3. 在5秒内，启动事务B - 普通SELECT
curl "http://localhost:8080/forupdate/serializable/snapshot-read?productId=1"

# 4. 观察日志
```

#### 预期结果：

- **普通SELECT**：❌ 阻塞等待（SERIALIZABLE级别下，普通SELECT也会加共享锁）
- **共享锁与排他锁冲突**，必须等待事务A释放锁

---

### 场景5：实战 - 秒杀扣减库存

#### 测试正确做法（使用FOR UPDATE）：

```bash
# 1. 初始化数据（商品1库存100）
curl http://localhost:8080/forupdate/init

# 2. 并发测试（10个线程，每次扣减10）
curl "http://localhost:8080/forupdate/concurrent-test?productId=1&threadCount=10&quantity=10&useCorrect=true"

# 3. 查看结果
# 预期：初始库存100，扣减10次*10=100，最终库存0（正确）
```

#### 测试错误做法（使用快照读）：

```bash
# 1. 初始化数据
curl http://localhost:8080/forupdate/init

# 2. 并发测试（使用错误方法）
curl "http://localhost:8080/forupdate/concurrent-test?productId=1&threadCount=10&quantity=10&useCorrect=false"

# 3. 查看结果
# 可能出现：超卖（库存变成负数）
```

---

## 四、核心代码说明

### 4.1 实体类

- **Product.java**：商品实体，包含价格、库存、版本号等字段

### 4.2 Mapper层

- **ProductMapper.java**：定义了三种查询方式
  - `selectById()`：普通SELECT（快照读）
  - `selectByIdForUpdate()`：SELECT ... FOR UPDATE（当前读，排他锁）
  - `selectByIdLockInShareMode()`：SELECT ... LOCK IN SHARE MODE（当前读，共享锁）

### 4.3 Service层

- **ForUpdateIsolationService.java**：核心演示逻辑
  - `readUncommittedTransactionA/B()`：READ UNCOMMITTED演示
  - `readCommittedTransactionA/B()`：READ COMMITTED演示
  - `repeatableReadTransactionA/B()`：REPEATABLE READ演示
  - `serializableTransactionA/B()`：SERIALIZABLE演示
  - `seckillCorrect()`：秒杀正确做法
  - `seckillWrong()`：秒杀错误做法

### 4.4 Controller层

- **ForUpdateTestController.java**：提供HTTP接口
  - 初始化数据
  - 各种隔离级别的演示接口
  - 并发测试接口

---

## 五、关键结论

### 5.1 快照读 vs 当前读

| 读取方式 | 是否加锁 | 能否读取（当其他事务持有FOR UPDATE锁） | 读取到的数据 |
|---------|---------|----------------------------------|------------|
| **普通SELECT（快照读）** | ❌ 不加锁 | ✅ 能读取 | 快照数据（MVCC） |
| **FOR UPDATE（当前读）** | ✅ 加排他锁 | ❌ 阻塞等待 | 最新已提交数据 |
| **LOCK IN SHARE MODE（当前读）** | ✅ 加共享锁 | ❌ 阻塞等待 | 最新已提交数据 |

### 5.2 不同隔离级别的表现

| 隔离级别 | 普通SELECT | 能否脏读 | 能否不可重复读 | 能否幻读 |
|---------|-----------|---------|--------------|---------|
| **READ UNCOMMITTED** | 不加锁 | ✅ 是 | ✅ 是 | ✅ 是 |
| **READ COMMITTED** | 不加锁 | ❌ 否 | ✅ 是 | ✅ 是 |
| **REPEATABLE READ** | 不加锁（MVCC） | ❌ 否 | ❌ 否 | ⚠️ 部分解决 |
| **SERIALIZABLE** | 加共享锁 | ❌ 否 | ❌ 否 | ❌ 否 |

### 5.3 最佳实践

1. **读多写少场景**：使用快照读 + READ COMMITTED
   ```java
   @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
   public Product queryProduct(Long id) {
       return productMapper.selectById(id);  // 快照读
   }
   ```

2. **写操作场景**：使用FOR UPDATE + REPEATABLE READ
   ```java
   @Transactional(isolation = Isolation.REPEATABLE_READ)
   public void deductStock(Long id, Integer quantity) {
       Product product = productMapper.selectByIdForUpdate(id);  // 当前读
       // 扣减库存...
   }
   ```

3. **避免长事务持有锁**
   ```java
   // ❌ 错误：长时间持有锁
   @Transactional
   public void bad() {
       Product product = productMapper.selectByIdForUpdate(1L);
       Thread.sleep(10000);  // 持有锁10秒
   }
   
   // ✅ 正确：快速完成
   @Transactional
   public void good() {
       Product product = productMapper.selectByIdForUpdate(1L);
       productMapper.update(product);  // 立即完成
   }
   ```

---

## 六、常见问题

### Q1：为什么快照读不会被FOR UPDATE阻塞？

**A**：因为快照读使用MVCC（多版本并发控制），通过undo log读取历史版本，不需要加锁，因此不会与FOR UPDATE的排他锁冲突。

### Q2：什么时候应该使用FOR UPDATE？

**A**：
- ✅ 需要修改数据，且要基于最新数据判断（如扣减库存）
- ✅ 需要防止并发修改导致的数据不一致
- ❌ 仅查询展示，不需要修改（应使用快照读）

### Q3：如何避免死锁？

**A**：
1. 按相同顺序访问资源
2. 缩小事务范围
3. 降低隔离级别（如使用READ COMMITTED）
4. 添加合理的索引

### Q4：REPEATABLE READ下，快照读和当前读的区别？

**A**：
- **快照读**：读事务开始时的快照，可重复读
- **当前读**：读最新已提交的数据，可能与快照不一致

---

## 七、相关文档

- **详细文档**：`doc/问题05-FOR UPDATE执行时其他线程的读取行为与事务隔离级别.md`
- **MySQL锁机制**：`doc/01-MySQL锁机制.md`
- **MVCC机制**：`doc/02-MVCC机制.md`

---

## 八、监控命令

```sql
-- 查看当前锁等待
SELECT * FROM performance_schema.data_lock_waits;

-- 查看当前持有的锁
SELECT * FROM performance_schema.data_locks;

-- 查看事务状态
SELECT * FROM information_schema.innodb_trx;

-- 查看当前隔离级别
SELECT @@transaction_isolation;

-- 设置隔离级别
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

---

## 九、总结

通过本演示项目，你可以清楚地看到：

1. **快照读（普通SELECT）不会被FOR UPDATE阻塞**，但读到的是快照数据
2. **当前读（FOR UPDATE）会被阻塞**，等锁释放后读到最新数据
3. **不同隔离级别主要影响快照读的行为**，当前读始终读最新数据
4. **实战中要根据场景选择合适的读取方式和隔离级别**

记住：**快照读性能高但可能读到旧数据，当前读数据准确但会阻塞**。
