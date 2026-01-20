# MVCC（多版本并发控制）详解

## 一、什么是MVCC

MVCC（Multi-Version Concurrency Control，多版本并发控制）是MySQL InnoDB存储引擎实现高并发的核心机制。

### 1.1 MVCC的作用
- **提高并发性能**：读不加锁，读写不冲突
- **实现一致性读**：通过快照读实现可重复读
- **解决幻读问题**：配合间隙锁解决幻读

### 1.2 MVCC的优势
- 读操作不加锁，提高并发性能
- 写操作不阻塞读操作
- 解决脏读、不可重复读问题

## 二、MVCC的实现原理

### 2.1 隐藏字段

InnoDB为每行数据添加了三个隐藏字段：

| 字段名 | 长度 | 说明 |
|--------|------|------|
| DB_TRX_ID | 6字节 | 最后修改该行的事务ID |
| DB_ROLL_PTR | 7字节 | 回滚指针，指向undo log中的历史版本 |
| DB_ROW_ID | 6字节 | 隐藏的主键（如果表没有主键） |

### 2.2 Undo Log

**作用**：
1. 事务回滚时恢复数据
2. MVCC读取历史版本数据

**版本链**：
- 每次修改数据时，都会在undo log中记录一条历史版本
- 通过DB_ROLL_PTR形成一个版本链
- 版本链的头节点是最新数据，尾节点是最老数据

**示例**：
```
当前数据：balance = 1000, trx_id = 100
         ↓ (DB_ROLL_PTR)
历史版本1：balance = 800, trx_id = 90
         ↓ (DB_ROLL_PTR)
历史版本2：balance = 500, trx_id = 80
```

### 2.3 Read View（一致性视图）

**定义**：事务在执行快照读时创建的一致性视图，用于判断哪些版本的数据对当前事务可见。

**核心字段**：
- `m_ids`：当前活跃事务ID列表
- `min_trx_id`：最小活跃事务ID
- `max_trx_id`：下一个要分配的事务ID（最大事务ID + 1）
- `creator_trx_id`：创建该Read View的事务ID

**可见性判断规则**：

对于数据版本的`trx_id`：

1. **trx_id < min_trx_id**：数据版本在Read View创建前已提交，可见
2. **trx_id >= max_trx_id**：数据版本在Read View创建后才开始，不可见
3. **min_trx_id <= trx_id < max_trx_id**：
   - 如果`trx_id`在`m_ids`中：说明事务还未提交，不可见
   - 如果`trx_id`不在`m_ids`中：说明事务已提交，可见
4. **trx_id == creator_trx_id**：当前事务自己修改的数据，可见

### 2.4 不同隔离级别下的Read View

#### READ COMMITTED（读已提交）
- **特点**：每次SELECT都创建新的Read View
- **结果**：可以读取到其他事务已提交的修改（不可重复读）

#### REPEATABLE READ（可重复读）
- **特点**：事务开始时创建Read View，整个事务期间使用同一个
- **结果**：多次查询结果一致（可重复读）

## 三、快照读 vs 当前读

### 3.1 快照读（Snapshot Read）

**定义**：读取的是数据的快照版本（历史版本），不加锁。

**SQL语句**：
```sql
SELECT * FROM table WHERE ...;
```

**特点**：
- 通过MVCC实现
- 不加锁，不阻塞其他事务
- 读取的是Read View创建时的数据版本

### 3.2 当前读（Current Read）

**定义**：读取的是数据的最新版本，并且会加锁。

**SQL语句**：
```sql
SELECT * FROM table WHERE ... FOR UPDATE;          -- 排他锁
SELECT * FROM table WHERE ... LOCK IN SHARE MODE;  -- 共享锁
INSERT INTO table ...;                              -- 排他锁
UPDATE table SET ...;                               -- 排他锁
DELETE FROM table WHERE ...;                        -- 排他锁
```

**特点**：
- 读取最新数据
- 会加锁（记录锁、间隙锁、临键锁）
- 阻塞其他事务的写操作

## 四、MVCC解决的问题

### 4.1 脏读（Dirty Read）
- **问题**：读取到其他事务未提交的数据
- **解决**：通过Read View的可见性判断，只读取已提交的数据

### 4.2 不可重复读（Non-Repeatable Read）
- **问题**：同一事务中多次读取同一数据，结果不一致
- **解决**：在RR隔离级别下，使用同一个Read View，保证可重复读

### 4.3 幻读（Phantom Read）
- **问题**：同一事务中多次查询，结果集的行数不一致
- **解决**：MVCC + 间隙锁（Next-Key Lock）

## 五、MVCC的局限性

### 5.1 只在RC和RR隔离级别下工作
- READ UNCOMMITTED：不需要MVCC
- SERIALIZABLE：使用锁机制，不使用MVCC

### 5.2 无法完全解决幻读
- **快照读**：通过MVCC解决
- **当前读**：需要配合间隙锁解决

### 5.3 Undo Log的空间占用
- 长事务会导致大量undo log堆积
- 影响性能和存储空间

## 六、实战示例

### 6.1 演示不可重复读（RC级别）
```sql
-- 事务1（READ COMMITTED）
BEGIN;
SELECT balance FROM account WHERE id = 1;  -- 结果：1000
-- 等待5秒
SELECT balance FROM account WHERE id = 1;  -- 结果：2000（读取到事务2的修改）
COMMIT;

-- 事务2
BEGIN;
UPDATE account SET balance = 2000 WHERE id = 1;
COMMIT;
```

### 6.2 演示可重复读（RR级别）
```sql
-- 事务1（REPEATABLE READ）
BEGIN;
SELECT balance FROM account WHERE id = 1;  -- 结果：1000
-- 等待5秒
SELECT balance FROM account WHERE id = 1;  -- 结果：1000（可重复读）
COMMIT;

-- 事务2
BEGIN;
UPDATE account SET balance = 2000 WHERE id = 1;
COMMIT;
```

### 6.3 演示当前读
```sql
-- 事务1（REPEATABLE READ）
BEGIN;
SELECT balance FROM account WHERE id = 1;              -- 快照读：1000
SELECT balance FROM account WHERE id = 1 FOR UPDATE;  -- 当前读：2000
COMMIT;
```

## 七、优化建议

### 7.1 避免长事务
- 长事务会导致大量undo log堆积
- 占用存储空间，影响性能
- 可能导致锁等待

### 7.2 合理选择隔离级别
- **RC级别**：性能更好，但可能出现不可重复读
- **RR级别**：数据一致性更好，但性能稍差

### 7.3 及时提交事务
```sql
-- 不好的做法
BEGIN;
SELECT * FROM account WHERE id = 1;
-- 长时间业务处理...
COMMIT;

-- 好的做法
SELECT * FROM account WHERE id = 1;  -- 快照读，不开启事务
-- 业务处理...
BEGIN;
UPDATE account SET balance = 1000 WHERE id = 1;
COMMIT;  -- 立即提交
```

## 八、代码示例位置

- **MVCC演示**：`MvccService.java`
- **READ COMMITTED演示**：`readCommittedDemo()`
- **REPEATABLE READ演示**：`repeatableReadDemo()`
- **当前读vs快照读**：`currentReadVsSnapshotRead()`

## 九、相关配置

```sql
-- 查看当前隔离级别
SELECT @@transaction_isolation;

-- 设置隔离级别
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- 查看undo log使用情况
SHOW ENGINE INNODB STATUS;
```
