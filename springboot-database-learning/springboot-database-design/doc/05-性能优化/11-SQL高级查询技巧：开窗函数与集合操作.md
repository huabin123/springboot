# SQL 高级查询技巧：开窗函数与集合操作

## 1. 开窗函数（Window Function）

### 1.1 什么是开窗函数

开窗函数（Window Function，也称窗口函数）是 SQL 中一类特殊的函数，它可以**在不折叠行的前提下，对当前行的"窗口范围"内的数据进行计算**。

与聚合函数（`GROUP BY`）的根本区别：

| 对比维度 | 聚合函数（GROUP BY） | 开窗函数（OVER） |
|----------|---------------------|-----------------|
| 结果行数 | 每组压缩为一行 | 每行保留，不折叠 |
| 能否同时访问明细 | ❌ 不能 | ✅ 可以 |
| 语法特征 | `GROUP BY col` | `函数名() OVER (PARTITION BY col ORDER BY col)` |

**语法结构：**

```sql
函数名() OVER (
    [PARTITION BY 分组列]   -- 定义窗口的分组范围（类似 GROUP BY，但不折叠行）
    [ORDER BY 排序列]       -- 定义窗口内的排序规则
    [ROWS/RANGE BETWEEN ... AND ...]  -- 定义行范围（滑动窗口，可选）
)
```

### 1.2 开窗函数能解决什么问题

开窗函数主要解决以下类型的问题：

1. **排名问题**：每个分组内的排名（如：每个部门薪资前 N 名）
2. **累计计算**：累计求和、累计平均（如：按日期累计销售额）
3. **取同组其他行的值**：取上一行/下一行的值（如：环比计算）
4. **分组后保留明细**：既要分组统计，又要保留每行原始数据

---

## 2. ROW_NUMBER() 与 RANK() 函数

### 2.1 三个排名函数对比

MySQL 8.0+ 提供三个常用排名函数：

| 函数 | 说明 | 并列时处理 | 序号是否连续 |
|------|------|-----------|-------------|
| `ROW_NUMBER()` | 每行唯一序号 | 并列时序号不同（按排序随机分配） | ✅ 连续 |
| `RANK()` | 标准排名 | 并列时序号相同，下一名跳过 | ❌ 不连续（1,1,3） |
| `DENSE_RANK()` | 密集排名 | 并列时序号相同，下一名不跳过 | ✅ 连续（1,1,2） |

**示例数据：**

```sql
CREATE TABLE employee (
    id         INT PRIMARY KEY,
    name       VARCHAR(50),
    department VARCHAR(50),
    salary     DECIMAL(10, 2)
);

INSERT INTO employee VALUES
(1, '张三', '技术部', 15000),
(2, '李四', '技术部', 18000),
(3, '王五', '技术部', 18000),
(4, '赵六', '技术部', 12000),
(5, '孙七', '市场部', 14000),
(6, '周八', '市场部', 16000),
(7, '吴九', '市场部', 14000);
```

**三函数对比查询：**

```sql
SELECT
    name,
    department,
    salary,
    ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC) AS row_num,
    RANK()       OVER (PARTITION BY department ORDER BY salary DESC) AS rnk,
    DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS dense_rnk
FROM employee;
```

**结果：**

| name | department | salary | row_num | rnk | dense_rnk |
|------|-----------|--------|---------|-----|-----------|
| 李四 | 技术部 | 18000 | 1 | 1 | 1 |
| 王五 | 技术部 | 18000 | 2 | 1 | 1 |
| 张三 | 技术部 | 15000 | 3 | 3 | 2 |
| 赵六 | 技术部 | 12000 | 4 | 4 | 3 |
| 周八 | 市场部 | 16000 | 1 | 1 | 1 |
| 孙七 | 市场部 | 14000 | 2 | 2 | 2 |
| 吴九 | 市场部 | 14000 | 3 | 2 | 2 |

> 注意：`RANK()` 中技术部两个 18000 并列第1，下一名直接跳到第3；`DENSE_RANK()` 则是第2。

### 2.2 实际应用场景：每个部门薪资 Top N

**需求：查询每个部门薪资排名前 2 的员工**

```sql
-- 方案一：使用 ROW_NUMBER()（每组严格取前2行，不处理并列）
SELECT *
FROM (
    SELECT
        name, department, salary,
        ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC) AS rn
    FROM employee
) ranked
WHERE rn <= 2;
```

**结果：**

| name | department | salary | rn |
|------|-----------|--------|----|
| 李四 | 技术部 | 18000 | 1 |
| 王五 | 技术部 | 18000 | 2 |
| 周八 | 市场部 | 16000 | 1 |
| 孙七 | 市场部 | 14000 | 2 |

```sql
-- 方案二：使用 RANK()（并列时都保留，可能返回超过2行）
SELECT *
FROM (
    SELECT
        name, department, salary,
        RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employee
) ranked
WHERE rnk <= 2;
```

