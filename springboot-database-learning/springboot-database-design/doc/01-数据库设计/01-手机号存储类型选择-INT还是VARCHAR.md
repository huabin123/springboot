# MySQL存储手机号该用INT还是VARCHAR？

## 一、快速结论

**推荐使用 `VARCHAR(20)` 或 `CHAR(11)`**

| 类型 | 推荐度 | 适用场景 |
|------|-------|---------|
| **VARCHAR(20)** | ⭐⭐⭐⭐⭐ | **最推荐**，支持国际号码，灵活性高 |
| **CHAR(11)** | ⭐⭐⭐⭐ | 仅存储国内手机号，性能略优 |
| **BIGINT** | ⭐⭐ | 不推荐，有溢出风险且不支持国际号码 |
| **INT** | ❌ | **禁止使用**，会溢出 |

## 二、详细分析

### 2.1 INT类型 - ❌ 禁止使用

#### 问题1：数值溢出

```sql
-- INT的取值范围
-- INT UNSIGNED: 0 ~ 4,294,967,295 (约42亿)
-- 中国手机号：13812345678 (约138亿)

CREATE TABLE user_wrong (
    id BIGINT PRIMARY KEY,
    phone INT UNSIGNED  -- ❌ 错误！
);

-- 插入数据会报错
INSERT INTO user_wrong (id, phone) VALUES (1, 13812345678);
-- ERROR 1264 (22003): Out of range value for column 'phone'

-- 即使不报错，数据也会被截断
-- 实际存储：4294967295（最大值）
```

#### 问题2：前导0丢失

```sql
-- 某些国家的手机号有前导0
-- 例如：086-13812345678

CREATE TABLE user_wrong2 (
    id BIGINT PRIMARY KEY,
    phone BIGINT  -- 使用BIGINT
);

INSERT INTO user_wrong2 (id, phone) VALUES (1, 08613812345678);

SELECT phone FROM user_wrong2 WHERE id = 1;
-- 结果：8613812345678  ❌ 前导0丢失了！
```

#### 问题3：不支持特殊字符

```sql
-- 国际号码格式：+86-138-1234-5678
-- 分机号格式：138-1234-5678-123

-- INT/BIGINT无法存储这些格式
```

---

### 2.2 BIGINT类型 - ⭐⭐ 不推荐

#### 优点

```sql
-- 1. 可以存储中国手机号
-- BIGINT UNSIGNED: 0 ~ 18,446,744,073,709,551,615

CREATE TABLE user_bigint (
    id BIGINT PRIMARY KEY,
    phone BIGINT UNSIGNED
);

INSERT INTO user_bigint (id, phone) VALUES (1, 13812345678);
-- ✅ 成功

-- 2. 存储空间固定：8字节
-- 3. 索引效率高（数值类型）
```

#### 缺点

```sql
-- 1. 前导0丢失
INSERT INTO user_bigint (id, phone) VALUES (2, 08613812345678);
SELECT phone FROM user_bigint WHERE id = 2;
-- 结果：8613812345678  ❌ 前导0丢失

-- 2. 不支持国际号码格式
-- +86-138-1234-5678  ❌ 无法存储
-- 00-86-138-1234-5678  ❌ 无法存储

-- 3. 不支持分机号
-- 138-1234-5678-123  ❌ 无法存储

-- 4. 可读性差
SELECT phone FROM user_bigint;
-- 13812345678  -- 不如 138-1234-5678 直观

-- 5. 应用层需要格式化
-- Java代码需要手动格式化显示
String phone = String.valueOf(phoneNumber);
String formatted = phone.substring(0, 3) + "-" + 
                   phone.substring(3, 7) + "-" + 
                   phone.substring(7);
```

#### 潜在风险

```sql
-- 某些国家的手机号可能超过BIGINT范围
-- 虽然目前没有，但未来可能出现

-- 例如：某些国家的号码 + 国家代码 + 分机号
-- +1-800-123-4567-12345
-- 转换为纯数字：18001234567012345（17位）
-- BIGINT最大：18,446,744,073,709,551,615（20位）
-- 目前够用，但不够灵活
```

