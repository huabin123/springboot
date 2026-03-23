# 慢SQL排查与EXPLAIN详解

## 一、快速结论

**慢SQL排查的核心：看访问路径，而不是背优化技巧。**

```
排查顺序（5步法）：
1. EXPLAIN看type → 是否全表扫描（ALL）
2. 看key → 索引是否生效
3. 看rows → 扫描行数是否合理
4. 看Extra → 是否有filesort、temporary
5. 看实际执行时间 → 是否符合预期

90%的慢SQL都是：索引没用上或访问路径不对
```

## 二、EXPLAIN核心字段详解

### 2.1 完整示例

```sql
EXPLAIN SELECT * FROM user WHERE age = 25;

+----+-------------+-------+------+---------------+----------+---------+-------+------+-------+
| id | select_type | table | type | possible_keys | key      | key_len | ref   | rows | Extra |
+----+-------------+-------+------+---------------+----------+---------+-------+------+-------+
|  1 | SIMPLE      | user  | ref  | idx_age       | idx_age  | 5       | const |  100 | NULL  |
+----+-------------+-------+------+---------------+----------+---------+-------+------+-------+
```

### 2.2 type（访问类型）- 最重要！

```
性能从好到差：
system > const > eq_ref > ref > range > index > ALL

记忆口诀：
系统常量等于引用，范围索引全表扫
```

#### type = ALL（全表扫描）❌

```sql
EXPLAIN SELECT * FROM user WHERE name = '张三';

type: ALL  ← 全表扫描，最慢！
rows: 1000000  ← 扫描100万行

原因：name字段没有索引

解决：
CREATE INDEX idx_name ON user(name);
```

#### type = index（索引扫描）⚠️

```sql
EXPLAIN SELECT id FROM user;

type: index  ← 扫描整个索引树
rows: 1000000

原因：虽然用了索引，但扫描了整个索引

优化：
-- 如果只是统计数量
SELECT COUNT(*) FROM user;  -- MySQL会优化
```

#### type = range（范围扫描）✅

```sql
EXPLAIN SELECT * FROM user WHERE age BETWEEN 20 AND 30;

type: range  ← 范围扫描，较好
rows: 10000

触发条件：>, <, >=, <=, BETWEEN, IN, LIKE 'abc%'
```

#### type = ref（非唯一索引等值查询）✅

```sql
EXPLAIN SELECT * FROM user WHERE age = 25;

type: ref  ← 使用非唯一索引，好
rows: 100

触发条件：普通索引的等值查询
```

#### type = eq_ref（唯一索引等值查询）✅✅

```sql
EXPLAIN SELECT * FROM user u 
JOIN order o ON u.id = o.user_id;

type: eq_ref  ← JOIN时使用主键或唯一索引，很好
rows: 1

触发条件：JOIN时使用主键或唯一索引
```

#### type = const（主键或唯一索引等值查询）✅✅✅

```sql
EXPLAIN SELECT * FROM user WHERE id = 1;

type: const  ← 主键等值查询，最快
rows: 1

触发条件：主键或唯一索引的等值查询
```

### 2.3 key（实际使用的索引）

```sql
-- 示例1：索引生效
EXPLAIN SELECT * FROM user WHERE age = 25;
key: idx_age  ✅ 使用了索引

-- 示例2：索引失效
EXPLAIN SELECT * FROM user WHERE age + 1 = 26;
key: NULL  ❌ 索引失效（字段上有函数）

-- 示例3：索引选择错误
EXPLAIN SELECT * FROM user WHERE age = 25 AND name = '张三';
possible_keys: idx_age, idx_name
key: idx_age  ⚠️ 优化器选错了索引

-- 强制使用索引
SELECT * FROM user FORCE INDEX(idx_name) 
WHERE age = 25 AND name = '张三';
```

### 2.4 rows（扫描行数）

