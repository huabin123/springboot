-- 产品创建日志表
-- 数据库：springboot_db2

USE springboot_db2;

CREATE TABLE `product_create_log` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `batch_no` VARCHAR(50) NOT NULL COMMENT '批次号',
  `product_id` BIGINT(20) DEFAULT NULL COMMENT '产品ID',
  `product_code` VARCHAR(50) NOT NULL COMMENT '产品编码',
  `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称',
  `price` DECIMAL(10,2) NOT NULL COMMENT '产品价格',
  `stock` INT(11) DEFAULT '0' COMMENT '库存数量',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '产品描述',
  `create_status` INT(11) NOT NULL DEFAULT '0' COMMENT '创建状态：0-创建中，1-创建成功，2-创建失败',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  `creator` VARCHAR(50) NOT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_batch_no` (`batch_no`),
  KEY `idx_product_code` (`product_code`),
  KEY `idx_create_status` (`create_status`),
  KEY `idx_creator` (`creator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品创建日志表';
