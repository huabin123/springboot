# Redo Log 和 Undo Log 详解

## 一、日志系统概述

MySQL InnoDB存储引擎使用日志来保证事务的持久性（Durability）和原子性（Atomicity）。

### 1.1 核心日志类型

| 日志类型 | 作用 | 实现的特性 |
|---------|------|-----------|
| Redo Log | 重做日志，记录数据页的物理修改 | 持久性（Durability） |
| Undo Log | 回滚日志，记录数据的逻辑修改 | 原子性（Atomicity）、MVCC |
| Binlog | 二进制日志，记录所有DDL和DML语句 | 主从复制、数据恢复 |

## 二、Redo Log（重做日志）

### 2.1 什么是Redo Log

**定义**：记录数据页的物理修改，用于在数据库崩溃后恢复未刷盘的数据。

**作用**：
- 保证事务的持久性（Durability）
- 实现崩溃恢复（Crash Recovery）
- 提高性能（WAL机制）

### 2.2 为什么需要Redo Log

**问题**：如果每次修改数据都直接写入磁盘，性能会很差（随机I/O）。

**解决方案**：
1. 先将修改写入内存（Buffer Pool）
2. 同时将修改记录到Redo Log（顺序I/O，速度快）
3. 后台异步将内存中的脏页刷入磁盘

**优势**：
- 顺序I/O比随机I/O快得多
- 批量刷盘，提高效率
- 即使数据库崩溃，也能通过Redo Log恢复数据

### 2.3 Redo Log的结构

#### 物理文件
```
ib_logfile0  -- 默认48MB
ib_logfile1  -- 默认48MB
```

#### 循环写入
```
+------------------+
|   ib_logfile0    |
|                  |
|   write pos -->  |  当前写入位置
|                  |
|   checkpoint --> |  已刷盘位置
+------------------+
|   ib_logfile1    |
|                  |
+------------------+
```

- **write pos**：当前写入位置
- **checkpoint**：已刷盘的位置
- **可用空间**：write pos 到 checkpoint 之间的空间
- **写满处理**：当write pos追上checkpoint时，需要等待刷盘

### 2.4 WAL（Write-Ahead Logging）机制

**核心思想**：先写日志，再写磁盘。

**流程**：
```
1. 事务修改数据
   ↓
2. 修改Buffer Pool中的数据页（内存）
   ↓
3. 将修改记录到Redo Log Buffer（内存）
   ↓
4. 事务提交时，将Redo Log Buffer写入Redo Log文件（磁盘）
   ↓
5. 后台线程异步将脏页刷入磁盘
```

### 2.5 Redo Log的刷盘策略

通过参数`innodb_flush_log_at_trx_commit`控制：

| 值 | 行为 | 性能 | 安全性 |
|----|------|------|--------|
| 0 | 每秒刷盘一次 | 最高 | 最低（可能丢失1秒数据） |
| 1 | 每次事务提交都刷盘 | 最低 | 最高（推荐） |
| 2 | 每次提交写入OS缓存，每秒刷盘 | 中等 | 中等 |

**推荐配置**：
```sql
SET GLOBAL innodb_flush_log_at_trx_commit = 1;  -- 生产环境推荐
```

### 2.6 Redo Log的两阶段提交

**目的**：保证Redo Log和Binlog的一致性。

**流程**：
```
1. Prepare阶段：写入Redo Log，标记为prepare状态
   ↓
2. 写入Binlog
   ↓
3. Commit阶段：将Redo Log标记为commit状态
```

**崩溃恢复**：
- 如果Redo Log处于prepare状态，但Binlog已写入：提交事务
- 如果Redo Log处于prepare状态，但Binlog未写入：回滚事务

## 三、Undo Log（回滚日志）

### 3.1 什么是Undo Log

**定义**：记录数据修改前的值，用于事务回滚和MVCC。

**作用**：
- 事务回滚（保证原子性）
- MVCC（多版本并发控制）
- 崩溃恢复

### 3.2 Undo Log的类型

#### Insert Undo Log
- **记录内容**：插入记录的主键
- **回滚操作**：删除该记录
- **特点**：事务提交后可以立即删除

#### Update Undo Log
- **记录内容**：修改前的旧值
- **回滚操作**：恢复旧值
- **特点**：需要保留用于MVCC，不能立即删除

### 3.3 Undo Log的存储

**位置**：
- MySQL 5.6之前：存储在ibdata1（系统表空间）
- MySQL 5.6之后：可以存储在独立的undo表空间

**配置**：
```sql
-- 查看undo表空间数量
SHOW VARIABLES LIKE 'innodb_undo_tablespaces';

-- 设置undo表空间数量（需要重启）
SET GLOBAL innodb_undo_tablespaces = 2;
```

### 3.4 Undo Log的版本链

**结构**：
```
当前版本：id=1, name='张三', trx_id=100
         ↓ (DB_ROLL_PTR)
历史版本1：id=1, name='李四', trx_id=90
         ↓ (DB_ROLL_PTR)
历史版本2：id=1, name='王五', trx_id=80
```

**作用**：
- 事务回滚时，沿着版本链恢复数据
- MVCC读取时，根据Read View找到可见的版本

### 3.5 Undo Log的清理

**清理时机**：
- Insert Undo Log：事务提交后立即删除
- Update Undo Log：当没有事务需要读取该版本时删除

**Purge线程**：
- 后台线程，负责清理不再需要的Undo Log
- 避免Undo Log无限增长

**长事务的影响**：
- 长事务会导致大量Undo Log堆积
- 占用存储空间
- 影响性能

## 四、Redo Log vs Undo Log

