# 数据库设计最佳实践

## 项目简介

本项目专注于**数据库设计相关的问题和最佳实践**，包括字段类型选择、索引设计、性能优化等内容。

与其他项目的区别：
- **springboot-transaction-learning**：事务、锁、MVCC等运行时机制
- **springboot-sharding-jdbc**：分库分表、读写分离等架构设计
- **springboot-database-design**：字段类型、索引设计等数据库设计问题（本项目）

## 文档目录

### 一、字段类型设计

#### 1. [手机号存储类型选择：INT还是VARCHAR？](./doc/01-手机号存储类型选择-INT还是VARCHAR.md)

**核心问题**：MySQL存储手机号该用INT、BIGINT还是VARCHAR？

**快速结论**：
- ⭐⭐⭐⭐⭐ **推荐使用 `VARCHAR(20)` 或 `VARCHAR(25)`**
- ⭐⭐⭐⭐ 仅国内应用可使用 `CHAR(11)`
- ⭐⭐ 不推荐使用 `BIGINT`
- ❌ 禁止使用 `INT`（会溢出）

**涵盖内容**：
- INT/BIGINT/CHAR/VARCHAR的详细对比
- 存储空间和性能分析
- 国际化支持
- 实战建议和最佳实践
- 数据校验和索引设计

---

### 二、索引设计

#### 2. [为什么有最左匹配原则？](./doc/02-为什么有最左匹配原则.md)

**核心问题**：为什么联合索引必须遵循最左匹配原则？

**快速结论**：
- 最左匹配原则源于**B+树的存储结构**
- 联合索引按字段顺序排序，只有最左边的字段是全局有序的
- 查询必须从最左边的字段开始匹配

**涵盖内容**：
- B+树索引的存储结构详解
- 为什么必须从最左边开始？（图解）
- 最左匹配原则的5大规则
- 联合索引设计的5大原则
- 实战案例分析
- 常见误区和优化技巧

#### 3. [为什么VARCHAR经常设置成255？](./doc/03-为什么VARCHAR经常设置成255.md)

**核心问题**：VARCHAR(255)有什么特殊的讲究？为什么很多人习惯用255？

**快速结论**：
- VARCHAR(255)使用**1字节**存储长度前缀
- VARCHAR(256)及以上需要**2字节**存储长度前缀
- 255是存储效率的临界点，也是历史习惯和索引兼容性的安全值

**涵盖内容**：
- VARCHAR的存储结构详解
- 长度前缀的字节数规则
- 存储空间和性能对比
- 索引长度限制的影响
- 如何选择合适的VARCHAR长度
- VARCHAR vs TEXT的使用场景

---

### 三、性能优化

#### 4. [MySQL对范围查询做了什么优化？](./doc/04-MySQL对范围查询做了什么优化.md)

**核心问题**：MySQL如何优化范围查询（BETWEEN、>、<等）的性能？

**快速结论**：
- **索引范围扫描**：利用B+树的有序性快速定位范围
- **索引条件下推（ICP）**：在存储引擎层过滤数据，减少回表
- **多范围读优化（MRR）**：将随机I/O优化为顺序I/O
- **范围优化器**：选择最优的索引和访问方式
- **跳跃扫描**：MySQL 8.0+支持跳过索引前缀
- **松散索引扫描**：优化GROUP BY查询

**涵盖内容**：
- 6种核心优化技术详解
- B+树范围扫描原理
- ICP和MRR的工作机制
- 性能对比和实战案例
- 优化建议和最佳实践

#### 5. [唯一索引和普通索引的写入读取区别](./doc/05-唯一索引和普通索引的写入读取区别.md)

**核心问题**：唯一索引和普通索引在读取和写入性能上有什么区别？

**快速结论**：
- **读取性能**：唯一索引略优（找到后立即停止），差异微秒级
- **写入性能**：普通索引更优（可使用Change Buffer），快30-50%
- **核心差异**：Change Buffer的使用（普通索引可用，唯一索引不可用）

**涵盖内容**：
- 唯一索引和普通索引的查找过程对比
- Change Buffer的工作原理和性能影响
- 唯一性检查的成本分析
- 4个实战场景分析
- 优化建议和选择策略

#### 6. [快照读和当前读的区别](./doc/06-快照读和当前读的区别.md)

**核心问题**：什么是快照读？什么是当前读？UPDATE和DELETE是快照读还是当前读？