```sql
-- 示例1：扫描行数合理
EXPLAIN SELECT * FROM user WHERE id = 1;
rows: 1  ✅ 很好

-- 示例2：扫描行数过多
EXPLAIN SELECT * FROM user WHERE age > 20;
rows: 900000  ❌ 扫描90万行，太多了

-- 示例3：rows不准确
-- MySQL的rows是估算值，可能不准
-- 实际扫描行数看：Handler_read_rnd_next
SHOW SESSION STATUS LIKE 'Handler_read%';
```

### 2.5 Extra（额外信息）- 重点关注！

#### Using filesort（文件排序）❌

```sql
EXPLAIN SELECT * FROM user ORDER BY age;

Extra: Using filesort  ❌ 需要额外排序，慢！

原因：
1. ORDER BY的字段没有索引
2. 或者索引用不上（如WHERE和ORDER BY字段不一致）

解决：
CREATE INDEX idx_age ON user(age);
```

**filesort的两种算法**：
```
1. 双路排序（Two-Pass）：
   - 先读取排序字段和主键
   - 排序后再回表读取其他字段
   - 两次IO，慢

2. 单路排序（Single-Pass）：
   - 一次性读取所有字段
   - 在内存中排序
   - 如果内存不够，会用临时文件
   
MySQL会根据sort_buffer_size自动选择
```

#### Using temporary（使用临时表）❌

```sql
EXPLAIN SELECT age, COUNT(*) FROM user GROUP BY age;

Extra: Using temporary  ❌ 使用临时表，慢！

原因：
1. GROUP BY的字段没有索引
2. 或者索引用不上

解决：
CREATE INDEX idx_age ON user(age);
```

#### Using index（覆盖索引）✅✅✅

```sql
EXPLAIN SELECT id, age FROM user WHERE age = 25;

Extra: Using index  ✅ 覆盖索引，不需要回表，最快！

原因：
- 查询的字段都在索引中
- 不需要回表读取完整行

优化技巧：
-- 如果经常查询 (age, name)
CREATE INDEX idx_age_name ON user(age, name);
SELECT age, name FROM user WHERE age = 25;  -- 覆盖索引
```

#### Using where（使用WHERE过滤）

```sql
EXPLAIN SELECT * FROM user WHERE age = 25 AND name = '张三';

Extra: Using where  ← 使用WHERE过滤，正常

说明：
- 在存储引擎层过滤了部分数据
- 在Server层又过滤了一次
```

#### Using index condition（索引下推）✅

```sql
-- MySQL 5.6+支持索引下推（ICP）
EXPLAIN SELECT * FROM user WHERE age > 20 AND name LIKE '张%';

Extra: Using index condition  ✅ 索引下推，好！

说明：
- 把部分WHERE条件下推到存储引擎层
- 减少回表次数
```

## 三、常见慢SQL场景

### 3.1 场景1：索引失效

#### 1. 字段上有函数或运算

```sql
-- ❌ 索引失效
SELECT * FROM user WHERE YEAR(create_time) = 2024;
SELECT * FROM user WHERE age + 1 = 26;

-- ✅ 正确写法
SELECT * FROM user WHERE create_time >= '2024-01-01' 
  AND create_time < '2025-01-01';
SELECT * FROM user WHERE age = 25;
```

#### 2. 隐式类型转换

```sql
-- 表结构
CREATE TABLE user (
    id BIGINT PRIMARY KEY,
    phone VARCHAR(20),
    INDEX idx_phone (phone)
);

-- ❌ 索引失效（字符串字段用数字查询）
SELECT * FROM user WHERE phone = 13812345678;
-- MySQL会把phone转成数字：CAST(phone AS UNSIGNED)
-- 相当于：WHERE CAST(phone AS UNSIGNED) = 13812345678
-- 字段上有函数，索引失效！

-- ✅ 正确写法
SELECT * FROM user WHERE phone = '13812345678';
```

#### 3. LIKE以%开头

```sql
-- ❌ 索引失效
SELECT * FROM user WHERE name LIKE '%张三';
SELECT * FROM user WHERE name LIKE '%张三%';

-- ✅ 索引生效
SELECT * FROM user WHERE name LIKE '张三%';

-- 如果必须用%开头，用全文索引
CREATE FULLTEXT INDEX idx_name_fulltext ON user(name);
SELECT * FROM user WHERE MATCH(name) AGAINST('张三');
```

