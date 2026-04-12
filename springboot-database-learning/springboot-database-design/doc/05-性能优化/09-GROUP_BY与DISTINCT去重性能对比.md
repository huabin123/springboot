# GROUP BY 与 DISTINCT 去重性能对比

## 1. 概述

在 MySQL 中，`DISTINCT` 和 `GROUP BY` 都可以用于数据去重，但它们的底层实现机制、性能表现和适用场景存在差异。本文从执行原理、执行计划、数据量影响、索引影响等多个维度进行深入分析。

---

## 2. 基本语法对比

**使用 DISTINCT 去重：**
```sql
SELECT DISTINCT city FROM user;
```

**使用 GROUP BY 去重：**
```sql
SELECT city FROM user GROUP BY city;
```

两者在结果上等价（不含聚合函数时），但执行路径不同。

---

## 3. 底层执行原理

### 3.1 DISTINCT 的执行原理

MySQL 对 `DISTINCT` 的处理有两种路径：

1. **利用索引（index scan）**：若目标列有索引，MySQL 可以直接遍历索引完成去重，无需排序或临时表，效率极高。
2. **使用临时表（HashAggregate）**：若无法利用索引，MySQL 会将数据写入一张临时 Hash 表，利用 Hash 去重，最终返回结果。

> `DISTINCT` 在 MySQL 优化器中实际上被转换为一种特殊的 `GROUP BY`，本质执行逻辑相同。

### 3.2 GROUP BY 的执行原理

MySQL 对 `GROUP BY` 的处理同样有两种路径：

1. **松散索引扫描（Loose Index Scan）**：如果分组列有索引，优化器可以跳跃式扫描索引，每组只读取一个值，效率极高，`Extra` 列显示 `Using index for group-by`。
2. **紧密索引扫描（Tight Index Scan）**：索引覆盖分组列，但不能使用松散扫描时，需全量扫描索引。
3. **排序 + 临时表**：无索引时，先对数据排序（`filesort`），再分组，或直接建 Hash 临时表。

### 3.3 MySQL 优化器视角

MySQL 8.0 之前，`GROUP BY` 默认会对结果排序（隐式 `ORDER BY`）；**MySQL 8.0+ 移除了这个隐式排序行为**，`GROUP BY` 不再默认排序，与 `DISTINCT` 的执行路径更接近。

```sql
-- MySQL 8.0 以前，GROUP BY 会隐式排序（等价于加了 ORDER BY city）
SELECT city FROM user GROUP BY city;

-- MySQL 8.0+，不再隐式排序
SELECT city FROM user GROUP BY city;  -- 结果顺序不保证
```

---

## 4. EXPLAIN 执行计划分析

### 4.1 无索引场景

```sql
-- 建表（city 列无索引）
CREATE TABLE user (
    id   INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50),
    city VARCHAR(50)
);
```

```sql
EXPLAIN SELECT DISTINCT city FROM user;
EXPLAIN SELECT city FROM user GROUP BY city;
```

| 指标 | DISTINCT | GROUP BY |
|------|----------|----------|
| type | ALL（全表扫描） | ALL（全表扫描） |
| Extra | Using temporary | Using temporary; Using filesort（8.0以前） |

- `DISTINCT`：使用临时 Hash 表去重，无需排序。
- `GROUP BY`（8.0 前）：使用临时表 + `filesort`，多一次排序开销。
- `GROUP BY`（8.0+）：与 `DISTINCT` 几乎一致，均使用 Hash 临时表，无额外排序。

> **结论（无索引）**：MySQL 8.0+ 两者性能基本相同；8.0 以前 `DISTINCT` 略优，因为少了排序步骤。

### 4.2 有索引场景

```sql
-- 为 city 列添加索引
ALTER TABLE user ADD INDEX idx_city (city);
```

```sql
EXPLAIN SELECT DISTINCT city FROM user;
EXPLAIN SELECT city FROM user GROUP BY city;
```

| 指标 | DISTINCT | GROUP BY |
|------|----------|----------|
| type | index（索引扫描） | range / index |
| Extra | Using index | Using index for group-by（松散索引扫描） |

- 两者都能利用索引，无需临时表，无需 `filesort`。
- `GROUP BY` 可能触发**松散索引扫描**，性能甚至略优于 `DISTINCT`（每组只读一个值）。

