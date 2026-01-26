-- ============================================
-- MySQL 访问权限修复脚本
-- 问题: Access denied for user 'root'@'192.168.65.1'
-- 原因: MySQL 用户权限限制了远程访问
-- ============================================

-- 方案一：授权特定 IP 访问（推荐 - 更安全）
-- 授权 192.168.65.1 访问
CREATE USER IF NOT EXISTS 'root'@'192.168.65.1' IDENTIFIED BY 'DjeEZw2S2xS7vCq';
GRANT ALL PRIVILEGES ON springboot_db.* TO 'root'@'192.168.65.1';
FLUSH PRIVILEGES;

-- 方案二：授权 Docker 网段访问（推荐 - 适用于 Docker 环境）
-- 授权 192.168.65.0/24 网段访问
CREATE USER IF NOT EXISTS 'root'@'192.168.65.%' IDENTIFIED BY 'DjeEZw2S2xS7vCq';
GRANT ALL PRIVILEGES ON springboot_db.* TO 'root'@'192.168.65.%';
FLUSH PRIVILEGES;

-- 方案三：授权所有 IP 访问（不推荐 - 安全风险）
-- 仅用于开发环境
-- CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'DjeEZw2S2xS7vCq';
-- GRANT ALL PRIVILEGES ON springboot_db.* TO 'root'@'%';
-- FLUSH PRIVILEGES;

-- 查看当前用户权限
SELECT user, host FROM mysql.user WHERE user = 'root';

-- 查看具体权限
SHOW GRANTS FOR 'root'@'192.168.65.1';
-- 或
-- SHOW GRANTS FOR 'root'@'192.168.65.%';
