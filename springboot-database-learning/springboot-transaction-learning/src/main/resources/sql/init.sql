-- 创建数据库
CREATE DATABASE IF NOT EXISTS springboot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE springboot_db;

-- 账户表（用于演示行锁、间隙锁、MVCC等）
DROP TABLE IF EXISTS account;
CREATE TABLE account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '账户ID',
    user_name VARCHAR(50) NOT NULL COMMENT '用户名',
    balance DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '账户余额',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_name(user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表';

-- 插入测试数据
INSERT INTO account (user_name, balance) VALUES 
('张三', 1000.00),
('李四', 2000.00),
('王五', 3000.00),
('赵六', 4000.00),
('钱七', 5000.00);

-- 订单表（用于演示表级锁）
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount DECIMAL(10, 2) NOT NULL COMMENT '订单金额',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no(order_no),
    INDEX idx_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单详情表
DROP TABLE IF EXISTS order_detail;
CREATE TABLE order_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '详情ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    product_name VARCHAR(100) NOT NULL COMMENT '商品名称',
    quantity INT NOT NULL COMMENT '数量',
    price DECIMAL(10, 2) NOT NULL COMMENT '单价',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_order_id(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单详情表';

-- 日志表（用于记录事务操作）
DROP TABLE IF EXISTS transaction_log;
CREATE TABLE transaction_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    description VARCHAR(500) COMMENT '描述',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务日志表';

-- 用户表（用于演示间隙锁）
DROP TABLE IF EXISTS user;
CREATE TABLE user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    age INT NOT NULL COMMENT '年龄',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    email VARCHAR(100) COMMENT '邮箱',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_age(age)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 插入测试数据
INSERT INTO user (age, name, email) VALUES 
(10, '用户A', 'a@test.com'),
(20, '用户B', 'b@test.com'),
(30, '用户C', 'c@test.com'),
(50, '用户D', 'd@test.com');