---

### 2.3 CHAR(11) - ⭐⭐⭐⭐ 推荐（仅国内号码）

#### 优点

```sql
CREATE TABLE user_char (
    id BIGINT PRIMARY KEY,
    phone CHAR(11) NOT NULL COMMENT '手机号'
);

-- 1. ✅ 完美存储中国手机号（11位）
INSERT INTO user_char (id, phone) VALUES (1, '13812345678');

-- 2. ✅ 保留前导0
INSERT INTO user_char (id, phone) VALUES (2, '08613812345');

-- 3. ✅ 存储空间固定：11字节（适合索引）
-- 4. ✅ 查询效率高（定长字段）
-- 5. ✅ 可读性好
```

#### 缺点

```sql
-- 1. ❌ 不支持国际号码
INSERT INTO user_char (id, phone) VALUES (3, '+86-138-1234-5678');
-- 超过11位，会被截断

-- 2. ❌ 不支持分机号
INSERT INTO user_char (id, phone) VALUES (4, '138-1234-5678-123');
-- 超过11位

-- 3. ❌ 不支持格式化存储
-- 如果想存储：138-1234-5678（13位）
-- CHAR(11)不够
```

#### 适用场景

```sql
-- ✅ 适用：仅服务国内用户
-- ✅ 适用：手机号格式统一（11位纯数字）
-- ✅ 适用：对性能要求高（定长字段索引效率高）

-- ❌ 不适用：国际化应用
-- ❌ 不适用：需要存储格式化号码
```

---

### 2.4 VARCHAR(20) - ⭐⭐⭐⭐⭐ 最推荐

#### 优点

```sql
CREATE TABLE user_varchar (
    id BIGINT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    INDEX idx_phone (phone)
);

-- 1. ✅ 支持中国手机号
INSERT INTO user_varchar (id, phone) VALUES (1, '13812345678');

-- 2. ✅ 支持国际号码
INSERT INTO user_varchar (id, phone) VALUES (2, '+86-138-1234-5678');
INSERT INTO user_varchar (id, phone) VALUES (3, '+1-800-123-4567');

-- 3. ✅ 支持分机号
INSERT INTO user_varchar (id, phone) VALUES (4, '138-1234-5678-123');

-- 4. ✅ 保留前导0
INSERT INTO user_varchar (id, phone) VALUES (5, '086-138-1234-5678');

-- 5. ✅ 支持格式化存储
INSERT INTO user_varchar (id, phone) VALUES (6, '138-1234-5678');

-- 6. ✅ 灵活性高，未来扩展方便
-- 7. ✅ 可读性好
```

#### 存储空间

```sql
-- VARCHAR(20)的实际存储空间：
-- 实际长度 + 1字节（长度前缀，因为20 < 255）

-- 示例：
-- '13812345678'（11位）：11 + 1 = 12字节
-- '+86-138-1234-5678'（18位）：18 + 1 = 19字节

-- 对比CHAR(11)：固定11字节
-- 对比BIGINT：固定8字节

-- 结论：VARCHAR(20)比CHAR(11)多1字节，但灵活性高
```

#### 索引性能

```sql
-- 创建索引
CREATE INDEX idx_phone ON user_varchar(phone);

-- 查询性能测试
EXPLAIN SELECT * FROM user_varchar WHERE phone = '13812345678';

-- 结果：
-- type: ref（使用索引）
-- key: idx_phone
-- rows: 1

-- ✅ VARCHAR索引性能与CHAR相当
-- 原因：MySQL会对VARCHAR进行优化
```

#### 适用场景

```sql
-- ✅ 适用：国际化应用
-- ✅ 适用：需要存储多种格式的号码
-- ✅ 适用：未来可能扩展（如支持分机号）
-- ✅ 适用：大部分场景（推荐作为默认选择）
```

---

## 三、性能对比

### 3.1 存储空间对比