#### 4. OR条件有字段没索引

```sql
-- ❌ 索引失效
SELECT * FROM user WHERE age = 25 OR name = '张三';
-- 如果name没有索引，整个查询都不会用索引

-- ✅ 正确写法
-- 方案1：给name加索引
CREATE INDEX idx_name ON user(name);

-- 方案2：改成UNION
SELECT * FROM user WHERE age = 25
UNION
SELECT * FROM user WHERE name = '张三';
```

#### 5. !=、<>、NOT IN

```sql
-- ❌ 可能不走索引
SELECT * FROM user WHERE age != 25;
SELECT * FROM user WHERE age <> 25;
SELECT * FROM user WHERE age NOT IN (25, 26, 27);

-- ✅ 改成范围查询
SELECT * FROM user WHERE age < 25 OR age > 25;
```

### 3.2 场景2：联合索引最左前缀失效

```sql
-- 索引：INDEX idx_abc (a, b, c)

-- ✅ 使用索引
WHERE a = 1
WHERE a = 1 AND b = 2
WHERE a = 1 AND b = 2 AND c = 3

-- ❌ 不使用索引
WHERE b = 2
WHERE c = 3
WHERE b = 2 AND c = 3

-- ⚠️ 部分使用索引
WHERE a = 1 AND c = 3  -- 只用到a
WHERE a = 1 AND b > 2 AND c = 3  -- 只用到a和b，c用不上
```

### 3.3 场景3：深分页

```sql
-- ❌ 慢查询
SELECT * FROM user ORDER BY id LIMIT 1000000, 20;

-- 问题：
-- 1. MySQL需要扫描1000020行
-- 2. 然后丢弃前1000000行
-- 3. 只返回最后20行

-- ✅ 优化方案1：子查询
SELECT * FROM user 
WHERE id >= (SELECT id FROM user ORDER BY id LIMIT 1000000, 1)
LIMIT 20;

-- ✅ 优化方案2：记录上次最大ID
SELECT * FROM user WHERE id > 上次最大ID ORDER BY id LIMIT 20;

-- ✅ 优化方案3：延迟关联
SELECT * FROM user u
JOIN (SELECT id FROM user ORDER BY id LIMIT 1000000, 20) t
ON u.id = t.id;
```

### 3.4 场景4：JOIN导致慢查询

```sql
-- ❌ 慢查询
SELECT * FROM user u
LEFT JOIN order o ON u.name = o.user_name;

-- 问题：
-- 1. JOIN字段没有索引
-- 2. 或者JOIN字段类型不一致

-- ✅ 优化
-- 1. 给JOIN字段加索引
CREATE INDEX idx_user_name ON order(user_name);

-- 2. 确保JOIN字段类型一致
-- user.name: VARCHAR(50)
-- order.user_name: VARCHAR(50)  ← 类型必须一致
```

### 3.5 场景5：COUNT(*)慢

```sql
-- ❌ 慢查询
SELECT COUNT(*) FROM user WHERE age > 20;

-- 问题：
-- 1. InnoDB需要扫描所有符合条件的行
-- 2. 因为MVCC，不同事务看到的行数不同

-- ✅ 优化方案1：使用覆盖索引
CREATE INDEX idx_age ON user(age);
SELECT COUNT(*) FROM user WHERE age > 20;  -- 走索引

-- ✅ 优化方案2：近似值
EXPLAIN SELECT * FROM user WHERE age > 20;
-- 看rows字段，作为近似值

-- ✅ 优化方案3：维护计数表
CREATE TABLE user_count (
    age_range VARCHAR(20),
    count INT
);
-- 定期更新计数表
```

## 四、慢SQL排查实战

### 4.1 排查步骤