**快速结论**：
- **快照读**：读取历史版本（MVCC），不加锁，高并发
- **当前读**：读取最新版本，加锁，强一致性
- **UPDATE/DELETE**：都是当前读（必须读取最新数据并加锁）

**涵盖内容**：
- 快照读和当前读的定义和区别
- MVCC机制详解（版本链、ReadView、可见性判断）
- UPDATE/DELETE为什么必须是当前读
- 不同隔离级别下的快照读行为
- 实战场景分析（秒杀、转账等）
- 常见问题解答

#### 7. [ES倒排索引和MySQL的B+树索引对比](./doc/07-ES倒排索引和MySQL的B+树索引对比.md)

**核心问题**：ES的倒排索引和MySQL的B+树索引有什么区别？各自适用于什么场景？

**快速结论**：
- **MySQL B+树索引**：精确查询、范围查询，O(log n)，适合结构化数据
- **ES 倒排索引**：全文搜索、模糊匹配，O(1)~O(k)，适合文本搜索
- **实际应用**：通常采用MySQL + ES混合架构，各取所长

**涵盖内容**：
- B+树索引和倒排索引的数据结构详解
- 查询方式和性能对比（精确、范围、全文、模糊）
- 更新性能和空间占用对比
- 3个实战案例分析（商品搜索、订单查询、日志分析）
- 混合架构设计方案
- 选择决策树和最佳实践

---

## 即将更新

### 四、字段类型设计（规划中）

- [ ] 为什么主键推荐使用BIGINT而不是INT？
- [ ] 金额字段该用DECIMAL还是BIGINT？
- [ ] 时间字段该用DATETIME还是TIMESTAMP？
- [ ] 布尔字段该用TINYINT还是CHAR(1)？
- [ ] 枚举类型该用ENUM还是VARCHAR？

### 五、索引设计（规划中）

- [ ] 什么时候需要创建联合索引？
- [ ] 如何选择索引的字段顺序？
- [ ] 什么是覆盖索引？如何设计？
- [ ] 如何避免索引失效？

### 六、表结构设计（规划中）

- [ ] 为什么不推荐使用外键约束？
- [ ] 如何设计一对多、多对多关系？
- [ ] 什么时候需要分表？如何分表？
- [ ] 如何设计软删除？
- [ ] 如何设计审计字段（创建时间、更新时间等）？

### 七、性能优化（规划中）

- [ ] 如何优化大表的查询性能？
- [ ] 如何优化分页查询？
- [ ] 如何优化COUNT查询？
- [ ] 如何优化JOIN查询？
- [ ] 如何优化ORDER BY和GROUP BY？

---

## 学习路径

### 初级（数据库设计基础）

1. **字段类型选择**
   - 阅读：[手机号存储类型选择](./doc/01-手机号存储类型选择-INT还是VARCHAR.md)
   - 阅读：[为什么VARCHAR经常设置成255](./doc/03-为什么VARCHAR经常设置成255.md)
   - 掌握：常见字段类型的选择原则
   - 实践：设计一个用户表，选择合适的字段类型

2. **索引基础**
   - 阅读：[为什么有最左匹配原则](./doc/02-为什么有最左匹配原则.md)
   - 掌握：B+树索引的存储结构
   - 实践：创建联合索引，验证最左匹配原则

### 中级（索引优化）

1. **索引设计**
   - 学习：如何根据查询需求设计索引
   - 掌握：联合索引的设计原则
   - 实践：分析慢查询日志，优化索引

2. **范围查询优化**
   - 阅读：[MySQL对范围查询做了什么优化](./doc/04-MySQL对范围查询做了什么优化.md)
   - 掌握：ICP、MRR等优化技术
   - 实践：使用EXPLAIN分析范围查询的执行计划

3. **索引类型选择**
   - 阅读：[唯一索引和普通索引的写入读取区别](./doc/05-唯一索引和普通索引的写入读取区别.md)
   - 掌握：Change Buffer的工作原理
   - 实践：根据业务场景选择合适的索引类型

4. **事务和并发控制**
   - 阅读：[快照读和当前读的区别](./doc/06-快照读和当前读的区别.md)
   - 掌握：MVCC机制和锁机制
   - 实践：理解不同隔离级别下的读取行为

### 高级（性能调优）

1. **表结构优化**
   - 学习：分表、分区等技术
   - 掌握：大表优化的方法
   - 实践：优化千万级数据表的查询性能

2. **综合优化**
   - 学习：查询优化、JOIN优化等
   - 掌握：性能调优的方法论
   - 实践：解决实际项目中的性能问题

