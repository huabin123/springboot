# MySQL锁机制详解

## 一、锁的分类

### 1. 按锁的粒度分类

#### 1.1 表级锁（Table Lock）
- **特点**：开销小，加锁快；不会出现死锁；锁定粒度大，发生锁冲突的概率最高，并发度最低
- **适用场景**：MyISAM存储引擎
- **类型**：
  - 表共享读锁（Table Read Lock）
  - 表独占写锁（Table Write Lock）

#### 1.2 行级锁（Row Lock）
- **特点**：开销大，加锁慢；会出现死锁；锁定粒度最小，发生锁冲突的概率最低，并发度也最高
- **适用场景**：InnoDB存储引擎
- **类型**：
  - 记录锁（Record Lock）
  - 间隙锁（Gap Lock）
  - 临键锁（Next-Key Lock）

#### 1.3 页级锁（Page Lock）
- **特点**：介于表级锁和行级锁之间
- **适用场景**：BDB存储引擎

### 2. 按锁的类型分类

#### 2.1 共享锁（S锁，Shared Lock）
- **又称**：读锁（Read Lock）
- **特点**：多个事务可以同时持有共享锁，但不能修改数据
- **SQL语法**：
```sql
SELECT * FROM table WHERE ... LOCK IN SHARE MODE;
```

#### 2.2 排他锁（X锁，Exclusive Lock）
- **又称**：写锁（Write Lock）
- **特点**：只有一个事务可以持有排他锁，其他事务不能读取（加锁读）也不能修改
- **SQL语法**：
```sql
SELECT * FROM table WHERE ... FOR UPDATE;
UPDATE/DELETE/INSERT 语句自动加排他锁
```

### 3. 按锁的思想分类

#### 3.1 乐观锁
- **实现方式**：通过版本号（version）或时间戳实现
- **特点**：假设不会发生冲突，只在更新时检查是否被修改
- **适用场景**：读多写少的场景
- **示例**：
```sql
-- 查询时获取版本号
SELECT id, balance, version FROM account WHERE id = 1;

-- 更新时检查版本号
UPDATE account SET balance = 1000, version = version + 1 
WHERE id = 1 AND version = 10;
```

#### 3.2 悲观锁
- **实现方式**：通过数据库的锁机制实现（FOR UPDATE）
- **特点**：假设会发生冲突，每次读取数据时都加锁
- **适用场景**：写多读少的场景

## 二、InnoDB行锁详解

### 1. 记录锁（Record Lock）
- **定义**：锁定单个行记录
- **触发条件**：精确匹配索引（唯一索引或主键）
- **示例**：
```sql
-- 锁定id=1的记录
SELECT * FROM account WHERE id = 1 FOR UPDATE;
```

### 2. 间隙锁（Gap Lock）
- **定义**：锁定索引记录之间的间隙，防止其他事务在间隙中插入数据
- **目的**：解决幻读问题
- **触发条件**：范围查询（BETWEEN、>、< 等）
- **示例**：
```sql
-- 假设当前有记录：age = 10, 20, 30, 50
-- 执行以下查询会产生间隙锁
SELECT * FROM user WHERE age BETWEEN 20 AND 30 FOR UPDATE;

-- 锁定的间隙：(10, 20), (20, 30), (30, 50)
-- 此时其他事务无法在这些间隙中插入数据
```

### 3. 临键锁（Next-Key Lock）
- **定义**：记录锁 + 间隙锁的组合
- **特点**：锁定一个范围，并且锁定记录本身
- **默认行为**：InnoDB在RR隔离级别下的默认行锁算法
- **锁定范围**：(左开右闭]
- **示例**：
```sql
-- 假设索引值：10, 20, 30, 50
-- 执行：SELECT * FROM user WHERE age >= 20 AND age < 40 FOR UPDATE;
-- 产生的Next-Key Lock：(10, 20], (20, 30], (30, 50)
```

## 三、锁的兼容性

| 当前锁\请求锁 | 共享锁（S） | 排他锁（X） |
|--------------|------------|------------|
| 共享锁（S）   | 兼容       | 冲突       |
| 排他锁（X）   | 冲突       | 冲突       |

## 四、死锁

### 1. 死锁的定义
两个或多个事务互相持有对方需要的锁，造成循环等待。

### 2. 死锁示例
```sql
-- 事务1
BEGIN;
SELECT * FROM account WHERE id = 1 FOR UPDATE;  -- 锁定账户1
-- 等待1秒
SELECT * FROM account WHERE id = 2 FOR UPDATE;  -- 尝试锁定账户2（等待）

-- 事务2
BEGIN;
SELECT * FROM account WHERE id = 2 FOR UPDATE;  -- 锁定账户2
-- 等待1秒
SELECT * FROM account WHERE id = 1 FOR UPDATE;  -- 尝试锁定账户1（等待）

-- 结果：死锁！MySQL会自动检测并回滚其中一个事务
```

### 3. 避免死锁的方法
1. **按相同顺序访问资源**：所有事务都按照相同的顺序锁定资源
2. **缩小事务范围**：尽量减少事务持有锁的时间
3. **降低隔离级别**：如果业务允许，可以降低到RC级别
4. **添加合理的索引**：避免全表扫描导致的大范围锁定
5. **使用乐观锁**：在适合的场景下使用乐观锁代替悲观锁

### 4. 查看死锁信息
```sql
-- 查看最近一次死锁信息
SHOW ENGINE INNODB STATUS;

-- 查看当前锁等待情况
SELECT * FROM information_schema.innodb_locks;
SELECT * FROM information_schema.innodb_lock_waits;
```

## 五、实战建议

### 1. 选择合适的锁
- **读多写少**：使用乐观锁或共享锁
- **写多读少**：使用排他锁
- **需要防止幻读**：使用FOR UPDATE（会产生间隙锁）

### 2. 优化锁的使用
- **尽量使用索引**：避免锁定过多行
- **缩短事务时间**：减少锁持有时间
- **避免大事务**：拆分成小事务
- **合理设置超时时间**：`innodb_lock_wait_timeout`

### 3. 监控锁的情况
```sql
-- 查看当前事务
SELECT * FROM information_schema.innodb_trx;

-- 查看锁等待
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

## 六、代码示例位置

- **行锁演示**：`RowLockService.java`
- **间隙锁演示**：`GapLockService.java`
- **死锁演示**：`RowLockService.deadlockDemo1/2()`