```sql
-- 步骤1：开启慢查询日志
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 1;  -- 超过1秒记录

-- 步骤2：查看慢查询日志
-- Linux: /var/lib/mysql/slow.log
-- 或者用pt-query-digest分析

-- 步骤3：EXPLAIN分析
EXPLAIN SELECT * FROM user WHERE age = 25;

-- 步骤4：查看实际执行时间
SET profiling = ON;
SELECT * FROM user WHERE age = 25;
SHOW PROFILES;
SHOW PROFILE FOR QUERY 1;

-- 步骤5：查看索引使用情况
SHOW INDEX FROM user;
SHOW STATUS LIKE 'Handler_read%';
```

### 4.2 实战案例

#### 案例1：索引失效导致慢查询

```sql
-- 慢查询
SELECT * FROM order WHERE DATE(create_time) = '2024-01-01';

-- EXPLAIN分析
type: ALL  ← 全表扫描
rows: 1000000
Extra: Using where

-- 问题：DATE()函数导致索引失效

-- 优化
SELECT * FROM order 
WHERE create_time >= '2024-01-01 00:00:00' 
  AND create_time < '2024-01-02 00:00:00';

-- EXPLAIN优化后
type: range
rows: 1000
key: idx_create_time
```

#### 案例2：联合索引顺序不对

```sql
-- 表结构
CREATE TABLE order (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    status VARCHAR(20),
    create_time DATETIME,
    INDEX idx_status_user (status, user_id)
);

-- 慢查询
SELECT * FROM order WHERE user_id = 1001;

-- EXPLAIN分析
type: ALL  ← 全表扫描
key: NULL  ← 索引没用上

-- 问题：索引是(status, user_id)，查询跳过了status

-- 优化方案1：调整索引顺序
DROP INDEX idx_status_user ON order;
CREATE INDEX idx_user_status ON order(user_id, status);

-- 优化方案2：添加单独的索引
CREATE INDEX idx_user_id ON order(user_id);
```

#### 案例3：ORDER BY导致filesort

```sql
-- 慢查询
SELECT * FROM order 
WHERE user_id = 1001 
ORDER BY create_time DESC 
LIMIT 10;

-- EXPLAIN分析
type: ref
key: idx_user_id
Extra: Using filesort  ← 需要排序

-- 问题：索引只有user_id，ORDER BY的create_time用不上

-- 优化：创建联合索引
CREATE INDEX idx_user_time ON order(user_id, create_time);

-- EXPLAIN优化后
type: ref
key: idx_user_time
Extra: Using index  ← 不需要排序了
```

## 五、EXPLAIN的完整字段说明

### 5.1 id（查询序号）

```sql
-- 示例：子查询
EXPLAIN SELECT * FROM user 
WHERE id IN (SELECT user_id FROM order WHERE status = 'PAID');

id: 1  ← 外层查询
id: 2  ← 子查询

规则：
- id相同：从上到下执行
- id不同：id大的先执行
```

### 5.2 select_type（查询类型）

```
SIMPLE：简单查询，不包含子查询或UNION
PRIMARY：最外层查询
SUBQUERY：子查询
DERIVED：派生表（FROM子句中的子查询）
UNION：UNION后面的查询
UNION RESULT：UNION的结果
```

### 5.3 key_len（索引长度）

```sql
-- 计算规则：
-- INT: 4字节
-- BIGINT: 8字节
-- VARCHAR(N): N * 字符集字节数 + 2（长度） + 1（NULL）
-- DATETIME: 5字节（MySQL 5.6+）

-- 示例
CREATE TABLE user (
    id BIGINT,  -- 8字节
    age INT,  -- 4字节
    name VARCHAR(50),  -- 50*4+2+1=203字节（utf8mb4）
    INDEX idx_age_name (age, name)
);

EXPLAIN SELECT * FROM user WHERE age = 25;
key_len: 5  ← 4(INT) + 1(NULL) = 5，只用到age

EXPLAIN SELECT * FROM user WHERE age = 25 AND name = '张三';
key_len: 208  ← 5(age) + 203(name) = 208，用到age和name
```

### 5.4 ref（索引引用）