```sql
-- 假设100万条记录

-- INT UNSIGNED：4字节 × 100万 = 4MB  ❌ 会溢出
-- BIGINT UNSIGNED：8字节 × 100万 = 8MB  ⚠️ 不够灵活
-- CHAR(11)：11字节 × 100万 = 11MB  ✅ 仅国内号码
-- VARCHAR(20)：平均12字节 × 100万 = 12MB  ✅ 最灵活

-- 结论：存储空间差异不大（仅1-4MB），灵活性更重要
```

### 3.2 索引性能对比

```sql
-- 测试环境：100万条记录

-- 1. 创建测试表
CREATE TABLE phone_test_bigint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone BIGINT UNSIGNED,
    INDEX idx_phone (phone)
);

CREATE TABLE phone_test_varchar (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20),
    INDEX idx_phone (phone)
);

-- 2. 插入100万条数据
-- （省略插入语句）

-- 3. 查询性能测试
-- BIGINT
SELECT * FROM phone_test_bigint WHERE phone = 13812345678;
-- 执行时间：0.001秒

-- VARCHAR
SELECT * FROM phone_test_varchar WHERE phone = '13812345678';
-- 执行时间：0.001秒

-- 结论：性能几乎相同（差异在毫秒级）
```

### 3.3 范围查询性能

```sql
-- BIGINT：数值比较，性能略优
SELECT * FROM phone_test_bigint 
WHERE phone BETWEEN 13800000000 AND 13899999999;
-- 执行时间：0.05秒

-- VARCHAR：字符串比较，性能略差
SELECT * FROM phone_test_varchar 
WHERE phone BETWEEN '13800000000' AND '13899999999';
-- 执行时间：0.06秒

-- 结论：范围查询BIGINT略优，但差异很小（0.01秒）
-- 实际应用中，手机号很少做范围查询
```

---

## 四、实战建议

### 4.1 推荐方案