> **结论（有索引）**：两者性能相当，甚至 `GROUP BY` 略优（松散索引扫描）。

---

## 5. 不同数据量下的性能表现

### 5.1 测试环境说明

| 项目 | 配置 |
|------|------|
| 数据库 | MySQL 8.0 |
| 表结构 | id(PK), name, city, age |
| city 枚举值 | 100 个城市（重复率高） |

### 5.2 测试数据量梯度

| 数据量 | DISTINCT 耗时（ms）参考 | GROUP BY 耗时（ms）参考 | 说明 |
|--------|------------------------|------------------------|------|
| 1 万行（无索引） | ~5 | ~5 | 差异极小，均走临时表 |
| 10 万行（无索引） | ~30 | ~32 | 差异极小 |
| 100 万行（无索引） | ~280 | ~290 | 差异极小 |
| 1000 万行（无索引） | ~3000 | ~3100 | 略有差异，均受临时表大小影响 |
| 1 万行（有索引） | ~1 | ~1 | 均走索引，极快 |
| 100 万行（有索引） | ~3 | ~2 | 索引下性能相近，GROUP BY 略优 |
| 1000 万行（有索引） | ~15 | ~10 | 索引下 GROUP BY 松散扫描更优 |

> ⚠️ 以上数据为参考量级，实际值受硬件、缓冲池命中率、并发等因素影响。

### 5.3 关键规律总结

- **小数据量**：两者差异可忽略不计，均在毫秒级。
- **大数据量 + 无索引**：两者均需临时表，性能瓶颈相同，差异微小（8.0+ 几乎一致）。
- **大数据量 + 有索引**：`GROUP BY` 可利用松散索引扫描，性能略优于 `DISTINCT`。
- **超大数据量（千万级）**：差异会被放大，但瓶颈通常在**是否有索引**，而非 `DISTINCT` vs `GROUP BY` 本身。

---

## 6. 索引对性能的影响

索引是影响两者性能的**最核心因素**，远大于 `DISTINCT` vs `GROUP BY` 的选择本身。

### 6.1 单列索引

```sql
-- 去重列有单列索引时
ALTER TABLE user ADD INDEX idx_city (city);

-- 两者均可利用索引，避免全表扫描
SELECT DISTINCT city FROM user;       -- Using index
SELECT city FROM user GROUP BY city;  -- Using index for group-by
```

### 6.2 覆盖索引（联合索引）

```sql
-- 联合索引覆盖查询列
ALTER TABLE user ADD INDEX idx_city_name (city, name);

SELECT DISTINCT city, name FROM user;
-- Extra: Using index（覆盖索引，无需回表）

SELECT city, name FROM user GROUP BY city, name;
-- Extra: Using index（覆盖索引）
```

联合索引覆盖所有查询列时，两者均可走**覆盖索引**，性能最优。

### 6.3 无索引时的优化路径

当无法添加索引时：

```sql
-- 若去重列选择性低（如性别），可考虑直接枚举，避免全表扫描
SELECT DISTINCT status FROM order WHERE status IN (0, 1, 2, 3);

-- 对大表去重，可先用子查询缩小范围
SELECT DISTINCT city FROM user WHERE create_time > '2024-01-01';
-- 确保 create_time 有索引，先过滤数据量，再去重
```

### 6.4 索引影响总结

| 场景 | DISTINCT | GROUP BY |
|------|----------|----------|
| 无索引，小表 | 临时 Hash 表 | 临时 Hash 表 |
| 无索引，大表 | 临时表（可能落磁盘） | 临时表（可能落磁盘） |
| 单列索引 | 索引扫描 | 索引扫描/松散扫描 |
| 覆盖索引 | 覆盖索引，无回表 | 覆盖索引，无回表 |
| 松散索引扫描 | 不支持 | 支持（GROUP BY 独有优势） |

---

## 7. 特殊场景分析

### 7.1 多列去重

```sql
-- 多列去重
SELECT DISTINCT city, age FROM user;
SELECT city, age FROM user GROUP BY city, age;
```

多列去重时行为一致，性能差异与单列相同逻辑。

### 7.2 GROUP BY + 聚合函数

`GROUP BY` 支持聚合函数，`DISTINCT` 不支持，这是两者功能上的本质区别：