```sql
-- const：常量
EXPLAIN SELECT * FROM user WHERE age = 25;
ref: const

-- 字段引用
EXPLAIN SELECT * FROM user u JOIN order o ON u.id = o.user_id;
ref: database.o.user_id
```

## 六、优化技巧总结

### 6.1 索引优化

```
1. 选择性高的字段建索引
   - 区分度 = COUNT(DISTINCT col) / COUNT(*)
   - 区分度 > 0.1 才值得建索引

2. 联合索引顺序
   - 高频查询字段放前面
   - 区分度高的字段放前面
   - 等值查询字段放前面，范围查询字段放后面

3. 覆盖索引
   - 查询的字段都在索引中
   - 避免回表

4. 前缀索引
   - VARCHAR字段太长，用前缀索引
   - CREATE INDEX idx_name ON user(name(10));

5. 不要过度索引
   - 索引越多，写入越慢
   - 一般一个表不超过5个索引
```

### 6.2 SQL优化

```
1. 避免SELECT *
   - 只查询需要的字段
   - 减少网络传输
   - 可能用上覆盖索引

2. 小表驱动大表
   - IN适合外表大内表小
   - EXISTS适合外表小内表大

3. 避免子查询
   - 改成JOIN
   - 或者用临时表

4. 分页优化
   - 避免深分页
   - 用子查询或延迟关联

5. 批量操作
   - INSERT多条用批量插入
   - UPDATE多条用CASE WHEN
```

## 七、面试高频问题

### Q1：EXPLAIN重点看什么？

**标准答案**：
```
重点看4个字段：

1. type：访问类型
   - ALL、index：全表扫描，需要优化
   - range、ref、eq_ref、const：正常

2. key：使用的索引
   - NULL：没用索引，需要优化
   - 有值：用了索引

3. rows：扫描行数
   - 越少越好
   - 如果很大，说明索引不够精确

4. Extra：额外信息
   - Using filesort：需要排序，考虑加索引
   - Using temporary：使用临时表，考虑加索引
   - Using index：覆盖索引，很好
```

### Q2：为什么有索引还是慢？

**标准答案**：
```
常见原因：

1. 索引失效：
   - 字段上有函数或运算
   - 隐式类型转换
   - LIKE '%abc'
   - OR条件有字段没索引

2. 索引选择错误：
   - MySQL优化器选错了索引
   - 用FORCE INDEX强制指定

3. 回表次数多：
   - 二级索引需要回表
   - 用覆盖索引优化

4. 深分页：
   - LIMIT 1000000, 20
   - 需要扫描1000020行

5. 数据量太大：
   - 即使用了索引，扫描行数还是很多
   - 考虑分库分表
```

### Q3：深分页为什么慢？

**标准答案**：
```
原因：
SELECT * FROM user ORDER BY id LIMIT 1000000, 20;

MySQL需要：
1. 扫描1000020行
2. 丢弃前1000000行
3. 只返回最后20行

优化方案：
1. 子查询：
   SELECT * FROM user 
   WHERE id >= (SELECT id FROM user LIMIT 1000000, 1)
   LIMIT 20;

2. 记录上次最大ID：
   SELECT * FROM user WHERE id > 上次最大ID LIMIT 20;

3. 延迟关联：
   SELECT * FROM user u
   JOIN (SELECT id FROM user LIMIT 1000000, 20) t
   ON u.id = t.id;
```

## 八、总结

### 核心要点

```
慢SQL排查5步法：

1. EXPLAIN看type
   - ALL、index → 全表扫描，需要优化

2. 看key
   - NULL → 索引没用上

3. 看rows
   - 太大 → 索引不够精确

4. 看Extra
   - Using filesort → 需要排序
   - Using temporary → 使用临时表

5. 看实际执行时间
   - SHOW PROFILES
```

### 记忆口诀

```
类型全表最要命，索引为空需优化
扫描行数越少越好，文件排序要避免
覆盖索引不回表，联合索引看顺序
深分页用子查询，函数字段索引废
```

**最终答案：慢SQL的本质是访问路径不对，EXPLAIN看type、key、rows、Extra四个字段，90%的问题都能发现。**