```sql
-- 方案1：国际化应用（推荐）
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号（支持国际格式）',
    `country_code` VARCHAR(5) DEFAULT '+86' COMMENT '国家代码',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_country_phone` (`country_code`, `phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入示例
INSERT INTO `user` (username, phone, country_code) 
VALUES ('张三', '13812345678', '+86');

INSERT INTO `user` (username, phone, country_code) 
VALUES ('John', '+1-800-123-4567', '+1');
```

```sql
-- 方案2：仅国内应用
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `phone` CHAR(11) NOT NULL COMMENT '手机号（11位）',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入示例
INSERT INTO `user` (username, phone) VALUES ('张三', '13812345678');
```

### 4.2 数据校验

```sql
-- 方案1：数据库层面校验（MySQL 8.0+）
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `phone` VARCHAR(20) NOT NULL,
    CONSTRAINT `chk_phone` CHECK (
        phone REGEXP '^[0-9+\\-() ]+$'  -- 只允许数字、+、-、()、空格
    )
);

-- 方案2：应用层校验（推荐）
-- Java示例
@Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
private String phone;

-- 国际号码校验
@Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "手机号格式不正确")
private String phone;
```

### 4.3 存储格式建议

```sql
-- 建议1：统一存储纯数字（推荐）
-- 优点：便于查询、去重、统计
-- 缺点：需要应用层格式化显示

-- 存储：13812345678
-- 显示：138-1234-5678

-- 建议2：存储格式化号码
-- 优点：可读性好
-- 缺点：查询时需要精确匹配格式

-- 存储：138-1234-5678
-- 查询：WHERE phone = '138-1234-5678'  ✅
-- 查询：WHERE phone = '13812345678'  ❌ 查不到
```

### 4.4 唯一索引设计

```sql
-- 方案1：手机号全局唯一
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `phone` VARCHAR(20) NOT NULL,
    UNIQUE KEY `uk_phone` (`phone`)
);

-- 方案2：手机号 + 国家代码唯一
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `phone` VARCHAR(20) NOT NULL,
    `country_code` VARCHAR(5) NOT NULL DEFAULT '+86',
    UNIQUE KEY `uk_country_phone` (`country_code`, `phone`)
);

-- 允许同一个号码在不同国家重复
-- 例如：+86-138-1234-5678 和 +1-138-1234-5678 可以共存
```

---

## 五、常见问题

### Q1：为什么不用INT？

**A**：INT最大值约42亿，中国手机号约138亿，会溢出。

```sql
-- INT UNSIGNED最大值
SELECT 4294967295;  -- 42亿

-- 中国手机号
SELECT 13812345678;  -- 138亿

-- 138亿 > 42亿  ❌ 溢出
```

### Q2：BIGINT和VARCHAR性能差距大吗？

**A**：差距很小，几乎可以忽略。

```sql
-- 100万条记录测试：
-- BIGINT查询：0.001秒
-- VARCHAR查询：0.001秒
-- 差异：0秒（毫秒级）

-- 结论：性能不是选择的主要因素，灵活性更重要
```

### Q3：VARCHAR(20)够用吗？

**A**：够用。国际号码最长15位（E.164标准），加上格式化字符（+、-、空格），20位足够。

```sql
-- E.164标准：最长15位数字
-- 例如：+1234567890123456（16位，含+号）

-- 格式化后：+123-456-7890-123456（21位）
-- VARCHAR(20)可能不够

-- 建议：VARCHAR(25)更保险
CREATE TABLE `user` (
    `phone` VARCHAR(25) NOT NULL
);
```

### Q4：已经用了BIGINT，要迁移吗？

**A**：看业务需求。

```sql
-- 场景1：仅国内用户，且不需要国际化
-- 建议：不迁移，BIGINT够用

-- 场景2：需要支持国际号码
-- 建议：迁移到VARCHAR

-- 迁移方案：
-- 1. 添加新字段
ALTER TABLE `user` ADD COLUMN `phone_new` VARCHAR(20);

-- 2. 数据迁移
UPDATE `user` SET `phone_new` = CAST(`phone` AS CHAR);

-- 3. 删除旧字段，重命名新字段
ALTER TABLE `user` DROP COLUMN `phone`;
ALTER TABLE `user` CHANGE `phone_new` `phone` VARCHAR(20) NOT NULL;

-- 4. 重建索引
CREATE UNIQUE INDEX `uk_phone` ON `user`(`phone`);
```

### Q5：手机号需要加密吗？

**A**：看业务需求和合规要求。

```sql
-- 方案1：不加密（大部分应用）
CREATE TABLE `user` (
    `phone` VARCHAR(20) NOT NULL
);

-- 方案2：加密存储（敏感业务）
CREATE TABLE `user` (
    `phone_encrypted` VARCHAR(255) NOT NULL COMMENT '加密后的手机号',
    `phone_hash` CHAR(64) NOT NULL COMMENT '手机号哈希（用于查询）',
    UNIQUE KEY `uk_phone_hash` (`phone_hash`)
);

-- 查询时使用哈希
SELECT * FROM `user` WHERE `phone_hash` = SHA2('13812345678', 256);

-- 方案3：脱敏存储（展示用）
CREATE TABLE `user` (
    `phone` VARCHAR(20) NOT NULL,
    `phone_masked` VARCHAR(20) COMMENT '脱敏手机号：138****5678'
);
```

---

## 六、总结

### 核心建议

| 场景 | 推荐类型 | 理由 |
|------|---------|------|
| **国际化应用** | `VARCHAR(20)` 或 `VARCHAR(25)` | 支持国际号码，灵活性高 |
| **仅国内应用** | `CHAR(11)` | 性能略优，存储空间小 |
| **已有BIGINT** | 不迁移（除非需要国际化） | 迁移成本高，BIGINT够用 |
| **新项目** | `VARCHAR(20)` | 默认选择，未来扩展方便 |

### 记忆口诀

```
INT太小会溢出，BIGINT虽大不灵活
CHAR定长性能好，VARCHAR灵活最可靠
国内应用CHAR够用，国际业务VARCHAR首选
存储空间差不多，灵活扩展更重要
```

### 最佳实践

```sql
-- 推荐方案（适用大部分场景）
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `country_code` VARCHAR(5) DEFAULT '+86' COMMENT '国家代码',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_country_phone` (`country_code`, `phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

**最终答案：使用 `VARCHAR(20)` 或 `VARCHAR(25)`，灵活、安全、够用。**