```sql
-- 去重的同时统计每个城市的用户数
SELECT city, COUNT(*) FROM user GROUP BY city;  -- 正确

-- DISTINCT 无法完成此需求
SELECT DISTINCT city, COUNT(*) FROM user;  -- 错误，语义不符
```

### 7.3 DISTINCT + 聚合函数（列内去重）

`DISTINCT` 可以在聚合函数内部使用，实现列内去重统计：

```sql
-- 统计不同城市的数量
SELECT COUNT(DISTINCT city) FROM user;

-- GROUP BY 实现相同效果（性能类似）
SELECT COUNT(*) FROM (SELECT city FROM user GROUP BY city) t;
```

### 7.4 ORDER BY 的影响

```sql
-- 8.0 以前，GROUP BY 隐式排序，加 ORDER BY NULL 可消除排序开销
SELECT city FROM user GROUP BY city ORDER BY NULL;

-- 8.0+，GROUP BY 不再隐式排序，无需 ORDER BY NULL 技巧
```

---

## 8. 实际建议与最佳实践

### 8.1 如何选择

| 场景 | 推荐 |
|------|------|
| 纯去重，无聚合需求 | `DISTINCT`，语义更清晰 |
| 去重 + 聚合统计 | `GROUP BY` |
| 大数据量，关注极致性能 | 优先加索引，其次选 `GROUP BY`（可利用松散扫描） |
| MySQL 8.0 以前，无索引 | `DISTINCT` 略优（少了排序） |
| MySQL 8.0+，无索引 | 两者相同，选 `DISTINCT` 可读性更好 |

### 8.2 优化优先级

优化去重查询时，优先级如下：

```
1. 为去重列添加合适的索引（最重要）
   ↓
2. 使用覆盖索引避免回表
   ↓
3. 先过滤（WHERE）后去重，减少参与去重的数据量
   ↓
4. 再考虑 DISTINCT vs GROUP BY 的选择（影响较小）
```

### 8.3 反例：不合理的去重写法

```sql
-- 反例：对超大表全量去重（无索引，无过滤条件）
SELECT DISTINCT city FROM user_log;  -- user_log 有 1 亿行

-- 优化方案一：添加索引
ALTER TABLE user_log ADD INDEX idx_city (city);

-- 优化方案二：先过滤，再去重
SELECT DISTINCT city FROM user_log WHERE log_date = '2024-01-01';
-- 确保 log_date 有索引

-- 优化方案三：使用字典表，避免对业务大表做去重
SELECT city FROM city_dict;
```

---

## 9. 总结

| 对比维度 | DISTINCT | GROUP BY |
|----------|----------|----------|
| **本质** | 特殊的 GROUP BY（MySQL内部转换） | 分组聚合 |
| **无索引性能** | 临时 Hash 表（8.0+与GROUP BY相同） | 临时 Hash 表（8.0+无隐式排序） |
| **有索引性能** | 索引扫描 | 索引扫描 + 松散扫描（更优） |
| **大数据量** | 差异微小，均受制于是否有索引 | 差异微小，均受制于是否有索引 |
| **索引影响** | 巨大，有无索引性能差距可达 100x | 巨大，有无索引性能差距可达 100x |
| **功能** | 仅去重 | 去重 + 聚合 |
| **可读性** | 去重语义更直观 | 聚合语义更清晰 |
| **推荐场景** | 纯去重，代码可读性优先 | 聚合统计，或需要极致性能时 |

**核心结论：**
1. **性能差异很小**，两者在 MySQL 8.0+ 中几乎等价，优化器会做相同处理。
2. **索引才是关键**，有无索引对去重性能的影响远大于选择哪个关键字。
3. **数据量越大，索引越重要**；不同数据量下，两者的相对差异基本一致。
4. `GROUP BY` 在有索引时可利用**松散索引扫描**，是其唯一性能优势点。
5. 选择标准：**纯去重用 `DISTINCT`（可读性好），聚合统计用 `GROUP BY`（功能完整）**。

---

## 10. 参考

- [MySQL 8.0 GROUP BY 优化](https://dev.mysql.com/doc/refman/8.0/en/group-by-optimization.html)
- [MySQL DISTINCT 优化](https://dev.mysql.com/doc/refman/8.0/en/distinct-optimization.html)
- [MySQL 8.0 Release Notes - GROUP BY 隐式排序移除](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-1.html)