| 对比项 | Redo Log | Undo Log |
|--------|----------|----------|
| 作用 | 重做，恢复未刷盘的数据 | 回滚，撤销未提交的修改 |
| 记录内容 | 物理修改（数据页的变化） | 逻辑修改（修改前的值） |
| 实现特性 | 持久性（Durability） | 原子性（Atomicity）、MVCC |
| 写入时机 | 事务执行过程中 | 事务执行过程中 |
| 清理时机 | 循环覆盖 | 事务提交后（或无事务需要时） |
| 存储位置 | ib_logfile0/1 | ibdata1或独立表空间 |

## 五、Binlog（二进制日志）

### 5.1 什么是Binlog

**定义**：MySQL Server层的日志，记录所有DDL和DML语句。

**作用**：
- 主从复制
- 数据恢复
- 审计

### 5.2 Binlog vs Redo Log

| 对比项 | Binlog | Redo Log |
|--------|--------|----------|
| 层次 | MySQL Server层 | InnoDB存储引擎层 |
| 作用 | 主从复制、数据恢复 | 崩溃恢复 |
| 记录内容 | 逻辑日志（SQL语句） | 物理日志（数据页修改） |
| 写入方式 | 追加写入 | 循环写入 |
| 文件大小 | 可配置，写满后创建新文件 | 固定大小，循环使用 |

### 5.3 Binlog的格式

#### STATEMENT
- **记录内容**：SQL语句
- **优点**：日志量小
- **缺点**：可能导致主从不一致（如NOW()、UUID()等函数）

#### ROW（推荐）
- **记录内容**：每行数据的变化
- **优点**：保证主从一致
- **缺点**：日志量大

#### MIXED
- **记录内容**：混合模式，自动选择
- **优点**：兼顾日志量和一致性

**配置**：
```sql
-- 查看binlog格式
SHOW VARIABLES LIKE 'binlog_format';

-- 设置binlog格式
SET GLOBAL binlog_format = 'ROW';
```

## 六、事务提交的完整流程

```
1. 执行SQL语句
   ↓
2. 修改Buffer Pool中的数据页（标记为脏页）
   ↓
3. 写入Undo Log（用于回滚）
   ↓
4. 写入Redo Log Buffer
   ↓
5. 事务提交
   ↓
6. Redo Log进入prepare状态
   ↓
7. 写入Binlog
   ↓
8. Redo Log进入commit状态
   ↓
9. 事务提交成功
   ↓
10. 后台线程异步将脏页刷入磁盘
```

## 七、崩溃恢复流程

### 7.1 恢复流程
```
1. 数据库启动
   ↓
2. 读取Redo Log
   ↓
3. 重做已提交但未刷盘的事务（Redo）
   ↓
4. 读取Undo Log
   ↓
5. 回滚未提交的事务（Undo）
   ↓
6. 数据库恢复完成
```

### 7.2 两阶段提交的恢复

| Redo Log状态 | Binlog状态 | 恢复操作 |
|-------------|-----------|---------|
| prepare | 已写入 | 提交事务 |
| prepare | 未写入 | 回滚事务 |
| commit | - | 提交事务 |

## 八、性能优化建议

### 8.1 Redo Log优化
```sql
-- 增大Redo Log大小（减少刷盘频率）
SET GLOBAL innodb_log_file_size = 512M;

-- 设置Redo Log文件数量
SET GLOBAL innodb_log_files_in_group = 3;

-- 刷盘策略（生产环境推荐1）
SET GLOBAL innodb_flush_log_at_trx_commit = 1;
```

### 8.2 Undo Log优化
```sql
-- 避免长事务
-- 及时提交事务
-- 定期清理不需要的Undo Log

-- 查看undo log使用情况
SHOW ENGINE INNODB STATUS;
```

### 8.3 Binlog优化
```sql
-- 设置binlog格式为ROW
SET GLOBAL binlog_format = 'ROW';

-- 设置binlog刷盘策略
SET GLOBAL sync_binlog = 1;  -- 每次提交都刷盘（最安全）

-- 设置binlog过期时间
SET GLOBAL expire_logs_days = 7;  -- 保留7天
```

## 九、监控和排查

### 9.1 查看日志状态
```sql
-- 查看Redo Log状态
SHOW ENGINE INNODB STATUS;

-- 查看Binlog状态
SHOW BINARY LOGS;
SHOW MASTER STATUS;

-- 查看Binlog内容
SHOW BINLOG EVENTS IN 'mysql-bin.000001';
```

### 9.2 常见问题排查

#### Redo Log写满
```sql
-- 现象：数据库hang住，无法写入
-- 原因：脏页刷盘速度跟不上
-- 解决：增大Redo Log大小，调整刷盘策略

SET GLOBAL innodb_log_file_size = 1G;
SET GLOBAL innodb_io_capacity = 2000;  -- 提高刷盘速度
```

#### Undo Log堆积
```sql
-- 现象：ibdata1文件持续增长
-- 原因：存在长事务
-- 解决：找出并终止长事务

-- 查找长事务
SELECT * FROM information_schema.innodb_trx 
WHERE TIME_TO_SEC(TIMEDIFF(NOW(), trx_started)) > 60;

-- 终止长事务
KILL <trx_mysql_thread_id>;
```

## 十、总结

### 10.1 核心要点
- **Redo Log**：保证持久性，记录物理修改，用于崩溃恢复
- **Undo Log**：保证原子性，记录逻辑修改，用于回滚和MVCC
- **Binlog**：用于主从复制和数据恢复
- **两阶段提交**：保证Redo Log和Binlog的一致性

### 10.2 最佳实践
1. 生产环境设置`innodb_flush_log_at_trx_commit = 1`
2. 避免长事务，及时提交
3. 合理配置Redo Log大小
4. 定期清理Binlog
5. 监控日志使用情况
