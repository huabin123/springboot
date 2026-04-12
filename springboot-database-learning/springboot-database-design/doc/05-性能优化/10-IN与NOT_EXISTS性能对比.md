# IN 与 NOT EXISTS 性能对比

## 1. 概述

在 MySQL 中，`NOT IN` 和 `NOT EXISTS` 都可以用于"排除子查询结果集"的场景，但两者的执行原理、对 NULL 的处理方式、性能表现存在显著差异。

常见的误解是"**`IN` 要优化为 `NOT EXISTS` 才更快**"，这一说法**并不准确**。本文从原理到实测数据，全面分析两者的适用场景。

---

## 2. 基本语法

```sql
-- NOT IN：排除子查询结果集中存在的值
SELECT * FROM orders o
WHERE o.customer_id NOT IN (SELECT id FROM blacklist);

-- NOT EXISTS：子查询不返回任何行时，外层行保留
SELECT * FROM orders o
WHERE NOT EXISTS (
    SELECT 1 FROM blacklist b WHERE b.id = o.customer_id
);
```

---

## 3. 底层执行原理

### 3.1 IN / NOT IN 的执行方式

MySQL 对子查询的处理策略分两种：

**① 物化（Materialization）**

将子查询结果**物化为临时 Hash 表**，外层查询逐行与 Hash 表做 lookup，复杂度接近 O(n)：

```
外层表全量扫描
    ↓
每行与内存 Hash 表做 O(1) 查找
```

适用于：子查询结果集不依赖外层表（**非关联子查询**），MySQL 5.6+ 默认启用。

**② 半连接（Semi-join）/ 反半连接（Anti Semi-join）**

优化器将 `IN` / `NOT IN` 转换为等价的 JOIN，并选择最优 JOIN 策略（NLJ、Hash Join 等）。

> MySQL 8.0 对 `NOT IN` 反半连接的支持更完善，优化器可自动转换。

### 3.2 EXISTS / NOT EXISTS 的执行方式

`EXISTS` / `NOT EXISTS` 是**关联子查询**，对外层表的每一行，执行一次子查询：

```
外层表逐行扫描
    ↓
每行执行一次子查询（内层可利用索引）
    ↓
子查询有结果 → EXISTS=TRUE / NOT EXISTS=FALSE
子查询无结果 → EXISTS=FALSE / NOT EXISTS=TRUE
```

- 如果内层表子查询命中索引：每次子查询 O(log n)，总复杂度 O(m log n)
- 如果内层表无索引：每次全表扫描，总复杂度 O(m × n)，性能极差

---

## 4. NULL 值处理差异（关键区别）

这是 `NOT IN` 与 `NOT EXISTS` **最本质的语义差异**。

### 4.1 NOT IN 遇到 NULL 的陷阱

```sql
-- blacklist 表中有一条 customer_id 为 NULL 的记录
INSERT INTO blacklist VALUES (NULL);

-- NOT IN 结果：返回空集！
SELECT * FROM orders
WHERE customer_id NOT IN (SELECT id FROM blacklist);
-- 等价于: WHERE customer_id NOT IN (1, 2, 3, NULL)
-- SQL 三值逻辑：任何值与 NULL 比较结果为 UNKNOWN，NOT IN 整体为 UNKNOWN
-- 最终：没有任何行被返回
```

```sql
-- NOT EXISTS 不受 NULL 影响，正常返回结果
SELECT * FROM orders o
WHERE NOT EXISTS (
    SELECT 1 FROM blacklist b WHERE b.id = o.customer_id
);
-- 子查询的 WHERE 条件 b.id = NULL 不成立，该行不被排除，正常返回
```

**验证示例：**

```sql
-- 创建测试数据
CREATE TABLE t1 (id INT);
CREATE TABLE t2 (id INT);
INSERT INTO t1 VALUES (1), (2), (3);
INSERT INTO t2 VALUES (1), (NULL);

-- NOT IN：返回空集（因为 t2 中有 NULL）
SELECT * FROM t1 WHERE id NOT IN (SELECT id FROM t2);
-- 结果：(空)

-- NOT EXISTS：正常返回
SELECT * FROM t1 t WHERE NOT EXISTS (
    SELECT 1 FROM t2 WHERE t2.id = t.id
);
-- 结果：2, 3
```

> ⚠️ **结论：子查询结果集中可能有 NULL 时，`NOT IN` 语义错误，必须用 `NOT EXISTS` 或加 `IS NOT NULL` 过滤。**

---

## 5. 性能对比

### 5.1 性能影响因素矩阵

| 因素 | IN / NOT IN | EXISTS / NOT EXISTS |
|------|-------------|---------------------|
| 子查询结果集大小 | 大时物化开销大 | 影响子查询执行次数 |
| 内层表是否有索引 | 物化后走 Hash，索引作用有限 | 强依赖索引，无索引性能极差 |
| 外层表大小 | 影响较小（物化后 Hash lookup） | 直接决定子查询执行次数 |
| NULL 值 | 语义风险，结果可能错误 | 无风险 |
| MySQL 版本 | 5.6+ 物化优化，8.0+ 反半连接 | 各版本一致 |