**结果（市场部 14000 并列，均返回）：**

| name | department | salary | rnk |
|------|-----------|--------|-----|
| 李四 | 技术部 | 18000 | 1 |
| 王五 | 技术部 | 18000 | 1 |
| 周八 | 市场部 | 16000 | 1 |
| 孙七 | 市场部 | 14000 | 2 |
| 吴九 | 市场部 | 14000 | 2 |

**选择建议：**
- 需要严格限制行数 → `ROW_NUMBER()`
- 需要包含所有并列名次 → `RANK()` 或 `DENSE_RANK()`

### 2.3 其他常用开窗函数

**累计求和（运营场景：按日期累计销售额）：**

```sql
CREATE TABLE daily_sales (
    sale_date DATE,
    amount    DECIMAL(10, 2)
);
INSERT INTO daily_sales VALUES
('2024-01-01', 1000),
('2024-01-02', 1500),
('2024-01-03', 800),
('2024-01-04', 2000);

SELECT
    sale_date,
    amount,
    SUM(amount) OVER (ORDER BY sale_date) AS cumulative_sum,  -- 累计销售额
    AVG(amount) OVER (ORDER BY sale_date
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS moving_avg  -- 近3日均值
FROM daily_sales;
```

**结果：**

| sale_date | amount | cumulative_sum | moving_avg |
|-----------|--------|---------------|------------|
| 2024-01-01 | 1000 | 1000 | 1000.00 |
| 2024-01-02 | 1500 | 2500 | 1250.00 |
| 2024-01-03 | 800 | 3300 | 1100.00 |
| 2024-01-04 | 2000 | 5300 | 1433.33 |

**取上一行值（环比计算）：**

```sql
SELECT
    sale_date,
    amount,
    LAG(amount, 1) OVER (ORDER BY sale_date) AS prev_day_amount,
    amount - LAG(amount, 1) OVER (ORDER BY sale_date) AS day_over_day_diff
FROM daily_sales;
```

| sale_date | amount | prev_day_amount | day_over_day_diff |
|-----------|--------|----------------|-------------------|
| 2024-01-01 | 1000 | NULL | NULL |
| 2024-01-02 | 1500 | 1000 | 500 |
| 2024-01-03 | 800 | 1500 | -700 |
| 2024-01-04 | 2000 | 800 | 1200 |

---

## 3. SELECT DUAL 的用途

### 3.1 什么是 DUAL

`DUAL` 是 Oracle 中一张特殊的**虚拟表**，只有一行一列，专门用于在不需要真实数据表时执行表达式、函数或常量查询。

MySQL 为了兼容 Oracle 语法，也支持 `DUAL`：

```sql
-- Oracle 中必须有 FROM 子句，所以用 DUAL
SELECT 1 + 1 FROM DUAL;        -- 结果：2
SELECT NOW() FROM DUAL;        -- 结果：当前时间
SELECT 'hello' FROM DUAL;      -- 结果：hello
SELECT VERSION() FROM DUAL;    -- 结果：MySQL 版本号
```

### 3.2 MySQL 中 DUAL 是否必要

**MySQL 中 `FROM DUAL` 可以省略**，以下两种写法等价：

```sql
SELECT 1 + 1 FROM DUAL;   -- 有 DUAL（兼容 Oracle 写法）
SELECT 1 + 1;             -- 无 DUAL（MySQL 原生写法，推荐）
```

### 3.3 DUAL 的实际用途

**① 测试函数或表达式**

```sql
SELECT NOW() FROM DUAL;                    -- 测试当前时间
SELECT MD5('password') FROM DUAL;          -- 测试 MD5 函数
SELECT DATE_FORMAT(NOW(), '%Y-%m') DUAL;   -- 测试日期格式化
```

**② MyBatis / ORM 框架中动态 SQL**

在某些框架生成的 SQL 中，为保证语法合法，会用 `DUAL` 做占位：

```sql
-- 使用 DUAL 实现"存在则更新，不存在则插入"的判断逻辑
INSERT INTO config (key, value)
SELECT 'max_retry', '3' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM config WHERE key = 'max_retry');
```

**③ 生成序列或常量结果集**

```sql
-- 生成一个常量行参与 UNION
SELECT 'admin' AS role, 1 AS level FROM DUAL
UNION ALL
SELECT 'user', 2 FROM DUAL
UNION ALL
SELECT 'guest', 3 FROM DUAL;
```

> **总结**：在 MySQL 中，`SELECT DUAL` 主要用于**兼容 Oracle 语法**或**框架动态 SQL 的占位**，纯 MySQL 项目中通常无需使用。

---

## 4. UNION 与 UNION ALL 的区别

### 4.1 基本语法

```sql
-- UNION：合并结果集，自动去重
SELECT col FROM table1
UNION
SELECT col FROM table2;

-- UNION ALL：合并结果集，保留所有行（含重复）
SELECT col FROM table1
UNION ALL
SELECT col FROM table2;
```

