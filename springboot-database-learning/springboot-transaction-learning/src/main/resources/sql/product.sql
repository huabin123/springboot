-- 商品表（用于演示FOR UPDATE在不同事务隔离级别下的行为）
CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
    `price` DECIMAL(10, 2) NOT NULL COMMENT '商品价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    `version` INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- 初始化测试数据
INSERT INTO `product` (`id`, `name`, `price`, `stock`, `version`, `created_time`, `updated_time`)
VALUES
    (1, 'iPhone 15 Pro', 7999.00, 100, 0, NOW(), NOW()),
    (2, 'MacBook Pro', 12999.00, 50, 0, NOW(), NOW()),
    (3, 'AirPods Pro', 1999.00, 200, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `price` = VALUES(`price`),
    `stock` = VALUES(`stock`),
    `version` = 0,
    `updated_time` = NOW();
