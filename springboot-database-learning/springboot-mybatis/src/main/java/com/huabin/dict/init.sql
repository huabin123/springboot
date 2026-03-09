-- ====================================
-- 字典管理模块 - 数据库初始化脚本
-- ====================================

-- 1. 创建字典类型表
DROP TABLE IF EXISTS sys_dict_type;
CREATE TABLE sys_dict_type (
    dict_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典ID',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典名称',
    dict_pid BIGINT NOT NULL DEFAULT 0 COMMENT '父字典ID，0表示顶级字典',
    PRIMARY KEY (dict_id),
    INDEX idx_dict_pid (dict_pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 2. 创建字典项表
DROP TABLE IF EXISTS sys_dict_item;
CREATE TABLE sys_dict_item (
    item_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
    dict_id BIGINT NOT NULL COMMENT '字典ID',
    item_name VARCHAR(100) NOT NULL COMMENT '字典项名称',
    item_value VARCHAR(100) NOT NULL COMMENT '字典项值',
    PRIMARY KEY (item_id),
    INDEX idx_dict_id (dict_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';

-- 3. 插入测试数据

-- 插入顶级字典类型（dict_pid = 0）
INSERT INTO sys_dict_type (dict_id, dict_name, dict_pid) VALUES
(1, '性别', 0),
(2, '状态', 0),
(3, '用户类型', 0),
(4, '订单状态', 0);

-- 插入二级字典类型（dict_pid = 1，性别的子分类）
INSERT INTO sys_dict_type (dict_id, dict_name, dict_pid) VALUES
(5, '性别扩展', 1);

-- 插入字典项 - 性别（dict_id = 1）
INSERT INTO sys_dict_item (item_id, dict_id, item_name, item_value) VALUES
(1, 1, '男', 'male'),
(2, 1, '女', 'female'),
(3, 1, '未知', 'unknown');

-- 插入字典项 - 状态（dict_id = 2）
INSERT INTO sys_dict_item (item_id, dict_id, item_name, item_value) VALUES
(4, 2, '启用', '1'),
(5, 2, '禁用', '0');

-- 插入字典项 - 用户类型（dict_id = 3）
INSERT INTO sys_dict_item (item_id, dict_id, item_name, item_value) VALUES
(6, 3, '普通用户', 'normal'),
(7, 3, 'VIP用户', 'vip'),
(8, 3, '管理员', 'admin');

-- 插入字典项 - 订单状态（dict_id = 4）
INSERT INTO sys_dict_item (item_id, dict_id, item_name, item_value) VALUES
(9, 4, '待支付', 'pending'),
(10, 4, '已支付', 'paid'),
(11, 4, '已发货', 'shipped'),
(12, 4, '已完成', 'completed'),
(13, 4, '已取消', 'cancelled');

-- 插入字典项 - 性别扩展（dict_id = 5）
INSERT INTO sys_dict_item (item_id, dict_id, item_name, item_value) VALUES
(14, 5, '保密', 'secret'),
(15, 5, '其他', 'other');

-- 4. 验证数据
SELECT '=== 字典类型表数据 ===' AS '';
SELECT * FROM sys_dict_type ORDER BY dict_pid, dict_id;

SELECT '=== 字典项表数据 ===' AS '';
SELECT * FROM sys_dict_item ORDER BY dict_id, item_id;

-- 5. 测试查询SQL（使用列别名避免字段冲突）
SELECT '=== 测试查询：dictPid = 0（顶级字典） ===' AS '';
SELECT
    dt.dict_id AS dict_id,
    dt.dict_name AS dict_name,
    di.item_id AS item_id,
    di.dict_id AS item_dict_id,
    di.item_name AS item_name,
    di.item_value AS item_value
FROM
    sys_dict_type dt
LEFT JOIN
    sys_dict_item di ON dt.dict_id = di.dict_id
WHERE
    dt.dict_pid = 0
ORDER BY
    dt.dict_id, di.item_id;

SELECT '=== 测试查询：dictPid = 1（性别的子字典） ===' AS '';
SELECT
    dt.dict_id AS dict_id,
    dt.dict_name AS dict_name,
    di.item_id AS item_id,
    di.dict_id AS item_dict_id,
    di.item_name AS item_name,
    di.item_value AS item_value
FROM
    sys_dict_type dt
LEFT JOIN
    sys_dict_item di ON dt.dict_id = di.dict_id
WHERE
    dt.dict_pid = 1
ORDER BY
    dt.dict_id, di.item_id;

-- 6. 清理脚本（可选，用于重置数据）
-- DROP TABLE IF EXISTS sys_dict_item;
-- DROP TABLE IF EXISTS sys_dict_type;