---

## 工具推荐

### 1. 数据库设计工具

- **MySQL Workbench**：官方图形化工具，支持ER图设计
- **Navicat**：强大的数据库管理工具
- **DBeaver**：开源的数据库管理工具

### 2. 性能分析工具

- **EXPLAIN**：MySQL自带的查询分析工具
- **SHOW PROFILE**：查看SQL执行的详细过程
- **Performance Schema**：MySQL性能监控工具
- **pt-query-digest**：Percona Toolkit的慢查询分析工具

### 3. 在线工具

- **dbdiagram.io**：在线ER图设计工具
- **sqldbm.com**：在线数据库建模工具

---

## 最佳实践总结

### 字段类型选择

```sql
-- 1. 主键：BIGINT UNSIGNED AUTO_INCREMENT
id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT

-- 2. 手机号：VARCHAR(20)
phone VARCHAR(20) NOT NULL

-- 3. 金额：DECIMAL(10, 2)
amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00

-- 4. 时间：DATETIME（推荐）或 TIMESTAMP
created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

-- 5. 布尔：TINYINT(1)
is_deleted TINYINT(1) NOT NULL DEFAULT 0

-- 6. 枚举：VARCHAR（推荐）或 TINYINT
status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
```

### 索引设计

```sql
-- 1. 主键索引：自动创建
PRIMARY KEY (id)

-- 2. 唯一索引：保证唯一性
UNIQUE KEY uk_phone (phone)

-- 3. 普通索引：提高查询性能
INDEX idx_user_id (user_id)

-- 4. 联合索引：覆盖多个查询条件
INDEX idx_user_status_time (user_id, status, created_time)

-- 5. 覆盖索引：包含查询的所有字段
INDEX idx_user_status_amount (user_id, status, amount)
```

### 表结构设计

```sql
-- 推荐的表结构模板
CREATE TABLE `table_name` (
    -- 主键
    `id` BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    
    -- 业务字段
    `name` VARCHAR(100) NOT NULL COMMENT '名称',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    
    -- 审计字段
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT UNSIGNED COMMENT '创建人ID',
    `updated_by` BIGINT UNSIGNED COMMENT '更新人ID',
    
    -- 软删除
    `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-否，1-是',
    
    -- 索引
    INDEX `idx_status` (`status`),
    INDEX `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表注释';
```

---

## 参考资料

### 官方文档

- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [MySQL Optimization](https://dev.mysql.com/doc/refman/8.0/en/optimization.html)
- [MySQL Indexes](https://dev.mysql.com/doc/refman/8.0/en/mysql-indexes.html)

### 推荐书籍

- 《高性能MySQL》（第3版）
- 《MySQL技术内幕：InnoDB存储引擎》
- 《数据库索引设计与优化》

### 推荐博客

- [MySQL官方博客](https://mysqlserverteam.com/)
- [Percona博客](https://www.percona.com/blog/)
- [阿里云数据库博客](https://developer.aliyun.com/group/database)

---

## 贡献指南

欢迎提交Issue和Pull Request，一起完善这个项目！

### 如何贡献

1. Fork本项目
2. 创建新的文档或优化现有文档
3. 提交Pull Request

### 文档规范

- 使用Markdown格式
- 包含清晰的代码示例
- 提供实战案例分析
- 总结最佳实践

---

## 联系方式

如有问题或建议，欢迎通过以下方式联系：

- 提交Issue
- 发送邮件
- 加入讨论组

---

## 许可证

本项目采用MIT许可证。

---

## 更新日志

### 2026-01-19

- ✅ 创建项目
- ✅ 添加文档：手机号存储类型选择（INT还是VARCHAR）
- ✅ 添加文档：为什么有最左匹配原则
- ✅ 添加文档：为什么VARCHAR经常设置成255
- ✅ 添加文档：MySQL对范围查询做了什么优化
- ✅ 添加文档：唯一索引和普通索引的写入读取区别
- ✅ 添加文档：快照读和当前读的区别
- ✅ 添加文档：ES倒排索引和MySQL的B+树索引对比

### 待更新

- [ ] 添加更多字段类型设计文档（主键、金额、时间等）
- [ ] 添加更多索引设计文档（覆盖索引、索引失效等）
- [ ] 添加表结构设计文档（外键、软删除等）
- [ ] 添加性能优化文档（分页、COUNT、JOIN等）
- [ ] 添加演示代码和测试用例