### 4.2 核心区别

| 对比维度 | UNION | UNION ALL |
|----------|-------|-----------|
| **去重** | ✅ 自动去除重复行 | ❌ 保留所有行（含重复） |
| **性能** | 慢（需要排序/Hash去重） | 快（直接合并，无额外处理） |
| **结果顺序** | 不保证（去重时可能排序） | 不保证 |
| **适用场景** | 需要去重的合并 | 确定无重复或不关心重复时 |

### 4.3 性能差异原因

`UNION` 在合并后需要对整个结果集进行**去重处理**（通常借助临时表 + 排序或 Hash），而 `UNION ALL` 直接流式返回，无需额外操作：

```sql
EXPLAIN SELECT id FROM t1 UNION SELECT id FROM t2;
-- Extra: Using temporary（使用临时表去重）

EXPLAIN SELECT id FROM t1 UNION ALL SELECT id FROM t2;
-- 无临时表，直接合并输出
```

### 4.4 实际场景示例

**场景一：合并两个不重叠的分区表（用 UNION ALL）**

```sql
-- orders_2023 和 orders_2024 按年分表，数据天然不重复
SELECT id, amount FROM orders_2023 WHERE customer_id = 100
UNION ALL
SELECT id, amount FROM orders_2024 WHERE customer_id = 100;
-- 无重复可能，UNION ALL 更快
```

**场景二：多渠道用户合并去重（用 UNION）**

```sql
-- 微信注册用户和手机注册用户可能有重叠（同一个人两种方式注册）
SELECT user_id FROM wechat_users
UNION
SELECT user_id FROM phone_users;
-- 需要去重，使用 UNION
```

**场景三：生成枚举结果集**

```sql
-- 生成状态码映射表
SELECT 0 AS code, '待支付' AS label FROM DUAL
UNION ALL
SELECT 1, '已支付' FROM DUAL
UNION ALL
SELECT 2, '已取消' FROM DUAL;
-- 数据明确无重复，用 UNION ALL
```

### 4.5 使用注意事项

**① 列数和类型必须匹配**

```sql
-- 错误：列数不一致
SELECT id, name FROM t1
UNION ALL
SELECT id FROM t2;  -- 报错

-- 正确：列数一致，类型兼容
SELECT id, name FROM t1
UNION ALL
SELECT id, NULL FROM t2;  -- 用 NULL 补齐
```

**② ORDER BY 只能在最后一条 SELECT 上**

```sql
-- 正确：对最终结果排序
SELECT id, amount FROM orders_2023
UNION ALL
SELECT id, amount FROM orders_2024
ORDER BY amount DESC;

-- 错误：中间 SELECT 加 ORDER BY（MySQL 会忽略或报错）
SELECT id, amount FROM orders_2023 ORDER BY amount  -- ❌
UNION ALL
SELECT id, amount FROM orders_2024;
```

**③ 优先使用 UNION ALL**

只要业务上能确定两个结果集无重复（或不关心重复），**始终使用 `UNION ALL`**，避免不必要的去重开销。

---

## 5. 综合实战：结合开窗函数与 UNION ALL

**需求：统计技术部和市场部各自的薪资排名，合并输出，并标注所属部门**

```sql
SELECT department, name, salary, rnk, '部门排名' AS tag
FROM (
    SELECT
        department, name, salary,
        RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS rnk
    FROM employee
    WHERE department IN ('技术部', '市场部')
) t
WHERE rnk <= 3

UNION ALL

SELECT '全公司', name, salary, rnk, '全公司排名'
FROM (
    SELECT
        name, salary,
        RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employee
) t
WHERE rnk <= 3

ORDER BY tag, department, rnk;
```

---

## 6. 总结

| 知识点 | 核心要点 |
|--------|----------|
| **开窗函数** | 不折叠行，对"窗口范围"内数据计算；`OVER(PARTITION BY ... ORDER BY ...)` |
| **ROW_NUMBER()** | 每行唯一序号，严格取前N行时使用 |
| **RANK()** | 并列时相同名次，下一名跳过；保留所有并列时使用 |
| **DENSE_RANK()** | 并列时相同名次，序号连续；需要连续排名时使用 |
| **SELECT DUAL** | 虚拟表，MySQL 兼容 Oracle 语法；MySQL 中 `FROM DUAL` 可省略 |
| **UNION** | 合并去重，有额外排序/Hash开销 |
| **UNION ALL** | 合并不去重，性能更优；确认无重复时优先使用 |

---

## 7. 参考

- [MySQL 8.0 Window Functions](https://dev.mysql.com/doc/refman/8.0/en/window-functions.html)
- [MySQL 8.0 UNION Syntax](https://dev.mysql.com/doc/refman/8.0/en/union.html)
- [MySQL 8.0 DUAL Table](https://dev.mysql.com/doc/refman/8.0/en/select.html)