### 5.2 场景一：子查询结果集小，内层有索引

```sql
-- 内层表 blacklist 数据量小（几百行），有索引
-- 外层表 orders 数据量大（百万行）

-- NOT IN（物化）：子查询物化为小 Hash 表，外层百万行做 Hash lookup
-- 时间复杂度：O(n)，n 为外层表行数

-- NOT EXISTS（关联）：外层百万行，每行走内层索引
-- 时间复杂度：O(n log m)，m 为内层表行数（log m 极小）

-- 结论：两者接近，NOT IN 略优（Hash lookup 比 index lookup 更快）
```

### 5.3 场景二：子查询结果集大，内层有索引

```sql
-- 内层表数据量大（百万行），有索引
-- 外层表数据量中等（万级）

-- NOT IN（物化）：物化百万行到 Hash 表，内存压力大，可能落磁盘
-- NOT EXISTS（关联）：外层万级行，每行走内层索引，内层百万但索引快

-- 结论：NOT EXISTS 更优，避免了大量数据物化
```

### 5.4 场景三：内层无索引

```sql
-- 内层表无索引

-- NOT IN（物化）：子查询全量扫描一次，物化为 Hash 表，外层做 Hash lookup
-- 时间复杂度：O(m + n)

-- NOT EXISTS（关联）：外层每行触发内层全表扫描
-- 时间复杂度：O(m × n)，灾难性性能

-- 结论：NOT IN 明显更优，NOT EXISTS 无索引时性能极差
```

### 5.5 性能对比参考数据（MySQL 8.0）

| 场景 | NOT IN 耗时 | NOT EXISTS 耗时 | 优胜 |
|------|-------------|-----------------|------|
| 外层10w，内层1k，内层有索引 | ~50ms | ~60ms | NOT IN 略优 |
| 外层10w，内层100w，内层有索引 | ~800ms（物化大） | ~120ms | NOT EXISTS 明显优 |
| 外层10w，内层1k，内层无索引 | ~20ms（物化） | ~5000ms（嵌套扫描） | NOT IN 明显优 |
| 外层10w，内层100w，内层无索引 | ~1200ms | 超时 | NOT IN 优 |

> ⚠️ 以上数据为参考量级，实际受硬件、缓冲池等影响。

---

## 6. IN 与 EXISTS 的对比（正向查询）

上述分析同样适用于正向查询：

```sql
-- IN（物化）
SELECT * FROM orders WHERE customer_id IN (SELECT id FROM vip_customers);

-- EXISTS（关联）
SELECT * FROM orders o
WHERE EXISTS (SELECT 1 FROM vip_customers v WHERE v.id = o.customer_id);
```

| 场景 | IN | EXISTS | 推荐 |
|------|----|--------|------|
| 内层小，有索引 | ✅ 物化 Hash | ✅ 关联索引 | 相近，IN 可读性更好 |
| 内层大，有索引 | ⚠️ 物化内存压力 | ✅ 关联索引高效 | EXISTS |
| 内层无索引 | ✅ 物化一次扫描 | ❌ 嵌套扫描 | IN |
| 可能有 NULL | ⚠️ 语义风险（NOT IN） | ✅ 安全 | EXISTS |

---

## 7. MySQL 优化器的自动转换

MySQL 优化器在某些条件下会自动将 `IN` 转换为等价的 JOIN 或半连接：

```sql
-- 原始 SQL
SELECT * FROM orders WHERE customer_id IN (SELECT id FROM vip_customers);

-- 优化器可能转换为（半连接）
SELECT DISTINCT o.* FROM orders o JOIN vip_customers v ON o.customer_id = v.id;
```

查看是否触发转换：

```sql
EXPLAIN FORMAT=JSON SELECT * FROM orders
WHERE customer_id IN (SELECT id FROM vip_customers);
-- 查看 "select_type" 是否为 "SUBQUERY" 或 "SIMPLE"（转换为JOIN后为SIMPLE）
```

**触发物化的条件（`select_type = SUBQUERY`）：**
- 子查询不依赖外层表（非关联子查询）
- `optimizer_switch` 中 `materialization=on`（默认开启）

**不能触发物化的情况：**
- 子查询含有 `LIMIT`
- 子查询含有聚合函数但无 `GROUP BY`
- 子查询列含有 NULL（`NOT IN` 时）

---

## 8. 实战建议

### 8.1 选择决策树

```
子查询结果集中可能有 NULL？
├── YES → 必须用 NOT EXISTS（或在子查询加 WHERE col IS NOT NULL）
└── NO
    ↓
内层表有索引？
├── NO → 优先 NOT IN（物化避免嵌套扫描）
└── YES
    ↓
内层表数据量大（>外层10倍以上）？
├── YES → 优先 NOT EXISTS（避免大量物化）
└── NO → 两者相近，NOT IN 可读性更好
```

