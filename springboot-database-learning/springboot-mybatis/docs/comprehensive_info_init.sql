-- ============================================
-- 数据库初始化脚本
-- 表名: comprehensive_info
-- 描述: 综合信息表
-- 创建时间: 2026-01-21
-- ============================================

-- 如果表存在则删除
DROP TABLE IF EXISTS `comprehensive_info`;

-- 创建表
CREATE TABLE `comprehensive_info` (
  `prod_code` VARCHAR(50) NOT NULL COMMENT '产品代码',
  `prod_name` VARCHAR(200) DEFAULT NULL COMMENT '产品名称',
  `data_date` DATETIME DEFAULT NULL COMMENT '数据日期',
  `prod_cls` VARCHAR(50) DEFAULT NULL COMMENT '产品分类',
  PRIMARY KEY (`prod_code`),
  KEY `idx_data_date` (`data_date`),
  KEY `idx_prod_cls` (`prod_cls`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='综合信息表';

-- 插入测试数据
INSERT INTO `comprehensive_info` (`prod_code`, `prod_name`, `data_date`, `prod_cls`) VALUES
('PROD001', '产品A', '2026-01-01 10:00:00', 'TYPE_A'),
('PROD002', '产品B', '2026-01-02 11:00:00', 'TYPE_B'),
('PROD003', '产品C', '2026-01-03 12:00:00', 'TYPE_A'),
('PROD004', '产品D', '2026-01-04 13:00:00', 'TYPE_C'),
('PROD005', '产品E', '2026-01-05 14:00:00', 'TYPE_B');

-- 查询验证
SELECT * FROM `comprehensive_info`;