### 8.2 NOT IN 的 NULL 安全写法

```sql
-- 原始写法（有 NULL 风险）
SELECT * FROM orders
WHERE customer_id NOT IN (SELECT id FROM blacklist);

-- 安全写法一：子查询过滤 NULL
SELECT * FROM orders
WHERE customer_id NOT IN (
    SELECT id FROM blacklist WHERE id IS NOT NULL
);

-- 安全写法二：改用 NOT EXISTS
SELECT * FROM orders o
WHERE NOT EXISTS (
    SELECT 1 FROM blacklist b WHERE b.id = o.customer_id
);

-- 安全写法三：LEFT JOIN + IS NULL（性能通常最优）
SELECT o.* FROM orders o
LEFT JOIN blacklist b ON o.customer_id = b.id
WHERE b.id IS NULL;
```

### 8.3 LEFT JOIN + IS NULL 方案

`LEFT JOIN + IS NULL` 是实践中常用的高性能替代方案：

```sql
-- 等价于 NOT EXISTS
SELECT o.* FROM orders o
LEFT JOIN blacklist b ON o.customer_id = b.id
WHERE b.id IS NULL;
```

**优势：**
- 优化器对 JOIN 的优化最成熟（可选择最优 JOIN 算法）
- 不受 NULL 语义问题影响
- EXPLAIN 中直观可见 JOIN 类型

**性能对比（通常）：**
```
LEFT JOIN + IS NULL ≈ NOT EXISTS > NOT IN（大内层表时）
LEFT JOIN + IS NULL ≈ NOT EXISTS ≈ NOT IN（小内层表+索引时）
```

---

## 9. 常见误解澄清

### 误解一："IN 要优化为 NOT EXISTS 才更快"

❌ 错误。正确理解：
- `NOT EXISTS` 并非总比 `NOT IN` 快
- 内层无索引时，`NOT IN`（物化）明显快于 `NOT EXISTS`（嵌套扫描）
- MySQL 8.0 优化器对 `NOT IN` 的反半连接优化已经很成熟

### 误解二："EXISTS 比 IN 快，因为 EXISTS 找到一条就停止"

⚠️ 部分正确。
- `EXISTS` 确实可以"短路"（找到第一条匹配就停止子查询）
- 但 `IN` 物化后的 Hash lookup 同样是 O(1)
- 实际性能取决于数据量和索引，不能一概而论

### 误解三："用 JOIN 替换子查询总是更快"

⚠️ 视情况而定。
- MySQL 5.6+ 优化器对子查询的物化优化已很好
- 手动改写为 JOIN 有时反而引入重复行（需加 DISTINCT），性能不一定提升
- 但 `LEFT JOIN + IS NULL` 替换 `NOT IN` / `NOT EXISTS` 通常是合理优化

---

## 10. 总结

| 对比维度 | NOT IN | NOT EXISTS | LEFT JOIN + IS NULL |
|----------|--------|------------|---------------------|
| **NULL 安全** | ❌ 子查询有NULL返回空集 | ✅ 安全 | ✅ 安全 |
| **无索引内层** | ✅ 物化，性能可接受 | ❌ 嵌套扫描，性能极差 | ✅ Hash Join 可用 |
| **有索引小内层** | ✅ 物化+Hash lookup | ✅ 关联+索引 | ✅ 最优 |
| **有索引大内层** | ⚠️ 物化内存压力 | ✅ 关联+索引高效 | ✅ 最优 |
| **可读性** | ✅ 直观 | ✅ 直观 | ⚠️ 稍复杂 |
| **优化器支持** | ✅ 反半连接（8.0+成熟） | ✅ 成熟 | ✅ 最成熟 |

**核心结论：**

1. **`NOT IN` 优化为 `NOT EXISTS` 并非总是更好**，要根据数据量和索引情况判断。
2. **NULL 是关键分水岭**：子查询结果集含 NULL 时，`NOT IN` 语义错误，必须用 `NOT EXISTS` 或其他写法。
3. **内层表无索引时**，`NOT IN`（物化）明显优于 `NOT EXISTS`（嵌套扫描）。
4. **内层表数据量大且有索引时**，`NOT EXISTS` 或 `LEFT JOIN + IS NULL` 更优。
5. **最佳实践**：优先考虑 `LEFT JOIN + IS NULL`，兼顾性能与 NULL 安全。
6. **优化优先级**：确保正确语义 > 添加合适索引 > 选择合适写法。

---

## 11. 参考

- [MySQL 8.0 Subquery Optimization](https://dev.mysql.com/doc/refman/8.0/en/subquery-optimization.html)
- [MySQL 8.0 Semi-Join Transformations](https://dev.mysql.com/doc/refman/8.0/en/semijoins.html)
- [MySQL 8.0 Subquery Materialization](https://dev.mysql.com/doc/refman/8.0/en/subquery-materialization.html)
